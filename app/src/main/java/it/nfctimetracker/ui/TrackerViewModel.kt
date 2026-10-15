package it.nfctimetracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.nfctimetracker.TrackerApplication
import it.nfctimetracker.data.*
import it.nfctimetracker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScanFeedback(val text: String, val punch: PunchResult? = null, val success: Boolean = false, val uid: String? = null)

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as TrackerApplication).repository
    val state: StateFlow<TrackerState?> = repo.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val admin = MutableStateFlow(false)
    val pairingEmployee = MutableStateFlow<Long?>(null)
    val feedback = MutableStateFlow<ScanFeedback?>(null)
    val notice = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val pendingRestore = MutableStateFlow<BackupData?>(null)
    val vibration = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val scanMutex = Mutex()
    private var feedbackJob: Job? = null
    private var failedPins = 0
    private var pinRetryAt = 0L
    private var exportBytes: ByteArray? = null
    var filePickerOpen = false

    fun unlock(pin: String, done: (Boolean) -> Unit) {
        val snapshot = state.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            if (android.os.SystemClock.elapsedRealtime() < pinRetryAt) {
                notice.value = "Troppi tentativi: attendi 30 secondi"; withContext(Dispatchers.Main) { done(false) }; return@launch
            }
            val valid = snapshot.pinHash.isEmpty() || Pin.verify(pin, snapshot.pinHash)
            if (valid) { failedPins = 0; admin.value = true; feedback.value = null }
            else {
                failedPins++
                if (failedPins >= 5) { pinRetryAt = android.os.SystemClock.elapsedRealtime() + 30_000; failedPins = 0 }
                notice.value = "PIN non corretto"
            }
            withContext(Dispatchers.Main) { done(valid) }
        }
    }
    fun lock() { admin.value = false; pairingEmployee.value = null; pendingRestore.value = null; exportBytes = null }
    fun associate(id: Long) { if (admin.value) pairingEmployee.value = id }
    fun cancelAssociation() { pairingEmployee.value = null }

    fun onTag(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            scanMutex.withLock {
                if (state.value == null || busy.value) return@withLock
                try {
                    val pairing = pairingEmployee.value
                    if (admin.value) {
                        if (pairing != null) {
                            repo.bind(pairing, uid); pairingEmployee.value = null
                            notice.value = "Tag associato correttamente"; vibration.tryEmit(Unit)
                        }
                    } else {
                        val result = repo.punch(uid)
                        showFeedback(ScanFeedback("Timbratura registrata", result, true, TrackerRepository.normalizeUid(uid))); vibration.tryEmit(Unit)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (admin.value) notice.value = e.message ?: "Impossibile associare il tag"
                    else showFeedback(ScanFeedback(e.message ?: "Errore di lettura", uid = runCatching { TrackerRepository.normalizeUid(uid) }.getOrNull()))
                }
            }
        }
    }
    private fun showFeedback(value: ScanFeedback) {
        feedbackJob?.cancel(); feedback.value = value
        feedbackJob = viewModelScope.launch { delay(3000); feedback.value = null }
    }
    fun adminAction(message: String, done: () -> Unit = {}, action: suspend TrackerRepository.() -> Unit) {
        if (!admin.value || busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.action() }
                notice.value = message; done()
            } catch (e: Exception) { if (e is CancellationException) throw e; notice.value = e.message ?: "Operazione non riuscita" }
            finally { busy.value = false }
        }
    }
    fun saveSettings(seconds: Int, screenOn: Boolean, zone: String, newPin: String?, done: () -> Unit) {
        adminAction("Impostazioni salvate", done) {
            val values = mutableListOf(Setting("debounce_seconds", seconds.toString()), Setting("keep_screen_on", screenOn.toString()), Setting("zone", zone))
            if (newPin != null) values += Setting("pin_hash", if (newPin.isEmpty()) "" else Pin.hash(newPin))
            setSettings(values)
        }
    }
    fun prepareExport(kind: String, filter: ReportFilter, ready: () -> Unit) {
        adminAction("File pronto") {
            val snapshot = snapshot()
            exportBytes = when (kind) {
                "backup" -> Backup.encode(snapshot)
                "employees" -> Reports.employeesCsv(snapshot)
                else -> {
                    val sheets = Reports.sheets(snapshot, filter, this@TrackerViewModel.state.value!!.zone)
                    if (kind == "xlsx") Reports.xlsx(sheets) else Reports.csv(sheets[1])
                }
            }
            withContext(Dispatchers.Main) { if (admin.value) ready() }
        }
    }
    fun writeExport(uri: Uri?) {
        if (uri == null) { exportBytes = null; return }
        val bytes = exportBytes ?: return
        adminAction("File salvato") {
            val resolver = getApplication<Application>().contentResolver
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes); it.flush() }
            exportBytes = null
        }
    }
    fun readRestore(uri: Uri?) {
        if (uri == null) return
        adminAction("Backup verificato. Conferma il ripristino.") {
            val bytes = requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use { it.readBytesLimited(Backup.MAX_BYTES) }
            pendingRestore.value = Backup.decode(bytes)
        }
    }
    fun confirmRestore() {
        val data = pendingRestore.value ?: return
        adminAction("Backup ripristinato", { lock() }) { restore(data) }
    }
    private fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(out.size() + count <= max) { "Backup troppo grande" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
