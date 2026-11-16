package com.dvdiso.sunbi

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Sons curtos da interface (gerados por tools/sounds.py). Baixos de propósito e desligáveis
 * em Ajustes. Usa o canal de "sons do sistema" (sonification), que respeita o modo silencioso.
 */
object Sfx {
    enum class S(val res: Int, val volume: Float) {
        POP(R.raw.pop, 0.7f),
        UNPOP(R.raw.unpop, 0.6f),
        TICK(R.raw.tick, 0.8f),
        CHIME(R.raw.chime, 0.8f),
        FOCUS_START(R.raw.focus_start, 0.8f),
        FOCUS_END(R.raw.focus_end, 0.9f),
    }

    private var pool: SoundPool? = null
    private val ids = HashMap<S, Int>()
    private val ready = HashSet<Int>()

    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(ready) { ready += id } }
        for (s in S.entries) ids[s] = p.load(ctx, s.res, 1)
        pool = p
    }

    fun play(s: S) {
        if (!Prefs.uiSounds) return
        val id = ids[s] ?: return
        if (synchronized(ready) { id !in ready }) return
        pool?.play(id, s.volume, s.volume, 1, 0, 1f)
    }
}
