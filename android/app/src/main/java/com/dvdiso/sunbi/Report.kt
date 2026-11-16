package com.dvdiso.sunbi

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rótulos da escala de humor -3..+3 (NIMH Life Chart: grave, moderado, leve). */
val HUMOR = mapOf(
    -3 to "Muito para baixo", -2 to "Para baixo", -1 to "Um pouco para baixo",
    0 to "Estável",
    1 to "Um pouco acelerado", 2 to "Acelerado", 3 to "Muito acelerado",
)

private val PT = Locale.forLanguageTag("pt-BR")
private val LONG = DateTimeFormatter.ofPattern("EEEE, dd/MM", PT)
private val SHORT = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT)
private val WD = DateTimeFormatter.ofPattern("EEE dd/MM", PT)

fun niceDay(day: String): String = LocalDate.parse(day).format(LONG).replaceFirstChar { it.uppercase() }
fun shortDay(day: String): String = LocalDate.parse(day).format(WD)

private fun fmt(x: Double) = "%.1f".format(PT, x)

/** Resumo dos últimos [n] dias até [end], em texto para levar à consulta. */
fun report(end: String, n: Int = 7): String {
    val days = lastDays(end, n)
    val tasks = Repo.tasks()
    val meds = tasks.filter { it.med }
    val signs = Repo.signs().associateBy { it.id }
    val events = EVENTS.toMap()
    val sb = StringBuilder()
    sb.append("SunBi · ${LocalDate.parse(days.first()).format(SHORT)} a ${LocalDate.parse(end).format(SHORT)}\n")

    val hum = days.mapNotNull { Repo.humor(it) }
    val sl = days.mapNotNull { Repo.sleep(it) }
    sb.append("\nRESUMO\n")
    sb.append("Dias com registro: ${days.count { Repo.hasDay(it) }} de $n\n")
    if (hum.isNotEmpty()) sb.append("Humor (-3 a +3): mín ${hum.min()}, máx ${hum.max()}, média ${fmt(hum.average())}\n")
    if (sl.isNotEmpty()) sb.append("Sono: média ${fmt(sl.average())} h (de ${fmt(sl.min())} a ${fmt(sl.max())} h)\n")
    wakeSpreadMinutes(days)?.let { sb.append("Variação no horário de acordar: ±$it min\n") }
    medAdherence(days)?.let { sb.append("Remédios marcados: $it%\n") }
    val r = days.mapNotNull { rhythm(it) }
    if (r.isNotEmpty()) {
        sb.append("Âncoras no horário (±45 min): ${100 * r.sumOf { it.first } / r.sumOf { it.second }}%\n")
    }

    sb.append("\nDIA A DIA\n")
    for (d in days) {
        sb.append("\n${shortDay(d)}: ")
        if (!Repo.hasDay(d)) {
            sb.append("sem registro\n")
            continue
        }
        val parts = mutableListOf<String>()
        Repo.humor(d)?.let { parts += "humor ${if (it > 0) "+$it" else "$it"} (${HUMOR[it]})" }
        Repo.sleep(d)?.let { s ->
            val times = if (Repo.bed(d) != null && Repo.wake(d) != null) " ${Repo.bed(d)}→${Repo.wake(d)}" else ""
            parts += "sono ${fmt(s)} h$times"
        }
        Repo.level(d, "energy")?.let { parts += "energia $it/5" }
        Repo.level(d, "irrit")?.let { if (it > 0) parts += "irritação $it/3" }
        Repo.level(d, "anx")?.let { if (it > 0) parts += "ansiedade $it/3" }
        Repo.level(d, "focus")?.let { parts += "foco $it/5" }
        parts += "rotina ${Repo.percent(d)}%"
        sb.append(parts.joinToString(" · ")).append("\n")
        if (meds.isNotEmpty()) {
            sb.append("  Remédios: ")
            sb.append(meds.joinToString(", ") { "${it.time} ${if (Repo.done(d, it.id)) "✓" else "✗"}" })
            sb.append("\n")
        }
        val sg = Repo.signsOn(d).mapNotNull { signs[it]?.label }
        if (sg.isNotEmpty()) sb.append("  Sinais: ${sg.joinToString("; ")}\n")
        val ev = Repo.eventsOn(d).mapNotNull { events[it] }
        if (ev.isNotEmpty()) sb.append("  Acontecimentos: ${ev.joinToString(", ")}\n")
        val note = Repo.note(d)
        if (note.isNotBlank()) sb.append("  Anotação: ${note.replace('\n', ' ')}\n")
    }
    sb.append("\nRegistrado pelo próprio paciente no app SunBi. Não é avaliação clínica.")
    return sb.toString()
}
