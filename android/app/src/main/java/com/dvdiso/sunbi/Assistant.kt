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
 * Assistente do SunBi: conversa por qualquer API compatível com OpenAI Chat Completions
 * (OpenCode Go, OpenAI, OpenRouter, Groq, DeepSeek, Ollama ou um endereço próprio).
 *
 * A cada pergunta envia um resumo dos registros (últimos 31 dias), as duas rotinas e o plano.
 * Pode mudar a rotina por ferramentas (add/update/remove_task) com regras checadas aqui:
 * remédios não são apagados nem mudam de horário pela IA. Cada resposta guarda um retrato das
 * rotinas de antes, para "Desfazer".
 */
object Assistant {
    const val DEFAULT_NAME = "Assistente"

    /**
     * Um provedor: endereço base da API (até o /v1), onde pegar a chave e alguns modelos de
     * sugestão. A lista de verdade vem de [listModels], e o campo de modelo aceita qualquer id.
     */
    data class Provider(val id: String, val name: String, val baseUrl: String, val keyHint: String, val models: List<String>)

    val PROVIDERS = listOf(
        Provider("opencode", "OpenCode Go", "https://opencode.ai/inference/go/openai/v1", "opencode.ai/console",
            listOf("kimi-k3", "glm-5.3", "deepseek-v4-pro", "qwen3.8-max", "deepseek-v4.1-flash")),
        Provider("openai", "OpenAI", "https://api.openai.com/v1", "platform.openai.com/api-keys",
            listOf("gpt-5-mini", "gpt-5", "gpt-4.1-mini")),
        Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter.ai/keys",
            listOf("openai/gpt-5-mini", "anthropic/claude-sonnet-4.5", "google/gemini-2.5-flash", "deepseek/deepseek-chat")),
        Provider("groq", "Groq", "https://api.groq.com/openai/v1", "console.groq.com/keys",
            listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b")),
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "platform.deepseek.com",
            listOf("deepseek-chat")),
        // qualquer outro serviço ou servidor próprio (Ollama, LM Studio, LiteLLM...) que fale o formato da OpenAI
        Provider("custom", "Outro (compatível com OpenAI)", "", "", emptyList()),
    )

    fun provider(id: String = Prefs.aiProvider): Provider = PROVIDERS.firstOrNull { it.id == id } ?: PROVIDERS.first()

    /**
     * Aceita o endereço como a pessoa colar: com ou sem /v1, com /chat/completions no fim, sem
     * http:// (servidor de casa). Devolve a base, sem barra final.
     */
    fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isEmpty()) return ""
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        u = u.removeSuffix("/chat/completions").removeSuffix("/models").trimEnd('/')
        // só o servidor, sem caminho: o padrão é /v1
        if (u.substringAfter("://").none { it == '/' }) u += "/v1"
        return u
    }

    fun baseUrl(): String = provider().let { if (it.id == "custom") normalizeBaseUrl(Prefs.aiBaseUrl) else it.baseUrl }

    /** Só para os testes apontarem para um servidor falso. */
    internal var ENDPOINT: String? = null
    private fun endpoint(): String = ENDPOINT ?: (baseUrl() + "/chat/completions")

    /** Nome que a pessoa deu à assistente (padrão "Assistente"). */
    fun name(): String = Prefs.aiName.ifBlank { DEFAULT_NAME }

    data class Change(val kind: String, val routine: String, val text: String)

    data class Msg(
        val role: String, // "user" | "assistant" | "error"
        val text: String,
        val changes: List<Change> = emptyList(),
        val undo: JSONObject? = null, // rotinas antes das mudanças
        val undone: Boolean = false,
    )

    private lateinit var file: File
    private lateinit var sessionFile: File

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "sunai.json")
        sessionFile = File(ctx.filesDir, "sunai.session")
    }

    /** Chave e consentimento dados: dá para perguntar. */
    fun ready(): Boolean = Prefs.aiEnabled && Prefs.aiConsent && Prefs.aiModel.isNotBlank() && baseUrl().isNotBlank() &&
        (Prefs.aiKey.isNotBlank() || Prefs.aiProvider == "custom") // servidor próprio pode não pedir chave

    /**
     * ID estável da conversa, enviado em x-opencode-session (o OpenCode Go exige: sem ele
     * responde 400 "MissingSessionID"; os outros provedores ignoram). Muda a cada conversa nova.
     */
    fun sessionId(): String {
        val cur = runCatching { sessionFile.readText().trim() }.getOrNull()
        if (!cur.isNullOrBlank()) return cur
        val id = "sunbi-" + java.util.UUID.randomUUID()
        runCatching { sessionFile.writeText(id) }
        return id
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
        runCatching { sessionFile.delete() } // conversa nova, sessão nova
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
        if (Prefs.userName.isNotBlank()) sb.append("A pessoa se chama ${Prefs.userName}. ")
        val names = CONCERNS.toMap()
        val cares = Prefs.concerns.filter { it != "routine" }.mapNotNull { names[it]?.lowercase() }
        sb.append(when {
            cares.isNotEmpty() -> "Ela usa o app para cuidar de: ${cares.joinToString(", ")}. "
            Prefs.focusArea == "bipolar" -> "Ela cuida do transtorno bipolar. "
            Prefs.focusArea == "tdah" -> "Ela cuida do TDAH. "
            Prefs.focusArea == "both" -> "Ela cuida de transtorno bipolar e TDAH. "
            else -> "Ela usa o app para organizar a rotina; não suponha nenhum diagnóstico. "
        })
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

    private fun system(): String {
        val who = name().takeIf { it != DEFAULT_NAME }?.let { " A pessoa escolheu te chamar de \"$it\": esse é o seu nome." } ?: ""
        return "Você é a assistente do app SunBi.$who A pessoa usa o app para cuidar da rotina, do sono e do humor; o contexto abaixo diz do que ela cuida.\n\n$RULES"
    }

    private val RULES = """
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
                if (old.med) return "erro: tarefas de remédio não podem ser removidas pela assistente"
                store(list.filterNot { it.id == id })
                s.changes += Change("remove", label, "${old.time} ${old.label}")
                "ok: removida"
            }
            else -> "erro: ferramenta desconhecida"
        }
    }

    // ── chamada à API ───────────────────────────────────────────────────────

    class ApiError(msg: String, val code: Int = 0) : Exception(msg)

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 120_000
        if (Prefs.aiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${Prefs.aiKey}")
        conn.setRequestProperty("User-Agent", "SunBi/${BuildConfig.VERSION_NAME} (Android)")
        when (Prefs.aiProvider) {
            // o OpenCode Go pede um ID de sessão estável
            "opencode" -> conn.setRequestProperty("x-opencode-session", sessionId())
            // o OpenRouter mostra o nome do app no painel da pessoa
            "openrouter" -> conn.setRequestProperty("X-Title", "SunBi")
        }
        return conn
    }

    /** Lê a resposta; em erro, traduz o código para uma frase com o nome do provedor. */
    private fun read(conn: HttpURLConnection): JSONObject {
        val who = provider().name.substringBefore(" (")
        val code = try { conn.responseCode } catch (e: java.io.IOException) {
            throw ApiError("Não consegui falar com $who: ${e.message ?: "sem conexão"}. Confira a internet e o endereço.")
        }
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            // mostra a explicação do próprio servidor, quando houver
            val detail = runCatching {
                val e = JSONObject(text).opt("error")
                (e as? JSONObject)?.optString("message") ?: (e as? String)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: text.take(200)
            throw ApiError(
                when (code) {
                    401, 403 -> "A chave de $who não foi aceita. Confira em ⋮ → Provedor, modelo e chave."
                    402 -> "Faltam créditos na sua conta de $who."
                    404 -> "$who não achou o modelo \"${Prefs.aiModel}\" (ou o endereço está errado). $detail"
                    429 -> "O limite da sua conta de $who acabou por agora. Tente mais tarde ou troque de modelo."
                    else -> "$who respondeu com erro $code: $detail"
                },
                code,
            )
        }
        return runCatching { JSONObject(text) }.getOrElse { throw ApiError("$who mandou uma resposta que não entendi. Confira o endereço.") }
    }

    private fun post(body: JSONObject): JSONObject {
        val conn = open(endpoint())
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        } catch (e: java.io.IOException) {
            throw ApiError("Não consegui falar com ${provider().name.substringBefore(" (")}: ${e.message ?: "sem conexão"}. Confira a internet e o endereço.")
        }
        return read(conn)
    }

    /** Modelos que a chave enxerga no provedor atual (GET /models), em ordem alfabética. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val res = read(open(baseUrl() + "/models"))
        val arr = res.optJSONArray("data") ?: res.optJSONArray("models") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            when (val m = arr.opt(i)) {
                is JSONObject -> m.optString("id").ifBlank { m.optString("name") }
                is String -> m
                else -> null
            }?.takeIf { it.isNotBlank() }
        }.distinct().sorted()
    }

    /** Envia a pergunta com o histórico recente. Devolve a resposta (com as mudanças feitas na rotina). */
    suspend fun ask(history: List<Msg>, question: String): Msg = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system() + "\n\n" + context()))
        for (m in history.filter { it.role == "user" || it.role == "assistant" }.takeLast(12)) {
            messages.put(JSONObject().put("role", m.role).put("content", m.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", question))

        val session = Session()
        var answer = ""
        var useTools = true
        for (round in 0 until 6) {
            // sem "temperature": modelos com raciocínio (Kimi, DeepSeek, GPT-5) recusam valores fora do padrão
            val body = JSONObject().put("model", Prefs.aiModel).put("messages", messages)
            if (useTools) body.put("tools", tools())
            val res = try {
                post(body)
            } catch (e: ApiError) {
                // modelo sem suporte a ferramentas: segue só conversando, sem mexer na rotina
                val aboutTools = e.message.orEmpty().contains("tool", ignoreCase = true) || e.message.orEmpty().contains("function", ignoreCase = true)
                if (round == 0 && useTools && e.code in listOf(400, 404, 422) && aboutTools) {
                    useTools = false
                    post(body.apply { remove("tools") })
                } else throw e
            }
            val msg = res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: throw ApiError("Resposta vazia de ${provider().name.substringBefore(" (")}.")
            val calls = msg.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                answer = msg.optString("content", "").let { if (it == "null") "" else it }.trim()
                break
            }
            // devolve a vez da assistente exatamente como veio (inclui reasoning_content, que os
            // modelos com raciocínio exigem de volta nas rodadas de ferramenta) e responde cada chamada
            if (!msg.has("content")) msg.put("content", JSONObject.NULL)
            messages.put(msg)
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
