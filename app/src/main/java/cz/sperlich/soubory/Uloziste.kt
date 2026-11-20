package cz.sperlich.soubory

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File

data class Svazek(val nazev: String, val cesta: File, val celkem: Long, val volno: Long, val vymenny: Boolean)

data class MSoubor(val soubor: File, val nazev: String, val velikost: Long, val pridano: Long, val mime: String?)

data class Aplikace(
    val nazev: String, val balicek: String, val apk: File, val velikost: Long, val ikona: ImageBitmap?
)

object Uloziste {

    fun svazky(ctx: Context): List<Svazek> {
        val sm = ctx.getSystemService(StorageManager::class.java)
        return sm.storageVolumes.mapNotNull { v ->
            val d = v.directory ?: return@mapNotNull null
            if (!d.canRead()) return@mapNotNull null
            val st = runCatching { StatFs(d.path) }.getOrNull() ?: return@mapNotNull null
            val nazev = when {
                v.isPrimary -> "Hlavní úložiště"
                v.isRemovable -> "SD karta"
                else -> v.getDescription(ctx)
            }
            Svazek(nazev, d, st.totalBytes, st.availableBytes, v.isRemovable)
        }
    }

    fun svazekPro(ctx: Context, f: File): Svazek? {
        val p = f.absolutePath
        return svazky(ctx).firstOrNull { p == it.cesta.absolutePath || p.startsWith(it.cesta.absolutePath + "/") }
    }

    fun stazene(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** Dotaz do systémového katalogu souborů (MediaStore), seřazeno od nejnovějšího. */
    fun media(ctx: Context, vyber: String?, args: Array<String>?, limit: Int = 0): List<MSoubor> {
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val sloupce = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val b = Bundle().apply {
            if (vyber != null) putString(ContentResolver.QUERY_ARG_SQL_SELECTION, vyber)
            if (args != null) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaStore.MediaColumns.DATE_ADDED + " DESC")
            if (limit > 0) putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val vysledek = ArrayList<MSoubor>()
        ctx.contentResolver.query(uri, sloupce, b, null)?.use { c ->
            while (c.moveToNext()) {
                val cesta = c.getString(0) ?: continue
                if (cesta.contains("/.")) continue
                val f = File(cesta)
                if (!f.isFile) continue
                vysledek += MSoubor(f, c.getString(1) ?: f.name, c.getLong(2), c.getLong(3) * 1000L, c.getString(4))
            }
        }
        return vysledek
    }

    fun posledniFotky(ctx: Context) = media(ctx, "media_type IN (1,3)", null, 2000)
    fun obrazky(ctx: Context) = media(ctx, "media_type = 1", null)
    fun videa(ctx: Context) = media(ctx, "media_type = 3", null)
    fun audio(ctx: Context) = media(ctx, "media_type = 2", null)
    fun dokumenty(ctx: Context) = media(
        ctx,
        "(mime_type LIKE 'application/%' OR mime_type LIKE 'text/%') AND mime_type != 'application/vnd.android.package-archive'",
        null
    )

    fun posledniSoubory(ctx: Context, dni: Int = 30): List<MSoubor> {
        val od = System.currentTimeMillis() / 1000 - dni * 86400L
        return media(ctx, "date_added >= ? AND mime_type IS NOT NULL", arrayOf(od.toString()), 1500)
    }

    fun obsahSlozky(d: File): Pair<Long, Int> {
        var vel = 0L
        var pocet = 0
        d.walkTopDown().filter { it.isFile }.forEach { vel += it.length(); pocet++ }
        return vel to pocet
    }

    fun aplikace(ctx: Context, sIkonami: Boolean): List<Aplikace> {
        val pm = ctx.packageManager
        return pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { ai ->
                val apk = File(ai.sourceDir)
                val vel = apk.length() + (ai.splitSourceDirs?.sumOf { File(it).length() } ?: 0L)
                Aplikace(
                    nazev = ai.loadLabel(pm).toString(),
                    balicek = ai.packageName,
                    apk = apk,
                    velikost = vel,
                    ikona = if (sIkonami) runCatching { ai.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull() else null
                )
            }
            .sortedBy { it.nazev.lowercase() }
    }
}
