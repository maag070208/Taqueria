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
)

/** Precios en centavos para no arrastrar errores de punto flotante. El orden de la lista es el orden de aparición. */
@Serializable
data class Product(
    val id: String,
    val name: String,
    val priceCents: Long,
    val category: String,
    val active: Boolean = true,
    /** Ícono que se ve en la tarjeta; vacío = el de su categoría. */
    val emoji: String = "",
    val modifierGroupIds: List<String> = emptyList(),
) {
    val icon get() = emoji.ifEmpty { defaultEmoji(category) }
}

fun defaultEmoji(category: String): String {
    val c = category.lowercase()
    return when {
        "beb" in c || "agua" in c || "refresco" in c -> "🥤"
        "taco" in c -> "🌮"
        "postre" in c -> "🍮"
        "especial" in c || "quesadilla" in c -> "🧀"
        else -> "🍽️"
    }
}

/** Íconos disponibles al editar un producto. */
val PRODUCT_EMOJIS = listOf("🌮", "🌯", "🫓", "🧀", "🥩", "🐷", "🍖", "🌶️", "🥑", "🧅", "🥤", "🥛", "🍹", "🍺", "☕", "🍮")

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
) {
    val subtotalCents get() = priceCents * qty
    val prep get() = (mods + note).filter { it.isNotBlank() }.joinToString(", ")
}

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
) {
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
) {
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
    fun groupsFor(product: Product) = product.modifierGroupIds.mapNotNull { id -> modifierGroups.firstOrNull { it.id == id } }
}

// ---------------- Protocolo: una línea JSON por mensaje sobre TCP ----------------

@Serializable
data class LineRequest(val productId: String, val qty: Int, val mods: List<String> = emptyList(), val note: String = "")

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
    ) : ClientMsg

    /** Reemplaza los grupos de una orden que sigue en cocina. */
    @Serializable @SerialName("editOrder")
    data class EditOrder(override val reqId: Long, val orderId: String, val lines: List<LineRequest>) : ClientMsg

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
}

@Serializable
sealed interface ServerMsg {
    @Serializable @SerialName("state")
    data class State(val state: AppState, val serverTime: Long) : ServerMsg

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

fun tableLabel(table: String) = if (table == "Llevar") "Para llevar" else "Mesa $table"
