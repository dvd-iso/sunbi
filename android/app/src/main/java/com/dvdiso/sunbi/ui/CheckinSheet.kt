package com.dvdiso.sunbi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.EVENTS
import com.dvdiso.sunbi.HUMOR
import com.dvdiso.sunbi.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

private const val STEPS = 3

/**
 * Check-in em passos curtos: 1) humor, 2) sono, 3) detalhes opcionais.
 * Escolher o humor já avança; dá para fechar a qualquer momento (tudo é salvo na hora).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CheckinSheet(day: String, startStep: Int = 0, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    val progress by animateFloatAsState((step + 1f) / STEPS, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "steps")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding()) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
                },
                label = "step",
            ) { s ->
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (s) {
                        0 -> HumorStep(day) { step = 1 }
                        1 -> SleepStep(day, onBack = { step = 0 }, onNext = { step = 2 }, onDone = onDismiss)
                        else -> DetailsStep(day, onBack = { step = 1 }, onDone = onDismiss)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StepTitle(n: Int, title: String, hint: String) {
    Text("Passo $n de $STEPS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
    Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Sete opções escritas por extenso, de cima (acelerado) para baixo (para baixo). Tocar já avança. */
@Composable
private fun HumorStep(day: String, onNext: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val fb = rememberFeedback()
    val current = Repo.humor(day)
    var picked by remember { mutableStateOf<Int?>(null) }
    // humor para baixo: pergunta uma vez se quer deixar o dia só com o essencial
    var offerEssential by remember { mutableStateOf(false) }
    var offered by remember { mutableStateOf(false) }
    LaunchedEffect(picked) {
        val p = picked ?: return@LaunchedEffect
        delay(280) // deixa ver a escolha antes de avançar
        if (p <= -2 && !offered && !Repo.isHard(day) && Repo.tasksFor(day).isNotEmpty()) {
            offered = true
            offerEssential = true
        } else onNext()
    }
    if (offerEssential) {
        EssentialOffer(
            onYes = { Feedback.toggle(true); Repo.setHard(day, true); offerEssential = false; onNext() },
            onNo = { Feedback.tick(); offerEssential = false; onNext() },
        )
        return
    }
    StepTitle(1, "Como está o seu humor hoje?", "Pense no dia como um todo.")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (v in 3 downTo -3) {
            val sel = (picked ?: current) == v
            val tone = when {
                v > 0 -> MoodColors.up(cs, dark)
                v < 0 -> MoodColors.down(cs, dark)
                else -> cs.primary
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.large)
                    .background(if (sel) tone.copy(alpha = 0.22f) else cs.surfaceContainerHigh)
                    .then(if (sel) Modifier.border(2.dp, tone, MaterialTheme.shapes.large) else Modifier)
                    .clickable {
                        fb.tick()
                        Repo.set("$day/humor", v)
                        picked = v
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // barrinha que cresce com a intensidade, para cima ou para baixo
                Box(Modifier.size(width = 6.dp, height = (8 + 6 * kotlin.math.abs(v)).dp).clip(CircleShape).background(tone))
                Spacer(Modifier.width(14.dp))
                Text(HUMOR[v] ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                Text(if (v > 0) "+$v" else "$v", style = MaterialTheme.typography.titleMedium, color = tone)
            }
        }
    }
}

