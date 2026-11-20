package cz.sperlich.soubory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Šifrování souborů po blocích (1 MB) pomocí AES-256-GCM.
 * Formát: "TRZ1" | nonce(8) | opakovaně [délka(4) | šifrovaný blok + tag].
 * Každý blok má vlastní IV (nonce + pořadí) a ví, jestli je poslední – takže
 * nejde nepozorovaně prohodit, vynechat ani useknout žádnou část.
 */
object Krypto {
    private const val BLOK = 1 shl 20
    private val MAGIC = "TRZ1".toByteArray()
    private val nahoda = SecureRandom()

    fun nahodne(n: Int) = ByteArray(n).also { nahoda.nextBytes(it) }

    private fun iv(nonce: ByteArray, index: Int) = ByteBuffer.allocate(12).put(nonce).putInt(index).array()
    private fun aad(index: Int, posledni: Boolean) =
        ByteBuffer.allocate(5).putInt(index).put(if (posledni) 1 else 0).array()

    private fun precti(vstup: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = vstup.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    fun sifrovat(vstup: InputStream, vystup: OutputStream, klic: SecretKey, pricti: (Long) -> Unit = {}) {
        val out = DataOutputStream(BufferedOutputStream(vystup, 1 shl 16))
        val nonce = nahodne(8)
        out.write(MAGIC)
        out.write(nonce)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        var a = ByteArray(BLOK)
        var b = ByteArray(BLOK)
        var na = precti(vstup, a)
        var index = 0
        while (true) {
            val nb = if (na == BLOK) precti(vstup, b) else 0
            val posledni = nb == 0
            c.init(Cipher.ENCRYPT_MODE, klic, GCMParameterSpec(128, iv(nonce, index)))
            c.updateAAD(aad(index, posledni))
            val ct = c.doFinal(a, 0, na)
            out.writeInt(ct.size)
            out.write(ct)
            pricti(na.toLong())
            if (posledni) break
            val t = a; a = b; b = t
            na = nb
            index++
        }
        out.flush()
    }

    fun desifrovat(vstup: InputStream, vystup: OutputStream, klic: SecretKey) {
        val bis = BufferedInputStream(vstup, 1 shl 16)
        val din = DataInputStream(bis)
        val m = ByteArray(4)
        din.readFully(m)
        if (!m.contentEquals(MAGIC)) throw IOException("Poškozený soubor v trezoru")
        val nonce = ByteArray(8)
        din.readFully(nonce)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        var index = 0
        while (true) {
            val delka = din.readInt()
            if (delka < 16 || delka > BLOK + 16) throw IOException("Poškozený soubor v trezoru")
            val ct = ByteArray(delka)
            din.readFully(ct)
            bis.mark(1)
            val posledni = bis.read() < 0
            bis.reset()
            c.init(Cipher.DECRYPT_MODE, klic, GCMParameterSpec(128, iv(nonce, index)))
            c.updateAAD(aad(index, posledni))
            vystup.write(c.doFinal(ct))
            if (posledni) break
            index++
        }
        vystup.flush()
    }

    fun zasifrovatBajty(data: ByteArray, klic: SecretKey): ByteArray =
        ByteArrayOutputStream().also { sifrovat(ByteArrayInputStream(data), it, klic) }.toByteArray()

    fun desifrovatBajty(data: ByteArray, klic: SecretKey): ByteArray =
        ByteArrayOutputStream().also { desifrovat(ByteArrayInputStream(data), it, klic) }.toByteArray()
}

data class VTrezoru(
    val id: String, val jmeno: String, val velikost: Long, val pridano: Long,
    val puvodni: String, val nahled: Boolean
) {
    val druh get() = druhJmena(jmeno)
}

/** Něco, co se má zašifrovat do trezoru (soubor z telefonu nebo vybraný dokument). */
class Zdroj(
    val jmeno: String,
    val delka: Long,
    val puvodni: String,
    val otevrit: () -> InputStream,
    val nahled: () -> Bitmap?,
    val smazat: () -> Boolean,
)

object Trezor {
    private const val ITERACE = 150_000
    private const val BIO = "trezor_otisk"

