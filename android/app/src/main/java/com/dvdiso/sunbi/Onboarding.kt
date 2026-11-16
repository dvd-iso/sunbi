package com.dvdiso.sunbi

import java.time.LocalTime

/**
 * Respostas do tour inicial. Cada pergunta vira uma parte da rotina ou um ajuste do app:
 * o que a pessoa quer cuidar, horários de dormir (âncoras), folga, remédios, o que ocupa o dia,
 * refeições, extras, lembretes e se quer a assistente de IA. Nada aqui supõe um diagnóstico.
 */
data class Answers(
    val name: String = "",
    /** O que trouxe a pessoa: ids de [CONCERNS]. Vazio = só organizar a rotina. */
    val concerns: Set<String> = emptySet(),
    val wake: String = "07:00",
    val bed: String = "22:30",
    /** Sábado e domingo usam a rotina de folga. */
    val offWeekends: Boolean = true,
    /** Feriados nacionais usam a rotina de folga. */
    val offHolidays: Boolean = true,
    /** Quanto a folga pode atrasar o acordar/deitar (minutos): 0, 60 ou 120. */
    val weekendDelta: Int = 60,
    /** Remédios em horário fixo: "am", "mid", "pm". */
    val meds: Set<String> = emptySet(),
    val medAm: String = "07:15",
    val medMid: String = "13:00",
    val medPm: String = "22:00",
    /** "work", "study", "both", "care" (casa e família) ou "home" (sem horário fixo). */
    val dayKind: String = "work",
    val start: String = "09:00",
    val breakfast: Boolean = true,
    val lunch: String = "12:30",
    val dinner: String = "19:30",
    /** ids de [EXTRAS]. */
    val extras: Set<String> = setOf("contact", "water", "move", "dim"),
    val alarms: Boolean = true,
    val checkinReminder: Boolean = true,
    val sounds: Boolean = true,
    /** Quer usar a assistente de IA (dá para ligar ou desligar depois em Ajustes). */
    val ai: Boolean = false,
    /** Nome que a pessoa quer dar à assistente (vazio = "Assistente"). */
    val assistant: String = "",
) : java.io.Serializable {
    /** Valor antigo de [Prefs.focusArea], que outras partes ainda leem. */
    val focus: String
        get() = when {
            "bipolar" in concerns && "tdah" in concerns -> "both"
            "bipolar" in concerns -> "bipolar"
            "tdah" in concerns -> "tdah"
            else -> "general"
        }
}

/** O que a pessoa quer cuidar com o app (pode marcar vários, ou nenhum). */
val CONCERNS = listOf(
    "bipolar" to "Bipolaridade",
    "tdah" to "TDAH",
    "depression" to "Depressão",
    "anxiety" to "Ansiedade",
    "sleep" to "Sono",
    "routine" to "Só quero organizar a rotina",
)

