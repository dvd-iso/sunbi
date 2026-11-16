package com.dvdiso.sunbi.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Launcher
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.Task
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
 * Tela inicial, de propósito quase vazia: relógio, data, uma linha com a tarefa da vez e os apps
 * favoritos em texto, sobre o fundo do tema. Deslizar para cima abre todos os apps.
 * Segurar um app abre as opções.
 */
@Composable
fun LauncherScreen(apps: List<Launcher.AppEntry>, homePressed: Long, onOpenSunBi: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var drawer by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    // estado local dos apps: favoritos, ocultos e nomes mudam na hora, sem esperar recarregar
    var list by remember(apps) { mutableStateOf(apps) }
    var favKeys by remember { mutableStateOf(Launcher.favorites()) }
    var hiddenKeys by remember { mutableStateOf(Launcher.hidden()) }
    var options by remember { mutableStateOf<Launcher.AppEntry?>(null) }
    LaunchedEffect(homePressed) { drawer = false }
    LaunchedEffect(Unit) { while (true) { delay(5_000); tick++ } }
    BackHandler(enabled = drawer) { drawer = false }

    val byKey = list.associateBy { it.key }
    val favorites = favKeys.mapNotNull { byKey[it] }
    val visible = list.filter { it.key !in hiddenKeys }
    val open: (Launcher.AppEntry) -> Unit = { app ->
        Feedback.tap()
        if (!Launcher.launch(ctx, app)) Feedback.error()
        drawer = false
    }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        AnimatedContent(
            drawer,
            transitionSpec = {
                if (targetState) (slideInVertically(spring(dampingRatio = 0.85f, stiffness = 400f)) { it / 3 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 8 } + fadeOut())
                else (slideInVertically { -it / 8 } + fadeIn()) togetherWith
                    (slideOutVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it / 3 } + fadeOut())
            },
            label = "drawer",
        ) { d ->
            if (d) AppDrawer(visible, hiddenKeys.size, onOpen = open, onLongPress = { options = it }, onClose = { Feedback.tap(); drawer = false })
            else Home(tick, favorites, onOpenSunBi, onOpenDrawer = { Feedback.tap(); drawer = true }, onOpen = open, onLongPress = { options = it })
        }
    }

    options?.let { app ->
        AppOptionsSheet(
            app, isFavorite = app.key in favKeys, favoriteCount = favorites.size,
            onDismiss = { options = null },
            onFavorite = { Launcher.toggleFavorite(app); favKeys = Launcher.favorites(); options = null },
            onRename = { name ->
                val renamed = Launcher.rename(app, name)
                list = Launcher.sorted(list.map { if (it.key == app.key) renamed else it })
                options = null
            },
            onHide = { Launcher.setHidden(app, true); hiddenKeys = Launcher.hidden(); favKeys = Launcher.favorites(); options = null },
            onInfo = { Launcher.appInfo(ctx, app); options = null },
            onUninstall = {
                options = null
                if (!Launcher.uninstall(ctx, app)) {
                    Toast.makeText(ctx, "Esse app é do sistema ou de outro perfil: abri as informações dele.", Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

private fun minutes(t: String): Int? = runCatching { LocalTime.parse(t).toSecondOfDay() / 60 }.getOrNull()

/** A tarefa da vez (atrasada há até 3 h, senão a próxima), igual ao cartão "Agora" do app. */
private fun currentTask(day: String): Task? {
    val now = LocalTime.now().toSecondOfDay() / 60
    val pending = Repo.tasksFor(day).filter { !Repo.done(day, it.id) && minutes(it.time) != null }
    return pending.lastOrNull { val m = minutes(it.time)!!; m <= now && now - m <= 180 } ?: pending.firstOrNull { minutes(it.time)!! > now }
}

/** Entra uma vez, deslizando de [fromY] dp e aparecendo, com atraso em ms. */
private fun Modifier.enter(delayMs: Int, fromY: Float = 18f): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 220f)) }
    graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * fromY.dp.toPx()
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
    val task = currentTask(day)
    var turns by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -120f) onOpenDrawer() }) { _, dy -> total += dy }
            }
            .semantics { contentDescription = "Tela inicial. Deslize para cima para ver todos os apps." }
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Clock(Modifier.enter(0, fromY = -16f))
        Text(
            LocalDate.parse(day).format(DATE), Modifier.enter(80, fromY = -10f),
            style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
        )

        // uma linha só: a tarefa da vez; tocar marca como feita e a próxima entra no lugar
        Spacer(Modifier.height(28.dp))
        AnimatedContent(
            task,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it } + fadeIn()) togetherWith
                    (slideOutVertically { -it } + fadeOut())
            },
            contentKey = { it?.id },
            modifier = Modifier.enter(160),
            label = "task",
        ) { t -> if (t != null) TaskLine(day, t) else Spacer(Modifier.height(1.dp)) }

        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((i, app) in favorites.withIndex()) {
                AppRow(app, big = true, modifier = Modifier.enter(220 + 50 * i), onOpen = onOpen, onLongPress = onLongPress)
            }
        }
        if (favorites.isEmpty()) {
            Text("Deslize para cima e segure um app para deixá-lo aqui.", Modifier.enter(220),
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        // a flor dá um giro e abre o SunBi
        Box(Modifier.fillMaxWidth().enter(300, fromY = 24f), contentAlignment = Alignment.Center) {
            SunBiMark(
                Modifier.clip(CircleShape).bouncyClick(label = "Abrir o SunBi") { Feedback.tap(); turns += 1f; onOpenSunBi() },
                size = 40.dp, turns = turns, sunglasses = Repo.isOff(day),
            )
        }
    }
}

