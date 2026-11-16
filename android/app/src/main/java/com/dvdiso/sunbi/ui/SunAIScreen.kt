package com.dvdiso.sunbi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SunAIScreen(contentPadding: PaddingValues, onSettings: () -> Unit) {
    var ready by remember { mutableStateOf(Prefs.aiConsent && Prefs.aiKey.isNotBlank()) }
    var setup by remember { mutableStateOf(false) }
    if (!ready) {
        SunAISetup(contentPadding) { ready = true }
        return
    }

    val msgs = remember { mutableStateListOf<SunAI.Msg>().apply { addAll(SunAI.load()) } }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val fb = rememberFeedback()

    fun send(q: String) {
        if (q.isBlank() || busy) return
        fb.tick()
        msgs += SunAI.Msg("user", q.trim())
        input = ""
        busy = true
        scope.launch {
            val reply = runCatching { SunAI.ask(msgs.dropLast(1), q.trim()) }
                .getOrElse { SunAI.Msg("error", it.message ?: "Não consegui falar com o OpenCode Go.") }
            msgs += reply
            if (reply.changes.isNotEmpty()) fb.toggle(true)
            SunAI.save(msgs)
            busy = false
        }
    }
    LaunchedEffect(msgs.size, busy) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size + (if (busy) 1 else 0)) }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f),
            state = list,
            contentPadding = PaddingValues(
                start = contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                end = contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = contentPadding.calculateTopPadding(), bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader("SunAI", subtitle = "Sua assistente de rotina", onSettings = onSettings, leading = { SunBiMark(size = 40.dp) })
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { setup = true }) {
                        Icon(Icons.Filled.MoreVert, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chave, modelo e conversa")
                    }
                }
            }
            if (msgs.isEmpty()) {
                item {
                    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Oi! Eu sou a SunAI.", style = MaterialTheme.typography.titleLargeEmphasized)
                            Text(
                                "Posso olhar como foi a sua semana ou o seu mês, dar ideias para hoje e ajustar a sua rotina. " +
                                    "Toque numa sugestão ou escreva do seu jeito.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            itemsIndexed(msgs) { i, m ->
                Bubble(m, onUndo = {
                    SunAI.undo(m)
                    msgs[i] = m.copy(undone = true)
                    SunAI.save(msgs)
                })
            }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LoadingIndicator(Modifier.size(44.dp))
                        Text("SunAI está pensando…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // sugestões e campo de mensagem, sempre embaixo
        Column(
            Modifier.fillMaxWidth().padding(
                start = contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                end = contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = contentPadding.calculateBottomPadding() - 80.dp + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedVisibility(!busy) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in SUGGESTIONS) AssistChip(onClick = { send(s) }, label = { Text(s) }, shape = MaterialTheme.shapes.large)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it }, placeholder = { Text("Pergunte ou peça um ajuste") },
                    modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.extraLarge, maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar")
                }
            }
        }
    }

    if (setup) {
        SunAISettingsSheet(
            onDismiss = { setup = false },
            onClear = { SunAI.clear(); msgs.clear(); setup = false },
            onDisconnect = { Prefs.aiConsent = false; Prefs.aiKey = ""; setup = false; ready = false },
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
private fun Bubble(m: SunAI.Msg, onUndo: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mine = m.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(if (mine) 0.85f else 1f),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val (bg, fg) = when (m.role) {
                "user" -> cs.primary to cs.onPrimary
                "error" -> cs.errorContainer to cs.onErrorContainer
                else -> cs.surfaceContainerHigh to cs.onSurface
            }
            AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 3 }) {
                Text(
                    richText(m.text),
                    Modifier.clip(MaterialTheme.shapes.extraLarge).background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge, color = fg,
                )
            }
            if (m.changes.isNotEmpty()) {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (m.undone) "Mudanças desfeitas" else "Mudanças na rotina", style = MaterialTheme.typography.titleMedium)
                        for (c in m.changes) {
                            val sign = when (c.kind) { "add" -> "＋"; "remove" -> "－"; else -> "↻" }
                            Text("$sign ${c.text} (${c.routine})", style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (m.undone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        }
                        if (!m.undone && m.undo != null) {
                            OutlinedButton(onClick = onUndo, shapes = ButtonDefaults.shapes()) { Text("Desfazer") }
                        }
                    }
                }
            }
        }
    }
}

/** Primeira vez: explica o que é enviado, pede a chave e o consentimento. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SunAISetup(contentPadding: PaddingValues, onReady: () -> Unit) {
    var key by remember { mutableStateOf(Prefs.aiKey) }
    var model by remember { mutableStateOf(Prefs.aiModel) }
    var agree by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("SunAI", subtitle = "Assistente com IA", leading = { SunBiMark(size = 40.dp) }) }
        item {
            SectionCard("Antes de começar") {
                Text(
                    "A SunAI usa o OpenCode Go, com a sua chave. A cada pergunta, o app envia a sua rotina, o seu plano " +
                        "e os registros dos últimos 31 dias (humor, sono, remédios marcados, sinais e anotações) para o " +
                        "OpenCode Go e para o modelo escolhido gerarem a resposta.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "A chave fica só neste aparelho. A conversa fica só aqui e pode ser apagada. A SunAI não substitui " +
                        "sua equipe de saúde e nunca muda remédios.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.clip(MaterialTheme.shapes.large).clickable { agree = !agree }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(agree, { agree = it })
                    Text("Entendi e quero usar a SunAI", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            SectionCard("Sua chave do OpenCode Go") {
                OutlinedTextField(
                    key, { key = it.trim() }, label = { Text("Chave da API") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    supportingText = { Text("No opencode.ai/console, assine o Go e copie a chave da API.") },
                )
                Label("Modelo")
                ModelPicker(model) { model = it }
                Button(
                    onClick = {
                        Prefs.aiKey = key
                        Prefs.aiModel = model
                        Prefs.aiConsent = true
                        onReady()
                    },
                    enabled = agree && key.length >= 8,
                    shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Ativar a SunAI", style = MaterialTheme.typography.titleMedium) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
    EditorSheet("SunAI", "Chave, modelo e conversa", onDismiss) {
        Label("Modelo")
        ModelPicker(model) { model = it; Prefs.aiModel = it }
        OutlinedButton(onClick = onClear, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Apagar a conversa") }
        TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Desligar a SunAI e apagar a chave") }
    }
}
