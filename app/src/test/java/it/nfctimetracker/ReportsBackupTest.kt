package it.nfctimetracker

import it.nfctimetracker.data.*
import it.nfctimetracker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.*
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ReportsBackupTest {
    private val employee = Employee(1, "Mario", "Rossi", createdAt = 1)
    private val time = Instant.parse("2026-10-15T08:00:00Z").toEpochMilli()
    private val events = listOf(AttendanceEvent(1, 1, time, ENTRY), AttendanceEvent(2, 1, time + 30_600_000, EXIT))
    private val data = BackupData(listOf(employee), listOf(NfcTag(1, "01AABBCC", 1)), events, emptyList(), listOf(Setting("debounce_seconds", "10")))
    private val filter = ReportFilter(null, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
    @Test fun backupRoundTripIncludesAssociationsAndSettings() {
        assertEquals(data, Backup.decode(Backup.encode(data)))
    }
    @Test fun damagedBackupIsRejected() {
        val bytes = Backup.encode(data).toString(Charsets.UTF_8).replace("Mario", "Maria").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(bytes) }
    }
    @Test fun duplicateUidIsRejectedBeforeRestore() {
        val invalid = data.copy(employees = data.employees + employee.copy(id = 2), tags = data.tags + NfcTag(2, "01AABBCC", 2))
        assertThrows(IllegalArgumentException::class.java) { Backup.validate(invalid) }
    }
    @Test fun orphanEventIsRejectedBeforeRestore() {
        assertThrows(IllegalArgumentException::class.java) { Backup.validate(data.copy(events = listOf(events[0].copy(employeeId = 999)))) }
    }
    @Test fun malformedJsonAndUnsupportedVersionAreRejected() {
        assertThrows(Exception::class.java) { Backup.decode("{}".toByteArray()) }
        val text = Backup.encode(data).toString(Charsets.UTF_8).replace("\"version\":1", "\"version\":99")
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(text.toByteArray()) }
    }
    @Test fun xlsxContainsThreeReadableXmlSheetsAndNumericDuration() {
        val bytes = Reports.xlsx(Reports.sheets(data, filter, ZoneOffset.UTC))
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; files[entry.name] = zip.readBytes() }
        }
        assertEquals(3, files.keys.count { it.startsWith("xl/worksheets/sheet") })
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        files.values.forEach { parser.parse(ByteArrayInputStream(it)) }
        val xml = files.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertTrue(xml.contains("<v>${30_600_000 / 86_400_000.0}</v>"))
        assertTrue(files.getValue("xl/workbook.xml").toString(Charsets.UTF_8).contains("Riepilogo mensile"))
        // Keep this run's report as an artifact for validation with an independent XLSX reader.
        val artifact = java.io.File("build/test-artifacts/report.xlsx")
        requireNotNull(artifact.parentFile).mkdirs()
        artifact.writeBytes(bytes)
    }
    @Test fun exportClipsNightShiftAndKeepsIncompleteRows() {
        val night = listOf(events[0].copy(timestamp = Instant.parse("2026-09-30T22:00:00Z").toEpochMilli()), events[1].copy(timestamp = Instant.parse("2026-10-01T06:00:00Z").toEpochMilli()))
        val sheets = Reports.sheets(data.copy(events = night), filter, ZoneOffset.UTC)
        assertEquals(21_600_000L, sheets[0].rows[1][4].durationMillis)
        val incomplete = Reports.sheets(data.copy(events = listOf(events[0])), filter, ZoneOffset.UTC)
        assertTrue(incomplete[0].rows[1].last().text.contains("INCOMPLETA"))
        assertNull(incomplete[0].rows[1][4].durationMillis)
    }
    @Test fun csvEscapesFormulaAndQuoteInjection() {
        val bytes = Reports.csv(ReportSheet("test", listOf(listOf(ReportCell("=HYPERLINK(\"evil\")"), ReportCell("Rossi;Mario")))))
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFF"))
        assertTrue(text.contains("\"'=HYPERLINK(\"\"evil\"\")\""))
        assertTrue(text.contains("\"Rossi;Mario\""))
    }
    @Test fun pinIsSaltedAndVerifiesWithoutStoringPlainText() {
        val one = Pin.hash("123456"); val two = Pin.hash("123456")
        assertNotEquals(one, two); assertTrue(Pin.verify("123456", one)); assertFalse(Pin.verify("000000", one)); assertTrue(Pin.validEncoding(one))
    }
}
