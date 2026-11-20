@file:OptIn(ExperimentalFoundationApi::class)

package cz.sperlich.soubory.ui

import android.content.Context
import android.graphics.Bitmap
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.sperlich.soubory.Druh
import cz.sperlich.soubory.Nav
import cz.sperlich.soubory.Obr
import cz.sperlich.soubory.Soubory
import cz.sperlich.soubory.Trezor
import cz.sperlich.soubory.VTrezoru
import cz.sperlich.soubory.otevrit
import cz.sperlich.soubory.sPrubehem
import cz.sperlich.soubory.velikost
import cz.sperlich.soubory.zprava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

private fun otisk(ctx: Context, titulek: String, sifra: Cipher, ok: (Cipher) -> Unit) {
    val prompt = BiometricPrompt.Builder(ctx)
        .setTitle(titulek)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButton("Použít PIN", ctx.mainExecutor) { _, _ -> }
        .build()
    prompt.authenticate(
        BiometricPrompt.CryptoObject(sifra), CancellationSignal(), ctx.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(ok)
            }
        }
    )
}

@Composable
fun TrezorObr() {
    when {
        Trezor.odemceno -> ObsahTrezoru()
        !Trezor.existuje() -> ZalozitTrezor()
        else -> OdemknoutTrezor()
    }
}

@Composable
private fun PinPole(hodnota: String, zmena: (String) -> Unit, popis: String) {
    OutlinedTextField(
        value = hodnota,
        onValueChange = { zmena(it.filter(Char::isDigit).take(12)) },
        label = { Text(popis) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ZalozitTrezor() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    Scaffold(topBar = { Lista("Trezor") }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Nový trezor", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Fotky a soubory v trezoru budou zašifrované (AES-256). Nikde jinde v telefonu nebudou vidět.",
                textAlign = TextAlign.Center
            )
            PinPole(pin, { pin = it }, "PIN (aspoň 4 číslice)")
            PinPole(pin2, { pin2 = it }, "PIN znovu")
            Text(
                "Pozor: když PIN zapomeneš, obsah trezoru už nikdo neotevře.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Button(
                enabled = pin.length >= 4 && pin == pin2,
                onClick = {
                    scope.launch {
                        sPrubehem(ctx, "Zakládám trezor…") { Trezor.zalozit(ctx, pin) }
                    }
                }
            ) { Text("Vytvořit trezor") }
        }
    }
}

@Composable
private fun OdemknoutTrezor() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var chyba by remember { mutableStateOf<String?>(null) }
    var pracuji by remember { mutableStateOf(false) }
    val bio = remember { Trezor.bioZapnuto(ctx) }

    fun zkusOtisk() {
        val c = Trezor.bioSifraOdemknout(ctx)
        if (c == null) {
            chyba = "Otisk prstu se změnil – odemkni PINem a otisk zapni znovu."
            return
        }
        otisk(ctx, "Odemknout trezor", c) { hotova ->
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { Trezor.bioOdemknout(ctx, hotova) } }
                    .onFailure { chyba = "Odemknutí otiskem selhalo" }
            }
        }
    }

    fun odemkni() {
        pracuji = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Trezor.odemknoutPinem(pin) }.getOrDefault(false) }
            pracuji = false
            if (!ok) { chyba = "Špatný PIN"; pin = "" }
        }
    }

    LaunchedEffect(Unit) { if (bio) zkusOtisk() }

    Scaffold(topBar = { Lista("Trezor") }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Trezor je zamčený", style = MaterialTheme.typography.headlineSmall)
            PinPole(pin, { pin = it; chyba = null }, "PIN")
            chyba?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = pin.length >= 4 && !pracuji, onClick = { odemkni() }) {
                Text(if (pracuji) "Odemykám…" else "Odemknout")
            }
            if (bio) OutlinedButton(onClick = { zkusOtisk() }) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.size(8.dp))
                Text("Otisk prstu")
            }
        }
    }
}

