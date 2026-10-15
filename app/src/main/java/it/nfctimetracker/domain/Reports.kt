package it.nfctimetracker.domain

import it.nfctimetracker.data.*
import java.io.ByteArrayOutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ReportFilter(val employeeId: Long?, val first: LocalDate, val last: LocalDate) {
    init { require(!last.isBefore(first) && java.time.temporal.ChronoUnit.DAYS.between(first, last) <= 3660) { "Scegli un periodo fino a 10 anni" } }
    fun period(zone: ZoneId) = Period.dates(first, last, zone)
}
data class ReportCell(val text: String = "", val durationMillis: Long? = null)
data class ReportSheet(val name: String, val rows: List<List<ReportCell>>)

object Reports {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private fun row(vararg values: String) = values.map { ReportCell(it) }
    fun sheets(data: BackupData, filter: ReportFilter, zone: ZoneId): List<ReportSheet> {
        val employees = data.employees.filter { filter.employeeId == null || it.id == filter.employeeId }.sortedBy { it.fullName }
        val period = filter.period(zone)
        val daily = mutableListOf(row("Data", "Dipendente", "Entrata", "Uscita", "Ore", "Matricola", "Note"))
        val detail = mutableListOf(row("Data", "Ora", "Dipendente", "Tipo", "Origine", "Stato", "Note", "ID"))
        val monthly = mutableListOf(row("Mese", "Dipendente", "Ore", "Matricola"))
        employees.forEach { employee ->
            val events = data.events.filter { it.employeeId == employee.id }
            val pairing = Attendance.pair(events)
            pairing.intervals.forEach { interval ->
                var start = Instant.ofEpochMilli(maxOf(interval.entry.timestamp, period.start.toEpochMilli()))
                val end = Instant.ofEpochMilli(minOf(interval.exit.timestamp, period.end.toEpochMilli()))
                while (start < end) {
                    val day = start.atZone(zone).toLocalDate()
                    val next = minOf(day.plusDays(1).atStartOfDay(zone).toInstant(), end)
                    val note = if (interval.entry.timestamp < day.atStartOfDay(zone).toInstant().toEpochMilli() || interval.exit.timestamp >= day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()) "Quota del turno notturno" else ""
                    daily += row(day.format(dateFormat), employee.fullName, start.atZone(zone).format(timeFormat), if (next.atZone(zone).toLocalDate() != day) "24:00:00" else next.atZone(zone).format(timeFormat)) + ReportCell(durationMillis = Duration.between(start, next).toMillis()) + row(employee.number, note)
                    start = next
                }
            }
            pairing.incomplete.filter { period.contains(it.timestamp) }.forEach { event ->
                val time = Instant.ofEpochMilli(event.timestamp).atZone(zone)
                daily += row(time.format(dateFormat), employee.fullName, if (event.type == ENTRY) time.format(timeFormat) else "", if (event.type == EXIT) time.format(timeFormat) else "", "", employee.number, "INCOMPLETA — ${event.note}")
            }
            events.filter { period.contains(it.timestamp) }.sortedBy { it.timestamp }.forEach { event ->
                val time = Instant.ofEpochMilli(event.timestamp).atZone(zone)
                detail += row(time.format(dateFormat), time.format(timeFormat), employee.fullName, event.type, event.source, if (event.cancelled) "ANNULLATA" else "VALIDA", event.note, event.id.toString())
            }
            var month = YearMonth.from(filter.first)
            while (month <= YearMonth.from(filter.last)) {
                val first = maxOf(month.atDay(1), filter.first)
                val last = minOf(month.atEndOfMonth(), filter.last)
                monthly += row(month.toString(), employee.fullName) + ReportCell(durationMillis = Attendance.total(events, Period.dates(first, last, zone))) + ReportCell(employee.number)
                month = month.plusMonths(1)
            }
        }
        return listOf(ReportSheet("Riepilogo giornaliero", daily), ReportSheet("Dettaglio timbrature", detail), ReportSheet("Riepilogo mensile", monthly))
    }
    fun employeesCsv(data: BackupData): ByteArray {
        val rows = listOf(row("ID", "Nome", "Cognome", "Matricola", "Attivo", "Eliminato", "UID NFC", "Registrazione UTC", "Minuti teorici lun-ven")) + data.employees.map { e ->
            row(e.id.toString(), e.firstName, e.lastName, e.number, e.active.toString(), e.deleted.toString(), data.tags.find { it.employeeId == e.id }?.uid.orEmpty(), Instant.ofEpochMilli(e.createdAt).toString(), e.dailyMinutes?.toString().orEmpty())
        }
        return csv(ReportSheet("Anagrafiche", rows))
    }
    fun csv(sheet: ReportSheet): ByteArray {
        val text = sheet.rows.joinToString("\r\n") { row -> row.joinToString(";") { cell ->
            var value = cell.durationMillis?.let { "%02d:%02d".format(java.util.Locale.ROOT, it / 3_600_000, (it / 60_000) % 60) } ?: cell.text
            if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) value = "'$value"
            "\"${value.replace("\"", "\"\"")}\""
        } }
        return ("\uFEFF" + text + "\r\n").toByteArray(Charsets.UTF_8)
    }

    fun xlsx(sheets: List<ReportSheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun file(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + xml).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            file("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""")
            file("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            file("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.mapIndexed { i, s -> "<sheet name=\"${escape(s.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("")}</sheets></workbook>""")
            file("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" }}<Relationship Id="styles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            file("xl/styles.xml", """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="[h]:mm"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            sheets.forEachIndexed { index, sheet ->
                val rows = sheet.rows.mapIndexed { rowIndex, cells ->
                    "<row r=\"${rowIndex + 1}\">" + cells.mapIndexed { col, c ->
                        val reference = column(col) + (rowIndex + 1)
                        if (c.durationMillis != null) "<c r=\"$reference\" s=\"2\"><v>${c.durationMillis / 86_400_000.0}</v></c>"
                        else "<c r=\"$reference\" t=\"inlineStr\" s=\"${if (rowIndex == 0) 1 else 0}\"><is><t xml:space=\"preserve\">${escape(c.text)}</t></is></c>"
                    }.joinToString("") + "</row>"
                }.joinToString("")
                file("xl/worksheets/sheet${index + 1}.xml", """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="12" width="24" customWidth="1"/></cols><sheetData>$rows</sheetData></worksheet>""")
            }
        }
        return out.toByteArray()
    }
    private fun escape(s: String) = s.filter { it == '\n' || it == '\t' || it == '\r' || it.code >= 32 }.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun column(index: Int): String = if (index < 26) ('A' + index).toString() else column(index / 26 - 1) + ('A' + index % 26)
}
