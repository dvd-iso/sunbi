package com.dvdiso.sunbi.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.dvdiso.sunbi.HUMOR
import com.dvdiso.sunbi.Prefs
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.Sfx
import com.dvdiso.sunbi.sleepBaseline
import java.time.LocalDate
import kotlin.math.ceil

/** Tela estreita (celular pequeno, fonte grande): menos respiro nas bordas. */
@Composable
fun isNarrow(): Boolean = LocalConfiguration.current.screenWidthDp < 380

/** Som + vibração leve para as ações da interface (desligáveis em Ajustes). */
class Feedback(private val haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    private fun buzz(t: HapticFeedbackType) {
        if (Prefs.uiSounds) haptic.performHapticFeedback(t)
    }

    /** Marcar ou desmarcar uma tarefa; [completesDay] toca o acorde de dia completo. */
    fun toggle(done: Boolean, completesDay: Boolean = false) {
        when {
            completesDay -> { Sfx.play(Sfx.S.CHIME); buzz(HapticFeedbackType.Confirm) }
            done -> { Sfx.play(Sfx.S.POP); buzz(HapticFeedbackType.ToggleOn) }
            else -> { Sfx.play(Sfx.S.UNPOP); buzz(HapticFeedbackType.ToggleOff) }
        }
    }

    fun tick() {
        Sfx.play(Sfx.S.TICK)
        buzz(HapticFeedbackType.SegmentTick)
    }
}

@Composable
fun rememberFeedback(): Feedback {
    val h = LocalHapticFeedback.current
    return remember(h) { Feedback(h) }
}

/** Marca uma tarefa no dia, com som, vibração e o acorde quando a rotina fecha. */
fun toggleTask(fb: Feedback, day: String, id: String, done: Boolean) {
    Repo.setDone(day, id, done)
    val tasks = Repo.tasks()
    fb.toggle(done, completesDay = done && tasks.isNotEmpty() && tasks.all { Repo.done(day, it.id) })
}

/**
 * Marca do SunBi: uma flor de pétalas redondas (forma "Flower" do Material) com um rostinho.
 * Usa as cores do tema, então acompanha o Material You do sistema.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SunBiMark(modifier: Modifier = Modifier, size: Dp = 40.dp, spin: Boolean = false, turns: Float = 0f) {
    val cs = MaterialTheme.colorScheme
    val angle = if (spin) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "a").value
    } else 0f
    // um giro com mola cada vez que [turns] muda (ex.: rotina do dia completa)
    val extra by animateFloatAsState(turns * 360f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "turns")
    val petals = MaterialShapes.Flower.toShape()
    Box(modifier.size(size).semantics { contentDescription = "SunBi" }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().rotate(angle + extra).clip(petals).background(cs.primary))
        Box(Modifier.size(size * 0.56f).clip(CircleShape).background(cs.primaryContainer))
        Canvas(Modifier.size(size * 0.56f)) {
            val w = this.size.width
            val ink = cs.onPrimaryContainer
            drawCircle(ink, radius = w * 0.075f, center = Offset(w * 0.33f, w * 0.43f))
            drawCircle(ink, radius = w * 0.075f, center = Offset(w * 0.67f, w * 0.43f))
            drawArc(
                ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(w * 0.32f, w * 0.40f), size = Size(w * 0.36f, w * 0.30f),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round),
            )
        }
    }
}

/** Forma que interpola entre dois polígonos do Material (círculo → biscoito ao marcar). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = morph.toPath(progress, Path())
        p.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(p)
    }
}

/** Caixinha de tarefa: círculo vazio que vira um biscoito preenchido com mola e um "pulo". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphCheck(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val cs = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val p by animateFloatAsState(if (checked) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "check")
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) { first = false; return@LaunchedEffect }
        pop.snapTo(0.82f)
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 500f))
    }
    val fill = androidx.compose.ui.graphics.lerp(cs.surfaceContainerHighest, cs.primary, p)
    Box(
        modifier.size(size).scale(pop.value).rotate(p * 40f).clip(MorphShape(morph, p)).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (p > 0.05f) {
            Icon(Icons.Filled.Check, null, tint = cs.onPrimary, modifier = Modifier.size(size * 0.55f).scale(p).rotate(-p * 40f))
        } else {
            Box(Modifier.size(size * 0.72f).clip(CircleShape).background(cs.surface))
        }
    }
}

/** Cartão de seção com título. Cresce e encolhe com animação. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pad = if (isNarrow()) 16.dp else 20.dp
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.padding(pad).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Grupo de botões conectados (Material 3 Expressive) para escolher um valor.
 * Tocar de novo no valor marcado limpa a escolha.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChoiceGroup(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    checkedColor: (T) -> Color? = { null },
    description: (T) -> String = { "" },
) {
    val fb = rememberFeedback()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (value, label) ->
            val custom = checkedColor(value)
            ToggleButton(
                checked = selected == value,
                onCheckedChange = {
                    fb.tick()
                    onSelect(if (it) value else null)
                },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics {
                    role = Role.RadioButton
                    val d = description(value)
                    if (d.isNotEmpty()) contentDescription = d
                },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = if (custom != null) {
                    ToggleButtonDefaults.colors(checkedContainerColor = custom, checkedContentColor = Color.Black.copy(alpha = 0.87f))
                } else ToggleButtonDefaults.colors(),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Escala de humor -3..+3 com cores de polo. O nome do nível fica numa linha própria. */
