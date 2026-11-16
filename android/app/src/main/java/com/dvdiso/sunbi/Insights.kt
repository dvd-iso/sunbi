package com.dvdiso.sunbi

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Leituras automáticas dos registros. São avisos gentis para a pessoa olhar o próprio plano,
 * nunca diagnóstico. As regras seguem o que a psicoeducação ensina a observar
 * (docs/METODOS.md): queda de sono com energia alta antes da mania, humor baixo persistente
 * e sono demais antes da depressão, sinais pessoais, doses esquecidas e ritmo irregular.
 */

enum class InsightKind { UP, DOWN, SLEEP, MEDS, RHYTHM, KUDOS }

data class Insight(val kind: InsightKind, val title: String, val body: String)

fun lastDays(end: String, n: Int): List<String> {
    val e = LocalDate.parse(end)
    return (n - 1 downTo 0).map { e.minusDays(it.toLong()).toString() }
}

private fun median(xs: List<Double>): Double? {
    if (xs.isEmpty()) return null
    val s = xs.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

/** Sono habitual: mediana dos 14 dias antes dos 2 últimos. Sem dados suficientes, 7,5 h. */
fun sleepBaseline(end: String): Double {
    val past = lastDays(end, 16).dropLast(2).mapNotNull { Repo.sleep(it) }
    return if (past.size >= 4) median(past)!! else 7.5
}

/**
 * Regularidade das âncoras num dia (ideia do Social Rhythm Metric): quantas âncoras foram
 * feitas a até 45 minutos do horário-alvo. Âncora marcada sem horário conta como feita.
 * Devolve (acertos, total) ou null se o dia não tem nenhuma âncora marcada.
 */
fun rhythm(day: String): Pair<Int, Int>? {
    val anchors = Repo.tasks().filter { it.anchor }
    if (anchors.isEmpty()) return null
    var marked = false
    var hits = 0
    for (a in anchors) {
        if (!Repo.done(day, a.id)) continue
        marked = true
        val at = Repo.doneAt(day, a.id)
        if (at == null || withinMinutes(a.time, at, 45)) hits++
    }
    return if (marked) hits to anchors.size else null
}

private fun withinMinutes(target: String, actual: String, tol: Int): Boolean {
    val t = runCatching { LocalTime.parse(target) }.getOrNull() ?: return true
    val a = runCatching { LocalTime.parse(actual) }.getOrNull() ?: return true
    var diff = abs(t.toSecondOfDay() - a.toSecondOfDay()) / 60
    if (diff > 12 * 60) diff = 24 * 60 - diff
    return diff <= tol
}

/** Desvio-padrão (minutos) do horário de acordar: quanto menor, mais regular. */
fun wakeSpreadMinutes(days: List<String>): Int? {
    val mins = days.mapNotNull { d -> Repo.wake(d)?.let { runCatching { LocalTime.parse(it).toSecondOfDay() / 60.0 }.getOrNull() } }
    if (mins.size < 3) return null
    val mean = mins.average()
    return sqrt(mins.sumOf { (it - mean) * (it - mean) } / mins.size).toInt()
}

/** Doses (tarefas de remédio) não marcadas. No dia de hoje só conta o que passou há mais de 1 hora. */
fun missedMeds(days: List<String>): Int {
    val meds = Repo.tasks().filter { it.med }
    val now = LocalTime.now().toSecondOfDay() / 60
    val today = today()
    var missed = 0
    for (d in days) for (m in meds) {
        // dia sem nenhum registro: o app pode só não ter sido aberto, então não conta
        if (d != today && !Repo.hasDay(d)) continue
        if (Repo.done(d, m.id)) continue
        if (d == today) {
            val t = runCatching { LocalTime.parse(m.time).toSecondOfDay() / 60 }.getOrNull() ?: continue
            if (now - t < 60) continue
        }
        missed++
    }
    return missed
}

fun medAdherence(days: List<String>): Int? {
    val meds = Repo.tasks().filter { it.med }
    val tracked = days.filter { Repo.hasDay(it) }
    if (meds.isEmpty() || tracked.isEmpty()) return null
    val total = meds.size * tracked.size
    val taken = tracked.sumOf { d -> meds.count { Repo.done(d, it.id) } }
    return 100 * taken / total
}

fun insights(day: String = today()): List<Insight> {
    val out = mutableListOf<Insight>()
    val d3 = lastDays(day, 3)
    val d7 = lastDays(day, 7)
    val base = sleepBaseline(day)
    val signs = Repo.signs().associateBy { it.id }

    // ── subida ──
    val upWhy = mutableListOf<String>()
    val h = d3.map { Repo.humor(it) }
    val s2 = lastDays(day, 2).map { Repo.sleep(it) }
    val shortSleep = s2.all { it != null && (it < 6.0 || it <= base - 2.0) }
    val energyHigh = d3.any { (Repo.level(it, "energy") ?: 0) >= 4 }
    if (h.any { (it ?: 0) >= 2 }) upWhy += "humor bem acima do estável"
    else if (h.count { (it ?: 0) >= 1 } >= 3) upWhy += "humor acima do estável há 3 dias"
    if (shortSleep && (energyHigh || h.any { (it ?: 0) >= 1 })) upWhy += "menos sono com energia alta"
    val upSigns = d3.flatMap { Repo.signsOn(it) }.toSet().filter { signs[it]?.pole == "up" }
    if (upSigns.size >= 2) upWhy += "${upSigns.size} sinais de subida do seu plano"
    if (upWhy.isNotEmpty()) {
        out += Insight(
            InsightKind.UP, "Sinais de subida",
            "Percebi ${upWhy.joinToString(", ")}. Vale abrir seu plano para quando o humor sobe e, se combinou isso com sua equipe, avisar seu psiquiatra.",
        )
    }

    // ── descida ──
    val downWhy = mutableListOf<String>()
    val h7 = d7.map { Repo.humor(it) }
    if (h.count { (it ?: 0) <= -2 } >= 2) downWhy += "humor bem baixo em 2 dos últimos 3 dias"
    else if (h7.count { (it ?: 0) <= -1 } >= 5) downWhy += "humor baixo na maior parte da semana"
    val longSleep = d3.mapNotNull { Repo.sleep(it) }.let { s -> s.size == 3 && s.all { it >= 10.0 || it >= base + 2.5 } }
    if (longSleep) downWhy += "sono bem acima do seu normal"
    val downSigns = d3.flatMap { Repo.signsOn(it) }.toSet().filter { signs[it]?.pole == "down" }
    if (downSigns.size >= 2) downWhy += "${downSigns.size} sinais de descida do seu plano"
    if (downWhy.isNotEmpty()) {
        out += Insight(
            InsightKind.DOWN, "Sinais de descida",
            "Percebi ${downWhy.joinToString(", ")}. Seu plano para quando o humor desce pode ajudar. Uma coisa pequena hoje já conta.",
        )
    }

    // ── sono curto sem outros sinais ──
    if (upWhy.isEmpty() && shortSleep) {
        out += Insight(
            InsightKind.SLEEP, "Duas noites curtas",
            "Você dormiu menos que o seu normal (${"%.1f".format(base)} h) nas duas últimas noites. Perder sono pode desregular o humor: hoje vale proteger o horário de deitar.",
        )
    }

    // ── remédios ──
    val missed = missedMeds(d3)
    if (missed >= 2) {
        out += Insight(
            InsightKind.MEDS, "Doses sem marcar",
            "$missed doses dos últimos 3 dias ficaram sem marcar. Se esqueceu alguma, siga a orientação do seu médico: não dobre a dose por conta própria.",
        )
    }

    // ── ritmo ──
    val r = d7.mapNotNull { rhythm(it) }
    if (r.size >= 4) {
        val avg = r.sumOf { it.first.toDouble() / it.second } / r.size
        if (avg < 0.6) {
            out += Insight(
                InsightKind.RHYTHM, "Ritmo irregular",
                "Suas âncoras variaram bastante nesta semana. Escolha só uma para firmar nos próximos dias, de preferência o horário de levantar.",
            )
        }
    }

    // ── reconhecimento (sem sequências que zeram) ──
    if (out.isEmpty()) {
        val n = d7.count { Repo.checkedIn(it) }
        if (n >= 4) {
            out += Insight(
                InsightKind.KUDOS, "Você registrou $n dos últimos 7 dias",
                "Esse histórico ajuda muito nas consultas. Dias sem registro não apagam nada.",
            )
        }
    }
    return out
}
