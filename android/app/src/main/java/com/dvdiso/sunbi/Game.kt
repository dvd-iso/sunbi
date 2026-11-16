package com.dvdiso.sunbi

import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Gamificação gentil, no espírito do Duolingo mas sem punição: pontos (XP) por tarefas e
 * check-ins, níveis com nome de girassol, sequência de dias e conquistas.
 *
 * Tudo é derivado dos registros que já existem (nada novo para sincronizar), então os três
 * aparelhos mostram os mesmos números. Dias de folga sem registro não quebram a sequência.
 */
object Game {
    data class Badge(val id: String, val emoji: String, val title: String, val desc: String)

    val BADGES = listOf(
        Badge("first_checkin", "🌱", "Primeiro registro", "Fez o primeiro check-in"),
        Badge("checkins_7", "📒", "Semana registrada", "7 dias com check-in"),
        Badge("checkins_30", "📚", "Mês registrado", "30 dias com check-in"),
        Badge("streak_3", "🔥", "Três dias seguidos", "Sequência de 3 dias"),
        Badge("streak_7", "☀️", "Uma semana seguida", "Sequência de 7 dias"),
        Badge("streak_30", "🌞", "Um mês seguido", "Sequência de 30 dias"),
        Badge("full_1", "✅", "Rotina completa", "Um dia com todas as tarefas feitas"),
        Badge("full_10", "🏅", "Dez dias completos", "10 dias com a rotina inteira"),
        Badge("meds_7", "💊", "Remédios em dia", "7 dias com todos os remédios marcados"),
        Badge("rhythm_1", "⏰", "Ritmo certinho", "Todas as âncoras no horário num dia"),
        Badge("rhythm_7", "🎯", "Ritmo firme", "7 dias com as âncoras no horário"),
        Badge("plan", "🛟", "Plano pronto", "Plano de segurança e um contato preenchidos"),
        Badge("level_5", "🌻", "Girassol", "Chegou ao nível 5"),
        Badge("level_10", "🌾", "Campo de girassóis", "Chegou ao nível 10"),
    )

    private val LEVEL_NAMES = listOf("Semente", "Broto", "Muda", "Botão", "Girassol", "Girassol alto", "Girassol forte", "Girassol radiante", "Girassol pleno", "Campo de girassóis")

    fun levelName(level: Int): String = LEVEL_NAMES[(level - 1).coerceIn(0, LEVEL_NAMES.lastIndex)] + if (level > LEVEL_NAMES.size) " ${level - LEVEL_NAMES.size + 1}" else ""

    /** XP acumulado para chegar ao nível [n]: 0, 100, 300, 600, 1000... */
    fun xpForLevel(n: Int): Int = 50 * n * (n - 1)

    fun levelFor(xp: Int): Int = ((1 + sqrt(1.0 + 8.0 * xp / 100.0)) / 2).toInt().coerceAtLeast(1)

    data class Stats(
        val xp: Int,
        val level: Int,
        val levelName: String,
        val levelStart: Int,
        val levelEnd: Int,
        val streak: Int,
        val bestStreak: Int,
        val todayXp: Int,
        val checkins: Int,
        val fullDays: Int,
        val badges: Set<String>,
        /** Meta do dia: check-in feito e todas as âncoras marcadas. */
        val goalCheckin: Boolean,
        val goalAnchors: Boolean,
    ) {
        val progress: Float get() = if (levelEnd == levelStart) 1f else (xp - levelStart).toFloat() / (levelEnd - levelStart)
    }

    /** Pontos de um dia: 10 por tarefa, 20 pelo check-in, 30 pela rotina inteira, 15 pelas âncoras no horário. */
    fun dayXp(day: String): Int {
        val tasks = Repo.tasksFor(day)
        val done = tasks.count { Repo.done(day, it.id) }
        var xp = 10 * done
        if (Repo.checkedIn(day)) xp += 20
        if (tasks.isNotEmpty() && done == tasks.size) xp += 30
        rhythm(day)?.let { (h, n) -> if (n > 0 && h == n) xp += 15 }
        return xp
    }

