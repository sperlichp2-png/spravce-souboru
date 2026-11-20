@file:OptIn(ExperimentalFoundationApi::class)

package cz.sperlich.soubory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cz.sperlich.soubory.Druh
import cz.sperlich.soubory.MSoubor
import cz.sperlich.soubory.Nav
import cz.sperlich.soubory.Obr
import cz.sperlich.soubory.Uloziste
import cz.sperlich.soubory.denKlic
import cz.sperlich.soubory.denText
import cz.sperlich.soubory.druhJmena
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class MediaDruh { POSLEDNI, OBRAZKY, VIDEA }

@Composable
fun MediaObr(o: Obr.Media) {
    val ctx = LocalContext.current
    var obnova by remember { mutableIntStateOf(0) }
    val data by produceState<List<MSoubor>?>(null, obnova) {
        value = withContext(Dispatchers.IO) {
            when (o.druh) {
                MediaDruh.POSLEDNI -> Uloziste.posledniFotky(ctx)
                MediaDruh.OBRAZKY -> Uloziste.obrazky(ctx)
                MediaDruh.VIDEA -> Uloziste.videa(ctx)
            }
        }
    }
    val vybrane = o.vybrane
    BackHandler(vybrane.isNotEmpty()) { vybrane.clear() }
    fun prepni(f: File) { if (f in vybrane) vybrane.remove(f) else vybrane.add(f) }

    Scaffold(topBar = {
        if (vybrane.isEmpty()) Lista(o.titulek, data?.let { "${it.size} položek" })
        else VyberLista(vybrane.size, { vybrane.clear() }, { vybrane.clear(); data?.let { d -> vybrane.addAll(d.map { it.soubor }) } }) {
            LokalniAkce(vybrane.toList(), { vybrane.clear(); obnova++ })
        }
    }) { pad ->
        val d = data
        when {
            d == null -> Nacitani(pad)
            d.isEmpty() -> Prazdne(pad, "Nic tu není")
            else -> {
                val skupiny = remember(d) { d.groupBy { denKlic(it.pridano) } }
                val vsechny = remember(d) { d.map { it.soubor } }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.padding(pad).fillMaxSize(),
                    state = o.stav,
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    skupiny.forEach { (den, polozky) ->
                        item(key = "den-$den", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                denText(polozky[0].pridano),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp)
                            )
                        }
                        items(polozky, key = { it.soubor.absolutePath }) { m ->
                            MediaBunka(
                                f = m.soubor,
                                video = druhJmena(m.nazev) == Druh.VIDEO,
                                vybrano = m.soubor in vybrane,
                                klik = {
                                    if (vybrane.isNotEmpty()) prepni(m.soubor)
                                    else Nav.jdi(Obr.Prohlizec(vsechny, vsechny.indexOf(m.soubor)))
                                },
                                dlouze = { prepni(m.soubor) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaBunka(f: File, video: Boolean, vybrano: Boolean, klik: () -> Unit, dlouze: () -> Unit) {
    Box(
        Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = klik, onLongClick = dlouze)
    ) {
        AsyncImage(model = f, contentDescription = f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (video) Icon(
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
