package com.dvdiso.sunbi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dvdiso.sunbi.Game
import com.dvdiso.sunbi.Prefs
import kotlin.math.sin
import kotlin.random.Random

/** Selo pequeno do cabeçalho de Hoje: sequência e nível. Toque abre as conquistas. */
@Composable
fun GameChip(stats: Game.Stats, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(cs.surfaceContainerHigh)
            .semantics { contentDescription = "Conquistas: ${stats.streak} dias seguidos, nível ${stats.level}" }
            .bouncyClick(label = "Conquistas") { Feedback.tap(); onOpen() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (stats.streak > 0) "🔥" else "🌱", fontSize = 14.sp, modifier = Modifier.pulse(stats.goalCheckin && stats.streak > 0))
        Spacer(Modifier.width(4.dp))
        // tela estreita: só a sequência, para não cortar a saudação
        val n = animatedInt(stats.streak)
        Text(if (isNarrow()) "$n" else "$n · Nv ${stats.level}", style = MaterialTheme.typography.labelLarge, color = cs.onSurface, maxLines = 1)
    }
}

/** Faixa em Hoje: sequência, nível e a barra até o próximo. Toque abre as conquistas. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GameStrip(stats: Game.Stats, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val progress by animateFloatAsState(stats.progress.coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "xp")
    Card(
        modifier.fillMaxWidth().semantics { contentDescription = "Conquistas" }.bouncyClick(label = "Conquistas") { Feedback.tap(); onOpen() },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // chama da sequência: pulsa quando o dia de hoje já conta
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (stats.streak > 0) cs.primaryContainer else cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (stats.streak > 0) "🔥" else "🌱", fontSize = 22.sp, modifier = Modifier.pulse(stats.goalCheckin && stats.streak > 0))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${animatedInt(stats.streak)} ${if (stats.streak == 1) "dia seguido" else "dias seguidos"}" +
                        if (stats.todayXp > 0) "  ·  hoje +${stats.todayXp} XP" else "",
                    style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Nível ${stats.level} · ${stats.levelName} · ${animatedInt(stats.xp - stats.levelStart)} de ${stats.levelEnd - stats.levelStart} XP",
                    style = MaterialTheme.typography.labelMedium, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
        }
    }
}

/** Folha com nível, sequência, meta do dia e todas as conquistas. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun AchievementsSheet(stats: Game.Stats, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    EditorSheet("Conquistas", "Pontos por cuidar da rotina. Sem punição: folga não quebra a sequência.", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { stats.progress.coerceIn(0f, 1f) }, modifier = Modifier.size(96.dp))
                Text("${stats.level}", style = MaterialTheme.typography.headlineMediumEmphasized, color = cs.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(stats.levelName, style = MaterialTheme.typography.titleLargeEmphasized)
                Text("${stats.xp} XP no total", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                Text("Faltam ${(stats.levelEnd - stats.xp).coerceAtLeast(0)} para o nível ${stats.level + 1}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${stats.streak}", "dias seguidos", Modifier.weight(1f))
            Stat("${stats.bestStreak}", "melhor sequência", Modifier.weight(1f))
            Stat("${stats.badges.size}/${Game.BADGES.size}", "conquistas", Modifier.weight(1f))
        }
        Label("Meta de hoje")
        GoalRow("Fazer o check-in", stats.goalCheckin)
        GoalRow("Marcar todas as âncoras", stats.goalAnchors)
        Text(
            "Como ganhar pontos: 10 por tarefa, 20 pelo check-in, 30 pela rotina inteira e 15 pelas âncoras no horário.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
        Label("Conquistas")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
            for (b in Game.BADGES) BadgeTile(b, b.id in stats.badges, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GoalRow(text: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MorphCheck(done, size = 28.dp)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BadgeTile(b: Game.Badge, unlocked: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(MaterialTheme.shapes.large)
            .background(if (unlocked) cs.primaryContainer else cs.surfaceContainerHigh)
            .alpha(if (unlocked) 1f else 0.55f)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(if (unlocked) b.emoji else "🔒", fontSize = 28.sp)
        Text(b.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
            color = if (unlocked) cs.onPrimaryContainer else cs.onSurface)
        Text(b.desc, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 3, color = cs.onSurfaceVariant)
    }
}

// ── comemorações ────────────────────────────────────────────────────────────

sealed class Celebration {
    data class NewBadge(val badge: Game.Badge) : Celebration()
    data class LevelUp(val level: Int, val name: String) : Celebration()
}

/**
 * Observa as estatísticas e mostra uma comemoração quando aparece uma conquista nova ou um
 * nível novo. Na primeira vez (instalação antiga com histórico) só anota, sem festa atrasada.
 */