    /** Trezor je na úložišti telefonu (ne v datech aplikace), takže přežije přeinstalaci
     *  a FolderSync ho může zálohovat na NAS – je zašifrovaný. */
    val slozka: File get() = File(Environment.getExternalStorageDirectory(), ".TrezorSpravce")
    private val data get() = File(slozka, "data")
    private val nahledy get() = File(slozka, "nahledy")
    private val indexSoubor get() = File(slozka, "index.bin")
    private val klicSoubor get() = File(slozka, "klic.json")

    @Volatile private var klic: SecretKey? = null
    var odemceno by mutableStateOf(false)
        private set
    var polozky by mutableStateOf<List<VTrezoru>>(emptyList())
        private set

    /** Soubory, které uživatel poslal do trezoru, když byl zamčený. */
    var cekajici by mutableStateOf<List<File>>(emptyList())

    /** Když otevíráme soubor z trezoru v jiné aplikaci, nezamykat hned. */
    @Volatile var nezamykat = false

    private val cache = LruCache<String, Bitmap>(300)
    private val zamek = Any()

    fun existuje() = klicSoubor.exists()

    private fun pinKlic(pin: String, salt: ByteArray, iter: Int): SecretKey {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(pin.toCharArray(), salt, iter, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun zB64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun ulozKlic(pin: String, master: ByteArray) {
        val salt = Krypto.nahodne(16)
        val iv = Krypto.nahodne(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, pinKlic(pin, salt, ITERACE), GCMParameterSpec(128, iv))
        val zabaleny = c.doFinal(master)
        val o = JSONObject().put("verze", 1).put("salt", b64(salt)).put("iter", ITERACE)
            .put("iv", b64(iv)).put("klic", b64(zabaleny))
        val tmp = File(slozka, "klic.json.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(klicSoubor)) throw IOException("Nepodařilo se uložit klíč trezoru")
    }

    fun zalozit(ctx: Context, pin: String) {
        synchronized(zamek) {
            data.mkdirs()
            nahledy.mkdirs()
            File(slozka, ".nomedia").createNewFile()
            bioVypnout(ctx)
            val master = Krypto.nahodne(32)
            ulozKlic(pin, master)
            klic = SecretKeySpec(master, "AES")
            polozky = emptyList()
            ulozIndex()
            odemceno = true
        }
    }

    fun odemknoutPinem(pin: String): Boolean {
        val o = JSONObject(klicSoubor.readText())
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(
            Cipher.DECRYPT_MODE,
            pinKlic(pin, zB64(o.getString("salt")), o.getInt("iter")),
            GCMParameterSpec(128, zB64(o.getString("iv")))
        )
        val master = try {
            c.doFinal(zB64(o.getString("klic")))
        } catch (e: AEADBadTagException) {
            return false
        }
        nastav(master)
        return true
    }

    fun zmenitPin(novy: String) {
        val k = klic ?: throw IOException("Trezor je zamčený")
        synchronized(zamek) { ulozKlic(novy, k.encoded) }
    }

    private fun nastav(master: ByteArray) {
        synchronized(zamek) {
            klic = SecretKeySpec(master, "AES")
            data.mkdirs(); nahledy.mkdirs()
            polozky = nactiIndex()
            odemceno = true
        }
    }

    fun zamknout() {
        klic = null
        odemceno = false
        polozky = emptyList()
        cache.evictAll()
    }

    fun vycistitDocasne(ctx: Context) {
        File(ctx.cacheDir, "trezor").deleteRecursively()
        File(ctx.cacheDir, "nas").deleteRecursively()
    }

    private fun nactiIndex(): List<VTrezoru> {
        val k = klic ?: return emptyList()
        if (!indexSoubor.exists()) return emptyList()
        val a = JSONArray(String(Krypto.desifrovatBajty(indexSoubor.readBytes(), k)))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            VTrezoru(
                o.getString("id"), o.getString("jmeno"), o.getLong("velikost"), o.getLong("pridano"),
                o.optString("puvodni"), o.optBoolean("nahled")
            )
        }
    }

    private fun ulozIndex() {
        val k = klic ?: return
        val a = JSONArray()
        polozky.forEach {
            a.put(
                JSONObject().put("id", it.id).put("jmeno", it.jmeno).put("velikost", it.velikost)
                    .put("pridano", it.pridano).put("puvodni", it.puvodni).put("nahled", it.nahled)
            )
        }
        val tmp = File(slozka, "index.bin.tmp")
        tmp.writeBytes(Krypto.zasifrovatBajty(a.toString().toByteArray(), k))
        if (!tmp.renameTo(indexSoubor)) throw IOException("Nepodařilo se uložit seznam trezoru")
    }

    // ---------- otisk prstu ----------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("trezor", Context.MODE_PRIVATE)
    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun bioZapnuto(ctx: Context) = prefs(ctx).contains("bio_ct")

    /** Připraví šifru, kterou odemkne otisk prstu (pro zapnutí funkce). */
    fun bioSifraZapnout(): Cipher {
        val ks = keystore()
        if (ks.containsAlias(BIO)) ks.deleteEntry(BIO)
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(BIO, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, g.generateKey())
        return c
    }

    fun bioDokoncit(ctx: Context, c: Cipher) {
        val k = klic ?: return
        val ct = c.doFinal(k.encoded)
        prefs(ctx).edit().putString("bio_iv", b64(c.iv)).putString("bio_ct", b64(ct)).apply()
    }

    fun bioSifraOdemknout(ctx: Context): Cipher? {
        val iv = prefs(ctx).getString("bio_iv", null) ?: return null
        return try {
            val k = keystore().getKey(BIO, null) as? SecretKey ?: return null
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, zB64(iv))) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            bioVypnout(ctx)
            null
        } catch (e: Exception) {
            null
        }
    }

