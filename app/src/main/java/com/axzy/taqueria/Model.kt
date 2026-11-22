package com.axzy.taqueria

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------- Catálogo ----------------

/**
 * Tipo de modificación: "Salsa" con opciones Con / Sin / Aparte. Si es exclusiva
 * solo se puede elegir una opción (no existe "con salsa" + "sin salsa").
 */
@Serializable
data class ModifierGroup(
    val id: String,
    val name: String,
    val options: List<String>,
    val exclusive: Boolean = true,
    /** Opción con la que arranca cada renglón nuevo (ej. "Con todo"); vacío = ninguna. */
    val defaultOption: String = "",
    /**
     * Atajos: elegir la opción marca otras de otros tipos. "Puro verde" → Sin salsa, Sin cebolla…
     * Lo configura el taquero en Admin.
     */
    val implies: Map<String, List<String>> = emptyMap(),
)

/** Botón del menú principal de la mesera (Tacos, Quesadillas, Órdenes…). */
@Serializable
data class Category(
    val name: String,
    /** Cómo se nombra una pieza: "Taco" Asada, "Quesadilla" Asada; vacío = solo el producto. */
    val singular: String = "",
    /** Sin uso: la ilustración sale del nombre (ver DishIcons). Se conserva por compatibilidad de datos. */
    val icon: String = "",
    /** Se vende por monto (orden de $100 de asada), no por pieza con precio fijo. */
    val byAmount: Boolean = false,
    /** Muestra los extras gratis (cebollitas, chiles…) por persona. */
    val freeExtras: Boolean = true,
)

/** Presentación con precio propio: tortilla de maíz o de harina, torta sencilla o con queso. */
@Serializable
data class Variant(val name: String, val priceCents: Long)

/** Color de la tarjeta para reconocer la carne de un vistazo. */
@Serializable
enum class MeatColor(val label: String) { NONE("Sin color"), GRAY("Gris"), RED("Rojo"), BROWN("Café") }

/** Precios en centavos para no arrastrar errores de punto flotante. El orden de la lista es el orden de aparición. */
@Serializable
data class Product(
    val id: String,
    val name: String,
    /** Precio si no tiene presentaciones. */
    val priceCents: Long,
    val category: String,
    val active: Boolean = true,
    /** Ícono que se ve en la tarjeta; vacío = el de su categoría. */
    val emoji: String = "",
    val modifierGroupIds: List<String> = emptyList(),
    val color: MeatColor = MeatColor.NONE,
    /** Si tiene, cada presentación lleva su precio y se elige al pedir; la primera es la de siempre. */
    val variants: List<Variant> = emptyList(),
    /** Mixto: al pedir se eligen las carnes que lleva. */
    val mix: Boolean = false,
) {
    val defaultVariant get() = variants.firstOrNull()?.name.orEmpty()
    /** Precio de la presentación; nulo si este producto no se vende así. */
    fun priceOf(variant: String): Long? =
        if (variants.isEmpty()) priceCents.takeIf { variant.isEmpty() } else variants.firstOrNull { it.name == variant }?.priceCents
}

// ---------------- Órdenes, cuentas y pagos ----------------

/** Grupo de preparación: N piezas de un producto con las mismas modificaciones. */
@Serializable
data class OrderLine(
    val productId: String,
    val name: String,
    val priceCents: Long,
    val qty: Int,
    val mods: List<String> = emptyList(),
    val note: String = "",
    /** Presentación elegida (Maíz, Harina…); vacío si el producto no tiene. */
    val variant: String = "",
    /** Carnes del mixto. */
    val mix: List<String> = emptyList(),
    /** Persona de la mesa a la que va (1, 2…). */
    val person: Int = 1,
) {
    val subtotalCents get() = priceCents * qty
    val prep get() = (mods + note).filter { it.isNotBlank() }.joinToString(", ")
    /** "Taco Mixto (Asada + Adobada) · Harina". */
    val label get() = name + (if (mix.isEmpty()) "" else " (${mix.joinToString(" + ")})") + (if (variant.isEmpty()) "" else " · $variant")
}

/** Extras sin costo que pidió una persona (cebollitas, chiles…). */
@Serializable
data class PersonExtras(val person: Int, val items: List<String>)

