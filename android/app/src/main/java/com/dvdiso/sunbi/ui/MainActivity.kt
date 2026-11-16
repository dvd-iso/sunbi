package com.dvdiso.sunbi.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Focus
import com.dvdiso.sunbi.Prefs
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.SyncManager
import com.dvdiso.sunbi.Task
import com.dvdiso.sunbi.today
import kotlinx.coroutines.delay

/** Pedidos que chegam pela intent: abrir o check-in (lembrete), uma aba (resumo semanal) ou a explicação do Health Connect. */
data class Launch(val checkin: Boolean = false, val tab: Int? = null, val healthRationale: Boolean = false, val n: Long = 0)

class MainActivity : ComponentActivity() {
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val launch = mutableStateOf(Launch())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        SyncManager.start(this)
        ThemeState.load()
        // só na primeira criação: ao girar a tela, o pedido da notificação não se repete
        if (savedInstanceState == null) read(intent)
        setContent { SunBiTheme { Root(launch.value) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(i: android.content.Intent?) {
        if (i == null) return
        val rationale = i.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" || i.action == android.content.Intent.ACTION_VIEW_PERMISSION_USAGE
        launch.value = Launch(i.getBooleanExtra("checkin", false), i.getIntExtra("tab", -1).takeIf { it >= 0 }, rationale, System.currentTimeMillis())
    }

    override fun onResume() {
        super.onResume()
        Focus.restore()
        // rede de segurança: abrir o app sempre confere os alarmes dos próximos dias
        com.dvdiso.sunbi.AlarmScheduler.scheduleAll(this)
        SyncManager.syncAllAsync()
    }
}

private data class Dest(val label: String, val icon: ImageVector)

// telas que não são abas: abertas por um botão, "voltar" retorna à aba anterior
private const val SETTINGS = 10
private const val ROUTINE = 11
private const val TAB_PLAN = 3
private const val TAB_AI = 2

private val DESTS = listOf(
    Dest("Hoje", Icons.Filled.Home),
    Dest("Histórico", Icons.Filled.DateRange),
    Dest("Assistente", Icons.Filled.Star),
    Dest("Plano", Icons.Filled.Favorite),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Root(launch: Launch = Launch()) {
    // primeira vez: tour que monta a rotina (ou código de pareamento)
    var onboarded by remember { mutableStateOf(Prefs.onboarded) }
    var aiKickoff by remember { mutableStateOf<String?>(null) }
    if (!onboarded) {
        // o tour fica fora do Scaffold: sem esta superfície, os textos sem cor própria (títulos)
        // saem em preto e somem no tema escuro
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            OnboardingScreen { ai -> aiKickoff = ai; onboarded = true }
        }
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // a assistente de IA é opcional: desligada (no tour ou em Ajustes), a aba não existe
    var aiOn by remember { mutableStateOf(Prefs.aiEnabled) }
    val dests = DESTS.withIndex().filter { it.index != TAB_AI || aiOn }
    LaunchedEffect(aiKickoff) { if (aiKickoff != null && aiOn) tab = TAB_AI }
    LaunchedEffect(aiOn) { if (!aiOn && tab == TAB_AI) tab = 0 }
    var healthRationale by remember { mutableStateOf(false) }
    if (healthRationale) HealthRationaleSheet { healthRationale = false }
    var now by remember { mutableStateOf(today()) }
    // gamificação: lida a cada mudança nos registros (a leitura observa o Repo)
    val stats = com.dvdiso.sunbi.Game.stats(now)
    var achievements by rememberSaveable { mutableStateOf(false) }
    if (achievements) AchievementsSheet(stats) { achievements = false }
    var tick by remember { mutableLongStateOf(0L) }
    var focusTask by remember { mutableStateOf<Task?>(null) }
    var focusOpen by remember { mutableStateOf(false) }
    var crisisRequest by remember { mutableStateOf(false) }
    // Ajustes é uma tela à parte (índice 3), aberta pela engrenagem; voltar retorna à aba anterior
    var prevTab by rememberSaveable { mutableIntStateOf(0) }
    var checkin by remember { mutableStateOf<Pair<String, Int>?>(null) }
    // comemoração só com a tela livre: nunca por cima do check-in, do foco ou das conquistas
    Celebrations(stats, paused = checkin != null || focusOpen || achievements)
    // para onde "voltar" leva a partir da Rotina (uma aba ou Ajustes)
    var routineBack by rememberSaveable { mutableIntStateOf(0) }
    val openSettings = { if (tab < SETTINGS) prevTab = tab; tab = SETTINGS }
    val openRoutine = { if (tab < SETTINGS) prevTab = tab; routineBack = tab; tab = ROUTINE }
    BackHandler(enabled = tab >= SETTINGS) { tab = if (tab == ROUTINE) routineBack else prevTab }
    val focusEnd by Focus.end.collectAsState()
    val fb = rememberFeedback()
    // pedidos da intent (notificações e Health Connect)
    LaunchedEffect(launch.n) {
        if (launch.n == 0L) return@LaunchedEffect
        if (launch.checkin) checkin = today() to 0
        launch.tab?.let { if (it in 0..TAB_PLAN && (it != TAB_AI || aiOn)) tab = it }
        if (launch.healthRationale) healthRationale = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = today()
            tick++
        }
    }

    val openFocus: (Task?) -> Unit = { t ->
        focusTask = t ?: Repo.tasksFor(today()).firstOrNull { it.id == Prefs.focusTask }
        focusOpen = true
    }

    val ld = androidx.compose.ui.platform.LocalLayoutDirection.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Tablets e celular deitado: trilho lateral e conteúdo centralizado.
        val wide = maxWidth >= 600.dp
        Scaffold(
            bottomBar = {
                if (!wide) {
                    // a barra desenha o próprio fundo até a borda; os itens ficam acima da barra de
                    // gestos do sistema, com uma folga para as duas não encostarem
                    val labels = ThemeState.navLabels.value
                    val barColor = MaterialTheme.colorScheme.surfaceContainer
                    Surface(color = barColor) {
                        Column(Modifier.navigationBarsPadding()) {
                            ShortNavigationBar(containerColor = barColor, windowInsets = WindowInsets(0, 0, 0, 0)) {
                                dests.forEach { (i, d) ->
                                    ShortNavigationBarItem(
                                        selected = tab == i, onClick = { if (tab != i) fb.tick(); tab = i },
                                        // sem o título, o nome da aba vira a descrição do ícone (leitor de tela)
                                        icon = { Icon(d.icon, if (labels) null else d.label) },
                                        label = if (labels) { { Text(d.label, maxLines = 1, overflow = TextOverflow.Ellipsis) } } else null,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            },
            floatingActionButton = {
                // na conversa, o botão cobriria o de enviar
                if (focusEnd != null && !focusOpen && tab != TAB_AI) {
                    SmallExtendedFloatingActionButton(
                        onClick = { openFocus(null) },
                        icon = { Icon(Icons.Filled.PlayArrow, null) },
                        text = { FocusRemaining(focusEnd!!) },
                    )
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { inner ->
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    // o Scaffold já entrega as margens do sistema em [inner]; o trilho não soma as dele
                    NavigationRail(
                        Modifier.padding(start = inner.calculateStartPadding(ld), top = inner.calculateTopPadding()),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        Spacer(Modifier.weight(1f))
                        dests.forEach { (i, d) ->
                            NavigationRailItem(
                                selected = tab == i, onClick = { if (tab != i) fb.tick(); tab = i },
                                icon = { Icon(d.icon, null) }, label = { Text(d.label) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    // celular deitado: a barra do sistema ou o recorte da câmera ficam dos lados
                    val sideStart = if (wide) 0.dp else inner.calculateStartPadding(ld)
                    val sideEnd = inner.calculateEndPadding(ld)
                    val pad = PaddingValues(
                        start = 16.dp + sideStart, end = 16.dp + sideEnd,
                        top = inner.calculateTopPadding() + 8.dp,
                        bottom = inner.calculateBottomPadding() + 88.dp,
                    )
                    // a conversa da assistente cuida das próprias margens e do teclado
                    val chatPad = PaddingValues(start = sideStart, end = sideEnd, top = inner.calculateTopPadding() + 4.dp, bottom = inner.calculateBottomPadding())
                    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                    AnimatedContent(
                        targetState = tab,
                        // desliza na direção da aba escolhida, com a mola do Material Expressive
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(slide) { it / 5 * dir } + fadeIn(enter)) togetherWith
                                (slideOutHorizontally(slide) { -it / 5 * dir } + fadeOut(exit))
                        },
                        modifier = Modifier.widthIn(max = 720.dp),
                        label = "tab",
                    ) { t ->
                        when (t) {
                            0 -> TodayScreen(
                                now, tick, pad, stats, onFocus = openFocus, onOpenPlan = { tab = TAB_PLAN }, onSettings = openSettings,
                                onCheckin = { d, step -> checkin = d to step }, onEditRoutine = openRoutine,
                                onAchievements = { achievements = true },
                            )
                            1 -> HistoryScreen(now, pad, onSettings = openSettings)
                            TAB_AI -> AssistantScreen(chatPad, initial = aiKickoff, onInitialSent = { aiKickoff = null },
                                onDisable = { Prefs.aiEnabled = false; aiOn = false })
                            TAB_PLAN -> PlanScreen(pad, crisisRequest, { crisisRequest = false }, onSettings = openSettings)
                            ROUTINE -> RoutineScreen(pad) { tab = routineBack }
                            else -> SettingsScreen(pad, onBack = { tab = prevTab },
                                onRoutine = { routineBack = SETTINGS; tab = ROUTINE }, onAssistant = { tab = TAB_AI },
                                onAiEnabled = { aiOn = it })
                        }
                    }
                }
            }
        }
    }

    if (focusOpen) FocusSheet(focusTask) { focusOpen = false }
    checkin?.let { (d, step) -> CheckinSheet(d, step) { checkin = null } }
}

/** Explicação pedida pelo Health Connect sobre a permissão de sono. */
@Composable
private fun HealthRationaleSheet(onDismiss: () -> Unit) {
    EditorSheet("Sono do relógio", "Por que o SunBi pede acesso ao sono no Health Connect", onDismiss) {
        Text(
            "Só leitura das sessões de sono, e só quando você toca em \"Puxar do relógio\" no check-in. " +
                "O horário em que deitou e acordou é copiado para o registro do dia. Nada é lido sozinho, nada é enviado a lugar nenhum: " +
                "os dados ficam nos seus aparelhos.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FocusRemaining(end: Long) {
    var left by remember { mutableLongStateOf(end - System.currentTimeMillis()) }
    LaunchedEffect(end) {
        while (true) {
            left = end - System.currentTimeMillis()
            delay(1000)
        }
    }
    Text(mmss(left))
}

private fun mmss(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Bloco de foco (TDAH): escolhe um tempo curto e começa pelo primeiro passo.
 * Cinco minutos é o padrão de propósito: o difícil é começar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FocusSheet(task: Task?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val end by Focus.end.collectAsState()
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(end) {
        while (end != null) {
            left = end!! - System.currentTimeMillis()
            if (left <= 0) break
            delay(250)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Foco", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(task?.label ?: "Um bloco de foco", style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
            if (task?.step?.isNotBlank() == true) {
                Text("Comece só por: ${task.step}", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            if (end == null) {
                Text("Quanto tempo agora?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceGroup(listOf(5 to "5 min", 10 to "10", 15 to "15", 25 to "25"), minutes, { if (it != null) minutes = it })
                Button(
                    onClick = { Focus.start(ctx, task?.id ?: "", minutes) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Começar $minutes minutos", style = MaterialTheme.typography.titleMedium)
                }
                Text("Se no fim quiser continuar, é só fazer mais um bloco.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else {
                val total = Prefs.focusTotal.coerceAtLeast(1)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularWavyProgressIndicator(
                        progress = { (left.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.size(220.dp),
                    )
                    Text(mmss(left), style = MaterialTheme.typography.displayMediumEmphasized, fontWeight = FontWeight.Bold)
                }
                // em tela estreita os três botões não cabem numa linha: quebram em vez de espremer
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Focus.stop(ctx) }, shapes = ButtonDefaults.shapes()) { Text("Parar") }
                    FilledTonalButton(onClick = { Focus.extend(ctx, 5) }, shapes = ButtonDefaults.shapes()) { Text("+5 min") }
                    if (task != null && task.id.isNotEmpty()) {
                        Button(onClick = {
                            Repo.setDone(today(), task.id, true)
                            Focus.stop(ctx)
                            onDismiss()
                        }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Concluído")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
