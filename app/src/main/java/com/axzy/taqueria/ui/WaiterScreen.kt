package com.axzy.taqueria.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.Account
import com.axzy.taqueria.App
import com.axzy.taqueria.R
import com.axzy.taqueria.Staff
import com.axzy.taqueria.AppState
import com.axzy.taqueria.ConnectionQr
import com.axzy.taqueria.Link
import com.axzy.taqueria.WaiterWifi
import com.axzy.taqueria.DEFAULT_PORT
import com.axzy.taqueria.OrderStatus
import com.axzy.taqueria.PayStatus
import com.axzy.taqueria.elapsedText
import com.axzy.taqueria.isTakeout
import com.axzy.taqueria.money
import androidx.compose.runtime.saveable.rememberSaveable
import com.axzy.taqueria.tableLabel
import com.axzy.taqueria.QueuedOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private sealed interface Page {
    data object Home : Page
    /** Captura de productos; el destino (mesa/cuenta) ya está en el borrador. */
    data class Capture(val pickTable: Boolean) : Page
    data class AccountDetail(val id: String) : Page
}

/** Lo que comparten las pantallas de la mesera: llamadas a cocina con aviso de error y deshacer. */
class WaiterCtx(val app: App, val scope: CoroutineScope, val context: Context, val undo: UndoState) {
    val client get() = app.client

    /** Ejecuta [block] contra cocina; si falla muestra el motivo, si sale bien llama [onOk]. */
    fun run(block: suspend () -> Unit, onOk: () -> Unit = {}) = scope.launch {
        try {
            block()
        } catch (e: Exception) {
            toast(e.message ?: "Error de conexión")
            return@launch
        }
        onOk()
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    /** Marca como entregado lo que la cuenta tenga en cocina (ya pagada), con opción de deshacer. */
    fun deliverAll(acc: Account, state: AppState) {
        val pending = state.ordersOf(acc.id).filter { it.status == OrderStatus.PENDING }
        if (pending.isEmpty()) return
        run({ pending.forEach { client.setOrderStatus(it.id, OrderStatus.DELIVERED) } }, onOk = {
            undo.show("${tableLabel(acc.table)} · Cuenta ${acc.number} entregada") {
                pending.forEach { client.setOrderStatus(it.id, OrderStatus.PENDING) }
            }
        })
    }
}

@Composable
fun WaiterScreen(app: App, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = remember { UndoState() }
    val ctx = remember { WaiterCtx(app, scope, context, undo) }
    var page by remember { mutableStateOf<Page>(Page.Home) }
    var showSettings by remember { mutableStateOf(false) }
    // Escáner del QR de conexión que muestra la cocina.
    var scanning by remember { mutableStateOf(false) }
    val state by app.client.state.collectAsState()

    // Avisos de las órdenes que se mandaron solas al volver la conexión.
    LaunchedEffect(Unit) { app.outbox.notices.collect { ctx.toast(it) } }

    // Si cocina retira un producto mientras se captura, se quita del borrador.
    LaunchedEffect(state.products) {
        app.draft.retainAvailable(state.products.filter { it.active }.map { it.id }.toSet())
    }

    if (scanning) {
        QrScannerScreen(
            onDetected = { raw ->
                scanning = false
                when (val r = ConnectionQr.parse(raw)) {
                    is ConnectionQr.Result.Valid -> {
                        app.connectToKitchen(r.host, r.port, r.wifi)
                        ctx.toast(r.wifi?.let { "Entrando a la red de cocina «${it.ssid}»…" } ?: "Cocina configurada: ${r.host}:${r.port}")
                    }
                    is ConnectionQr.Result.Invalid -> ctx.toast(r.message)
                }
            },
            onCancel = { scanning = false },
            title = "Escanear QR de cocina",
            hint = "Apunta al QR de la cocina (menú → Conectar meseras o Activar zona). Si la cocina abrió su zona, el teléfono entra solo a esa red.",
            fallbackLabel = "Cancelar",
        )
        return
    }

    val drawer = rememberDrawerControl()
    val groups = listOf(
        listOf(
            DrawerItem(R.drawable.ic_receipt, "Cuentas abiertas", selected = true) {},
            DrawerItem(R.drawable.ic_wifi, "Conexión con cocina") { showSettings = true },
        ),
        listOf(DrawerItem(R.drawable.ic_swap, "Cambiar rol del teléfono", onClick = onExit)),
    )
    AppDrawer(drawer, Staff.MESERO, "Teléfono de mesero", groups) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                when (val p = page) {
                    Page.Home -> Home(
                        ctx, state,
                        onNewOrder = { app.draft.start("", null); page = Page.Capture(pickTable = true) },
                        onNewAccount = { table -> app.draft.start(table, null); page = Page.Capture(pickTable = false) },
                        onOpen = { page = Page.AccountDetail(it) },
                        onReview = { q -> app.outbox.remove(q.key); app.draft.load(q); page = Page.Capture(pickTable = false) },
                        onMenu = drawer::open,
                    )
                    is Page.Capture -> CaptureScreen(ctx, state, pickTable = p.pickTable, onClose = { page = Page.Home })
                    is Page.AccountDetail -> {
                        BackHandler { page = Page.Home }
                        AccountScreen(
                            ctx, state, p.id,
                            onBack = { page = Page.Home },
                            onAddProducts = { acc -> app.draft.start(acc.table, acc.id); page = Page.Capture(pickTable = false) },
                            onEditOrder = { order -> app.draft.startEdit(order); page = Page.Capture(pickTable = false) },
                        )
                    }
                }
            }
            UndoBar(undo) { item -> ctx.run({ item.undo() }, onOk = { ctx.toast("Listo, se deshizo") }) }
        }
    }

    if (showSettings) {
        SettingsDialog(
            current = app.manualHost,
            onDismiss = { showSettings = false },
            onSave = { text ->
                showSettings = false
                val host = text.substringBeforeLast(":", text).trim()
                if (host.isEmpty()) app.updateManualHost("") else app.connectToKitchen(host, text.substringAfterLast(":", "").toIntOrNull() ?: DEFAULT_PORT, null)
            },
            onScanQr = { showSettings = false; scanning = true },
        )
    }
}