private enum class Filtr(val popis: String) { VSE("Vše"), FOTKY("Fotky a videa"), SOUBORY("Soubory") }

@Composable
private fun ObsahTrezoru() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var filtr by remember { mutableStateOf(Filtr.VSE) }
    val vybrane = remember { mutableStateListOf<VTrezoru>() }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var bio by remember { mutableStateOf(Trezor.bioZapnuto(ctx)) }

    // Soubory poslané do trezoru z jiných obrazovek.
    LaunchedEffect(Trezor.cekajici) {
        val f = Trezor.cekajici
        if (f.isEmpty()) return@LaunchedEffect
        Trezor.cekajici = emptyList()
        val nesmazano = sPrubehem(ctx, "Šifruji do trezoru…") {
            val n = Trezor.pridat(Trezor.zdrojeZeSouboru(f))
            // prázdné složky, ze kterých se všechno přesunulo, uklidit
            f.filter { it.isDirectory && it.walkTopDown().none { x -> x.isFile } }.forEach { it.deleteRecursively() }
            Soubory.ohlasit(ctx, f)
            n
        }
        when {
            nesmazano == null -> {}
            nesmazano > 0 -> zprava(ctx, "Zašifrováno, ale $nesmazano originálů se nepodařilo smazat")
            else -> zprava(ctx, "Přesunuto do trezoru")
        }
    }

    val vyberSouboru = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val nesmazano = sPrubehem(ctx, "Šifruji do trezoru…") {
                Trezor.pridat(uris.map { Trezor.zdrojZUri(ctx, it) })
            }
            if (nesmazano != null && nesmazano > 0) zprava(ctx, "Zašifrováno. $nesmazano originálů nešlo smazat – smaž je ručně.")
        }
    }

    BackHandler(vybrane.isNotEmpty()) { vybrane.clear() }

    val vse = Trezor.polozky.sortedByDescending { it.pridano }
    val zobrazene = when (filtr) {
        Filtr.VSE -> vse
        Filtr.FOTKY -> vse.filter { it.druh == Druh.OBRAZEK || it.druh == Druh.VIDEO }
        Filtr.SOUBORY -> vse.filter { it.druh != Druh.OBRAZEK && it.druh != Druh.VIDEO }
    }
    fun prepni(p: VTrezoru) { if (p in vybrane) vybrane.remove(p) else vybrane.add(p) }

    Scaffold(
        topBar = {
            if (vybrane.isEmpty()) Lista("Trezor", "${vse.size} položek • ${velikost(vse.sumOf { it.velikost })}") {
                IconButton(onClick = { Trezor.zamknout() }) { Icon(Icons.Filled.Lock, "Zamknout") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Další") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (bio) "Vypnout otisk prstu" else "Zapnout otisk prstu") },
                            leadingIcon = { Icon(Icons.Filled.Fingerprint, null) },
                            onClick = {
                                menu = false
                                if (bio) { Trezor.bioVypnout(ctx); bio = false }
                                else {
                                    val c = runCatching { Trezor.bioSifraZapnout() }.getOrNull()
                                    if (c == null) zprava(ctx, "Nejdřív si v nastavení telefonu přidej otisk prstu")
                                    else otisk(ctx, "Zapnout odemykání otiskem", c) { hotova ->
                                        runCatching { Trezor.bioDokoncit(ctx, hotova) }
                                            .onSuccess { bio = true; zprava(ctx, "Otisk prstu zapnut") }
                                            .onFailure { zprava(ctx, "Zapnutí otisku selhalo") }
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(text = { Text("Změnit PIN") }, onClick = { menu = false; dialog = "pin" })
                    }
                }
            } else VyberLista(vybrane.size, { vybrane.clear() }, { vybrane.clear(); vybrane.addAll(zobrazene) }) {
                IconButton(onClick = {
                    val sez = vybrane.toList()
                    scope.launch {
                        val f = sPrubehem(ctx, "Vracím z trezoru…") { Trezor.vratit(ctx, sez) }
                        if (f != null) zprava(ctx, "Vráceno ${f.size} položek na původní místo")
                        vybrane.clear()
                    }
                }) { Icon(Icons.Filled.LockOpen, "Vrátit z trezoru") }
                IconButton(onClick = { dialog = "smazat" }) { Icon(Icons.Filled.Delete, "Smazat") }
            }
        },
        floatingActionButton = {
            if (vybrane.isEmpty()) FloatingActionButton(onClick = {
                Trezor.nezamykat = true
                vyberSouboru.launch(arrayOf("*/*"))
            }) { Icon(Icons.Filled.Add, "Přidat do trezoru") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filtr.entries.forEach { f -> FilterChip(selected = filtr == f, onClick = { filtr = f }, label = { Text(f.popis) }) }
            }
            if (zobrazene.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Trezor je prázdný.\nPřidej soubory tlačítkem +, nebo je v prohlížeči podrž a dej „Přesunout do trezoru“.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(zobrazene, key = { it.id }) { p ->
                    BunkaTrezoru(
                        p, p in vybrane,
                        klik = {
                            when {
                                vybrane.isNotEmpty() -> prepni(p)
                                p.druh == Druh.OBRAZEK || p.druh == Druh.VIDEO -> {
                                    val media = zobrazene.filter { it.druh == Druh.OBRAZEK || it.druh == Druh.VIDEO }
                                    Nav.jdi(Obr.TrezorFoto(media, media.indexOf(p)))
                                }
                                else -> scope.launch {
                                    val f = sPrubehem(ctx, "Dešifruji…") { Trezor.docasny(ctx, p) }
                                    if (f != null) { Trezor.nezamykat = true; otevrit(ctx, f) }
                                }
                            }
                        },
                        dlouze = { prepni(p) }
                    )
                }
            }
        }
    }

    when (dialog) {
        "smazat" -> Potvrzeni(
            "Smazat z trezoru?", "${vybrane.size} položek bude nenávratně smazáno.", "Smazat",
            ok = {
                dialog = null
                val sez = vybrane.toList()
                scope.launch { sPrubehem(ctx, "Mažu…") { Trezor.smazat(sez) }; vybrane.clear() }
            },
            zrusit = { dialog = null }
        )

        "pin" -> ZmenaPinu(zavrit = { dialog = null })
    }
}

@Composable
private fun ZmenaPinu(zavrit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = zavrit,
        title = { Text("Změnit PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinPole(pin, { pin = it }, "Nový PIN")
                PinPole(pin2, { pin2 = it }, "Nový PIN znovu")
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length >= 4 && pin == pin2, onClick = {
                scope.launch {
                    val ok = sPrubehem(ctx, "Měním PIN…") { Trezor.zmenitPin(pin) }
                    if (ok != null) zprava(ctx, "PIN změněn")
                    zavrit()
                }
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = zavrit) { Text("Zrušit") } }
    )
}

@Composable
private fun BunkaTrezoru(p: VTrezoru, vybrano: Boolean, klik: () -> Unit, dlouze: () -> Unit) {
    val nahled by produceState<Bitmap?>(null, p.id) { value = withContext(Dispatchers.IO) { Trezor.nahled(p) } }
    Box(
        Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = klik, onLongClick = dlouze)
    ) {
        val n = nahled
        if (n != null) {
            Image(n.asImageBitmap(), p.jmeno, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IkonaDruhu(p.druh, Modifier.size(44.dp))
                Spacer(Modifier.height(6.dp))
                Text(p.jmeno, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        if (p.druh == Druh.VIDEO) Icon(
            Icons.Filled.PlayCircle, null, tint = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(22.dp)
        )
        if (vybrano) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Icon(
                Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
            )
        }
    }
}
