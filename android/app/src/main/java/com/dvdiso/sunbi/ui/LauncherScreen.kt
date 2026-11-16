package com.dvdiso.sunbi.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Launcher
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.Task
import com.dvdiso.sunbi.today
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 *
 * A lista de apps acompanha o dedo: [p] vai de 0 (tela inicial) a 1 (lista aberta), o arrasto
 * mexe nele direto e, ao soltar, uma mola leva até o fim. Uma animação só, sem cascatas por cima.
 */
@Composable
fun LauncherScreen(apps: List<Launcher.AppEntry>, homePressed: Long, onOpenSunBi: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var p by remember { mutableFloatStateOf(0f) }
    var opening by remember { mutableStateOf(false) } // para onde a mola está levando
    var pulling by remember { mutableStateOf(false) } // puxando a lista aberta para baixo
    var job by remember { mutableStateOf<Job?>(null) }
    var height by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableLongStateOf(0L) }
    // a entrada em cascata da tela inicial acontece uma vez só, não a cada volta da lista
    var homeEntered by remember { mutableStateOf(false) }
    // estado local dos apps: favoritos, ocultos e nomes mudam na hora, sem esperar recarregar
    var list by remember(apps) { mutableStateOf(apps) }
    var favKeys by remember { mutableStateOf(Launcher.favorites()) }
    var hiddenKeys by remember { mutableStateOf(Launcher.hidden()) }
    var options by remember { mutableStateOf<Launcher.AppEntry?>(null) }

    fun settle(open: Boolean) {
        opening = open
        pulling = false
        job?.cancel()
        job = scope.launch {
            animate(p, if (open) 1f else 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f)) { v, _ -> p = v.coerceIn(0f, 1f) }
        }
    }
    fun snap(open: Boolean) {
        job?.cancel()
        opening = open
        pulling = false
        p = if (open) 1f else 0f
    }
    // o dedo percorre meia tela para abrir ou fechar por completo
    fun dragBy(dy: Float) {
        job?.cancel()
        p = (p - dy / (height * 0.5f).coerceAtLeast(300f)).coerceIn(0f, 1f)
    }

    LaunchedEffect(homePressed) { if (p > 0f) settle(false) }
    LaunchedEffect(Unit) { delay(900); homeEntered = true }
    LaunchedEffect(Unit) { while (true) { delay(5_000); tick++ } }
    // a composição só olha para estes três; o valor de [p] quadro a quadro é lido nas camadas
    // (graphicsLayer), então arrastar não recompõe a tela
    val showHome by remember { derivedStateOf { p < 1f } }
    val showDrawer by remember { derivedStateOf { p > 0f } }
    val opened by remember { derivedStateOf { p >= 1f } }
    BackHandler(enabled = opening || showDrawer) { Feedback.tap(); settle(false) }

    val favorites = remember(list, favKeys) { val byKey = list.associateBy { it.key }; favKeys.mapNotNull { byKey[it] } }
    val visible = remember(list, hiddenKeys) { list.filter { it.key !in hiddenKeys } }
    val open: (Launcher.AppEntry) -> Unit = { app ->
        Feedback.tap()
        if (!Launcher.launch(ctx, app)) Feedback.error()
        snap(false)
    }

    Box(Modifier.fillMaxSize().background(cs.surface).onSizeChanged { height = it.height.toFloat() }) {
        if (showHome) {
            // a tela inicial recua de leve e some enquanto a lista sobe
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                translationY = -p * size.height * 0.10f
                val sc = 1f - 0.05f * p
                scaleX = sc
                scaleY = sc
            }) {
                Home(
                    tick, favorites, skipEnter = homeEntered, onOpenSunBi = onOpenSunBi,
                    onDrag = { dragBy(it) },
                    onRelease = { last ->
                        val go = p > 0.3f || (last < -6f && p > 0.04f)
                        if (go) Feedback.tap()
                        settle(go)
                    },
                    onOpen = open, onLongPress = { options = it },
                )
            }
        }
        if (showDrawer) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = ((p - 0.1f) / 0.6f).coerceIn(0f, 1f)
                translationY = (1f - p) * size.height * 0.35f
            }) {
                AppDrawer(
                    visible, hiddenKeys.size, opened = opened, pulling = { pulling },
                    onDrag = { dy -> if (dy > 0f) pulling = true; dragBy(dy) },
                    onRelease = { vy ->
                        if (p < 1f) {
                            val close = p < 0.78f || vy > 1500f
                            if (close) Feedback.tap()
                            settle(!close)
                        } else pulling = false
                    },
                    onOpen = open, onLongPress = { options = it },
                )
            }
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

