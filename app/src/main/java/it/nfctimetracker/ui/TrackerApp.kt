@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package it.nfctimetracker.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.nfctimetracker.R
import it.nfctimetracker.data.*
import it.nfctimetracker.domain.*
import it.nfctimetracker.domain.Period
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val italian = Locale.ITALIAN
private val dateLabel = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", italian)
private val eventLabel = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm:ss", italian)

@Composable
fun TrackerApp(vm: TrackerViewModel, nfc: String, openNfc: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val admin by vm.admin.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val pairing by vm.pairingEmployee.collectAsStateWithLifecycle()
    val restore by vm.pendingRestore.collectAsStateWithLifecycle()
    var pinDialog by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("Dipendenti") }
    val snackbar = remember { SnackbarHostState() }
    val data = state
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); if (vm.notice.value == it) vm.notice.value = null } }
    BackHandler(admin) { vm.lock() }
    Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (data == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.fillMaxSize().padding(padding).widthIn(max = 1100.dp).padding(horizontal = 20.dp).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(Teal), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Nfc, null, tint = MaterialTheme.colorScheme.onPrimary) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (admin) "AREA AMMINISTRATIVA" else "TERMINALE PRESENZE", fontSize = 10.sp, letterSpacing = 1.5.sp, color = Muted)
                }
                if (admin) IconButton(onClick = { vm.lock() }) { Icon(Icons.AutoMirrored.Outlined.Logout, "Torna al terminale") }
                else IconButton(onClick = {
                    if (data.pinHash.isEmpty()) vm.unlock("") { } else pinDialog = true
                }) { Icon(Icons.Outlined.AdminPanelSettings, "Amministrazione") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!admin) Terminal(data, feedback, nfc, openNfc, Modifier.weight(1f))
            else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Dipendenti", "Storico", "Statistiche", "Esporta e backup", "Impostazioni", "Registro modifiche").forEach { label ->
                        FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (data.pinHash.isEmpty()) Text("Accesso senza PIN. Puoi proteggerlo nelle Impostazioni.", color = Amber, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                when (tab) {
                    "Dipendenti" -> EmployeesScreen(data, vm)
                    "Storico" -> HistoryScreen(data, vm)
                    "Statistiche" -> StatisticsScreen(data)
                    "Esporta e backup" -> ExportScreen(data, vm)
                    "Impostazioni" -> SettingsScreen(data, vm)
                    else -> AuditScreen(data)
                }
            }
        }
    }
    if (pinDialog) PinDialog(onDismiss = { pinDialog = false }, submit = { pin -> vm.unlock(pin) { if (it) pinDialog = false } })
    if (pairing != null && admin) AlertDialog(onDismissRequest = { vm.cancelAssociation() },
        icon = { Icon(Icons.Outlined.Nfc, null) }, title = { Text("Avvicina il nuovo tag") },
        text = { Text("Dipendente: ${data?.employees?.find { it.id == pairing }?.fullName}\n\nIl tag precedente verrà sostituito. Il tag non sarà modificato e non verrà registrata una timbratura.\n\n$nfc") },
        confirmButton = { TextButton(onClick = { vm.cancelAssociation() }) { Text("Annulla") } })
    if (restore != null && admin) AlertDialog(onDismissRequest = { vm.pendingRestore.value = null }, title = { Text("Sovrascrivere tutti i dati?") },
        text = { Text("Il backup contiene ${restore!!.employees.size} dipendenti e ${restore!!.events.size} timbrature. Tutti i dati, le associazioni e le impostazioni attuali verranno sostituiti. Verrà ripristinato anche il PIN del backup. Salva prima un backup dei dati attuali.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { vm.confirmRestore() }) { Text("Sovrascrivi e ripristina") } },
        dismissButton = { TextButton(onClick = { vm.pendingRestore.value = null }) { Text("Annulla") } })
}

@Composable private fun Terminal(data: TrackerState, feedback: ScanFeedback?, nfc: String, openNfc: () -> Unit, modifier: Modifier) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(1000) } }
    val time = now.atZone(data.zone)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(22.dp))
        Text(time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), fontSize = 56.sp, fontWeight = FontWeight.Light, color = Foreground)
        Text(time.format(dateLabel).replaceFirstChar { it.titlecase(italian) }, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth().widthIn(max = 680.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val punch = feedback?.punch
                val accent = if (feedback != null && !feedback.success) MaterialTheme.colorScheme.error else if (punch?.event?.type == EXIT) ExitBlue else Teal
                Box(Modifier.size(108.dp).clip(CircleShape).background(accent.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
                    Icon(if (feedback?.success == true) Icons.Outlined.CheckCircle else if (feedback != null) Icons.Outlined.Info else Icons.Outlined.Nfc, null, Modifier.size(52.dp), tint = accent)
                }
                Spacer(Modifier.height(24.dp))
                if (punch != null) {
                    Text(punch.event.type, color = accent, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 3.sp)
                    Text(punch.employee.fullName.uppercase(italian), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                    Text(Instant.ofEpochMilli(punch.event.timestamp).atZone(data.zone).format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text(if (punch.event.type == EXIT) "Ore lavorate oggi: ${durationLabel(punch.workedToday)}" else "Timbratura registrata", color = Muted, modifier = Modifier.padding(top = 12.dp))
                } else if (feedback != null) {
                    Text(feedback.text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = accent)
                } else {
                    Text("AVVICINA IL TAG NFC", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Entrata e uscita si registrano\nautomaticamente con il tuo tag.", textAlign = TextAlign.Center, color = Muted, modifier = Modifier.padding(top = 14.dp))
                }
                feedback?.uid?.let { uid ->
                    Text("UID $uid", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(26.dp))
                AssistChip(onClick = { if (nfc != "NFC pronto") openNfc() }, label = { Text(nfc) }, leadingIcon = {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (nfc == "NFC pronto") Teal else Amber))
                })
                if (data.employees.none { !it.deleted }) Text("Per iniziare, aggiungi i dipendenti e associa i tag dall’area amministrativa.", textAlign = TextAlign.Center, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.OfflinePin, null, Modifier.size(16.dp), tint = Muted); Text("  Offline · Dati salvati su questo dispositivo", color = Muted, fontSize = 12.sp) }
        Text("Fuso orario: ${data.zone.id}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
    }
}

@Composable private fun PinDialog(onDismiss: () -> Unit, submit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Accesso amministratore") }, text = {
        OutlinedTextField(pin, { if (it.length <= 12 && it.all(Char::isDigit)) pin = it }, label = { Text("PIN") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
    }, confirmButton = { TextButton(onClick = { submit(pin) }) { Text("Accedi") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

@Composable private fun PageHeading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
}
@Composable private fun EmptyState(text: String) { Text(text, color = Muted, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center) }

@Composable private fun EmployeesScreen(data: TrackerState, vm: TrackerViewModel) {
    var editing by remember { mutableStateOf<Employee?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Employee?>(null) }
    val people = data.employees.filterNot { it.deleted }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeading("Le persone, i loro orari", "${people.count { it.active }} attivi · ${people.size} dipendenti · tag personali")
            Button(onClick = { adding = true }) { Icon(Icons.Outlined.Add, null); Text("Aggiungi dipendente", Modifier.padding(start = 8.dp)) }
        }
        if (people.isEmpty()) item { EmptyState("Nessun dipendente. Aggiungi la prima persona, poi associa il suo tag NFC.") }
        items(people, key = { it.id }) { employee ->
            val tag = data.tags.find { it.employeeId == employee.id }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Teal.copy(alpha = .08f)), contentAlignment = Alignment.Center) { Text("${employee.firstName.take(1)}${employee.lastName.take(1)}", color = Teal, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(employee.fullName, fontWeight = FontWeight.Bold)
                            Text(if (employee.active) "Attivo${if (employee.number.isNotEmpty()) " · ${employee.number}" else ""}" else "Disattivato", color = if (employee.active) Muted else Amber, fontSize = 12.sp)
                        }
                        IconButton(onClick = { editing = employee }) { Icon(Icons.Outlined.Edit, "Modifica ${employee.fullName}") }
                        IconButton(onClick = { deleting = employee }) { Icon(Icons.Outlined.DeleteOutline, "Elimina ${employee.fullName}") }
                    }
                    Text(tag?.let { "UID ${it.uid}" } ?: "Nessun tag associato", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    Row {
                        TextButton(onClick = { vm.associate(employee.id) }) { Icon(Icons.Outlined.Nfc, null, Modifier.size(18.dp)); Text(if (tag == null) "Associa tag" else "Sostituisci tag", Modifier.padding(start = 6.dp)) }
                        if (tag != null) TextButton(onClick = { vm.adminAction("Associazione rimossa") { unbind(employee.id) } }) { Text("Rimuovi") }
                    }
                    Text("Registrato il ${Instant.ofEpochMilli(employee.createdAt).atZone(data.zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
    if (adding || editing != null) EmployeeDialog(editing, vm) { adding = false; editing = null }
    deleting?.let { e -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminare ${e.fullName}?") },
        text = { Text("L’eliminazione è logica: storico e conteggi saranno conservati. Il tag non potrà più timbrare per questo dipendente.") },
        confirmButton = { TextButton(onClick = { vm.adminAction("Dipendente eliminato", { deleting = null }) { deleteEmployee(e.id) } }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } }) }
}

@Composable private fun EmployeeDialog(employee: Employee?, vm: TrackerViewModel, close: () -> Unit) {
    var first by remember { mutableStateOf(employee?.firstName.orEmpty()) }
    var last by remember { mutableStateOf(employee?.lastName.orEmpty()) }
    var number by remember { mutableStateOf(employee?.number.orEmpty()) }
    var active by remember { mutableStateOf(employee?.active ?: true) }
    var minutes by remember { mutableStateOf(employee?.dailyMinutes?.toString().orEmpty()) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val valid = first.isNotBlank() && last.isNotBlank() && (minutes.isBlank() || minutes.toIntOrNull() in 1..1440)
    AlertDialog(onDismissRequest = close, title = { Text(if (employee == null) "Nuovo dipendente" else "Modifica dipendente") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(first, { first = it.take(100) }, label = { Text("Nome") }, singleLine = true)
            OutlinedTextField(last, { last = it.take(100) }, label = { Text("Cognome") }, singleLine = true)
            OutlinedTextField(number, { number = it.take(100) }, label = { Text("Matricola (facoltativa)") }, singleLine = true)
            OutlinedTextField(minutes, { minutes = it }, label = { Text("Minuti teorici al giorno (facoltativi)") }, supportingText = { Text("Esempio: 480 = 8 ore. Da lunedì a venerdì, dalla registrazione.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Dipendente attivo", Modifier.weight(1f)); Switch(active, { active = it }) }
        }
    }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { vm.adminAction("Dipendente salvato", close) { saveEmployee(employee?.id, first, last, number, active, minutes.toIntOrNull()) } }) { Text("Salva") } }, dismissButton = { TextButton(onClick = close) { Text("Annulla") } })
}

@Composable private fun EmployeePicker(employees: List<Employee>, selected: Long?, all: Boolean = true, choose: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Icon(Icons.Outlined.People, null, Modifier.size(18.dp)); Text(employees.find { it.id == selected }?.fullName ?: if (all) "Tutti i dipendenti" else "Scegli dipendente", Modifier.padding(horizontal = 8.dp)); Icon(Icons.Outlined.ExpandMore, null) }
        DropdownMenu(expanded, { expanded = false }) {
            if (all) DropdownMenuItem(text = { Text("Tutti i dipendenti") }, onClick = { choose(null); expanded = false })
            employees.forEach { e -> DropdownMenuItem(text = { Text(e.fullName + if (e.deleted) " (eliminato)" else "") }, onClick = { choose(e.id); expanded = false }) }
        }
    }
}

@Composable private fun ReportFilters(data: TrackerState, selected: Long?, first: String, last: String, choose: (Long?) -> Unit, dates: (String, String) -> Unit) {
    val today = LocalDate.now(data.zone)
    EmployeePicker(data.employees, selected, choose = choose)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Oggi", "Settimana", "Mese").forEach { label ->
            AssistChip(onClick = {
                val start = when (label) { "Settimana" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); "Mese" -> today.withDayOfMonth(1); else -> today }
                val end = when (label) { "Settimana" -> start.plusDays(6); "Mese" -> today.with(TemporalAdjusters.lastDayOfMonth()); else -> today }
                dates(start.toString(), end.toString())
            }, label = { Text(label) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(first, { dates(it, last) }, Modifier.weight(1f), label = { Text("Dal (AAAA-MM-GG)") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
        OutlinedTextField(last, { dates(first, it) }, Modifier.weight(1f), label = { Text("Al (AAAA-MM-GG)") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
    }
    Text("Estremi inclusi · Fuso ${data.zone.id}", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 8.dp))
}

private fun filterOrNull(id: Long?, first: String, last: String): ReportFilter? = runCatching { ReportFilter(id, LocalDate.parse(first), LocalDate.parse(last)) }.getOrNull()

@Composable private fun HistoryScreen(data: TrackerState, vm: TrackerViewModel) {
    val today = LocalDate.now(data.zone)
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var first by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var last by rememberSaveable { mutableStateOf(today.with(TemporalAdjusters.lastDayOfMonth()).toString()) }
    var editing by remember { mutableStateOf<AttendanceEvent?>(null) }
    var adding by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf<AttendanceEvent?>(null) }
    val filter = filterOrNull(selected, first, last)
    val period = filter?.period(data.zone)
    val all = data.events.filter { selected == null || it.employeeId == selected }
    val events = if (period == null) emptyList() else all.filter { period.contains(it.timestamp) }.sortedWith(compareByDescending<AttendanceEvent> { it.timestamp }.thenByDescending { it.id })
    val incomplete = remember(all) { Attendance.pair(all).incomplete.map { it.id }.toSet() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeading("Storico timbrature", "Correzioni tracciate, senza cancellare lo storico.")
            ReportFilters(data, selected, first, last, { selected = it }, { a, b -> first = a; last = b })
            if (period == null) Text("Date non valide o intervallo superiore a 10 anni", color = MaterialTheme.colorScheme.error)
            else Text("Totale: ${durationLabel(Attendance.total(all, period))}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = { adding = true }, enabled = data.employees.isNotEmpty()) { Icon(Icons.Outlined.Add, null); Text("Aggiungi timbratura", Modifier.padding(start = 8.dp)) }
        }
        if (events.isEmpty()) item { EmptyState("Nessuna timbratura nel periodo selezionato.") }
        items(events, key = { it.id }) { event ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(event.type + if (event.cancelled) " · ANNULLATA" else if (event.id in incomplete) " · INCOMPLETA" else "", color = if (event.cancelled || event.id in incomplete) Amber else if (event.type == ENTRY) Teal else ExitBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(data.employees.find { it.id == event.employeeId }?.fullName ?: "Dipendente ${event.employeeId}", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                            Text(Instant.ofEpochMilli(event.timestamp).atZone(data.zone).format(eventLabel), color = Muted, fontSize = 13.sp)
                            Text("${event.source} · #${event.id}", color = Muted, fontSize = 11.sp)
                        }
                        if (!event.cancelled) {
                            IconButton(onClick = { editing = event }) { Icon(Icons.Outlined.Edit, "Correggi timbratura") }
                            IconButton(onClick = { cancelling = event }) { Icon(Icons.Outlined.DeleteOutline, "Annulla timbratura") }
                        }
                    }
                    if (event.note.isNotBlank()) Text(event.note, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    if (adding || editing != null) EventDialog(data, editing, selected, vm) { adding = false; editing = null }
    cancelling?.let { e -> AlertDialog(onDismissRequest = { cancelling = null }, title = { Text("Annullare la timbratura?") }, text = { Text("La registrazione rimarrà visibile come annullata e non contribuirà ai conteggi. La modifica verrà registrata nel registro.") },
        confirmButton = { TextButton(onClick = { vm.adminAction("Timbratura annullata", { cancelling = null }) { cancelEvent(e.id) } }) { Text("Annulla timbratura") } }, dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Indietro") } }) }
}

@Composable private fun EventDialog(data: TrackerState, event: AttendanceEvent?, initialEmployee: Long?, vm: TrackerViewModel, close: () -> Unit) {
    val initial = Instant.ofEpochMilli(event?.timestamp ?: System.currentTimeMillis()).atZone(data.zone)
    var employee by remember { mutableStateOf(event?.employeeId ?: initialEmployee ?: data.employees.firstOrNull()?.id) }
    var date by remember { mutableStateOf(initial.toLocalDate().toString()) }
    var time by remember { mutableStateOf(initial.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))) }
    var offset by remember { mutableStateOf(initial.offset.toString()) }
    var type by remember { mutableStateOf(event?.type ?: ENTRY) }
    var note by remember { mutableStateOf(event?.note.orEmpty()) }
    val parsed = runCatching {
        val local = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
        val offsets = data.zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "Ora inesistente per il cambio dell’ora legale" }
        val chosen = if (offsets.size == 1) offsets.first() else ZoneOffset.of(offset).also { require(it in offsets) { "Scegli un offset valido: $offsets" } }
        ZonedDateTime.ofStrict(local, chosen, data.zone).toInstant().toEpochMilli()
    }
    val busy by vm.busy.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = close, title = { Text(if (event == null) "Aggiungi timbratura" else "Correggi timbratura") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (event == null) EmployeePicker(data.employees, employee, all = false) { employee = it }
            else Text(data.employees.find { it.id == employee }?.fullName.orEmpty())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(ENTRY, EXIT).forEach { value -> FilterChip(type == value, { type = value }, label = { Text(value) }) } }
            OutlinedTextField(date, { date = it }, label = { Text("Data (AAAA-MM-GG)") }, singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("Ora (HH:mm:ss)") }, singleLine = true)
            OutlinedTextField(offset, { offset = it }, label = { Text("Offset nel cambio ora (es. +02:00)") }, singleLine = true)
            Text("Fuso: ${data.zone.id}. Per ore ripetute al cambio dell’ora, scegli l’offset desiderato.", color = Muted, fontSize = 11.sp)
            OutlinedTextField(note, { note = it.take(2000) }, label = { Text("Note / motivo della correzione") }, minLines = 2)
            if (parsed.isFailure) Text(parsed.exceptionOrNull()?.message ?: "Data o ora non valida", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(enabled = employee != null && parsed.isSuccess && !busy, onClick = { vm.adminAction("Timbratura salvata", close) { saveEvent(event?.id, employee!!, parsed.getOrThrow(), type, note) } }) { Text("Salva") } }, dismissButton = { TextButton(onClick = close) { Text("Annulla") } })
}

@Composable private fun StatisticsScreen(data: TrackerState) {
    val today = LocalDate.now(data.zone)
    val week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val month = today.withDayOfMonth(1)
    val monthPeriod = Period.dates(month, today, data.zone)
    val pairing = remember(data.events) { Attendance.pair(data.events) }
    val present = data.employees.count { e -> !e.deleted && data.events.lastOrNull { it.employeeId == e.id && !it.cancelled }?.type == ENTRY }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading("Il tempo, in chiaro", "Conteggi degli intervalli chiusi · nessuna pausa sottratta") }
        item { StatCard("${data.employees.count { !it.deleted }} dipendenti", "$present attualmente presenti", Icons.Outlined.People) }
        item { StatCard(durationLabel(Attendance.total(data.events)), "Ore complessive · ${pairing.incomplete.size} timbrature incomplete", Icons.Outlined.AccessTime) }
        if (pairing.incomplete.isNotEmpty()) item { Text("Le timbrature incomplete includono entrate ancora aperte, entrate consecutive e uscite senza entrata. Verificale nello Storico. I turni aperti non aggiungono ore ai totali.", color = Amber, fontSize = 12.sp) }
        items(data.employees, key = { it.id }) { e ->
            val events = data.events.filter { it.employeeId == e.id }
            val daily = Attendance.daily(events, data.zone, monthPeriod)
            val monthTotal = Attendance.total(events, monthPeriod)
            val expected = e.dailyMinutes?.let { Attendance.theoreticalMillis(month, today, Instant.ofEpochMilli(e.createdAt).atZone(data.zone).toLocalDate(), it) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(e.fullName + if (e.deleted) " (eliminato)" else "", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    StatLine("Oggi", durationLabel(Attendance.total(events, Period.dates(today, today, data.zone))))
                    StatLine("Settimana fino a oggi", durationLabel(Attendance.total(events, Period.dates(week, today, data.zone))))
                    StatLine("Mese fino a oggi", durationLabel(monthTotal))
                    StatLine("Totale storico", durationLabel(Attendance.total(events)))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    StatLine("Giorni lavorati nel mese", daily.size.toString())
                    StatLine("Media giornaliera del mese", if (daily.isEmpty()) "—" else durationLabel(monthTotal / daily.size))
                    StatLine("Saldo teorico del mese", expected?.let { durationLabel(monthTotal - it) } ?: "Non configurato")
                    if (expected != null) Text("Teorico lun–ven, dalla registrazione a oggi; nessun calendario festivi.", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}
@Composable private fun StatCard(value: String, label: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 18.dp)) { Text(value, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp) }
        }
    }
}
@Composable private fun StatLine(label: String, value: String) { Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), color = Muted, fontSize = 13.sp); Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp) } }

