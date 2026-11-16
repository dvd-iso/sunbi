package com.dvdiso.sunbi

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Modo avançado: uma página de configuração servida pelo próprio app, para abrir no navegador
 * do computador (mesma rede). Edita as duas rotinas, sinais, planos, contatos, ajustes e os
 * registros dos últimos dias, além de exportar e importar um backup.
 *
 * Só responde com o modo ligado em Ajustes e com o PIN que o app mostra. Cinco PINs errados
 * desligam o modo (quem tem o celular na mão liga de novo).
 */
object WebConfig {
    class Res(val status: Int, val body: String, val type: String = "application/json; charset=utf-8")

    private var fails = 0
    private val HM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val PLAN_SECTIONS = listOf("up", "down", "warn", "cope", "distract", "safe", "reasons")

    fun newPin(): String = "%06d".format(SecureRandom().nextInt(1_000_000))

    /** Desliga sozinho depois deste tempo sem nenhum acesso à página. */
    const val IDLE_MS = 60 * 60_000L

    @Volatile var lastUse = 0L
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun enable(ctx: Context?, on: Boolean) {
        Prefs.webConfig = on
        if (on && Prefs.webPin.length != 6) Prefs.webPin = newPin()
        fails = 0
        lastUse = System.currentTimeMillis()
        ctx?.let { keepAwake(it, on) }
    }

