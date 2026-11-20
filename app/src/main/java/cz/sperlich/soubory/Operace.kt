package cz.sperlich.soubory

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

class Zruseno : Exception("Zrušeno")

/** Globální ukazatel průběhu dlouhých operací (kopírování, šifrování, přenos z NAS). */
object Prubeh {
    var aktivni by mutableStateOf(false)
    var text by mutableStateOf("")
    var podil by mutableFloatStateOf(-1f)
    @Volatile var zruseno = false

    fun krok(hotovo: Long, celkem: Long) {
        if (zruseno) throw Zruseno()
        podil = if (celkem > 0) (hotovo.toFloat() / celkem).coerceIn(0f, 1f) else -1f
    }
}

suspend fun <T> sPrubehem(ctx: Context, text: String, blok: suspend () -> T): T? {
    Prubeh.text = text
    Prubeh.podil = -1f
    Prubeh.zruseno = false
    Prubeh.aktivni = true
    return try {
        withContext(Dispatchers.IO) { blok() }
    } catch (e: Zruseno) {
        zprava(ctx, "Zrušeno")
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        zprava(ctx, chybaText(e))
        null
    } finally {
        Prubeh.aktivni = false
    }
}

fun chybaText(e: Throwable): String {
    val m = e.message ?: e.javaClass.simpleName
    return when {
        m.contains("Auth fail", true) || m.contains("Auth cancel", true) -> "Špatné jméno nebo heslo k NAS"
        m.contains("HostKey has been changed", true) ->
            "Změnil se klíč serveru. Pokud jsi NAS přeinstaloval, dej v úpravě připojení „Zapomenout klíč serveru“."
        m.contains("UnknownHost", true) || e is java.net.UnknownHostException -> "Server nenalezen – zkontroluj adresu"
        m.contains("timeout", true) || m.contains("timed out", true) -> "NAS neodpovídá – jsi na domácí Wi-Fi?"
        m.contains("Connection refused", true) -> "NAS odmítl spojení – běží na něm SSH/SFTP?"
        m.contains("Permission denied", true) -> "Nemáš oprávnění"
        else -> "Chyba: $m"
    }
}

sealed class Polozka {
    abstract val jmeno: String

    data class Lok(val f: File) : Polozka() {
        override val jmeno get() = f.name
    }

    data class Vzd(val pripojeniId: String, val cesta: String, val slozka: Boolean) : Polozka() {
        override val jmeno get() = cesta.trimEnd('/').substringAfterLast('/')
    }
}

object Schranka {
    var polozky by mutableStateOf<List<Polozka>>(emptyList())
        private set
    var vyjmout by mutableStateOf(false)
        private set

    fun nastav(p: List<Polozka>, vyjmout: Boolean) {
        polozky = p
        this.vyjmout = vyjmout
    }

    fun vycistit() { polozky = emptyList() }
}

object Soubory {

    fun volneJmeno(dir: File, jmeno: String): File {
        var f = File(dir, jmeno)
        if (!f.exists()) return f
        val tecka = jmeno.lastIndexOf('.')
        val zaklad = if (tecka > 0) jmeno.substring(0, tecka) else jmeno
        val pripona = if (tecka > 0) jmeno.substring(tecka) else ""
        var i = 1
        while (true) {
            f = File(dir, "$zaklad ($i)$pripona")
            if (!f.exists()) return f
            i++
        }
    }

    fun velikostStromu(f: File): Long =
        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()

    fun kopirovat(ctx: Context, zdroje: List<File>, cil: File, presun: Boolean) {
        val celkem = zdroje.sumOf { velikostStromu(it) }.coerceAtLeast(1)
        var hotovo = 0L
        val zmenene = ArrayList<File>()
        val cilSvazek = Uloziste.svazekPro(ctx, cil)?.cesta
        for (z in zdroje) {
            if (cil.absolutePath == z.absolutePath || cil.absolutePath.startsWith(z.absolutePath + "/")) {
                throw IOException("Složku nejde kopírovat do sebe sama")
            }
            if (presun && z.parentFile?.absolutePath == cil.absolutePath) continue
            val c = volneJmeno(cil, z.name)
            if (presun && cilSvazek != null && Uloziste.svazekPro(ctx, z)?.cesta == cilSvazek && z.renameTo(c)) {
                hotovo += velikostStromu(c)
                Prubeh.krok(hotovo, celkem)
                zmenene += z; zmenene += c
                continue
            }
            try {
                kopirujStrom(z, c) { n -> hotovo += n; Prubeh.krok(hotovo, celkem) }
            } catch (e: Exception) {
                c.deleteRecursively()
                throw e
            }
            if (presun) { z.deleteRecursively(); zmenene += z }
            zmenene += c
        }
        ohlasit(ctx, zmenene)
    }