@Composable
private fun Home(
    ctx: WaiterCtx, state: AppState,
    onNewOrder: () -> Unit, onNewAccount: (String) -> Unit, onOpen: (String) -> Unit,
    onReview: (QueuedOrder) -> Unit, onMenu: () -> Unit,
) {
    val link by ctx.client.link.collectAsState()
    val queued by ctx.app.outbox.items.collectAsState()
    // Sin conexión se puede capturar con el último menú que mandó cocina; la orden queda en cola.
    val canCapture = link is Link.Connected || state.products.isNotEmpty()
    val zone by ctx.app.waiterWifi.state.collectAsState()
    val now = rememberNow() + ctx.client.clockOffset
    var paying by remember { mutableStateOf<Account?>(null) }
    // Deslizar → abre las acciones disponibles: entregar (aunque no esté pagada) y cobrar.
    var actions by remember { mutableStateOf<Account?>(null) }
    val connected = link is Link.Connected
    val update by ctx.client.latestApp.collectAsState()
    val (linkColor, linkText) = when {
        link is Link.Connected -> Palette.ok to "● Conectado a ${state.kitchenName.ifBlank { "cocina" }}"
        link is Link.Rejected -> Palette.late to "● ${(link as Link.Rejected).message}"
        zone is WaiterWifi.State.Joining -> Palette.warn to "● Entrando a la red de cocina «${(zone as WaiterWifi.State.Joining).ssid}»…"
        zone is WaiterWifi.State.Failed -> Palette.late to "● ${(zone as WaiterWifi.State.Failed).message}"
        else -> when (link) {
        is Link.Connected -> Palette.ok to "● Conectado a ${state.kitchenName.ifBlank { "cocina" }}"
        is Link.Connecting -> Palette.warn to "● Conectando…"
        Link.Searching -> Palette.late to "● Buscando cocina en la red…"
        is Link.Rejected -> Palette.late to "● ${(link as Link.Rejected).message}"
        }
    }

    // Para llevar va en su propia pestaña: son clientes de paso, no mesas.
    var takeoutTab by rememberSaveable { mutableStateOf(false) }
    val open = state.openAccounts()
    val takeoutCount = open.count { isTakeout(it.table) }
    // Mesas ordenadas por su cuenta más antigua; dentro, cuentas por número.
    val tables = open.filter { isTakeout(it.table) == takeoutTab }.groupBy { it.table }.entries
        .sortedBy { (_, accs) -> accs.minOf { it.createdAt } }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Cuentas abiertas", subtitle = linkText, subtitleColor = linkColor, onMenu = onMenu)
        Segmented(
            listOf("Mesas · ${open.size - takeoutCount}", "Para llevar · $takeoutCount"),
            if (takeoutTab) 1 else 0, { takeoutTab = it == 1 },
            Modifier.padding(start = Ui.pad, end = Ui.pad, bottom = Ui.gap),
        )
        update?.let { UpdateBanner(it, Modifier.padding(start = Ui.pad, end = Ui.pad, bottom = Ui.gap)) }
        if (queued.isNotEmpty()) QueuedOrders(queued, connected, onReview, onDiscard = { ctx.app.outbox.remove(it.key) })
        if (tables.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Illustration(if (takeoutTab) R.drawable.ico_bolsa_llevar else R.drawable.ico_taco_generico, 88.dp)
                Text(if (takeoutTab) "No hay pedidos para llevar" else "No hay mesas abiertas", color = Palette.muted, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
                verticalArrangement = Arrangement.spacedBy(Ui.gap),
            ) {
                item(key = "hint") {
                    Text("Toca: detalle · ← cancelar · → cobrar o entregar", color = Palette.muted, fontSize = 13.sp)
                }
                tables.forEach { (table, accounts) ->
                    item(key = "t-$table") {
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionTitle(tableLabel(table), Modifier.weight(1f))
                                ActionButton(if (isTakeout(table)) "+ Pedido" else "+ Cuenta", Palette.card, Palette.primary, Modifier.width(120.dp), enabled = canCapture) {
                                    onNewAccount(table)
                                }
                            }
                            // Cuentas divididas: la mesa resume a todas (con una sola, la tarjeta ya lo dice).
                            if (accounts.size > 1) TableSummary(state, accounts, now)
                        }
                    }
                    items(accounts.sortedBy { it.number }, key = { it.id }) { acc ->
                        val balance = state.balanceOf(acc)
                        val cancelled = acc.cancelledAt != null
                        val pending = state.hasPending(acc.id)
                        // Comer antes de pagar es normal: el gesto ofrece entregar y cobrar por separado.
                        val hasActions = pending || balance != 0L
                        SwipeCard(
                            leftLabel = "Cancelar cuenta",
                            onSwipeLeft = if (!cancelled && connected) {
                                {
                                    ctx.run({ ctx.client.cancelAccount(acc.id) }, onOk = {
                                        ctx.undo.show("${tableLabel(acc.table)} · Cuenta ${acc.number} cancelada") {
                                            ctx.client.restoreAccount(acc.id)
                                        }
                                    })
                                }
                            } else null,
                            rightLabel = when {
                                balance < 0 && pending -> "Entregar o devolver"
                                balance < 0 -> "Devolver"
                                pending && balance > 0 -> "Entregar o cobrar"
                                pending -> "Entregar"
                                else -> "Cobrar"
                            },
                            onSwipeRight = if (connected && hasActions) {
                                { actions = acc }
                            } else null,
                        ) {
                            AccountCard(state, acc, now) { onOpen(acc.id) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Palette.card).padding(Ui.pad)) {
            ActionButton(
                when {
                    !canCapture -> "Sin conexión con cocina"
                    takeoutTab -> "+ Pedido para llevar"
                    else -> "+ Nueva orden"
                },
                Palette.primary, Color.White, Modifier.fillMaxWidth().height(60.dp), enabled = canCapture,
            ) { if (takeoutTab) onNewAccount(com.axzy.taqueria.TAKEOUT) else onNewOrder() }
        }
    }

    paying?.let { acc -> PaymentDialog(ctx, state, acc, onDismiss = { paying = null }) }
    actions?.let { acc ->
        AccountActionsDialog(
            ctx, state, acc,
            onEntregar = { ctx.deliverAll(acc, state); actions = null },
            onCobrar = { paying = acc; actions = null },
            onDismiss = { actions = null },
        )
    }
}

/**
 * Acciones rápidas al deslizar la tarjeta: el taquero puede marcar entregado lo
 * que ya salió de cocina aunque la cuenta se cobre hasta el final, o cobrar.
 */
@Composable
private fun AccountActionsDialog(
    ctx: WaiterCtx, state: AppState, acc: Account,
    onEntregar: () -> Unit, onCobrar: () -> Unit, onDismiss: () -> Unit,
) {
    val balance = state.balanceOf(acc)
    val pendingOrders = state.ordersOf(acc.id).count { it.status == OrderStatus.PENDING }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tableLabel(acc.table)} · Cuenta ${acc.number}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (pendingOrders > 0) "$pendingOrders ${if (pendingOrders == 1) "orden" else "órdenes"} en cocina" else "Nada pendiente en cocina",
                    color = Palette.muted,
                )
                Text(
                    when {
                        balance > 0 -> "Saldo pendiente ${money(balance)}"
                        balance < 0 -> "A devolver ${money(-balance)}"
                        else -> "Cuenta saldada"
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (pendingOrders > 0) {
                    ActionButton("Entregar (sale de cocina)", Palette.ok, Color.White, Modifier.fillMaxWidth(), icon = R.drawable.ic_check, onClick = onEntregar)
                }
                if (balance != 0L) {
                    ActionButton(
                        if (balance > 0) "Cobrar ${money(balance)}" else "Devolver ${money(-balance)}",
                        Palette.primary, Color.White, Modifier.fillMaxWidth(), icon = R.drawable.ic_cash, onClick = onCobrar,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Estado de la mesa con varias cuentas: entrega de todas y lo que deben juntas. */
@Composable
private fun TableSummary(state: AppState, accounts: List<Account>, now: Long) {
    val owed = accounts.sumOf { state.balanceOf(it).coerceAtLeast(0) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeliveryChip(state.deliveryOf(accounts.map { it.id }), now)
        Text(
            "${accounts.size} cuentas" + if (owed > 0) " · debe ${money(owed)}" else " · pagada",
            color = Palette.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tarjeta uniforme: cuenta, estado de pago, consumo, estado en cocina y saldo. */
@Composable
fun AccountCard(state: AppState, acc: Account, now: Long, onClick: () -> Unit) {
    val orders = state.ordersOf(acc.id)
    val balance = state.balanceOf(acc)
    val pay = state.payStatus(acc)
    val items = orders.filter { it.status != OrderStatus.CANCELLED }.flatMap { it.lines }
        .groupBy { it.name }.map { (name, l) -> "${l.sumOf { it.qty }} $name" }
    Column(
        Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape)
            .clickable(onClick = onClick).padding(Ui.pad),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cuenta ${acc.number}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatusChip(pay.label, payColor(pay), filled = pay == PayStatus.PAID || pay == PayStatus.CANCELLED)
        }
        Text(items.joinToString(" · ").ifEmpty { "Sin consumo" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeliveryChip(state.deliveryOf(listOf(acc.id)), now)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    balance > 0 -> "Debe ${money(balance)}"
                    balance < 0 -> "Devolver ${money(-balance)}"
                    else -> money(state.totalOf(acc.id))
                },
                fontWeight = FontWeight.Black, fontSize = 17.sp,
                color = if (balance < 0) Palette.late else Palette.primary,
            )
        }
    }
}

fun payColor(p: PayStatus) = when (p) {
    PayStatus.UNPAID -> Palette.late
    PayStatus.PARTIALLY_PAID -> Palette.warn
    PayStatus.PAID -> Palette.ok
    PayStatus.CANCELLED -> Palette.muted
}

@Composable
internal fun SettingsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onScanQr: () -> Unit) {
    var host by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conexión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Déjalo vacío para encontrar la cocina automáticamente. Si no la encuentra, escribe la IP que aparece arriba en la pantalla de cocina.", color = Palette.muted)
                ActionButton("Escanear QR de cocina", Palette.primary, Color.White, Modifier.fillMaxWidth(), icon = R.drawable.ic_camera, onClick = onScanQr)
                Text("O escribe la IP a mano:", color = Palette.muted, fontSize = 13.sp)
                OutlinedTextField(host, { host = it.take(40) }, label = { Text("IP de cocina (opcional)") }, singleLine = true, shape = Ui.smallShape, placeholder = { Text("192.168.1.20") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(host) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Órdenes que cocina todavía no confirma: las que esperan conexión y las que cocina rechazó. */
@Composable
private fun QueuedOrders(
    queued: List<QueuedOrder>, connected: Boolean, onReview: (QueuedOrder) -> Unit, onDiscard: (QueuedOrder) -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf<QueuedOrder?>(null) }
    val waiting = queued.count { it.error == null }
    Column(
        Modifier.fillMaxWidth().padding(start = Ui.pad, end = Ui.pad, bottom = Ui.gap)
            .background(Palette.card, Ui.smallShape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (waiting > 0) {
            Text(
                (if (waiting == 1) "1 orden" else "$waiting órdenes") + if (connected) " enviándose a cocina…" else " esperando conexión · se mandan solas",
                color = Palette.warn, fontSize = 15.sp,
            )
        }
        queued.filter { it.error != null }.forEach { q ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${tableLabel(q.table)} · ${q.lines.sumOf { it.qty }} productos", fontSize = 15.sp)
                    Text("Cocina no la aceptó: ${q.error}", color = Palette.late, fontSize = 13.sp)
                }
                TextButton(onClick = { onReview(q) }) { Text("Revisar") }
                TextButton(onClick = { confirmDiscard = q }) { Text("Descartar", color = Palette.late) }
            }
        }
    }
    confirmDiscard?.let { q ->
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text("¿Descartar la orden de ${tableLabel(q.table)}?") },
            text = { Text("Cocina nunca la recibió.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = null; onDiscard(q) }) { Text("Descartar", color = Palette.late) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) { Text("Conservar") } },
        )
    }
}
