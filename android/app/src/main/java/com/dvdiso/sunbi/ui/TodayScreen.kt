package com.dvdiso.sunbi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.EVENTS
import com.dvdiso.sunbi.Insight
import com.dvdiso.sunbi.InsightKind
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.Task
import com.dvdiso.sunbi.insights
import com.dvdiso.sunbi.niceDay
import com.dvdiso.sunbi.today
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(
    todayStr: String,
    tick: Long,
    contentPadding: PaddingValues,
    onFocus: (Task?) -> Unit,
    onOpenPlan: () -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(todayStr) }
    LaunchedEffect(todayStr) { if (day > todayStr) day = todayStr }
    val isToday = day == todayStr
    val tasks = Repo.tasks()
    val ins = if (isToday) insights(day) else emptyList()

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DayHeader(day, isToday, onPrev = { day = LocalDate.parse(day).minusDays(1).toString() },
                onNext = { day = LocalDate.parse(day).plusDays(1).toString() }, onToday = { day = todayStr })
        }
        if (isToday) {
            item(key = "now") { NowCard(tasks, tick, onFocus) }
            items(ins.size) { i -> InsightCard(ins[i], onOpenPlan) }
        }
        item(key = "checkin-$day") { CheckinCard(day) }
        item(key = "routine-head") { RoutineHeader(day, tasks) }
        itemsIndexed(tasks, key = { _, t -> "task-" + t.id }) { i, t -> TaskItem(day, t, i, tasks.size, isToday, onFocus) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DayHeader(day: String, isToday: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SunBiMark(size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isToday) saudacao() else "Outro dia", style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(niceDay(day), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Dia anterior") }
        if (isToday) {
            IconButton(onClick = onNext, enabled = false) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo dia") }
        } else {
            TextButton(onClick = onToday) { Text("Hoje") }
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo dia") }
        }
    }
}

private fun saudacao(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "Boa noite"
        h < 12 -> "Bom dia"
        h < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
}

private fun mins(time: String): Int? = runCatching { LocalTime.parse(time).toSecondOfDay() / 60 }.getOrNull()

/** Cartão "Agora": torna o tempo visível (TDAH) e oferece começar com um bloco curto. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowCard(tasks: List<Task>, tick: Long, onFocus: (Task?) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") tick
    val d = today()
    val now = LocalTime.now().toSecondOfDay() / 60
    val pending = tasks.filter { !Repo.done(d, it.id) && mins(it.time) != null }
    // atrasada: passou da hora há até 3 h e ainda não foi feita
    val late = pending.lastOrNull { val m = mins(it.time)!!; m <= now && now - m <= 180 }
    val next = pending.firstOrNull { mins(it.time)!! > now }
    val cs = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val focus = late ?: next
            if (focus == null) {
                Text("Tudo certo por agora", style = MaterialTheme.typography.titleLargeEmphasized)
                Text("Nenhuma tarefa pendente. Descansar também faz parte da rotina.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val m = mins(focus.time)!!
            Text(if (focus == late) "AGORA" else "PRÓXIMA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(focus.label, style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                if (focus == late) "Era às ${focus.time} · ${now - m} min atrás. Tudo bem, dá para fazer agora."
                else "Às ${focus.time} · ${minutesLabel((m - now).toLong())}" + if (focus.min > 0) " · leva uns ${focus.min} min" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (focus != late) {
                // quanto do intervalo até a próxima tarefa já passou
                val prev = tasks.mapNotNull { mins(it.time) }.filter { it <= now }.maxOrNull() ?: (m - 120).coerceAtLeast(0)
                val span = (m - prev).coerceAtLeast(1)
                val frac = ((now - prev).toFloat() / span).coerceIn(0f, 1f)
                LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = cs.primary, trackColor = cs.onPrimaryContainer.copy(alpha = 0.15f))
            }
            if (focus.step.isNotBlank()) {
                Text("Primeiro passo: ${focus.step}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onFocus(focus) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Começar")
                }
                FilledTonalButton(onClick = { Repo.setDone(d, focus.id, true) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Feito")
                }
            }
        }
    }
}

@Composable
private fun InsightCard(i: Insight, onOpenPlan: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (i.kind) {
        // cores calmas de propósito: o aviso convida a olhar o plano, não assusta
        InsightKind.UP, InsightKind.DOWN -> cs.secondaryContainer to cs.onSecondaryContainer
        InsightKind.KUDOS -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.surfaceContainerHigh to cs.onSurface
    }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(i.title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(i.body, style = MaterialTheme.typography.bodyMedium)
            if (i.kind == InsightKind.UP || i.kind == InsightKind.DOWN) {
                TextButton(onClick = onOpenPlan, contentPadding = PaddingValues(0.dp)) { Text("Abrir meu plano", color = fg, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ── check-in ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckinCard(day: String) {
    val humor = Repo.humor(day)
    val hasDetails = listOf("energy", "irrit", "anx", "focus").any { Repo.level(day, it) != null } ||
        Repo.signsOn(day).isNotEmpty() || Repo.eventsOn(day).isNotEmpty() || Repo.note(day).isNotBlank()
    var more by rememberSaveable(day) { mutableStateOf(hasDetails) }

    SectionCard(
        title = "Como você está?",
        subtitle = "Pense no dia como um todo. Leva menos de um minuto.",
    ) {
        Label("Humor")
        HumorScale(humor) { Repo.set("$day/humor", it) }

        Label("Sono da última noite")
        SleepRow(day)

        TextButton(onClick = { more = !more }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (more) "Menos detalhes" else "Energia, foco, sinais e anotação")
            Spacer(Modifier.width(4.dp))
            Icon(if (more) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
        }
        AnimatedVisibility(more) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LevelRow(day, "energy", "Energia", 1..5, listOf("muito baixa", "baixa", "normal", "alta", "muito alta"))
                LevelRow(day, "focus", "Foco e atenção", 1..5, listOf("disperso", "difícil", "ok", "bom", "ótimo"))
                LevelRow(day, "irrit", "Irritação", 0..3, listOf("nenhuma", "leve", "moderada", "forte"))
                LevelRow(day, "anx", "Ansiedade", 0..3, listOf("nenhuma", "leve", "moderada", "forte"))

                val signs = Repo.signs()
                if (signs.isNotEmpty()) {
                    Label("Sinais do seu plano que apareceram")
                    val on = Repo.signsOn(day)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (s in signs) {
                            val sel = s.id in on
                            FilterChip(
                                selected = sel,
                                onClick = { Repo.setStrSet("$day/signs", if (sel) on - s.id else on + s.id) },
                                label = { Text(s.label) },
                                leadingIcon = { Text(if (s.pole == "up") "↑" else "↓", fontWeight = FontWeight.Bold) },
                                shape = MaterialTheme.shapes.large,
                            )
                        }
                    }
                }

                Label("Acontecimentos")
                val ev = Repo.eventsOn(day)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((id, label) in EVENTS) {
                        val sel = id in ev
                        FilterChip(
                            selected = sel,
                            onClick = { Repo.setStrSet("$day/events", if (sel) ev - id else ev + id) },
                            label = { Text(label) },
                            shape = MaterialTheme.shapes.large,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        )
                    }
                }

                var note by remember(day) { mutableStateOf(Repo.note(day)) }
                LaunchedEffect(note) {
                    delay(1000)
                    if (note != Repo.note(day)) Repo.set("$day/note", note)
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Anotação: como foi o dia, apetite, conversas...") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                )
            }
        }
    }
}

@Composable
private fun LevelRow(day: String, field: String, title: String, range: IntRange, names: List<String>) {
    val v = Repo.level(day, field)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Label(title, Modifier.weight(1f))
            Text(v?.let { names[it - range.first] } ?: "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        ChoiceGroup(
            options = range.map { it to it.toString() },
            selected = v,
            onSelect = { Repo.setLevel(day, field, it) },
            description = { "$title: ${names[it - range.first]}" },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepRow(day: String) {
    var editing by remember { mutableStateOf<String?>(null) } // "bed" | "wake"
    val bed = Repo.bed(day)
    val wake = Repo.wake(day)
    val hours = Repo.sleep(day)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { editing = "bed" }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
            Text(if (bed != null) "Deitei $bed" else "Deitei às...", maxLines = 1)
        }
        OutlinedButton(onClick = { editing = "wake" }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
            Text(if (wake != null) "Acordei $wake" else "Acordei às...", maxLines = 1)
        }
    }
    if (hours != null) {
        val cs = MaterialTheme.colorScheme
        val short = hours < 6.0
        Text(
            "%.1f h de sono".format(hours).replace('.', ',') + if (short) " · noite curta" else "",
            style = MaterialTheme.typography.titleMedium,
            color = if (short) cs.error else cs.primary,
        )
    }
    editing?.let { which ->
        val cur = (if (which == "bed") bed else wake) ?: (if (which == "bed") "23:00" else "07:00")
        val t = LocalTime.parse(cur)
        val state = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (which == "bed") "Fui deitar às" else "Acordei às") },
            confirmButton = {
                TextButton(onClick = {
                    val hm = "%02d:%02d".format(state.hour, state.minute)
                    if (which == "bed") Repo.setSleepTimes(day, hm, wake) else Repo.setSleepTimes(day, bed, hm)
                    editing = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        ) { TimePicker(state) }
    }
}

// ── rotina ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutineHeader(day: String, tasks: List<Task>) {
    val done = tasks.count { Repo.done(day, it.id) }
    val frac = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(progress = { frac }, modifier = Modifier.size(56.dp))
            Text("$done", style = MaterialTheme.typography.titleMediumEmphasized)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Rotina", style = MaterialTheme.typography.titleLargeEmphasized)
            Text("$done de ${tasks.size} · toque para marcar, segure para focar", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaskItem(day: String, t: Task, index: Int, count: Int, isToday: Boolean, onFocus: (Task?) -> Unit) {
    val done = Repo.done(day, t.id)
    val cs = MaterialTheme.colorScheme
    val at = Repo.doneAt(day, t.id)
    SegmentedListItem(
        checked = done,
        onCheckedChange = { Repo.setDone(day, t.id, it) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        onLongClick = if (isToday) ({ onFocus(t) }) else null,
        onLongClickLabel = "Começar foco",
        leadingContent = {
            if (done) {
                Box(Modifier.size(36.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp))
                }
            } else {
                Box(Modifier.size(36.dp).padding(3.dp).border(BorderStroke(2.dp, cs.outline), CircleShape))
            }
        },
        supportingContent = {
            val bits = mutableListOf<String>()
            if (t.step.isNotBlank() && !done) bits += "1º passo: ${t.step}"
            if (t.min > 0 && !done) bits += "~${t.min} min"
            if (done && at != null) bits += "feito às $at"
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "))
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.time, style = MaterialTheme.typography.titleMedium, color = if (done) cs.onSurfaceVariant else cs.primary)
                if (t.med) Tag("remédio", cs.tertiaryContainer, cs.onTertiaryContainer)
                else if (t.anchor) Tag("âncora", cs.secondaryContainer, cs.onSecondaryContainer)
            }
        },
    ) {
        Text(
            t.label,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) cs.onSurfaceVariant else Color.Unspecified,
        )
    }
}