val EXTRAS = listOf(
    "contact" to "Falar com alguém todo dia",
    "move" to "Movimento ou luz do dia",
    "water" to "Lembrete de água",
    "hygiene" to "Banho e higiene",
    "pause" to "Pausa à tarde",
    "tidy" to "Arrumar um cantinho",
    "fun" to "Um tempo de lazer",
    "plan" to "Olhar o dia de amanhã",
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
        "care" -> "Começar as tarefas da casa"
        else -> "Começar a primeira tarefa do dia"
    }

    /** Soma minutos sem atravessar a meia-noite: o que passaria vira 23:55 (continua sendo "hoje à noite"). */
    fun plusSameDay(hm: String, minutes: Int): String =
        if (minutes > 0 && mins(hm) + minutes >= 24 * 60) "23:55" else plus(hm, minutes)

    fun weekdays(a0: Answers): List<Task> {
        // "começar o dia" antes de levantar não faz sentido: vale uma hora depois de acordar
        val a = if (mins(a0.start) <= mins(a0.wake)) a0.copy(start = plusSameDay(a0.wake, 60)) else a0
        val out = ArrayList<Task>()
        out += Task("wake", a.wake, "Levantar da cama", anchor = true, step = "Sentar na cama e abrir a cortina")
        if ("am" in a.meds) out += Task("medam", a.medAm, "Remédio da manhã", med = true, step = "Pegar o copo d'água")
        if ("hygiene" in a.extras) out += Task("hygiene", plus(a.wake, 10), "Banho e escovar os dentes", step = "Ir até o banheiro")
        if (a.breakfast) out += Task("breakfast", plus(a.wake, 30), "Café da manhã", step = "Ir até a cozinha")
        if ("contact" in a.extras) {
            var c = maxOf(mins(plus(a.wake, 60)), mins(a.start) - 30)
            if (c >= mins(a.start)) c = mins(a.start) + 60
            out += Task("contact", plus("00:00", c), "Falar com alguém (mensagem, ligação ou pessoalmente)", anchor = true, step = "Mandar um \"bom dia\"")
        }
        out += Task("start", a.start, startLabel(a.dayKind), anchor = true, step = "Abrir só a primeira coisa da lista")
        if ("water" in a.extras) out += Task("water1", plus(a.start, 90), "Beber água")
        out += Task("lunch", a.lunch, "Almoço: pare e coma", step = "Levantar da cadeira")
        if ("mid" in a.meds) out += Task("medmid", a.medMid, "Remédio da tarde", med = true, step = "Pegar o copo d'água")
        if ("pause" in a.extras) out += Task("pause", plus(a.lunch, 150), "Pausa e lanche", step = "Levantar e olhar pela janela")
        if ("move" in a.extras) out += Task("move", plus(a.dinner, -120), "Movimento leve ou luz do dia", step = "Calçar o tênis")
        if ("tidy" in a.extras) out += Task("tidy", plus(a.dinner, -45), "Arrumar um cantinho por 10 minutos", step = "Escolher um lugar só")
        out += Task("dinner", a.dinner, "Jantar", anchor = true, step = "Pôr a mesa ou esquentar a comida")
        if ("fun" in a.extras) out += Task("fun", plus(a.dinner, 60), "Um tempo para algo que você gosta", step = "Escolher uma coisa só")
        if ("dim" in a.extras) out += Task("dim", plus(a.bed, -60), "Luz baixa e telas no modo noturno", step = "Apagar a luz principal")
        if ("plan" in a.extras) out += Task("plan", plus(a.bed, -20), "Olhar o dia de amanhã", step = "Separar a primeira coisa de amanhã")
        if ("pm" in a.meds) out += Task("medpm", a.medPm, "Remédio da noite", med = true, step = "Pegar o copo d'água")
        out += Task("bed", a.bed, "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama")
        return out.sortedBy { it.time }
    }

    /** Folga: mais solta, mas as âncoras perto das de semana. Remédios ficam no mesmo horário. */
    fun offDays(a: Answers): List<Task> {
        val d = a.weekendDelta
        val wake = plus(a.wake, d)
        // deitar mais tarde na folga não pode cair no dia seguinte (o alarme tocaria na noite errada);
        // quem já deita depois da meia-noite mantém o horário
        val bed = if (mins(a.bed) >= 12 * 60) plusSameDay(a.bed, d) else plus(a.bed, d)
        val out = ArrayList<Task>()
        out += Task("wake", wake, "Levantar da cama", anchor = true, step = "Abrir a cortina e beber água")
        if ("am" in a.meds) out += Task("medam", a.medAm, "Remédio da manhã", med = true, step = "Pegar o copo d'água")
        if ("hygiene" in a.extras) out += Task("hygiene", plus(wake, 10), "Banho e escovar os dentes", step = "Ir até o banheiro")
        if (a.breakfast) out += Task("breakfast", plus(wake, 30), "Café da manhã sem pressa")
        if ("contact" in a.extras) out += Task("contact", plus(wake, 120), "Falar com alguém ou sair de casa", anchor = true, step = "Mandar uma mensagem")
        out += Task("lunch", plus(a.lunch, 30), "Almoço")
        if ("mid" in a.meds) out += Task("medmid", a.medMid, "Remédio da tarde", med = true, step = "Pegar o copo d'água")
        out += Task("fun", "15:00", "Algo que você gosta de fazer", step = "Escolher uma coisa só")
        if ("move" in a.extras) out += Task("move", plus(a.dinner, -90), "Luz do dia ou caminhada", step = "Calçar o tênis")
        out += Task("dinner", plus(a.dinner, 30), "Jantar", anchor = true)
        if ("dim" in a.extras) out += Task("dim", plus(bed, -60), "Luz baixa e telas no modo noturno", step = "Apagar a luz principal")
        if ("pm" in a.meds) out += Task("medpm", a.medPm, "Remédio da noite", med = true, step = "Pegar o copo d'água")
        out += Task("bed", bed, "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama")
        return out.sortedBy { it.time }
    }

    /** Guarda as respostas, os ajustes escolhidos e as duas rotinas. */
    fun apply(a: Answers) {
        Prefs.userName = a.name
        Prefs.focusArea = a.focus
        Prefs.concerns = a.concerns
        Prefs.aiEnabled = a.ai
        if (a.assistant.isNotBlank()) Prefs.aiName = a.assistant
        if (Prefs.reportName.isBlank()) Prefs.reportName = a.name
        // antes das rotinas: os alarmes são agendados quando elas são gravadas
        Prefs.alarmsEnabled = a.alarms
        Prefs.checkinReminder = a.checkinReminder
        Prefs.uiSounds = a.sounds
        Repo.set("off/weekends", a.offWeekends)
        Repo.set("off/holidays", a.offHolidays)
        Repo.setTasks(weekdays(a))
        Repo.setOffTasks(offDays(a))
    }

    /** Texto que abre a conversa com a assistente depois do tour. */
    fun kickoff(a: Answers): String {
        val names = mapOf("am" to "de manhã", "mid" to "à tarde", "pm" to "à noite")
        val meds = a.meds.mapNotNull { names[it] }.joinToString(", ").ifEmpty { "nenhum em horário fixo" }
        val day = when (a.dayKind) { "work" -> "trabalho"; "study" -> "estudo"; "both" -> "trabalho e estudo"; "care" -> "cuido da casa e da família"; else -> "sem horário fixo" }
        return "Acabei de montar minha rotina no tour do app. Acordo às ${a.wake} e deito às ${a.bed}; " +
            "na folga, até ${a.weekendDelta} min mais tarde. Remédios: $meds. Meu dia: $day, começando às ${a.start}. " +
            "Olhe as duas rotinas e, se fizer sentido, sugira até 3 ajustes pequenos explicando o porquê. Só aplique se eu concordar."
    }
}
