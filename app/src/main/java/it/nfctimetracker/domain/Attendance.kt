package it.nfctimetracker.domain

import it.nfctimetracker.data.AttendanceEvent
import java.time.*

const val ENTRY = "ENTRATA"
const val EXIT = "USCITA"

data class WorkInterval(val entry: AttendanceEvent, val exit: AttendanceEvent)
data class Pairing(val intervals: List<WorkInterval>, val incomplete: List<AttendanceEvent>)
data class Period(val start: Instant, val end: Instant) {
    init { require(end > start) { "Intervallo non valido" } }
    fun contains(timestamp: Long) = timestamp >= start.toEpochMilli() && timestamp < end.toEpochMilli()
    companion object {
        fun dates(first: LocalDate, last: LocalDate, zone: ZoneId) = Period(first.atStartOfDay(zone).toInstant(), last.plusDays(1).atStartOfDay(zone).toInstant())
    }
}

object Attendance {
    fun pair(events: List<AttendanceEvent>): Pairing {
        val intervals = mutableListOf<WorkInterval>()
        val incomplete = mutableListOf<AttendanceEvent>()
        events.filterNot { it.cancelled }.groupBy { it.employeeId }.values.forEach { employeeEvents ->
            var pending: AttendanceEvent? = null
            employeeEvents.sortedWith(compareBy({ it.timestamp }, { it.id })).forEach { event ->
                if (event.type == ENTRY) {
                    pending?.let { incomplete += it }
                    pending = event
                } else if (event.type == EXIT) {
                    val entry = pending
                    if (entry == null || event.timestamp <= entry.timestamp) {
                        incomplete += event
                    } else {
                        intervals += WorkInterval(entry, event)
                        pending = null
                    }
                }
            }
            pending?.let { incomplete += it }
        }
        return Pairing(intervals.sortedBy { it.entry.timestamp }, incomplete.sortedBy { it.timestamp })
    }

    fun millis(interval: WorkInterval, period: Period? = null): Long {
        val start = maxOf(interval.entry.timestamp, period?.start?.toEpochMilli() ?: Long.MIN_VALUE)
        val end = minOf(interval.exit.timestamp, period?.end?.toEpochMilli() ?: Long.MAX_VALUE)
        return (end - start).coerceAtLeast(0)
    }
    fun total(events: List<AttendanceEvent>, period: Period? = null) = pair(events).intervals.sumOf { millis(it, period) }

    fun daily(events: List<AttendanceEvent>, zone: ZoneId, period: Period? = null): Map<LocalDate, Long> {
        val days = sortedMapOf<LocalDate, Long>()
        pair(events).intervals.forEach { interval ->
            var start = Instant.ofEpochMilli(maxOf(interval.entry.timestamp, period?.start?.toEpochMilli() ?: Long.MIN_VALUE))
            val end = Instant.ofEpochMilli(minOf(interval.exit.timestamp, period?.end?.toEpochMilli() ?: Long.MAX_VALUE))
            while (start < end) {
                val date = start.atZone(zone).toLocalDate()
                val next = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), end)
                days[date] = (days[date] ?: 0) + Duration.between(start, next).toMillis()
                start = next
            }
        }
        return days
    }

    // Theoretical schedule: Monday–Friday, beginning on the employee registration date.
    fun theoreticalMillis(first: LocalDate, last: LocalDate, registered: LocalDate, minutes: Int): Long {
        var day = maxOf(first, registered)
        var count = 0L
        while (!day.isAfter(last)) {
            if (day.dayOfWeek.value <= 5) count++
            day = day.plusDays(1)
        }
        return count * minutes * 60_000L
    }
}

fun durationLabel(millis: Long): String {
    val minutes = kotlin.math.abs(millis) / 60_000
    return "${if (millis < 0) "−" else ""}${minutes / 60} h ${minutes % 60} min"
}