@Composable
fun HumorScale(value: Int?, onChange: (Int?) -> Unit) {
    val dark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val down = MoodColors.down(cs, dark)
    val up = MoodColors.up(cs, dark)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            value?.let { HUMOR[it] } ?: "Toque no número que mais combina com hoje",
            style = if (value != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = when {
                value == null -> cs.onSurfaceVariant
                value < 0 -> down
                value > 0 -> up
                else -> cs.primary
            },
            fontWeight = if (value != null) FontWeight.SemiBold else null,
        )
        ChoiceGroup(
            options = (-3..3).map { it to (if (it > 0) "+$it" else "$it") },
            selected = value,
            onSelect = onChange,
            checkedColor = { v ->
                when {
                    v < 0 -> down
                    v > 0 -> up
                    else -> null
                }
            },
            description = { HUMOR[it] ?: "" },
        )
        Row(Modifier.fillMaxWidth()) {
            Text("↓ para baixo", style = MaterialTheme.typography.labelMedium, color = down, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Text("acelerado ↑", style = MaterialTheme.typography.labelMedium, color = up, maxLines = 1, softWrap = false)
        }
    }
}

/** Anima o desenho dos gráficos ao aparecer e ao trocar o período. */
@Composable
private fun rememberGrow(key: Any): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 120f)) }
    return a.value
}