/** Entra uma vez, deslizando de [fromY] dp e aparecendo, com atraso em ms. Com [skip], já nasce no lugar. */
private fun Modifier.enter(delayMs: Int, fromY: Float = 18f, skip: Boolean = false): Modifier = composed {
    val a = remember { Animatable(if (skip) 1f else 0f) }
    LaunchedEffect(Unit) { if (a.value < 1f) { delay(delayMs.toLong()); a.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 220f)) } }
    graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * fromY.dp.toPx()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Home(
    tick: Long, favorites: List<Launcher.AppEntry>, skipEnter: Boolean, onOpenSunBi: () -> Unit,
    onDrag: (Float) -> Unit, onRelease: (Float) -> Unit,
    onOpen: (Launcher.AppEntry) -> Unit, onLongPress: (Launcher.AppEntry) -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") tick
    val cs = MaterialTheme.colorScheme
    val day = today()
    val task = currentTask(day)
    var turns by remember { mutableFloatStateOf(0f) }
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    // quem volta da lista de apps encontra a tela já no lugar
    val skip = remember { skipEnter }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .pointerInput(Unit) {
                var last = 0f
                detectVerticalDragGestures(
                    onDragStart = { last = 0f }, onDragEnd = { release(last) }, onDragCancel = { release(0f) },
                ) { _, dy -> last = dy; drag(dy) }
            }
            .semantics { contentDescription = "Tela inicial. Deslize para cima para ver todos os apps." }
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Clock(Modifier.enter(0, fromY = -16f, skip = skip))
        Text(
            LocalDate.parse(day).format(DATE), Modifier.enter(80, fromY = -10f, skip = skip),
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
            modifier = Modifier.enter(160, skip = skip),
            label = "task",
        ) { t -> if (t != null) TaskLine(day, t) else Spacer(Modifier.height(1.dp)) }

        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((i, app) in favorites.withIndex()) {
                AppRow(app, big = true, modifier = Modifier.enter(220 + 50 * i, skip = skip), onOpen = onOpen, onLongPress = onLongPress)
            }
        }
        if (favorites.isEmpty()) {
            Text("Deslize para cima e segure um app para deixá-lo aqui.", Modifier.enter(220, skip = skip),
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        // a flor dá um giro e abre o SunBi
        Box(Modifier.fillMaxWidth().enter(300, fromY = 24f, skip = skip), contentAlignment = Alignment.Center) {
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

/**
 * Todos os apps, com busca. [opened] vira true quando a lista terminou de subir (só aí o teclado
 * abre). Puxar para baixo com a lista no topo, ou pela busca, arrasta a tela de volta: os
 * arrastos saem por [onDrag] e o soltar por [onRelease] (com a velocidade), e quem decide é a tela.
 */
@Composable
private fun AppDrawer(
    apps: List<Launcher.AppEntry>, hiddenCount: Int, opened: Boolean, pulling: () -> Boolean,
    onDrag: (Float) -> Unit, onRelease: (Float) -> Unit,
    onOpen: (Launcher.AppEntry) -> Unit, onLongPress: (Launcher.AppEntry) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val shown = remember(apps, query) { Launcher.filter(apps, query) }
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val isPulling by rememberUpdatedState(pulling)
    LaunchedEffect(opened) { if (opened) runCatching { focus.requestFocus() } }
    // uma só correspondência: abre sozinho (busca de launcher)
    LaunchedEffect(shown.size, query) { if (query.length >= 2 && shown.size == 1) onOpen(shown.single()) }

    // a lista rola normalmente; só o que sobra no topo (puxar para baixo) vira arrasto da tela
    val pull = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // no meio do puxão, voltar o dedo para cima devolve a tela antes de rolar a lista
                if (source == NestedScrollSource.UserInput && available.y < 0f && isPulling()) {
                    drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isPulling()) {
                    release(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Buscar app") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { release(0f) }, onDragCancel = { release(0f) }) { _, dy -> drag(dy) }
                },
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onOpen) }),
        )
        LazyColumn(Modifier.weight(1f).nestedScroll(pull), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
            itemsIndexed(shown, key = { _, a -> a.key }) { _, app ->
                // sem entrada por item: rolar não pode reanimar o que volta para a tela.
                // animateItem só reposiciona quando a busca filtra a lista.
                AppRow(app, big = false, modifier = Modifier.animateItem(), onOpen = onOpen, onLongPress = onLongPress)
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
