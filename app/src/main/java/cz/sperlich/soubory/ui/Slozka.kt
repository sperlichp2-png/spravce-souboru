package cz.sperlich.soubory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cz.sperlich.soubory.Druh
import cz.sperlich.soubory.Nastaveni
import cz.sperlich.soubory.Nav
import cz.sperlich.soubory.Obr
import cz.sperlich.soubory.Razeni
import cz.sperlich.soubory.Soubory
import cz.sperlich.soubory.datumText
import cz.sperlich.soubory.druhSouboru
import cz.sperlich.soubory.jeMedium
import cz.sperlich.soubory.otevrit
import cz.sperlich.soubory.velikost
import cz.sperlich.soubory.vlozitDo
import cz.sperlich.soubory.zprava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Radek(val f: File, val slozka: Boolean, val velikost: Long, val zmeneno: Long, val pocet: Int, val druh: Druh)

private fun nactiSlozku(dir: File, skryte: Boolean, razeni: Razeni): List<Radek> {
    val r = (dir.listFiles() ?: emptyArray())
        .filter { skryte || !it.name.startsWith(".") }
        .map {
            val d = it.isDirectory
            Radek(
                it, d, if (d) 0 else it.length(), it.lastModified(),
                if (d) it.list()?.count { n -> skryte || !n.startsWith(".") } ?: 0 else 0,
                druhSouboru(it)
            )
        }
    val cmp = when (razeni) {
        Razeni.NAZEV -> compareBy<Radek> { !it.slozka }.thenBy { it.f.name.lowercase() }
        Razeni.DATUM -> compareBy<Radek> { !it.slozka }.thenByDescending { it.zmeneno }
        Razeni.VELIKOST -> compareBy<Radek> { !it.slozka }.thenByDescending { it.velikost }.thenBy { it.f.name.lowercase() }
    }
    return r.sortedWith(cmp)
}

@Composable
fun SlozkaObr(o: Obr.Slozka) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var obnova by remember { mutableIntStateOf(0) }
    val skryte = Nastaveni.skryte
    val razeni = Nastaveni.razeni
    val radky by produceState<List<Radek>?>(null, o.dir, obnova, skryte, razeni) {
        value = withContext(Dispatchers.IO) { nactiSlozku(o.dir, skryte, razeni) }
    }
    var dialog by remember { mutableStateOf<String?>(null) }
    val vybrane = o.vybrane

    LaunchedEffect(o.dir) {
        if (o.posledniDir != null && o.posledniDir != o.dir) o.stav.scrollToItem(0)
        o.posledniDir = o.dir
    }
    BackHandler(vybrane.isNotEmpty() || o.dir != o.koren) {
        if (vybrane.isNotEmpty()) vybrane.clear()
        else o.dir = o.dir.parentFile ?: o.koren
    }
    fun prepni(f: File) { if (f in vybrane) vybrane.remove(f) else vybrane.add(f) }
    val hotovo: () -> Unit = { vybrane.clear(); obnova++ }

    val relativni = o.dir.absolutePath.removePrefix(o.koren.absolutePath)
    Scaffold(
        topBar = {
            if (vybrane.isEmpty()) Lista(
                if (o.dir == o.koren) o.nazev else o.dir.name,
                o.nazev + relativni.ifEmpty { "" }
            ) {
                RazeniMenu()
                IconButton(onClick = { dialog = "nova" }) { Icon(Icons.Filled.CreateNewFolder, "Nová složka") }
            } else VyberLista(vybrane.size, { vybrane.clear() }, { vybrane.clear(); radky?.let { r -> vybrane.addAll(r.map { it.f }) } }) {
                LokalniAkce(vybrane.toList(), hotovo, prejmenovat = { dialog = "prejmenovat" })
            }
        },
        bottomBar = {
            SchrankaLista { scope.launch { vlozitDo(ctx, o.dir); obnova++ } }
        }
    ) { pad ->
        val r = radky
        when {
            r == null -> Nacitani(pad)
            r.isEmpty() -> Prazdne(pad, "Prázdná složka")
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize(), state = o.stav) {
                items(r, key = { it.f.absolutePath }) { radek ->
                    RadekSouboru(
                        nazev = radek.f.name,
                        popis = if (radek.slozka) "${radek.pocet} položek • ${datumText(radek.zmeneno)}"
                        else "${velikost(radek.velikost)} • ${datumText(radek.zmeneno)}",
                        vybrano = radek.f in vybrane,
                        klik = {
                            when {
                                vybrane.isNotEmpty() -> prepni(radek.f)
                                radek.slozka -> o.dir = radek.f
                                jeMedium(radek.f.name) -> {
                                    val media = r.filter { !it.slozka && jeMedium(it.f.name) }.map { it.f }
                                    Nav.jdi(Obr.Prohlizec(media, media.indexOf(radek.f)))
                                }
                                else -> otevrit(ctx, radek.f)
                            }
                        },
                        dlouze = { prepni(radek.f) }
                    ) { NahledSouboru(radek.f, radek.druh) }
                }
            }
        }
    }

    when (dialog) {
        "nova" -> TextDialog("Nová složka", "", ok = { jmeno ->
            dialog = null
            scope.launch {
                val ok = withContext(Dispatchers.IO) { File(o.dir, jmeno).mkdir() }
                if (!ok) zprava(ctx, "Složku nejde vytvořit") else obnova++
            }
        }, zrusit = { dialog = null })

        "prejmenovat" -> {
            val f = vybrane.firstOrNull()
            if (f == null) dialog = null
            else TextDialog("Přejmenovat", f.name, ok = { jmeno ->
                dialog = null
                scope.launch {
                    val chyba = withContext(Dispatchers.IO) {
                        val c = File(f.parentFile, jmeno)
                        when {
                            c.exists() -> "Tohle jméno už existuje"
                            !f.renameTo(c) -> "Přejmenování se nepodařilo"
                            else -> { Soubory.ohlasit(ctx, listOf(f, c)); null }
                        }
                    }
                    if (chyba != null) zprava(ctx, chyba)
                    hotovo()
                }
            }, zrusit = { dialog = null })
        }
    }
}