/** Pergunta curta depois de um humor para baixo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EssentialOffer(onYes: () -> Unit, onNo: () -> Unit) {
    Text("Quer deixar hoje só o essencial?", style = MaterialTheme.typography.headlineSmallEmphasized)
    Text("A rotina de hoje fica só com as âncoras e os remédios. O resto espera, sem culpa.",
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onNo, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Não") }
        Button(onClick = onYes, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Só o essencial") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SleepStep(day: String, onBack: () -> Unit, onNext: () -> Unit, onDone: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    val bed = Repo.bed(day)
    val wake = Repo.wake(day)
    val hours = Repo.sleep(day)
    StepTitle(2, "Como você dormiu?", "A hora em que deitou ontem e a que acordou hoje.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ValueTile("Deitei", bed, Modifier.weight(1f)) { editing = "bed" }
        ValueTile("Acordei", wake, Modifier.weight(1f)) { editing = "wake" }
    }
    if (hours != null) {
        val short = hours < 6.0
        Text(
            "%.1f h de sono".format(hours).replace('.', ',') + if (short) " · noite curta" else "",
            style = MaterialTheme.typography.titleLarge,
            color = if (short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    HealthButton(day)
    // quebra de linha em tela estreita ou fonte grande, em vez de espremer o "Pronto"
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onBack) { Text("Voltar") }
        OutlinedButton(onClick = onNext, shapes = ButtonDefaults.shapes()) { Text("Mais detalhes") }
        Button(onClick = onDone, shapes = ButtonDefaults.shapes()) { Text("Pronto") }
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

/** "Puxar do relógio": lê a noite no Health Connect (pede a permissão na primeira vez). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HealthButton(day: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (!remember { com.dvdiso.sunbi.Health.available(ctx) }) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val fb = rememberFeedback()
    fun pull() {
        busy = true
        scope.launch {
            val night = com.dvdiso.sunbi.Health.lastNight(ctx, day)
            busy = false
            if (night == null) { msg = "Nenhuma noite registrada no Health Connect para esse dia."; fb.error() }
            else { Repo.setSleepTimes(day, night.first, night.second); msg = "Copiado do relógio: ${night.first} → ${night.second}"; fb.toggle(true) }
        }
    }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { granted -> if (granted.containsAll(com.dvdiso.sunbi.Health.PERMISSIONS)) pull() else msg = "Sem permissão para ler o sono." }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { fb.tap(); scope.launch { if (com.dvdiso.sunbi.Health.granted(ctx)) pull() else ask.launch(com.dvdiso.sunbi.Health.PERMISSIONS) } },
            enabled = !busy, shapes = ButtonDefaults.shapes(),
        ) { Text(if (busy) "Lendo…" else "Puxar do relógio (Health Connect)") }
        msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(day: String, onBack: () -> Unit, onDone: () -> Unit) {
    val fb = rememberFeedback()
    StepTitle(3, "Algo mais?", "Tudo opcional. Marque só o que fizer sentido hoje.")
    LevelRow(day, "energy", "Energia", 1..5, listOf("muito baixa", "baixa", "normal", "alta", "muito alta"))
    LevelRow(day, "focus", "Foco e atenção", 1..5, listOf("disperso", "difícil", "ok", "bom", "ótimo"))
    LevelRow(day, "irrit", "Irritação", 0..3, listOf("nenhuma", "leve", "moderada", "forte"))
    LevelRow(day, "anx", "Ansiedade", 0..3, listOf("nenhuma", "leve", "moderada", "forte"))

    val signs = Repo.signs()
    if (signs.isNotEmpty()) {
        Label("Algum dos seus sinais apareceu?")
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

    Label("Aconteceu algo?")
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
        delay(800)
        if (note != Repo.note(day)) Repo.set("$day/note", note)
    }
    OutlinedTextField(
        value = note, onValueChange = { note = it },
        label = { Text("Anotação do dia") },
        placeholder = { Text("Como foi o dia, apetite, conversas...") },
        minLines = 3, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("Voltar") }
        Spacer(Modifier.weight(1f))
        Button(onClick = {
            if (note != Repo.note(day)) Repo.set("$day/note", note)
            onDone()
        }, shapes = ButtonDefaults.shapes()) { Text("Concluir") }
    }
}

@Composable
private fun LevelRow(day: String, field: String, title: String, range: IntRange, names: List<String>) {
    val v = Repo.level(day, field)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(title, Modifier.weight(1f))
            Text(v?.let { names[it - range.first] } ?: "", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        ChoiceGroup(
            options = range.map { it to it.toString() },
            selected = v,
            onSelect = { Repo.setLevel(day, field, it) },
            description = { "$title: ${names[it - range.first]}" },
        )
    }
}
