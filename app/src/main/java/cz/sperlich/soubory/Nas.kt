package cz.sperlich.soubory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpProgressMonitor
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Pripojeni(
    val id: String, val nazev: String, val host: String, val port: Int,
    val uzivatel: String, val heslo: String, val slozka: String
)

data class VzdSoubor(val jmeno: String, val cesta: String, val slozka: Boolean, val velikost: Long, val zmeneno: Long)

/** Šifrování hesel klíčem uloženým v čipu telefonu (Android Keystore). */
object Klicenka {
    private const val ALIAS = "hesla_nas"

    private fun klic(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    fun zasifruj(s: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, klic())
        return Base64.encodeToString(c.iv + c.doFinal(s.toByteArray()), Base64.NO_WRAP)
    }

    fun desifruj(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, klic(), GCMParameterSpec(128, b, 0, 12))
        return String(c.doFinal(b, 12, b.size - 12))
    }
}

object PripojeniUloziste {
    var seznam by mutableStateOf<List<Pripojeni>>(emptyList())
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nas", Context.MODE_PRIVATE)

    fun nacti(ctx: Context) {
        val a = runCatching { JSONArray(prefs(ctx).getString("seznam", "[]")) }.getOrDefault(JSONArray())
        seznam = (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Pripojeni(
                o.getString("id"), o.getString("nazev"), o.getString("host"), o.getInt("port"),
                o.getString("uzivatel"),
                runCatching { Klicenka.desifruj(o.getString("heslo")) }.getOrDefault(""),
                o.optString("slozka")
            )
        }
    }

    private fun uloz(ctx: Context, s: List<Pripojeni>) {
        val a = JSONArray()
        s.forEach {
            a.put(
                JSONObject().put("id", it.id).put("nazev", it.nazev).put("host", it.host).put("port", it.port)
                    .put("uzivatel", it.uzivatel).put("heslo", Klicenka.zasifruj(it.heslo)).put("slozka", it.slozka)
            )
        }
        prefs(ctx).edit().putString("seznam", a.toString()).apply()
        seznam = s
    }

    fun ulozit(ctx: Context, p: Pripojeni) {
        Nas.odpojit(p.id)
        uloz(ctx, if (seznam.any { it.id == p.id }) seznam.map { if (it.id == p.id) p else it } else seznam + p)
    }

    fun odebrat(ctx: Context, id: String) {
        Nas.odpojit(id)
        uloz(ctx, seznam.filter { it.id != id })
    }

    fun najdi(id: String) = seznam.firstOrNull { it.id == id }
}

/** SFTP klient (Raspberry Pi s OpenMediaVault). */
object Nas {
    private val kanaly = HashMap<String, ChannelSftp>()

    private class Info(val heslo: String) : UserInfo, UIKeyboardInteractive {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String = heslo
        override fun promptPassword(message: String?): Boolean = true
        override fun promptPassphrase(message: String?): Boolean = false
        // Nový server přijmeme a zapamatujeme si jeho klíč; změněný klíč odmítneme.
        override fun promptYesNo(message: String?): Boolean = message?.contains("CHANGED") != true
        override fun showMessage(message: String?) {}
        override fun promptKeyboardInteractive(
            destination: String?, name: String?, instruction: String?,
            prompt: Array<out String>?, echo: BooleanArray?
        ): Array<String> = Array(prompt?.size ?: 0) { heslo }
    }

    private class Monitor(val pricti: (Long) -> Unit) : SftpProgressMonitor {
        override fun init(op: Int, src: String?, dest: String?, max: Long) {}
        override fun count(count: Long): Boolean {
            pricti(count)
            return !Prubeh.zruseno
        }
        override fun end() {}
    }

    private fun knownHosts(ctx: Context) = File(ctx.filesDir, "known_hosts")

    fun zapomenoutKlice(ctx: Context) {
        synchronized(this) {
            kanaly.keys.toList().forEach { odpojit(it) }
            knownHosts(ctx).delete()
        }
    }