@Composable
fun Celebrations(stats: Game.Stats, paused: Boolean = false) {
    var show by remember { mutableStateOf<Celebration?>(null) }
    LaunchedEffect(stats.level, stats.badges, paused) {
        if (Prefs.seenLevel == 0) {
            Prefs.seenLevel = stats.level
            Prefs.seenBadges = stats.badges
            return@LaunchedEffect
        }
        // com o check-in ou outra folha aberta, espera fechar: uma janela por vez
        if (show != null || paused) return@LaunchedEffect
        if (stats.level > Prefs.seenLevel) {
            Prefs.seenLevel = stats.level
            show = Celebration.LevelUp(stats.level, stats.levelName)
            Feedback.levelUp()
            return@LaunchedEffect
        }
        val fresh = stats.badges - Prefs.seenBadges
        if (fresh.isNotEmpty()) {
            val b = Game.badge(fresh.first()) ?: return@LaunchedEffect
            Prefs.seenBadges = Prefs.seenBadges + b.id
            show = Celebration.NewBadge(b)
            Feedback.badge()
        }
    }
    show?.let { c -> CelebrationDialog(c) { show = null } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CelebrationDialog(c: Celebration, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Confetti(Modifier.fillMaxSize())
            Card(
                Modifier.padding(32.dp).scale(0.6f + 0.4f * pop.value),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (c) {
                        is Celebration.NewBadge -> {
                            Text(c.badge.emoji, fontSize = 64.sp, modifier = Modifier.rotate((1f - pop.value) * -30f))
                            Text("Conquista nova!", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                            Text(c.badge.title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
                            Text(c.badge.desc, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        is Celebration.LevelUp -> {
                            SunBiMark(size = 72.dp, turns = pop.value)
                            Text("Subiu de nível!", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                            Text("Nível ${c.level} · ${c.name}", style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
                            Text("Cada dia cuidado conta. Continue no seu ritmo.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    Button(onClick = onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(top = 8.dp)) {
                        Text("Que legal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private class Particle(val x: Float, val y0: Float, val speed: Float, val size: Float, val color: Int, val spin: Float, val drift: Float)

/** Confete caindo, desenhado no Canvas: leve e sem imagens. */
@Composable
fun Confetti(modifier: Modifier = Modifier, count: Int = 90) {
    val cs = MaterialTheme.colorScheme
    val palette = listOf(cs.primary, cs.tertiary, cs.secondary, cs.primaryContainer, cs.tertiaryContainer)
    val parts = remember {
        val r = Random(42)
        List(count) { Particle(r.nextFloat(), -r.nextFloat() * 0.6f, 0.25f + r.nextFloat() * 0.35f, 6f + r.nextFloat() * 8f, r.nextInt(palette.size), r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 0.2f) }
    }
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < 4f) {
            withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
        }
    }
    val fade by animateFloatAsState(if (t > 3f) 0f else 1f, tween(900, easing = LinearEasing), label = "fade")
    Canvas(modifier) {
        for (p in parts) {
            val y = (p.y0 + p.speed * t) * size.height
            if (y > size.height + 20f) continue
            val x = (p.x + sin(t * 2f + p.spin) * 0.02f + p.drift * t) * size.width
            val s = p.size.dp.toPx()
            rotate(p.spin + t * 180f, Offset(x, y)) {
                drawRoundRect(palette[p.color].copy(alpha = fade), Offset(x - s / 2, y - s / 4), Size(s, s / 2), CornerRadius(s / 6))
            }
        }
    }
}