/** Estado de preparación. El pago es independiente y vive en la cuenta. */
@Serializable
enum class OrderStatus { PENDING, DELIVERED, CANCELLED }

@Serializable
data class Order(
    val id: String,
    val number: Int,
    val accountId: String,
    val table: String,
    val accountNumber: Int,
    val lines: List<OrderLine>,
    val status: OrderStatus,
    val createdAt: Long,
    val deliveredAt: Long? = null,
    val cancelledAt: Long? = null,
    /** Cancelada junto con su cuenta (para restaurarla al deshacer). */
    val cancelledByAccount: Boolean = false,
    /** Entregada al registrar este pago (para regresarla a cocina si se deshace el pago). */
    val deliveredByPayment: String? = null,
    /** Versiones anteriores de [lines], de la más vieja a la más reciente: el historial de ediciones. */
    val revisions: List<List<OrderLine>> = emptyList(),
    val editedAt: Long? = null,
    /** Llave del cliente para que un reintento por mala señal no duplique la orden. */
    val clientKey: String,
    val extras: List<PersonExtras> = emptyList(),
) {
    val persons get() = (lines.map { it.person } + extras.map { it.person }).distinct().sorted()
    fun extrasOf(person: Int) = extras.firstOrNull { it.person == person }?.items.orEmpty()
    val totalCents get() = lines.sumOf { it.subtotalCents }
}

@Serializable
enum class PayMethod(val label: String) { CASH("Efectivo"), TRANSFER("Transferencia") }

/** Movimiento de pago. Negativo = devolución. Nunca se borra: al deshacer se marca como anulado. */
@Serializable
data class Payment(
    val id: String,
    val amountCents: Long,
    val method: PayMethod,
    val at: Long,
    val clientKey: String,
    val voided: Boolean = false,
    /** Efectivo recibido, para saber el cambio que se dio. */
    val receivedCents: Long? = null,
)

@Serializable
data class Account(
    val id: String,
    val table: String,
    /** Cuenta 1, Cuenta 2… dentro de la mesa. */
    val number: Int,
    val createdAt: Long,
    val payments: List<Payment> = emptyList(),
    val cancelledAt: Long? = null,
    /** Cuándo quedó saldada y sin nada en cocina; se archiva tras el periodo de deshacer. */
    val closedAt: Long? = null,
) {
    val paidCents get() = payments.filterNot { it.voided }.sumOf { it.amountCents }
}

enum class PayStatus(val label: String) {
    UNPAID("Sin pagar"), PARTIALLY_PAID("Pago parcial"), PAID("Pagada"), CANCELLED("Cancelada"),
}

