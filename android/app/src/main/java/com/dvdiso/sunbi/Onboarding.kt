package com.dvdiso.sunbi

import java.time.LocalTime

/**
 * Respostas do tour inicial. Poucas perguntas, cada uma vira uma parte da rotina:
 * horários de dormir (âncoras), remédios, o que ocupa o dia, refeições e extras.
 */
data class Answers(
    val name: String = "",
    /** Nome que a pessoa quer dar à assistente (vazio = "Assistente"). */
    val assistant: String = "",
    /** "bipolar", "tdah" ou "both". */
    val focus: String = "both",
    val wake: String = "07:00",
    val bed: String = "22:30",
    /** Quanto a folga pode atrasar o acordar/deitar (minutos): 0, 60 ou 120. */
    val weekendDelta: Int = 60,
    /** "none", "am", "pm" ou "both". */
    val meds: String = "none",
    val medAm: String = "07:15",
    val medPm: String = "22:00",
    /** "work", "study", "both" ou "home". */
    val dayKind: String = "work",
    val start: String = "09:00",
    val lunch: String = "12:30",
    val dinner: String = "19:30",
    /** "contact", "water", "pause", "move", "dim". */
    val extras: Set<String> = setOf("contact", "water", "move", "dim"),
)

val EXTRAS = listOf(
    "contact" to "Falar com alguém todo dia",
    "move" to "Movimento ou luz do dia",
    "water" to "Lembrete de água",
    "pause" to "Pausa à tarde",
    "dim" to "Luz baixa antes de dormir",
)

/** Monta as duas rotinas a partir das respostas. Puro, para dar para testar. */
object RoutineBuilder {
    fun plus(hm: String, minutes: Int): String {
        val t = runCatching { LocalTime.parse(hm) }.getOrDefault(LocalTime.NOON).plusMinutes(minutes.toLong())
        return "%02d:%02d".format(t.hour, t.minute)
    }

    private fun mins(hm: String): Int = runCatching { LocalTime.parse(hm).toSecondOfDay() / 60 }.getOrDefault(12 * 60)

    fun startLabel(kind: String): String = when (kind) {
        "work" -> "Começar o trabalho"
        "study" -> "Começar os estudos"
        "both" -> "Começar o trabalho ou os estudos"
        else -> "Começar a primeira tarefa do dia"
    }

    fun weekdays(a: Answers): List<Task> {
        val out = ArrayList<Task>()
        out += Task("wake", a.wake, "Levantar da cama", anchor = true, step = "Sentar na cama e abrir a cortina")
        if (a.meds == "am" || a.meds == "both") out += Task("medam", a.medAm, "Remédio da manhã", med = true, step = "Pegar o copo d'água")
        out += Task("breakfast", plus(a.wake, 30), "Café da manhã", step = "Ir até a cozinha")
        if ("contact" in a.extras) {
            var c = maxOf(mins(plus(a.wake, 60)), mins(a.start) - 30)
            if (c >= mins(a.start)) c = mins(a.start) + 60
            out += Task("contact", plus("00:00", c), "Falar com alguém (mensagem, ligação ou pessoalmente)", anchor = true, step = "Mandar um \"bom dia\"")
        }
        out += Task("start", a.start, startLabel(a.dayKind), anchor = true, step = "Abrir só a primeira coisa da lista")
        if ("water" in a.extras) out += Task("water1", plus(a.start, 90), "Beber água")
        out += Task("lunch", a.lunch, "Almoço: pare e coma", step = "Levantar da cadeira")
        if ("pause" in a.extras) out += Task("pause", plus(a.lunch, 150), "Pausa e lanche", step = "Levantar e olhar pela janela")
        if ("move" in a.extras) out += Task("move", plus(a.dinner, -120), "Movimento leve ou luz do dia", step = "Calçar o tênis")
        out += Task("dinner", a.dinner, "Jantar", anchor = true, step = "Pôr a mesa ou esquentar a comida")
        if ("dim" in a.extras) out += Task("dim", plus(a.bed, -60), "Luz baixa e telas no modo noturno", step = "Apagar a luz principal")
        if (a.meds == "pm" || a.meds == "both") out += Task("medpm", a.medPm, "Remédio da noite", med = true, step = "Pegar o copo d'água")
        out += Task("bed", a.bed, "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama")
        return out.sortedBy { it.time }
    }

    /** Folga: mais solta, mas as âncoras perto das de semana. Remédios ficam no mesmo horário. */
    fun offDays(a: Answers): List<Task> {
        val d = a.weekendDelta
        val wake = plus(a.wake, d)
        val bed = plus(a.bed, d)
        val out = ArrayList<Task>()
        out += Task("wake", wake, "Levantar da cama", anchor = true, step = "Abrir a cortina e beber água")
        if (a.meds == "am" || a.meds == "both") out += Task("medam", a.medAm, "Remédio da manhã", med = true, step = "Pegar o copo d'água")
        out += Task("breakfast", plus(wake, 30), "Café da manhã sem pressa")
        if ("contact" in a.extras) out += Task("contact", plus(wake, 120), "Falar com alguém ou sair de casa", anchor = true, step = "Mandar uma mensagem")
        out += Task("lunch", plus(a.lunch, 30), "Almoço")
        out += Task("fun", "15:00", "Algo que você gosta de fazer", step = "Escolher uma coisa só")
        if ("move" in a.extras) out += Task("move", plus(a.dinner, -90), "Luz do dia ou caminhada", step = "Calçar o tênis")
        out += Task("dinner", plus(a.dinner, 30), "Jantar", anchor = true)
        if ("dim" in a.extras) out += Task("dim", plus(bed, -60), "Luz baixa e telas no modo noturno", step = "Apagar a luz principal")
        if (a.meds == "pm" || a.meds == "both") out += Task("medpm", a.medPm, "Remédio da noite", med = true, step = "Pegar o copo d'água")
        out += Task("bed", bed, "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama")
        return out.sortedBy { it.time }
    }

    /** Guarda as respostas e as duas rotinas. */
    fun apply(a: Answers) {
        Prefs.userName = a.name
        Prefs.focusArea = a.focus
        if (a.assistant.isNotBlank()) Prefs.aiName = a.assistant
        if (Prefs.reportName.isBlank()) Prefs.reportName = a.name
        Repo.setTasks(weekdays(a))
        Repo.setOffTasks(offDays(a))
    }

    /** Texto que abre a conversa com a assistente depois do tour. */
    fun kickoff(a: Answers): String {
        val meds = when (a.meds) { "am" -> "de manhã"; "pm" -> "à noite"; "both" -> "de manhã e à noite"; else -> "nenhum em horário fixo" }
        val day = when (a.dayKind) { "work" -> "trabalho"; "study" -> "estudo"; "both" -> "trabalho e estudo"; else -> "fico em casa" }
        return "Acabei de montar minha rotina no tour do app. Acordo às ${a.wake} e deito às ${a.bed}; " +
            "na folga, até ${a.weekendDelta} min mais tarde. Remédios: $meds. Meu dia: $day, começando às ${a.start}. " +
            "Olhe as duas rotinas e, se fizer sentido, sugira até 3 ajustes pequenos explicando o porquê. Só aplique se eu concordar."
    }
}
