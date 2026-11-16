package com.dvdiso.sunbi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Prefs
import com.dvdiso.sunbi.SunAI
import kotlinx.coroutines.launch

private val SUGGESTIONS = listOf(
    "Como foi minha semana?",
    "Como foi meu mês?",
    "Me dá um conselho para hoje",
    "Ajuste minha rotina",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SunAIScreen(contentPadding: PaddingValues, initial: String? = null, onInitialSent: () -> Unit = {}) {
    var ready by remember { mutableStateOf(Prefs.aiConsent && Prefs.aiKey.isNotBlank()) }
    var setup by remember { mutableStateOf(false) }
    if (!ready) {
        SunAISetup(contentPadding) { ready = true }
        return
    }
    SunAIChat(contentPadding, initial, onInitialSent, onDisconnect = { ready = false })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SunAIChat(contentPadding: PaddingValues, initial: String?, onInitialSent: () -> Unit, onDisconnect: () -> Unit) {
    var setup by remember { mutableStateOf(false) }

    val msgs = remember { mutableStateListOf<SunAI.Msg>().apply { addAll(SunAI.load()) } }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val fb = rememberFeedback()
    val density = androidx.compose.ui.platform.LocalDensity.current

    fun send(q: String) {
        if (q.isBlank() || busy) return
        fb.send()
        msgs += SunAI.Msg("user", q.trim())
        input = ""
        busy = true
        scope.launch {
            val reply = runCatching { SunAI.ask(msgs.dropLast(1), q.trim()) }
                .getOrElse { SunAI.Msg("error", it.message ?: "Não consegui falar com o OpenCode Go.") }
            msgs += reply
            when {
                reply.role == "error" -> fb.error()
                reply.changes.isNotEmpty() -> fb.toggle(true)
                else -> fb.tap()
            }
            SunAI.save(msgs)
            busy = false
        }
    }
    // pergunta que veio do tour inicial ("Refinar com a SunAI")
    LaunchedEffect(initial) {
        if (initial != null) { send(initial); onInitialSent() }
    }
    LaunchedEffect(msgs.size, busy) {
        val last = msgs.size + (if (busy) 1 else 0) - 1
        if (last >= 0) list.animateScrollToItem(last)
    }

    // o campo fica logo acima do teclado; sem teclado, logo acima da barra de abas
    val bottomBar = contentPadding.calculateBottomPadding()
    val ime = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val inputBottom = (ime - bottomBar).coerceAtLeast(0.dp) + bottomBar + 8.dp
    val side = PaddingValues(horizontal = 16.dp)

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        // cabeçalho enxuto: nome e um menu
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SunBiMark(size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text("SunAI", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções da SunAI") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Modelo e chave") }, onClick = { menu = false; setup = true })
                    DropdownMenuItem(text = { Text("Nova conversa") }, enabled = msgs.isNotEmpty(),
                        onClick = { menu = false; SunAI.clear(); msgs.clear() })
                }
            }
        }

        if (msgs.isEmpty() && !busy) {
            // tela vazia: a flor, uma pergunta e as sugestões
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(side),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SunBiMark(size = 72.dp)
                Spacer(Modifier.height(16.dp))
                Text("Como posso ajudar?", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text("Eu olho os seus registros e a sua rotina.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (sug in SUGGESTIONS) {
                        Text(
                            sug,
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .bouncyClick { send(sug) }.padding(horizontal = 18.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(msgs) { i, m ->
                    Message(m, onUndo = {
                        SunAI.undo(m)
                        msgs[i] = m.copy(undone = true)
                        SunAI.save(msgs)
                    })
                }
                if (busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pensando…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // campo de mensagem com o botão de enviar dentro
        OutlinedTextField(
            input, { input = it },
            placeholder = { Text("Escreva para a SunAI") },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = inputBottom),
            shape = MaterialTheme.shapes.extraLarge, maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send(input) }),
            trailingIcon = {
                FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar")
                }
            },
        )
    }

    if (setup) {
        SunAISettingsSheet(
            onDismiss = { setup = false },
            onClear = { SunAI.clear(); msgs.clear(); setup = false },
            onDisconnect = { Prefs.aiConsent = false; Prefs.aiKey = ""; setup = false; onDisconnect() },
        )
    }
}

