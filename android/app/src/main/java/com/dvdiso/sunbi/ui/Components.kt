package com.dvdiso.sunbi.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dvdiso.sunbi.HUMOR
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.sleepBaseline
import java.time.LocalDate

/**
 * Marca do SunBi: uma flor de pétalas redondas (forma "Flower" do Material) com um rostinho.
 * Usa as cores do tema, então acompanha o Material You do sistema.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SunBiMark(modifier: Modifier = Modifier, size: Dp = 40.dp, spin: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val angle = if (spin) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "a").value
    } else 0f
    val petals = MaterialShapes.Flower.toShape()
    Box(modifier.size(size).semantics { contentDescription = "SunBi" }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().rotate(angle).clip(petals).background(cs.primary))
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

/** Cartão de seção com título. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (value, label) ->
            val custom = checkedColor(value)
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { onSelect(if (it) value else null) },
                modifier = Modifier.weight(1f).semantics {
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
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                Text(label, maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Escala de humor -3..+3 com cores de polo. */
@Composable
fun HumorScale(value: Int?, onChange: (Int?) -> Unit) {
    val dark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceGroup(
            options = (-3..3).map { it to (if (it > 0) "+$it" else "$it") },
            selected = value,
            onSelect = onChange,
            checkedColor = { v ->
                when {
                    v < 0 -> MoodColors.down(cs, dark)
                    v > 0 -> MoodColors.up(cs, dark)
                    else -> null
                }
            },
            description = { HUMOR[it] ?: "" },
        )
        Row(Modifier.fillMaxWidth()) {
            Text("para baixo", style = MaterialTheme.typography.labelMedium, color = MoodColors.down(cs, dark), modifier = Modifier.weight(1f))
            Text(value?.let { HUMOR[it] } ?: "estável", style = MaterialTheme.typography.labelLarge, color = cs.onSurface, textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
            Text("acelerado", style = MaterialTheme.typography.labelMedium, color = MoodColors.up(cs, dark), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

/** Gráfico estilo Life Chart: barras para cima (elevação) e para baixo (depressão) a partir do estável. */
@Composable
fun LifeChart(days: List<String>, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val up = MoodColors.up(cs, dark)
    val down = MoodColors.down(cs, dark)
    val values = days.map { Repo.humor(it) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = days.size
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val mid = size.height / 2
            val unit = (size.height / 2 - 4.dp.toPx()) / 3f
            // faixas leves, moderadas e graves
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
                    val h = kotlin.math.abs(v) * unit
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
                    drawRoundRect(col, Offset(cx - barW / 2, y(v)), Size(barW, size.height - y(v)), CornerRadius(barW / 2.5f))
                }
            }
        }
        DayAxis(days)
    }
}

@Composable
private fun DayAxis(days: List<String>) {
    val wd = listOf("S", "T", "Q", "Q", "S", "S", "D")
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        days.forEachIndexed { i, d ->
            val ld = LocalDate.parse(d)
            val show = days.size <= 14 || i % 7 == days.lastIndex % 7
            Text(
                if (!show) "" else if (days.size <= 7) wd[ld.dayOfWeek.value - 1] else ld.dayOfMonth.toString(),
                Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Lista de textos editável (adicionar e remover), para sinais e planos. */
@Composable
fun EditableList(
    items: List<String>,
    onChange: (List<String>) -> Unit,
    placeholder: String,
    bullet: @Composable (() -> Unit)? = null,
) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bullet != null) {
                    bullet()
                    Spacer(Modifier.width(10.dp))
                } else {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(12.dp))
                }
                Text(s, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remover \"$s\"", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val add = {
                if (draft.isNotBlank()) {
                    onChange(items + draft.trim())
                    draft = ""
                }
            }
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, singleLine = true,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        color = content, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
    )
}

fun minutesLabel(m: Long): String = when {
    m < 1 -> "agora"
    m < 60 -> "em $m min"
    m % 60 == 0L -> "em ${m / 60} h"
    else -> "em ${m / 60} h ${m % 60} min"
}
