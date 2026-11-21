package com.axzy.taqueria

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Servidor TCP del teléfono de cocina. Cada mesero conectado recibe el estado
 * completo en cada cambio; se anuncia en la red local por mDNS para que el
 * mesero lo encuentre sin escribir la IP.
 */
class KitchenServer(
    private val context: Context,
    private val store: OrderStore,
    /** Licencia bloqueada (suspendida, revocada o vencida): no se acepta nada de los meseros. */
    private val isLocked: () -> Boolean = { false },
) {
    private val tag = "KitchenServer"
    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private val clients: MutableSet<Connection> = ConcurrentHashMap.newKeySet()

    private val _clientCount = MutableStateFlow(0)
    val clientCount = _clientCount.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        s.launch { acceptLoop() }
        s.launch { store.state.collect { broadcast(stateMsg()) } }
        s.launch {
            while (isActive) {
                delay(PING_MS)
                broadcast(AppJson.encodeToString<ServerMsg>(ServerMsg.Ping))
                // Cuentas cerradas pasan al historial cuando ya no se pueden deshacer.
                runCatching { store.archiveClosed() }.onFailure { Log.e(tag, "No se pudo archivar", it) }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.forEach { it.close() }
        unregisterNsd()
    }

    private fun acceptLoop() {
        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(DEFAULT_PORT))
            }
        } catch (e: IOException) {
            _error.value = "No se pudo abrir el puerto $DEFAULT_PORT: ${e.message}"
            return
        }
        serverSocket = socket
        _error.value = null
        registerNsd(socket.localPort)
        while (scope?.isActive == true) {
            val client = try { socket.accept() } catch (_: IOException) { break }
            scope?.launch { serve(client) }
        }
    }

    private fun serve(socket: Socket) {
        val conn = Connection(socket)
        clients += conn
        _clientCount.value = clients.size
        try {
            conn.send(stateMsg())
            while (true) {
                val line = socket.readLineLimited(conn.reader, MAX_REQUEST_CHARS) ?: break
                val reply = handle(line)
                if (reply != null) conn.send(AppJson.encodeToString<ServerMsg>(reply))
            }
        } catch (e: IOException) {
            Log.i(tag, "Mesero desconectado: ${e.message}")
        } finally {
            conn.close()
            clients -= conn
            _clientCount.value = clients.size
        }
    }

    private fun handle(line: String): ServerMsg.Reply? {
        val msg = try {
            AppJson.decodeFromString<ClientMsg>(line)
        } catch (e: Exception) {
            Log.w(tag, "Mensaje inválido", e)
            return null
        }
        if (isLocked()) return ServerMsg.Reply(msg.reqId, error = LOCKED_MESSAGE)
        return try {
            when (msg) {
                is ClientMsg.CreateOrder -> ServerMsg.Reply(msg.reqId, order = store.createOrder(msg))
                is ClientMsg.EditOrder -> ServerMsg.Reply(msg.reqId, order = store.editOrder(msg.orderId, msg.lines))
                is ClientMsg.SetOrderStatus -> ServerMsg.Reply(msg.reqId, order = store.setOrderStatus(msg.orderId, msg.status))
                is ClientMsg.CancelAccount -> ServerMsg.Reply(msg.reqId).also { store.cancelAccount(msg.accountId) }
                is ClientMsg.RestoreAccount -> ServerMsg.Reply(msg.reqId).also { store.restoreAccount(msg.accountId) }
                is ClientMsg.AddPayment -> ServerMsg.Reply(msg.reqId, paymentId = store.addPayment(msg))
                is ClientMsg.VoidPayment -> ServerMsg.Reply(msg.reqId).also { store.voidPayment(msg.accountId, msg.paymentId) }
            }
        } catch (e: ValidationException) {
            ServerMsg.Reply(msg.reqId, error = e.message)
        } catch (e: Exception) {
            Log.e(tag, "Error al procesar", e)
            ServerMsg.Reply(msg.reqId, error = "Error en el teléfono de cocina")
        }
    }

    private fun stateMsg() = AppJson.encodeToString<ServerMsg>(ServerMsg.State(store.state.value, System.currentTimeMillis()))

    private fun broadcast(line: String) {
        for (c in clients) {
            try { c.send(line) } catch (_: IOException) { c.close() }
        }
    }

    private fun registerNsd(port: Int) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { Log.i(tag, "Anunciado como ${info.serviceName}") }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { Log.w(tag, "mDNS falló: $code") }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        val info = NsdServiceInfo().apply {
            serviceName = "Taqueria Cocina"
            serviceType = SERVICE_TYPE
            this.port = port
        }
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdListener = listener
        } catch (e: SecurityException) {
            _error.value = "Falta el permiso de red local: actívalo en Ajustes de la app"
        }
    }

    private fun unregisterNsd() {
        val l = nsdListener ?: return
        nsdListener = null
        runCatching { context.getSystemService(NsdManager::class.java)?.unregisterService(l) }
    }

    private class Connection(val socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

        @Synchronized
        fun send(line: String) {
            writer.write(line)
            writer.write("\n")
            writer.flush()
        }

        fun close() = runCatching { socket.close() }
    }

    companion object {
        const val PING_MS = 10_000L
        const val LOCKED_MESSAGE = "La cuenta de la taquería está suspendida. Revisa el teléfono de cocina."
        private const val MAX_REQUEST_CHARS = 64 * 1024

        /** IPs del teléfono en la red local, para conectar a mano si mDNS no funciona. */
        fun localIps(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress.orEmpty() }
        }.getOrDefault(emptyList())
    }
}

/** readLine con tope de tamaño: un mensaje gigante corta la conexión en vez de agotar la memoria. */
fun Socket.readLineLimited(reader: BufferedReader, max: Int): String? {
    val sb = StringBuilder()
    while (true) {
        val c = reader.read()
        if (c == -1) return if (sb.isEmpty()) null else sb.toString()
        if (c == '\n'.code) return sb.toString()
        sb.append(c.toChar())
        if (sb.length > max) {
            close()
            throw IOException("Mensaje demasiado grande")
        }
    }
}
