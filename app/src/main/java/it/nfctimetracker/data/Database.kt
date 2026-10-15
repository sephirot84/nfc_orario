package it.nfctimetracker.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "Employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String, val lastName: String, val number: String = "",
    val active: Boolean = true, val deleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val dailyMinutes: Int? = null, val lastScanAt: Long? = null,
) { val fullName: String get() = "$firstName $lastName" }

@Entity(tableName = "NfcTags", indices = [Index(value = ["uid"], unique = true), Index(value = ["employeeId"], unique = true)],
    foreignKeys = [ForeignKey(entity = Employee::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT)])
data class NfcTag(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uid: String, val employeeId: Long, val active: Boolean = true)

@Entity(tableName = "AttendanceEvents", indices = [Index(value = ["employeeId", "timestamp"])],
    foreignKeys = [ForeignKey(entity = Employee::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT)])
data class AttendanceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long, val timestamp: Long, val type: String,
    val source: String = "NFC", val cancelled: Boolean = false, val note: String = "",
)

@Entity(tableName = "AuditLogs", indices = [Index("eventId")])
data class AuditLog(@PrimaryKey(autoGenerate = true) val id: Long = 0, val eventId: Long? = null,
    val action: String, val oldValue: String, val newValue: String, val changedAt: Long = System.currentTimeMillis())

@Entity(tableName = "Settings")
data class Setting(@PrimaryKey val key: String, val value: String)

@Dao
interface TrackerDao {
    @Query("SELECT * FROM Employees ORDER BY lastName COLLATE NOCASE, firstName COLLATE NOCASE") fun observeEmployees(): Flow<List<Employee>>
    @Query("SELECT * FROM AttendanceEvents ORDER BY timestamp, id") fun observeEvents(): Flow<List<AttendanceEvent>>
    @Query("SELECT * FROM NfcTags") fun observeTags(): Flow<List<NfcTag>>
    @Query("SELECT * FROM Settings") fun observeSettings(): Flow<List<Setting>>
    @Query("SELECT * FROM AuditLogs ORDER BY changedAt DESC, id DESC") fun observeAudit(): Flow<List<AuditLog>>
    @Query("SELECT * FROM Employees") suspend fun employees(): List<Employee>
    @Query("SELECT * FROM AttendanceEvents ORDER BY timestamp, id") suspend fun events(): List<AttendanceEvent>
    @Query("SELECT * FROM NfcTags") suspend fun tags(): List<NfcTag>
    @Query("SELECT * FROM Settings") suspend fun settings(): List<Setting>
    @Query("SELECT * FROM AuditLogs ORDER BY changedAt, id") suspend fun audit(): List<AuditLog>
    @Query("SELECT * FROM Employees WHERE id = :id") suspend fun employee(id: Long): Employee?
    @Query("SELECT * FROM AttendanceEvents WHERE id = :id") suspend fun event(id: Long): AttendanceEvent?
    @Query("SELECT * FROM NfcTags WHERE uid = :uid") suspend fun tag(uid: String): NfcTag?
    @Query("SELECT * FROM AttendanceEvents WHERE employeeId = :id AND cancelled = 0 ORDER BY timestamp DESC, id DESC LIMIT 1") suspend fun latest(id: Long): AttendanceEvent?
    @Query("SELECT value FROM Settings WHERE `key` = :key") suspend fun setting(key: String): String?
    @Insert suspend fun insertEmployee(employee: Employee): Long
    @Update suspend fun updateEmployee(employee: Employee)
    @Insert suspend fun insertTag(tag: NfcTag): Long
    @Query("DELETE FROM NfcTags WHERE employeeId = :id OR uid = :uid") suspend fun removeTags(id: Long, uid: String)
    @Insert suspend fun insertEvent(event: AttendanceEvent): Long
    @Update suspend fun updateEvent(event: AttendanceEvent)
    @Insert suspend fun insertAudit(log: AuditLog): Long
    @Upsert suspend fun set(setting: Setting)
    @Insert suspend fun insertEmployees(items: List<Employee>)
    @Insert suspend fun insertTags(items: List<NfcTag>)
    @Insert suspend fun insertEvents(items: List<AttendanceEvent>)
    @Insert suspend fun insertAudit(items: List<AuditLog>)
    @Query("DELETE FROM AttendanceEvents") suspend fun clearEvents()
    @Query("DELETE FROM NfcTags") suspend fun clearTags()
    @Query("DELETE FROM Employees") suspend fun clearEmployees()
    @Query("DELETE FROM AuditLogs") suspend fun clearAudit()
    @Query("DELETE FROM Settings") suspend fun clearSettings()
}

@Database(entities = [Employee::class, NfcTag::class, AttendanceEvent::class, AuditLog::class, Setting::class], version = 2, exportSchema = true)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao
    companion object {
        // v1 did not persist the employee debounce timestamp; never discard existing data.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE Employees ADD COLUMN lastScanAt INTEGER") }
        }
    }
}
