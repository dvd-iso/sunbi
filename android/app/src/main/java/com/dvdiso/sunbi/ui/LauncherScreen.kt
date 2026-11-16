package com.dvdiso.sunbi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dvdiso.sunbi.Game
import com.dvdiso.sunbi.Launcher
import com.dvdiso.sunbi.Prefs
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.today
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT = Locale.forLanguageTag("pt-BR")
private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT)

/**
 * Tela inicial: relógio, a tarefa da vez, o check-in e os apps favoritos em texto.
 * Deslizar para cima (ou tocar na seta) abre a gaveta com busca. Segurar um app abre as opções.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LauncherScreen(apps: List<Launcher.AppEntry>, homePressed: Long, onOpenSunBi: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var drawer by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    var favKeys by remember { mutableStateOf(Launcher.favorites()) }
    var hiddenKeys by remember { mutableStateOf(Launcher.hidden()) }
    var options by remember { mutableStateOf<Launcher.AppEntry?>(null) }
    LaunchedEffect(homePressed) { drawer = false }
    LaunchedEffect(Unit) { while (true) { delay(10_000); tick++ } }
    BackHandler(enabled = drawer) { drawer = false }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val favorites = favKeys.mapNotNull { byKey[it] }
    val visible = remember(apps, hiddenKeys) { apps.filter { it.key !in hiddenKeys } }
    val open: (Launcher.AppEntry) -> Unit = { app ->
        Feedback.tap()
        if (!Launcher.launch(ctx, app)) Feedback.error()
        drawer = false
    }

    // o papel de parede fica atrás; um véu da cor de superfície mantém a leitura e o tema
    Box(Modifier.fillMaxSize().background(cs.surface.copy(alpha = 0.86f))) {
        AnimatedContent(
            drawer,
            transitionSpec = {
                if (targetState) (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 6 } + fadeOut())
                else (slideInVertically { -it / 6 } + fadeIn()) togetherWith (slideOutVertically { it / 3 } + fadeOut())
            },
            label = "drawer",
        ) { d ->
            if (d) AppDrawer(visible, favKeys, onOpen = open, onLongPress = { options = it })
            else Home(tick, favorites, onOpenSunBi, onOpenDrawer = { Feedback.tap(); drawer = true }, onOpen = open, onLongPress = { options = it })
        }
    }

    options?.let { app ->
        val fav = app.key in favKeys
        EditorSheet(app.label, if (fav) "Favorito" else null, onDismiss = { options = null }) {
            Button(onClick = { Feedback.toggle(!fav); Launcher.toggleFavorite(app); favKeys = Launcher.favorites(); options = null },
                shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text(if (fav) "Tirar dos favoritos" else "Adicionar aos favoritos (${favorites.size}/${Launcher.MAX_FAVORITES})")
            }
            OutlinedButton(onClick = { Feedback.tick(); Launcher.setHidden(app, true); hiddenKeys = Launcher.hidden(); favKeys = Launcher.favorites(); options = null },
                shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Esconder da lista") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { Launcher.appInfo(ctx, app); options = null }, modifier = Modifier.weight(1f)) { Text("Informações") }
                TextButton(onClick = { Launcher.uninstall(ctx, app); options = null }, modifier = Modifier.weight(1f)) { Text("Desinstalar", color = cs.error) }
            }
            Text("Apps escondidos voltam em Ajustes → Tela inicial.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Home(
    tick: Long, favorites: List<Launcher.AppEntry>, onOpenSunBi: () -> Unit, onOpenDrawer: () -> Unit,
    onOpen: (Launcher.AppEntry) -> Unit, onLongPress: (Launcher.AppEntry) -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") tick
    val cs = MaterialTheme.colorScheme
    val day = today()
    val tasks = Repo.tasksFor(day)
    val stats = Game.stats(day)
    val now = LocalTime.now()
    var lockHelp by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -120f) onOpenDrawer() }) { _, dy -> total += dy }
            }
            // toque duplo no fundo: trava a tela (com o serviço de acessibilidade ligado)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (!com.dvdiso.sunbi.LockService.lock()) lockHelp = true else Feedback.tap() }) }
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // relógio e data; a flor abre o SunBi
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(now.format(HM), style = MaterialTheme.typography.displayLargeEmphasized, fontWeight = FontWeight.Bold, color = cs.onSurface)
                Text(LocalDate.parse(day).format(DATE).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                if (stats.streak > 0) {
                    Text("🔥 ${stats.streak} ${if (stats.streak == 1) "dia" else "dias"} · nível ${stats.level}", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            SunBiMark(Modifier.clip(CircleShape).bouncyClick(label = "Abrir o SunBi") { Feedback.tap(); onOpenSunBi() },
                size = 56.dp, sunglasses = Repo.isOff(day))
        }
        if (tasks.isNotEmpty()) NowCard(tasks, tick, onFocus = {}, Modifier.enterFromBelow(0))
        if (!Repo.checkedIn(day)) CheckinSummary(day, Modifier.enterFromBelow(1)) { onOpenSunBi() }

        // favoritos em texto, como nos launchers minimalistas
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (favorites.isEmpty()) {
                Text("Segure um app na lista para deixá-lo aqui.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
            for ((i, app) in favorites.withIndex()) {
                AppRow(app, big = true, modifier = Modifier.enterFromBelow(2 + i), onOpen = onOpen, onLongPress = onLongPress)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenDrawer, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.KeyboardArrowUp, null)
            Spacer(Modifier.width(6.dp))
            Text("Todos os apps")
        }
    }
    if (lockHelp) {
        EditorSheet("Travar a tela com dois toques", "Para isso o Android pede um serviço de acessibilidade. O do SunBi só trava a tela: não lê a tela nem outros apps.", { lockHelp = false }) {
            Button(onClick = { com.dvdiso.sunbi.LockService.openSettings(ctx); lockHelp = false }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text("Abrir Acessibilidade")
            }
            Text("Lá, ligue \"SunBi · travar a tela\". Depois, dois toques no fundo da tela inicial travam o telefone.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppDrawer(
    apps: List<Launcher.AppEntry>, favKeys: List<String>,
    onOpen: (Launcher.AppEntry) -> Unit, onLongPress: (Launcher.AppEntry) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val shown = remember(apps, query) { Launcher.filter(apps, query) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // uma só correspondência: abre sozinho (busca de launcher)
    LaunchedEffect(shown.size, query) { if (query.length >= 2 && shown.size == 1) onOpen(shown.single()) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Buscar app") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onOpen) }),
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { it.key }) { app ->
                AppRow(app, big = false, star = app.key in favKeys, onOpen = onOpen, onLongPress = onLongPress)
            }
            if (shown.isEmpty()) item { Text("Nenhum app com \"$query\"", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRow(
    app: Launcher.AppEntry, big: Boolean, modifier: Modifier = Modifier, star: Boolean = false,
    onOpen: (Launcher.AppEntry) -> Unit, onLongPress: (Launcher.AppEntry) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .pointerInput(app.key) {
                detectTapGestures(onTap = { onOpen(app) }, onLongPress = { Feedback.tick(); onLongPress(app) })
            }
            .padding(horizontal = 8.dp, vertical = if (big) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            app.label, Modifier.weight(1f),
            style = if (big) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleMedium,
            color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (app.userSerial != 0L) Tag("trabalho", cs.secondaryContainer, cs.onSecondaryContainer)
        if (star) Text("★", color = cs.primary, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
