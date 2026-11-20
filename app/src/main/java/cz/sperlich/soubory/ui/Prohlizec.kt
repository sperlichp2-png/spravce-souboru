@file:OptIn(ExperimentalFoundationApi::class)

package cz.sperlich.soubory.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cz.sperlich.soubory.Druh
import cz.sperlich.soubory.Kos
import cz.sperlich.soubory.Nav
import cz.sperlich.soubory.Obr
import cz.sperlich.soubory.Trezor
import cz.sperlich.soubory.datumText
import cz.sperlich.soubory.druhJmena
import cz.sperlich.soubory.otevrit
import cz.sperlich.soubory.sPrubehem
import cz.sperlich.soubory.sdilet
import cz.sperlich.soubory.velikost
import cz.sperlich.soubory.zprava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Přibližování dvěma prsty a dvojklepnutím; jedním prstem jde listovat dál. */
@Composable
fun Zoom(obsah: @Composable () -> Unit) {
    var meritko by remember { mutableFloatStateOf(1f) }
    var posun by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (meritko > 1f) { meritko = 1f; posun = Offset.Zero } else meritko = 2.5f
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val e = awaitPointerEvent()
                        if (e.changes.size > 1 || meritko > 1f) {
                            meritko = (meritko * e.calculateZoom()).coerceIn(1f, 6f)
                            posun = if (meritko == 1f) Offset.Zero else posun + e.calculatePan()
                            e.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (e.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = meritko; scaleY = meritko
                translationX = posun.x; translationY = posun.y
            },
        contentAlignment = Alignment.Center
    ) { obsah() }
}

@Composable
private fun HorniPruh(titulek: String, podtitul: String, akce: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).statusBarsPadding().padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { Nav.zpet() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(titulek, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(podtitul, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        akce()
    }
}

@Composable
fun ProhlizecObr(o: Obr.Prohlizec) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (o.soubory.isEmpty()) { LaunchedEffect(Unit) { Nav.zpet() }; return }
    val stav = rememberPagerState(initialPage = o.start.coerceIn(0, o.soubory.size - 1)) { o.soubory.size }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = stav, modifier = Modifier.fillMaxSize(), key = { o.soubory[it].absolutePath }) { i ->
            val f = o.soubory[i]
            if (druhJmena(f.name) == Druh.VIDEO) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(model = f, contentDescription = f.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    IconButton(onClick = { otevrit(ctx, f) }, modifier = Modifier.size(96.dp)) {
                        Icon(Icons.Filled.PlayCircle, "Přehrát", tint = Color.White, modifier = Modifier.size(88.dp))
                    }
                }
            } else {
                Zoom {
                    AsyncImage(model = f, contentDescription = f.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val f = o.soubory[stav.currentPage.coerceIn(0, o.soubory.size - 1)]
        HorniPruh(f.name, "${stav.currentPage + 1} / ${o.soubory.size} • ${velikost(f.length())} • ${datumText(f.lastModified())}") {
            IconButton(onClick = { sdilet(ctx, listOf(f)) }) { Icon(Icons.Filled.Share, "Sdílet", tint = Color.White) }
            IconButton(onClick = { otevrit(ctx, f, vyber = true) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Otevřít v…", tint = Color.White)
            }
            IconButton(onClick = {
                scope.launch {
                    sPrubehem(ctx, "Přesouvám do koše…") { Kos.vyhodit(ctx, listOf(f)) }
                    Nav.zpet()
                }
            }) { Icon(Icons.Filled.Delete, "Smazat", tint = Color.White) }
        }
    }
}

@Composable
fun TrezorFotoObr(o: Obr.TrezorFoto) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (!Trezor.odemceno || o.polozky.isEmpty()) { LaunchedEffect(Unit) { Nav.zpet() }; return }
    val stav = rememberPagerState(initialPage = o.start.coerceIn(0, o.polozky.size - 1)) { o.polozky.size }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = stav, modifier = Modifier.fillMaxSize(), key = { o.polozky[it].id }) { i ->
            val p = o.polozky[i]
            if (p.druh == Druh.VIDEO) {
                val nahled by produceState<Bitmap?>(null, p.id) { value = withContext(Dispatchers.IO) { Trezor.nahled(p) } }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    nahled?.let { Image(it.asImageBitmap(), p.jmeno, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    IconButton(onClick = {
                        scope.launch {
                            val f = sPrubehem(ctx, "Dešifruji…") { Trezor.docasny(ctx, p) }
                            if (f != null) { Trezor.nezamykat = true; otevrit(ctx, f) }
                        }
                    }, modifier = Modifier.size(96.dp)) {
                        Icon(Icons.Filled.PlayCircle, "Přehrát", tint = Color.White, modifier = Modifier.size(88.dp))
                    }
                }
            } else {
                val bmp by produceState<Bitmap?>(null, p.id) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { Trezor.obrazek(p) }.onFailure { zprava(ctx, "Fotku nejde zobrazit") }.getOrNull()
                    }
                }
                val b = bmp
                if (b == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else Zoom { Image(b.asImageBitmap(), p.jmeno, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
        }
        val p = o.polozky[stav.currentPage.coerceIn(0, o.polozky.size - 1)]
        HorniPruh(p.jmeno, "${stav.currentPage + 1} / ${o.polozky.size} • ${velikost(p.velikost)}") {}
    }
}
