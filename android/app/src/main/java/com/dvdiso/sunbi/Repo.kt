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
 * [min]: duração estimada em minutos (TDAH: tornar o tempo visível).
 */
data class Task(
    val id: String,
    val time: String,
    val label: String,
    val med: Boolean = false,
    val anchor: Boolean = false,
    val step: String = "",
    val min: Int = 0,
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
                .apply()
        }
    }

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
        Task("wake", "07:00", "Levantar da cama", anchor = true, step = "Sentar na cama e abrir a cortina", min = 5),
        Task("medam", "07:15", "Remédio da manhã", med = true, step = "Pegar o copo d'água", min = 2),
        Task("breakfast", "07:30", "Café da manhã", step = "Ir até a cozinha", min = 20),
        Task("contact", "08:30", "Falar com alguém (mensagem, ligação ou pessoalmente)", anchor = true, step = "Mandar um \"bom dia\"", min = 5),
        Task("start", "09:00", "Começar o trabalho, estudo ou tarefa do dia", anchor = true, step = "Abrir só a primeira coisa da lista", min = 25),
        Task("water1", "10:30", "Beber água", min = 1),
        Task("lunch", "12:30", "Almoço: pare e coma", step = "Levantar da cadeira", min = 30),
        Task("water2", "15:00", "Lanche e água", min = 10),
        Task("move", "17:30", "Movimento leve ou luz do dia", step = "Calçar o tênis", min = 20),
        Task("dinner", "19:30", "Jantar", anchor = true, step = "Pôr a mesa ou esquentar a comida", min = 30),
        Task("dim", "21:30", "Luz baixa e telas no modo noturno", step = "Apagar a luz principal", min = 5),
        Task("medpm", "22:00", "Remédio da noite", med = true, step = "Pegar o copo d'água", min = 2),
        Task("bed", "22:30", "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama", min = 10),
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
        // t = 1: qualquer versão editada em outro aparelho vence a padrão
        if (!entries.containsKey("tasks")) entries["tasks"] = Rec(tasksToJson(DEFAULT_TASKS), 1, "")
        if (!entries.containsKey("signs")) entries["signs"] = Rec(signsToJson(DEFAULT_SIGNS), 1, "")
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

    fun tasks(): List<Task> {
        val arr = get("tasks") as? JSONArray ?: return DEFAULT_TASKS
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Task(
                o.optString("id"), o.optString("time"), o.optString("label"),
                med = o.optBoolean("med", false),
                anchor = o.optBoolean("anchor", false),
                step = o.optString("step", ""),
                min = o.optInt("min", 0),
            )
        }.sortedBy { it.time }
    }

    fun setTasks(list: List<Task>) = set("tasks", tasksToJson(list))

    private fun tasksToJson(list: List<Task>): JSONArray {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject().put("id", t.id).put("time", t.time).put("label", t.label)
            if (t.med) o.put("med", true)
            if (t.anchor) o.put("anchor", true)
            if (t.step.isNotBlank()) o.put("step", t.step)
            if (t.min > 0) o.put("min", t.min)
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
        val t = tasks()
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
