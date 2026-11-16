package com.dvdiso.sunbi.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
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
import com.dvdiso.sunbi.CONCERNS
import com.dvdiso.sunbi.EXTRAS
import com.dvdiso.sunbi.Prefs
import com.dvdiso.sunbi.RoutineBuilder
import com.dvdiso.sunbi.SyncManager
import com.dvdiso.sunbi.Task
import java.time.LocalTime

/** Número de perguntas; o passo seguinte é a revisão da rotina. */
private const val QUESTIONS = 11
private const val LAST = QUESTIONS + 1

/**
 * Tour inicial: perguntas curtas que viram a rotina de semana, a de folga e os ajustes do app.
 * Serve para qualquer pessoa: nenhuma pergunta supõe um diagnóstico, um emprego ou o uso de IA.
 * Quem já usa o SunBi em outro aparelho só digita o código de pareamento.
 * [onDone] recebe a pergunta para abrir a assistente (ou null para ir direto para Hoje).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onDone: (aiKickoff: String?) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    // rememberSaveable: girar a tela ou trocar o tema no meio do tour não apaga as respostas
    var a by rememberSaveable { mutableStateOf(Answers()) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    val progress by animateFloatAsState(if (pairing) 1f else step.toFloat() / LAST, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "tour")
    BackHandler(enabled = step > 0 || pairing) { if (pairing) pairing = false else step-- }
    val next: () -> Unit = { Feedback.step(); step += 1 }
    val ctx = LocalContext.current

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
                    2 -> ConcernsStep(a, { a = it }, next)
                    3 -> SleepStep(a, { a = it }, next)
                    4 -> OffDaysStep(a, { a = it }, next)
                    5 -> WeekendStep(a, { a = it }, next)
                    6 -> MedsStep(a, { a = it }, next)
                    7 -> DayStep(a, { a = it }, next)
                    8 -> MealsStep(a, { a = it }, next)
                    9 -> ExtrasStep(a, { a = it }, next)
                    10 -> RemindersStep(a, { a = it }, next)
                    11 -> AssistantStep(a, { a = it }, next)
                    else -> ReviewStep(a, onFinish = { ai ->
                        RoutineBuilder.apply(a)
                        com.dvdiso.sunbi.Reminders.schedule(ctx)
                        Prefs.onboarded = true
                        Prefs.seenLevel = 1
                        Feedback.toggle(true, completesDay = true)
                        onDone(if (ai && a.ai) RoutineBuilder.kickoff(a) else null)
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
    Button(onClick = onClick, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
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
            "Vou te fazer umas perguntas rápidas e montar uma rotina do seu jeito. Dá para mudar tudo depois.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Text(
            "Horários regulares fazem bem para o sono, o humor e a disposição, e ajudam a começar as coisas. É disso que a gente vai cuidar.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Next("Vamos lá", onClick = onStart)
        TextButton(onClick = onPair) { Text("Já uso o SunBi em outro aparelho") }
    }
}

private fun kicker(n: Int) = "$n de $QUESTIONS"

@Composable
private fun NameStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(1), "Como posso te chamar?", "Pode ser só o primeiro nome. Também vale pular.")
    OutlinedTextField(
        a.name, { set(a.copy(name = it)) }, label = { Text("Seu nome") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
    )
    Next(if (a.name.isBlank()) "Pular" else "Continuar", onClick = next)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConcernsStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(2), "O que te trouxe aqui?", "Marque quantos quiser, ou nenhum. Isso só muda o que os avisos olham primeiro; o app não diagnostica nada.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((id, label) in CONCERNS) {
            val on = id in a.concerns
            FilterChip(
                selected = on,
                onClick = { Feedback.tick(); set(a.copy(concerns = if (on) a.concerns - id else a.concerns + id)) },
                label = { Text(label, Modifier.padding(vertical = 6.dp)) }, shape = MaterialTheme.shapes.large,
            )
        }
    }
    Next(onClick = next)
}

@Composable
private fun SleepStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(3), "Nos dias comuns, que horas você…", "Levantar e deitar são os dois horários que mais seguram o resto do dia.")
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
    return "Isso dá cerca de ${"%.1f".format(h).replace('.', ',')} h de sono." + if (h < 7) " Menos de 7 h costuma pesar no humor e na disposição." else ""
}

@Composable
private fun OffDaysStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(4), "Quando é a sua folga?", "Nos dias de folga vale uma rotina mais solta. Quem trabalha em escala pode marcar a folga dia a dia, tocando na flor.")
    val pick = when {
        a.offWeekends && a.offHolidays -> "both"
        a.offWeekends -> "weekends"
        a.offHolidays -> "holidays"
        else -> "none"
    }
    OptionList(
        listOf("both" to "Fins de semana e feriados", "weekends" to "Só fins de semana", "holidays" to "Só feriados", "none" to "Não tenho folga fixa"),
        pick, { set(a.copy(offWeekends = it == "both" || it == "weekends", offHolidays = it == "both" || it == "holidays")) },
    )
    Next(onClick = next)
}

@Composable
private fun WeekendStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(5), "E na folga, você acorda…", "Vale também para as férias.")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedsStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(6), "Você toma remédio em horário fixo?", "Se sim, marque quando. Eles viram alarmes e entram no relatório. Se não, é só continuar.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((id, label) in listOf("am" to "De manhã", "mid" to "À tarde", "pm" to "À noite")) {
            val on = id in a.meds
            FilterChip(
                selected = on,
                onClick = { Feedback.tick(); set(a.copy(meds = if (on) a.meds - id else a.meds + id)) },
                label = { Text(label, Modifier.padding(vertical = 6.dp)) }, shape = MaterialTheme.shapes.large,
            )
        }
    }
    if ("am" in a.meds) TimeTile("Remédio da manhã", a.medAm, Modifier.fillMaxWidth()) { set(a.copy(medAm = it)) }
    if ("mid" in a.meds) TimeTile("Remédio da tarde", a.medMid, Modifier.fillMaxWidth()) { set(a.copy(medMid = it)) }
    if ("pm" in a.meds) TimeTile("Remédio da noite", a.medPm, Modifier.fillMaxWidth()) { set(a.copy(medPm = it)) }
    Next(if (a.meds.isEmpty()) "Não tomo" else "Continuar", onClick = next)
}

@Composable
private fun DayStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(7), "O que ocupa o seu dia?", "Ter uma hora para começar o dia ajuda a não travar, mesmo sem horário de entrada.")
    OptionList(
        listOf("work" to "Trabalho", "study" to "Estudo", "both" to "Trabalho e estudo", "care" to "Cuido da casa ou da família", "home" to "Sem horário fixo no momento"),
        a.dayKind, { set(a.copy(dayKind = it)) },
    )
    TimeTile("Começa às", a.start, Modifier.fillMaxWidth()) { set(a.copy(start = it)) }
    Next(onClick = next)
}

@Composable
private fun MealsStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(8), "E as refeições?", "Comer em horário regular também dá ritmo ao dia.")
    ToggleRow("Tomo café da manhã", a.breakfast) { set(a.copy(breakfast = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTile("Almoço", a.lunch, Modifier.weight(1f)) { set(a.copy(lunch = it)) }
        TimeTile("Jantar", a.dinner, Modifier.weight(1f)) { set(a.copy(dinner = it)) }
    }
    Next(onClick = next)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtrasStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(9), "O que mais você quer na rotina?", "Marque o que faz sentido. Menos é mais: dá para acrescentar depois.")
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
    Next(onClick = next)
}

@Composable
private fun RemindersStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(10), "Como você quer ser lembrado?", "Tudo isso muda depois em Ajustes.")
    ToggleRow("Alarme na hora de cada tarefa", a.alarms, "Toca até você marcar Concluído ou Parar. Desligado, a rotina fica só na tela.") { set(a.copy(alarms = it)) }
    ToggleRow("Lembrete de check-in às 21h", a.checkinReminder, "Só se você ainda não tiver registrado o dia.") { set(a.copy(checkinReminder = it)) }
    ToggleRow("Sons e vibração no app", a.sounds) { set(a.copy(sounds = it)) }
    Next(onClick = next)
}

/** A IA é uma escolha: quem recusa não vê a aba nem recebe convites; dá para ligar depois. */
@Composable
private fun AssistantStep(a: Answers, set: (Answers) -> Unit, next: () -> Unit) {
    Title(kicker(11), "Quer uma assistente de IA?", "Ela conversa, resume a sua semana e ajuda a ajustar a rotina. É opcional e o app funciona inteiro sem ela.")
    OptionList(
        listOf(false to "Não quero IA", true to "Quero usar"),
        a.ai, { set(a.copy(ai = it)) },
    )
    if (a.ai) {
        OutlinedTextField(
            a.assistant, { set(a.copy(assistant = it.take(24))) }, label = { Text("Como quer chamar a assistente?") },
            placeholder = { Text(com.dvdiso.sunbi.Assistant.DEFAULT_NAME) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        Text(
            "Ela precisa de uma chave de um serviço de IA (OpenAI, OpenRouter, OpenCode Go…), que costuma ser pago, e só recebe os seus registros depois que você aceitar.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            "Sem IA, nada do que você registra sai do aparelho e a aba da assistente não aparece. Se mudar de ideia: Ajustes → Assistente.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Next("Montar minha rotina", onClick = next)
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cs.surfaceContainerHigh)
            .toggleable(checked, role = Role.Switch) { Feedback.tick(); onChange(it) }.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        // a linha inteira é o interruptor (um alvo só para o toque e para o leitor de tela)
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReviewStep(a: Answers, onFinish: (ai: Boolean) -> Unit) {
    val week = remember(a) { RoutineBuilder.weekdays(a) }
    val off = remember(a) { RoutineBuilder.offDays(a) }
    val cs = MaterialTheme.colorScheme
    Title("Pronto", if (a.name.isBlank()) "Sua rotina" else "Sua rotina, ${a.name}", "${week.size} tarefas nos dias comuns e ${off.size} na folga. Toque em Editar em Hoje para ajustar quando quiser.")
    SectionCard("Dias de semana") { for ((i, t) in week.withIndex()) PreviewRow(t, i) }
    SectionCard("Folga e férias", container = cs.surfaceContainer) { for ((i, t) in off.withIndex()) PreviewRow(t, i) }
    Next("Começar", onClick = { onFinish(false) })
    if (a.ai) {
        OutlinedButton(onClick = { onFinish(true) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            SunBiMark(size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Text("Refinar com ${a.assistant.ifBlank { "a assistente" }}")
        }
    }
}

@Composable
private fun PreviewRow(t: Task, index: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.enterFromBelow(index), verticalAlignment = Alignment.CenterVertically) {
        Text(t.time, style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.widthIn(min = 56.dp).padding(end = 8.dp))
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
    var key by rememberSaveable { mutableStateOf("") }
    Title("Parear", "Qual é o código de pareamento?", "Ele aparece no outro aparelho em Ajustes → Sincronização. Os dois precisam estar na mesma rede Wi-Fi.")
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
                    .semantics { role = Role.RadioButton; selected = on }
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
