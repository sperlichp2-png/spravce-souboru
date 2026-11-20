package cz.sperlich.soubory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cz.sperlich.soubory.Druh
import cz.sperlich.soubory.Nas
import cz.sperlich.soubory.Nastaveni
import cz.sperlich.soubory.Nav
import cz.sperlich.soubory.Obr
import cz.sperlich.soubory.Polozka
import cz.sperlich.soubory.Pripojeni
import cz.sperlich.soubory.PripojeniUloziste
import cz.sperlich.soubory.Schranka
import cz.sperlich.soubory.Soubory
import cz.sperlich.soubory.Uloziste
import cz.sperlich.soubory.VzdSoubor
import cz.sperlich.soubory.chybaText
import cz.sperlich.soubory.datumText
import cz.sperlich.soubory.druhJmena
import cz.sperlich.soubory.jeMedium
import cz.sperlich.soubory.otevrit
import cz.sperlich.soubory.sPrubehem
import cz.sperlich.soubory.velikost
import cz.sperlich.soubory.vlozitNaNas
import cz.sperlich.soubory.zprava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun NasSeznamObr() {
    val ctx = LocalContext.current
    var upravit by remember { mutableStateOf<Pripojeni?>(null) }
    var novy by remember { mutableStateOf(false) }
    val seznam = PripojeniUloziste.seznam
    Scaffold(
        topBar = { Lista("NAS – síťové disky", "SFTP") },
        floatingActionButton = {
            FloatingActionButton(onClick = { novy = true }) { Icon(Icons.Filled.Add, "Přidat připojení") }
        }
    ) { pad ->
        if (seznam.isEmpty()) {
            Prazdne(pad, "Zatím žádné připojení.\nTlačítkem + přidej svůj NAS (Raspberry Pi s OMV).")
        } else LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(seznam, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.nazev) },
                    supportingContent = { Text("${p.uzivatel}@${p.host}:${p.port}" + if (p.slozka.isNotBlank()) "  ${p.slozka}" else "") },
                    leadingContent = { Icon(Icons.Filled.Dns, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { IconButton(onClick = { upravit = p }) { Icon(Icons.Filled.Edit, "Upravit") } },
                    modifier = Modifier.clickable { Nav.jdi(Obr.Nas(p)) }
                )
            }
        }
    }
    if (novy || upravit != null) {
        PripojeniDialog(upravit, zavrit = { novy = false; upravit = null }) { p ->
            PripojeniUloziste.ulozit(ctx, p)
            novy = false; upravit = null
        }
    }
}