    /** O dia conta para a sequência: check-in ou pelo menos uma tarefa feita. */
    fun counts(day: String): Boolean =
        Repo.checkedIn(day) || Repo.tasksFor(day).any { Repo.done(day, it.id) }

    /**
     * Sequência atual: dias seguidos com atividade, terminando hoje ou ontem (hoje ainda pode
     * vir). Dia de folga sem nada não conta nem quebra: descanso faz parte.
     */
    fun streak(today: String): Int {
        var d = LocalDate.parse(today)
        var n = 0
        var first = true
        for (i in 0 until 400) {
            val s = d.toString()
            when {
                counts(s) -> n++
                first -> {} // hoje ainda não começou: olha ontem
                Repo.isOff(s) -> {}
                else -> break
            }
            first = false
            d = d.minusDays(1)
        }
        return n
    }

    fun stats(today: String = today()): Stats {
        val days = Repo.recordedDays().filter { it <= today }.sorted()
        var xp = 0
        var checkins = 0
        var fullDays = 0
        var medDays = 0
        var rhythmDays = 0
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days) {
            xp += dayXp(d)
            if (Repo.checkedIn(d)) checkins++
            val tasks = Repo.tasksFor(d)
            if (tasks.isNotEmpty() && tasks.all { Repo.done(d, it.id) }) fullDays++
            val meds = tasks.filter { it.med }
            if (meds.isNotEmpty() && meds.all { Repo.done(d, it.id) }) medDays++
            rhythm(d)?.let { (h, n) -> if (h == n) rhythmDays++ }
            // melhor sequência da história (mesma regra: folga sem nada não quebra)
            if (counts(d)) {
                val ld = LocalDate.parse(d)
                val p = prev
                run = if (p == null || gapIsOff(p, ld)) run + 1 else 1
                prev = ld
                if (run > best) best = run
            }
        }
        val streak = streak(today)
        if (streak > best) best = streak
        val level = levelFor(xp)
        val anchors = Repo.tasksFor(today).filter { it.anchor }
        val planReady = listOf("cope", "distract", "safe", "reasons").count { Repo.planList(it).isNotEmpty() } >= 2 && Repo.contacts().isNotEmpty()
        val badges = HashSet<String>()
        if (checkins >= 1) badges += "first_checkin"
        if (checkins >= 7) badges += "checkins_7"
        if (checkins >= 30) badges += "checkins_30"
        if (best >= 3) badges += "streak_3"
        if (best >= 7) badges += "streak_7"
        if (best >= 30) badges += "streak_30"
        if (fullDays >= 1) badges += "full_1"
        if (fullDays >= 10) badges += "full_10"
        if (medDays >= 7) badges += "meds_7"
        if (rhythmDays >= 1) badges += "rhythm_1"
        if (rhythmDays >= 7) badges += "rhythm_7"
        if (planReady) badges += "plan"
        if (level >= 5) badges += "level_5"
        if (level >= 10) badges += "level_10"
        return Stats(
            xp, level, levelName(level), xpForLevel(level), xpForLevel(level + 1),
            streak, best, dayXp(today), checkins, fullDays, badges,
            goalCheckin = Repo.checkedIn(today),
            goalAnchors = anchors.isNotEmpty() && anchors.all { Repo.done(today, it.id) },
        )
    }

    /** Entre dois dias com atividade só houve dias de folga (ou nenhum dia). */
    private fun gapIsOff(a: LocalDate, b: LocalDate): Boolean {
        var d = a.plusDays(1)
        while (d.isBefore(b)) {
            if (!Repo.isOff(d.toString())) return false
            d = d.plusDays(1)
        }
        return true
    }

    fun badge(id: String): Badge? = BADGES.firstOrNull { it.id == id }
}
