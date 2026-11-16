package com.dvdiso.sunbi.ui

import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.net.ConnectivityManager
import java.net.Inet4Address
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.AlarmScheduler
import com.dvdiso.sunbi.BuildConfig
import com.dvdiso.sunbi.Launcher
import com.dvdiso.sunbi.Prefs
import com.dvdiso.sunbi.Reminders
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.RingService
import com.dvdiso.sunbi.Sfx
import com.dvdiso.sunbi.SyncManager
import com.dvdiso.sunbi.WebConfig

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(contentPadding: PaddingValues, onBack: () -> Unit, onRoutine: () -> Unit, onAssistant: () -> Unit, onAiEnabled: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val peers by SyncManager.peers.collectAsState()
    // recalcula as permissões ao voltar dos ajustes do Android
    var resumed by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val fixes = remember(resumed) { permissionFixes(ctx) }

    data class Item(val key: String, val title: String, val sub: String, val go: () -> Unit)
    val items = buildList {
        add(Item("look", "Aparência e sons", if (Prefs.uiSounds) "Sons e vibração ligados" else "Sons e vibração desligados") { sheet = "look" })
        add(Item("alarms", "Alarmes e lembretes", if (Prefs.alarmsEnabled) "Alarmes ligados neste aparelho" else "Alarmes desligados neste aparelho") { sheet = "alarms" })
        add(Item("routine", "Rotina e horários", "Semana ${Repo.tasks().size} · folga ${Repo.offTasks().size} tarefas") { onRoutine() })
        add(Item("ai", "Assistente de IA", when {
            !Prefs.aiEnabled -> "Desligada"
            !com.dvdiso.sunbi.Assistant.ready() -> "Ligada · falta configurar"
            else -> "${com.dvdiso.sunbi.Assistant.name()} · ${com.dvdiso.sunbi.Assistant.provider().name.substringBefore(" (")}"
        }) { sheet = "ai" })
        add(Item("sync", "Sincronização", when (peers.size) { 0 -> "Nenhum aparelho"; 1 -> "1 aparelho"; else -> "${peers.size} aparelhos" }) { sheet = "sync" })
        add(Item("web", "Modo avançado", if (Prefs.webConfig) "Ligado" else "Configurar pelo computador") { sheet = "web" })
        add(Item("launcher", "Tela inicial", if (Launcher.isEnabled(ctx)) "Ligada" else "Usar o SunBi como tela inicial") { sheet = "launcher" })
    }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Ajustes", leading = {
                IconButton(onClick = { Feedback.tap(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            })
        }
        if (fixes.isNotEmpty()) {
            item {
                val cs = MaterialTheme.colorScheme
                Row(
                    Modifier.fillMaxWidth().enterFromBelow(0).clip(MaterialTheme.shapes.large).background(cs.errorContainer)
                        .bouncyClick(label = "Resolver permissões") { Feedback.tap(); sheet = "perms" }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(20.dp), tint = cs.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(if (fixes.size == 1) "1 permissão pendente" else "${fixes.size} permissões pendentes",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = cs.onErrorContainer)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onErrorContainer)
                }
            }
        }
        item {
            Column(Modifier.enterFromBelow(1), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEachIndexed { i, it -> NavRow(it.title, it.sub, i, items.size) { Feedback.tap(); it.go() } }
            }
        }
        item {
            Text(
                "SunBi ${BuildConfig.VERSION_NAME} · organiza e observa; não diagnostica nem substitui seu tratamento.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    when (sheet) {
        "perms" -> EditorSheet("Permissões", "Sem elas os alarmes podem não tocar na hora ou não aparecer.", { sheet = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                fixes.forEachIndexed { i, (title, fix) -> NavRow(title, "Toque para resolver", i, fixes.size) { Feedback.tap(); runCatching(fix) } }
            }
        }
        "ai" -> EditorSheet(
            "Assistente de IA",
            "Opcional. O app funciona inteiro sem ela.",
            { sheet = null },
        ) { AiCard(onAiEnabled) { sheet = null; onAssistant() } }
        "look" -> EditorSheet("Aparência e sons", onDismiss = { sheet = null }) { LookCard() }
        "alarms" -> EditorSheet("Alarmes e lembretes", onDismiss = { sheet = null }) { AlarmsCard() }
        "sync" -> EditorSheet(
            "Sincronização",
            "Seus aparelhos trocam os dados direto entre si pela rede de casa, sem servidor e sem conta.",
            { sheet = null },
        ) { SyncCard() }
        "web" -> EditorSheet(
            "Modo avançado",
            "Uma página servida por este aparelho para configurar o SunBi inteiro com calma, no computador.",
            { sheet = null },
        ) { WebConfigCard() }
        "launcher" -> EditorSheet(
            "Tela inicial",
            "O SunBi vira a tela inicial do telefone: relógio, a tarefa da vez e seus apps em texto, sem ícones gritando.",
            { sheet = null },
        ) { LauncherCard() }
    }
}

/** Liga o modo launcher, pede para ser a tela inicial padrão e mostra os apps escondidos. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LauncherCard() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Launcher.isEnabled(ctx)) }
    var isDefault by remember { mutableStateOf(Launcher.isDefault(ctx)) }
    var hidden by remember { mutableStateOf(Launcher.hidden()) }
    val apps = remember { Launcher.apps(ctx).associateBy { it.key } }
    val cs = MaterialTheme.colorScheme
    // o diálogo do Android responde aqui; se não virou padrão (ou nem apareceu), abre "App de início"
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) {
        isDefault = Launcher.isDefault(ctx)
        if (!isDefault) Launcher.openHomeSettings(ctx)
    }
    // ao voltar dos ajustes do Android, atualiza o estado
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) isDefault = Launcher.isDefault(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow("Usar o SunBi como tela inicial", null, on) {
            Feedback.tick()
            Launcher.setEnabled(ctx, it)
            on = it
        }
        androidx.compose.animation.AnimatedVisibility(on) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isDefault) {
                    Text("Pronto: o SunBi é a sua tela inicial.", style = MaterialTheme.typography.bodyLarge, color = cs.primary, fontWeight = FontWeight.SemiBold)
                } else {
                    Button(onClick = {
                        Feedback.tap()
                        val req = Launcher.roleRequestIntent(ctx)
                        val asked = req != null && runCatching { ask.launch(req); true }.getOrDefault(false)
                        if (!asked) Launcher.openHomeSettings(ctx)
                    }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Definir como tela inicial")
                    }
                    Text("Se aparecer a lista de apps de início, escolha SunBi.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Text("Deslize para cima para ver todos os apps. Segure um app para favoritar, renomear ou ocultar.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (isDefault) {
                    TextButton(onClick = { Feedback.tap(); Launcher.openHomeSettings(ctx) }) { Text("Trocar a tela inicial") }
                }
                if (hidden.isNotEmpty()) {
                    Label("Apps ocultos")
                    for (k in hidden.sortedBy { apps[it]?.label ?: it }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(apps[k]?.label ?: k.substringBefore('/'), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = {
                                Feedback.tick()
                                Prefs.launcherHidden = Prefs.launcherHidden - k
                                hidden = Launcher.hidden()
                            }) { Text("Mostrar") }
                        }
                    }
                }
            }
        }
    }
}

/** Liga ou desliga a IA no app inteiro. Desligada, a aba some e nenhum dado é enviado. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiCard(onChanged: (Boolean) -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Prefs.aiEnabled) }
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow("Usar a assistente de IA", "Conversa, resumo da semana e ajustes na rotina", on) {
            Feedback.tick()
            on = it
            Prefs.aiEnabled = it
            Reminders.schedule(ctx) // o resumo semanal depende dela
            onChanged(it)
        }
        Text(
            if (on) "Ela só recebe os seus registros depois que você escolher um provedor, colocar a sua chave e aceitar. Nunca mexe em remédios."
            else "Com a IA desligada, a aba da assistente não aparece e nada do que você registra é enviado a serviços de IA. A chave e a conversa guardadas continuam no aparelho, para o caso de você ligar de novo.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        )
        androidx.compose.animation.AnimatedVisibility(on) {
            Button(onClick = { Feedback.tap(); onOpen() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (com.dvdiso.sunbi.Assistant.ready()) "Abrir a conversa" else "Configurar")
            }
        }
    }
}

/** Liga a página de configuração e mostra o endereço e o PIN. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WebConfigCard() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Prefs.webConfig) }
    var pin by remember { mutableStateOf(Prefs.webPin) }
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow("Ligar o modo avançado", "Rotinas, sinais, planos, pessoas, registros e backup", on) {
            Feedback.tick()
            WebConfig.enable(ctx, it)
            on = it
            pin = Prefs.webPin
            if (it) SyncManager.start(ctx)
        }
        androidx.compose.animation.AnimatedVisibility(on) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val ip = SyncManager.localIp(ctx)
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Abra no computador", style = MaterialTheme.typography.labelLarge)
                        Text(if (ip != null) "http://$ip:${SyncManager.PORT}/" else "Conecte este aparelho ao Wi-Fi", style = MaterialTheme.typography.titleLargeEmphasized)
                        Text("PIN", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        Text(pin.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmallEmphasized, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Com a extensão do SunBi no navegador, o botão \"Abrir o modo avançado\" (nos ajustes dela) já abre o endereço certo. Sem a extensão, digite o endereço inteiro, com http:// no começo (com https:// não abre). Desliga sozinho depois de 1 hora sem uso.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
                TextButton(onClick = { Feedback.tick(); Prefs.webPin = WebConfig.newPin(); pin = Prefs.webPin }) { Text("Gerar outro PIN") }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Tudo o que é liga/desliga num cartão só. */
@Composable
private fun LookCard() {
    var dyn by remember { mutableStateOf(Prefs.dynamicColor) }
    var ui by remember { mutableStateOf(Prefs.uiSounds) }
    var labels by remember { mutableStateOf(Prefs.navLabels) }
    SwitchRow("Cores do sistema", "Acompanha o papel de parede (Material You)", dyn) {
        Feedback.tick()
        dyn = it
        Prefs.dynamicColor = it
        ThemeState.load()
    }
    SwitchRow("Títulos das abas", "O nome embaixo de cada ícone, na barra de baixo", labels) {
        Feedback.tick()
        labels = it
        Prefs.navLabels = it
        ThemeState.load()
    }
    SwitchRow("Sons e vibração", "Cada ação tem um toque próprio", ui) {
        ui = it
        Prefs.uiSounds = it
        if (it) Feedback.toggle(true)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlarmsCard() {
    val ctx = LocalContext.current
    var alarms by remember { mutableStateOf(Prefs.alarmsEnabled) }
    var remind by remember { mutableStateOf(Prefs.checkinReminder) }
    var tone by remember { mutableStateOf(Prefs.alarmSound) }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.release() } }
    SwitchRow("Alarmes neste aparelho", "Sem soneca: só Concluído ou Parar", alarms) {
        Feedback.tick()
        alarms = it
        Prefs.alarmsEnabled = it
        AlarmScheduler.scheduleAll(ctx)
    }
    androidx.compose.animation.AnimatedVisibility(alarms) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("Os alarmes tocam em")
            ChoiceGroup(listOf(false to "Todos os aparelhos", true to "Só neste"), Repo.alarmDevice() == Prefs.device, { v ->
                if (v == null) return@ChoiceGroup
                Repo.set("alarm_device", if (v) Prefs.device else null)
            })
            Label("Toque")
            ChoiceGroup(listOf("amanhecer" to "Amanhecer", "girassol" to "Girassol"), tone, { v ->
                if (v == null) return@ChoiceGroup
                tone = v
                Prefs.alarmSound = v
                preview?.release()
                preview = MediaPlayer.create(ctx, RingService.soundRes())?.apply {
                    setOnCompletionListener { it.release(); if (preview === it) preview = null }
                    start()
                }
            })
            TextButton(onClick = {
                Feedback.tap()
                val t = Repo.tasks().firstOrNull() ?: return@TextButton
                ctx.startForegroundService(Intent(ctx, RingService::class.java).setAction(RingService.START).putExtra("task", t.id))
            }) { Text("Testar o alarme agora") }
        }
    }
    SwitchRow("Lembrete de check-in às 21h", "Só se o dia ainda não tiver registro", remind) {
        Feedback.tick()
        remind = it
        Prefs.checkinReminder = it
        Reminders.schedule(ctx)
    }
}

