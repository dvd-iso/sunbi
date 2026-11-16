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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.dvdiso.sunbi.Game
import com.dvdiso.sunbi.HUMOR
import com.dvdiso.sunbi.Insight
import com.dvdiso.sunbi.InsightKind
import com.dvdiso.sunbi.Prefs
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
    stats: Game.Stats,
    onFocus: (Task?) -> Unit,
    onOpenPlan: () -> Unit,
    onSettings: () -> Unit,
    onCheckin: (day: String, step: Int) -> Unit,
    onEditRoutine: () -> Unit,
    onAchievements: () -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(todayStr) }
    LaunchedEffect(todayStr) { if (day > todayStr) day = todayStr }
    val isToday = day == todayStr
    val tasks = Repo.tasksFor(day)
    val off = Repo.offReason(day)
    val fb = rememberFeedback()
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
                title = if (isToday) saudacao(Prefs.userName) else "Outro dia",
                onSettings = onSettings,
                leading = {
                    // tocar na flor liga/desliga o modo férias (rotina de folga)
                    SunBiMark(
                        Modifier.clip(CircleShape).bouncyClick(label = "Modo férias") {
                            val on = Repo.toggleOff(day)
                            fb.vacation(on)
                        },
                        size = 44.dp, turns = turns, sunglasses = off != null,
                    )
                },
                trailing = if (isToday) ({ GameChip(stats, onAchievements) }) else null,
            )
            DayPicker(day, isToday, onPrev = { day = LocalDate.parse(day).minusDays(1).toString() },
                onNext = { day = LocalDate.parse(day).plusDays(1).toString() }, onToday = { day = todayStr })
            AnimatedVisibility(off != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                OffBadge(day, off)
            }
            // dia difícil (ligado no check-in): só um selo; tocar volta à rotina inteira
            AnimatedVisibility(Repo.isHard(day), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                HardBadge { Feedback.toggle(false); Repo.setHard(day, false) }
            }
        }
        if (isToday) {
            if (tasks.isNotEmpty()) item(key = "now") { NowCard(tasks, tick, onFocus, Modifier.animateItem().enterFromBelow(0)) }
        }
        // aviso e check-in feito: uma linha cada, o detalhe abre ao tocar
        item(key = "lines") {
            Column(Modifier.animateItem().enterFromBelow(1), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (insight != null) InsightLine(insight, onOpenPlan)
                CheckinSummary(day) { step -> onCheckin(day, step) }
            }
        }
        if (tasks.isEmpty()) {
            item(key = "empty") { EmptyRoutine(off, onEditRoutine, Modifier.animateItem().enterFromBelow(4)) }
        } else {
            item(key = "routine-head") { RoutineHeader(day, tasks.size, done.size, allDone, off, onEditRoutine) }
        }
        itemsIndexed(pending, key = { _, t -> "task-" + t.id }) { i, t ->
            TaskItem(day, t, i, pending.size, isToday, onFocus, Modifier.animateItem().enterFromBelow(4 + i))
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

private fun saudacao(name: String): String {
    val h = LocalTime.now().hour
    val s = when {
        h < 5 -> "Boa noite"
        h < 12 -> "Bom dia"
        h < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
    return if (name.isBlank()) s else "$s, ${name.substringBefore(' ')}"
}

/** Sem rotina ainda (antes do tour terminar ou depois de apagar tudo). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyRoutine(off: Repo.OffReason?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (off != null) "Sem rotina de folga ainda" else "Sem rotina ainda", style = MaterialTheme.typography.titleLargeEmphasized)
            Text("Monte a sua em Rotina: dá para usar a sugerida e mudar o que quiser.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            Button(onClick = onEdit, shapes = ButtonDefaults.shapes()) { Text("Montar rotina") }
        }
    }
}

private fun mins(time: String): Int? = runCatching { LocalTime.parse(time).toSecondOfDay() / 60 }.getOrNull()

/** Cartão "Agora": a tarefa da vez, quanto falta e dois botões. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NowCard(tasks: List<Task>, tick: Long, onFocus: (Task?) -> Unit, modifier: Modifier = Modifier) {
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
                Button(onClick = { toggleTask(fb, d, t.id, true) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Concluído", maxLines = 1)
                }
            }
        }
    }
}

/** Linha de informação: ícone, texto curto e seta. Usada para o aviso e o check-in feito. */
@Composable
private fun InfoLine(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).bouncyClick(label = label) { Feedback.tap(); onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
    }
}

/** O aviso do dia numa linha; o texto inteiro e o atalho para o plano abrem numa folha. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InsightLine(i: Insight, onOpenPlan: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val warn = i.kind == InsightKind.UP || i.kind == InsightKind.DOWN
    InfoLine(
        when (i.kind) { InsightKind.KUDOS -> Icons.Filled.Star; InsightKind.UP, InsightKind.DOWN -> Icons.Filled.Warning; else -> Icons.Filled.Info },
        if (warn) cs.secondary else cs.primary, i.title, "Ver o aviso",
    ) { open = true }
    if (open) {
        EditorSheet(i.title, onDismiss = { open = false }) {
            Text(i.body, style = MaterialTheme.typography.bodyLarge)
            if (warn) {
                Button(onClick = { open = false; onOpenPlan() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Ver meu plano") }
            }
        }
    }
}

/** Check-in: convite quando falta; quando feito, uma linha com humor e sono. */
@Composable
internal fun CheckinSummary(day: String, modifier: Modifier = Modifier, open: (step: Int) -> Unit) {
    val h = Repo.humor(day)
    val s = Repo.sleep(day)
    val cs = MaterialTheme.colorScheme
    if (h == null && s == null) {
        Card(
            modifier.fillMaxWidth().padding(top = 4.dp).bouncyClick { Feedback.tap(); open(0) }, shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Como você está hoje?", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text("Humor e sono em menos de um minuto", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Button(onClick = { Feedback.tap(); open(0) }, shapes = ButtonDefaults.shapes()) { Text("Registrar") }
            }
        }
        return
    }
    val sleep = s?.let { "%.1f h de sono".format(it).replace('.', ',') }
    val text = listOfNotNull(h?.let { HUMOR[it] }, sleep).joinToString(" · ")
    Box(modifier) {
        InfoLine(Icons.Filled.Face, if (s != null && s < 6.0) cs.error else cs.primary, text, "Editar o check-in") { open(0) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutineHeader(day: String, total: Int, done: Int, allDone: Boolean, off: Repo.OffReason?, onEdit: () -> Unit) {
    val frac by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "routine")
    val hard = Repo.isHard(day)
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (hard) "Só o essencial" else if (off != null) "Rotina de folga" else "Rotina", style = MaterialTheme.typography.titleLargeEmphasized)
                Text("$done de $total feitas", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            TextButton(onClick = onEdit) { Text("Editar") }
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

/** Selo que explica por que o dia usa a rotina de folga. */
@Composable
private fun HardBadge(onOff: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        "Só o essencial hoje · toque para voltar à rotina inteira",
        Modifier.padding(top = 4.dp).clip(MaterialTheme.shapes.large).background(cs.secondaryContainer)
            .bouncyClick(label = "Voltar à rotina inteira", onClick = onOff)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun OffBadge(day: String, off: Repo.OffReason?) {
    val cs = MaterialTheme.colorScheme
    val text = when (off) {
        Repo.OffReason.VACATION -> "Modo férias · rotina de folga. Toque na flor para voltar."
        Repo.OffReason.WEEKEND -> "Fim de semana · rotina de folga"
        Repo.OffReason.HOLIDAY -> "Feriado (${com.dvdiso.sunbi.Holidays.name(LocalDate.parse(day)) ?: ""}) · rotina de folga"
        Repo.OffReason.MANUAL -> "Dia de folga · rotina de folga"
        null -> ""
    }
    Text(
        text,
        Modifier.padding(top = 4.dp).clip(MaterialTheme.shapes.large).background(cs.tertiaryContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer, fontWeight = FontWeight.SemiBold,
    )
}
