package com.dvdiso.sunflowerclocks

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

data class Task(val id: String, val time: String, val label: String, val med: Boolean = false)

/** Registro last-writer-wins: valor, momento (ms) e aparelho. Veja docs/PROTOCOL.md. */
data class Rec(val v: Any?, val t: Long, val d: String)

fun today(): String = LocalDate.now().toString()

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("sunflower", Context.MODE_PRIVATE)
        if (!sp.contains("device")) {
            val prefix = if (Build.MANUFACTURER.equals("Oculus", true) || Build.MANUFACTURER.equals("Meta", true)) "quest" else "android"
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
    var manualPeers: Set<String>
        get() = sp.getStringSet("manualPeers", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("manualPeers", v).apply()
    var scheduledIds: Set<String>
        get() = sp.getStringSet("scheduled", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("scheduled", v).apply()
}

object Repo {
    private lateinit var file: File
    private val entries = HashMap<String, Rec>()
    private val listeners = CopyOnWriteArrayList<(Set<String>) -> Unit>()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    val DEFAULT_TASKS = listOf(
        Task("t0700", "07:00", "Comer e tomar remédio", true),
        Task("t0715", "07:15", "Escovar os dentes e se arrumar"),
        Task("t0930", "09:30", "Beber água"),
        Task("t1200", "12:00", "Almoço: pare e coma"),
        Task("t1245", "12:45", "Tomar o remédio (depois do almoço)", true),
        Task("t1400", "14:00", "Beber água"),
        Task("t1530", "15:30", "Lanche e água"),
        Task("t1700", "17:00", "Beber água"),
        Task("t1900", "19:00", "Jantar"),
        Task("t1945", "19:45", "Exercício leve"),
        Task("t2030", "20:30", "Tomar banho"),
        Task("t2100", "21:00", "Hora do Astral (até 22h)"),
        Task("t2230", "22:30", "Desligar telas e tomar remédio", true),
        Task("t2240", "22:40", "Escovar os dentes e ir para a cama"),
    )

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "state.json")
        if (file.exists()) {
            runCatching {
                val obj = JSONObject(file.readText()).optJSONObject("entries") ?: JSONObject()
                for (k in obj.keys()) parseRec(obj.optJSONObject(k))?.let { entries[k] = it }
            }
        }
        if (!entries.containsKey("tasks")) {
            // t = 1: qualquer lista editada em outro aparelho vence a padrão
            entries["tasks"] = Rec(tasksToJson(DEFAULT_TASKS), 1, "")
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
        for (l in listeners) runCatching { l(keys) }
    }

    @Synchronized
    fun get(key: String): Any? = entries[key]?.v

    fun tasks(): List<Task> {
        val arr = get("tasks") as? JSONArray ?: return DEFAULT_TASKS
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Task(o.optString("id"), o.optString("time"), o.optString("label"), o.optBoolean("med", false))
        }.sortedBy { it.time }
    }

    fun setTasks(list: List<Task>) = set("tasks", tasksToJson(list))

    private fun tasksToJson(list: List<Task>): JSONArray {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject().put("id", t.id).put("time", t.time).put("label", t.label)
            if (t.med) o.put("med", true)
            arr.put(o)
        }
        return arr
    }

    fun done(day: String, id: String): Boolean = get("$day/done/$id") == true
    fun setDone(day: String, id: String, v: Boolean) = set("$day/done/$id", v)
    fun mood(day: String): Int? = (get("$day/mood") as? Number)?.toInt()
    fun sleep(day: String): Double? = (get("$day/sleep") as? Number)?.toDouble()
    fun note(day: String): String = get("$day/note") as? String ?: ""
    fun hasDay(day: String): Boolean = synchronized(this) { entries.keys.any { it.startsWith("$day/") } }

    fun percent(day: String): Int {
        val t = tasks()
        if (t.isEmpty()) return 0
        return 100 * t.count { done(day, it.id) } / t.size
    }
}
