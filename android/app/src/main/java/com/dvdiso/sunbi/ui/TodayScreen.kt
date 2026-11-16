package com.dvdiso.sunbi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.EVENTS
import com.dvdiso.sunbi.Insight
import com.dvdiso.sunbi.InsightKind
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.Task
import com.dvdiso.sunbi.insights
import com.dvdiso.sunbi.niceDay
import com.dvdiso.sunbi.shortDay
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
    val allDone = tasks.isNotEmpty() && tasks.all { Repo.done(day, it.id) }
    // gira a flor uma vez quando a rotina do dia fecha
    var turns by remember { mutableFloatStateOf(0f) }
    var wasDone by remember(day) { mutableStateOf(allDone) }
    LaunchedEffect(allDone) {
        if (allDone && !wasDone) turns += 1f
        wasDone = allDone
    }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            DayHeader(day, isToday, turns, onPrev = { day = LocalDate.parse(day).minusDays(1).toString() },
                onNext = { day = LocalDate.parse(day).plusDays(1).toString() }, onToday = { day = todayStr })
        }
        if (isToday) {
            item(key = "now") { NowCard(tasks, tick, onFocus, Modifier.animateItem()) }
            for (i in ins) {
                item(key = "ins-" + i.kind) { InsightCard(i, onOpenPlan, Modifier.animateItem()) }
            }
        }
        item(key = "checkin-$day") { CheckinCard(day) }
        item(key = "routine-head") { RoutineHeader(day, tasks, allDone) }
        itemsIndexed(tasks, key = { _, t -> "task-" + t.id }) { i, t ->
            TaskItem(day, t, i, tasks.size, isToday, onFocus, Modifier.animateItem())
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DayHeader(day: String, isToday: Boolean, turns: Float, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SunBiMark(size = 48.dp, turns = turns)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isToday) saudacao() else "Outro dia", style = MaterialTheme.typography.headlineMediumEmphasized,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val narrow = isNarrow()
            AnimatedContent(day, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "day") { d ->
                Text(
                    (if (narrow) shortDay(d) else niceDay(d)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Dia anterior") }
        if (!isToday) {
            TextButton(onClick = onToday, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Hoje", maxLines = 1) }
        }
        IconButton(onClick = onNext, enabled = !isToday) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo dia") }
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun NowCard(tasks: List<Task>, tick: Long, onFocus: (Task?) -> Unit, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") tick
    val d = today()
    val fb = rememberFeedback()
    val now = LocalTime.now().toSecondOfDay() / 60
    val pending = tasks.filter { !Repo.done(d, it.id) && mins(it.time) != null }
    // atrasada: passou da hora há até 3 h e ainda não foi feita
    val late = pending.lastOrNull { val m = mins(it.time)!!; m <= now && now - m <= 180 }
    val next = pending.firstOrNull { mins(it.time)!! > now }
    val focus = late ?: next
    val cs = MaterialTheme.colorScheme
    val pad = if (isNarrow()) 16.dp else 20.dp

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
    ) {
        // a troca de tarefa desliza para cima, como uma fila andando
        AnimatedContent(
            targetState = focus?.id,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
            },
            label = "now",
        ) { id ->
            val t = tasks.firstOrNull { it.id == id }
            Column(Modifier.fillMaxWidth().padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (t == null) {
                    Text("Tudo certo por agora", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text("Nenhuma tarefa pendente. Descansar também faz parte da rotina.", style = MaterialTheme.typography.bodyLarge)
                    return@Column
                }
                val m = mins(t.time)!!
                val isLate = m <= now
                Text(if (isLate) "AGORA" else "PRÓXIMA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(t.label, style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    if (isLate) "Era às ${t.time} · ${now - m} min atrás. Tudo bem, dá para fazer agora."
                    else "Às ${t.time} · ${minutesLabel((m - now).toLong())}" + if (t.min > 0) " · leva uns ${t.min} min" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!isLate) {
                    // quanto do intervalo até a próxima tarefa já passou
                    val prev = tasks.mapNotNull { mins(it.time) }.filter { it <= now }.maxOrNull() ?: (m - 120).coerceAtLeast(0)
                    val span = (m - prev).coerceAtLeast(1)
                    val target = ((now - prev).toFloat() / span).coerceIn(0f, 1f)
                    val frac by animateFloatAsState(target, MaterialTheme.motionScheme.slowSpatialSpec(), label = "frac")
                    LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = cs.primary, trackColor = cs.onPrimaryContainer.copy(alpha = 0.15f))
                }
                if (t.step.isNotBlank()) {
                    Text("Primeiro passo: ${t.step}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onFocus(t) }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Começar", maxLines = 1)
                    }
                    FilledTonalButton(onClick = { toggleTask(fb, d, t.id, true) }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Feito", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(i: Insight, onOpenPlan: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (i.kind) {
        // cores calmas de propósito: o aviso convida a olhar o plano, não assusta
        InsightKind.UP, InsightKind.DOWN -> cs.secondaryContainer to cs.onSecondaryContainer
        InsightKind.KUDOS -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.surfaceContainerHigh to cs.onSurface
    }
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(if (isNarrow()) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(i.title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(i.body, style = MaterialTheme.typography.bodyLarge)
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
    val fb = rememberFeedback()

    SectionCard(
        title = "Como você está?",
        subtitle = "Pense no dia como um todo. Leva menos de um minuto.",
    ) {
        Label("Humor")
        HumorScale(humor) { Repo.set("$day/humor", it) }

        Label("Sono da última noite")
        SleepRow(day)

        TextButton(onClick = { fb.tick(); more = !more }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (more) "Menos detalhes" else "Energia, foco, sinais e anotação")
            Spacer(Modifier.width(4.dp))
            Icon(if (more) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
        }
        AnimatedVisibility(more, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                onClick = { fb.tick(); Repo.setStrSet("$day/signs", if (sel) on - s.id else on + s.id) },
                                label = { Text(s.label, modifier = Modifier.padding(vertical = 6.dp)) },
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
                            onClick = { fb.tick(); Repo.setStrSet("$day/events", if (sel) ev - id else ev + id) },
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
                    label = { Text("Anotação do dia") },
                    placeholder = { Text("Como foi o dia, apetite, conversas...") },
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(title, Modifier.weight(1f))
            AnimatedContent(v, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = field) { x ->
                Text(x?.let { names[it - range.first] } ?: "", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ValueTile("Deitei", bed, Modifier.weight(1f)) { editing = "bed" }
        ValueTile("Acordei", wake, Modifier.weight(1f)) { editing = "wake" }
    }
    AnimatedVisibility(hours != null) {
        val cs = MaterialTheme.colorScheme
        val h = hours ?: 0.0
        val short = h < 6.0
        Text(
            "%.1f h de sono".format(h).replace('.', ',') + if (short) " · noite curta" else "",
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
private fun RoutineHeader(day: String, tasks: List<Task>, allDone: Boolean) {
    val done = tasks.count { Repo.done(day, it.id) }
    val target = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
    val frac by animateFloatAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "routine")
    Column(Modifier.padding(top = 12.dp, start = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { frac }, modifier = Modifier.size(56.dp))
                AnimatedContent(done, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "count") { n ->
                    Text("$n", style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Rotina", style = MaterialTheme.typography.titleLargeEmphasized)
                Text("$done de ${tasks.size} · toque para marcar, segure para focar", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(allDone, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = MaterialTheme.shapes.large) {
                Text(
                    "Rotina completa. Cada dia assim fortalece o seu ritmo.",
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaskItem(day: String, t: Task, index: Int, count: Int, isToday: Boolean, onFocus: (Task?) -> Unit, modifier: Modifier = Modifier) {
    val done = Repo.done(day, t.id)
    val cs = MaterialTheme.colorScheme
    val at = Repo.doneAt(day, t.id)
    val fb = rememberFeedback()
    SegmentedListItem(
        checked = done,
        onCheckedChange = { toggleTask(fb, day, t.id, it) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        onLongClick = if (isToday) ({ onFocus(t) }) else null,
        onLongClickLabel = "Começar foco",
        leadingContent = { MorphCheck(done) },
        supportingContent = {
            val bits = mutableListOf<String>()
            if (t.step.isNotBlank() && !done) bits += "1º passo: ${t.step}"
            if (t.min > 0 && !done) bits += "~${t.min} min"
            if (done && at != null) bits += "feito às $at"
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.time, style = MaterialTheme.typography.titleMedium, color = if (done) cs.onSurfaceVariant else cs.primary, maxLines = 1)
                if (t.med) Tag("remédio", cs.tertiaryContainer, cs.onTertiaryContainer)
                else if (t.anchor) Tag("âncora", cs.secondaryContainer, cs.onSecondaryContainer)
            }
        },
    ) {
        Text(
            t.label,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) cs.onSurfaceVariant else Color.Unspecified,
        )
    }
}
