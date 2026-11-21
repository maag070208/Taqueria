package com.axzy.taqueria

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

// ---------------- Respuesta del servidor ----------------

@Serializable
data class LicenseResponse(
    val status: String,
    val tenant: NameRef,
    val plan: NameRef? = null,
    val nextDueDate: String? = null,
    val validUntil: String,
    val serverTime: String,
)

@Serializable
data class NameRef(val name: String)

// ---------------- Lógica pura (probada en LicenseTest) ----------------

/**
 * Lo último que dijo el servidor, guardado en el teléfono. La app trabaja sin
 * internet con esto hasta [validUntilMs].
 */
@Serializable
data class LicenseCache(
    val token: String,
    val status: String = "ACTIVE",
    val revoked: Boolean = false,
    val tenantName: String = "",
    val nextDueDate: String? = null,
    val validUntilMs: Long = 0,
    /** Hora del servidor menos la del teléfono en la última consulta. */
    val clockOffsetMs: Long = 0,
    /** Hora más reciente que se ha visto: atrasar el reloj del teléfono no alarga la licencia. */
    val maxSeenMs: Long = 0,
    val lastCheckMs: Long = 0,
) {
    fun effectiveNow(phoneNowMs: Long) = maxOf(phoneNowMs + clockOffsetMs, maxSeenMs)

    fun withResponse(r: LicenseResponse, phoneNowMs: Long): LicenseCache {
        val server = Instant.parse(r.serverTime).toEpochMilli()
        return copy(
            status = r.status,
            revoked = false,
            tenantName = r.tenant.name,
            nextDueDate = r.nextDueDate,
            validUntilMs = Instant.parse(r.validUntil).toEpochMilli(),
            clockOffsetMs = server - phoneNowMs,
            maxSeenMs = maxOf(maxSeenMs, server),
            lastCheckMs = phoneNowMs,
        )
    }
}

enum class LockReason { SUSPENDED, REVOKED, EXPIRED }

sealed interface LicenseState {
    data object NotActivated : LicenseState
    data class Active(val tenantName: String, val validUntilMs: Long, val expiresSoon: Boolean) : LicenseState
    data class Locked(val reason: LockReason, val tenantName: String) : LicenseState
}

const val EXPIRES_SOON_MS = 3L * 24 * 60 * 60 * 1000

fun evaluateLicense(cache: LicenseCache?, phoneNowMs: Long): LicenseState {
    if (cache == null) return LicenseState.NotActivated
    val now = cache.effectiveNow(phoneNowMs)
    return when {
        cache.revoked -> LicenseState.Locked(LockReason.REVOKED, cache.tenantName)
        cache.status != "ACTIVE" -> LicenseState.Locked(LockReason.SUSPENDED, cache.tenantName)
        now > cache.validUntilMs -> LicenseState.Locked(LockReason.EXPIRED, cache.tenantName)
        else -> LicenseState.Active(cache.tenantName, cache.validUntilMs, cache.validUntilMs - now < EXPIRES_SOON_MS)
    }
}

// ---------------- Consulta y vigilancia (solo en el teléfono de cocina) ----------------

/**
 * Licencia de la taquería. Solo la consulta el teléfono de cocina: si está
 * bloqueado, [KitchenServer] rechaza lo que mandan los meseros.
 *
 * Consulta a Axzy Central (`GET /license`) al abrir, cada vez que Android detecta una red con salida a internet
 * (el WiFi del local o el hotspot muchas veces no la tienen) y cada pocas horas.
 * Sin conexión se queda con la última respuesta: "suspendida" sigue bloqueando y
 * "activa" vale hasta su `validUntil`.
 */