/** Relógio em que cada dígito que muda sobe e o novo entra por baixo. */
@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val text = LocalTime.now().format(HM)
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                ch,
                transitionSpec = {
                    ContentTransform(
                        slideInVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { it } + fadeIn(),
                        slideOutVertically { -it } + fadeOut(),
                    )
                },
                label = "digit$i",
            ) { c ->
                Text(c.toString(), style = MaterialTheme.typography.displayLargeEmphasized, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun TaskLine(day: String, task: Task) {
    val cs = MaterialTheme.colorScheme
    var done by remember(task.id) { mutableStateOf(false) }
    // marca na hora (o visto anima) e só grava depois, para dar tempo de ver antes da próxima entrar
    LaunchedEffect(done) {
        if (done) { delay(650); toggleTask(Feedback, day, task.id, true) }
    }
    Row(
        Modifier.clip(MaterialTheme.shapes.large)
            .bouncyClick(label = "Marcar como feita", enabled = !done) { done = true; Feedback.toggle(true) }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MorphCheck(done, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(task.time, style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Spacer(Modifier.width(10.dp))
        Text(task.label, style = MaterialTheme.typography.titleMedium, color = if (done) cs.onSurfaceVariant else cs.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (done) TextDecoration.LineThrough else null)
    }
}

@Composable
private fun AppDrawer(
    apps: List<Launcher.AppEntry>, hiddenCount: Int,
    onOpen: (Launcher.AppEntry) -> Unit, onLongPress: (Launcher.AppEntry) -> Unit, onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val shown = remember(apps, query) { Launcher.filter(apps, query) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // uma só correspondência: abre sozinho (busca de launcher)
    LaunchedEffect(shown.size, query) { if (query.length >= 2 && shown.size == 1) onOpen(shown.single()) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .pointerInput(Unit) {
                // puxar para baixo fecha a lista
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 160f) onClose() }) { _, dy -> total += dy }
            },
    ) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Buscar app") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus).enter(0, fromY = -12f),
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onOpen) }),
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
            itemsIndexed(shown, key = { _, a -> a.key }) { i, app ->
                // os primeiros entram em cascata; os de baixo já aparecem prontos
                AppRow(app, big = false, modifier = Modifier.animateItem().then(if (i < 14) Modifier.enter(30 * i) else Modifier),
                    onOpen = onOpen, onLongPress = onLongPress)
            }
            if (shown.isEmpty()) item { Text("Nenhum app com \"$query\"", Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant) }
            item {
                Text(
                    "Segure um app para favoritar, renomear ou ocultar." + if (hiddenCount > 0) " Ocultos: $hiddenCount (Ajustes → Tela inicial)." else "",
                    Modifier.padding(8.dp, 16.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** Linha de app em texto: encolhe de leve ao tocar, abre com toque, opções com toque longo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: Launcher.AppEntry, big: Boolean, modifier: Modifier = Modifier,
    onOpen: (Launcher.AppEntry) -> Unit, onLongPress: (Launcher.AppEntry) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Row(
        modifier.fillMaxWidth().scale(s).clip(MaterialTheme.shapes.large)
            .combinedClickable(
                interactionSource = src, indication = ripple(),
                onLongClickLabel = "Opções", onLongClick = { Feedback.tick(); onLongPress(app) },
                onClick = { onOpen(app) },
            )
            .padding(horizontal = 4.dp, vertical = if (big) 8.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            app.label, Modifier.weight(1f),
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (app.userSerial != 0L) Tag("trabalho", cs.secondaryContainer, cs.onSecondaryContainer)
    }
}

/** Opções de um app: uma lista curta, uma ação por linha. Renomear abre um campo no lugar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppOptionsSheet(
    app: Launcher.AppEntry, isFavorite: Boolean, favoriteCount: Int,
    onDismiss: () -> Unit, onFavorite: () -> Unit, onRename: (String) -> Unit,
    onHide: () -> Unit, onInfo: () -> Unit, onUninstall: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(app.label) }
    EditorSheet(app.label, if (app.renamed) "Nome original: ${app.original}" else null, onDismiss) {
        AnimatedContent(renaming, label = "rename") { r ->
            if (r) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Nome") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { Feedback.toggle(true); onRename(name) }),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (app.renamed) TextButton(onClick = { Feedback.tick(); onRename("") }) { Text("Voltar ao original") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { Feedback.toggle(true); onRename(name) }, enabled = name.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text("Salvar") }
                    }
                }
            } else {
                val full = !isFavorite && favoriteCount >= Launcher.MAX_FAVORITES
                val rows = listOf(
                    (if (isFavorite) "Tirar da tela inicial" else "Pôr na tela inicial") to (if (full) "A tela inicial já tem ${Launcher.MAX_FAVORITES} apps" else null),
                    "Renomear" to null,
                    "Ocultar" to "Some da lista; volta em Ajustes → Tela inicial",
                    "Informações do app" to null,
                    "Desinstalar" to null,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.forEachIndexed { i, (title, sub) ->
                        NavRow(title, sub, i, rows.size) {
                            when (i) {
                                0 -> if (!full) { Feedback.toggle(!isFavorite); onFavorite() } else Feedback.error()
                                1 -> { Feedback.tap(); renaming = true }
                                2 -> { Feedback.tick(); onHide() }
                                3 -> { Feedback.tap(); onInfo() }
                                else -> { Feedback.tap(); onUninstall() }
                            }
                        }
                    }
                }
            }
        }
    }
}
