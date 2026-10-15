package it.nfctimetracker

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.nfctimetracker.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: TrackerViewModel by viewModels()
    private val adapter by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var nfcStatus by mutableStateOf("Verifica NFC…")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { TrackerTheme { TrackerApp(model, nfcStatus, {
            runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }.onFailure { model.notice.value = "Impostazioni NFC non disponibili su questo dispositivo" }
        }) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.vibration.collect { @Suppress("DEPRECATION") val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator; vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)) } }
                launch { model.state.collect { s ->
                    if (s?.keepScreenOn == true) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } }
                launch { while (true) { updateNfc(); delay(1000) } }
            }
        }
    }
    private fun updateNfc() { nfcStatus = when { adapter == null -> "NFC non disponibile"; adapter?.isEnabled != true -> "NFC disattivato"; else -> "NFC pronto" } }
    override fun onResume() {
        super.onResume(); updateNfc()
        adapter?.enableReaderMode(this, { tag ->
            if (tag.id.isNotEmpty()) model.onTag(tag.id.joinToString("") { "%02X".format(it) })
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null)
    }
    override fun onPause() { adapter?.disableReaderMode(this); super.onPause() }
    override fun onStop() { if (!isChangingConfigurations && !model.filePickerOpen) model.lock(); super.onStop() }
}
