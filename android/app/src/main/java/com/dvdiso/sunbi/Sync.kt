package com.dvdiso.sunbi

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.URL
import kotlin.coroutines.resume

private const val TAG = "SunBiSync"

/**
 * Par de sincronização. [client] = só nos chama (ex.: o plugin do Noctalia no PC, que não tem
 * servidor); a gente não tenta chamá-lo de volta, só mostra quando foi a última troca.
 */
data class Peer(val device: String, val name: String, val addr: String, val ok: Boolean?, val seen: Long, val client: Boolean = false)

object SyncManager {
    const val PORT = 47321
    const val TYPE = "_sunbi._tcp."

    val peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pushJob: Job? = null

    fun loadManualPeers() {
        for (addr in Prefs.manualPeers) addPeer("manual:$addr", addr, addr)
    }

    fun addPeer(device: String, name: String, addr: String) {
        if (device == Prefs.device) return
        val old = peers.value[device]
        peers.update { it + (device to Peer(device, name, addr, old?.ok, System.currentTimeMillis())) }
        if (old == null || old.addr != addr) scope.launch { syncOne(device) }
    }

    fun removePeer(device: String) = peers.update { it - device }

    /** Empurra o estado para todos os pares logo após uma mudança (agrupando mudanças rápidas). */
    fun pushSoon() {
        if (!Prefs.syncEnabled) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(400)
            syncAll()
        }
    }

    fun syncAllAsync() = scope.launch { syncAll() }

    suspend fun syncAll() {
        for ((d, p) in peers.value.toList()) if (!p.client) syncOne(d)
    }

    /** Registra um par que só chama a gente (sem servidor próprio). */
    fun sawClient(device: String, name: String, ip: String) {
        if (device == Prefs.device) return
        peers.update { it + (device to Peer(device, name, ip, true, System.currentTimeMillis(), client = true)) }
    }

    fun syncOne(device: String): Boolean {
        val peer = peers.value[device] ?: return false
        if (peer.client) return true
        var ok = false
        try {
            val conn = URL("http://${peer.addr}/v1/merge").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 4000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-SunBi-Key", Prefs.key)
            val body = Repo.snapshot().put("name", Prefs.name)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                Repo.merge(JSONObject(body).optJSONObject("entries"))
                ok = true
            } else {
                Log.w(TAG, "${peer.name}: HTTP ${conn.responseCode}")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "${peer.name}: ${e.message}")
        }
        peers.update { m ->
            val p = m[device] ?: return@update m
            m + (device to p.copy(ok = ok, seen = if (ok) System.currentTimeMillis() else p.seen))
        }
        return ok
    }

    fun start(ctx: Context) {
        if (!Prefs.syncEnabled) return
        ContextCompat.startForegroundService(ctx, Intent(ctx, SyncService::class.java))
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, SyncService::class.java))
    }
}

/** Servidor HTTP do protocolo (docs/PROTOCOL.md). */
class PeerServer : NanoHTTPD(SyncManager.PORT) {
    override fun serve(s: IHTTPSession): Response {
        val key = s.headers["x-sunbi-key"] ?: ""
        if (!key.equals(Prefs.key, ignoreCase = true)) {
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "código de pareamento inválido"))
        }
        return when (s.uri) {
            "/v1/hello" -> json(Response.Status.OK, JSONObject().put("device", Prefs.device).put("name", Prefs.name).put("v", 1))
            "/v1/state" -> json(Response.Status.OK, Repo.snapshot())
            "/v1/merge" -> {
                if (s.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, JSONObject())
                val len = s.headers["content-length"]?.toIntOrNull() ?: 0
                if (len > 5_000_000) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "grande demais"))
                val buf = ByteArray(len)
                var off = 0
                while (off < len) {
                    val n = s.inputStream.read(buf, off, len - off)
                    if (n < 0) break
                    off += n
                }
                val body = runCatching { JSONObject(String(buf, 0, off, Charsets.UTF_8)) }.getOrNull()
                    ?: return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "JSON inválido"))
                Repo.merge(body.optJSONObject("entries"))
                val dev = body.optString("device")
                val name = body.optString("name").ifBlank { dev }
                if (!body.optBoolean("listen", true)) {
                    // Cliente sem servidor (plugin do Noctalia): a troca já foi feita nesta chamada.
                    if (dev.isNotEmpty()) SyncManager.sawClient(dev, name, s.remoteIpAddress)
                } else if (dev.isNotEmpty() && dev != Prefs.device && SyncManager.peers.value.values.none { it.device == dev || it.addr.startsWith(s.remoteIpAddress + ":") }) {
                    // Quem nos procurou vira par também (ajuda quando o mDNS só funciona num sentido).
                    SyncManager.addPeer(dev, name, "${s.remoteIpAddress}:${SyncManager.PORT}")
                }
                json(Response.Status.OK, Repo.snapshot())
            }
            else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "rota desconhecida"))
        }
    }

    private fun json(status: Response.Status, obj: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", obj.toString())
}