    private fun kanal(ctx: Context, p: Pripojeni): ChannelSftp {
        kanaly[p.id]?.let { if (it.isConnected && it.session.isConnected) return it }
        odpojit(p.id)
        val kh = knownHosts(ctx)
        if (!kh.exists()) kh.createNewFile()
        val j = JSch()
        j.setKnownHosts(kh.absolutePath)
        val s = j.getSession(p.uzivatel, p.host, p.port)
        s.setPassword(p.heslo)
        s.userInfo = Info(p.heslo)
        s.setConfig("StrictHostKeyChecking", "ask")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.setServerAliveInterval(20000)
        s.connect(15000)
        val ch = s.openChannel("sftp") as ChannelSftp
        ch.connect(15000)
        kanaly[p.id] = ch
        return ch
    }

    fun odpojit(id: String) {
        synchronized(this) {
            kanaly.remove(id)?.let { ch ->
                runCatching { ch.disconnect() }
                runCatching { ch.session.disconnect() }
            }
        }
    }

    private fun <T> s(ctx: Context, p: Pripojeni, blok: (ChannelSftp) -> T): T = synchronized(this) {
        val ch = kanal(ctx, p)
        try {
            blok(ch)
        } catch (e: Zruseno) {
            throw e
        } catch (e: Exception) {
            val spojeno = runCatching { ch.isConnected && ch.session.isConnected }.getOrDefault(false)
            if (spojeno) throw e
            odpojit(p.id)
            blok(kanal(ctx, p))
        }
    }

    fun spoj(a: String, b: String) = if (a.endsWith("/")) a + b else "$a/$b"
    fun rodic(c: String): String = c.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

    fun domov(ctx: Context, p: Pripojeni): String =
        s(ctx, p) { ch -> if (p.slozka.isNotBlank()) p.slozka.trim() else ch.pwd() }

    private fun polozky(ch: ChannelSftp, cesta: String): List<ChannelSftp.LsEntry> {
        val out = ArrayList<ChannelSftp.LsEntry>()
        for (o in ch.ls(cesta)) {
            val e = o as ChannelSftp.LsEntry
            if (e.filename != "." && e.filename != "..") out += e
        }
        return out
    }

    fun seznam(ctx: Context, p: Pripojeni, cesta: String): List<VzdSoubor> = s(ctx, p) { ch ->
        polozky(ch, cesta)
            .filter { Nastaveni.skryte || !it.filename.startsWith(".") }
            .map { e ->
                val c = spoj(cesta, e.filename)
                var dir = e.attrs.isDir
                if (e.attrs.isLink) dir = runCatching { ch.stat(c).isDir }.getOrDefault(false)
                VzdSoubor(e.filename, c, dir, e.attrs.size, e.attrs.mTime * 1000L)
            }
            .sortedWith(compareBy<VzdSoubor> { !it.slozka }.thenBy { it.jmeno.lowercase() })
    }

    private fun velikostVzd(ch: ChannelSftp, cesta: String, slozka: Boolean): Long =
        if (!slozka) runCatching { ch.stat(cesta).size }.getOrDefault(0L)
        else polozky(ch, cesta).sumOf { velikostVzd(ch, spoj(cesta, it.filename), it.attrs.isDir) }

    fun stahnout(ctx: Context, p: Pripojeni, zdroje: List<Pair<String, Boolean>>, cil: File): List<File> = s(ctx, p) { ch ->
        val celkem = zdroje.sumOf { velikostVzd(ch, it.first, it.second) }.coerceAtLeast(1)
        var hotovo = 0L
        val vysledky = ArrayList<File>()
        for ((c, dir) in zdroje) {
            val l = Soubory.volneJmeno(cil, c.trimEnd('/').substringAfterLast('/'))
            try {
                stahniStrom(ch, c, dir, l) { hotovo += it; Prubeh.krok(hotovo, celkem) }
            } catch (e: Exception) {
                l.deleteRecursively(); throw e
            }
            vysledky += l
        }
        vysledky
    }

