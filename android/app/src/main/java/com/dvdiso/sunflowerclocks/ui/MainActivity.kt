package com.dvdiso.sunflowerclocks.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dvdiso.sunflowerclocks.AlarmScheduler
import com.dvdiso.sunflowerclocks.MOODS
import com.dvdiso.sunflowerclocks.Prefs
import com.dvdiso.sunflowerclocks.Repo
import com.dvdiso.sunflowerclocks.RingService
import com.dvdiso.sunflowerclocks.SyncManager
import com.dvdiso.sunflowerclocks.Task
import com.dvdiso.sunflowerclocks.niceDay
import com.dvdiso.sunflowerclocks.today
import com.dvdiso.sunflowerclocks.weeklyReport
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        SyncManager.start(this)
        setContent { SunflowerTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        SyncManager.syncAllAsync()
    }
}

@Composable
private fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    // lê a versão para recompor sempre que o estado muda (local ou vindo de outro aparelho)
    val version by Repo.version.collectAsState()
    var now by remember { mutableStateOf(today()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = today()
        }
    }
    Column(Modifier.fillMaxSize().background(Sun.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SunflowerIcon(size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text("SUNFLOWER CLOCKS", color = Sun.sun, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
        }
        TabRow(selectedTabIndex = tab, containerColor = Sun.bg, contentColor = Sun.sun) {
            listOf("Hoje", "Semana", "Ajustes").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> TodayScreen(now, version)
            1 -> WeekScreen(now, version)
            else -> SettingsScreen()
        }
    }
}

// ── Hoje ────────────────────────────────────────────────────────────────────

@Composable
private fun TodayScreen(todayStr: String, v: Long) {
    var day by remember { mutableStateOf(todayStr) }
    val tasks = Repo.tasks()
    val done = tasks.count { Repo.done(day, it.id) }
    val isToday = day == todayStr

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp).border(2.dp, Sun.line).background(Sun.panel),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelButton("◀") { day = LocalDate.parse(day).minusDays(1).toString() }
                Text(
                    (if (isToday) "Hoje · " else "") + niceDay(day),
                    Modifier.weight(1f), color = Sun.ink, fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                PixelButton("▶", enabled = !isToday) { day = LocalDate.parse(day).plusDays(1).toString() }
            }
            if (!isToday) {
                OutlinedButton(onClick = { day = todayStr }, shape = RectangleShape) { Text("Voltar para hoje", color = Sun.ink) }
            }
            LinearProgressIndicator(
                progress = { if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size },
                modifier = Modifier.fillMaxWidth().height(14.dp).border(2.dp, Sun.line),
                color = Sun.sun, trackColor = Color(0xFF120F0C),
            )
            Text("$done de ${tasks.size} concluídas", color = Sun.dim, modifier = Modifier.padding(vertical = 6.dp))
        }
        items(tasks, key = { it.id }) { t -> TaskRow(day, t, v) }
        item { DayExtras(day, v) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun TaskRow(day: String, t: Task, v: Long) {
    val isDone = Repo.done(day, t.id)
    Row(
        Modifier.fillMaxWidth()
            .background(Sun.panel)
            .border(BorderStroke(1.dp, Sun.line))
            .clickable { Repo.setDone(day, t.id, !isDone) }
            .padding(12.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).border(3.dp, if (isDone) Sun.sun else Sun.off).background(if (isDone) Sun.sun else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { if (isDone) Text("✓", color = Sun.dark, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(14.dp))
        Text(t.time, color = Sun.sun, fontWeight = FontWeight.Bold, modifier = Modifier.width(62.dp))
        Text(
            t.label, Modifier.weight(1f),
            color = if (isDone) Sun.dim else Sun.ink,
            textDecoration = if (isDone) TextDecoration.LineThrough else null,
        )
        if (t.med) {
            Text("remédio", color = Sun.dark, fontSize = 12.sp, modifier = Modifier.background(Sun.leaf).padding(4.dp, 1.dp))
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun DayExtras(day: String, v: Long) {
    Column(Modifier.padding(top = 18.dp)) {
        SectionTitle("HUMOR DO DIA")
        val mood = Repo.mood(day)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..5) {
                val on = mood == i
                Button(
                    onClick = { Repo.set("$day/mood", if (on) null else i) },
                    shape = RectangleShape,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp, 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (on) Sun.sun else Color(0xFF2C241B),
                        contentColor = if (on) Sun.dark else Sun.ink,
                    ),
                ) { Text("$i", fontWeight = FontWeight.Bold) }
            }
        }
        Text(mood?.let { MOODS[it - 1] } ?: "1 muito baixo · 3 estável · 5 muito agitado", color = Sun.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(14.dp))
        var sleep by remember(day) { mutableStateOf(Repo.sleep(day)?.toString() ?: "") }
        OutlinedTextField(
            value = sleep,
            onValueChange = { v ->
                sleep = v.replace(',', '.')
                val n = sleep.toDoubleOrNull()
                if (sleep.isEmpty()) Repo.set("$day/sleep", null) else if (n != null && n in 0.0..24.0) Repo.set("$day/sleep", n)
            },
            label = { Text("Horas de sono (noite anterior)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        var note by remember(day) { mutableStateOf(Repo.note(day)) }
        LaunchedEffect(note) {
            delay(1200)
            if (note != Repo.note(day)) Repo.set("$day/note", note)
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Anotações (como me senti, apetite, gênero...)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Semana ──────────────────────────────────────────────────────────────────

@Composable
@Suppress("UNUSED_PARAMETER")
private fun WeekScreen(todayStr: String, v: Long) {
    val ctx = LocalContext.current
    val end = LocalDate.parse(todayStr)
    val days = (6 downTo 0).map { end.minusDays(it.toLong()).toString() }
    val wd = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
    val tasks = Repo.tasks()
    val labelW = 170.dp
    val cellW = 44.dp

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            SectionTitle("ÚLTIMOS 7 DIAS")
            Column(Modifier.border(2.dp, Sun.line).background(Sun.panel).horizontalScroll(rememberScrollState())) {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("Tarefa", Modifier.width(labelW).padding(start = 6.dp), color = Sun.dim)
                    for (d in days) {
                        val ld = LocalDate.parse(d)
                        Text("${wd[ld.dayOfWeek.value - 1]}\n${d.substring(8)}", Modifier.width(cellW), color = Sun.dim, fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                for (t in tasks) {
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${t.time} ${t.label}", Modifier.width(labelW).padding(start = 6.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Sun.ink)
                        for (d in days) {
                            Box(Modifier.width(cellW), contentAlignment = Alignment.Center) {
                                val y = Repo.done(d, t.id)
                                Box(Modifier.size(14.dp).border(2.dp, if (y) Sun.sun else Sun.off).background(if (y) Sun.sun else Color.Transparent))
                            }
                        }
                    }
                }
                WeekRow("Concluído", labelW, cellW, days) { if (Repo.hasDay(it)) "${Repo.percent(it)}%" else "–" }
                WeekRow("Humor", labelW, cellW, days) { Repo.mood(it)?.toString() ?: "–" }
                WeekRow("Sono (h)", labelW, cellW, days) { Repo.sleep(it)?.toString() ?: "–" }
            }
        }
        item {
            SectionTitle("RELATÓRIO PARA O PSICÓLOGO")
            val report = weeklyReport(todayStr)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                    ctx.startActivity(Intent.createChooser(send, "Enviar relatório"))
                }, shape = RectangleShape, colors = ButtonDefaults.buttonColors(containerColor = Sun.sun, contentColor = Sun.dark)) {
                    Text("Compartilhar")
                }
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Relatório", report))
                }, shape = RectangleShape) { Text("Copiar", color = Sun.ink) }
            }
            Text(report, color = Sun.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp).border(2.dp, Sun.line).background(Color(0xFF120F0C)).padding(10.dp))
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WeekRow(label: String, labelW: Dp, cellW: Dp, days: List<String>, value: (String) -> String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, Modifier.width(labelW).padding(start = 6.dp), color = Sun.sun, fontSize = 13.sp)
        for (d in days) Text(value(d), Modifier.width(cellW), color = Sun.sun, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ── Ajustes ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsScreen() {
    val ctx = LocalContext.current
    val peers by SyncManager.peers.collectAsState()
    var key by remember { mutableStateOf(Prefs.key) }
    var name by remember { mutableStateOf(Prefs.name) }
    var sync by remember { mutableStateOf(Prefs.syncEnabled) }
    var alarms by remember { mutableStateOf(Prefs.alarmsEnabled) }
    var manual by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            SectionTitle("SINCRONIZAÇÃO")
            SwitchRow("Sincronizar com outros aparelhos", sync) {
                sync = it
                Prefs.syncEnabled = it
                if (it) SyncManager.start(ctx) else SyncManager.stop(ctx)
            }
            OutlinedTextField(
                value = key, onValueChange = { key = it.uppercase() }, singleLine = true,
                label = { Text("Código de pareamento") }, modifier = Modifier.fillMaxWidth(),
            )
            Text("Use o mesmo código em todos os aparelhos. No PC: sunflowerd key", color = Sun.dim, fontSize = 13.sp)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Nome deste aparelho") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(onClick = {
                Prefs.key = key
                Prefs.name = name
                SyncManager.stop(ctx)
                SyncManager.start(ctx)
            }, shape = RectangleShape, modifier = Modifier.padding(vertical = 8.dp)) { Text("Salvar e reconectar") }

            Text("Aparelhos encontrados", color = Sun.sun, modifier = Modifier.padding(top = 8.dp))
            if (peers.isEmpty()) Text("Nenhum ainda. Eles precisam estar na mesma rede Wi-Fi.", color = Sun.dim, fontSize = 13.sp)
            for (p in peers.values.sortedBy { it.name }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (p.ok) { true -> "●"; false -> "×"; null -> "○" },
                        color = when (p.ok) { true -> Sun.leaf; false -> Color(0xFFD9733B); null -> Sun.dim },
                        modifier = Modifier.width(24.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Sun.ink)
                        Text(p.addr + if (p.ok == false) " · código errado ou fora do ar" else "", color = Sun.dim, fontSize = 12.sp)
                    }
                    if (p.device.startsWith("manual:")) {
                        Text("remover", color = Sun.dim, fontSize = 12.sp, modifier = Modifier.clickable {
                            Prefs.manualPeers = Prefs.manualPeers - p.addr
                            SyncManager.removePeer(p.device)
                        }.padding(6.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manual, onValueChange = { manual = it.trim() }, singleLine = true,
                    label = { Text("Adicionar ip:porta (ex. Tailscale)") }, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    if (manual.isNotEmpty()) {
                        val addr = if (':' in manual) manual else "$manual:${SyncManager.PORT}"
                        Prefs.manualPeers = Prefs.manualPeers + addr
                        SyncManager.addPeer("manual:$addr", addr, addr)
                        manual = ""
                    }
                }, shape = RectangleShape) { Text("+", color = Sun.ink) }
            }
            OutlinedButton(onClick = { SyncManager.syncAllAsync() }, shape = RectangleShape, modifier = Modifier.padding(top = 8.dp)) {
                Text("Sincronizar agora", color = Sun.ink)
            }
        }
        item {
            SectionTitle("ALARMES")
            SwitchRow("Tocar alarmes neste aparelho", alarms) {
                alarms = it
                Prefs.alarmsEnabled = it
                AlarmScheduler.scheduleAll(ctx)
            }
            PermissionHints()
            OutlinedButton(onClick = {
                val t = Repo.tasks().firstOrNull() ?: return@OutlinedButton
                ctx.startForegroundService(Intent(ctx, RingService::class.java).setAction(RingService.START).putExtra("task", t.id))
            }, shape = RectangleShape) { Text("Testar alarme", color = Sun.ink) }
        }
        item { TaskEditor() }
        item {
            Text("Aparelho: ${Prefs.device}", color = Sun.dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 24.dp))
        }
    }
}

@Composable
private fun PermissionHints() {
    val ctx = LocalContext.current
    val pkg = Uri.parse("package:${ctx.packageName}")
    if (!AlarmScheduler.canScheduleExact(ctx) && Build.VERSION.SDK_INT >= 31) {
        Hint("Alarmes exatos estão bloqueados.") {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
        }
    }
    if (Build.VERSION.SDK_INT >= 34) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.canUseFullScreenIntent()) {
            Hint("O alarme não pode abrir em tela cheia.") {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
            }
        }
    }
    if (Build.VERSION.SDK_INT >= 33) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) {
            Hint("As notificações estão desligadas.") {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        }
    }
}