@Composable private fun ExportScreen(data: TrackerState, vm: TrackerViewModel) {
    val today = LocalDate.now(data.zone)
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var first by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var last by rememberSaveable { mutableStateOf(today.toString()) }
    val filter = filterOrNull(selected, first, last)
    val busy by vm.busy.collectAsStateWithLifecycle()
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { vm.filePickerOpen = false; vm.writeExport(it) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { vm.filePickerOpen = false; vm.writeExport(it) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.filePickerOpen = false; vm.writeExport(it) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.filePickerOpen = false; vm.readRestore(it) }
    fun export(kind: String) {
        val effective = filter ?: ReportFilter(null, today, today)
        vm.prepareExport(kind, effective) {
            vm.filePickerOpen = true
            when (kind) { "xlsx" -> xlsx.launch("presenze-$first-$last.xlsx"); "backup" -> backup.launch("nfc-backup-$today.json"); "employees" -> csv.launch("anagrafiche-$today.csv"); else -> csv.launch("timbrature-$first-$last.csv") }
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("Esporta e conserva", "File locali, senza server o account obbligatori."); ReportFilters(data, selected, first, last, { selected = it }, { a, b -> first = a; last = b }) }
        item { ActionCard("Excel · XLSX", "Tre fogli: riepilogo giornaliero, dettaglio timbrature e riepilogo mensile. Turni notturni ripartiti per giorno.", "Esporta Excel", Icons.Outlined.TableChart, filter != null && !busy) { export("xlsx") } }
        item { ActionCard("Dettaglio · CSV", "Una riga per evento nel periodo scelto, incluse le timbrature annullate. Separatore punto e virgola.", "Esporta CSV", Icons.Outlined.Description, filter != null && !busy) { export("csv") } }
        item { ActionCard("Anagrafiche", "Tutti i dipendenti e le associazioni NFC, comprese le anagrafiche eliminate logicamente. Nessun PIN incluso.", "Esporta anagrafiche", Icons.Outlined.People, !busy) { export("employees") } }
        item { ActionCard("Backup completo", "Include tutti i dati, i tag, il registro modifiche e le impostazioni, incluso l’hash del PIN. File non cifrato: conservalo in un luogo protetto. Il filtro non si applica.", "Salva backup", Icons.Outlined.SaveAlt, !busy) { export("backup") } }
        item { ActionCard("Ripristina da file", "Il file verrà verificato prima di chiedere conferma. Il ripristino sostituisce tutti i dati attuali in una sola transazione.", "Scegli backup", Icons.Outlined.Restore, !busy) { vm.filePickerOpen = true; restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) } }
    }
}
@Composable private fun ActionCard(title: String, description: String, action: String, icon: ImageVector, enabled: Boolean, click: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Teal); Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp)) }
            Text(description, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            OutlinedButton(onClick = click, enabled = enabled) { Text(action) }
        }
    }
}

