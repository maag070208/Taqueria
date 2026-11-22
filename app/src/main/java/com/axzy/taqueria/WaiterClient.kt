package com.axzy.taqueria

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

sealed interface Link {
    data object Searching : Link
    data class Connecting(val host: String) : Link
    data class Connected(val host: String) : Link
    /** Cocina no aceptó a esta mesera (p. ej. el plan ya tiene todas sus meseras). */
    data class Rejected(val message: String) : Link
}

/**
 * Conexión del mesero con la cocina: la busca por mDNS (o usa la IP fija que
 * se configure), mantiene el estado sincronizado y reconecta sola.
 */
class WaiterClient(
    private val context: Context,
    /** Identifica a este teléfono ante la cocina (no cambia al reiniciar la app). */
    private val waiterId: String = java.util.UUID.randomUUID().toString(),
    /** Fallas de esta app que se le pasan a la cocina al conectar. */
    private val crashes: CrashReporter? = null,
) {
    private val tag = "WaiterClient"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<ServerMsg.Reply>>()
    private val nextReq = AtomicLong(1)

    private val _link = MutableStateFlow<Link>(Link.Searching)
    val link = _link.asStateFlow()
    private val _state = MutableStateFlow(AppState())
    val state = _state.asStateFlow()
    /** Versión nueva que avisó la cocina (la publica el proveedor en la Central). */
    private val _latestApp = MutableStateFlow<LatestApp?>(null)
    val latestApp = _latestApp.asStateFlow()

    /** Diferencia reloj cocina − reloj mesero, para que los tiempos coincidan en ambos teléfonos. */
    @Volatile var clockOffset = 0L
        private set

    /** [manualHost] vacío = buscar automáticamente. */
    fun start(manualHost: String) {
        loop?.cancel()
        closeSocket()
        _link.value = Link.Searching
        loop = scope.launch {
            while (isActive) {
                try {
                    val target = parseHost(manualHost) ?: run {
                        if (_link.value !is Link.Rejected) _link.value = Link.Searching
                        discover()
                    }
                    session(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.i(tag, "Sin conexión: ${e.message}")
                }
                failPending("Se perdió la conexión con cocina")
                // Rechazada por el plan: se reintenta despacio por si otra mesera se desconecta.
                delay(if (_link.value is Link.Rejected) 20_000 else 1_500)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        closeSocket()
        failPending("Desconectado")
    }

    suspend fun createOrder(
        clientKey: String, table: String, accountId: String?, lines: List<LineRequest>, extras: List<PersonExtras> = emptyList(),
    ): Order =
        request { ClientMsg.CreateOrder(it, clientKey, table, accountId, lines, extras) }.order
            ?: throw IOException("Respuesta inválida de cocina")

    suspend fun editOrder(orderId: String, lines: List<LineRequest>, extras: List<PersonExtras> = emptyList()): Order =
        request { ClientMsg.EditOrder(it, orderId, lines, extras) }.order ?: throw IOException("Respuesta inválida de cocina")

    suspend fun setOrderStatus(orderId: String, status: OrderStatus) {
        request { ClientMsg.SetOrderStatus(it, orderId, status) }
    }

    suspend fun cancelAccount(accountId: String) {
        request { ClientMsg.CancelAccount(it, accountId) }
    }

    suspend fun restoreAccount(accountId: String) {
        request { ClientMsg.RestoreAccount(it, accountId) }
    }

    /** Devuelve el id del pago, para poder deshacerlo. */
    suspend fun addPayment(
        clientKey: String, accountId: String, amountCents: Long, method: PayMethod, receivedCents: Long?, deliver: Boolean,
    ): String =
        request { ClientMsg.AddPayment(it, clientKey, accountId, amountCents, method, receivedCents, deliver) }.paymentId
            ?: throw IOException("Respuesta inválida de cocina")

    suspend fun voidPayment(accountId: String, paymentId: String) {
        request { ClientMsg.VoidPayment(it, accountId, paymentId) }
    }

    /** [direct]: también antes de que cocina acepte a la mesera (solo el saludo y las fallas). */
    private suspend fun request(direct: Boolean = false, build: (Long) -> ClientMsg): ServerMsg.Reply {
        if (!direct && _link.value !is Link.Connected) throw IOException("Sin conexión con cocina")
        val w = writer ?: throw IOException("Sin conexión con cocina")
        val reqId = nextReq.getAndIncrement()
        val deferred = CompletableDeferred<ServerMsg.Reply>()
        pending[reqId] = deferred
        try {
            withContext(Dispatchers.IO) {
                synchronized(w) {
                    w.write(AppJson.encodeToString(build(reqId)))
                    w.write("\n")
                    w.flush()
                }
            }
            val reply = withTimeout(8_000) { deferred.await() }
            reply.error?.let { throw ValidationException(it) }
            return reply
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IOException("Cocina no respondió, revisa la conexión")
        } finally {
            pending.remove(reqId)
        }
    }

    private fun session(target: InetSocketAddress) {
        // Si cocina rechazó a esta mesera, el aviso se queda hasta que la acepte.
        if (_link.value !is Link.Rejected) _link.value = Link.Connecting(target.hostString)
        val s = Socket()
        socket = s
        s.connect(target, 4_000)
        // Cocina manda ping cada 10 s; si no llega nada en 25 s la conexión murió.
        s.soTimeout = (KitchenServer.PING_MS * 2.5).toInt()
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
        // Conectada solo cuando cocina la acepta; mientras tanto ya se ven las cuentas.
        // No se cancela al cerrar: si cocina contestó "rechazada" y cerró, el aviso igual debe llegar.
        scope.launch { introduce(target.hostString, s) }
        try {
            while (true) {
                val line = s.readLineLimited(reader, MAX_STATE_CHARS) ?: throw IOException("Cocina cerró la conexión")
                when (val msg = AppJson.decodeFromString<ServerMsg>(line)) {
                    is ServerMsg.State -> {
                        clockOffset = msg.serverTime - System.currentTimeMillis()
                        _state.value = msg.state
                        _latestApp.value = msg.latestApp
                    }
                    is ServerMsg.Reply -> pending[msg.reqId]?.complete(msg)
                    ServerMsg.Ping -> Unit
                }
            }
        } finally {
            closeSocket()
        }
    }

    /** Se presenta con cocina y le pasa las fallas guardadas de este teléfono. */
    private suspend fun introduce(host: String, session: Socket) {
        try {
            request(direct = true) { ClientMsg.Hello(it, waiterId, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}") }
        } catch (e: ValidationException) {
            _link.value = Link.Rejected(e.message ?: "Cocina no aceptó la conexión")
            if (socket === session) closeSocket()
            return
        } catch (e: IOException) {
            return
        }
        // La conexión pudo caerse mientras cocina contestaba.
        if (socket !== session) return
        _link.value = Link.Connected(host)
        val errors = crashes?.pending().orEmpty().take(20)
        if (errors.isNotEmpty()) {
            runCatching { request(direct = true) { ClientMsg.ReportErrors(it, errors) } }
                .onSuccess { crashes?.remove(errors.map { e -> e.id }) }
        }
    }

    private suspend fun discover(): InetSocketAddress {
        val nsd = context.getSystemService(NsdManager::class.java) ?: throw IOException("mDNS no disponible")
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        // Muchos teléfonos filtran multicast con la pantalla apagada o sin este candado.
        val lock = wifi?.createMulticastLock("taqueria")?.apply { setReferenceCounted(false); acquire() }
        try {
            return suspendCancellableCoroutine { cont ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onServiceFound(info: NsdServiceInfo) {
                        if (!info.serviceType.startsWith("_taqueria")) return
                        val discovery = this
                        @Suppress("DEPRECATION")
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(r: NsdServiceInfo) {
                                @Suppress("DEPRECATION")
                                val host = r.host?.hostAddress ?: return
                                if (!cont.isActive) return
                                runCatching { nsd.stopServiceDiscovery(discovery) }
                                cont.resume(InetSocketAddress(host, r.port))
                            }
                            override fun onResolveFailed(r: NsdServiceInfo, code: Int) {
                                Log.w(tag, "No se pudo resolver: $code")
                            }
                        })
                    }
                    override fun onServiceLost(info: NsdServiceInfo) {}
                    override fun onDiscoveryStarted(type: String) {}
                    override fun onDiscoveryStopped(type: String) {}
                    override fun onStartDiscoveryFailed(type: String, code: Int) {
                        Log.w(tag, "Búsqueda falló: $code")
                    }
                    override fun onStopDiscoveryFailed(type: String, code: Int) {}
                }
                nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
            }
        } finally {
            lock?.release()
        }
    }

    private fun closeSocket() {
        writer = null
        runCatching { socket?.close() }
        socket = null
        if (_link.value is Link.Connected || _link.value is Link.Connecting) _link.value = Link.Searching
    }

    private fun failPending(reason: String) {
        pending.values.forEach { it.completeExceptionally(IOException(reason)) }
        pending.clear()
    }

    private fun parseHost(raw: String): InetSocketAddress? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val host = t.substringBefore(":")
        val port = t.substringAfter(":", "").toIntOrNull() ?: DEFAULT_PORT
        return InetSocketAddress(host, port)
    }

    companion object {
        private const val MAX_STATE_CHARS = 4 * 1024 * 1024
    }
}