    private fun kopirujStrom(z: File, c: File, pricti: (Long) -> Unit) {
        if (z.isDirectory) {
            if (!c.mkdirs() && !c.isDirectory) throw IOException("Nelze vytvořit složku ${c.name}")
            z.listFiles()?.forEach { kopirujStrom(it, File(c, it.name), pricti) }
        } else {
            z.inputStream().use { i ->
                c.outputStream().use { o ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        pricti(n.toLong())
                    }
                }
            }
            c.setLastModified(z.lastModified())
        }
    }

    private fun likeEscape(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Dá vědět galerii a ostatním aplikacím, že se soubory změnily. */
    fun ohlasit(ctx: Context, cesty: Collection<File>) {
        val seznam = ArrayList<String>()
        for (f in cesty) {
            if (f.exists()) {
                if (f.isDirectory) f.walkTopDown().filter { it.isFile }.take(5000).forEach { seznam += it.absolutePath }
                else seznam += f.absolutePath
            } else {
                seznam += f.absolutePath
                try {
                    ctx.contentResolver.delete(
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        MediaStore.MediaColumns.DATA + " LIKE ? ESCAPE '\\'",
                        arrayOf(likeEscape(f.absolutePath) + "/%")
                    )
                } catch (_: Exception) {
                }
            }
        }
        if (seznam.isNotEmpty()) MediaScannerConnection.scanFile(ctx, seznam.toTypedArray(), null, null)
    }
}

data class VKosi(
    val id: String, val jmeno: String, val puvodni: String, val smazano: Long,
    val velikost: Long, val slozka: Boolean, val kos: File
) {
    val soubor get() = File(kos, id)
}

/** Koš: na každém úložišti skrytá složka, smazané věci se do ní jen přesunou. */
object Kos {
    private const val SLOZKA = ".Kos_Spravce"
    private val zamek = Any()

    private fun kosPro(ctx: Context, f: File): File? {
        val sv = Uloziste.svazekPro(ctx, f) ?: return null
        return File(sv.cesta, SLOZKA).apply {
            if (!exists()) {
                mkdirs()
                File(this, ".nomedia").createNewFile()
            }
        }
    }

