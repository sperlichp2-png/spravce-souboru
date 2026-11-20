@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package cz.sperlich.soubory.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cz.sperlich.soubory.Druh
import cz.sperlich.soubory.Kos
import cz.sperlich.soubory.Nastaveni
import cz.sperlich.soubory.Nav
import cz.sperlich.soubory.Obr
import cz.sperlich.soubory.Polozka
import cz.sperlich.soubory.Prubeh
import cz.sperlich.soubory.Razeni
import cz.sperlich.soubory.Schranka
import cz.sperlich.soubory.Trezor
import cz.sperlich.soubory.sPrubehem
import cz.sperlich.soubory.sdilet
import cz.sperlich.soubory.zprava
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun Lista(
    titulek: String,
    podtitul: String? = null,
    zpet: (() -> Unit)? = { Nav.zpet() },
    akce: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(titulek, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (podtitul != null) Text(
                    podtitul, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            if (zpet != null) IconButton(onClick = zpet) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") }
        },
        actions = akce
    )
}

@Composable
fun VyberLista(pocet: Int, zrusit: () -> Unit, vseVybrat: (() -> Unit)? = null, akce: @Composable RowScope.() -> Unit) {
    TopAppBar(
        title = { Text("$pocet vybráno") },
        navigationIcon = { IconButton(onClick = zrusit) { Icon(Icons.Filled.Close, "Zrušit výběr") } },
        actions = {
            if (vseVybrat != null) IconButton(onClick = vseVybrat) { Icon(Icons.Filled.SelectAll, "Vybrat vše") }
            akce()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    )
}

fun barvaDruhu(d: Druh): Color = when (d) {
    Druh.SLOZKA -> Color(0xFFE6A23C)
    Druh.OBRAZEK -> Color(0xFF8E44AD)
    Druh.VIDEO -> Color(0xFFD9363E)
    Druh.AUDIO -> Color(0xFF16A085)
    Druh.DOKUMENT -> Color(0xFF2D7DD2)
    Druh.APK -> Color(0xFF7CB342)
    Druh.ARCHIV -> Color(0xFF8D6E63)
    Druh.JINE -> Color(0xFF78909C)
}

fun ikonaDruhu(d: Druh): ImageVector = when (d) {
    Druh.SLOZKA -> Icons.Filled.Folder
    Druh.OBRAZEK -> Icons.Filled.Image
    Druh.VIDEO -> Icons.Filled.Movie
    Druh.AUDIO -> Icons.Filled.MusicNote
    Druh.DOKUMENT -> Icons.Filled.Description
    Druh.APK -> Icons.Filled.Android
    Druh.ARCHIV -> Icons.Filled.Archive
    Druh.JINE -> Icons.Filled.InsertDriveFile
}

@Composable
fun IkonaDruhu(d: Druh, modifier: Modifier = Modifier) {
    val b = barvaDruhu(d)
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(b.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) { Icon(ikonaDruhu(d), null, tint = b, modifier = Modifier.size(26.dp)) }
}

@Composable
fun NahledSouboru(f: File, d: Druh, modifier: Modifier = Modifier.size(44.dp)) {
    if (d == Druh.OBRAZEK || d == Druh.VIDEO) {
        AsyncImage(
            model = f, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
    } else IkonaDruhu(d, modifier)
}

@Composable
fun RadekSouboru(
    nazev: String,
    popis: String,
    vybrano: Boolean,
    klik: () -> Unit,
    dlouze: () -> Unit,
    nahled: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (vybrano) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = klik, onLongClick = dlouze)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { nahled() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(nazev, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(popis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (vybrano) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun Nacitani(pad: PaddingValues) {
    Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun Prazdne(pad: PaddingValues, text: String) {
    Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TextDialog(titulek: String, vychozi: String, ok: (String) -> Unit, zrusit: () -> Unit) {
    var text by remember { mutableStateOf(vychozi) }
    AlertDialog(
        onDismissRequest = zrusit,
        title = { Text(titulek) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = { ok(text.trim()) }, enabled = text.isNotBlank() && !text.contains('/')) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = zrusit) { Text("Zrušit") } }
    )
}

@Composable
fun Potvrzeni(titulek: String, text: String, tlacitko: String, ok: () -> Unit, zrusit: () -> Unit) {
    AlertDialog(
        onDismissRequest = zrusit,
        title = { Text(titulek) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = ok) { Text(tlacitko) } },
        dismissButton = { TextButton(onClick = zrusit) { Text("Zrušit") } }
    )
}

@Composable
fun PrubehDialog() {
    if (!Prubeh.aktivni) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(Prubeh.text) },
        text = {
            Column {
                if (Prubeh.podil < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                else {
                    LinearProgressIndicator(progress = { Prubeh.podil }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(Prubeh.podil * 100).toInt()} %")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { Prubeh.zruseno = true }) { Text("Zrušit") } }
    )
}

/** Spodní lišta se schránkou – „Vložit sem“. */
@Composable
fun SchrankaLista(vlozit: () -> Unit) {
    val pol = Schranka.polozky
    if (pol.isEmpty()) return
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (Schranka.vyjmout) "Přesunout" else "Kopírovat", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (pol.size == 1) pol[0].jmeno else "${pol.size} položek",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { Schranka.vycistit() }) { Text("Zrušit") }
            Button(onClick = vlozit) { Text("Vložit sem") }
        }
    }
}

@Composable
fun RazeniMenu() {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Řazení") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Razeni.entries.forEach { r ->
                DropdownMenuItem(
                    text = { Text(r.popis) },
                    leadingIcon = { if (Nastaveni.razeni == r) Icon(Icons.Filled.Check, null) },
                    onClick = { Nastaveni.razeni = r; menu = false }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Zobrazit skryté soubory") },
                leadingIcon = { if (Nastaveni.skryte) Icon(Icons.Filled.Check, null) },
                onClick = { Nastaveni.skryte = !Nastaveni.skryte; menu = false }
            )
        }
    }
}

/** Akce pro vybrané soubory v telefonu: kopírovat, vyjmout, smazat, sdílet, do trezoru. */
@Composable
fun LokalniAkce(vybrane: List<File>, hotovo: () -> Unit, prejmenovat: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var mazat by remember { mutableStateOf(false) }
    IconButton(onClick = {
        Schranka.nastav(vybrane.map { Polozka.Lok(it) }, false)
        zprava(ctx, "Zkopírováno – otevři cílovou složku a dej Vložit sem")
        hotovo()
    }) { Icon(Icons.Filled.ContentCopy, "Kopírovat") }
    IconButton(onClick = {
        Schranka.nastav(vybrane.map { Polozka.Lok(it) }, true)
        zprava(ctx, "Vyjmuto – otevři cílovou složku a dej Vložit sem")
        hotovo()
    }) { Icon(Icons.Filled.ContentCut, "Vyjmout") }
    IconButton(onClick = { mazat = true }) { Icon(Icons.Filled.Delete, "Smazat") }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Další") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (prejmenovat != null && vybrane.size == 1) DropdownMenuItem(
                text = { Text("Přejmenovat") }, onClick = { menu = false; prejmenovat() }
            )
            DropdownMenuItem(text = { Text("Sdílet") }, onClick = { menu = false; sdilet(ctx, vybrane) })
            DropdownMenuItem(text = { Text("Přesunout do trezoru") }, onClick = {
                menu = false
                Trezor.cekajici = vybrane.toList()
                hotovo()
                Nav.jdi(Obr.Trezor)
            })
        }
    }
    if (mazat) Potvrzeni(
        titulek = "Přesunout do koše?",
        text = if (vybrane.size == 1) vybrane[0].name else "${vybrane.size} položek",
        tlacitko = "Do koše",
        ok = {
            mazat = false
            val seznam = vybrane.toList()
            scope.launch {
                sPrubehem(ctx, "Přesouvám do koše…") { Kos.vyhodit(ctx, seznam) }
                hotovo()
            }
        },
        zrusit = { mazat = false }
    )
}
