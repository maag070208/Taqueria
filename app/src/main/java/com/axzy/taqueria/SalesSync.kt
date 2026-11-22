package com.axzy.taqueria

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

// ---------------- Contrato de `POST /sales` (AxzyCentral) ----------------

@Serializable
data class SalesUpload(val appVersion: String, val tickets: List<SaleTicket>)

/** Una cuenta cerrada tal como la ve la Central. Montos en centavos; fechas ISO con el offset del local. */
@Serializable
data class SaleTicket(
    val id: String,
    val table: String,
    val accountNumber: Int,
    val openedAt: String,
    val closedAt: String?,
    val utcOffsetMin: Int,
    val cancelled: Boolean,
    val totalCents: Long,
    val cashCents: Long,
    val transferCents: Long,
    val persons: Int,
    /** Promedio de segundos de cocina (llegó → entregada); no cuenta lo que se entregó al cobrar. */
    val prepSeconds: Int?,
    val lines: List<SaleTicketLine>,
)

@Serializable
data class SaleTicketLine(
    val productId: String,
    val name: String,
    val variant: String,
    val category: String?,
    val qty: Int,
    val unitCents: Long,
)

/** Lo que se reporta de una cuenta archivada. Puro: se prueba en SalesSyncTest. */
fun saleTicket(e: HistoryEntry, categoryOf: (productId: String) -> String?, zone: ZoneId = ZoneId.systemDefault()): SaleTicket {
    val a = e.account
    val live = e.orders.filter { it.status != OrderStatus.CANCELLED }
    val payments = a.payments.filterNot { it.voided }
    val prep = live.mapNotNull { o ->
        o.deliveredAt?.takeIf { o.deliveredByPayment == null && it >= o.createdAt }?.let { (it - o.createdAt) / 1000 }
    }
    val offset = zone.rules.getOffset(Instant.ofEpochMilli(a.createdAt))
    fun iso(ms: Long) = Instant.ofEpochMilli(ms).atOffset(zone.rules.getOffset(Instant.ofEpochMilli(ms))).toString()
    return SaleTicket(
        id = a.id,
        table = a.table,
        accountNumber = a.number,
        openedAt = iso(a.createdAt),
        closedAt = (a.closedAt ?: a.cancelledAt)?.let(::iso),
        utcOffsetMin = offset.totalSeconds / 60,
        cancelled = a.cancelledAt != null || live.isEmpty(),
        totalCents = live.sumOf { it.totalCents },
        cashCents = payments.filter { it.method == PayMethod.CASH }.sumOf { it.amountCents },
        transferCents = payments.filter { it.method == PayMethod.TRANSFER }.sumOf { it.amountCents },
        persons = live.flatMap { it.persons }.distinct().size,
        prepSeconds = if (prep.isEmpty()) null else (prep.sum() / prep.size).toInt(),
        lines = live.flatMap { it.lines }.map { l ->
            SaleTicketLine(l.productId, l.name, l.variant, categoryOf(l.productId), l.qty, l.priceCents)
        },
    )
}

/** Servidor y código del dispositivo para llamar a la Central. */
data class CentralCredentials(val apiBase: String, val token: String)

/**
 * Sube a Axzy Central las cuentas que la cocina ya archivó, para las métricas
 * del cliente. Solo la cocina; sin internet no pasa nada y se reintenta después.
 *
 * El historial solo crece y lo archivado ya no cambia, así que basta con
 * recordar cuántas cuentas se subieron ([getSent]/[putSent]). La Central
 * guarda cada cuenta una sola vez: si un lote se repite no se duplica.
 */
class SalesSync(
    private val store: OrderStore,
    private val credentials: () -> CentralCredentials?,
    private val appVersion: String,
    private val getSent: () -> Int,
    private val putSent: (Int) -> Unit,
) {
    private val tag = "SalesSync"
    private val mutex = Mutex()
    private var scope: CoroutineScope? = null

    sealed interface Result {
        data class Sent(val tickets: Int) : Result
        data object NothingNew : Result
        data object NotActivated : Result
        data class Failed(val message: String) : Result
    }

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        s.launch {
            // Revisar si hay cuentas nuevas es local y barato; a la Central solo se llama
            // cuando hay algo que subir, y tras un error se espera antes de reintentar.
            var retryAt = 0L
            while (isActive) {
                delay(CHECK_MS)
                if (System.currentTimeMillis() < retryAt) continue
                val r = runCatching { sync() }.getOrElse { Result.Failed(it.message.orEmpty()) }
                if (r is Result.Failed) {
                    Log.i(tag, "No se pudo reportar: ${r.message}")
                    retryAt = System.currentTimeMillis() + RETRY_MS
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    /** Sube lo pendiente en lotes. Se detiene en el primer error y sigue en la siguiente vuelta. */
    suspend fun sync(): Result = mutex.withLock {
        val creds = credentials() ?: return@withLock Result.NotActivated
        val history = store.archived()
        var sent = getSent()
        // El historial se borró (datos reiniciados): se empieza de nuevo; la Central no duplica.
        if (sent > history.size) sent = 0
        if (sent == history.size) return@withLock Result.NothingNew
        val categories = store.state.value.products.associate { it.id to it.category }
        var total = 0
        while (sent < history.size) {
            val batch = history.subList(sent, minOf(history.size, sent + BATCH))
            val upload = SalesUpload(appVersion, batch.map { saleTicket(it, categories::get) })
            post(creds, AppJson.encodeToString(upload))?.let { return@withLock Result.Failed(it) }
            sent += batch.size
            total += batch.size
            putSent(sent)
        }
        Result.Sent(total)
    }

    /** Devuelve el error o null si la Central lo recibió. */
    private fun post(creds: CentralCredentials, body: String): String? = try {
        val r = centralCall(creds, "POST", "sales", body, TIMEOUT_MS)
        if (r.code in 200..299) null else "La Central respondió ${r.code}"
    } catch (e: IOException) {
        "Sin conexión con la Central: ${e.message}"
    }

    private companion object {
        const val BATCH = 20
        const val TIMEOUT_MS = 15_000
        const val CHECK_MS = 60_000L
        const val RETRY_MS = 10 * 60_000L
    }
}
