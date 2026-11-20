package cz.sperlich.soubory

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Nastaveni.init(this)
        PripojeniUloziste.nacti(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}

enum class Razeni(val popis: String) {
    NAZEV("Název"), DATUM("Datum (nejnovější)"), VELIKOST("Velikost (největší)")
}

object Nastaveni {
    private lateinit var p: SharedPreferences
    private val _skryte = mutableStateOf(false)
    private val _razeni = mutableStateOf(Razeni.NAZEV)

    var skryte: Boolean
        get() = _skryte.value
        set(v) { _skryte.value = v; p.edit().putBoolean("skryte", v).apply() }

    var razeni: Razeni
        get() = _razeni.value
        set(v) { _razeni.value = v; p.edit().putString("razeni", v.name).apply() }

    fun init(ctx: Context) {
        p = ctx.getSharedPreferences("nastaveni", Context.MODE_PRIVATE)
        _skryte.value = p.getBoolean("skryte", false)
        _razeni.value = runCatching { Razeni.valueOf(p.getString("razeni", "NAZEV")!!) }.getOrDefault(Razeni.NAZEV)
    }
}

enum class Druh { SLOZKA, OBRAZEK, VIDEO, AUDIO, DOKUMENT, APK, ARCHIV, JINE }

private val CS = Locale("cs", "CZ")

fun velikost(b: Long): String {
    if (b < 1024) return "$b B"
    val jednotky = arrayOf("kB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < jednotky.size - 1) { v /= 1024; i++ }
    return String.format(CS, if (v < 10) "%.1f %s" else "%.0f %s", v, jednotky[i])
}

fun datumText(ms: Long): String = SimpleDateFormat("d. M. yyyy  H:mm", CS).format(Date(ms))

fun denKlic(ms: Long): String = SimpleDateFormat("yyyyMMdd", CS).format(Date(ms))

fun denText(ms: Long): String {
    val dnes = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    fun stejny(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (stejny(dnes, c)) return "Dnes"
    dnes.add(Calendar.DAY_OF_YEAR, -1)
    if (stejny(dnes, c)) return "Včera"
    return SimpleDateFormat("EEEE d. MMMM yyyy", CS).format(Date(ms)).replaceFirstChar { it.uppercase() }
}

fun pripona(jmeno: String) = jmeno.substringAfterLast('.', "").lowercase()

fun mimeJmena(jmeno: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(pripona(jmeno)) ?: "*/*"

private val DOKUMENTY = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "csv", "txt", "rtf", "md", "epub")
private val ARCHIVY = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

fun druhJmena(jmeno: String): Druh {
    val m = mimeJmena(jmeno)
    val e = pripona(jmeno)
    return when {
        m.startsWith("image/") -> Druh.OBRAZEK
        m.startsWith("video/") -> Druh.VIDEO
        m.startsWith("audio/") -> Druh.AUDIO
        e == "apk" -> Druh.APK
        e in ARCHIVY -> Druh.ARCHIV
        m.startsWith("text/") || e in DOKUMENTY -> Druh.DOKUMENT
        else -> Druh.JINE
    }
}

fun druhSouboru(f: File): Druh = if (f.isDirectory) Druh.SLOZKA else druhJmena(f.name)

fun jeMedium(jmeno: String) = druhJmena(jmeno).let { it == Druh.OBRAZEK || it == Druh.VIDEO }

fun zprava(ctx: Context, text: String) {
    Handler(Looper.getMainLooper()).post { Toast.makeText(ctx.applicationContext, text, Toast.LENGTH_LONG).show() }
}

fun uriPro(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".soubory", f)

fun otevrit(ctx: Context, f: File, vyber: Boolean = false) {
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriPro(ctx, f), mimeJmena(f.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    try {
        val cil = if (vyber) Intent.createChooser(i, "Otevřít v…") else i
        ctx.startActivity(cil.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        zprava(ctx, "Žádná aplikace tento soubor neumí otevřít")
    }
}

fun sdilet(ctx: Context, soubory: List<File>) {
    val uris = ArrayList(soubory.filter { it.isFile }.map { uriPro(ctx, it) })
    if (uris.isEmpty()) { zprava(ctx, "Složky sdílet nejde, jen soubory"); return }
    val i = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(mimeJmena(soubory.first { it.isFile }.name))
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
    }
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Sdílet").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
