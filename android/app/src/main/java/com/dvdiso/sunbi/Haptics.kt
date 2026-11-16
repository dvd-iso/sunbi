package com.dvdiso.sunbi

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibrações com forma para o feedback tátil. Cada ação tem um desenho próprio: marcar uma
 * tarefa "sobe e estala", desmarcar "cai", fechar o dia é um acorde, conquista é um giro.
 *
 * Em aparelhos com motor moderno (Android 11+ com primitivas) usa composições; nos outros,
 * ondas com amplitude. Tudo desligável em Ajustes, junto com os sons.
 */
object Haptics {
    enum class H { TAP, SELECT, STEP, DONE, UNDONE, DAY_COMPLETE, VACATION_ON, VACATION_OFF, ERROR, BADGE, LEVEL_UP, STREAK, SEND }

    /** Um trecho de composição: primitiva, intensidade 0..1 e pausa antes (ms). */
    private class P(val id: Int, val scale: Float, val delay: Int = 0)

    /** Desenho de uma vibração: primitivas (motor moderno) e onda (tempos ms / amplitudes 0..255). */
    private class Pattern(val prims: List<P>, val timings: LongArray, val amps: IntArray)

    private var vib: Vibrator? = null

    fun init(ctx: Context) {
        vib = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // primitivas (constantes do Android 11/12; só são usadas depois de checar o suporte)
    private const val CLICK = 1
    private const val THUD = 2
    private const val SPIN = 3
    private const val QUICK_RISE = 4
    private const val SLOW_RISE = 5
    private const val QUICK_FALL = 6
    private const val TICK = 7

    private fun pattern(h: H): Pattern = when (h) {
        H.TAP -> Pattern(listOf(P(TICK, 0.5f)), longArrayOf(0, 12), intArrayOf(0, 90))
        H.SELECT -> Pattern(listOf(P(CLICK, 0.7f)), longArrayOf(0, 18), intArrayOf(0, 150))
        H.STEP -> Pattern(listOf(P(TICK, 0.4f)), longArrayOf(0, 10), intArrayOf(0, 70))
        H.SEND -> Pattern(listOf(P(QUICK_RISE, 0.5f)), longArrayOf(0, 30), intArrayOf(0, 120))
        // marcar: sobe rápido e estala
        H.DONE -> Pattern(listOf(P(QUICK_RISE, 0.6f), P(CLICK, 1f)), longArrayOf(0, 28, 20, 24), intArrayOf(0, 90, 0, 220))
        // desmarcar: cai
        H.UNDONE -> Pattern(listOf(P(QUICK_FALL, 0.6f)), longArrayOf(0, 40), intArrayOf(0, 110))
        // rotina do dia fechada: sobe devagar, estala e um baque macio
        H.DAY_COMPLETE -> Pattern(
            listOf(P(SLOW_RISE, 1f), P(CLICK, 1f, 40), P(THUD, 0.7f, 110)),
            longArrayOf(0, 120, 40, 30, 90, 90), intArrayOf(0, 140, 0, 255, 0, 170),
        )
        // óculos escuros descendo: sobe e dois toquinhos
        H.VACATION_ON -> Pattern(listOf(P(QUICK_RISE, 0.8f), P(TICK, 0.6f, 120), P(TICK, 0.6f, 90)),
            longArrayOf(0, 40, 100, 14, 80, 14), intArrayOf(0, 160, 0, 90, 0, 90))
        H.VACATION_OFF -> Pattern(listOf(P(QUICK_FALL, 0.8f)), longArrayOf(0, 50), intArrayOf(0, 150))
        H.ERROR -> Pattern(listOf(P(THUD, 0.9f), P(THUD, 0.9f, 120)), longArrayOf(0, 60, 90, 60), intArrayOf(0, 210, 0, 210))
        // conquista: gira, sobe e estala
        H.BADGE -> Pattern(listOf(P(SPIN, 0.9f), P(QUICK_RISE, 1f, 60), P(CLICK, 1f, 40)),
            longArrayOf(0, 70, 50, 40, 30, 24), intArrayOf(0, 120, 0, 180, 0, 255))
        // subiu de nível: três subidas crescentes e um baque
        H.LEVEL_UP -> Pattern(
            listOf(P(QUICK_RISE, 0.5f), P(QUICK_RISE, 0.75f, 70), P(QUICK_RISE, 1f, 70), P(THUD, 1f, 120)),
            longArrayOf(0, 30, 60, 30, 60, 30, 100, 80), intArrayOf(0, 100, 0, 160, 0, 220, 0, 255),
        )
        // sequência: dois toquinhos e um estalo
        H.STREAK -> Pattern(listOf(P(TICK, 0.5f), P(TICK, 0.7f, 70), P(CLICK, 1f, 70)),
            longArrayOf(0, 12, 60, 12, 60, 24), intArrayOf(0, 90, 0, 130, 0, 230))
    }

    fun play(h: H) {
        if (!Prefs.uiSounds) return
        val v = vib ?: return
        if (!v.hasVibrator()) return
        val p = pattern(h)
        runCatching {
            if (Build.VERSION.SDK_INT >= 30 && supports(v, p)) {
                val c = VibrationEffect.startComposition()
                for (x in p.prims) c.addPrimitive(x.id, x.scale, x.delay)
                v.vibrate(c.compose())
            } else if (v.hasAmplitudeControl()) {
                v.vibrate(VibrationEffect.createWaveform(p.timings, p.amps, -1))
            } else {
                v.vibrate(VibrationEffect.createWaveform(p.timings, -1))
            }
        }
    }

    private fun supports(v: Vibrator, p: Pattern): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        // THUD e SPIN só existem no Android 12
        if (Build.VERSION.SDK_INT < 31 && p.prims.any { it.id == THUD || it.id == SPIN }) return false
        return v.areAllPrimitivesSupported(*p.prims.map { it.id }.toIntArray())
    }
}