    /**
     * Com a tela apagada, o Wi-Fi de muitos celulares (Samsung incluso) entra em economia e
     * derruba conexões que chegam de outro aparelho. Enquanto o modo está ligado, o Wi-Fi e a
     * CPU ficam acordados; o desligamento automático limita o gasto de bateria.
     */
    @Synchronized
    fun keepAwake(ctx: Context, on: Boolean) {
        val app = ctx.applicationContext
        if (on) {
            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                wifiLock = runCatching {
                    app.getSystemService(WifiManager::class.java)
                        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "sunbi:webconfig")
                        .apply { setReferenceCounted(false); acquire() }
                }.getOrNull()
            }
            if (wakeLock == null) {
                wakeLock = runCatching {
                    app.getSystemService(PowerManager::class.java)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sunbi:webconfig")
                        .apply { setReferenceCounted(false); acquire(IDLE_MS + 5 * 60_000L) }
                }.getOrNull()
            }
        } else {
            runCatching { wifiLock?.release() }
            runCatching { wakeLock?.release() }
            wifiLock = null
            wakeLock = null
        }
    }

    /** Chamado a cada minuto pelo serviço: desliga o modo esquecido ligado. */
    fun checkIdle(ctx: Context, now: Long = System.currentTimeMillis()) {
        if (Prefs.webConfig && now - lastUse > IDLE_MS) enable(ctx, false)
    }

    /** Trata uma chamada. Devolve null quando a rota não é da página (segue para o protocolo de sync). */
    fun handle(ctx: Context, method: String, uri: String, pin: String?, query: Map<String, String>, body: String?): Res? {
        if (uri == "/" || uri == "/index.html") {
            if (!Prefs.webConfig) return Res(404, OFF_HTML, "text/html; charset=utf-8")
            lastUse = System.currentTimeMillis()
            val html = runCatching { ctx.assets.open("web/index.html").bufferedReader().readText() }.getOrNull()
                ?: return Res(500, "<p>página não encontrada no app</p>", "text/html; charset=utf-8")
            return Res(200, html, "text/html; charset=utf-8")
        }
        if (!uri.startsWith("/api/")) return null
        if (!Prefs.webConfig) return err(403, "modo avançado desligado")
        lastUse = System.currentTimeMillis()
        if (pin == null || pin != Prefs.webPin) {
            fails++
            if (fails >= 5) {
                enable(ctx, false)
                return err(403, "PIN errado 5 vezes: o modo avançado foi desligado; ligue de novo no app")
            }
            return err(401, "PIN errado")
        }
        fails = 0
        return runCatching {
            when (uri) {
                "/api/config" -> if (method == "POST") {
                    applyConfig(JSONObject(body ?: "{}"))
                    Res(200, config().toString())
                } else Res(200, config().toString())
                "/api/days" -> Res(200, days(query["n"]?.toIntOrNull()?.coerceIn(1, 366) ?: 60).toString())
                "/api/day" -> if (method == "POST") {
                    val o = JSONObject(body ?: "{}")
                    val day = o.optString("day")
                    if (!DAY.matches(day)) return err(400, "dia inválido")
                    applyDay(day, o)
                    Res(200, dayJson(day).toString())
                } else err(405, "use POST")
                "/api/export" -> Res(200, Repo.snapshot().toString())
                "/api/import" -> if (method == "POST") {
                    val n = Repo.merge(JSONObject(body ?: "{}").optJSONObject("entries")).size
                    Res(200, JSONObject().put("merged", n).toString())
                } else err(405, "use POST")
                else -> err(404, "rota desconhecida")
            }
        }.getOrElse { err(400, it.message ?: "pedido inválido") }
    }

    private fun err(code: Int, msg: String) = Res(code, JSONObject().put("error", msg).toString())

    // ── leitura ─────────────────────────────────────────────────────────────

    fun config(): JSONObject {
        val plan = JSONObject()
        for (s in PLAN_SECTIONS) plan.put(s, JSONArray(Repo.planList(s)))
        val contacts = JSONArray()
        for (c in Repo.contacts()) contacts.put(JSONObject().put("name", c.name).put("role", c.role).put("phone", c.phone))
        val signs = JSONArray()
        for (s in Repo.signs()) signs.put(JSONObject().put("id", s.id).put("label", s.label).put("pole", s.pole))
        val st = Game.stats()
        return JSONObject()
            .put("version", BuildConfig.VERSION_NAME)
            .put("device", Prefs.device)
            .put("deviceName", Prefs.name)
            .put("tasks", Repo.tasksToJson(Repo.tasks()))
            .put("tasks_off", Repo.tasksToJson(Repo.offTasks()))
            .put("signs", signs)
            .put("plan", plan)
            .put("contacts", contacts)
            .put("off", JSONObject().put("weekends", Repo.offWeekends()).put("holidays", Repo.offHolidays()))
            .put("vacation", Repo.vacationSince() ?: JSONObject.NULL)
            .put("prefs", JSONObject()
                .put("userName", Prefs.userName).put("focusArea", Prefs.focusArea)
                .put("alarms", Prefs.alarmsEnabled).put("alarmSound", Prefs.alarmSound)
                .put("uiSounds", Prefs.uiSounds).put("dynamicColor", Prefs.dynamicColor)
                .put("aiModel", Prefs.aiModel).put("reportName", Prefs.reportName))
            .put("stats", JSONObject().put("xp", st.xp).put("level", st.level).put("levelName", st.levelName)
                .put("streak", st.streak).put("bestStreak", st.bestStreak).put("badges", JSONArray(st.badges.sorted())))
    }

    private fun dayJson(day: String): JSONObject {
        val o = JSONObject().put("day", day)
        o.put("off", Repo.isOff(day))
        o.put("humor", Repo.humor(day) ?: JSONObject.NULL)
        o.put("bed", Repo.bed(day) ?: JSONObject.NULL)
        o.put("wake", Repo.wake(day) ?: JSONObject.NULL)
        o.put("sleep", Repo.sleep(day) ?: JSONObject.NULL)
        for (f in listOf("energy", "focus", "irrit", "anx")) o.put(f, Repo.level(day, f) ?: JSONObject.NULL)
        o.put("signs", JSONArray(Repo.signsOn(day).sorted()))
        o.put("events", JSONArray(Repo.eventsOn(day).sorted()))
        o.put("note", Repo.note(day))
        o.put("done", JSONArray(Repo.tasksFor(day).filter { Repo.done(day, it.id) }.map { it.id }))
        o.put("total", Repo.tasksFor(day).size)
        return o
    }

    private fun days(n: Int): JSONArray {
        val arr = JSONArray()
        for (d in lastDays(today(), n)) arr.put(dayJson(d))
        return arr
    }

    // ── escrita ─────────────────────────────────────────────────────────────

    private fun parseTasks(arr: JSONArray): List<Task> {
        val out = ArrayList<Task>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val time = o.optString("time")
            val label = o.optString("label").trim()
            if (!HM.matches(time)) throw IllegalArgumentException("horário inválido: \"$time\"")
            if (label.isEmpty()) throw IllegalArgumentException("tarefa sem texto às $time")
            var id = o.optString("id").ifBlank { newId("w") }
            if (!seen.add(id)) { id = newId("w"); seen += id }
            out += Task(id, time, label, med = o.optBoolean("med", false), anchor = o.optBoolean("anchor", false), step = o.optString("step", "").trim())
        }
        return out
    }

    private fun strings(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }

    fun applyConfig(o: JSONObject) {
        o.optJSONArray("tasks")?.let { Repo.setTasks(parseTasks(it)) }
        o.optJSONArray("tasks_off")?.let { Repo.setOffTasks(parseTasks(it)) }
        o.optJSONArray("signs")?.let { arr ->
            val list = (0 until arr.length()).mapNotNull { i ->
                val s = arr.getJSONObject(i)
                val label = s.optString("label").trim()
                if (label.isEmpty()) null
                else Sign(s.optString("id").ifBlank { newId(if (s.optString("pole") == "down") "d_" else "u_") }, label, if (s.optString("pole") == "down") "down" else "up")
            }
            Repo.setSigns(list)
        }
        o.optJSONObject("plan")?.let { p ->
            for (s in PLAN_SECTIONS) if (p.has(s)) Repo.setPlanList(s, strings(p.optJSONArray(s)))
        }
        o.optJSONArray("contacts")?.let { arr ->
            val list = (0 until arr.length()).mapNotNull { i ->
                val c = arr.getJSONObject(i)
                val name = c.optString("name").trim()
                if (name.isEmpty()) null else Contact(name, c.optString("role", "Pessoa de confiança"), c.optString("phone").trim())
            }
            Repo.setContacts(list)
        }
        o.optJSONObject("off")?.let { f ->
            if (f.has("weekends")) Repo.set("off/weekends", f.optBoolean("weekends"))
            if (f.has("holidays")) Repo.set("off/holidays", f.optBoolean("holidays"))
        }
        o.optJSONObject("prefs")?.let { p ->
            if (p.has("userName")) Prefs.userName = p.optString("userName")
            if (p.has("focusArea")) Prefs.focusArea = p.optString("focusArea").takeIf { it in listOf("bipolar", "tdah", "both") } ?: "both"
            if (p.has("alarms")) Prefs.alarmsEnabled = p.optBoolean("alarms")
            if (p.has("alarmSound")) Prefs.alarmSound = if (p.optString("alarmSound") == "girassol") "girassol" else "amanhecer"
            if (p.has("uiSounds")) Prefs.uiSounds = p.optBoolean("uiSounds")
            if (p.has("dynamicColor")) Prefs.dynamicColor = p.optBoolean("dynamicColor")
            if (p.has("aiModel")) p.optString("aiModel").takeIf { m -> SunAI.MODELS.any { it.first == m } }?.let { Prefs.aiModel = it }
            if (p.has("reportName")) Prefs.reportName = p.optString("reportName")
        }
    }

    private fun applyDay(day: String, o: JSONObject) {
        if (o.has("humor")) Repo.set("$day/humor", if (o.isNull("humor")) null else o.optInt("humor").coerceIn(-3, 3))
        if (o.has("bed") || o.has("wake")) {
            val bed = if (o.has("bed")) o.optString("bed").takeIf { HM.matches(it) } else Repo.bed(day)
            val wake = if (o.has("wake")) o.optString("wake").takeIf { HM.matches(it) } else Repo.wake(day)
            Repo.setSleepTimes(day, bed, wake)
        }
        for (f in listOf("energy", "focus", "irrit", "anx")) if (o.has(f)) Repo.setLevel(day, f, if (o.isNull(f)) null else o.optInt(f))
        if (o.has("note")) Repo.set("$day/note", o.optString("note"))
        if (o.has("signs")) Repo.setStrSet("$day/signs", strings(o.optJSONArray("signs")).toSet())
        if (o.has("events")) Repo.setStrSet("$day/events", strings(o.optJSONArray("events")).toSet())
        if (o.has("done")) {
            val ids = strings(o.optJSONArray("done")).toSet()
            for (t in Repo.tasksFor(day)) {
                val now = Repo.done(day, t.id)
                if (t.id in ids && !now) Repo.set("$day/done/${t.id}", true)
                if (t.id !in ids && now) Repo.set("$day/done/${t.id}", false)
            }
        }
        if (o.has("off")) Repo.set("$day/off", if (o.isNull("off")) null else o.optBoolean("off"))
    }

    private const val OFF_HTML = """<!doctype html><meta charset="utf-8"><title>SunBi</title>
<body style="font-family:system-ui;max-width:32rem;margin:4rem auto;padding:0 1rem;color:#1f1b13;background:#fff8f2">
<h1>🌻 SunBi</h1><p>O <b>modo avançado</b> está desligado neste aparelho.</p>
<p>No app, abra <b>Ajustes → Modo avançado</b>, ligue e recarregue esta página.</p></body>"""
}
