package com.dvdiso.sunbi.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

class MainActivity : ComponentActivity() {
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        SyncManager.start(this)
        ThemeState.load()
        setContent { SunBiTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        Focus.restore()
        SyncManager.syncAllAsync()
    }
}

private data class Dest(val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("Hoje", Icons.Filled.Home),
    Dest("Histórico", Icons.Filled.DateRange),
    Dest("Plano", Icons.Filled.Favorite),
    Dest("Ajustes", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Root() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(today()) }
    var tick by remember { mutableLongStateOf(0L) }
    var focusTask by remember { mutableStateOf<Task?>(null) }
    var focusOpen by remember { mutableStateOf(false) }
    var crisisRequest by remember { mutableStateOf(false) }
    val focusEnd by Focus.end.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = today()
            tick++
        }
    }

    val openFocus: (Task?) -> Unit = { t ->
        focusTask = t ?: Repo.tasks().firstOrNull { it.id == Prefs.focusTask }
        focusOpen = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Quest, tablets e celular deitado: trilho lateral e conteúdo centralizado.
        val wide = maxWidth >= 600.dp
        Scaffold(
            bottomBar = {
                if (!wide) {
                    ShortNavigationBar {
                        DESTS.forEachIndexed { i, d ->
                            ShortNavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(d.icon, null) }, label = { Text(d.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (focusEnd != null && !focusOpen) {
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
                    NavigationRail(Modifier.padding(inner)) {
                        Spacer(Modifier.weight(1f))
                        DESTS.forEachIndexed { i, d ->
                            NavigationRailItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(d.icon, null) }, label = { Text(d.label) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val pad = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = inner.calculateTopPadding() + 8.dp,
                        bottom = inner.calculateBottomPadding() + 88.dp,
                    )
                    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
                        modifier = Modifier.widthIn(max = 720.dp),
                        label = "tab",
                    ) { t ->
                        when (t) {
                            0 -> TodayScreen(now, tick, pad, onFocus = openFocus, onOpenPlan = { tab = 2 })
                            1 -> HistoryScreen(now, pad)
                            2 -> PlanScreen(pad, crisisRequest) { crisisRequest = false }
                            else -> SettingsScreen(pad)
                        }
                    }
                }
            }
        }
    }

    if (focusOpen) FocusSheet(focusTask) { focusOpen = false }
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(),
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text("Feito")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
