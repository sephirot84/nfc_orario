package it.nfctimetracker.data

import com.google.gson.Gson
import java.security.MessageDigest
import java.time.ZoneId

data class BackupData(val employees: List<Employee>, val tags: List<NfcTag>, val events: List<AttendanceEvent>, val audit: List<AuditLog>, val settings: List<Setting>)
private data class BackupEnvelope(val format: String = "NFC-TIME-TRACKER", val version: Int = 1, val payload: String, val sha256: String)

object Backup {
    const val MAX_BYTES = 20 * 1024 * 1024
    val gson = Gson()
    fun encode(data: BackupData): ByteArray {
        validate(data)
        val payload = gson.toJson(data)
        return gson.toJson(BackupEnvelope(payload = payload, sha256 = digest(payload))).toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BYTES) { "Backup troppo grande (massimo 20 MB)" }
        }
    }
    fun decode(bytes: ByteArray): BackupData {
        require(bytes.size <= MAX_BYTES) { "Backup troppo grande" }
        val envelope = gson.fromJson(bytes.toString(Charsets.UTF_8), BackupEnvelope::class.java)
        require(envelope.format == "NFC-TIME-TRACKER" && envelope.version == 1) { "Formato backup non supportato" }
        require(envelope.sha256 == digest(envelope.payload)) { "Backup danneggiato: checksum non valido" }
        return gson.fromJson(envelope.payload, BackupData::class.java).also(::validate)
    }
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun validate(d: BackupData) {
        val employees = requireNotNull(d.employees)
        val tags = requireNotNull(d.tags)
        val events = requireNotNull(d.events)
        val audit = requireNotNull(d.audit)
        val settings = requireNotNull(d.settings)
        fun ids(ids: List<Long>) { require(ids.all { it > 0 } && ids.distinct().size == ids.size) { "ID duplicati o non validi" } }
        require(employees.size <= 10_000 && events.size <= 100_000 && tags.size <= 10_000 && audit.size <= 100_000)
        ids(employees.map { it.id }); ids(tags.map { it.id }); ids(events.map { it.id }); ids(audit.map { it.id })
        val employeeIds = employees.map { it.id }.toSet()
        employees.forEach {
            require(!it.firstName.isNullOrBlank() && it.firstName.length <= 100 && !it.lastName.isNullOrBlank() && it.lastName.length <= 100)
            require(requireNotNull(it.number).length <= 100 && it.createdAt in 0..7_258_118_400_000L && (it.dailyMinutes == null || it.dailyMinutes in 1..1440))
            require(it.lastScanAt == null || it.lastScanAt in 0..7_258_118_400_000L)
        }
        require(tags.map { it.uid }.distinct().size == tags.size && tags.map { it.employeeId }.distinct().size == tags.size)
        tags.forEach { require(requireNotNull(it.uid).matches(Regex("[0-9A-F]{2,64}")) && it.uid.length % 2 == 0 && it.employeeId in employeeIds) }
        events.forEach {
            require(it.employeeId in employeeIds && it.timestamp in 0..7_258_118_400_000L && it.type in listOf("ENTRATA", "USCITA") && it.source in listOf("NFC", "MANUALE"))
            require(requireNotNull(it.note).length <= 2000)
        }
        audit.forEach { requireNotNull(it.action); requireNotNull(it.oldValue); requireNotNull(it.newValue); require(it.changedAt >= 0) }
        require(settings.map { it.key }.distinct().size == settings.size)
        settings.forEach { validateSetting(it) }
    }
    fun validateSetting(s: Setting) {
        requireNotNull(s.key); requireNotNull(s.value)
        when (s.key) {
            "debounce_seconds" -> require(s.value.toInt() in 1..3600)
            "keep_screen_on" -> require(s.value in listOf("true", "false"))
            "zone" -> ZoneId.of(s.value)
            "pin_hash" -> require(s.value.isEmpty() || Pin.validEncoding(s.value))
            else -> error("Impostazione non riconosciuta: ${s.key}")
        }
    }
}