/** Serviço em primeiro plano: servidor, anúncio e busca mDNS, sincronização periódica. */
class SyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: PeerServer? = null
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null
    private val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = Notifications.sync(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.SYNC_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.SYNC_ID, n)
        }

        server = PeerServer().also {
            try {
                it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            } catch (e: Exception) {
                Log.e(TAG, "servidor não iniciou: ${e.message}")
            }
        }

        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        lock = wifi.createMulticastLock("sunbi").apply {
            setReferenceCounted(false)
            acquire()
        }

        nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        register()
        discover()
        scope.launch { resolveLoop() }

        SyncManager.loadManualPeers()
        scope.launch {
            while (true) {
                SyncManager.syncAll()
                delay(60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun register() {
        val info = NsdServiceInfo().apply {
            serviceName = Prefs.device
            serviceType = SyncManager.TYPE
            port = SyncManager.PORT
            setAttribute("id", Prefs.device)
            setAttribute("v", "1")
            setAttribute("name", Prefs.name)
        }
        regListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "anunciado como ${i.serviceName}") }
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { Log.w(TAG, "anúncio falhou: $code") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener) }
    }

    private fun discover() {
        discListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { Log.w(TAG, "busca falhou: $code") }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                if (!i.serviceName.startsWith(Prefs.device)) toResolve.trySend(i)
            }
            override fun onServiceLost(i: NsdServiceInfo) {}
        }
        runCatching { nsd?.discoverServices(SyncManager.TYPE, NsdManager.PROTOCOL_DNS_SD, discListener) }
    }

    /** O NsdManager só resolve um serviço por vez em versões antigas: fila. */
    @Suppress("DEPRECATION")
    private suspend fun resolveLoop() {
        for (info in toResolve) {
            val resolved = withTimeoutOrNull(8000) {
                suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                    try {
                        nsd?.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(i: NsdServiceInfo, code: Int) { if (cont.isActive) cont.resume(null) }
                            override fun onServiceResolved(i: NsdServiceInfo) { if (cont.isActive) cont.resume(i) }
                        }) ?: cont.resume(null)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } ?: continue
            val host = resolved.host ?: continue
            val ip = host.hostAddress ?: continue
            val addr = if (host is Inet6Address) "[${ip.substringBefore('%')}]" else ip
            val attrs = resolved.attributes
            val id = attrs["id"]?.toString(Charsets.UTF_8) ?: resolved.serviceName
            val name = attrs["name"]?.toString(Charsets.UTF_8) ?: id
            SyncManager.addPeer(id, name, "$addr:${resolved.port}")
        }
    }

    override fun onDestroy() {
        runCatching { discListener?.let { nsd?.stopServiceDiscovery(it) } }
        runCatching { regListener?.let { nsd?.unregisterService(it) } }
        runCatching { server?.stop() }
        runCatching { lock?.release() }
        toResolve.close()
        scope.cancel()
        super.onDestroy()
    }
}
