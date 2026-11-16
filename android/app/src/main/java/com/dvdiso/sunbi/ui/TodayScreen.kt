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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.HUMOR
import com.dvdiso.sunbi.Insight
import com.dvdiso.sunbi.InsightKind
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.Task
import com.dvdiso.sunbi.insights
import com.dvdiso.sunbi.niceDay
import com.dvdiso.sunbi.shortDay
import com.dvdiso.sunbi.today
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(
    todayStr: String,
    tick: Long,
    contentPadding: PaddingValues,
    onFocus: (Task?) -> Unit,
    onOpenPlan: () -> Unit,
    onSettings: () -> Unit,
    onCheckin: (day: String, step: Int) -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(todayStr) }
    LaunchedEffect(todayStr) { if (day > todayStr) day = todayStr }
    val isToday = day == todayStr
    val tasks = Repo.tasks()
    val pending = tasks.filter { !Repo.done(day, it.id) }
    val done = tasks.filter { Repo.done(day, it.id) }
    // só o aviso mais importante; o resto fica no plano
    val insight = if (isToday) insights(day).firstOrNull() else null
    val allDone = tasks.isNotEmpty() && pending.isEmpty()
    var showDone by rememberSaveable(day) { mutableStateOf(false) }
    // gira a flor uma vez quando a rotina do dia fecha
    var turns by remember { mutableFloatStateOf(0f) }
    var wasDone by remember(day) { mutableStateOf(allDone) }
    LaunchedEffect(allDone) {
        if (allDone && !wasDone) turns += 1f
        wasDone = allDone
    }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") {
            ScreenHeader(
                title = if (isToday) saudacao() else "Outro dia",
                onSettings = onSettings,
                leading = { SunBiMark(size = 44.dp, turns = turns) },
            )
            DayPicker(day, isToday, onPrev = { day = LocalDate.parse(day).minusDays(1).toString() },
                onNext = { day = LocalDate.parse(day).plusDays(1).toString() }, onToday = { day = todayStr })
        }
        if (isToday) {
            item(key = "now") { NowCard(tasks, tick, onFocus, Modifier.animateItem()) }
            if (insight != null) item(key = "insight") { InsightCard(insight, onOpenPlan, Modifier.animateItem()) }
        }
        item(key = "checkin") { CheckinSummary(day, Modifier.animateItem()) { step -> onCheckin(day, step) } }
        item(key = "routine-head") { RoutineHeader(tasks.size, done.size, allDone) }
        itemsIndexed(pending, key = { _, t -> "task-" + t.id }) { i, t ->
            TaskItem(day, t, i, pending.size, isToday, onFocus, Modifier.animateItem())
        }
        if (done.isNotEmpty()) {
            item(key = "done-toggle") {
                TextButton(onClick = { showDone = !showDone }, modifier = Modifier.animateItem()) {
                    Icon(if (showDone) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (showDone) "Esconder as feitas" else "Ver ${done.size} feita${if (done.size > 1) "s" else ""}")
                }
            }
            if (showDone) {
                itemsIndexed(done, key = { _, t -> "done-" + t.id }) { i, t ->
                    TaskItem(day, t, i, done.size, isToday, onFocus, Modifier.animateItem())
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DayPicker(day: String, isToday: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val narrow = isNarrow()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Dia anterior") }
        AnimatedContent(day, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "day", modifier = Modifier.weight(1f)) { d ->
            Text(
                (if (narrow) shortDay(d) else niceDay(d)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!isToday) TextButton(onClick = onToday) { Text("Hoje", maxLines = 1) }
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

/** Cartão "Agora": a tarefa da vez, quanto falta e dois botões. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowCard(tasks: List<Task>, tick: Long, onFocus: (Task?) -> Unit, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") tick
    val d = today()
    val fb = rememberFeedback()
    val now = LocalTime.now().toSecondOfDay() / 60
    val pending = tasks.filter { !Repo.done(d, it.id) && mins(it.time) != null }
    val late = pending.lastOrNull { val m = mins(it.time)!!; m <= now && now - m <= 180 }
    val next = pending.firstOrNull { mins(it.time)!! > now }
    val cs = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
    ) {
        AnimatedContent(
            targetState = (late ?: next)?.id,
            transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut()) },
            label = "now",
        ) { id ->
            val t = tasks.firstOrNull { it.id == id }
            Column(Modifier.fillMaxWidth().padding(if (isNarrow()) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t == null) {
                    Text("Nada pendente agora", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text("Descansar também faz parte da rotina.", style = MaterialTheme.typography.bodyLarge)
                    return@Column
                }
                val m = mins(t.time)!!
                val isLate = m <= now
                Text(
                    if (isLate) "AGORA · era às ${t.time}" else "PRÓXIMA · ${t.time} · ${minutesLabel((m - now).toLong())}",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                )
                Text(t.label, style = MaterialTheme.typography.headlineSmallEmphasized)
                if (t.step.isNotBlank()) {
                    Text("Comece por: ${t.step}", style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onFocus(t) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Começar", maxLines = 1)
                    }
                    FilledTonalButton(onClick = { toggleTask(fb, d, t.id, true) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
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
        InsightKind.UP, InsightKind.DOWN -> cs.secondaryContainer to cs.onSecondaryContainer
        InsightKind.KUDOS -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.surfaceContainerHigh to cs.onSurface
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier.fillMaxWidth().clickable { open = !open },
        shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(i.title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(i.body, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            if (i.kind == InsightKind.UP || i.kind == InsightKind.DOWN) {
                TextButton(onClick = onOpenPlan, contentPadding = PaddingValues(0.dp)) { Text("Ver meu plano", color = fg, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Resumo do check-in: convida a fazer ou mostra o que já foi registrado. */
@Composable
private fun CheckinSummary(day: String, modifier: Modifier = Modifier, open: (step: Int) -> Unit) {
    val h = Repo.humor(day)
    val s = Repo.sleep(day)
    val cs = MaterialTheme.colorScheme
    if (h == null && s == null) {
        Card(
            modifier.fillMaxWidth().clickable { open(0) }, shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Como você está hoje?", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text("Humor e sono em menos de um minuto", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Button(onClick = { open(0) }, shapes = ButtonDefaults.shapes()) { Text("Registrar") }
            }
        }
        return
    }
    Card(
        modifier.fillMaxWidth().clickable { open(0) }, shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Check-in", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Text(h?.let { HUMOR[it] } ?: "Humor sem registro", style = MaterialTheme.typography.titleMedium)
                Text(
                    s?.let { "%.1f h de sono".format(it).replace('.', ',') } ?: "Sono sem registro",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s != null && s < 6.0) cs.error else cs.onSurfaceVariant,
                )
            }
            TextButton(onClick = { open(0) }) { Text("Editar") }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutineHeader(total: Int, done: Int, allDone: Boolean) {
    val frac by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "routine")
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rotina", style = MaterialTheme.typography.titleLargeEmphasized)
                Text("$done de $total feitas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { frac }, modifier = Modifier.size(44.dp))
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

/** Linha de tarefa: caixinha, nome e hora. Segurar abre o foco. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaskItem(day: String, t: Task, index: Int, count: Int, isToday: Boolean, onFocus: (Task?) -> Unit, modifier: Modifier = Modifier) {
    val done = Repo.done(day, t.id)
    val cs = MaterialTheme.colorScheme
    val fb = rememberFeedback()
    SegmentedListItem(
        checked = done,
        onCheckedChange = { toggleTask(fb, day, t.id, it) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        onLongClick = if (isToday && !done) ({ onFocus(t) }) else null,
        onLongClickLabel = "Começar foco",
        leadingContent = { MorphCheck(done) },
        supportingContent = if (t.med) ({ Text("remédio", color = cs.tertiary, fontWeight = FontWeight.SemiBold) }) else null,
        trailingContent = {
            Text(Repo.doneAt(day, t.id) ?: t.time, style = MaterialTheme.typography.titleMedium,
                color = if (done) cs.onSurfaceVariant else cs.primary, maxLines = 1)
        },
    ) {
        Text(
            t.label,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) cs.onSurfaceVariant else androidx.compose.ui.graphics.Color.Unspecified,
        )
    }
}
