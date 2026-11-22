package com.axzy.taqueria

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

// ---------------- Contrato de `PUT/GET /backup` (AxzyCentral) ----------------

@Serializable
data class BackupUpload(val appVersion: String, val savedAt: String, val state: AppState)

@Serializable
data class BackupDownload(
    val savedAt: String,
    val deviceName: String,
    val fromThisDevice: Boolean = false,
    val appVersion: String? = null,
    val state: AppState,
)

/**
 * Respaldo del teléfono de cocina en Axzy Central: su estado completo (menú,
 * cuentas abiertas y órdenes). Si el teléfono se rompe o se pierde, otro
 * teléfono activado para la misma taquería lo recupera desde Admin.
 *
 * Cada minuto revisa si el estado cambió y solo entonces lo sube. Las cuentas
 * ya cerradas viajan aparte, como ventas ([SalesSync]).
 */
class BackupSync(
    private val store: OrderStore,
    private val credentials: () -> CentralCredentials?,
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val tag = "BackupSync"
    private val mutex = Mutex()
    private var scope: CoroutineScope? = null
    /** Lo último que la Central confirmó, para no subir lo mismo cada minuto. */
    @Volatile private var lastSent: AppState? = null

    /** Cuándo se respaldó por última vez (null = todavía no en esta sesión). */
    private val _lastBackupAt = MutableStateFlow<Long?>(null)
    val lastBackupAt = _lastBackupAt.asStateFlow()

    sealed interface Restore {
        data class Found(val backup: BackupDownload) : Restore
        data object None : Restore
        data object NotActivated : Restore
        data class Failed(val message: String) : Restore
    }

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        s.launch {
            var retryAt = 0L
            while (isActive) {
                if (now() >= retryAt) {
                    val error = runCatching { backup() }.getOrElse { it.message ?: "Error" }
                    if (error != null) {
                        Log.i(tag, "No se pudo respaldar: $error")
                        retryAt = now() + RETRY_MS
                    }
                }
                delay(CHECK_MS)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    /** Sube el estado si cambió. Devuelve el error, o null si quedó respaldado (o no había cambios). */
    suspend fun backup(): String? = mutex.withLock {
        val creds = credentials() ?: return@withLock null
        val state = store.state.value
        if (state == lastSent) return@withLock null
        val body = AppJson.encodeToString(BackupUpload(appVersion, iso(now()), state))
        val r = try {
            centralCall(creds, "PUT", "backup", body)
        } catch (e: IOException) {
            return@withLock "Sin conexión con la Central: ${e.message}"
        }
        if (r.code !in 200..299) return@withLock "La Central respondió ${r.code}"
        lastSent = state
        _lastBackupAt.value = now()
        null
    }

    /** Busca el último respaldo de la taquería en la Central. */
    suspend fun fetch(): Restore = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val creds = credentials() ?: return@withContext Restore.NotActivated
        try {
            val r = centralCall(creds, "GET", "backup")
            when (r.code) {
                in 200..299 -> Restore.Found(AppJson.decodeFromString<BackupDownload>(r.body))
                404 -> Restore.None
                else -> Restore.Failed("La Central respondió ${r.code}")
            }
        } catch (e: IOException) {
            Restore.Failed("Sin conexión con la Central. Revisa el internet del teléfono.")
        } catch (e: Exception) {
            Log.w(tag, "Respaldo ilegible", e)
            Restore.Failed("El respaldo no se pudo leer. Actualiza la app e intenta de nuevo.")
        }
    }

    /** Pone el respaldo en este teléfono; lo que tenía queda apartado en un archivo. */
    fun restore(backup: BackupDownload) {
        store.replaceAll(backup.state)
        // Lo recuperado ya está en la Central; se respalda de nuevo hasta que cambie.
        lastSent = store.state.value
    }

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()

    private companion object {
        const val CHECK_MS = 60_000L
        const val RETRY_MS = 5 * 60_000L
    }
}
