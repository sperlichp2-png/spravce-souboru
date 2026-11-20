package cz.sperlich.soubory

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.sperlich.soubory.ui.AplikaceObr
import cz.sperlich.soubory.ui.DomuObr
import cz.sperlich.soubory.ui.KosObr
import cz.sperlich.soubory.ui.MediaDruh
import cz.sperlich.soubory.ui.MediaObr
import cz.sperlich.soubory.ui.NasObr
import cz.sperlich.soubory.ui.NasSeznamObr
import cz.sperlich.soubory.ui.ProhlizecObr
import cz.sperlich.soubory.ui.PrubehDialog
import cz.sperlich.soubory.ui.SeznamDruh
import cz.sperlich.soubory.ui.SeznamObr
import cz.sperlich.soubory.ui.SlozkaObr
import cz.sperlich.soubory.ui.TrezorFotoObr
import cz.sperlich.soubory.ui.TrezorObr
import java.io.File

/** Obrazovky aplikace. Stav, který má přežít návrat zpět, si drží přímo instance. */
sealed class Obr {
    data object Domu : Obr()

    class Slozka(val koren: File, val nazev: String, start: File = koren) : Obr() {
        var dir by mutableStateOf(start)
        var posledniDir: File? = null
        val vybrane = mutableStateListOf<File>()
        val stav = LazyListState()
    }

    class Media(val titulek: String, val druh: MediaDruh) : Obr() {
        val vybrane = mutableStateListOf<File>()
        val stav = LazyGridState()
    }

    class Seznam(val titulek: String, val druh: SeznamDruh) : Obr() {
        val vybrane = mutableStateListOf<File>()
        val stav = LazyListState()
    }

    data object Aplikace : Obr()
    data object Kos : Obr()
    data object Trezor : Obr()
    data object NasSeznam : Obr()

    class Nas(val p: Pripojeni) : Obr() {
        var dir by mutableStateOf<String?>(null)
        var domov: String? = null
        val vybrane = mutableStateListOf<VzdSoubor>()
        val stav = LazyListState()
    }

    class Prohlizec(val soubory: List<File>, val start: Int) : Obr()
    class TrezorFoto(val polozky: List<VTrezoru>, val start: Int) : Obr()
}

object Nav {
    val zasobnik = mutableStateListOf<Obr>(Obr.Domu)
    fun jdi(o: Obr) { zasobnik.add(o) }
    fun zpet() { if (zasobnik.size > 1) zasobnik.removeAt(zasobnik.lastIndex) }
}

class MainActivity : ComponentActivity() {
    private var pristup by mutableStateOf(false)
    private var zastaveno = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Trezor.vycistitDocasne(this)
        setContent {
            Tema {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (pristup) Hlavni() else Povoleni()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pristup = Environment.isExternalStorageManager()
        // Vracíme se z jiné aplikace, kde byl otevřený soubor z trezoru.
        if (zastaveno > 0) {
            if (SystemClock.elapsedRealtime() - zastaveno > 60_000) Trezor.zamknout()
            zastaveno = 0
            Trezor.vycistitDocasne(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        if (Trezor.nezamykat) {
            Trezor.nezamykat = false
            zastaveno = SystemClock.elapsedRealtime()
        } else {
            Trezor.zamknout()
        }
    }
}

@Composable
fun Tema(obsah: @Composable () -> Unit) {
    val tmave = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val barvy = when {
        Build.VERSION.SDK_INT >= 31 -> if (tmave) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        tmave -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = barvy, content = obsah)
}

@Composable
private fun Hlavni() {
    val o = Nav.zasobnik.last()
    BackHandler(Nav.zasobnik.size > 1) { Nav.zpet() }
    key(o) {
        when (o) {
            is Obr.Domu -> DomuObr()
            is Obr.Slozka -> SlozkaObr(o)
            is Obr.Media -> MediaObr(o)
            is Obr.Seznam -> SeznamObr(o)
            is Obr.Aplikace -> AplikaceObr()
            is Obr.Kos -> KosObr()
            is Obr.Trezor -> TrezorObr()
            is Obr.NasSeznam -> NasSeznamObr()
            is Obr.Nas -> NasObr(o)
            is Obr.Prohlizec -> ProhlizecObr(o)
            is Obr.TrezorFoto -> TrezorFotoObr(o)
        }
    }
    PrubehDialog()
}

@Composable
private fun Povoleni() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.FolderOpen, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Přístup ke všem souborům", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Aby správce viděl celé úložiště i SD kartu, povol mu v dalším okně „Povolit přístup ke správě všech souborů“.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
            )
        }) { Text("Povolit přístup") }
    }
}