/** Gráfico estilo Life Chart: barras para cima (elevação) e para baixo (depressão) a partir do estável. */
@Composable
fun LifeChart(days: List<String>, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val up = MoodColors.up(cs, dark)
    val down = MoodColors.down(cs, dark)
    val values = days.map { Repo.humor(it) }
    val grow = rememberGrow(days.size)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = days.size
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val mid = size.height / 2
            val unit = (size.height / 2 - 4.dp.toPx()) / 3f
            for (k in 1..3) {
                drawLine(cs.outlineVariant.copy(alpha = 0.5f), Offset(0f, mid - k * unit), Offset(size.width, mid - k * unit),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                drawLine(cs.outlineVariant.copy(alpha = 0.5f), Offset(0f, mid + k * unit), Offset(size.width, mid + k * unit),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            }
            drawLine(cs.outline, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2.dp.toPx())
            values.forEachIndexed { i, v ->
                val cx = slot * i + slot / 2
                if (v == null) {
                    drawCircle(cs.outlineVariant, radius = 3.dp.toPx(), center = Offset(cx, mid))
                } else if (v == 0) {
                    drawRoundRect(cs.primary, Offset(cx - barW / 2, mid - 3.dp.toPx()), Size(barW, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                } else {
                    val h = kotlin.math.abs(v) * unit * grow
                    val top = if (v > 0) mid - h else mid
                    drawRoundRect(if (v > 0) up else down, Offset(cx - barW / 2, top), Size(barW, h), CornerRadius(barW / 2.5f))
                }
            }
        }
        DayAxis(days)
    }
}

/** Barras de horas de sono com a linha do sono habitual. */
@Composable
fun SleepChart(days: List<String>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val values = days.map { Repo.sleep(it) }
    val base = sleepBaseline(days.last())
    val grow = rememberGrow(days.size)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = days.size
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val maxH = 12.0
            fun y(h: Double) = size.height - (h.coerceIn(0.0, maxH) / maxH * size.height).toFloat()
            drawLine(cs.tertiary, Offset(0f, y(base)), Offset(size.width, y(base)), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            values.forEachIndexed { i, v ->
                val cx = slot * i + slot / 2
                if (v == null) {
                    drawCircle(cs.outlineVariant, radius = 3.dp.toPx(), center = Offset(cx, size.height - 3.dp.toPx()))
                } else {
                    val col = if (v < 6.0) cs.error else cs.primary
                    val top = size.height - (size.height - y(v)) * grow
                    drawRoundRect(col, Offset(cx - barW / 2, top), Size(barW, size.height - top), CornerRadius(barW / 2.5f))
                }
            }
        }
        DayAxis(days)
    }
}

/** Rótulos dos dias sob os gráficos, espaçados para nunca encavalar. */
@Composable
private fun DayAxis(days: List<String>) {
    val wd = listOf("S", "T", "Q", "Q", "S", "S", "D")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        // cada rótulo precisa de ~22 dp; com pouco espaço, mostra um a cada `step` dias
        val step = ceil(days.size * 22f / maxWidth.value).toInt().coerceAtLeast(1)
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                val ld = LocalDate.parse(d)
                val show = (days.lastIndex - i) % step == 0
                Text(
                    if (!show) "" else if (days.size <= 7) wd[ld.dayOfWeek.value - 1] else ld.dayOfMonth.toString(),
                    Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Número grande com legenda, para os resumos. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Bloco clicável "rótulo pequeno + valor grande" (ex.: Deitei / 23:10). Lê bem em qualquer largura. */
@Composable
fun ValueTile(label: String, value: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(cs.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, maxLines = 1)
        Text(
            value ?: "--:--", style = MaterialTheme.typography.headlineSmall,
            color = if (value != null) cs.onSurface else cs.onSurfaceVariant, maxLines = 1,
        )
    }
}

/** Lista de textos editável (adicionar e remover), para sinais e planos. */
@Composable
fun EditableList(
    items: List<String>,
    onChange: (List<String>) -> Unit,
    placeholder: String,
) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(12.dp))
                Text(s, Modifier.weight(1f).padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remover \"$s\"", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            val add = {
                if (draft.isNotBlank()) {
                    onChange(items + draft.trim())
                    draft = ""
                }
            }
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                placeholder = { Text(placeholder) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = add, enabled = draft.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar")
            }
        }
    }
}

/** Pílula pequena (remédio, âncora...). */
@Composable
fun Tag(text: String, container: Color, content: Color) {
    Text(
        text,
        Modifier.clip(CircleShape).background(container).padding(horizontal = 8.dp, vertical = 2.dp),
        color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
    )
}

fun minutesLabel(m: Long): String = when {
    m < 1 -> "agora"
    m < 60 -> "em $m min"
    m % 60 == 0L -> "em ${m / 60} h"
    else -> "em ${m / 60} h ${m % 60} min"
}
