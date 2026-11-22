package com.axzy.taqueria

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.util.UUID

class ValidationException(message: String) : Exception(message)

/** Registro de una cuenta cerrada: se conserva para consulta, con cancelaciones y pagos. */
@Serializable
data class HistoryEntry(val account: Account, val orders: List<Order>)

/**
 * Fuente de verdad del teléfono de cocina. Valida todo lo que llega de la red,
 * guarda en disco de forma atómica y publica el estado a la UI y al servidor.
 * Las cuentas cerradas se mueven a un historial (una línea JSON por cuenta) para
 * conservar cancelaciones y pagos sin que el estado activo crezca sin fin.
 * Métodos bloqueantes (escriben a disco): llamarlos fuera del hilo principal.
 */
class OrderStore(
    private val file: File,
    private val historyFile: File = File(file.parentFile, "historial.jsonl"),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    // ---------------- Órdenes ----------------

    fun createOrder(req: ClientMsg.CreateOrder): Order = synchronized(lock) {
        val s = _state.value
        s.orders.firstOrNull { it.clientKey == req.clientKey }?.let { return it }
        if (req.clientKey.isBlank() || req.clientKey.length > 64) throw ValidationException("Solicitud inválida")

        val table = text(req.table, "Mesa", 20)
        val lines = parseLines(s, req.lines)
        val t = now()

        val account = if (req.accountId == null) {
            val next = (s.accounts.filter { it.table == table }.maxOfOrNull { it.number } ?: 0) + 1
            Account(UUID.randomUUID().toString(), table, next, t)
        } else {
            val a = s.accounts.firstOrNull { it.id == req.accountId } ?: throw ValidationException("La cuenta ya se cerró; abre una nueva")
            if (a.cancelledAt != null) throw ValidationException("La cuenta está cancelada")
            if (a.closedAt != null) throw ValidationException("La cuenta ya se cerró; abre una nueva")
            if (a.table != table) throw ValidationException("La cuenta es de otra mesa")
            a
        }

        val today = LocalDate.now().toString()
        val number = if (s.counterDate == today) s.counterLast + 1 else 1
        val order = Order(
            id = UUID.randomUUID().toString(),
            number = number,
            accountId = account.id,
            table = table,
            accountNumber = account.number,
            lines = lines,
            status = OrderStatus.PENDING,
            createdAt = t,
            clientKey = req.clientKey,
        )
        val accounts = if (req.accountId == null) s.accounts + account else s.accounts
        commit(s.copy(accounts = accounts, orders = s.orders + order, counterDate = today, counterLast = number))
        order
    }

    /** Edita una orden que sigue en cocina; la versión anterior queda en su historial. */
    fun editOrder(orderId: String, raw: List<LineRequest>): Order = synchronized(lock) {
        val s = _state.value
        val order = s.orders.firstOrNull { it.id == orderId } ?: throw ValidationException("La orden ya no está activa")
        if (order.status != OrderStatus.PENDING) {
            throw ValidationException("Solo se puede editar una orden que sigue en cocina (esta ya está ${order.status.label()})")
        }
        if (s.accounts.firstOrNull { it.id == order.accountId }?.cancelledAt != null) throw ValidationException("La cuenta está cancelada")
        val lines = parseLines(s, raw, original = order.lines)
        if (lines == order.lines) return order
        val updated = order.copy(lines = lines, revisions = order.revisions + listOf(order.lines), editedAt = now())
        commit(s.copy(orders = s.orders.map { if (it.id == orderId) updated else it }))
        updated
    }

    fun setOrderStatus(orderId: String, status: OrderStatus): Order = synchronized(lock) {
        val s = _state.value
        val order = s.orders.firstOrNull { it.id == orderId } ?: throw ValidationException("La orden ya no está activa")
        val t = now()
        val updated = when {
            order.status == OrderStatus.PENDING && status == OrderStatus.DELIVERED ->
                order.copy(status = status, deliveredAt = t)
            order.status == OrderStatus.PENDING && status == OrderStatus.CANCELLED ->
                order.copy(status = status, cancelledAt = t, cancelledByAccount = false)
            // Deshacer entrega o cancelación, solo dentro del periodo permitido.
            order.status == OrderStatus.DELIVERED && status == OrderStatus.PENDING -> {
                requireUndo(order.deliveredAt)
                order.copy(status = status, deliveredAt = null, deliveredByPayment = null)
            }
            order.status == OrderStatus.CANCELLED && status == OrderStatus.PENDING -> {
                requireUndo(order.cancelledAt)
                if (s.accounts.firstOrNull { it.id == order.accountId }?.cancelledAt != null) {
                    throw ValidationException("La cuenta está cancelada")
                }
                order.copy(status = status, cancelledAt = null)
            }
            else -> throw ValidationException("La orden ya está ${order.status.label()}")
        }
        commit(s.copy(orders = s.orders.map { if (it.id == orderId) updated else it }))
        updated
    }

    // ---------------- Cuentas y pagos ----------------

    /** Cancela la cuenta y lo que tenga pendiente en cocina; lo ya entregado sigue cobrándose. */
    fun cancelAccount(accountId: String) = synchronized(lock) {
        val s = _state.value
        val a = account(s, accountId)
        if (a.cancelledAt != null) throw ValidationException("La cuenta ya estaba cancelada")
        val t = now()
        commit(
            s.copy(
                accounts = s.accounts.map { if (it.id == accountId) it.copy(cancelledAt = t) else it },
                orders = s.orders.map {
                    if (it.accountId == accountId && it.status == OrderStatus.PENDING) {
                        it.copy(status = OrderStatus.CANCELLED, cancelledAt = t, cancelledByAccount = true)
                    } else it
                },
            ),
        )
    }

    fun restoreAccount(accountId: String) = synchronized(lock) {
        val s = _state.value
        val a = account(s, accountId)
        val cancelledAt = a.cancelledAt ?: throw ValidationException("La cuenta no está cancelada")
        requireUndo(cancelledAt)
        commit(
            s.copy(
                accounts = s.accounts.map { if (it.id == accountId) it.copy(cancelledAt = null) else it },
                orders = s.orders.map {
                    if (it.accountId == accountId && it.cancelledByAccount && it.cancelledAt == cancelledAt) {
                        it.copy(status = OrderStatus.PENDING, cancelledAt = null, cancelledByAccount = false)
                    } else it
                },
            ),
        )
    }

    /** Registra un pago (o devolución si es negativo). Devuelve el id del movimiento. */
    fun addPayment(req: ClientMsg.AddPayment): String = synchronized(lock) {
        val s = _state.value
        val a = account(s, req.accountId)
        a.payments.firstOrNull { it.clientKey == req.clientKey }?.let { return it.id }
        if (req.clientKey.isBlank() || req.clientKey.length > 64) throw ValidationException("Solicitud inválida")
        if (a.closedAt != null) throw ValidationException("La cuenta ya está cerrada")
        val balance = s.balanceOf(a)
        when {
            req.amountCents == 0L -> throw ValidationException("El importe no puede ser cero")
            req.amountCents > 0 && req.amountCents > balance ->
                throw ValidationException("El importe excede el saldo pendiente de ${money(balance.coerceAtLeast(0))}")
            req.amountCents < 0 && -req.amountCents > -balance ->
                throw ValidationException("La devolución excede lo que se debe regresar")
        }
        val received = req.receivedCents
        if (received != null) {
            if (req.method != PayMethod.CASH || req.amountCents < 0) throw ValidationException("El cambio solo aplica a cobros en efectivo")
            if (received < req.amountCents) throw ValidationException("Recibiste menos que el importe")
            if (received > 10_000_000) throw ValidationException("Importe recibido inválido")
        }
        val t = now()
        val payment = Payment(UUID.randomUUID().toString(), req.amountCents, req.method, t, req.clientKey, receivedCents = received)
        commit(
            s.copy(
                accounts = s.accounts.map { if (it.id == a.id) it.copy(payments = it.payments + payment) else it },
                // Cobrar y entregar en un paso: lo que siga en cocina de esta cuenta se marca entregado.
                orders = if (!req.deliver) s.orders else s.orders.map {
                    if (it.accountId == a.id && it.status == OrderStatus.PENDING) {
                        it.copy(status = OrderStatus.DELIVERED, deliveredAt = t, deliveredByPayment = payment.id)
                    } else it
                },
            ),
        )
        payment.id
    }

    /** Deshacer un pago: queda registrado como anulado, nunca se borra. */
    fun voidPayment(accountId: String, paymentId: String) = synchronized(lock) {
        val s = _state.value
        val a = account(s, accountId)
        val p = a.payments.firstOrNull { it.id == paymentId } ?: throw ValidationException("No se encontró el pago")
        if (p.voided) return
        requireUndo(p.at)
        commit(
            s.copy(
                accounts = s.accounts.map { acc ->
                    if (acc.id == accountId) acc.copy(payments = acc.payments.map { if (it.id == paymentId) it.copy(voided = true) else it }) else acc
                },
                // Lo que se entregó con este pago regresa a cocina.
                orders = s.orders.map {
                    if (it.deliveredByPayment == paymentId && it.status == OrderStatus.DELIVERED) {
                        it.copy(status = OrderStatus.PENDING, deliveredAt = null, deliveredByPayment = null)
                    } else it
                },
            ),
        )
    }

    /** Pasa al historial las cuentas cerradas cuyo periodo de deshacer ya terminó. */
    fun archiveClosed() = synchronized(lock) {
        val s = _state.value
        val limit = now() - UNDO_WINDOW_MS
        val done = s.accounts.filter { it.closedAt != null && it.closedAt < limit }
        if (done.isEmpty()) return
        historyFile.parentFile?.mkdirs()
        historyFile.appendText(done.joinToString("") { a -> AppJson.encodeToString(HistoryEntry(a, s.ordersOf(a.id))) + "\n" })
        val ids = done.map { it.id }.toSet()
        commit(s.copy(accounts = s.accounts.filterNot { it.id in ids }, orders = s.orders.filterNot { it.accountId in ids }))
    }

    // ---------------- Catálogo ----------------

    /** Alta (id nulo) o edición de un producto. */
    fun saveProduct(
        id: String?, name: String, priceCents: Long, category: String, active: Boolean, emoji: String,
        modifierGroupIds: List<String>,
    ) = synchronized(lock) {
        val s = _state.value
        val clean = Product(
            id = id ?: UUID.randomUUID().toString(),
            name = text(name, "Nombre", 40),
            priceCents = priceCents.also { if (it !in 0..10_000_000) throw ValidationException("Precio inválido") },
            category = text(category, "Categoría", 30, required = false).ifEmpty { "Tacos" },
            active = active,
            emoji = emoji.trim().also { if (it.length > 16) throw ValidationException("Ícono inválido") },
            modifierGroupIds = modifierGroupIds.distinct().filter { g -> s.modifierGroups.any { it.id == g } },
        )
        val products = if (id == null) s.products + clean else s.products.map { if (it.id == id) clean else it }
        commit(s.copy(products = products))
    }

    fun deleteProduct(id: String) = synchronized(lock) {
        val s = _state.value
        commit(s.copy(products = s.products.filterNot { it.id == id }))
    }

    /** Mueve un producto arriba (-1) o abajo (+1) en el orden de aparición. */
    fun moveProduct(id: String, delta: Int) = synchronized(lock) {
        val s = _state.value
        val i = s.products.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in s.products.indices) return
        val list = s.products.toMutableList()
        list[i] = list[j].also { list[j] = list[i] }
        commit(s.copy(products = list))
    }

    fun saveModifierGroup(id: String?, name: String, options: List<String>, exclusive: Boolean) = synchronized(lock) {
        val s = _state.value
        val clean = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) throw ValidationException("Agrega al menos una opción")
        if (clean.any { it.length > 30 }) throw ValidationException("Opción: máximo 30 caracteres")
        // Cada opción pertenece a un solo tipo, así la validación de exclusivas no es ambigua.
        val taken = s.modifierGroups.filter { it.id != id }.flatMap { it.options }.toSet()
        clean.firstOrNull { it in taken }?.let { throw ValidationException("\"$it\" ya existe en otro tipo de modificación") }
        val group = ModifierGroup(id ?: UUID.randomUUID().toString(), text(name, "Nombre", 30), clean, exclusive)
        val groups = if (id == null) s.modifierGroups + group else s.modifierGroups.map { if (it.id == id) group else it }
        commit(s.copy(modifierGroups = groups))
    }

    fun deleteModifierGroup(id: String) = synchronized(lock) {
        val s = _state.value
        commit(
            s.copy(
                modifierGroups = s.modifierGroups.filterNot { it.id == id },
                products = s.products.map { p -> p.copy(modifierGroupIds = p.modifierGroupIds - id) },
            ),
        )
    }

    // ---------------- Internos ----------------

    /**
     * Valida los grupos de preparación y une los idénticos (mismo producto, modificaciones y nota).
     * Al editar, lo que ya estaba en la orden ([original]) se acepta aunque se haya agotado después.
     */
    private fun parseLines(s: AppState, raw: List<LineRequest>, original: List<OrderLine> = emptyList()): List<OrderLine> {
        if (raw.isEmpty()) throw ValidationException("La orden no tiene productos")
        if (raw.size > 60) throw ValidationException("Demasiados grupos en una orden")
        val merged = LinkedHashMap<Triple<String, List<String>, String>, OrderLine>()
        for (r in raw) {
            val wasOrdered = original.firstOrNull { it.productId == r.productId }
            val p = s.products.firstOrNull { it.id == r.productId && (it.active || wasOrdered != null) }
                ?: wasOrdered?.let { Product(it.productId, it.name, it.priceCents, "") }
                ?: throw ValidationException("Un producto ya no está disponible")
            if (r.qty !in 1..99) throw ValidationException("Cantidad inválida para ${p.name}")
            val groups = s.groupsFor(p)
            val mods = r.mods.map { it.trim() }.distinct()
            // Producto borrado del catálogo: se conservan sus modificaciones originales tal cual.
            val catalogGone = s.products.none { it.id == p.id }
            for (m in mods) {
                if (!catalogGone && groups.none { m in it.options }) throw ValidationException("\"$m\" no aplica a ${p.name}")
            }
            for (g in groups.filter { it.exclusive }) {
                if (mods.count { it in g.options } > 1) throw ValidationException("${p.name}: elige solo una opción de ${g.name}")
            }
            // Mismo orden de modificaciones que el catálogo, para que cocina lea siempre igual.
            val ordered = if (catalogGone) mods else groups.flatMap { it.options }.filter { it in mods }
            val note = text(r.note, "Nota", 100, required = false)
            val key = Triple(p.id, ordered, note)
            val prev = merged[key]?.qty ?: 0
            merged[key] = OrderLine(p.id, p.name, p.priceCents, prev + r.qty, ordered, note)
        }
        // Agrupa por producto en el orden del menú (sortedBy es estable: respeta el orden de los grupos).
        return merged.values.sortedBy { line -> s.products.indexOfFirst { it.id == line.productId } }
    }

    private fun account(s: AppState, id: String) =
        s.accounts.firstOrNull { it.id == id } ?: throw ValidationException("La cuenta ya se cerró")

    private fun requireUndo(at: Long?) {
        if (at == null || now() - at > UNDO_WINDOW_MS) throw ValidationException("Ya pasó el tiempo para deshacer")
    }

    /** Marca/desmarca el cierre de cada cuenta y guarda en disco de forma atómica. */
    private fun commit(next: AppState) {
        val t = now()
        val withClosures = next.copy(accounts = next.accounts.map { a ->
            val closed = next.isClosed(a)
            when {
                closed && a.closedAt == null -> a.copy(closedAt = t)
                !closed && a.closedAt != null -> a.copy(closedAt = null)
                else -> a
            }
        })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(withClosures))
        if (!tmp.renameTo(file)) throw IllegalStateException("No se pudo guardar ${file.name}")
        _state.value = withClosures
    }

    private fun load(): AppState {
        if (!file.exists()) return seed().also { writeInitial(it) }
        return try {
            AppJson.decodeFromString<AppState>(file.readText())
        } catch (e: Exception) {
            // No pisar datos dañados o de una versión anterior: se apartan para poder recuperarlos.
            val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            file.renameTo(backup)
            runCatching { Log.e("OrderStore", "Datos ilegibles, respaldados en ${backup.name}", e) }
            seed().also { writeInitial(it) }
        }
    }

    private fun writeInitial(s: AppState) {
        file.parentFile?.mkdirs()
        file.writeText(AppJson.encodeToString(s))
    }

    private fun text(value: String, field: String, max: Int, required: Boolean = true): String {
        val t = value.trim()
        if (required && t.isEmpty()) throw ValidationException("$field es requerido")
        if (t.length > max) throw ValidationException("$field: máximo $max caracteres")
        return t
    }

    companion object {
        fun seed(): AppState {
            fun id() = UUID.randomUUID().toString()
            val salsa = ModifierGroup(id(), "Salsa", listOf("Con salsa", "Sin salsa", "Salsa aparte"))
            val cebolla = ModifierGroup(id(), "Cebolla", listOf("Con cebolla", "Sin cebolla", "Cebolla aparte"))
            val cilantro = ModifierGroup(id(), "Cilantro", listOf("Con cilantro", "Sin cilantro"))
            val extras = ModifierGroup(id(), "Extras", listOf("Bien dorado", "Con queso", "Doble tortilla"), exclusive = false)
            val hielo = ModifierGroup(id(), "Hielo", listOf("Con hielo", "Sin hielo"))
            val taco = listOf(salsa.id, cebolla.id, cilantro.id, extras.id)
            return AppState(
                modifierGroups = listOf(salsa, cebolla, cilantro, extras, hielo),
                products = listOf(
                    "Asada" to 3500L, "Adobada" to 3000L, "Suadero" to 3500L, "Cabeza" to 3500L, "Tripa" to 3500L, "Vampiro" to 5000L,
                ).map { (n, p) -> Product(id(), n, p, "Tacos", modifierGroupIds = taco) } +
                    listOf(
                        Triple("Quesataco Asada", 4500L, "🧀"), Triple("Quesataco Adobada", 4500L, "🧀"),
                        Triple("Quesadilla Asada", 9000L, "🧀"), Triple("Quesadilla Adobada", 9000L, "🧀"),
                        Triple("Burrito", 18000L, "🌯"), Triple("Torta", 8000L, "🥖"), Triple("Torta con queso", 10000L, "🥖"),
                    ).map { (n, p, e) ->
                        Product(id(), n, p, "Especiales", emoji = e, modifierGroupIds = listOf(salsa.id, cebolla.id))
                    } +
                    listOf(Triple("Refresco", 3000L, "🥤"), Triple("Agua de sabor", 3000L, "🍹")).map { (n, p, e) ->
                        Product(id(), n, p, "Bebidas", emoji = e, modifierGroupIds = listOf(hielo.id))
                    },
            )
        }
    }
}

fun OrderStatus.label() = when (this) {
    OrderStatus.PENDING -> "en cocina"
    OrderStatus.DELIVERED -> "entregada"
    OrderStatus.CANCELLED -> "cancelada"
}