@Composable
private fun Hint(text: String, fix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFF3A2F24)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Sun.sun2, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text("Resolver", color = Sun.sun, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { fix() }.padding(6.dp))
    }
}

@Composable
private fun TaskEditor() {
    val list = remember { mutableStateListOf<Task>().apply { addAll(Repo.tasks()) } }
    var saved by remember { mutableStateOf(false) }
    Column {
        SectionTitle("TAREFAS E HORÁRIOS")
        list.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = t.time, onValueChange = { list[i] = t.copy(time = it.take(5)); saved = false },
                    singleLine = true, modifier = Modifier.width(92.dp), label = { Text("hora") },
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = t.label, onValueChange = { list[i] = t.copy(label = it); saved = false },
                    singleLine = true, modifier = Modifier.weight(1f), label = { Text("tarefa") },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(checked = t.med, onCheckedChange = { list[i] = t.copy(med = it); saved = false })
                    Text("remédio", fontSize = 10.sp, color = Sun.dim)
                }
                Text("×", color = Sun.dim, fontSize = 22.sp, modifier = Modifier.clickable { list.removeAt(i); saved = false }.padding(8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = {
                list.add(Task("t" + System.currentTimeMillis().toString(36), "12:00", "Nova tarefa"))
                saved = false
            }, shape = RectangleShape) { Text("+ Tarefa", color = Sun.ink) }
            Button(onClick = {
                val valid = list.filter { AlarmScheduler.next(it.time) != null && it.label.isNotBlank() }
                Repo.setTasks(valid)
                saved = true
            }, shape = RectangleShape) { Text(if (saved) "Salvo ✓" else "Salvar tarefas") }
        }
        Text("Horas no formato HH:MM. As tarefas valem para todos os aparelhos.", color = Sun.dim, fontSize = 12.sp)
    }
}

