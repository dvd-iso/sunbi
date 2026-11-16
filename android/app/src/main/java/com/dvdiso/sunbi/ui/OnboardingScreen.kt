package com.dvdiso.sunbi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Answers
import com.dvdiso.sunbi.EXTRAS
import com.dvdiso.sunbi.Prefs
import com.dvdiso.sunbi.RoutineBuilder
import com.dvdiso.sunbi.SyncManager
import com.dvdiso.sunbi.Task
import java.time.LocalTime

private const val LAST = 9

/**
 * Tour inicial: perguntas curtas que viram a rotina de semana e a de folga.
 * Quem já usa o SunBi em outro aparelho só digita o código de pareamento.
 * [onDone] recebe a pergunta para abrir a SunAI (ou null para ir direto para Hoje).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onDone: (aiKickoff: String?) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var a by remember { mutableStateOf(Answers()) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    val progress by animateFloatAsState(if (pairing) 1f else step.toFloat() / LAST, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "tour")
    BackHandler(enabled = step > 0 || pairing) { if (pairing) pairing = false else step-- }
    val next: () -> Unit = { Feedback.step(); step += 1 }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (pairing) pairing = false else step-- }, enabled = step > 0 || pairing) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
            }
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Spacer(Modifier.width(48.dp))
        }
        AnimatedContent(
            targetState = if (pairing) -1 else step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
            },
            label = "step", modifier = Modifier.weight(1f).widthIn(max = 560.dp),
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (s) {
                    -1 -> PairStep { onDone(null) }
                    0 -> Welcome(onStart = next, onPair = { pairing = true })
                    1 -> NameStep(a, { a = it }, next)
                    2 -> FocusStep(a, { a = it }, next)
                    3 -> SleepStep(a, { a = it }, next)
                    4 -> WeekendStep(a, { a = it }, next)
                    5 -> MedsStep(a, { a = it }, next)
                    6 -> DayStep(a, { a = it }, next)
                    7 -> MealsStep(a, { a = it }, next)
                    8 -> ExtrasStep(a, { a = it }, next)
                    else -> ReviewStep(a, onFinish = { ai ->
                        RoutineBuilder.apply(a)
                        Prefs.onboarded = true
                        Prefs.seenLevel = 1
                        Feedback.toggle(true, completesDay = true)
                        onDone(if (ai) RoutineBuilder.kickoff(a) else null)
                    })
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Title(kicker: String, title: String, hint: String? = null) {
    Text(kicker, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
    if (hint != null) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Next(text: String = "Continuar", enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Welcome(onStart: () -> Unit, onPair: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SunBiMark(size = 120.dp, spin = true)
        Spacer(Modifier.height(8.dp))
        Text("Oi! Eu sou o SunBi.", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        Text(
            "Vou te fazer umas perguntas rápidas e montar a sua rotina. Dá para mudar tudo depois.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Text(
            "Horários regulares protegem o humor e ajudam a começar as coisas. É isso que a gente vai cuidar juntos.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Next("Vamos lá", onClick = onStart)
        TextButton(onClick = onPair) { Text("Já uso o SunBi em outro aparelho") }
    }
}

@Composable
private fun NameStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title("1 de 8", "Como posso te chamar?", "Pode ser só o primeiro nome. Também vale pular.")
    OutlinedTextField(
        a.name, { set(a.copy(name = it)) }, label = { Text("Seu nome") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
    )
    Next(if (a.name.isBlank()) "Pular" else "Continuar", onClick = next)
}

@Composable
private fun FocusStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title("2 de 8", "Do que você quer cuidar?", "Isso muda o que a SunAI e os avisos olham primeiro.")
    OptionList(
        listOf("bipolar" to "Bipolaridade", "tdah" to "TDAH", "both" to "Os dois"),
        a.focus, { set(a.copy(focus = it)) },
    )
    Next(onClick = next)
}

@Composable
private fun SleepStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title("3 de 8", "Nos dias de semana, que horas você…", "Levantar e deitar são as duas âncoras mais importantes do ritmo.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTile("Levanta", a.wake, Modifier.weight(1f)) { set(a.copy(wake = it)) }
        TimeTile("Deita", a.bed, Modifier.weight(1f)) { set(a.copy(bed = it)) }
    }
    Text(sleepHint(a.wake, a.bed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Next(onClick = next)
}

private fun sleepHint(wake: String, bed: String): String {
    val w = LocalTime.parse(wake).toSecondOfDay() / 60
    val b = LocalTime.parse(bed).toSecondOfDay() / 60
    var h = (w - b + 24 * 60) % (24 * 60) / 60.0
    if (h == 0.0) h = 24.0
    return "Isso dá cerca de ${"%.1f".format(h).replace('.', ',')} h de sono." + if (h < 7) " Menos de 7 h costuma mexer com o humor; vale conversar com sua equipe." else ""
}

@Composable
private fun WeekendStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title("4 de 8", "E na folga, você acorda…", "Fim de semana, feriado e férias usam uma rotina mais solta.")
    OptionList(
        listOf(0 to "No mesmo horário", 60 to "Até 1 hora depois", 120 to "Umas 2 horas depois"),
        a.weekendDelta, { set(a.copy(weekendDelta = it)) },
    )
    Text(
        if (a.weekendDelta > 60) "Mais de 1 hora de diferença é o \"jet lag social\": costuma desregular a semana. Eu aviso, mas a escolha é sua."
        else "Até 1 hora de diferença protege o seu ritmo. Boa escolha.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Next(onClick = next)
}

@Composable
private fun MedsStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title("5 de 8", "Você toma remédio em horário fixo?", "Eles viram alarmes sem soneca e entram no relatório. A SunAI nunca mexe neles.")
    OptionList(
        listOf("none" to "Não", "am" to "De manhã", "pm" to "À noite", "both" to "Manhã e noite"),
        a.meds, { set(a.copy(meds = it)) },
    )
    if (a.meds != "none") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.meds == "am" || a.meds == "both") TimeTile("Remédio da manhã", a.medAm, Modifier.weight(1f)) { set(a.copy(medAm = it)) }
            if (a.meds == "pm" || a.meds == "both") TimeTile("Remédio da noite", a.medPm, Modifier.weight(1f)) { set(a.copy(medPm = it)) }
        }
    }
    Next(onClick = next)
}

@Composable
private fun DayStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title("6 de 8", "O que ocupa o seu dia?", "\"Começar o dia\" é outra âncora: ter um horário para isso ajuda a não travar.")
    OptionList(
        listOf("work" to "Trabalho", "study" to "Estudo", "both" to "Trabalho e estudo", "home" to "Fico em casa"),
        a.dayKind, { set(a.copy(dayKind = it)) },
    )
    TimeTile("Começa às", a.start, Modifier.fillMaxWidth()) { set(a.copy(start = it)) }
    Next(onClick = next)
}

@Composable
private fun MealsStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title("7 de 8", "Que horas você almoça e janta?", "Comer em horário regular também é âncora. O jantar vale mais.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTile("Almoço", a.lunch, Modifier.weight(1f)) { set(a.copy(lunch = it)) }
        TimeTile("Jantar", a.dinner, Modifier.weight(1f)) { set(a.copy(dinner = it)) }
    }
    Next(onClick = next)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtrasStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title("8 de 8", "O que mais você quer na rotina?", "Marque o que faz sentido. Tudo pode mudar depois.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((id, label) in EXTRAS) {
            val on = id in a.extras
            FilterChip(
                selected = on,
                onClick = { Feedback.tick(); set(a.copy(extras = if (on) a.extras - id else a.extras + id)) },
                label = { Text(label, Modifier.padding(vertical = 6.dp)) }, shape = MaterialTheme.shapes.large,
            )
        }
    }
    Next("Montar minha rotina", onClick = next)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReviewStep(a: Answers, onFinish: (ai: Boolean) -> Unit) {
    val week = remember(a) { RoutineBuilder.weekdays(a) }
    val off = remember(a) { RoutineBuilder.offDays(a) }
    val cs = MaterialTheme.colorScheme
    Title("Pronto", if (a.name.isBlank()) "Sua rotina" else "Sua rotina, ${a.name}", "${week.size} tarefas na semana e ${off.size} na folga. Toque em Editar em Hoje para ajustar quando quiser.")
    SectionCard("Dias de semana") { for ((i, t) in week.withIndex()) PreviewRow(t, i) }
    SectionCard("Folga e férias", container = cs.surfaceContainer) { for ((i, t) in off.withIndex()) PreviewRow(t, i) }
    Next("Começar", onClick = { onFinish(false) })
    OutlinedButton(onClick = { onFinish(true) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        SunBiMark(size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Text("Refinar com a SunAI")
    }
    Text(
        "A SunAI usa a sua chave do OpenCode Go e pede consentimento antes de ver os seus dados.",
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreviewRow(t: Task, index: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.enterFromBelow(index), verticalAlignment = Alignment.CenterVertically) {
        Text(t.time, style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.width(56.dp))
        Text(t.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (t.med) Tag("remédio", cs.tertiaryContainer, cs.onTertiaryContainer)
        else if (t.anchor) Tag("âncora", cs.primaryContainer, cs.onPrimaryContainer)
    }
}

/** Código de pareamento de quem já usa o SunBi em outro aparelho: a rotina chega pela sincronização. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PairStep(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf("") }
    Title("Parear", "Qual é o código de pareamento?", "Ele aparece no outro aparelho em Ajustes → Sincronização (ou no painel do plugin do Noctalia). Os dois precisam estar na mesma rede Wi-Fi.")
    OutlinedTextField(
        key, { key = it.uppercase().take(12) }, label = { Text("Código") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
    )
    Next("Conectar", enabled = key.length >= 4) {
        Prefs.key = key
        Prefs.onboarded = true
        Prefs.seenLevel = 1
        SyncManager.stop(ctx)
        SyncManager.start(ctx)
        Feedback.toggle(true)
        onDone()
    }
    Text("A rotina, o plano e o histórico chegam sozinhos assim que os aparelhos se encontrarem.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ── peças ───────────────────────────────────────────────────────────────────

/** Lista vertical de opções grandes (uma escolha). */
@Composable
private fun <T> OptionList(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, label) in options) {
            val on = v == selected
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(if (on) cs.primaryContainer else cs.surfaceContainerHigh)
                    .bouncyClick { Feedback.tick(); onPick(v) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) cs.onPrimaryContainer else cs.onSurface)
                MorphCheck(on, size = 26.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeTile(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    ValueTile(label, value, modifier) { picking = true }
    if (picking) {
        val t = LocalTime.parse(value)
        val state = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { picking = false },
            title = { Text(label) },
            confirmButton = { TextButton(onClick = { onChange("%02d:%02d".format(state.hour, state.minute)); picking = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { TimePicker(state) }
    }
}