/** **negrito** e "- item" do texto da IA, sem depender de biblioteca de markdown. */
private fun richText(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.lines().map { l ->
        val t = l.trimStart()
        if (t.startsWith("- ") || t.startsWith("* ")) "• " + t.drop(2) else l.replace(Regex("^#+\\s*"), "")
    }
    val src = lines.joinToString("\n")
    var i = 0
    val bold = Regex("\\*\\*(.+?)\\*\\*")
    for (m in bold.findAll(src)) {
        append(src.substring(i, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        i = m.range.last + 1
    }
    append(src.substring(i))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Message(m: SunAI.Msg, onUndo: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (m.role == "user") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                m.text,
                Modifier.widthIn(max = 320.dp).clip(MaterialTheme.shapes.extraLarge).background(cs.surfaceContainerHighest)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
            )
        }
        return
    }
    Row(Modifier.fillMaxWidth()) {
        SunBiMark(Modifier.padding(top = 2.dp), size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                richText(m.text),
                style = MaterialTheme.typography.bodyLarge,
                color = if (m.role == "error") cs.error else cs.onSurface,
            )
            if (m.changes.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cs.surfaceContainerLow)
                        .border(1.dp, cs.outlineVariant, MaterialTheme.shapes.large).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(if (m.undone) "Mudanças desfeitas" else "Mudei na sua rotina", style = MaterialTheme.typography.labelLarge,
                        color = cs.primary)
                    for (c in m.changes) {
                        val sign = when (c.kind) { "add" -> "+"; "remove" -> "−"; else -> "↻" }
                        Text("$sign ${c.text}" + if (c.routine == "folga") " · folga" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (m.undone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    }
                    if (!m.undone && m.undo != null) {
                        TextButton(onClick = onUndo, contentPadding = PaddingValues(0.dp)) { Text("Desfazer") }
                    }
                }
            }
        }
    }
}

/** Primeira vez: explica o que é enviado, pede a chave e o consentimento. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SunAISetup(insets: PaddingValues, onReady: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var key by remember { mutableStateOf(Prefs.aiKey) }
    var agree by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.enterFromBelow(0), verticalAlignment = Alignment.CenterVertically) {
            SunBiMark(size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text("SunAI", style = MaterialTheme.typography.headlineMediumEmphasized)
        }
        Column(Modifier.enterFromBelow(1), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("A SunAI lê sua rotina e os últimos 31 dias para responder. Tudo vai para o OpenCode Go, com a sua chave.",
                style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { Feedback.tap(); more = !more }, contentPadding = PaddingValues(0.dp)) {
                Text(if (more) "Mostrar menos" else "Saiba mais")
            }
            AnimatedVisibility(more) {
                Text(
                    "A cada pergunta, o app envia as duas rotinas, o seu plano e os registros dos últimos 31 dias (humor, sono, " +
                        "remédios marcados, sinais e anotações) ao OpenCode Go e ao modelo escolhido. A chave e a conversa ficam só " +
                        "neste aparelho e podem ser apagadas. A SunAI não substitui sua equipe de saúde e nunca muda remédios.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            key, { key = it.trim() }, label = { Text("Chave do OpenCode Go") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().enterFromBelow(2),
            shape = MaterialTheme.shapes.large,
            supportingText = { Text("Pegue em opencode.ai/console") },
        )
        Row(
            Modifier.fillMaxWidth().enterFromBelow(3).clip(MaterialTheme.shapes.large)
                .bouncyClick { Feedback.tick(); agree = !agree }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(agree, { Feedback.tick(); agree = it })
            Text("Entendi e quero usar", style = MaterialTheme.typography.bodyLarge)
        }
        Button(
            onClick = {
                Feedback.toggle(true)
                Prefs.aiKey = key
                Prefs.aiConsent = true
                onReady()
            },
            enabled = agree && key.length >= 8,
            shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp).enterFromBelow(4),
        ) { Text("Ativar a SunAI", style = MaterialTheme.typography.titleMedium) }
        Text("O modelo muda depois em ⋮ → Modelo e chave.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun ModelPicker(selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((id, name) in SunAI.MODELS) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { onPick(id) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected == id, { onPick(id) })
                Text(name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SunAISettingsSheet(onDismiss: () -> Unit, onClear: () -> Unit, onDisconnect: () -> Unit) {
    var model by remember { mutableStateOf(Prefs.aiModel) }
    var weekly by remember { mutableStateOf(Prefs.weeklySummary) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    EditorSheet("SunAI", "Chave, modelo e conversa", onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Resumo da semana no domingo", style = MaterialTheme.typography.bodyLarge)
                Text("Às 19h, por notificação. Também fica na conversa.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(weekly, { weekly = it; Prefs.weeklySummary = it; com.dvdiso.sunbi.Reminders.schedule(ctx); Feedback.tick() })
        }
        Label("Modelo")
        ModelPicker(model) { model = it; Prefs.aiModel = it }
        OutlinedButton(onClick = onClear, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Apagar a conversa") }
        TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Desligar a SunAI e apagar a chave") }
    }
}
