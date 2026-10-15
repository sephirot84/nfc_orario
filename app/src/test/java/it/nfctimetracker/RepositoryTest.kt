package it.nfctimetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.nfctimetracker.data.*
import it.nfctimetracker.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class RepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var context: Context
    private var now = Instant.parse("2026-10-15T21:59:55Z").toEpochMilli()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("repository-test.db")
        open()
        runBlocking {
            repo.saveEmployee(null, "Mario", "Rossi", "1", true, 480)
            repo.saveEmployee(null, "Anna", "Verdi", "2", true, null)
            repo.bind(1, "01AABBCC"); repo.bind(2, "02AABBCC")
        }
    }
    private fun open() {
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "repository-test.db").addMigrations(TrackerDatabase.MIGRATION_1_2).build()
        repo = TrackerRepository(db) { now }
    }
    @After fun close() { db.close(); context.deleteDatabase("repository-test.db") }
    private suspend fun fails(action: suspend () -> Unit) {
        var failed = false
        try { action() } catch (_: IllegalArgumentException) { failed = true } catch (_: IllegalStateException) { failed = true }
        assertTrue("Expected operation to fail", failed)
    }
    @Test fun tagBindingUniquenessAndReplacement() = runBlocking {
        fails { repo.bind(2, "01AABBCC") }
        repo.bind(1, "aa:bb:cc:dd")
        assertEquals("AABBCCDD", db.dao().tags().find { it.employeeId == 1L }!!.uid)
        fails { repo.punch("01AABBCC") }
    }
    @Test fun alternatingAndDebounceAreIndependentAcrossEmployeesAndMidnight() = runBlocking {
        assertEquals(ENTRY, repo.punch("01AABBCC").event.type)
        assertEquals(ENTRY, repo.punch("02AABBCC").event.type)
        fails { repo.punch("01AABBCC") }
        now += 10_000
        assertEquals(EXIT, repo.punch("01AABBCC").event.type)
        now += 10_000
        assertEquals(ENTRY, repo.punch("01AABBCC").event.type)
        assertEquals(4, db.dao().events().size)
    }
    @Test fun concurrentDuplicateCallbacksCommitOnlyOnce() = runBlocking {
        coroutineScope { (1..12).map { async(Dispatchers.IO) { runCatching { repo.punch("01AABBCC") } } }.awaitAll() }
        assertEquals(1, db.dao().events().size)
    }
    @Test fun confirmedEventsAndDebounceSurviveDatabaseReopen() = runBlocking {
        repo.punch("01AABBCC"); db.close(); open()
        assertEquals(1, db.dao().events().size)
        fails { repo.punch("01AABBCC") }
        now += 10_000
        assertEquals(EXIT, repo.punch("01AABBCC").event.type)
    }
    @Test fun disabledAndDeletedEmployeesCannotPunch() = runBlocking {
        repo.saveEmployee(1, "Mario", "Rossi", "1", false, 480)
        fails { repo.punch("01AABBCC") }
        repo.deleteEmployee(2)
        fails { repo.punch("02AABBCC") }
        assertEquals(2, db.dao().employees().size)
    }
    @Test fun correctionsAndCancellationKeepAuditAndOriginalSource() = runBlocking {
        val punch = repo.punch("01AABBCC")
        repo.saveEvent(punch.event.id, 1, now - 60_000, ENTRY, "Orologio corretto")
        val corrected = db.dao().events().single()
        assertEquals("NFC", corrected.source)
        repo.cancelEvent(corrected.id)
        assertTrue(db.dao().events().single().cancelled)
        val log = db.dao().audit().filter { it.eventId == punch.event.id }
        assertEquals(listOf("CORREZIONE", "ANNULLAMENTO"), log.map { it.action })
        assertTrue(log.first().oldValue.contains(now.toString()))
    }
    @Test fun manualEntriesAffectNextAutomaticPunch() = runBlocking {
        repo.saveEvent(null, 1, now - 60_000, ENTRY, "Entrata mancante")
        assertEquals(EXIT, repo.punch("01AABBCC").event.type)
    }
    @Test fun backupRestorePreservesEventsTagsSettingsAndAudit() = runBlocking {
        repo.punch("01AABBCC")
        repo.setSettings(listOf(Setting("debounce_seconds", "15"), Setting("pin_hash", Pin.hash("1234"))))
        val snapshot = Backup.decode(Backup.encode(repo.snapshot()))
        repo.deleteEmployee(1)
        repo.restore(snapshot)
        assertTrue(db.dao().employee(1)!!.active)
        assertEquals(snapshot.events, db.dao().events())
        assertEquals(snapshot.tags, db.dao().tags())
        assertEquals("15", db.dao().setting("debounce_seconds"))
        assertTrue(Pin.verify("1234", db.dao().setting("pin_hash")!!))
        assertEquals("RIPRISTINO_BACKUP", db.dao().audit().last().action)
    }
    @Test fun invalidRestoreLeavesExistingDataUntouched() = runBlocking {
        repo.punch("01AABBCC")
        val before = repo.snapshot()
        fails { repo.restore(before.copy(tags = before.tags + before.tags.first())) }
        assertEquals(before, repo.snapshot())
    }
    @Test fun clockGoingBackIsRejectedWithoutLosingData() = runBlocking {
        repo.punch("01AABBCC"); now -= 60_000
        fails { repo.punch("01AABBCC") }
        assertEquals(1, db.dao().events().size)
    }
    @Test fun atLeastFiftyEmployeesCanPunchConsecutively() = runBlocking {
        for (index in 3..50) { repo.saveEmployee(null, "Nome$index", "Cognome", "", true, null); repo.bind(index.toLong(), "%08X".format(index)) }
        for (tag in db.dao().tags()) repo.punch(tag.uid)
        assertEquals(50, db.dao().events().size)
        assertTrue(db.dao().events().all { it.type == ENTRY })
    }
    @Test fun migrationFromVersionOnePreservesPeopleEventsAndTags() = runBlocking {
        repo.punch("01AABBCC")
        db.close()
        val sqlite = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("repository-test.db").path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        sqlite.use {
            it.execSQL("PRAGMA foreign_keys=OFF")
            it.execSQL("CREATE TABLE Employees_v1 (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, firstName TEXT NOT NULL, lastName TEXT NOT NULL, number TEXT NOT NULL, active INTEGER NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, dailyMinutes INTEGER)")
            it.execSQL("INSERT INTO Employees_v1 SELECT id, firstName, lastName, number, active, deleted, createdAt, dailyMinutes FROM Employees")
            it.execSQL("DROP TABLE Employees")
            it.execSQL("ALTER TABLE Employees_v1 RENAME TO Employees")
            it.version = 1
        }
        open()
        assertEquals(2, db.dao().employees().size)
        assertEquals(1, db.dao().events().size)
        assertEquals(2, db.dao().tags().size)
        assertNull(db.dao().employee(1)!!.lastScanAt)
        fails { repo.punch("01AABBCC") }
        now += 10_000
        assertEquals(EXIT, repo.punch("01AABBCC").event.type)
    }
}