// ── peças ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Sun.sun, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Sun.ink)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PixelButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (enabled) Sun.sun else Sun.off,
        fontSize = 20.sp,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(16.dp, 12.dp),
    )
}

/** Girassol em pixel art 16×16 (o mesmo do ícone). */
@Composable
fun SunflowerIcon(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val petal = Sun.sun
    val rects = listOf(
        Triple(listOf(6, 0, 4, 2), petal, 0), Triple(listOf(6, 10, 4, 2), petal, 0),
        Triple(listOf(1, 4, 2, 4), petal, 0), Triple(listOf(13, 4, 2, 4), petal, 0),
        Triple(listOf(2, 1, 3, 3), petal, 0), Triple(listOf(11, 1, 3, 3), petal, 0),
        Triple(listOf(2, 8, 3, 3), petal, 0), Triple(listOf(11, 8, 3, 3), petal, 0),
        Triple(listOf(4, 2, 8, 8), Sun.sun2, 0), Triple(listOf(5, 3, 6, 6), Color(0xFF6B3F1D), 0),
        Triple(listOf(6, 4, 2, 2), Color(0xFF8A5528), 0),
        Triple(listOf(7, 12, 2, 4), Sun.leaf, 0), Triple(listOf(4, 12, 3, 2), Sun.leaf, 0), Triple(listOf(9, 13, 3, 2), Sun.leaf, 0),
    )
    Canvas(modifier.size(size)) {
        val u = this.size.width / 16f
        for ((r, c, _) in rects) {
            drawRect(c, Offset(r[0] * u, r[1] * u), Size(r[2] * u, r[3] * u))
        }
    }
}