/** Permissões que faltam, cada uma com o atalho para resolver. */
private fun permissionFixes(ctx: Context): List<Pair<String, () -> Unit>> {
    val pkg = Uri.parse("package:${ctx.packageName}")
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val out = mutableListOf<Pair<String, () -> Unit>>()
    if (!AlarmScheduler.canScheduleExact(ctx) && Build.VERSION.SDK_INT >= 31) {
        out += "Alarmes exatos estão bloqueados" to { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)) }
    }
    if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
        out += "O alarme não pode abrir em tela cheia" to { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)) }
    }
    if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) {
        out += "As notificações estão desligadas" to {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    }
    return out
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncCard() {
    val ctx = LocalContext.current
    val peers by SyncManager.peers.collectAsState()
    var key by remember { mutableStateOf(Prefs.key) }
    var name by remember { mutableStateOf(Prefs.name) }
    var sync by remember { mutableStateOf(Prefs.syncEnabled) }
    var manual by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow("Sincronizar com outros aparelhos", null, sync) {
            sync = it
            Prefs.syncEnabled = it
            if (it) SyncManager.start(ctx) else SyncManager.stop(ctx)
        }
        OutlinedTextField(
            key, { key = it.uppercase() }, singleLine = true, label = { Text("Código de pareamento") },
            supportingText = { Text("O mesmo em todos os aparelhos.") },
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        )
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nome deste aparelho") },
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
        Button(onClick = {
            Prefs.key = key
            Prefs.name = name
            SyncManager.stop(ctx)
            SyncManager.start(ctx)
        }, shapes = ButtonDefaults.shapes()) { Text("Salvar e reconectar") }

        // o computador entra pela extensão do navegador: ela só precisa destes dois dados
        Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val ip = SyncManager.localIp(ctx)
                Text("Conectar o computador", style = MaterialTheme.typography.titleMediumEmphasized)
                Text("Instale a extensão do SunBi no navegador e digite nela:", style = MaterialTheme.typography.bodyMedium)
                Text("Endereço", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Text(if (ip != null) "$ip:${SyncManager.PORT}" else "Conecte este aparelho ao Wi-Fi", style = MaterialTheme.typography.titleLargeEmphasized)
                Text("Código", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Text(Prefs.key, style = MaterialTheme.typography.titleLargeEmphasized, fontWeight = FontWeight.Bold)
                Text("A extensão avisa de cada tarefa no computador e reencontra o celular sozinha se o endereço mudar.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Label("Aparelhos encontrados")
        if (peers.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sync) {
                    LoadingIndicator(Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (sync) "Procurando na mesma rede Wi-Fi..." else "Sincronização desligada.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        for (p in peers.values.sortedBy { it.name }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (p.ok) { true -> "●"; false -> "×"; null -> "○" },
                    color = when (p.ok) { true -> cs.tertiary; false -> cs.error; null -> cs.onSurfaceVariant },
                    modifier = Modifier.width(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (p.client) "conectado · ${agoLabel(p.seen)}" else p.addr + if (p.ok == false) " · código errado ou fora do ar" else "",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
                if (p.device.startsWith("manual:")) {
                    TextButton(onClick = {
                        Prefs.manualPeers = Prefs.manualPeers - p.addr
                        SyncManager.removePeer(p.device)
                    }) { Text("Remover") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                manual, { manual = it.trim() }, singleLine = true, label = { Text("Adicionar ip:porta (ex. Tailscale)") },
                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (manual.isNotEmpty()) {
                    val addr = if (':' in manual) manual else "$manual:${SyncManager.PORT}"
                    Prefs.manualPeers = Prefs.manualPeers + addr
                    SyncManager.addPeer("manual:$addr", addr, addr)
                    manual = ""
                }
            }) { Icon(Icons.Filled.Add, "Adicionar par") }
        }
        OutlinedButton(onClick = { SyncManager.syncAllAsync() }, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Sincronizar agora")
        }
    }
}

private fun agoLabel(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "agora"
        m < 60 -> "há $m min"
        else -> "há ${m / 60} h"
    }
}