/** Todo lo que vive en el teléfono de cocina. Las cuentas cerradas pasan al historial. */
@Serializable
data class AppState(
    val products: List<Product> = emptyList(),
    val modifierGroups: List<ModifierGroup> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val orders: List<Order> = emptyList(),
    val counterDate: String = "",
    val counterLast: Int = 0,
    val categories: List<Category> = emptyList(),
    /** Mesas que ve la mesera (1…N); "Para llevar" va aparte. */
    val tableCount: Int = 12,
    /** Extras sin costo que se pueden pedir por persona. */
    val freeExtras: List<String> = emptyList(),
) {
    /** Categorías configuradas más las que solo existan en productos, en orden. */
    fun menuCategories(): List<Category> =
        categories + products.map { it.category }.distinct().filter { c -> categories.none { it.name == c } }.map { Category(it) }

    fun categoryOf(product: Product) = menuCategories().firstOrNull { it.name == product.category } ?: Category(product.category)
    fun ordersOf(accountId: String) = orders.filter { it.accountId == accountId }
    fun totalOf(accountId: String) = ordersOf(accountId).filter { it.status != OrderStatus.CANCELLED }.sumOf { it.totalCents }
    fun balanceOf(account: Account) = totalOf(account.id) - account.paidCents
    fun hasPending(accountId: String) = ordersOf(accountId).any { it.status == OrderStatus.PENDING }

    fun payStatus(account: Account): PayStatus {
        if (account.cancelledAt != null) return PayStatus.CANCELLED
        val paid = account.paidCents
        val balance = balanceOf(account)
        return when {
            paid != 0L && balance <= 0 -> PayStatus.PAID
            paid > 0 -> PayStatus.PARTIALLY_PAID
            else -> PayStatus.UNPAID
        }
    }

    /** Cerrada = no debe nada, no se le debe nada y cocina no tiene nada pendiente de ella. */
    fun isClosed(account: Account) =
        !hasPending(account.id) && balanceOf(account) == 0L && (account.cancelledAt != null || ordersOf(account.id).isNotEmpty())

    fun openAccounts() = accounts.filter { it.closedAt == null }

    /** Nombre del renglón para mostrar: la presentación de siempre (Maíz) no se repite, solo se marca la otra (Harina). */
    fun labelOf(l: OrderLine): String {
        val p = products.firstOrNull { it.id == l.productId }
        return if (p != null && l.variant == p.defaultVariant) l.copy(variant = "").label else l.label
    }
    fun groupsFor(product: Product) = product.modifierGroupIds.mapNotNull { id -> modifierGroups.firstOrNull { it.id == id } }

    /** Carnes que puede llevar un mixto: los demás productos de su categoría. */
    fun mixOptions(product: Product) = products.filter { it.category == product.category && !it.mix }.map { it.name }.distinct()

    /** Con qué arranca un renglón nuevo: la opción predeterminada de cada tipo y lo que esta implique. */
    fun defaultMods(product: Product): Set<String> =
        groupsFor(product).filter { it.defaultOption.isNotEmpty() }
            .fold(emptySet<String>()) { acc, g -> acc + g.defaultOption + g.implies[g.defaultOption].orEmpty() }
            .filterTo(LinkedHashSet<String>()) { m -> groupsFor(product).any { m in it.options } }

    /**
     * Tocar una opción en captura. En un tipo exclusivo reemplaza a las demás (y si tiene
     * predeterminada no se queda vacío). Si la opción es un atajo ("Puro verde"), quita lo que
     * marcaban los otros atajos del tipo y marca lo suyo; después se puede ajustar a mano.
     */
    fun toggleMod(product: Product, current: Set<String>, option: String): Set<String> {
        val groups = groupsFor(product)
        val g = groups.firstOrNull { option in it.options } ?: return current
        val pick = when {
            option !in current -> option
            g.exclusive && g.defaultOption.isNotEmpty() && option != g.defaultOption -> g.defaultOption
            g.exclusive && g.defaultOption.isNotEmpty() -> return current
            else -> return current - option
        }
        var next = if (g.exclusive) current - g.options.toSet() + pick else current + pick
        if (g.implies.isNotEmpty() && g.exclusive) {
            next = next - g.implies.values.flatten().toSet() + g.implies[pick].orEmpty()
            // Lo marcado por el atajo respeta los exclusivos de su tipo (Sin mayonesa quita Con mayonesa).
            for (m in g.implies[pick].orEmpty()) {
                groups.firstOrNull { m in it.options && it.exclusive }?.let { eg -> next = next - (eg.options.toSet() - m) }
            }
        }
        val valid = groups.flatMap { it.options }.toSet()
        return next.filterTo(LinkedHashSet()) { it in valid }
    }

    /**
     * Resumen corto para la mesera: el atajo y solo lo que se movió de él.
     * "Puro verde, con cilantro" en vez de repetir todo lo que el atajo implica.
     */
    fun shortPrep(product: Product?, mods: List<String>): String {
        if (product == null) return mods.joinToString(", ")
        val shown = ArrayList<String>()
        val hidden = HashSet<String>()
        for (g in groupsFor(product).filter { it.implies.isNotEmpty() }) {
            val chosen = mods.firstOrNull { it in g.options } ?: continue
            val implied = g.implies[chosen].orEmpty()
            hidden += implied
            hidden += chosen
            shown += chosen
            // Lo que el atajo quitaba y se volvió a poner: "con cilantro".
            implied.filter { it !in mods }.forEach { shown += it.replaceFirst("Sin ", "con ") }
        }
        mods.filter { it !in hidden }.forEach { shown += if (shown.isEmpty()) it else it.lowercase() }
        return shown.joinToString(", ")
    }
}

// ---------------- Protocolo: una línea JSON por mensaje sobre TCP ----------------