class LicenseManager(context: Context, private val baseUrl: String) {
    private val tag = "License"
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("license", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var cache: LicenseCache? = load()
    private var scope: CoroutineScope? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    var state by mutableStateOf(evaluateLicense(cache, System.currentTimeMillis()))
        private set
    var checking by mutableStateOf(false)
        private set

    /** Lo lee el servidor de cocina desde sus hilos. */
    @Volatile
    var locked = state !is LicenseState.Active
        private set

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        s.launch {
            refresh()
            var sinceCheck = 0L
            while (isActive) {
                delay(TICK_MS)
                tick()
                sinceCheck += TICK_MS
                if (sinceCheck >= PERIODIC_CHECK_MS) {
                    sinceCheck = 0
                    refresh()
                }
            }
        }
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
                val last = cache?.lastCheckMs ?: 0
                if (locked || System.currentTimeMillis() - last > ONLINE_CHECK_MS) scope?.launch { refresh() }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { callback = cb }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        callback?.let { cb -> runCatching { appContext.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        callback = null
    }

    /** Guarda el código si el servidor lo acepta. Devuelve el error para mostrar o null. */
    suspend fun activate(code: String): String? {
        val token = code.trim()
        if (token.isEmpty()) return "Pega el código de activación"
        return when (val r = fetch(token)) {
            is Fetch.Ok -> { save(LicenseCache(token).withResponse(r.body, System.currentTimeMillis())); null }
            Fetch.Unauthorized -> "Código inválido o revocado. Pide uno nuevo a tu proveedor."
            is Fetch.Failed -> r.message
        }
    }

    /** Vuelve a consultar. Devuelve el error para mostrar o null. */
    suspend fun refresh(): String? {
        val current = cache ?: return null
        return when (val r = fetch(current.token)) {
            is Fetch.Ok -> { save(current.withResponse(r.body, System.currentTimeMillis())); null }
            Fetch.Unauthorized -> { save(current.copy(revoked = true, lastCheckMs = System.currentTimeMillis())); null }
            is Fetch.Failed -> r.message
        }
    }

    /** Quita el código guardado para escribir otro. */
    fun forget() {
        cache = null
        prefs.edit().remove(KEY).apply()
        publish()
    }

    private fun tick() {
        val c = cache ?: return publish()
        val seen = c.effectiveNow(System.currentTimeMillis())
        if (seen > c.maxSeenMs) save(c.copy(maxSeenMs = seen)) else publish()
    }

    private sealed interface Fetch {
        data class Ok(val body: LicenseResponse) : Fetch
        data object Unauthorized : Fetch
        data class Failed(val message: String) : Fetch
    }

    private suspend fun fetch(token: String): Fetch = mutex.withLock {
        if (baseUrl.isBlank()) return@withLock Fetch.Failed("Esta versión de la app no tiene servidor configurado. Pide la versión correcta a tu proveedor.")
        withContext(Dispatchers.Main) { checking = true }
        try {
            withContext(Dispatchers.IO) {
                val conn = URL("${baseUrl.trimEnd('/')}/license").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = TIMEOUT_MS
                    conn.readTimeout = TIMEOUT_MS
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Accept", "application/json")
                    when (val code = conn.responseCode) {
                        200 -> Fetch.Ok(AppJson.decodeFromString<LicenseResponse>(conn.inputStream.bufferedReader().use { it.readText() }))
                        401 -> Fetch.Unauthorized
                        else -> Fetch.Failed("El servidor respondió $code. Intenta más tarde.")
                    }
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: IOException) {
            Log.i(tag, "Sin conexión al servidor: ${e.message}")
            Fetch.Failed("Sin internet. Conecta este teléfono a internet e intenta otra vez.")
        } catch (e: Exception) {
            Log.e(tag, "Respuesta inválida", e)
            Fetch.Failed("No se pudo leer la respuesta del servidor.")
        } finally {
            withContext(Dispatchers.Main) { checking = false }
        }
    }

    private fun save(next: LicenseCache) {
        cache = next
        prefs.edit().putString(KEY, AppJson.encodeToString(next)).apply()
        publish()
    }

    private fun publish() {
        val next = evaluateLicense(cache, System.currentTimeMillis())
        locked = next !is LicenseState.Active
        // El estado de Compose se escribe desde el hilo principal.
        android.os.Handler(android.os.Looper.getMainLooper()).post { state = next }
    }

    private fun load(): LicenseCache? = prefs.getString(KEY, null)?.let {
        runCatching { AppJson.decodeFromString<LicenseCache>(it) }.getOrNull()
    }

    private companion object {
        const val KEY = "cache"
        const val TIMEOUT_MS = 10_000
        const val TICK_MS = 60_000L
        const val PERIODIC_CHECK_MS = 6 * 60 * 60_000L
        const val ONLINE_CHECK_MS = 30 * 60_000L
    }
}