@Composable private fun SettingsScreen(data: TrackerState, vm: TrackerViewModel) {
    var debounce by remember { mutableStateOf(data.debounceSeconds.toString()) }
    var screenOn by remember { mutableStateOf(data.keepScreenOn) }
    var zone by remember { mutableStateOf(data.zone.id) }
    var changePin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val valid = debounce.toIntOrNull() in 1..3600 && runCatching { ZoneId.of(zone) }.isSuccess && (!changePin || (pin == confirm && (pin.isEmpty() || pin.matches(Regex("[0-9]{4,12}")))))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading("Impostazioni", "Il terminale rimane utilizzabile senza accesso amministrativo.") }
        item { OutlinedTextField(debounce, { debounce = it }, label = { Text("Intervallo antiduplicazione (secondi)") }, supportingText = { Text("Da 1 a 3600. Si applica al singolo dipendente.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Mantieni acceso il display", Modifier.weight(1f)); Switch(screenOn, { screenOn = it }) } }
        item { OutlinedTextField(zone, { zone = it }, label = { Text("Fuso orario dei conteggi") }, supportingText = { Text("Esempio: Europe/Rome. Le timbrature sono sempre memorizzate in UTC.") }, modifier = Modifier.fillMaxWidth()) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(changePin, { changePin = it }); Text("Modifica protezione PIN") } }
        if (changePin) {
            item { OutlinedTextField(pin, { pin = it }, label = { Text("Nuovo PIN (4–12 cifre)") }, supportingText = { Text("Lascia vuoto per rimuovere il PIN. Non esiste un PIN di fabbrica.") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(confirm, { confirm = it }, label = { Text("Ripeti PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()) }
        }
        item { Button(enabled = valid && !busy, onClick = { vm.saveSettings(debounce.toInt(), screenOn, zone, if (changePin) pin else null) { changePin = false; pin = ""; confirm = "" } }) { Text("Salva impostazioni") } }
        item { Text("NFC usa soltanto l’UID: i tag possono essere clonati. Il PIN protegge l’area amministrativa, non cifra il database. L’app non richiede accesso a Internet. Esegui backup periodici: disinstallare l’app elimina i dati locali.", color = Muted, fontSize = 12.sp) }
    }
}

@Composable private fun AuditScreen(data: TrackerState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading("Registro modifiche", "Aggiunte, correzioni e annullamenti conservano i valori precedenti.") }
        if (data.audit.isEmpty()) item { EmptyState("Nessuna modifica amministrativa registrata.") }
        items(data.audit, key = { it.id }) { entry ->
            var expanded by remember { mutableStateOf(false) }
            Card(onClick = { expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(entry.action.replace('_', ' '), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(Instant.ofEpochMilli(entry.changedAt).atZone(data.zone).format(eventLabel) + (entry.eventId?.let { " · Evento #$it" } ?: ""), fontSize = 12.sp, color = Muted)
                    if (expanded) { Text("Prima: ${entry.oldValue}", fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)); Text("Dopo: ${entry.newValue}", fontSize = 12.sp) }
                }
            }
        }
    }
}
