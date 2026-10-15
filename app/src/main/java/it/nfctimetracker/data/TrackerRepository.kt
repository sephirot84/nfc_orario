package it.nfctimetracker.data

import androidx.room.withTransaction
import it.nfctimetracker.domain.*
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

data class TrackerState(val employees: List<Employee> = emptyList(), val events: List<AttendanceEvent> = emptyList(),
    val tags: List<NfcTag> = emptyList(), val settings: Map<String, String> = emptyMap(), val audit: List<AuditLog> = emptyList()) {
    val zone: ZoneId get() = runCatching { ZoneId.of(settings["zone"] ?: "Europe/Rome") }.getOrDefault(ZoneId.of("Europe/Rome"))
    val debounceSeconds: Int get() = settings["debounce_seconds"]?.toIntOrNull() ?: 10
    val keepScreenOn: Boolean get() = settings["keep_screen_on"] != "false"
    val pinHash: String get() = settings["pin_hash"].orEmpty()
}
data class PunchResult(val employee: Employee, val event: AttendanceEvent, val workedToday: Long)

class TrackerRepository(private val db: TrackerDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.dao()
    val state = combine(dao.observeEmployees(), dao.observeEvents(), dao.observeTags(), dao.observeSettings(), dao.observeAudit()) { employees, events, tags, settings, audit ->
        TrackerState(employees, events, tags, settings.associate { it.key to it.value }, audit)
    }
    suspend fun punch(rawUid: String): PunchResult = db.withTransaction {
        val tag = dao.tag(normalizeUid(rawUid))?.takeIf { it.active } ?: error("Tag non riconosciuto. Contatta l’amministratore.")
        val employee = dao.employee(tag.employeeId)?.takeIf { it.active && !it.deleted } ?: error("Dipendente disattivato")
        val now = clock()
        val latest = dao.latest(employee.id)
        val seconds = dao.setting("debounce_seconds")?.toInt() ?: 10
        val lastScan = maxOf(employee.lastScanAt ?: Long.MIN_VALUE, latest?.timestamp ?: Long.MIN_VALUE)
        if (lastScan != Long.MIN_VALUE) {
            require(now >= lastScan) { "L’orologio è precedente all’ultima timbratura. Verifica data e ora." }
            require(now - lastScan >= seconds * 1000L) { "Timbratura già registrata. Attendi $seconds secondi." }
        }
        val event = AttendanceEvent(employeeId = employee.id, timestamp = now, type = if (latest?.type == ENTRY) EXIT else ENTRY)
        val id = dao.insertEvent(event)
        dao.updateEmployee(employee.copy(lastScanAt = now))
        val zone = ZoneId.of(dao.setting("zone") ?: "Europe/Rome")
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val worked = Attendance.total(dao.events().filter { it.employeeId == employee.id }, Period.dates(today, today, zone))
        PunchResult(employee, event.copy(id = id), worked)
    }
    suspend fun saveEmployee(id: Long?, first: String, last: String, number: String, active: Boolean, dailyMinutes: Int?) = db.withTransaction {
        require(first.trim().isNotEmpty() && last.trim().isNotEmpty() && first.length <= 100 && last.length <= 100) { "Inserisci nome e cognome (massimo 100 caratteri)" }
        require(number.length <= 100 && (dailyMinutes == null || dailyMinutes in 1..1440)) { "Ore teoriche non valide" }
        val old = id?.let { requireNotNull(dao.employee(it)) }
        require(old?.deleted != true)
        val employee = (old ?: Employee(firstName = first.trim(), lastName = last.trim(), createdAt = clock())).copy(firstName = first.trim(), lastName = last.trim(), number = number.trim(), active = active, dailyMinutes = dailyMinutes)
        val newId = if (old == null) dao.insertEmployee(employee) else { dao.updateEmployee(employee); employee.id }
        audit(null, "DIPENDENTE", old, employee.copy(id = newId))
    }
    suspend fun deleteEmployee(id: Long) = db.withTransaction {
        val old = requireNotNull(dao.employee(id))
        val next = old.copy(active = false, deleted = true)
        dao.updateEmployee(next); audit(null, "ELIMINAZIONE_LOGICA", old, next)
    }
    suspend fun bind(id: Long, rawUid: String) = db.withTransaction {
        val employee = requireNotNull(dao.employee(id))
        require(!employee.deleted) { "Dipendente eliminato" }
        val uid = normalizeUid(rawUid)
        val existing = dao.tag(uid)
        val owner = existing?.let { dao.employee(it.employeeId) }
        require(existing == null || existing.employeeId == id || owner?.deleted == true) { "Tag già associato a ${owner?.fullName}" }
        val old = dao.tags().find { it.employeeId == id }
        dao.removeTags(id, uid)
        dao.insertTag(NfcTag(uid = uid, employeeId = id))
        audit(null, "ASSOCIA_TAG", old, "${employee.fullName}: $uid")
    }
    suspend fun unbind(id: Long) = db.withTransaction {
        val old = dao.tags().find { it.employeeId == id }
        dao.removeTags(id, ""); audit(null, "RIMUOVI_TAG", old, null)
    }
    suspend fun saveEvent(id: Long?, employeeId: Long, timestamp: Long, type: String, note: String) = db.withTransaction {
        require(type in listOf(ENTRY, EXIT) && timestamp in 0..7_258_118_400_000L && note.length <= 2000) { "Timbratura non valida" }
        requireNotNull(dao.employee(employeeId))
        val old = id?.let { requireNotNull(dao.event(it)) }
        require(old == null || old.employeeId == employeeId) { "Il dipendente della timbratura non può cambiare" }
        val event = (old ?: AttendanceEvent(employeeId = employeeId, timestamp = timestamp, type = type, source = "MANUALE"))
            .copy(timestamp = timestamp, type = type, note = note.trim())
        val newId = if (old == null) dao.insertEvent(event) else { dao.updateEvent(event); event.id }
        audit(newId, if (old == null) "AGGIUNTA_MANUALE" else "CORREZIONE", old, event.copy(id = newId))
    }
    suspend fun cancelEvent(id: Long) = db.withTransaction {
        val old = requireNotNull(dao.event(id)); require(!old.cancelled)
        val next = old.copy(cancelled = true)
        dao.updateEvent(next); audit(id, "ANNULLAMENTO", old, next)
    }
    suspend fun setSettings(settings: List<Setting>) = db.withTransaction {
        settings.forEach { setting ->
            Backup.validateSetting(setting)
            val old = dao.setting(setting.key)
            dao.set(setting)
            // PIN hashes and salts do not belong in audit history.
            audit(null, "IMPOSTAZIONE_${setting.key}", if (setting.key == "pin_hash") "protetto" else old, if (setting.key == "pin_hash") "protetto" else setting.value)
        }
    }
    suspend fun snapshot(): BackupData = db.withTransaction { BackupData(dao.employees(), dao.tags(), dao.events(), dao.audit(), dao.settings()) }
    suspend fun restore(data: BackupData) = db.withTransaction {
        Backup.validate(data)
        dao.clearEvents(); dao.clearTags(); dao.clearEmployees(); dao.clearAudit(); dao.clearSettings()
        dao.insertEmployees(data.employees); dao.insertTags(data.tags); dao.insertEvents(data.events); dao.insertAudit(data.audit)
        data.settings.forEach { dao.set(it) }
        audit(null, "RIPRISTINO_BACKUP", null, "${data.employees.size} dipendenti, ${data.events.size} timbrature")
    }
    private suspend fun audit(id: Long?, action: String, old: Any?, new: Any?) {
        dao.insertAudit(AuditLog(eventId = id, action = action, oldValue = Backup.gson.toJson(old), newValue = Backup.gson.toJson(new), changedAt = clock()))
    }
    companion object {
        fun normalizeUid(uid: String): String = uid.replace(":", "").replace(" ", "").uppercase(java.util.Locale.ROOT).also {
            require(it.matches(Regex("[0-9A-F]{2,64}")) && it.length % 2 == 0) { "UID NFC non valido" }
        }
    }
}
