package cz.sperlich.soubory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.sperlich.soubory.Kos
import cz.sperlich.soubory.Nav
import cz.sperlich.soubory.Obr
import cz.sperlich.soubory.PripojeniUloziste
import cz.sperlich.soubory.Svazek
import cz.sperlich.soubory.Trezor
import cz.sperlich.soubory.Uloziste
import cz.sperlich.soubory.velikost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DomuInfo(
    val svazky: List<Svazek>,
    val stazene: Pair<Long, Int>,
    val kos: Long,
    val aplikace: Pair<Long, Int>,
)

@Composable
fun DomuObr() {
    val ctx = LocalContext.current
    val svazky by produceState(emptyList<Svazek>()) { value = withContext(Dispatchers.IO) { Uloziste.svazky(ctx) } }
    val info by produceState<DomuInfo?>(null) {
        value = withContext(Dispatchers.IO) {
            val apps = runCatching { Uloziste.aplikace(ctx, false) }.getOrDefault(emptyList())
            DomuInfo(
                Uloziste.svazky(ctx),
                Uloziste.obsahSlozky(Uloziste.stazene()),
                runCatching { Kos.velikost(ctx) }.getOrDefault(0L),
                apps.sumOf { it.velikost } to apps.size
            )
        }
    }
    Scaffold(topBar = { Lista("Správce souborů", zpet = null) }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            svazky.forEach { sv ->
                item {
                    val pouzito = sv.celkem - sv.volno
                    Dlazdice(
                        if (sv.vymenny) Icons.Filled.SdCard else Icons.Filled.Smartphone,
                        if (sv.vymenny) Color(0xFF5C6BC0) else Color(0xFF607D8B),
                        sv.nazev,
                        "${velikost(pouzito)} / ${velikost(sv.celkem)}",
                        podil = if (sv.celkem > 0) pouzito.toFloat() / sv.celkem else 0f
                    ) { Nav.jdi(Obr.Slozka(sv.cesta, sv.nazev)) }
                }
            }
            item {
                Dlazdice(
                    Icons.Filled.Download, Color(0xFFE09B3D), "Stažené",
                    info?.stazene?.let { "${velikost(it.first)} (${it.second})" }
                ) { Nav.jdi(Obr.Slozka(Uloziste.stazene(), "Stažené")) }
            }
            item {
                Dlazdice(Icons.Filled.PhotoLibrary, Color(0xFFEC407A), "Poslední fotky", null) {
                    Nav.jdi(Obr.Media("Poslední fotky", MediaDruh.POSLEDNI))
                }
            }
            item {
                Dlazdice(Icons.Filled.History, Color(0xFF26A69A), "Poslední soubory", "30 dní") {
                    Nav.jdi(Obr.Seznam("Poslední soubory", SeznamDruh.POSLEDNI))
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(2.dp)) }

            item {
                Dlazdice(Icons.Filled.Image, Color(0xFF8E44AD), "Obrázky", null) {
                    Nav.jdi(Obr.Media("Obrázky", MediaDruh.OBRAZKY))
                }
            }
            item {
                Dlazdice(Icons.Filled.Movie, Color(0xFFD9363E), "Videa", null) {
                    Nav.jdi(Obr.Media("Videa", MediaDruh.VIDEA))
                }
            }
            item {
                Dlazdice(Icons.Filled.MusicNote, Color(0xFF16A085), "Audio", null) {
                    Nav.jdi(Obr.Seznam("Audio", SeznamDruh.AUDIO))
                }
            }
            item {
                Dlazdice(Icons.Filled.Description, Color(0xFF2D7DD2), "Dokumenty", null) {
                    Nav.jdi(Obr.Seznam("Dokumenty", SeznamDruh.DOKUMENTY))
                }
            }
            item {
                Dlazdice(
                    Icons.Filled.Apps, Color(0xFF7CB342), "Aplikace",
                    info?.aplikace?.let { "${velikost(it.first)} (${it.second})" }
                ) { Nav.jdi(Obr.Aplikace) }
            }
            item {
                Dlazdice(
                    Icons.Filled.Dns, Color(0xFF795548), "NAS",
                    PripojeniUloziste.seznam.size.let { if (it == 0) "nepřipojeno" else "($it)" }
                ) { Nav.jdi(Obr.NasSeznam) }
            }
            item {
                Dlazdice(
                    Icons.Filled.Lock, Color(0xFF37474F), "Trezor",
                    if (Trezor.odemceno) "odemčený" else "zamčený"
                ) { Nav.jdi(Obr.Trezor) }
            }
            item {
                Dlazdice(Icons.Filled.Delete, Color(0xFF1E88E5), "Koš", info?.kos?.let { velikost(it) }) {
                    Nav.jdi(Obr.Kos)
                }
            }
        }
    }
}

@Composable
private fun Dlazdice(
    ikona: ImageVector,
    barva: Color,
    nazev: String,
    popis: String?,
    podil: Float? = null,
    klik: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = klik).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(barva.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(ikona, null, tint = barva, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(
            nazev, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (popis != null) Text(
            popis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (podil != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { podil }, color = barva,
                modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            )
        }
    }
}
