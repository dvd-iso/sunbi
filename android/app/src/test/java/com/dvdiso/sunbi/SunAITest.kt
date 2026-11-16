package com.dvdiso.sunbi

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/** Conversa com um "OpenCode Go" falso: pedido de ferramenta, execução e resposta final. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SunAITest {
    private lateinit var server: HttpServer
    private val bodies = mutableListOf<JSONObject>()
    private var auth = ""

    @Before
    fun start() {
        Seed.clear()
        Prefs.aiKey = "chave-de-teste"
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            auth = ex.requestHeaders.getFirst("Authorization") ?: ""
            val body = JSONObject(ex.requestBody.readBytes().toString(Charsets.UTF_8))
            bodies += body
            val msgs = body.getJSONArray("messages")
            val last = msgs.getJSONObject(msgs.length() - 1)
            val reply = if (last.getString("role") == "tool") {
                JSONObject().put("role", "assistant").put("content", "Coloquei **beber água** às 16:00. Pequeno e fácil.")
            } else {
                val call = JSONObject().put("id", "c1").put("type", "function").put("function", JSONObject()
                    .put("name", "add_task")
                    .put("arguments", JSONObject().put("routine", "semana").put("time", "16:00").put("label", "Beber água").toString()))
                JSONObject().put("role", "assistant").put("content", JSONObject.NULL).put("tool_calls", JSONArray().put(call))
            }
            val out = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", reply))).toString().toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        SunAI.ENDPOINT = "http://127.0.0.1:${server.address.port}/v1/chat/completions"
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun toolCallChangesRoutineAndAnswers() = runBlocking {
        val before = Repo.tasks().size
        val m = SunAI.ask(emptyList(), "Ajuste minha rotina")
        assertEquals("Bearer chave-de-teste", auth)
        assertEquals(2, bodies.size) // pedido + resposta da ferramenta
        assertTrue(bodies[0].getJSONArray("messages").getJSONObject(0).getString("content").contains("ROTINA DE SEMANA"))
        assertEquals(3, bodies[0].getJSONArray("tools").length())
        assertTrue(m.text.contains("beber água"))
        assertEquals(1, m.changes.size)
        assertEquals(before + 1, Repo.tasks().size)
        assertTrue(Repo.tasks().any { it.time == "16:00" && it.label == "Beber água" })
        // a ferramenta respondeu com sucesso para o modelo
        val toolMsg = bodies[1].getJSONArray("messages").let { it.getJSONObject(it.length() - 1) }
        assertTrue(toolMsg.getString("content").startsWith("ok"))
        SunAI.undo(m)
        assertEquals(before, Repo.tasks().size)
    }
}
