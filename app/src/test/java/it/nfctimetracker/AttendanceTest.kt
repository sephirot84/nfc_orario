package it.nfctimetracker

import it.nfctimetracker.data.AttendanceEvent
import it.nfctimetracker.domain.*
import it.nfctimetracker.domain.Period
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AttendanceTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun event(id: Long, time: String, type: String, employee: Long = 1, cancelled: Boolean = false) =
        AttendanceEvent(id, employee, ZonedDateTime.parse(time).toInstant().toEpochMilli(), type, cancelled = cancelled)
    private fun utc(id: Long, hour: String, type: String, employee: Long = 1) = event(id, "2026-10-15T${hour}:00Z", type, employee)
    @Test fun multipleIntervalsWithoutLunchDeduction() {
        val events = listOf(utc(1, "08:00", ENTRY), utc(2, "12:00", EXIT), utc(3, "13:00", ENTRY), utc(4, "17:30", EXIT))
        assertEquals(30_600_000L, Attendance.total(events))
        assertEquals("8 h 30 min", durationLabel(Attendance.total(events)))
    }
    @Test fun employeesAreIndependentEvenWhenInterleaved() {
        val events = listOf(utc(1, "08:00", ENTRY), utc(2, "08:01", ENTRY, 2), utc(3, "09:00", EXIT), utc(4, "10:01", EXIT, 2))
        assertEquals(3 * 3_600_000L, Attendance.total(events))
        assertTrue(Attendance.pair(events).incomplete.isEmpty())
    }
    @Test fun nightShiftIsSplitAcrossDates() {
        val events = listOf(event(1, "2026-10-15T22:00:00+02:00", ENTRY), event(2, "2026-10-16T06:00:00+02:00", EXIT))
        val daily = Attendance.daily(events, zone)
        assertEquals(2 * 3_600_000L, daily[LocalDate.parse("2026-10-15")])
        assertEquals(6 * 3_600_000L, daily[LocalDate.parse("2026-10-16")])
        assertEquals(6 * 3_600_000L, Attendance.total(events, Period.dates(LocalDate.parse("2026-10-16"), LocalDate.parse("2026-10-16"), zone)))
    }
    @Test fun springDstUsesElapsedTime() {
        val events = listOf(event(1, "2026-03-29T00:00:00+01:00", ENTRY), event(2, "2026-03-29T04:00:00+02:00", EXIT))
        assertEquals(3 * 3_600_000L, Attendance.total(events))
    }
    @Test fun autumnDstUsesElapsedTime() {
        val events = listOf(event(1, "2026-10-25T00:00:00+02:00", ENTRY), event(2, "2026-10-25T04:00:00+01:00", EXIT))
        assertEquals(5 * 3_600_000L, Attendance.total(events))
    }
    @Test fun openEntryDoesNotInventExit() {
        val events = listOf(utc(1, "08:00", ENTRY))
        assertEquals(0L, Attendance.total(events))
        assertEquals(listOf(1L), Attendance.pair(events).incomplete.map { it.id })
    }
    @Test fun orphanExitIsReported() {
        assertEquals(1, Attendance.pair(listOf(utc(1, "08:00", EXIT))).incomplete.size)
    }
    @Test fun consecutiveEntriesFlagEarlierEntry() {
        val events = listOf(utc(1, "08:00", ENTRY), utc(2, "09:00", ENTRY), utc(3, "10:00", EXIT))
        assertEquals(3_600_000L, Attendance.total(events))
        assertEquals(listOf(1L), Attendance.pair(events).incomplete.map { it.id })
    }
    @Test fun cancelledEventsDoNotContribute() {
        val events = listOf(utc(1, "08:00", ENTRY), utc(2, "09:00", EXIT).copy(cancelled = true))
        assertEquals(0L, Attendance.total(events))
        assertEquals(1, Attendance.pair(events).incomplete.size)
    }
    @Test fun equalTimestampsAreAnomalous() {
        val events = listOf(utc(1, "08:00", ENTRY), utc(2, "08:00", EXIT))
        assertEquals(0L, Attendance.total(events))
        assertEquals(2, Attendance.pair(events).incomplete.size)
    }
    @Test fun filteringKeepsEntryBeforePeriod() {
        val events = listOf(event(1, "2026-09-30T22:00:00Z", ENTRY), event(2, "2026-10-01T06:00:00Z", EXIT))
        assertEquals(6 * 3_600_000L, Attendance.total(events, Period.dates(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-01"), ZoneOffset.UTC)))
    }
    @Test fun theoreticalScheduleExcludesWeekendsAndPreRegistration() {
        assertEquals(2 * 8 * 3_600_000L, Attendance.theoreticalMillis(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-15"), 480))
    }
}