    fun bioOdemknout(ctx: Context, c: Cipher) {
        val ct = prefs(ctx).getString("bio_ct", null) ?: throw IOException("Otisk není nastavený")
        nastav(c.doFinal(zB64(ct)))
    }

    fun bioVypnout(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        runCatching { keystore().deleteEntry(BIO) }
    }

    // ---------- práce se soubory ----------

    fun zdrojeZeSouboru(soubory: List<File>): List<Zdroj> =
        soubory.flatMap { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.toList() else listOf(f) }
            .map { f ->
                Zdroj(
                    jmeno = f.name, delka = f.length(), puvodni = f.absolutePath,
                    otevrit = { FileInputStream(f) },
                    nahled = {
                        when (druhJmena(f.name)) {
                            Druh.OBRAZEK -> ThumbnailUtils.createImageThumbnail(f, Size(320, 320), null)
                            Druh.VIDEO -> ThumbnailUtils.createVideoThumbnail(f, Size(320, 320), null)
                            else -> null
                        }
                    },
                    smazat = { f.delete() }
                )
            }

    fun zdrojZUri(ctx: Context, uri: Uri): Zdroj {
        val cr = ctx.contentResolver
        var jmeno = "soubor"
        var delka = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { jmeno = it }
                if (!c.isNull(1)) delka = c.getLong(1)
            }
        }
        return Zdroj(
            jmeno = jmeno, delka = delka, puvodni = "",
            otevrit = { cr.openInputStream(uri) ?: throw IOException("Soubor nejde otevřít") },
            nahled = { if (jeMedium(jmeno)) cr.loadThumbnail(uri, Size(320, 320), null) else null },
            smazat = { runCatching { DocumentsContract.deleteDocument(cr, uri) }.getOrDefault(false) }
        )
    }

    /** Zašifruje zdroje do trezoru a originály smaže. Vrací počet originálů, které smazat nešlo. */
    fun pridat(zdroje: List<Zdroj>): Int {
        val k = klic ?: throw IOException("Trezor je zamčený")
        val celkem = zdroje.sumOf { it.delka.coerceAtLeast(0) }.coerceAtLeast(1)
        var hotovo = 0L
        var nesmazano = 0
        data.mkdirs(); nahledy.mkdirs()
        for (z in zdroje) {
            if (Prubeh.zruseno) throw Zruseno()
            val id = UUID.randomUUID().toString()
            val cil = File(data, "$id.bin")
            var delka = 0L
            try {
                z.otevrit().use { i ->
                    FileOutputStream(cil).use { o ->
                        Krypto.sifrovat(i, o, k) { delka += it; hotovo += it; Prubeh.krok(hotovo, celkem) }
                        o.fd.sync()
                    }
                }
            } catch (e: Exception) {
                cil.delete()
                throw e
            }
            var maNahled = false
            runCatching { z.nahled() }.getOrNull()?.let { bmp ->
                val bo = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, bo)
                File(nahledy, "$id.bin").writeBytes(Krypto.zasifrovatBajty(bo.toByteArray(), k))
                maNahled = true
            }
            synchronized(zamek) {
                polozky = polozky + VTrezoru(id, z.jmeno, delka, System.currentTimeMillis(), z.puvodni, maNahled)
                ulozIndex()
            }
            if (!runCatching { z.smazat() }.getOrDefault(false)) nesmazano++
        }
        return nesmazano
    }

    private fun desifrovatDo(p: VTrezoru, cil: File) {
        val k = klic ?: throw IOException("Trezor je zamčený")
        try {
            FileInputStream(File(data, "${p.id}.bin")).use { i -> FileOutputStream(cil).use { o -> Krypto.desifrovat(i, o, k) } }
        } catch (e: Exception) {
            cil.delete()
            throw e
        }
    }

    /** Vrátí soubory z trezoru zpět (na původní místo, jinak do Stažené/Z trezoru). */
    fun vratit(ctx: Context, vyber: List<VTrezoru>): List<File> {
        val vysledky = ArrayList<File>()
        for ((i, p) in vyber.withIndex()) {
            Prubeh.krok(i.toLong(), vyber.size.toLong())
            val puvodni = if (p.puvodni.isNotBlank()) File(p.puvodni) else null
            var cil = if (puvodni != null && (puvodni.parentFile?.let { it.isDirectory || it.mkdirs() } == true)) puvodni
            else File(Uloziste.stazene(), "Z trezoru/${p.jmeno}").also { it.parentFile?.mkdirs() }
            if (cil.exists()) cil = Soubory.volneJmeno(cil.parentFile!!, cil.name)
            desifrovatDo(p, cil)
            odstranit(p)
            vysledky += cil
        }
        Soubory.ohlasit(ctx, vysledky)
        return vysledky
    }

    fun smazat(vyber: List<VTrezoru>) = vyber.forEach { odstranit(it) }

    private fun odstranit(p: VTrezoru) {
        File(data, "${p.id}.bin").delete()
        File(nahledy, "${p.id}.bin").delete()
        cache.remove(p.id)
        synchronized(zamek) {
            polozky = polozky.filter { it.id != p.id }
            ulozIndex()
        }
    }

    fun nahled(p: VTrezoru): Bitmap? {
        cache.get(p.id)?.let { return it }
        if (!p.nahled) return null
        val k = klic ?: return null
        val b = runCatching { Krypto.desifrovatBajty(File(nahledy, "${p.id}.bin").readBytes(), k) }.getOrNull() ?: return null
        val bmp = BitmapFactory.decodeByteArray(b, 0, b.size) ?: return null
        cache.put(p.id, bmp)
        return bmp
    }

    /** Dešifruje fotku do paměti (zmenšenou na max. 2560 px, správně otočenou). */
    fun obrazek(p: VTrezoru): Bitmap? {
        val k = klic ?: return null
        val bo = ByteArrayOutputStream()
        FileInputStream(File(data, "${p.id}.bin")).use { Krypto.desifrovat(it, bo, k) }
        val src = ImageDecoder.createSource(ByteBuffer.wrap(bo.toByteArray()))
        return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            var s = 1
            while (maxOf(info.size.width, info.size.height) / s > 2560) s *= 2
            dec.setTargetSampleSize(s)
        }
    }

    /** Dešifruje soubor do dočasné složky, aby ho šlo otevřít v jiné aplikaci. */
    fun docasny(ctx: Context, p: VTrezoru): File {
        val d = File(ctx.cacheDir, "trezor/${p.id}").apply { mkdirs() }
        val f = File(d, p.jmeno)
        if (!f.exists()) desifrovatDo(p, f)
        return f
    }
}