    private fun stahniStrom(ch: ChannelSftp, c: String, dir: Boolean, l: File, pricti: (Long) -> Unit) {
        if (dir) {
            l.mkdirs()
            for (e in polozky(ch, c)) stahniStrom(ch, spoj(c, e.filename), e.attrs.isDir, File(l, e.filename), pricti)
        } else {
            l.outputStream().use { out -> ch.get(c, out, Monitor(pricti)) }
            if (Prubeh.zruseno) throw Zruseno()
        }
    }

    /** Stáhne soubor do mezipaměti, aby šel otevřít. */
    fun stahnoutDocasne(ctx: Context, p: Pripojeni, cesta: String): File = s(ctx, p) { ch ->
        val celkem = runCatching { ch.stat(cesta).size }.getOrDefault(0L)
        val d = File(ctx.cacheDir, "nas").apply { mkdirs() }
        val f = File(d, cesta.substringAfterLast('/'))
        var hotovo = 0L
        try {
            f.outputStream().use { out -> ch.get(cesta, out, Monitor { hotovo += it; Prubeh.krok(hotovo, celkem) }) }
            if (Prubeh.zruseno) throw Zruseno()
        } catch (e: Exception) {
            f.delete(); throw e
        }
        f
    }

    private fun existuje(ch: ChannelSftp, c: String) = runCatching { ch.lstat(c) }.isSuccess

    private fun volneVzd(ch: ChannelSftp, dir: String, jmeno: String): String {
        var c = spoj(dir, jmeno)
        if (!existuje(ch, c)) return c
        val tecka = jmeno.lastIndexOf('.')
        val zaklad = if (tecka > 0) jmeno.substring(0, tecka) else jmeno
        val pripona = if (tecka > 0) jmeno.substring(tecka) else ""
        var i = 1
        while (true) {
            c = spoj(dir, "$zaklad ($i)$pripona")
            if (!existuje(ch, c)) return c
            i++
        }
    }

    fun nahrat(ctx: Context, p: Pripojeni, soubory: List<File>, cilCesta: String) = s(ctx, p) { ch ->
        val celkem = soubory.sumOf { Soubory.velikostStromu(it) }.coerceAtLeast(1)
        var hotovo = 0L
        for (f in soubory) {
            nahrajStrom(ch, f, volneVzd(ch, cilCesta, f.name)) { hotovo += it; Prubeh.krok(hotovo, celkem) }
        }
    }

    private fun nahrajStrom(ch: ChannelSftp, f: File, c: String, pricti: (Long) -> Unit) {
        if (f.isDirectory) {
            ch.mkdir(c)
            f.listFiles()?.forEach { nahrajStrom(ch, it, spoj(c, it.name), pricti) }
        } else {
            f.inputStream().use { ch.put(it, c, Monitor(pricti), ChannelSftp.OVERWRITE) }
            if (Prubeh.zruseno) {
                runCatching { ch.rm(c) }
                throw Zruseno()
            }
        }
    }

    fun smazat(ctx: Context, p: Pripojeni, zdroje: List<Pair<String, Boolean>>) = s(ctx, p) { ch ->
        zdroje.forEach { (c, dir) -> smazStrom(ch, c, dir) }
    }

    private fun smazStrom(ch: ChannelSftp, c: String, dir: Boolean) {
        if (dir) {
            for (e in polozky(ch, c)) smazStrom(ch, spoj(c, e.filename), e.attrs.isDir)
            ch.rmdir(c)
        } else ch.rm(c)
    }

    fun prejmenovat(ctx: Context, p: Pripojeni, stara: String, noveJmeno: String) = s(ctx, p) { ch ->
        ch.rename(stara, spoj(rodic(stara), noveJmeno))
    }

    fun novaSlozka(ctx: Context, p: Pripojeni, dir: String, jmeno: String) = s(ctx, p) { ch ->
        ch.mkdir(spoj(dir, jmeno))
    }

    fun presunout(ctx: Context, p: Pripojeni, cesty: List<String>, cil: String) = s(ctx, p) { ch ->
        cesty.forEach { c ->
            if (rodic(c) != cil.trimEnd('/').ifEmpty { "/" }) ch.rename(c, volneVzd(ch, cil, c.trimEnd('/').substringAfterLast('/')))
        }
    }
}
