package com.dvdiso.sunbi

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * SunAI: assistente do SunBi via OpenCode Go (API compatível com OpenAI Chat Completions).
 *
 * A cada pergunta envia um resumo dos registros (últimos 31 dias), as duas rotinas e o plano.
 * Pode mudar a rotina por ferramentas (add/update/remove_task) com regras checadas aqui:
 * remédios não são apagados nem mudam de horário pela IA. Cada resposta guarda um retrato das
 * rotinas de antes, para "Desfazer".
 */
object SunAI {
    /** Endpoint OpenAI-compatível do OpenCode Go (var só para os testes apontarem para um servidor falso). */
    internal var ENDPOINT = "https://opencode.ai/inference/go/openai/v1/chat/completions"

    /** Modelos do OpenCode Go que funcionam bem em português e com ferramentas. */
    val MODELS = listOf(
        "kimi-k3" to "Kimi K3",
        "glm-5.3" to "GLM-5.3",
        "deepseek-v4-pro" to "DeepSeek V4 Pro",
        "qwen3.8-max" to "Qwen 3.8 Max",
        "deepseek-v4.1-flash" to "DeepSeek V4.1 Flash (rápido)",
    )

    data class Change(val kind: String, val routine: String, val text: String)

    data class Msg(
        val role: String, // "user" | "assistant" | "error"
        val text: String,
        val changes: List<Change> = emptyList(),
        val undo: JSONObject? = null, // rotinas antes das mudanças
        val undone: Boolean = false,
    )

