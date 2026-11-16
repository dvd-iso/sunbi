package com.dvdiso.sunbi

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tarefa da rotina.
 *
 * [anchor]: âncora do ritmo social (IPSRT/SRM): levantar, primeiro contato, começar o dia,
 * jantar, deitar. A regularidade dessas cinco é o que mais protege o humor.
 * [step]: o primeiro passo pequeno e concreto (TDAH: começar é a parte difícil).
 */
data class Task(
    val id: String,
    val time: String,
    val label: String,
    val med: Boolean = false,
    val anchor: Boolean = false,
    val step: String = "",
)

/** Sinal de alerta pessoal. [pole] = "up" (subida/mania) ou "down" (descida/depressão). */
data class Sign(val id: String, val label: String, val pole: String)

data class Contact(val name: String, val role: String, val phone: String)

/** Registro last-writer-wins: valor, momento (ms) e aparelho. Veja docs/PROTOCOL.md. */
data class Rec(val v: Any?, val t: Long, val d: String)

fun today(): String = LocalDate.now().toString()

fun nowHm(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

fun isQuest(): Boolean =
    Build.MANUFACTURER.equals("Oculus", true) || Build.MANUFACTURER.equals("Meta", true)

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("sunbi", Context.MODE_PRIVATE)
        if (!sp.contains("device")) {
            val prefix = if (isQuest()) "quest" else "android"
            sp.edit()
                .putString("device", "$prefix-${hex(3)}")
                .putString("name", Build.MODEL ?: prefix)
                .putString("key", hex(4).uppercase())
                .putBoolean("onboarded", false)
                .apply()
        } else if (!sp.contains("onboarded")) {
            // instalação de antes do tour: já tem rotina, não precisa passar por ele
            sp.edit().putBoolean("onboarded", true).apply()
        }
    }

    /** Passou pelo tour inicial (ou veio de uma versão anterior). */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()
    /** Como a pessoa quer ser chamada (opcional). */
    var userName: String
        get() = sp.getString("userName", "")!!
        set(v) = sp.edit().putString("userName", v.trim()).apply()
    /** O que a pessoa cuida: "bipolar", "tdah" ou "both". */
    var focusArea: String
        get() = sp.getString("focusArea", "both")!!
        set(v) = sp.edit().putString("focusArea", v).apply()

    // Modo complexo: página de configuração servida pelo próprio app, para abrir no computador
    var webConfig: Boolean
        get() = sp.getBoolean("webConfig", false)
        set(v) = sp.edit().putBoolean("webConfig", v).apply()
    var webPin: String
        get() = sp.getString("webPin", "")!!
        set(v) = sp.edit().putString("webPin", v).apply()

    /** Lembrete gentil de check-in às 21h, se ainda não registrou (só neste aparelho). */
    var checkinReminder: Boolean
        get() = sp.getBoolean("checkinReminder", true)
        set(v) = sp.edit().putBoolean("checkinReminder", v).apply()
    /** Resumo da semana pela SunAI no domingo à noite, por notificação. */
    var weeklySummary: Boolean
        get() = sp.getBoolean("weeklySummary", false)
        set(v) = sp.edit().putBoolean("weeklySummary", v).apply()

    // Modo launcher: favoritos (em ordem) e apps escondidos, só neste aparelho
    var launcherFavorites: List<String>
        get() = sp.getString("launcherFavorites", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("launcherFavorites", v.distinct().joinToString("\n")).apply()
    var launcherHidden: Set<String>
        get() = sp.getStringSet("launcherHidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("launcherHidden", v).apply()

    // Gamificação: o que já foi comemorado neste aparelho
    var seenBadges: Set<String>
        get() = sp.getStringSet("seenBadges", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("seenBadges", v).apply()
    var seenLevel: Int
        get() = sp.getInt("seenLevel", 0)
        set(v) = sp.edit().putInt("seenLevel", v).apply()

    private fun hex(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it) }
    }

    val device: String get() = sp.getString("device", "android")!!
    var name: String
        get() = sp.getString("name", "Android")!!
        set(v) = sp.edit().putString("name", v).apply()
    var key: String
        get() = sp.getString("key", "")!!
        set(v) = sp.edit().putString("key", v.trim().uppercase()).apply()
    var syncEnabled: Boolean
        get() = sp.getBoolean("sync", true)
        set(v) = sp.edit().putBoolean("sync", v).apply()
    var alarmsEnabled: Boolean
        get() = sp.getBoolean("alarms", true)
        set(v) = sp.edit().putBoolean("alarms", v).apply()
    /** Cores do sistema (Material You). No Quest não há papel de parede, então o padrão é a paleta Girassol. */
    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamicColor", !isQuest())
        set(v) = sp.edit().putBoolean("dynamicColor", v).apply()
    // SunAI: a chave fica só neste aparelho (não sincroniza, sem backup)
    var aiKey: String
        get() = sp.getString("aiKey", "")!!
        set(v) = sp.edit().putString("aiKey", v.trim()).apply()
    var aiModel: String
        get() = sp.getString("aiModel", "kimi-k3")!!
        set(v) = sp.edit().putString("aiModel", v).apply()
    /** A pessoa aceitou enviar os registros ao provedor de IA. */
    var aiConsent: Boolean
        get() = sp.getBoolean("aiConsent", false)
        set(v) = sp.edit().putBoolean("aiConsent", v).apply()
    /** Nome que aparece no PDF do relatório (opcional). */
    var reportName: String
        get() = sp.getString("reportName", "")!!
        set(v) = sp.edit().putString("reportName", v.trim()).apply()

    /** Sonzinhos e vibração leve ao tocar na interface. */
    var uiSounds: Boolean
        get() = sp.getBoolean("uiSounds", true)
        set(v) = sp.edit().putBoolean("uiSounds", v).apply()
    /** Toque dos alarmes: "amanhecer" (kalimba suave, padrão) ou "girassol". */
    var alarmSound: String
        get() = sp.getString("alarmSound", "amanhecer")!!
        set(v) = sp.edit().putString("alarmSound", v).apply()
    var manualPeers: Set<String>
        get() = sp.getStringSet("manualPeers", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("manualPeers", v).apply()
    var scheduledIds: Set<String>
        get() = sp.getStringSet("scheduled", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("scheduled", v).apply()

    // Cronômetro de foco (local a cada aparelho; não sincroniza)
    var focusEnd: Long
        get() = sp.getLong("focusEnd", 0)
        set(v) = sp.edit().putLong("focusEnd", v).apply()
    var focusTotal: Long
        get() = sp.getLong("focusTotal", 0)
        set(v) = sp.edit().putLong("focusTotal", v).apply()
    var focusTask: String
        get() = sp.getString("focusTask", "")!!
        set(v) = sp.edit().putString("focusTask", v).apply()
}

/** Acontecimentos que mexem com o ritmo e o humor (gatilhos comuns na psicoeducação). */
val EVENTS = listOf(
    "stress" to "Estresse",
    "conflict" to "Conflito",
    "late" to "Dormi tarde",
    "allnight" to "Virei a noite",
    "travel" to "Viagem / fuso",
    "alcohol" to "Álcool",
    "caffeine" to "Cafeína à tarde",
    "screens" to "Telas na cama",
    "sick" to "Doente",
    "period" to "Menstruação",
    "medchange" to "Mudança no remédio",
    "good" to "Algo bom aconteceu",
)

object Repo {
    private lateinit var file: File
    private val entries = HashMap<String, Rec>()
    private val listeners = CopyOnWriteArrayList<(Set<String>) -> Unit>()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    // Lido em toda consulta: qualquer composable que lê o Repo recompõe quando algo muda
    // (inclusive registros vindos de outro aparelho).
    private val observed = mutableLongStateOf(0L)

    /** Rotina inicial: as cinco âncoras do ritmo social, remédios e pausas. Tudo editável. */
    val DEFAULT_TASKS = listOf(
        Task("wake", "07:00", "Levantar da cama", anchor = true, step = "Sentar na cama e abrir a cortina"),
        Task("medam", "07:15", "Remédio da manhã", med = true, step = "Pegar o copo d'água"),
        Task("breakfast", "07:30", "Café da manhã", step = "Ir até a cozinha"),
        Task("contact", "08:30", "Falar com alguém (mensagem, ligação ou pessoalmente)", anchor = true, step = "Mandar um \"bom dia\""),
        Task("start", "09:00", "Começar o trabalho, estudo ou tarefa do dia", anchor = true, step = "Abrir só a primeira coisa da lista"),
        Task("water1", "10:30", "Beber água"),
        Task("lunch", "12:30", "Almoço: pare e coma", step = "Levantar da cadeira"),
        Task("water2", "15:00", "Lanche e água"),
        Task("move", "17:30", "Movimento leve ou luz do dia", step = "Calçar o tênis"),
        Task("dinner", "19:30", "Jantar", anchor = true, step = "Pôr a mesa ou esquentar a comida"),
        Task("dim", "21:30", "Luz baixa e telas no modo noturno", step = "Apagar a luz principal"),
        Task("medpm", "22:00", "Remédio da noite", med = true, step = "Pegar o copo d'água"),
        Task("bed", "22:30", "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama"),
    )

    /**
     * Rotina de folga (fins de semana, feriados, férias). Mais solta, mas com as âncoras perto
     * do horário de semana: acordar até 1 hora depois protege o ritmo (IPSRT).
     */
    val DEFAULT_OFF_TASKS = listOf(
        Task("wake", "08:00", "Levantar da cama", anchor = true, step = "Abrir a cortina e beber água"),
        Task("medam", "08:15", "Remédio da manhã", med = true, step = "Pegar o copo d'água"),
        Task("breakfast", "08:30", "Café da manhã sem pressa"),
        Task("contact", "10:00", "Falar com alguém ou sair de casa", anchor = true, step = "Mandar uma mensagem"),
        Task("lunch", "13:00", "Almoço"),
        Task("fun", "15:00", "Algo que você gosta de fazer", step = "Escolher uma coisa só"),
        Task("move", "17:00", "Luz do dia ou caminhada", step = "Calçar o tênis"),
        Task("dinner", "20:00", "Jantar", anchor = true),
        Task("dim", "22:00", "Luz baixa e telas no modo noturno", step = "Apagar a luz principal"),
        Task("medpm", "22:00", "Remédio da noite", med = true, step = "Pegar o copo d'água"),
        Task("bed", "23:00", "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama"),
    )

    val DEFAULT_SIGNS = listOf(
        Sign("u_sleep", "Durmo menos e não sinto cansaço", "up"),
        Sign("u_racing", "Pensamentos acelerados ou falo mais rápido", "up"),
        Sign("u_projects", "Começo muitos projetos ao mesmo tempo", "up"),
        Sign("u_spend", "Gasto mais do que planejei", "up"),
        Sign("u_irrit", "Fico mais irritável ou impaciente", "up"),
        Sign("u_risk", "Arrisco mais que o normal", "up"),
        Sign("d_interest", "Perco o interesse no que gosto", "down"),
        Sign("d_isolate", "Me isolo e evito pessoas", "down"),
        Sign("d_sleep", "Durmo demais ou não consigo levantar", "down"),
        Sign("d_slow", "Cansaço e lentidão o dia todo", "down"),
        Sign("d_appetite", "Meu apetite muda muito", "down"),
        Sign("d_guilt", "Culpa ou sensação de que nada vale a pena", "down"),
    )

    /** Plano de ação (psicoeducação) e plano de segurança (Stanley & Brown), já com sugestões editáveis. */
    val DEFAULT_PLAN: Map<String, List<String>> = mapOf(
        "up" to listOf(
            "Proteger o sono: deitar no horário, sem telas",
            "Adiar decisões grandes e compras por 48 horas",
            "Cortar cafeína, álcool e programas que agitam",
            "Avisar meu psiquiatra e uma pessoa de confiança",
            "Tomar os remédios exatamente como prescritos",
        ),
        "down" to listOf(
            "Manter o horário de levantar, mesmo sem vontade",
            "Fazer uma coisa pequena e boa por dia",
            "Pegar luz do dia de manhã",
            "Falar com alguém de confiança",
            "Avisar meu psiquiatra ou psicólogo",
        ),
        "warn" to listOf(),
        "cope" to listOf("Respirar devagar por 2 minutos", "Banho morno", "Ouvir uma música calma"),
        "distract" to listOf(),
        "safe" to listOf(),
        "reasons" to listOf(),
    )

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "state.json")
        entries.clear()
        if (file.exists()) {
            runCatching {
                val obj = JSONObject(file.readText()).optJSONObject("entries") ?: JSONObject()
                for (k in obj.keys()) parseRec(obj.optJSONObject(k))?.let { entries[k] = it }
            }
        }
        // t = 1: qualquer versão editada em outro aparelho vence a padrão.
        // A rotina não é semeada: ela nasce no tour inicial (ou chega de outro aparelho).
        if (!entries.containsKey("signs")) entries["signs"] = Rec(signsToJson(DEFAULT_SIGNS), 1, "")
    }

    /** Dias (AAAA-MM-DD) que têm algum registro. */
    fun recordedDays(): Set<String> {
        observed.longValue
        return synchronized(this) {
            entries.mapNotNullTo(HashSet()) { (k, r) ->
                if (k.length > 11 && k[10] == '/' && k[4] == '-' && r.v != null && r.v != false) k.substring(0, 10) else null
            }
        }
    }

    fun addListener(fn: (Set<String>) -> Unit) { listeners.add(fn) }

    private fun parseRec(o: JSONObject?): Rec? {
        if (o == null || !o.has("t")) return null
        val v = o.opt("v").let { if (it == JSONObject.NULL) null else it }
        return Rec(v, o.optLong("t"), o.optString("d", ""))
    }

    private fun newer(a: Rec?, b: Rec): Boolean =
        a == null || b.t > a.t || (b.t == a.t && b.d > a.d)

    private fun save() {
        val tmp = File(file.parentFile, "state.json.tmp")
        tmp.writeText(snapshotLocked().toString())
        tmp.renameTo(file)
    }

    private fun snapshotLocked(): JSONObject {
        val e = JSONObject()
        for ((k, r) in entries) {
            e.put(k, JSONObject().put("v", r.v ?: JSONObject.NULL).put("t", r.t).put("d", r.d))
        }
        return JSONObject().put("device", Prefs.device).put("entries", e)
    }

    @Synchronized
    fun snapshot(): JSONObject = snapshotLocked()

    fun set(key: String, value: Any?) {
        synchronized(this) {
            entries[key] = Rec(value, System.currentTimeMillis(), Prefs.device)
            save()
        }
        changed(setOf(key))
    }

    /** Mescla registros vindos de outro aparelho. Devolve as chaves que mudaram. */
    fun merge(remote: JSONObject?): Set<String> {
        if (remote == null) return emptySet()
        val changed = HashSet<String>()
        synchronized(this) {
            for (k in remote.keys()) {
                val r = parseRec(remote.optJSONObject(k)) ?: continue
                if (newer(entries[k], r)) {
                    entries[k] = r
                    changed.add(k)
                }
            }
            if (changed.isNotEmpty()) save()
        }
        if (changed.isNotEmpty()) changed(changed)
        return changed
    }

    private fun changed(keys: Set<String>) {
        _version.value = _version.value + 1
        observed.longValue = observed.longValue + 1
        for (l in listeners) runCatching { l(keys) }
    }

    fun get(key: String): Any? {
        observed.longValue
        return synchronized(this) { entries[key]?.v }
    }

    // ── rotina ──────────────────────────────────────────────────────────────

    /** Rotina de dias de semana (vazia até o tour montar uma ou outro aparelho mandar). */
    fun tasks(): List<Task> = parseTasks(get("tasks") as? JSONArray) ?: emptyList()

    /** Rotina de folga (fins de semana, feriados, modo férias). */
    fun offTasks(): List<Task> = parseTasks(get("tasks_off") as? JSONArray) ?: emptyList()

    fun hasRoutine(): Boolean = tasks().isNotEmpty() || offTasks().isNotEmpty()

    fun setOffTasks(list: List<Task>) = set("tasks_off", tasksToJson(list))

    /** A rotina que vale num dia. Em "dia difícil", só as âncoras e os remédios. */
    fun tasksFor(day: String): List<Task> {
        val base = if (isOff(day)) offTasks() else tasks()
        return if (isHard(day)) base.filter { it.anchor || it.med } else base
    }

    /** Dia difícil: a rotina encolhe para o essencial (sincroniza, vale em todos os aparelhos). */
    fun isHard(day: String): Boolean = get("$day/hard") == true
    fun setHard(day: String, on: Boolean) = set("$day/hard", if (on) true else null)

    /** Aparelho que toca os alarmes (id), ou null = todos. */
    fun alarmDevice(): String? = (get("alarm_device") as? String)?.ifBlank { null }
    fun ringsHere(): Boolean = alarmDevice().let { it == null || it == Prefs.device }

    private fun parseTasks(arr: JSONArray?): List<Task>? {
        if (arr == null) return null
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Task(
                o.optString("id"), o.optString("time"), o.optString("label"),
                med = o.optBoolean("med", false),
                anchor = o.optBoolean("anchor", false),
                step = o.optString("step", ""),
            )
        }.sortedBy { it.time }
    }

    // ── folga: fins de semana, feriados e modo férias ───────────────────────

    enum class OffReason { VACATION, WEEKEND, HOLIDAY, MANUAL }

    /** Início do modo férias (AAAA-MM-DD) ou null. */
    fun vacationSince(): String? = (get("vacation") as? JSONObject)?.optString("since")?.ifBlank { null }

    fun offWeekends(): Boolean = get("off/weekends") as? Boolean ?: true
    fun offHolidays(): Boolean = get("off/holidays") as? Boolean ?: true

    /** Por que o dia usa a rotina de folga (null = rotina de semana). */
    fun offReason(day: String): OffReason? {
        when (get("$day/off")) {
            true -> return if (vacationSince()?.let { day >= it } == true) OffReason.VACATION else OffReason.MANUAL
            false -> return null
        }
        vacationSince()?.let { if (day >= it) return OffReason.VACATION }
        val d = LocalDate.parse(day)
        if (offHolidays() && Holidays.name(d) != null) return OffReason.HOLIDAY
        if (offWeekends() && d.dayOfWeek.value >= 6) return OffReason.WEEKEND
        return null
    }

    fun isOff(day: String): Boolean = offReason(day) != null

    /**
     * Toque na flor. Hoje: liga ou desliga o modo férias (que segue ligado nos próximos dias).
     * Outro dia: troca só aquele dia. Devolve se o dia ficou de folga.
     */
    fun toggleOff(day: String): Boolean {
        val today = today()
        val since = vacationSince()
        if (day != today) {
            set("$day/off", !isOff(day))
            return isOff(day)
        }
        if (since != null) {
            // fim das férias: os dias passados ficam marcados como folga no histórico
            var d = LocalDate.parse(since)
            val t = LocalDate.parse(today)
            while (d.isBefore(t)) {
                if (get("$d/off") == null) set("$d/off", true)
                d = d.plusDays(1)
            }
            set("vacation", null)
            if (get("$today/off") == true) set("$today/off", null)
            if (isOff(today)) set("$today/off", false) // fim de semana ou feriado: hoje volta ao normal
            return isOff(today)
        }
        if (isOff(today)) {
            set("$today/off", false) // folga automática (fim de semana/feriado) desligada só hoje
            return false
        }
        if (get("$today/off") == false) set("$today/off", null)
        set("vacation", JSONObject().put("since", today))
        return true
    }

    fun setTasks(list: List<Task>) = set("tasks", tasksToJson(list))

    fun tasksToJson(list: List<Task>): JSONArray {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject().put("id", t.id).put("time", t.time).put("label", t.label)
            if (t.med) o.put("med", true)
            if (t.anchor) o.put("anchor", true)
            if (t.step.isNotBlank()) o.put("step", t.step)
            arr.put(o)
        }
        return arr
    }

    /**
     * Feito: `true` (sem horário) ou "HH:MM" (horário real em que foi feito, usado na
     * regularidade das âncoras). `false`/ausente = não feito.
     */
    fun done(day: String, id: String): Boolean = get("$day/done/$id").let { it == true || it is String }

    fun doneAt(day: String, id: String): String? = get("$day/done/$id") as? String

    fun setDone(day: String, id: String, v: Boolean) =
        set("$day/done/$id", if (!v) false else if (day == today()) nowHm() else true)

    fun percent(day: String): Int {
        val t = tasksFor(day)
        if (t.isEmpty()) return 0
        return 100 * t.count { done(day, it.id) } / t.size
    }

    // ── check-in do dia (inspirado no NIMH Life Chart) ──────────────────────

    /** Humor de -3 (depressão grave) a +3 (elevação grave); 0 = estável. Lê a escala antiga (1..5) se for o caso. */
    fun humor(day: String): Int? {
        (get("$day/humor") as? Number)?.let { return it.toInt().coerceIn(-3, 3) }
        val old = (get("$day/mood") as? Number)?.toInt() ?: return null
        return (old - 3).coerceIn(-2, 2)
    }

    fun level(day: String, field: String): Int? = (get("$day/$field") as? Number)?.toInt()
    fun setLevel(day: String, field: String, v: Int?) = set("$day/$field", v)

    fun bed(day: String): String? = get("$day/bed") as? String
    fun wake(day: String): String? = get("$day/wake") as? String

    /** Horas de sono da noite que terminou na manhã de [day]. */
    fun sleep(day: String): Double? {
        val b = bed(day)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val w = wake(day)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        if (b != null && w != null) {
            var mins = (w.toSecondOfDay() - b.toSecondOfDay()) / 60
            if (mins <= 0) mins += 24 * 60
            return Math.round(mins / 6.0) / 10.0
        }
        return (get("$day/sleep") as? Number)?.toDouble()
    }

    fun setSleepTimes(day: String, bed: String?, wake: String?) {
        set("$day/bed", bed)
        set("$day/wake", wake)
        sleep(day)?.let { set("$day/sleep", it) } // mantém o número para o plugin e versões antigas
    }

    fun note(day: String): String = get("$day/note") as? String ?: ""

    fun strSet(key: String): Set<String> {
        val arr = get(key) as? JSONArray ?: return emptySet()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }.toSet()
    }

    fun setStrSet(key: String, v: Set<String>) = set(key, JSONArray(v.sorted()))

    fun signsOn(day: String): Set<String> = strSet("$day/signs")
    fun eventsOn(day: String): Set<String> = strSet("$day/events")

    fun hasDay(day: String): Boolean = observed.longValue.let { _ -> synchronized(this) {
        entries.any { (k, r) -> k.startsWith("$day/") && r.v != null && r.v != false }
    } }

    /** Fez o check-in (humor ou sono) nesse dia. */
    fun checkedIn(day: String): Boolean = humor(day) != null || sleep(day) != null

    // ── sinais, plano e contatos ────────────────────────────────────────────

    fun signs(): List<Sign> {
        val arr = get("signs") as? JSONArray ?: return DEFAULT_SIGNS
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Sign(o.optString("id"), o.optString("label"), o.optString("pole", "up"))
        }
    }

    fun setSigns(list: List<Sign>) = set("signs", signsToJson(list))

    private fun signsToJson(list: List<Sign>): JSONArray {
        val arr = JSONArray()
        for (s in list) arr.put(JSONObject().put("id", s.id).put("label", s.label).put("pole", s.pole))
        return arr
    }

    fun planList(section: String): List<String> {
        val arr = get("plan/$section") as? JSONArray ?: return DEFAULT_PLAN[section].orEmpty()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    fun setPlanList(section: String, items: List<String>) = set("plan/$section", JSONArray(items))

    fun contacts(): List<Contact> {
        val arr = get("contacts") as? JSONArray ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Contact(o.optString("name"), o.optString("role"), o.optString("phone"))
        }
    }

    fun setContacts(list: List<Contact>) {
        val arr = JSONArray()
        for (c in list) arr.put(JSONObject().put("name", c.name).put("role", c.role).put("phone", c.phone))
        set("contacts", arr)
    }
}

fun newId(prefix: String): String = prefix + System.currentTimeMillis().toString(36)
