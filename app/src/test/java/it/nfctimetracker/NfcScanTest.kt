package it.nfctimetracker

import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import it.nfctimetracker.data.Setting
import it.nfctimetracker.domain.ENTRY
import it.nfctimetracker.domain.EXIT
import it.nfctimetracker.ui.TrackerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNfcAdapter
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = TrackerApplication::class)
class NfcScanTest {
    private var testedApp: TrackerApplication? = null
    private fun clearDatabase(app: TrackerApplication) = runBlocking {
        val db = app.database
        db.withTransaction {
            val dao = db.dao()
            dao.clearEvents(); dao.clearTags(); dao.clearEmployees(); dao.clearAudit(); dao.clearSettings()
            // Room's generated IDs must be predictable for the seeded badge owners.
            db.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence")
        }
    }
    @get:Rule(order = 0) val nfcHardware = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<TrackerApplication>()
            shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
            ShadowNfcAdapter.setNfcHardwareExists(true)
        }
        override fun after() {
            testedApp?.let { clearDatabase(it) }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun readerCallbackKeepsBadgesIndependentAndRejectsUnknownUid() {
        val app = compose.activity.application as TrackerApplication
        testedApp = app
        clearDatabase(app)
        val reader = shadowOf(NfcAdapter.getDefaultAdapter(compose.activity))
        assertTrue(reader.isInReaderMode)
        val repo = app.repository
        runBlocking {
            repo.saveEmployee(null, "Mario", "Rossi", "1", true, null)
            repo.saveEmployee(null, "Anna", "Verdi", "2", true, null)
            repo.bind(1, "0180ABFF")
            repo.bind(2, "0280ABFF")
            repo.setSettings(listOf(Setting("debounce_seconds", "1")))
        }
        compose.waitUntil(20_000) {
            ViewModelProvider(compose.activity)[TrackerViewModel::class.java].state.value?.employees?.size == 2
        }

        fun scan(uid: String) {
            val tag = ShadowNfcAdapter.createMockTag()
            ReflectionHelpers.setField(tag, "mId", uid.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            reader.dispatchTagDiscovered(tag)
            compose.waitUntil(20_000) {
                ViewModelProvider(compose.activity)[TrackerViewModel::class.java].feedback.value?.uid == uid
            }
            compose.onNodeWithText("UID $uid").performScrollTo().assertIsDisplayed()
        }
        fun events() = runBlocking { repo.snapshot().events }

        scan("0180ABFF")
        compose.onNodeWithText("MARIO ROSSI").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(1L to ENTRY), events().map { it.employeeId to it.type })
        scan("0280ABFF")
        compose.onNodeWithText("ANNA VERDI").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(1L to ENTRY, 2L to ENTRY), events().map { it.employeeId to it.type })

        val beforeUnknown = runBlocking { repo.snapshot() }
        scan("0380ABFF")
        compose.onNodeWithText("Tag non riconosciuto. Contatta l’amministratore.").performScrollTo().assertIsDisplayed()
        assertEquals(beforeUnknown, runBlocking { repo.snapshot() })

        compose.waitUntil(10_000) { System.currentTimeMillis() - events().last().timestamp >= 1_000 }
        scan("0180ABFF")
        compose.onNodeWithText("MARIO ROSSI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(EXIT).performScrollTo().assertIsDisplayed()
        scan("0280ABFF")
        compose.onNodeWithText("ANNA VERDI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(EXIT).performScrollTo().assertIsDisplayed()
        assertEquals(listOf(1L to ENTRY, 2L to ENTRY, 1L to EXIT, 2L to EXIT), events().map { it.employeeId to it.type })
    }
}