    private lateinit var file: File

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "sunai.json")
    }

    // ── histórico local (não sincroniza) ────────────────────────────────────

    fun load(): List<Msg> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ch = o.optJSONArray("changes")
            Msg(
                o.optString("role"), o.optString("text"),
                if (ch == null) emptyList() else (0 until ch.length()).map { j ->
                    val c = ch.getJSONObject(j)
                    Change(c.optString("kind"), c.optString("routine"), c.optString("text"))
                },
                o.optJSONObject("undo"), o.optBoolean("undone"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Msg>) {
        val arr = JSONArray()
        for (m in list.takeLast(80)) {
            val ch = JSONArray()
            for (c in m.changes) ch.put(JSONObject().put("kind", c.kind).put("routine", c.routine).put("text", c.text))
            arr.put(JSONObject().put("role", m.role).put("text", m.text).put("changes", ch)
                .put("undo", m.undo ?: JSONObject.NULL).put("undone", m.undone))
        }
        file.writeText(arr.toString())
    }

    fun clear() {
        runCatching { file.delete() }
    }

    fun undo(m: Msg) {
        val u = m.undo ?: return
        u.optJSONArray("tasks")?.let { Repo.set("tasks", it) }
        u.optJSONArray("tasks_off")?.let { Repo.set("tasks_off", it) }
    }

    // ── contexto ────────────────────────────────────────────────────────────

    private val PT = Locale.forLanguageTag("pt-BR")

    private fun taskLine(t: Task): String = buildString {
        append("${t.id} | ${t.time} | ${t.label}")
        if (t.med) append(" | remédio")
        if (t.anchor) append(" | âncora")
        if (t.step.isNotBlank()) append(" | 1º passo: ${t.step}")
    }

    fun context(): String {
        val today = today()
        val d = LocalDate.parse(today)
        val sb = StringBuilder()
        val off = Repo.offReason(today)
        sb.append("Hoje é ${d.dayOfWeek.getDisplayName(TextStyle.FULL, PT)}, $today, ${nowHm()}. ")
        sb.append(if (off == null) "Hoje vale a rotina de semana.\n" else "Hoje vale a rotina de folga (${off.name.lowercase()}).\n")
        Repo.vacationSince()?.let { sb.append("Modo férias ligado desde $it.\n") }
        sb.append("\nROTINA DE SEMANA (id | horário | tarefa):\n")
        Repo.tasks().forEach { sb.append("- ${taskLine(it)}\n") }
        sb.append("\nROTINA DE FOLGA (fins de semana, feriados, férias):\n")
        Repo.offTasks().forEach { sb.append("- ${taskLine(it)}\n") }

        val signs = Repo.signs()
        sb.append("\nSINAIS DE ALERTA DA PESSOA: subida: ${signs.filter { it.pole == "up" }.joinToString("; ") { it.label }}. ")
        sb.append("Descida: ${signs.filter { it.pole == "down" }.joinToString("; ") { it.label }}.\n")
        sb.append("PLANO quando sobe: ${Repo.planList("up").joinToString("; ")}. Quando desce: ${Repo.planList("down").joinToString("; ")}.\n")

        val ins = insights(today)
        if (ins.isNotEmpty()) sb.append("AVISOS AUTOMÁTICOS DE HOJE: ${ins.joinToString(" | ") { it.title + ": " + it.body }}\n")

        val events = EVENTS.toMap()
        val signById = signs.associateBy { it.id }
        sb.append("\nREGISTROS DOS ÚLTIMOS 31 DIAS (humor de -3 muito para baixo a +3 muito acelerado):\n")
        for (day in lastDays(today, 31)) {
            if (!Repo.hasDay(day)) {
                sb.append("$day: sem registro\n")
                continue
            }
            val p = mutableListOf<String>()
            if (Repo.isOff(day)) p += "folga"
            Repo.humor(day)?.let { p += "humor ${if (it > 0) "+$it" else "$it"}" }
            Repo.sleep(day)?.let { s ->
                p += "sono ${"%.1f".format(Locale.US, s)}h" + (if (Repo.bed(day) != null) " (${Repo.bed(day)}→${Repo.wake(day)})" else "")
            }
            Repo.level(day, "energy")?.let { p += "energia $it/5" }
            Repo.level(day, "focus")?.let { p += "foco $it/5" }
            Repo.level(day, "irrit")?.let { p += "irritação $it/3" }
            Repo.level(day, "anx")?.let { p += "ansiedade $it/3" }
            p += "rotina ${Repo.percent(day)}%"
            val meds = Repo.tasksFor(day).filter { it.med }
            if (meds.isNotEmpty()) p += "remédios ${meds.count { Repo.done(day, it.id) }}/${meds.size}"
            rhythm(day)?.let { (h, n) -> p += "âncoras no horário $h/$n" }
            val sg = Repo.signsOn(day).mapNotNull { signById[it]?.label }
            if (sg.isNotEmpty()) p += "sinais: ${sg.joinToString(", ")}"
            val ev = Repo.eventsOn(day).mapNotNull { events[it] }
            if (ev.isNotEmpty()) p += "acontecimentos: ${ev.joinToString(", ")}"
            Repo.note(day).takeIf { it.isNotBlank() }?.let { p += "nota: \"${it.replace('\n', ' ').take(160)}\"" }
            sb.append("$day: ${p.joinToString("; ")}\n")
        }
        return sb.toString()
    }

    private val SYSTEM = """
Você é a SunAI, a assistente do app SunBi. A pessoa usa o app para cuidar da rotina com transtorno bipolar e/ou TDAH.

Como falar:
- Português do Brasil, tom acolhedor, frases curtas, sem julgamento e sem culpa.
- Até umas 180 palavras, salvo se pedirem mais. Listas curtas quando ajudar.
- Baseie-se nos registros abaixo. Não invente dados. Diga quando faltam registros.
- Você não diagnostica e não substitui psiquiatra ou psicólogo.

O que você sabe (métodos do app):
- Ritmo social (IPSRT): âncoras regulares (levantar, primeiro contato, começar o dia, jantar, deitar), até 45 min do horário.
- Sono: dormir menos sem cansaço é sinal comum de subida; dormir muito mais pode indicar descida.
- Sinais de alerta e plano de ação da própria pessoa; ativação comportamental (coisas pequenas e boas).
- TDAH: tarefas pequenas, primeiro passo concreto, lembretes, tempo visível.

Regras de segurança (sempre):
- Nunca sugira começar, parar, trocar ou mudar dose ou horário de remédio. Isso é decidido com o médico.
- Se aparecer risco (vontade de morrer, se machucar, crise intensa), oriente na hora: CVV 188 (24 h), SAMU 192,
  o plano de segurança do app e procurar alguém de confiança.
- Em sinais fortes de subida ou descida, incentive seguir o plano da pessoa e falar com a equipe de saúde.

Mudar a rotina:
- Use as ferramentas add_task, update_task e remove_task quando a pessoa pedir ou aceitar um ajuste.
- Poucas mudanças por vez (no máximo 4, salvo pedido), cada uma com um motivo em uma frase.
- Mantenha as âncoras. Não mexa no horário de dormir ou acordar mais que 30 a 60 minutos de uma vez.
- Tarefas de remédio não podem ser removidas nem mudar de horário por você.
- A pessoa pode desfazer tudo com um toque, então seja direta.
""".trim()

    private fun tools(): JSONArray {
        fun fn(name: String, desc: String, props: JSONObject, required: List<String>) = JSONObject()
            .put("type", "function")
            .put("function", JSONObject().put("name", name).put("description", desc)
                .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required))))
        fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)
        fun bool(desc: String) = JSONObject().put("type", "boolean").put("description", desc)
        val routine = JSONObject().put("type", "string").put("enum", JSONArray(listOf("semana", "folga")))
            .put("description", "semana = dias de semana; folga = fins de semana, feriados e férias")
        return JSONArray()
            .put(fn("add_task", "Adiciona uma tarefa à rotina.", JSONObject()
                .put("routine", routine).put("time", str("Horário HH:MM (24 h)")).put("label", str("O que fazer, curto"))
                .put("step", str("Primeiro passo concreto (opcional)")).put("anchor", bool("É âncora do ritmo social"))
                .put("med", bool("É remédio (só se a pessoa pedir)")), listOf("routine", "time", "label")))
            .put(fn("update_task", "Muda horário, texto, primeiro passo ou âncora de uma tarefa existente.", JSONObject()
                .put("routine", routine).put("id", str("id da tarefa")).put("time", str("Novo horário HH:MM"))
                .put("label", str("Novo texto")).put("step", str("Novo primeiro passo")).put("anchor", bool("É âncora")),
                listOf("routine", "id")))
            .put(fn("remove_task", "Remove uma tarefa da rotina (nunca remédio).", JSONObject()
                .put("routine", routine).put("id", str("id da tarefa")), listOf("routine", "id")))
    }

    // ── execução das ferramentas, com regras ────────────────────────────────

    private val HM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    internal class Session {
        var undo: JSONObject? = null
        val changes = mutableListOf<Change>()
    }

    internal fun exec(s: Session, name: String, args: JSONObject): String {
        val routine = args.optString("routine", "semana")
        val off = routine == "folga"
        val label = if (off) "folga" else "semana"
        val list = (if (off) Repo.offTasks() else Repo.tasks()).toMutableList()
        fun store(l: List<Task>) {
            if (s.undo == null) {
                // retrato das duas rotinas antes da primeira mudança, para "Desfazer"
                s.undo = JSONObject().put("tasks", Repo.tasksToJson(Repo.tasks())).put("tasks_off", Repo.tasksToJson(Repo.offTasks()))
            }
            if (off) Repo.setOffTasks(l) else Repo.setTasks(l)
        }
        return when (name) {
            "add_task" -> {
                val time = args.optString("time")
                val text = args.optString("label").trim()
                if (!HM.matches(time)) return "erro: horário inválido, use HH:MM"
                if (text.isEmpty()) return "erro: falta o texto da tarefa"
                val t = Task(newId("ai"), time, text, med = args.optBoolean("med", false),
                    anchor = args.optBoolean("anchor", false), step = args.optString("step", "").trim())
                store(list + t)
                s.changes += Change("add", label, "${t.time} ${t.label}")
                "ok: adicionada com id ${t.id}"
            }
            "update_task" -> {
                val id = args.optString("id")
                val old = list.firstOrNull { it.id == id } ?: return "erro: não existe tarefa com id $id na rotina $label"
                val time = args.optString("time", old.time).ifBlank { old.time }
                if (!HM.matches(time)) return "erro: horário inválido, use HH:MM"
                if (old.med && time != old.time) return "erro: horário de remédio só a pessoa muda, com orientação médica"
                val nt = old.copy(
                    time = time,
                    label = args.optString("label", old.label).ifBlank { old.label },
                    step = if (args.has("step")) args.optString("step").trim() else old.step,
                    anchor = if (args.has("anchor")) args.optBoolean("anchor") else old.anchor,
                )
                store(list.map { if (it.id == id) nt else it })
                s.changes += Change("update", label, "${old.time} ${old.label} → ${nt.time} ${nt.label}")
                "ok: atualizada"
            }
            "remove_task" -> {
                val id = args.optString("id")
                val old = list.firstOrNull { it.id == id } ?: return "erro: não existe tarefa com id $id na rotina $label"
                if (old.med) return "erro: tarefas de remédio não podem ser removidas pela SunAI"
                store(list.filterNot { it.id == id })
                s.changes += Change("remove", label, "${old.time} ${old.label}")
                "ok: removida"
            }
            else -> "erro: ferramenta desconhecida"
        }
    }

    // ── chamada à API ───────────────────────────────────────────────────────

    class ApiError(msg: String) : Exception(msg)

    private fun post(body: JSONObject): JSONObject {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 120_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${Prefs.aiKey}")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            throw ApiError(
                when (code) {
                    401, 403 -> "A chave do OpenCode Go não foi aceita. Confira em Ajustes da SunAI."
                    429 -> "O limite do seu plano OpenCode Go acabou por agora. Tente mais tarde ou troque de modelo."
                    else -> "O OpenCode Go respondeu com erro $code. " + text.take(200)
                },
            )
        }
        return JSONObject(text)
    }

    /** Envia a pergunta com o histórico recente. Devolve a resposta (com as mudanças feitas na rotina). */
    suspend fun ask(history: List<Msg>, question: String): Msg = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", SYSTEM + "\n\n" + context()))
        for (m in history.filter { it.role == "user" || it.role == "assistant" }.takeLast(12)) {
            messages.put(JSONObject().put("role", m.role).put("content", m.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", question))

        val session = Session()
        var answer = ""
        for (round in 0 until 6) {
            val body = JSONObject().put("model", Prefs.aiModel).put("messages", messages)
                .put("tools", tools()).put("temperature", 0.6)
            val res = post(body)
            val msg = res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: throw ApiError("Resposta vazia do OpenCode Go.")
            val calls = msg.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                answer = msg.optString("content", "").let { if (it == "null") "" else it }.trim()
                break
            }
            // guarda a vez da assistente com as chamadas e responde cada uma
            messages.put(JSONObject().put("role", "assistant").put("content", msg.opt("content") ?: JSONObject.NULL).put("tool_calls", calls))
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                val f = c.optJSONObject("function") ?: continue
                val args = runCatching { JSONObject(f.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                val result = runCatching { exec(session, f.optString("name"), args) }.getOrElse { "erro: ${it.message}" }
                messages.put(JSONObject().put("role", "tool").put("tool_call_id", c.optString("id")).put("content", result))
            }
        }
        if (answer.isBlank()) answer = if (session.changes.isNotEmpty()) "Pronto, ajustei a sua rotina." else "Não consegui responder agora. Tente de novo."
        Msg("assistant", answer, session.changes.toList(), session.undo)
    }
}