@Composable
private fun PripojeniDialog(puvodni: Pripojeni?, zavrit: () -> Unit, ulozit: (Pripojeni) -> Unit) {
    val ctx = LocalContext.current
    var nazev by remember { mutableStateOf(puvodni?.nazev ?: "NAS") }
    var host by remember { mutableStateOf(puvodni?.host ?: "") }
    var port by remember { mutableStateOf((puvodni?.port ?: 22).toString()) }
    var uzivatel by remember { mutableStateOf(puvodni?.uzivatel ?: "") }
    var heslo by remember { mutableStateOf(puvodni?.heslo ?: "") }
    var slozka by remember { mutableStateOf(puvodni?.slozka ?: "") }
    var smazat by remember { mutableStateOf(false) }
    val platne = host.isNotBlank() && uzivatel.isNotBlank() && port.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = zavrit,
        title = { Text(if (puvodni == null) "Nové připojení" else "Upravit připojení") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(nazev, { nazev = it }, label = { Text("Název") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    host, { host = it.trim() }, label = { Text("Adresa (IP Raspberry Pi)") }, singleLine = true,
                    placeholder = { Text("192.168.1.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(uzivatel, { uzivatel = it.trim() }, label = { Text("Uživatel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    heslo, { heslo = it }, label = { Text("Heslo") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    slozka, { slozka = it.trim() }, label = { Text("Výchozí složka (nepovinné)") }, singleLine = true,
                    placeholder = { Text("/srv/…") }, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Heslo se ukládá zašifrované v bezpečném čipu telefonu.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (puvodni != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        Nas.zapomenoutKlice(ctx)
                        zprava(ctx, "Klíč serveru zapomenut – při dalším připojení se uloží nový")
                    }) { Text("Zapomenout klíč serveru") }
                    TextButton(onClick = { smazat = true }) { Text("Smazat připojení", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(enabled = platne, onClick = {
                ulozit(
                    Pripojeni(
                        puvodni?.id ?: UUID.randomUUID().toString(), nazev.ifBlank { host }, host,
                        port.toInt(), uzivatel, heslo, slozka
                    )
                )
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = zavrit) { Text("Zrušit") } }
    )
    if (smazat && puvodni != null) Potvrzeni(
        "Smazat připojení?", puvodni.nazev, "Smazat",
        ok = { PripojeniUloziste.odebrat(ctx, puvodni.id); smazat = false; zavrit() },
        zrusit = { smazat = false }
    )
}

private data class NasVysledek(val seznam: List<VzdSoubor>?, val chyba: String?)

@Composable
fun NasObr(o: Obr.Nas) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var obnova by remember { mutableIntStateOf(0) }
    var chybaDomov by remember { mutableStateOf<String?>(null) }
    val skryte = Nastaveni.skryte

    LaunchedEffect(obnova) {
        if (o.dir == null) {
            chybaDomov = null
            val d = withContext(Dispatchers.IO) { runCatching { Nas.domov(ctx, o.p) } }
            d.onSuccess { o.domov = it; o.dir = it }.onFailure { chybaDomov = chybaText(it) }
        }
    }
    val vysledek by produceState<NasVysledek?>(null, o.dir, obnova, skryte) {
        val d = o.dir ?: return@produceState
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { Nas.seznam(ctx, o.p, d) }.fold({ NasVysledek(it, null) }, { NasVysledek(null, chybaText(it)) })
        }
    }
    var dialog by remember { mutableStateOf<String?>(null) }
    val vybrane = o.vybrane
    fun prepni(v: VzdSoubor) { if (v in vybrane) vybrane.remove(v) else vybrane.add(v) }
    val hotovo: () -> Unit = { vybrane.clear(); obnova++ }

    BackHandler(vybrane.isNotEmpty() || (o.dir != null && o.dir != o.domov && o.dir != "/")) {
        if (vybrane.isNotEmpty()) vybrane.clear() else o.dir = Nas.rodic(o.dir!!)
    }

    Scaffold(
        topBar = {
            if (vybrane.isEmpty()) Lista(o.p.nazev, o.dir ?: "Připojuji…") {
                IconButton(onClick = { o.dir?.let { if (it != "/") o.dir = Nas.rodic(it) } }) {
                    Icon(Icons.Filled.ArrowUpward, "O úroveň výš")
                }
                IconButton(onClick = { obnova++ }) { Icon(Icons.Filled.Refresh, "Obnovit") }
                RazeniNas { dialog = "nova" }
            } else VyberLista(vybrane.size, { vybrane.clear() }, { vybrane.clear(); vysledek?.seznam?.let { vybrane.addAll(it) } }) {
                IconButton(onClick = {
                    val sez = vybrane.toList()
                    scope.launch {
                        val cil = File(Uloziste.stazene(), "NAS").apply { mkdirs() }
                        val nove = sPrubehem(ctx, "Stahuji…") {
                            Nas.stahnout(ctx, o.p, sez.map { it.cesta to it.slozka }, cil).also { Soubory.ohlasit(ctx, it) }
                        }
                        if (nove != null) zprava(ctx, "Staženo do Stažené/NAS")
                        hotovo()
                    }
                }) { Icon(Icons.Filled.Download, "Stáhnout") }
                IconButton(onClick = {
                    Schranka.nastav(vybrane.map { Polozka.Vzd(o.p.id, it.cesta, it.slozka) }, false)
                    zprava(ctx, "Zkopírováno – otevři cílovou složku v telefonu a dej Vložit sem")
                    hotovo()
                }) { Icon(Icons.Filled.ContentCopy, "Kopírovat") }
                IconButton(onClick = {
                    Schranka.nastav(vybrane.map { Polozka.Vzd(o.p.id, it.cesta, it.slozka) }, true)
                    zprava(ctx, "Vyjmuto – otevři cílovou složku a dej Vložit sem")
                    hotovo()
                }) { Icon(Icons.Filled.ContentCut, "Vyjmout") }
                IconButton(onClick = { dialog = "smazat" }) { Icon(Icons.Filled.Delete, "Smazat") }
                if (vybrane.size == 1) IconButton(onClick = { dialog = "prejmenovat" }) { Icon(Icons.Filled.Edit, "Přejmenovat") }
            }
        },
        bottomBar = {
            SchrankaLista {
                val d = o.dir ?: return@SchrankaLista
                scope.launch { vlozitNaNas(ctx, o.p, d); obnova++ }
            }
        }
    ) { pad ->
        val v = vysledek
        when {
            chybaDomov != null -> ChybaNas(pad, chybaDomov!!) { obnova++ }
            v == null -> Nacitani(pad)
            v.chyba != null -> ChybaNas(pad, v.chyba) { obnova++ }
            v.seznam.isNullOrEmpty() -> Prazdne(pad, "Prázdná složka")
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize(), state = o.stav) {
                items(v.seznam.orEmpty(), key = { it.cesta }) { s ->
                    val druh = if (s.slozka) Druh.SLOZKA else druhJmena(s.jmeno)
                    RadekSouboru(
                        nazev = s.jmeno,
                        popis = if (s.slozka) datumText(s.zmeneno) else "${velikost(s.velikost)} • ${datumText(s.zmeneno)}",
                        vybrano = s in vybrane,
                        klik = {
                            when {
                                vybrane.isNotEmpty() -> prepni(s)
                                s.slozka -> o.dir = s.cesta
                                else -> scope.launch {
                                    val f = sPrubehem(ctx, "Stahuji ${s.jmeno}…") { Nas.stahnoutDocasne(ctx, o.p, s.cesta) }
                                    if (f != null) {
                                        if (jeMedium(f.name)) Nav.jdi(Obr.Prohlizec(listOf(f), 0)) else otevrit(ctx, f)
                                    }
                                }
                            }
                        },
                        dlouze = { prepni(s) }
                    ) { IkonaDruhu(druh, Modifier.fillMaxSize()) }
                }
            }
        }
    }

    when (dialog) {
        "nova" -> TextDialog("Nová složka na NAS", "", ok = { jmeno ->
            dialog = null
            val d = o.dir ?: return@TextDialog
            scope.launch { sPrubehem(ctx, "Vytvářím složku…") { Nas.novaSlozka(ctx, o.p, d, jmeno) }; obnova++ }
        }, zrusit = { dialog = null })

        "prejmenovat" -> {
            val s = vybrane.firstOrNull()
            if (s == null) dialog = null
            else TextDialog("Přejmenovat", s.jmeno, ok = { jmeno ->
                dialog = null
                scope.launch { sPrubehem(ctx, "Přejmenovávám…") { Nas.prejmenovat(ctx, o.p, s.cesta, jmeno) }; hotovo() }
            }, zrusit = { dialog = null })
        }

        "smazat" -> Potvrzeni(
            "Smazat z NAS?",
            "${vybrane.size} položek bude na NAS nenávratně smazáno (NAS nemá koš).",
            "Smazat",
            ok = {
                dialog = null
                val sez = vybrane.toList()
                scope.launch { sPrubehem(ctx, "Mažu na NAS…") { Nas.smazat(ctx, o.p, sez.map { it.cesta to it.slozka }) }; hotovo() }
            },
            zrusit = { dialog = null }
        )
    }
}

@Composable
private fun RazeniNas(novaSlozka: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Další") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Nová složka") }, leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                onClick = { menu = false; novaSlozka() }
            )
            DropdownMenuItem(
                text = { Text(if (Nastaveni.skryte) "Skrýt skryté soubory" else "Zobrazit skryté soubory") },
                onClick = { menu = false; Nastaveni.skryte = !Nastaveni.skryte }
            )
        }
    }
}

@Composable
private fun ChybaNas(pad: androidx.compose.foundation.layout.PaddingValues, text: String, znovu: () -> Unit) {
    Column(
        Modifier.padding(pad).fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = znovu) { Text("Zkusit znovu") }
    }
}
