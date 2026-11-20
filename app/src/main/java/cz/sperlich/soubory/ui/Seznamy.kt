package cz.sperlich.soubory.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.sperlich.soubory.Aplikace
import cz.sperlich.soubory.Druh
import cz.sperlich.soubory.Kos
import cz.sperlich.soubory.MSoubor
import cz.sperlich.soubory.Nav
import cz.sperlich.soubory.Obr
import cz.sperlich.soubory.Soubory
import cz.sperlich.soubory.Uloziste
import cz.sperlich.soubory.VKosi
import cz.sperlich.soubory.datumText
import cz.sperlich.soubory.denKlic
import cz.sperlich.soubory.denText
import cz.sperlich.soubory.druhJmena
import cz.sperlich.soubory.jeMedium
import cz.sperlich.soubory.otevrit
import cz.sperlich.soubory.sPrubehem
import cz.sperlich.soubory.velikost
import cz.sperlich.soubory.zprava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SeznamDruh { POSLEDNI, AUDIO, DOKUMENTY }

@Composable
fun SeznamObr(o: Obr.Seznam) {
    val ctx = LocalContext.current
    var obnova by remember { mutableIntStateOf(0) }
    val data by produceState<List<MSoubor>?>(null, obnova) {
        value = withContext(Dispatchers.IO) {
            when (o.druh) {
                SeznamDruh.POSLEDNI -> Uloziste.posledniSoubory(ctx)
                SeznamDruh.AUDIO -> Uloziste.audio(ctx)
                SeznamDruh.DOKUMENTY -> Uloziste.dokumenty(ctx)
            }
        }
    }
    val vybrane = o.vybrane
    BackHandler(vybrane.isNotEmpty()) { vybrane.clear() }
    fun prepni(f: File) { if (f in vybrane) vybrane.remove(f) else vybrane.add(f) }

    Scaffold(topBar = {
        if (vybrane.isEmpty()) Lista(o.titulek, data?.let { "${it.size} souborů" })
        else VyberLista(vybrane.size, { vybrane.clear() }, { vybrane.clear(); data?.let { d -> vybrane.addAll(d.map { it.soubor }) } }) {
            LokalniAkce(vybrane.toList(), { vybrane.clear(); obnova++ })
        }
    }) { pad ->
        val d = data
        when {
            d == null -> Nacitani(pad)
            d.isEmpty() -> Prazdne(pad, "Nic tu není")
            else -> {
                val media = remember(d) { d.map { it.soubor }.filter { jeMedium(it.name) } }
                val skupiny = remember(d) {
                    if (o.druh == SeznamDruh.POSLEDNI) d.groupBy { denKlic(it.pridano) } else mapOf("" to d)
                }
                LazyColumn(Modifier.padding(pad).fillMaxSize(), state = o.stav) {
                    skupiny.forEach { (den, polozky) ->
                        if (den.isNotEmpty()) item(key = "den-$den") {
                            Text(
                                denText(polozky[0].pridano), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(polozky, key = { it.soubor.absolutePath }) { m ->
                            val druh = druhJmena(m.nazev)
                            RadekSouboru(
                                nazev = m.nazev,
                                popis = "${velikost(m.velikost)} • ${m.soubor.parentFile?.name ?: ""}",
                                vybrano = m.soubor in vybrane,
                                klik = {
                                    when {
                                        vybrane.isNotEmpty() -> prepni(m.soubor)
                                        druh == Druh.OBRAZEK || druh == Druh.VIDEO ->
                                            Nav.jdi(Obr.Prohlizec(media, media.indexOf(m.soubor)))
                                        else -> otevrit(ctx, m.soubor)
                                    }
                                },
                                dlouze = { prepni(m.soubor) }
                            ) { NahledSouboru(m.soubor, druh) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AplikaceObr() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by produceState<List<Aplikace>?>(null) {
        value = withContext(Dispatchers.IO) { Uloziste.aplikace(ctx, true).sortedByDescending { it.velikost } }
    }
    Scaffold(topBar = {
        Lista("Aplikace", data?.let { "${it.size} • ${velikost(it.sumOf { a -> a.velikost })}" })
    }) { pad ->
        val d = data
        if (d == null) { Nacitani(pad); return@Scaffold }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(d, key = { it.balicek }) { a ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    RadekSouboru(
                        nazev = a.nazev,
                        popis = "${velikost(a.velikost)} • ${a.balicek}",
                        vybrano = false,
                        klik = { menu = true },
                        dlouze = { menu = true }
                    ) {
                        val ik = a.ikona
                        if (ik != null) Image(ik, null, Modifier.size(44.dp)) else IkonaDruhu(Druh.APK, Modifier.size(44.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Otevřít") }, onClick = {
                            menu = false
                            ctx.packageManager.getLaunchIntentForPackage(a.balicek)?.let { ctx.startActivity(it) }
                                ?: zprava(ctx, "Aplikace nemá co otevřít")
                        })
                        DropdownMenuItem(text = { Text("Informace o aplikaci") }, onClick = {
                            menu = false
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.balicek))
                            )
                        })
                        DropdownMenuItem(text = { Text("Uložit APK do Stažené") }, onClick = {
                            menu = false
                            scope.launch {
                                val f = sPrubehem(ctx, "Ukládám APK…") {
                                    val cil = File(Uloziste.stazene(), "APK").apply { mkdirs() }
                                    val c = Soubory.volneJmeno(cil, "${a.nazev}.apk")
                                    a.apk.copyTo(c)
                                    Soubory.ohlasit(ctx, listOf(c))
                                    c
                                }
                                if (f != null) zprava(ctx, "Uloženo: Stažené/APK/${f.name}")
                            }
                        })
                        DropdownMenuItem(text = { Text("Odinstalovat") }, onClick = {
                            menu = false
                            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.balicek)))
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun KosObr() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var obnova by remember { mutableIntStateOf(0) }
    val data by produceState<List<VKosi>?>(null, obnova) { value = withContext(Dispatchers.IO) { Kos.seznam(ctx) } }
    val vybrane = remember { mutableStateListOf<VKosi>() }
    var vysypat by remember { mutableStateOf(false) }
    var mazatVybrane by remember { mutableStateOf(false) }
    BackHandler(vybrane.isNotEmpty()) { vybrane.clear() }
    fun prepni(p: VKosi) { if (p in vybrane) vybrane.remove(p) else vybrane.add(p) }
    fun obnovit(seznam: List<VKosi>) {
        scope.launch {
            sPrubehem(ctx, "Obnovuji…") { Kos.obnovit(ctx, seznam) }
            vybrane.clear(); obnova++
        }
    }

    Scaffold(topBar = {
        if (vybrane.isEmpty()) Lista("Koš", data?.let { "${it.size} položek • ${velikost(it.sumOf { p -> p.velikost })}" }) {
            if (!data.isNullOrEmpty()) IconButton(onClick = { vysypat = true }) { Icon(Icons.Filled.DeleteSweep, "Vysypat koš") }
        } else VyberLista(vybrane.size, { vybrane.clear() }, { vybrane.clear(); data?.let { vybrane.addAll(it) } }) {
            IconButton(onClick = { obnovit(vybrane.toList()) }) { Icon(Icons.Filled.Restore, "Obnovit") }
            IconButton(onClick = { mazatVybrane = true }) { Icon(Icons.Filled.DeleteSweep, "Smazat trvale") }
        }
    }) { pad ->
        val d = data
        when {
            d == null -> Nacitani(pad)
            d.isEmpty() -> Prazdne(pad, "Koš je prázdný")
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                items(d, key = { it.id }) { p ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        RadekSouboru(
                            nazev = p.jmeno,
                            popis = "${velikost(p.velikost)} • smazáno ${datumText(p.smazano)}\n${p.puvodni}",
                            vybrano = p in vybrane,
                            klik = { if (vybrane.isNotEmpty()) prepni(p) else menu = true },
                            dlouze = { prepni(p) }
                        ) {
                            val druh = if (p.slozka) Druh.SLOZKA else druhJmena(p.jmeno)
                            NahledSouboru(p.soubor, druh)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Obnovit") }, onClick = { menu = false; obnovit(listOf(p)) })
                            DropdownMenuItem(text = { Text("Smazat trvale") }, onClick = {
                                menu = false
                                scope.launch { sPrubehem(ctx, "Mažu…") { Kos.smazat(listOf(p)) }; obnova++ }
                            })
                        }
                    }
                }
            }
        }
    }

    if (vysypat) Potvrzeni(
        "Vysypat koš?", "Všechny položky v koši budou nenávratně smazány.", "Vysypat",
        ok = {
            vysypat = false
            val vse = data.orEmpty()
            scope.launch { sPrubehem(ctx, "Vysypávám koš…") { Kos.smazat(vse) }; obnova++ }
        },
        zrusit = { vysypat = false }
    )
    if (mazatVybrane) Potvrzeni(
        "Smazat trvale?", "${vybrane.size} položek bude nenávratně smazáno.", "Smazat",
        ok = {
            mazatVybrane = false
            val sez = vybrane.toList()
            scope.launch { sPrubehem(ctx, "Mažu…") { Kos.smazat(sez) }; vybrane.clear(); obnova++ }
        },
        zrusit = { mazatVybrane = false }
    )
}

