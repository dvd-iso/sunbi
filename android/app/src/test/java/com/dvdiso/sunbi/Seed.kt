package com.dvdiso.sunbi

import java.time.LocalDate

/** Dados de exemplo: 14 dias de uma pessoa saindo de uma fase baixa e com o sono encurtando no fim. */
object Seed {
    fun clear() {
        val snap = Repo.snapshot().getJSONObject("entries")
        for (k in snap.keys()) if (k.matches(Regex("""\d{4}-\d{2}-\d{2}/.*"""))) Repo.set(k, null)
    }

    fun twoWeeks(end: String = today()) {
        val e = LocalDate.parse(end)
        val humor = listOf(-2, -2, -1, -1, -1, 0, 0, 0, 1, 0, 0, 1, 1, 2)
        val beds = listOf("23:10", "23:40", "23:00", "22:50", "23:20", "22:40", "22:30", "22:45", "23:30", "22:40", "23:50", "00:40", "01:30", "02:10")
        val wakes = listOf("09:30", "09:10", "08:40", "08:00", "07:40", "07:10", "07:00", "07:05", "07:20", "07:00", "07:00", "06:50", "06:30", "06:40")
        for (i in 0 until 14) {
            val d = e.minusDays((13 - i).toLong()).toString()
            Repo.set("$d/humor", humor[i])
            Repo.setSleepTimes(d, beds[i], wakes[i])
            Repo.setLevel(d, "energy", (3 + humor[i]).coerceIn(1, 5))
            if (i >= 12) Repo.setLevel(d, "irrit", 1)
            for (t in Repo.tasks()) {
                val skip = (i + t.id.length) % 5 == 0
                if (!skip && d != end) Repo.set("$d/done/${t.id}", if (t.anchor) shift(t.time, (i % 3) * 20) else true)
            }
        }
        Repo.setStrSet("$end/signs", setOf("u_sleep", "u_projects"))
        Repo.setStrSet("$end/events", setOf("late", "caffeine"))
        Repo.set("$end/note", "Muitas ideias hoje, difícil desligar à noite.")
        // hoje: as primeiras tarefas da manhã feitas
        for (t in Repo.tasks().take(3)) Repo.set("$end/done/${t.id}", t.time)
    }

    private fun shift(hm: String, minutes: Int): String {
        val t = java.time.LocalTime.parse(hm).plusMinutes(minutes.toLong())
        return "%02d:%02d".format(t.hour, t.minute)
    }
}