@Serializable
data class LineRequest(
    val productId: String,
    val qty: Int,
    val mods: List<String> = emptyList(),
    val note: String = "",
    val variant: String = "",
    val mix: List<String> = emptyList(),
    val person: Int = 1,
    /** Solo en categorías por monto: cuánto es la orden. */
    val amountCents: Long? = null,
)

@Serializable
sealed interface ClientMsg {
    val reqId: Long

    /** [accountId] nulo = abrir una cuenta nueva en la mesa. */
    @Serializable @SerialName("create")
    data class CreateOrder(
        override val reqId: Long,
        val clientKey: String,
        val table: String,
        val accountId: String?,
        val lines: List<LineRequest>,
        val extras: List<PersonExtras> = emptyList(),
    ) : ClientMsg

    /** Reemplaza los grupos de una orden que sigue en cocina. */
    @Serializable @SerialName("editOrder")
    data class EditOrder(
        override val reqId: Long, val orderId: String, val lines: List<LineRequest>,
        val extras: List<PersonExtras> = emptyList(),
    ) : ClientMsg

    @Serializable @SerialName("orderStatus")
    data class SetOrderStatus(override val reqId: Long, val orderId: String, val status: OrderStatus) : ClientMsg

    @Serializable @SerialName("cancelAccount")
    data class CancelAccount(override val reqId: Long, val accountId: String) : ClientMsg

    @Serializable @SerialName("restoreAccount")
    data class RestoreAccount(override val reqId: Long, val accountId: String) : ClientMsg

    @Serializable @SerialName("pay")
    data class AddPayment(
        override val reqId: Long,
        val clientKey: String,
        val accountId: String,
        val amountCents: Long,
        val method: PayMethod,
        /** Con cuánto pagó en efectivo (opcional). */
        val receivedCents: Long? = null,
        /** Marcar como entregado lo que la cuenta tenga en cocina. */
        val deliver: Boolean = false,
    ) : ClientMsg

    @Serializable @SerialName("voidPayment")
    data class VoidPayment(override val reqId: Long, val accountId: String, val paymentId: String) : ClientMsg

    /**
     * Lo primero que manda la mesera al conectar: con [waiterId] la cocina cuenta
     * cuántas meseras hay (el plan puede limitarlas) y suelta una conexión vieja del mismo teléfono.
     */
    @Serializable @SerialName("hello")
    data class Hello(override val reqId: Long, val waiterId: String, val model: String = "") : ClientMsg

    /** Fallas de la app de mesera; la cocina las sube a la Central. */
    @Serializable @SerialName("appErrors")
    data class ReportErrors(override val reqId: Long, val errors: List<AppErrorReport>) : ClientMsg
}

@Serializable
sealed interface ServerMsg {
    @Serializable @SerialName("state")
    data class State(val state: AppState, val serverTime: Long, val latestApp: LatestApp? = null) : ServerMsg

    @Serializable @SerialName("reply")
    data class Reply(val reqId: Long, val error: String? = null, val order: Order? = null, val paymentId: String? = null) : ServerMsg

    @Serializable @SerialName("ping")
    data object Ping : ServerMsg
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

const val SERVICE_TYPE = "_taqueria._tcp."
const val DEFAULT_PORT = 7777
/** Tiempo en que cocina acepta deshacer una cancelación, entrega o pago. */
const val UNDO_WINDOW_MS = 30_000L
/** Personas por orden. */
const val MAX_PERSONS = 30

private val mxn = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
fun money(cents: Long): String = mxn.format(cents / 100.0)

/** "80", "80.5" o "80,50" → centavos; nulo si no es un importe válido (negativo o más de 2 decimales). */
fun parseMoney(text: String): Long? = text.trim().replace(",", ".").toBigDecimalOrNull()
    ?.takeIf { it.signum() >= 0 && it.scale() <= 2 && it < java.math.BigDecimal(100_000_000) }
    ?.movePointRight(2)?.toLong()

fun moneyInput(cents: Long): String = java.math.BigDecimal(cents).movePointLeft(2).stripTrailingZeros().toPlainString()

fun elapsedText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")
fun clockText(epochMs: Long): String = clock.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

const val TAKEOUT = "Llevar"
fun isTakeout(table: String) = table == TAKEOUT
fun tableLabel(table: String) = if (isTakeout(table)) "Para llevar" else "Mesa $table"
