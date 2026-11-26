package com.axzy.taqueria

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.IOException
import java.security.MessageDigest

/**
 * Hasta dónde va el menú del teléfono respecto a la Central: la versión con la que se
 * sincronizó por última vez y la huella del menú en ese momento. Si la huella actual ya
 * es otra, el admin cambió algo aquí. `account` evita mezclar menús si el teléfono se
 * activa con otro código.
 */
@Serializable
data class CatalogMark(val account: String, val version: Int, val fingerprint: String)

/** La Central y la cocina cambiaron el menú al mismo tiempo: alguien tiene que elegir. */
data class CatalogConflict(val remote: RemoteCatalog)

/**
 * Menú de la cocina con la Central (solo el teléfono de cocina principal):
 * - Lo que el admin cambia aquí se sube a los pocos segundos.
 * - Lo que se cambia en la Central llega en menos de un minuto (pregunta cada 30 s con ETag).
 * - La primera vez, si la Central no tiene menú, se adopta el de aquí (la migración del taquero).
 *   Si la Central ya tiene y este teléfono es nuevo (sin cuentas ni historial), se usa el de la Central.
 * - Si cambiaron los dos lados, no se pisa nada: queda [conflict] hasta que alguien elige.
 */
class CatalogSync(
    private val store: OrderStore,
    private val credentials: () -> CentralCredentials?,
    private val getMark: () -> CatalogMark?,
    private val putMark: (CatalogMark?) -> Unit,
    /** Guarda el menú de aquí antes de cambiarlo por el de la Central en un conflicto. */
    private val saveAside: (Catalog) -> Unit = {},
    private val call: (CentralCredentials, String, String, String?, Map<String, String>) -> CentralResponse =
        { c, m, p, b, h -> centralCall(c, m, p, b, 15_000, h) },
) {
    private val mutex = Mutex()
    private var scope: CoroutineScope? = null

    private val _conflict = MutableStateFlow<CatalogConflict?>(null)
    val conflict = _conflict.asStateFlow()

    sealed interface Result {
        data object NotActivated : Result
        data object UpToDate : Result
        data class Uploaded(val version: Int) : Result
        data class Applied(val version: Int) : Result
        data object Conflict : Result
        data class Failed(val message: String) : Result
    }

    @OptIn(FlowPreview::class)
    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        s.launch {
            while (isActive) {
                runCatching { sync() }
                delay(POLL_MS)
            }
        }
        // Un cambio del admin se sube en cuanto deja de editar unos segundos.
        s.launch {
            store.state.map { it.catalog() }.distinctUntilChanged().drop(1).debounce(EDIT_DEBOUNCE_MS).collect {
                runCatching { sync() }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    suspend fun sync(): Result = mutex.withLock {
        val creds = credentials() ?: return@withLock Result.NotActivated
        if (_conflict.value != null) return@withLock Result.Conflict
        try {
            val account = accountOf(creds)
            val mark = getMark()?.takeIf { it.account == account }
            val local = store.state.value.catalog()
            val fp = local.fingerprint()
            if (mark != null && mark.fingerprint != fp) upload(creds, account, mark.version, local, fp) else download(creds, account, mark, fp)
        } catch (e: IOException) {
            Result.Failed("Sin conexión con la Central: ${e.message}")
        } catch (e: Exception) {
            Result.Failed("El menú de la Central no se pudo leer: ${e.message}")
        }
    }

    /** Lo eligió el admin en el aviso de conflicto. */
    suspend fun resolve(useCentral: Boolean): Result = mutex.withLock {
        val c = _conflict.value ?: return@withLock Result.UpToDate
        val creds = credentials() ?: return@withLock Result.NotActivated
        val account = accountOf(creds)
        val local = store.state.value.catalog()
        try {
            if (useCentral) {
                saveAside(local)
                _conflict.value = null
                apply(account, c.remote, local.fingerprint())
            } else {
                _conflict.value = null
                // Se sube encima de la versión de la Central (queda en su historial).
                upload(creds, account, c.remote.version, local, local.fingerprint())
            }
        } catch (e: IOException) {
            _conflict.value = c
            Result.Failed("Sin conexión con la Central: ${e.message}")
        }
    }

    private fun upload(creds: CentralCredentials, account: String, base: Int, local: Catalog, fp: String): Result {
        val r = call(creds, "PUT", "catalog", AppJson.encodeToString(CatalogSave(base, local)), emptyMap())
        return when (r.code) {
            in 200..299 -> {
                val v = AppJson.decodeFromString<CatalogSaveReply>(r.body).catalog.version
                putMark(CatalogMark(account, v, fp))
                Result.Uploaded(v)
            }
            409 -> {
                val current = AppJson.decodeFromString<CatalogConflictReply>(r.body).current
                when {
                    // La Central ya no tiene menú (se borró): se adopta otra vez el de aquí.
                    current == null -> if (base == 0) Result.Failed("La Central rechazó el menú") else upload(creds, account, 0, local, fp)
                    current.data.fingerprint() == fp -> { putMark(CatalogMark(account, current.version, fp)); Result.UpToDate }
                    else -> { _conflict.value = CatalogConflict(current); Result.Conflict }
                }
            }
            else -> Result.Failed("La Central respondió ${r.code} al subir el menú")
        }
    }

    private fun download(creds: CentralCredentials, account: String, mark: CatalogMark?, fp: String): Result {
        val headers = mark?.let { mapOf("If-None-Match" to "\"v${it.version}\"") } ?: emptyMap()
        val r = call(creds, "GET", "catalog", null, headers)
        return when (r.code) {
            304 -> Result.UpToDate
            // La Central no tiene menú: este es el bueno (primera vez o migración desde la 1.2).
            404 -> upload(creds, account, 0, store.state.value.catalog(), fp)
            in 200..299 -> {
                val remote = AppJson.decodeFromString<RemoteCatalog>(r.body)
                remote.data.problem()?.let { return Result.Failed(it) }
                when {
                    remote.data.fingerprint() == fp -> { putMark(CatalogMark(account, remote.version, fp)); Result.UpToDate }
                    mark != null && remote.version == mark.version -> Result.UpToDate
                    // Sin cambios aquí desde la última vez: manda la Central.
                    mark != null -> apply(account, remote, fp)
                    // Primera vez con menú en los dos lados: un teléfono nuevo toma el de la Central.
                    isNewPhone() -> apply(account, remote, fp)
                    else -> { _conflict.value = CatalogConflict(remote); Result.Conflict }
                }
            }
            else -> Result.Failed("La Central respondió ${r.code} al pedir el menú")
        }
    }

    private fun apply(account: String, remote: RemoteCatalog, expected: String): Result {
        if (!store.applyCatalog(remote.data, expected)) return Result.UpToDate // el admin está editando; siguiente vuelta
        // La marca lleva la huella de la Central: si aquí quedó algo distinto (un agotado), se sube.
        putMark(CatalogMark(account, remote.version, remote.data.fingerprint()))
        return Result.Applied(remote.version)
    }

    private fun isNewPhone() = store.state.value.accounts.isEmpty() && store.archived().isEmpty()

    private fun accountOf(creds: CentralCredentials) =
        MessageDigest.getInstance("SHA-256").digest("${creds.apiBase}|${creds.token}".toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    private companion object {
        const val POLL_MS = 30_000L
        const val EDIT_DEBOUNCE_MS = 3_000L
    }
}
