package com.dvdiso.sunflowerclocks

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val MOODS = listOf("Muito baixo", "Baixo", "Estável", "Agitado", "Muito agitado")

private val PT = Locale.forLanguageTag("pt-BR")
private val LONG = DateTimeFormatter.ofPattern("EEEE, dd/MM", PT)
private val SHORT = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT)

fun niceDay(day: String): String = LocalDate.parse(day).format(LONG)

/** Resumo de 7 dias terminando em [end], em texto para mandar ao psicólogo. */
fun weeklyReport(end: String): String {
    val last = LocalDate.parse(end)
    val days = (6 downTo 0).map { last.minusDays(it.toLong()).toString() }
    val tasks = Repo.tasks()
    val meds = tasks.filter { it.med }
    val sb = StringBuilder()
    sb.append("Sunflower Clocks: ${LocalDate.parse(days.first()).format(SHORT)} a ${last.format(SHORT)}\n")
    var sum = 0
    var count = 0
    for (d in days) {
        sb.append("\n${niceDay(d)}: ")
        if (!Repo.hasDay(d)) {
            sb.append("sem registro\n")
            continue
        }
        val pct = Repo.percent(d)
        sum += pct
        count++
        val mood = Repo.mood(d)?.let { MOODS.getOrNull(it - 1) } ?: "não registrado"
        val sleep = Repo.sleep(d)?.let { "${it}h" } ?: "–"
        sb.append("$pct% concluído · humor: $mood · sono: $sleep\n")
        if (meds.isNotEmpty()) {
            sb.append("  Remédios: ")
            sb.append(meds.joinToString(", ") { "${it.time} ${if (Repo.done(d, it.id)) "✓" else "✗"}" })
            sb.append("\n")
        }
        val missed = tasks.filter { !Repo.done(d, it.id) }
        if (missed.isNotEmpty() && missed.size < tasks.size) {
            sb.append("  Não feito: ${missed.joinToString("; ") { it.label }}\n")
        }
        val note = Repo.note(d)
        if (note.isNotBlank()) sb.append("  Anotação: ${note.replace('\n', ' ')}\n")
    }
    if (count > 0) sb.append("\nMédia de conclusão: ${sum / count}% em $count dia(s) registrados.")
    return sb.toString()
}