    private fun nacti(kos: File): JSONArray {
        val f = File(kos, "index.json")
        return if (f.exists()) runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray()) else JSONArray()
    }

    private fun uloz(kos: File, a: JSONArray) {
        val tmp = File(kos, "index.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(File(kos, "index.json"))
    }

    fun vyhodit(ctx: Context, soubory: List<File>) {
        val zmenene = ArrayList<File>()
        synchronized(zamek) {
            for ((i, f) in soubory.withIndex()) {
                Prubeh.krok(i.toLong(), soubory.size.toLong())
                val kos = kosPro(ctx, f)
                if (kos == null || f.absolutePath.startsWith(kos.absolutePath)) {
                    f.deleteRecursively(); zmenene += f; continue
                }
                val id = UUID.randomUUID().toString()
                val vel = Soubory.velikostStromu(f)
                val slozka = f.isDirectory
                if (!f.renameTo(File(kos, id))) throw IOException("Nepodařilo se přesunout ${f.name} do koše")
                val a = nacti(kos)
                a.put(
                    JSONObject().put("id", id).put("jmeno", f.name).put("puvodni", f.absolutePath)
                        .put("smazano", System.currentTimeMillis()).put("velikost", vel).put("slozka", slozka)
                )
                uloz(kos, a)
                zmenene += f
            }
        }
        Soubory.ohlasit(ctx, zmenene)
    }

    fun seznam(ctx: Context): List<VKosi> {
        val out = ArrayList<VKosi>()
        for (sv in Uloziste.svazky(ctx)) {
            val kos = File(sv.cesta, SLOZKA)
            if (!kos.isDirectory) continue
            val a = nacti(kos)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val p = VKosi(
                    o.getString("id"), o.getString("jmeno"), o.getString("puvodni"), o.getLong("smazano"),
                    o.optLong("velikost"), o.optBoolean("slozka"), kos
                )
                if (p.soubor.exists()) out += p
            }
        }
        return out.sortedByDescending { it.smazano }
    }

    fun velikost(ctx: Context) = seznam(ctx).sumOf { it.velikost }

    private fun odebratZaznam(p: VKosi) {
        val a = nacti(p.kos)
        val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != p.id) n.put(a.getJSONObject(i))
        uloz(p.kos, n)
    }

    fun obnovit(ctx: Context, polozky: List<VKosi>) {
        val zmenene = ArrayList<File>()
        synchronized(zamek) {
            for (p in polozky) {
                var cil = File(p.puvodni)
                cil.parentFile?.mkdirs()
                if (cil.exists()) cil = Soubory.volneJmeno(cil.parentFile!!, cil.name)
                if (!p.soubor.renameTo(cil)) throw IOException("Nepodařilo se obnovit ${p.jmeno}")
                odebratZaznam(p)
                zmenene += cil
            }
        }
        Soubory.ohlasit(ctx, zmenene)
    }

    fun smazat(polozky: List<VKosi>) {
        synchronized(zamek) {
            for ((i, p) in polozky.withIndex()) {
                Prubeh.krok(i.toLong(), polozky.size.toLong())
                p.soubor.deleteRecursively()
                odebratZaznam(p)
            }
        }
    }
}

/** Vloží obsah schránky do místní složky (z telefonu i z NAS). */
suspend fun vlozitDo(ctx: Context, cil: File): Boolean {
    val pol = Schranka.polozky
    val presun = Schranka.vyjmout
    val lok = pol.filterIsInstance<Polozka.Lok>().map { it.f }
    val vzd = pol.filterIsInstance<Polozka.Vzd>()
    val ok = sPrubehem(ctx, if (presun) "Přesouvám…" else "Kopíruji…") {
        if (lok.isNotEmpty()) Soubory.kopirovat(ctx, lok, cil, presun)
        vzd.groupBy { it.pripojeniId }.forEach { (id, seznam) ->
            val p = PripojeniUloziste.najdi(id) ?: throw IOException("Připojení k NAS už neexistuje")
            Prubeh.text = "Stahuji z NAS…"
            val nove = Nas.stahnout(ctx, p, seznam.map { it.cesta to it.slozka }, cil)
            Soubory.ohlasit(ctx, nove)
            if (presun) Nas.smazat(ctx, p, seznam.map { it.cesta to it.slozka })
        }
        true
    }
    if (ok == true && presun) Schranka.vycistit()
    return ok == true
}

/** Vloží obsah schránky do složky na NAS. */
suspend fun vlozitNaNas(ctx: Context, p: Pripojeni, cilCesta: String): Boolean {
    val pol = Schranka.polozky
    val presun = Schranka.vyjmout
    val lok = pol.filterIsInstance<Polozka.Lok>().map { it.f }
    val vzd = pol.filterIsInstance<Polozka.Vzd>()
    if (vzd.any { it.pripojeniId != p.id }) {
        zprava(ctx, "Přenos mezi dvěma různými NAS zatím neumím"); return false
    }
    if (vzd.isNotEmpty() && !presun) {
        zprava(ctx, "Kopírovat v rámci NAS zatím neumím – použij Vyjmout (přesun)"); return false
    }
    val ok = sPrubehem(ctx, if (presun) "Přesouvám na NAS…" else "Nahrávám na NAS…") {
        if (lok.isNotEmpty()) {
            Nas.nahrat(ctx, p, lok, cilCesta)
            if (presun) Kos.vyhodit(ctx, lok)
        }
        if (vzd.isNotEmpty()) Nas.presunout(ctx, p, vzd.map { it.cesta }, cilCesta)
        true
    }
    if (ok == true && presun) Schranka.vycistit()
    return ok == true
}
