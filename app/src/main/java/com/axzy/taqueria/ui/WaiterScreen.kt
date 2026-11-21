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
import com.axzy.taqueria.AppState
import com.axzy.taqueria.Link
import com.axzy.taqueria.OrderStatus
import com.axzy.taqueria.PayStatus
import com.axzy.taqueria.elapsedText
import com.axzy.taqueria.money
import com.axzy.taqueria.tableLabel
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
    val state by app.client.state.collectAsState()

    // Si cocina retira un producto mientras se captura, se quita del borrador.
    LaunchedEffect(state.products) {
        app.draft.retainAvailable(state.products.filter { it.active }.map { it.id }.toSet())
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (val p = page) {
                Page.Home -> Home(
                    ctx, state,
                    onNewOrder = { app.draft.start("", null); page = Page.Capture(pickTable = true) },
                    onNewAccount = { table -> app.draft.start(table, null); page = Page.Capture(pickTable = false) },
                    onOpen = { page = Page.AccountDetail(it) },
                    onSettings = { showSettings = true },
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

    if (showSettings) {
        SettingsDialog(
            current = app.manualHost,
            onDismiss = { showSettings = false },
            onSave = { showSettings = false; app.updateManualHost(it) },
            onChangeRole = { showSettings = false; onExit() },
        )
    }
}

@Composable
private fun Home(
    ctx: WaiterCtx, state: AppState,
    onNewOrder: () -> Unit, onNewAccount: (String) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit,
) {
    val link by ctx.client.link.collectAsState()
    val now = rememberNow() + ctx.client.clockOffset
    var paying by remember { mutableStateOf<Account?>(null) }
    val connected = link is Link.Connected
    val (linkColor, linkText) = when (link) {
        is Link.Connected -> Palette.ok to "● Conectado a cocina"
        is Link.Connecting -> Palette.warn to "● Conectando…"
        Link.Searching -> Palette.late to "● Buscando cocina en la red…"
    }

    // Mesas ordenadas por su cuenta más antigua; dentro, cuentas por número.
    val tables = state.openAccounts().groupBy { it.table }.entries
        .sortedBy { (_, accs) -> accs.minOf { it.createdAt } }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Cuentas abiertas", subtitle = linkText, subtitleColor = linkColor) {
            SquareButton("⚙", onClick = onSettings)
        }
        if (tables.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                FoodIcon("🌮", 88.dp)
                Text("No hay cuentas abiertas", color = Palette.muted, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
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
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(tableLabel(table), Modifier.weight(1f))
                            ActionButton("+ Cuenta", Palette.card, Palette.primary, Modifier.width(120.dp), enabled = connected) {
                                onNewAccount(table)
                            }
                        }
                    }
                    items(accounts.sortedBy { it.number }, key = { it.id }) { acc ->
                        val balance = state.balanceOf(acc)
                        val cancelled = acc.cancelledAt != null
                        // Con saldo: cobrar. Ya pagada pero en cocina: el mismo gesto entrega.
                        val deliverOnly = balance == 0L && state.hasPending(acc.id)
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
                                balance < 0 -> "💵  Devolver"
                                deliverOnly -> "✓  Entregar"
                                else -> "💵  Cobrar"
                            },
                            onSwipeRight = when {
                                !connected -> null
                                balance != 0L -> { { paying = acc } }
                                deliverOnly -> { { ctx.deliverAll(acc, state) } }
                                else -> null
                            },
                        ) {
                            AccountCard(state, acc, now) { onOpen(acc.id) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Palette.card).padding(Ui.pad)) {
            ActionButton(
                if (connected) "+ Nueva orden" else "Sin conexión con cocina",
                Palette.primary, Color.White, Modifier.fillMaxWidth().height(60.dp), enabled = connected, onClick = onNewOrder,
            )
        }
    }

    paying?.let { acc -> PaymentDialog(ctx, state, acc, onDismiss = { paying = null }) }
}

/** Tarjeta uniforme: cuenta, estado de pago, consumo, estado en cocina y saldo. */
@Composable
fun AccountCard(state: AppState, acc: Account, now: Long, onClick: () -> Unit) {
    val orders = state.ordersOf(acc.id)
    val pending = orders.filter { it.status == OrderStatus.PENDING }
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
            StatusChip(pay.label, payColor(pay))
        }
        Text(items.joinToString(" · ").ifEmpty { "Sin consumo" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pending.isNotEmpty()) {
                val wait = now - pending.minOf { it.createdAt }
                StatusChip("En cocina · ${elapsedText(wait)}", waitColor(wait))
            } else {
                StatusChip(if (orders.any { it.status == OrderStatus.DELIVERED }) "Entregado" else "Sin órdenes", Palette.muted)
            }
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
    PayStatus.UNPAID -> Palette.muted
    PayStatus.PARTIALLY_PAID -> Palette.warn
    PayStatus.PAID -> Palette.ok
    PayStatus.CANCELLED -> Palette.late
}

@Composable
private fun SettingsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onChangeRole: () -> Unit) {
    var host by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conexión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Déjalo vacío para encontrar la cocina automáticamente. Si no la encuentra, escribe la IP que aparece arriba en la pantalla de cocina.", color = Palette.muted)
                OutlinedTextField(host, { host = it.take(40) }, label = { Text("IP de cocina (opcional)") }, singleLine = true, shape = Ui.smallShape, placeholder = { Text("192.168.1.20") })
                TextButton(onClick = onChangeRole) { Text("Cambiar rol de este teléfono") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(host) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
