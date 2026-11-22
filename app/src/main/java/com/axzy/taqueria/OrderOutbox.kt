package com.axzy.taqueria

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.IOException

/** Orden nueva que la mesera capturó y cocina todavía no confirmó. */
@Serializable
data class QueuedOrder(
    /** El mismo del borrador: cocina no duplica la orden si llega dos veces. */
    val key: String,
    val table: String,
    val accountId: String?,
    val lines: List<LineRequest>,
    val extras: List<PersonExtras> = emptyList(),
    val queuedAt: Long,
    /** Cocina la rechazó (p. ej. se agotó un producto): ya no se reintenta, la mesera la revisa. */
    val error: String? = null,
)

/**
 * Órdenes por enviar a cocina. Si no hay conexión (o cocina no contesta) la
 * orden se guarda en el teléfono y se manda sola al volver la conexión, así la
 * mesera no la pierde aunque cierre la app.
 *
 * Reenviar es seguro: cocina reconoce la orden por [QueuedOrder.key].
 */
class OrderOutbox(
    private val load: () -> String?,
    private val save: (String) -> Unit,
) {
    private val mutex = Mutex()
    private val _items = MutableStateFlow(decode(load()))
    val items = _items.asStateFlow()
    /** Avisos para la mesera ("Orden #12 enviada…"). */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices = _notices.asSharedFlow()

    fun add(order: QueuedOrder) = update { list -> list.filterNot { it.key == order.key } + order }

    fun remove(key: String) = update { list -> list.filterNot { it.key == key } }

    /**
     * Manda en orden lo pendiente. Se detiene al primer error de conexión (se
     * reintenta en la siguiente); si cocina rechaza una, se marca y sigue con las demás.
     */
    suspend fun flush(send: suspend (QueuedOrder) -> Order) = mutex.withLock {
        for (q in _items.value.filter { it.error == null }) {
            try {
                val order = send(q)
                remove(q.key)
                _notices.tryEmit("Orden #${order.number} enviada · ${tableLabel(order.table)} · Cuenta ${order.accountNumber}")
            } catch (e: ValidationException) {
                update { list -> list.map { if (it.key == q.key) it.copy(error = e.message ?: "Cocina no la aceptó") else it } }
                _notices.tryEmit("Cocina no aceptó una orden de ${tableLabel(q.table)}: revísala")
            } catch (e: IOException) {
                return@withLock
            }
        }
    }

    private fun update(change: (List<QueuedOrder>) -> List<QueuedOrder>) = synchronized(this) {
        val next = change(_items.value)
        _items.value = next
        save(AppJson.encodeToString(next))
    }

    private companion object {
        fun decode(raw: String?): List<QueuedOrder> =
            raw?.let { runCatching { AppJson.decodeFromString<List<QueuedOrder>>(it) }.getOrNull() }.orEmpty()
    }
}
