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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.dvdiso.sunbi.isQuest

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(contentPadding: PaddingValues, onBack: () -> Unit, onRoutine: () -> Unit, onSunAI: () -> Unit) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) } // "routine" | "sync"
    val peers by SyncManager.peers.collectAsState()
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Ajustes", leading = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            })
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PermissionHints() } }
        item { GeneralCard(Modifier.enterFromBelow(0)) }
        item {
            Column(Modifier.enterFromBelow(1), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val n = if (isQuest()) 4 else 5
                NavRow("Rotina e horários", "Semana e folga · ${Repo.tasks().size} e ${Repo.offTasks().size} tarefas", 0, n) { Feedback.tap(); onRoutine() }
                NavRow("SunAI", if (Prefs.aiKey.isBlank()) "Assistente com IA · configurar a chave" else "Assistente com IA · ${Prefs.aiModel}", 1, n) { Feedback.tap(); onSunAI() }
                NavRow(
                    "Sincronização",
                    "Código ${Prefs.key} · " + when (peers.size) { 0 -> "nenhum aparelho"; 1 -> "1 aparelho"; else -> "${peers.size} aparelhos" },
                    2, n,
                ) { Feedback.tap(); sheet = "sync" }
                NavRow(
                    "Modo complexo",
                    if (Prefs.webConfig) "Ligado · configure tudo pelo navegador do computador" else "Configuração completa pelo computador",
                    3, n,
                ) { Feedback.tap(); sheet = "web" }
                if (!isQuest()) {
                    val ctx = LocalContext.current
                    NavRow(
                        "Tela inicial (launcher)",
                        if (Launcher.isEnabled(ctx)) "Ligado · a rotina do dia e seus apps na tela inicial" else "Usar o SunBi como tela inicial do telefone",
                        4, n,
                    ) { Feedback.tap(); sheet = "launcher" }
                }
            }
        }
        item {
            Text(
                "SunBi ${BuildConfig.VERSION_NAME}\nO SunBi organiza e observa; não diagnostica nem substitui seu tratamento.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    when (sheet) {
        "sync" -> EditorSheet(
            "Sincronização",
            "Seus aparelhos se encontram na rede de casa e trocam os dados direto entre si, sem servidor.",
            { sheet = null },
        ) { SyncCard() }
        "web" -> EditorSheet(
            "Modo complexo",
            "Uma página servida por este aparelho para configurar o SunBi inteiro com calma, no computador.",
            { sheet = null },
        ) { WebConfigCard() }
        "launcher" -> EditorSheet(
            "Tela inicial",
            "O SunBi vira a tela inicial do telefone: relógio, a tarefa da vez, o check-in e seus apps em texto, sem ícones gritando.",
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
    var hidden by remember { mutableStateOf(Launcher.hidden()) }
    val apps = remember { Launcher.apps(ctx).associateBy { it.key } }
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow("Usar o SunBi como tela inicial", "Deslize para cima para ver todos os apps; segure um app para favoritar ou esconder", on) {
            Feedback.tick()
            Launcher.setEnabled(ctx, it)
            on = it
        }
        androidx.compose.animation.AnimatedVisibility(on) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (Launcher.isDefault(ctx)) {
                    Text("O SunBi é a tela inicial padrão. Para trocar, use o botão abaixo ou Ajustes do Android → Apps padrão.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                } else {
                    Text("Falta escolher o SunBi como padrão: o Android pergunta ao apertar o botão.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Button(onClick = {
                    Feedback.tap()
                    Launcher.requestDefaultIntent(ctx)?.let { runCatching { ctx.startActivity(it) } }
                }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (Launcher.isDefault(ctx)) "Trocar a tela inicial padrão" else "Definir como tela inicial padrão")
                }
                if (hidden.isNotEmpty()) {
                    Label("Apps escondidos")
                    for (k in hidden.sorted()) {
                        val app = apps[k]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app?.label ?: k.substringBefore('#'), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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

/** Liga a página de configuração e mostra o endereço e o PIN. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WebConfigCard() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Prefs.webConfig) }
    var pin by remember { mutableStateOf(Prefs.webPin) }
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow("Ligar o modo complexo", "Rotinas, sinais, planos, pessoas, registros, ajustes e backup", on) {
            Feedback.tick()
            WebConfig.enable(it)
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
                    "Os dois precisam estar na mesma rede. A página só funciona com o modo ligado; cinco PINs errados desligam. Desligue quando terminar.",
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GeneralCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var dyn by remember { mutableStateOf(Prefs.dynamicColor) }
    var ui by remember { mutableStateOf(Prefs.uiSounds) }
    var alarms by remember { mutableStateOf(Prefs.alarmsEnabled) }
    var remind by remember { mutableStateOf(Prefs.checkinReminder) }
    var tone by remember { mutableStateOf(Prefs.alarmSound) }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.release() } }
    SectionCard("Geral", modifier) {
        SwitchRow("Cores do sistema", "Acompanha o papel de parede (Material You)", dyn) {
            dyn = it
            Prefs.dynamicColor = it
            ThemeState.load()
        }
        SwitchRow("Sons e vibração", "Cada ação tem um toque próprio: marcar, fechar o dia, conquistas", ui) {
            ui = it
            Prefs.uiSounds = it
            if (it) Feedback.toggle(true)
        }
        SwitchRow("Lembrete de check-in às 21h", "Só se o dia ainda não tiver registro", remind) {
            remind = it
            Prefs.checkinReminder = it
            Reminders.schedule(ctx)
        }
        SwitchRow("Alarmes neste aparelho", if (isQuest()) "No Quest só toca com o headset ligado" else "Sem soneca: só Feito ou Parar", alarms) {
            alarms = it
            Prefs.alarmsEnabled = it
            AlarmScheduler.scheduleAll(ctx)
        }
        if (alarms) {
            Label("Os alarmes tocam em")
            val mine = Repo.alarmDevice() == Prefs.device
            ChoiceGroup(listOf(false to "Todos os aparelhos", true to "Só neste"), mine, { v ->
                if (v == null) return@ChoiceGroup
                Repo.set("alarm_device", if (v) Prefs.device else null)
            })
            Repo.alarmDevice()?.takeIf { it != Prefs.device }?.let {
                Text("Hoje quem toca é o aparelho $it. Escolha \"Só neste\" para trazer os alarmes para cá.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Label("Toque do alarme")
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
                val t = Repo.tasks().firstOrNull() ?: return@TextButton
                ctx.startForegroundService(Intent(ctx, RingService::class.java).setAction(RingService.START).putExtra("task", t.id))
            }) { Text("Testar o alarme agora") }
        }
    }
}

@Composable
private fun PermissionHints() {
    val ctx = LocalContext.current
    val pkg = Uri.parse("package:${ctx.packageName}")
    val nm = ctx.getSystemService(NotificationManager::class.java)
    if (!AlarmScheduler.canScheduleExact(ctx) && Build.VERSION.SDK_INT >= 31) {
        Hint("Alarmes exatos estão bloqueados.") { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)) }
    }
    if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
        Hint("O alarme não pode abrir em tela cheia.") { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)) }
    }
    if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) {
        Hint("As notificações estão desligadas.") {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    }
}

@Composable
private fun Hint(text: String, fix: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = { runCatching(fix) }, modifier = Modifier.align(Alignment.End)) {
                Text("Resolver", fontWeight = FontWeight.Bold, color = cs.onErrorContainer)
            }
        }
    }
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
            supportingText = { Text("O mesmo em todos os aparelhos. No PC, ele aparece no painel do plugin do Noctalia.") },
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

        SyncManager.localIp(ctx)?.let { ip ->
            Text(
                "Este aparelho: $ip:${SyncManager.PORT}. Se o PC não achar sozinho, coloque esse endereço em Aparelhos manuais no plugin do Noctalia.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
        }
        Label("Aparelhos encontrados")
        if (peers.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sync) {
                    LoadingIndicator(Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("Procurando na mesma rede Wi-Fi...", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
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
