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
        val extras = parseExtras(s, req.extras)
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
            extras = extras,
        )
        val accounts = if (req.accountId == null) s.accounts + account else s.accounts
        commit(s.copy(accounts = accounts, orders = s.orders + order, counterDate = today, counterLast = number))
        order
    }

    /** Edita una orden que sigue en cocina; la versión anterior queda en su historial. */
    fun editOrder(orderId: String, raw: List<LineRequest>, rawExtras: List<PersonExtras> = emptyList()): Order = synchronized(lock) {
        val s = _state.value
        val order = s.orders.firstOrNull { it.id == orderId } ?: throw ValidationException("La orden ya no está activa")
        if (order.status != OrderStatus.PENDING) {
            throw ValidationException("Solo se puede editar una orden que sigue en cocina (esta ya está ${order.status.label()})")
        }
        if (s.accounts.firstOrNull { it.id == order.accountId }?.cancelledAt != null) throw ValidationException("La cuenta está cancelada")
        val lines = parseLines(s, raw, original = order.lines)
        val extras = parseExtras(s, rawExtras, original = order.extras)
        if (lines == order.lines && extras == order.extras) return order
        val updated = if (lines == order.lines) order.copy(extras = extras, editedAt = now())
        else order.copy(lines = lines, extras = extras, revisions = order.revisions + listOf(order.lines), editedAt = now())
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
        val entries = done.map { a -> HistoryEntry(a, s.ordersOf(a.id)) }
        historyFile.parentFile?.mkdirs()
        historyFile.appendText(entries.joinToString("") { AppJson.encodeToString(it) + "\n" })
        historyCache = historyCache?.plus(entries)
        val ids = done.map { it.id }.toSet()
        commit(s.copy(accounts = s.accounts.filterNot { it.id in ids }, orders = s.orders.filterNot { it.accountId in ids }))
    }

    // ---------------- Ventas ----------------

    /** Historial leído de disco una sola vez; [archiveClosed] le agrega lo que archiva. */
    private var historyCache: List<HistoryEntry>? = null

    /**
     * Todas las cuentas para el reporte de ventas: las del historial más las activas.
     * Si una cuenta quedó en ambos (se cortó la luz entre archivar y guardar), cuenta una vez.
     */
    fun salesEntries(): List<HistoryEntry> = synchronized(lock) {
        val s = _state.value
        val history = archived()
        val live = s.accounts.map { HistoryEntry(it, s.ordersOf(it.id)) }
        (history + live).associateBy { it.account.id }.values.toList()
    }

    /** Cuentas archivadas en el orden en que se archivaron (el historial solo crece). */
    fun archived(): List<HistoryEntry> = synchronized(lock) {
        historyCache ?: readHistory().also { historyCache = it }
    }

    /** Una línea dañada no tumba el reporte: se omite y se registra. */
    private fun readHistory(): List<HistoryEntry> {
        if (!historyFile.exists()) return emptyList()
        return historyFile.useLines { lines ->
            lines.filter { it.isNotBlank() }.mapNotNull { line ->
                runCatching { AppJson.decodeFromString<HistoryEntry>(line) }
                    .onFailure { runCatching { Log.w("OrderStore", "Línea de historial ilegible", it) } }
                    .getOrNull()
            }.toList()
        }
    }

    /**
     * Reemplaza todo el estado (recuperar un respaldo). Lo que había se aparta en
     * `taqueria.json.antes-de-respaldo-<hora>` por si se recuperó por error.
     */
    fun replaceAll(next: AppState) = synchronized(lock) {
        if (file.exists()) file.copyTo(File(file.parentFile, "${file.name}.antes-de-respaldo-${now()}"), overwrite = true)
        commit(next)
    }

    // ---------------- Catálogo ----------------

    /**
     * Reemplaza productos y modificaciones por el menú predeterminado. Órdenes, cuentas e
     * historial no se tocan: cada línea ya guarda su nombre y precio.
     */
    fun resetCatalog() = synchronized(lock) {
        val menu = seed()
        commit(
            _state.value.copy(
                products = menu.products, modifierGroups = menu.modifierGroups,
                categories = menu.categories, freeExtras = menu.freeExtras,
            ),
        )
    }

    /** Alta (id vacío) o edición de un producto. */
    fun saveProduct(p: Product) = synchronized(lock) {
        val s = _state.value
        val isNew = p.id.isBlank() || s.products.none { it.id == p.id }
        val variants = p.variants.map { Variant(text(it.name, "Presentación", 20), it.priceCents) }
        if (variants.map { it.name.lowercase() }.distinct().size != variants.size) throw ValidationException("Hay presentaciones repetidas")
        if (variants.size > 6) throw ValidationException("Máximo 6 presentaciones")
        (variants.map { it.priceCents } + p.priceCents).forEach { if (it !in 0..10_000_000) throw ValidationException("Precio inválido") }
        val clean = p.copy(
            id = if (isNew) UUID.randomUUID().toString() else p.id,
            name = text(p.name, "Nombre", 40),
            category = text(p.category, "Categoría", 30, required = false).ifEmpty { "Tacos" },
            emoji = p.emoji.trim().also { if (it.length > 16) throw ValidationException("Ícono inválido") },
            modifierGroupIds = p.modifierGroupIds.distinct().filter { g -> s.modifierGroups.any { it.id == g } },
            variants = variants,
        )
        val products = if (isNew) s.products + clean else s.products.map { if (it.id == p.id) clean else it }
        commit(s.copy(products = products))
    }

    fun setTableCount(count: Int) = synchronized(lock) {
        if (count !in 1..100) throw ValidationException("Entre 1 y 100 mesas")
        commit(_state.value.copy(tableCount = count))
    }

    /** Alta (id vacío) o edición de una estación de cocina. */
    fun saveStation(st: Station) = synchronized(lock) {
        val s = _state.value
        val name = text(st.name, "Estación", 30)
        if (st.categories.isEmpty() && st.products.isEmpty()) throw ValidationException("Elige qué prepara la estación")
        if (s.stations.any { it.name.equals(name, ignoreCase = true) && it.id != st.id }) throw ValidationException("Ya hay una estación «$name»")
        val clean = st.copy(name = name, categories = st.categories.distinct().take(50), products = st.products.distinct().take(200))
        val isNew = st.id.isBlank() || s.stations.none { it.id == st.id }
        if (isNew && s.stations.size >= 10) throw ValidationException("Máximo 10 estaciones")
        commit(
            s.copy(
                stations = if (isNew) s.stations + clean.copy(id = UUID.randomUUID().toString())
                else s.stations.map { if (it.id == st.id) clean else it },
            ),
        )
    }

    fun deleteStation(id: String) = synchronized(lock) {
        commit(_state.value.copy(stations = _state.value.stations.filterNot { it.id == id }))
    }

    fun setKitchenName(name: String) = synchronized(lock) {
        commit(_state.value.copy(kitchenName = text(name, "Nombre de la cocina", 30, required = false)))
    }

    fun saveFreeExtras(items: List<String>) = synchronized(lock) {
        val clean = items.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.any { it.length > 30 }) throw ValidationException("Extra: máximo 30 caracteres")
        if (clean.size > 20) throw ValidationException("Máximo 20 extras")
        commit(_state.value.copy(freeExtras = clean))
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

    fun saveModifierGroup(
        id: String?, name: String, options: List<String>, exclusive: Boolean,
        defaultOption: String = "", implies: Map<String, List<String>> = emptyMap(),
    ) = synchronized(lock) {
        val s = _state.value
        val clean = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) throw ValidationException("Agrega al menos una opción")
        if (clean.any { it.length > 30 }) throw ValidationException("Opción: máximo 30 caracteres")
        // Cada opción pertenece a un solo tipo, así la validación de exclusivas no es ambigua.
        val taken = s.modifierGroups.filter { it.id != id }.flatMap { it.options }.toSet()
        clean.firstOrNull { it in taken }?.let { throw ValidationException("\"$it\" ya existe en otro tipo de modificación") }
        val default = defaultOption.trim().takeIf { it in clean && exclusive }.orEmpty()
        // Un atajo solo marca opciones de otros tipos que existen.
        val cleanImplies = if (!exclusive) emptyMap() else implies
            .filterKeys { it in clean }
            .mapValues { (_, v) -> v.distinct().filter { it in taken } }
            .filterValues { it.isNotEmpty() }
        val group = ModifierGroup(id ?: UUID.randomUUID().toString(), text(name, "Nombre", 30), clean, exclusive, default, cleanImplies)
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
        val merged = LinkedHashMap<OrderLine, Int>()
        for (r in raw) {
            val wasOrdered = original.firstOrNull { it.productId == r.productId }
            val p = s.products.firstOrNull { it.id == r.productId && (it.active || wasOrdered != null) }
                ?: wasOrdered?.let { Product(it.productId, it.name, it.priceCents, "") }
                ?: throw ValidationException("Un producto ya no está disponible")
            if (r.qty !in 1..99) throw ValidationException("Cantidad inválida para ${p.name}")
            if (r.person !in 1..MAX_PERSONS) throw ValidationException("Persona inválida")
            // Producto borrado del catálogo: se conserva tal cual se pidió.
            val catalogGone = s.products.none { it.id == p.id }
            val category = s.categoryOf(p)
            // Sin presentación elegida = la de siempre (la primera).
            val variant = r.variant.trim().ifEmpty { p.defaultVariant }

            val price = if (category.byAmount && !catalogGone) {
                val amount = r.amountCents ?: throw ValidationException("Falta el monto de la orden de ${p.name}")
                if (amount !in 100..10_000_000) throw ValidationException("Monto inválido para ${p.name}")
                amount
            } else {
                p.priceOf(variant)
                    ?: original.firstOrNull { it.productId == p.id && it.variant == variant }?.priceCents
                    ?: throw ValidationException("${p.name} no se vende en ${variant.ifEmpty { "esa presentación" }}")
            }

            val mixAllowed = s.mixOptions(p) + wasOrdered?.mix.orEmpty()
            val mix = r.mix.map { it.trim() }.distinct()
            if (p.mix) {
                if (mix.size !in 2..5) throw ValidationException("Elige de 2 a 5 carnes para el mixto")
                mix.firstOrNull { it !in mixAllowed }?.let { throw ValidationException("\"$it\" no va en el mixto") }
            } else if (mix.isNotEmpty() && !catalogGone) {
                throw ValidationException("${p.name} no es mixto")
            }

            val groups = s.groupsFor(p)
            val mods = r.mods.map { it.trim() }.distinct()
            for (m in mods) {
                if (!catalogGone && groups.none { m in it.options }) throw ValidationException("\"$m\" no aplica a ${p.name}")
            }
            for (g in groups.filter { it.exclusive }) {
                if (mods.count { it in g.options } > 1) throw ValidationException("${p.name}: elige solo una opción de ${g.name}")
            }
            // Mismo orden que el catálogo, para que cocina lea siempre igual.
            val ordered = if (catalogGone) mods else groups.flatMap { it.options }.filter { it in mods }
            val orderedMix = if (catalogGone) mix else mixAllowed.filter { it in mix }
            val note = text(r.note, "Nota", 100, required = false)
            val name = if (catalogGone) wasOrdered!!.name else listOf(category.singular, p.name).filter { it.isNotBlank() }.joinToString(" ")
            // Renglones idénticos se unen; la cantidad va aparte de la llave.
            val key = OrderLine(p.id, name, price, 0, ordered, note, variant, orderedMix, r.person)
            merged[key] = (merged[key] ?: 0) + r.qty
        }
        if (merged.values.any { it > 99 }) throw ValidationException("Máximo 99 por renglón")
        // Por persona y en el orden del menú (sortedWith es estable: respeta el orden de captura).
        return merged.map { (line, qty) -> line.copy(qty = qty) }
            .sortedWith(compareBy({ it.person }, { line -> s.products.indexOfFirst { it.id == line.productId } }))
    }

    /** Extras sin costo por persona; los que ya estaban en la orden se aceptan aunque se hayan quitado del menú. */
    private fun parseExtras(s: AppState, raw: List<PersonExtras>, original: List<PersonExtras> = emptyList()): List<PersonExtras> {
        val allowed = s.freeExtras.toSet() + original.flatMap { it.items }
        return raw.groupBy { it.person }.mapNotNull { (person, list) ->
            if (person !in 1..MAX_PERSONS) throw ValidationException("Persona inválida")
            val items = list.flatMap { it.items }.map { it.trim() }.distinct()
            items.firstOrNull { it !in allowed }?.let { throw ValidationException("\"$it\" no está en los extras") }
            items.takeIf { it.isNotEmpty() }?.let { PersonExtras(person, s.freeExtras.filter { e -> e in it } + it.filter { e -> e !in s.freeExtras }) }
        }.sortedBy { it.person }
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
            val allSin = listOf("Sin salsa", "Sin cebolla", "Sin cilantro", "Sin aguacate", "Sin mayonesa")
            val va = ModifierGroup(
                id(), "Va", listOf("Con todo", "Puro verde", "Pura salsa", "Pura carne"),
                defaultOption = "Con todo",
                implies = mapOf(
                    "Puro verde" to listOf("Sin salsa", "Sin cebolla", "Sin cilantro"),
                    "Pura salsa" to listOf("Sin cebolla", "Sin cilantro", "Sin aguacate", "Sin mayonesa"),
                    "Pura carne" to allSin,
                ),
            )
            val ajustes = ModifierGroup(id(), "Ajustes", listOf("Sin salsa", "Sin cebolla", "Sin cilantro", "Sin aguacate"), exclusive = false)
            val mayonesa = ModifierGroup(id(), "Mayonesa", listOf("Sin mayonesa", "Con mayonesa"))
            val hielo = ModifierGroup(id(), "Hielo", listOf("Con hielo", "Sin hielo"))
            val prep = listOf(va.id, ajustes.id, mayonesa.id)

            val color = mapOf("Asada" to MeatColor.GRAY, "Adobada" to MeatColor.RED, "Suadero" to MeatColor.BROWN)
            fun meats(category: String, names: List<String>, variants: (String) -> List<Variant>, price: Long = 0, groups: List<String> = prep) =
                names.map { n ->
                    Product(id(), n, price, category, modifierGroupIds = groups, color = color[n] ?: MeatColor.NONE, variants = variants(n), mix = n == "Mixto")
                }
            val none: (String) -> List<Variant> = { emptyList() }
            val tortilla: (String) -> List<Variant> = { n ->
                listOf(Variant("Maíz", if (n == "Adobada") 3000 else 3500)) + if (n == "Tripa") emptyList() else listOf(Variant("Harina", 10000))
            }
            val conMixto = listOf("Asada", "Adobada", "Suadero", "Cabeza", "Mixto")
            return AppState(
                categories = listOf(
                    Category("Tacos", "Taco"),
                    Category("Quesatacos", "Quesataco"),
                    Category("Quesadillas", "Quesadilla"),
                    Category("Tortas", "Torta"),
                    Category("Burritos", "Burrito"),
                    Category("Vampiros", "Vampiro"),
                    Category("Bebidas", "", freeExtras = false),
                    Category("Órdenes", "Orden de", byAmount = true, freeExtras = false),
                ),
                freeExtras = listOf("Cebollitas", "Chiles", "Tostadas"),
                modifierGroups = listOf(va, ajustes, mayonesa, hielo),
                products = meats("Tacos", listOf("Asada", "Adobada", "Suadero", "Cabeza", "Tripa", "Mixto"), tortilla) +
                    meats("Quesatacos", conMixto, none, 4500) +
                    meats("Quesadillas", conMixto, none, 9000) +
                    meats("Tortas", conMixto, { listOf(Variant("Sencilla", 8000), Variant("Con queso", 10000)) }) +
                    meats("Burritos", conMixto, none, 18000) +
                    meats("Vampiros", conMixto, none, 5000) +
                    listOf("Refresco" to 3000L, "Agua de sabor" to 3000L).map { (n, p) ->
                        Product(id(), n, p, "Bebidas", modifierGroupIds = listOf(hielo.id))
                    } +
                    meats("Órdenes", listOf("Asada", "Adobada", "Suadero", "Cabeza"), none, groups = emptyList()),
            )
        }
    }
}

fun OrderStatus.label() = when (this) {
    OrderStatus.PENDING -> "en cocina"
    OrderStatus.DELIVERED -> "entregada"
    OrderStatus.CANCELLED -> "cancelada"
}
