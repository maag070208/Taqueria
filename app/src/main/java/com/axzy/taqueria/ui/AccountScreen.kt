package com.axzy.taqueria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.Account
import com.axzy.taqueria.AppState
import com.axzy.taqueria.Link
import com.axzy.taqueria.Order
import com.axzy.taqueria.OrderStatus
import com.axzy.taqueria.PayMethod
import com.axzy.taqueria.clockText
import com.axzy.taqueria.elapsedText
import com.axzy.taqueria.money
import com.axzy.taqueria.moneyInput
import com.axzy.taqueria.parseMoney
import com.axzy.taqueria.tableLabel
import java.util.UUID

@Composable
fun AccountScreen(
    ctx: WaiterCtx, state: AppState, accountId: String,
    onBack: () -> Unit, onAddProducts: (Account) -> Unit, onEditOrder: (Order) -> Unit,
) {
    val acc = state.accounts.firstOrNull { it.id == accountId }
    // La cuenta se archiva cuando quedó saldada y entregada: regresar a la lista.
    if (acc == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val link by ctx.client.link.collectAsState()
    val connected = link is Link.Connected
    val now = rememberNow() + ctx.client.clockOffset
    var paying by remember { mutableStateOf(false) }
    val balance = state.balanceOf(acc)
    val total = state.totalOf(acc.id)
    val pay = state.payStatus(acc)
    val orders = state.ordersOf(acc.id).sortedBy { it.createdAt }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "${tableLabel(acc.table)} · Cuenta ${acc.number}", subtitle = pay.label, subtitleColor = payColor(pay), onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
            verticalArrangement = Arrangement.spacedBy(Ui.gap),
        ) {
            item(key = "totals") {
                Column(
                    Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MoneyRow("Total", total)
                    MoneyRow("Pagado", acc.paidCents)
                    MoneyRow(if (balance < 0) "A devolver" else "Saldo pendiente", kotlin.math.abs(balance), strong = true, color = if (balance < 0) Palette.late else Palette.primary)
                }
            }
            item(key = "h-orders") { SectionTitle("Órdenes", Modifier.padding(top = 4.dp)) }
            items(orders, key = { it.id }) { o -> OrderCard(ctx, o, now, connected, onEdit = { onEditOrder(o) }) }
            if (acc.payments.isNotEmpty()) {
                item(key = "h-pay") { SectionTitle("Pagos", Modifier.padding(top = 4.dp)) }
                items(acc.payments, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).background(Palette.card, Ui.smallShape).border(1.dp, Palette.line, Ui.smallShape).padding(horizontal = Ui.pad),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${if (p.amountCents < 0) "Devolución" else p.method.label} · ${clockText(p.at)}" + if (p.voided) " · anulado" else "",
                            color = if (p.voided) Palette.muted else Palette.text, modifier = Modifier.weight(1f),
                        )
                        Text(
                            money(p.amountCents), fontWeight = FontWeight.Bold,
                            color = if (p.voided) Palette.muted else Palette.text,
                            textDecoration = if (p.voided) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Palette.card).padding(Ui.pad)) {
            GridRow(listOf(0, 1), 2) { i ->
                if (i == 0) {
                    ActionButton(
                        "+ Agregar productos", Palette.card, Palette.primary, Modifier.fillMaxWidth().height(60.dp),
                        enabled = connected && acc.cancelledAt == null,
                    ) { onAddProducts(acc) }
                } else {
                    val deliverOnly = balance == 0L && state.hasPending(acc.id)
                    ActionButton(
                        when {
                            balance > 0 -> "Cobrar ${money(balance)}"
                            balance < 0 -> "Devolver ${money(-balance)}"
                            deliverOnly -> "Entregar ✓"
                            else -> "Sin saldo"
                        },
                        if (deliverOnly) Palette.ok else Palette.primary, Color.White, Modifier.fillMaxWidth().height(60.dp),
                        enabled = connected && (balance != 0L || deliverOnly),
                    ) { if (deliverOnly) ctx.deliverAll(acc, state) else paying = true }
                }
            }
        }
    }

    if (paying) PaymentDialog(ctx, state, acc, onDismiss = { paying = false })
}

@Composable
private fun MoneyRow(label: String, cents: Long, strong: Boolean = false, color: Color = Palette.text) {
    Row {
        Text(label, color = if (strong) Palette.text else Palette.muted, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(money(cents), fontWeight = if (strong) FontWeight.Black else FontWeight.SemiBold, fontSize = if (strong) 20.sp else 16.sp, color = color)
    }
}

@Composable
private fun OrderCard(ctx: WaiterCtx, o: Order, now: Long, connected: Boolean, onEdit: () -> Unit) {
    val pending = o.status == OrderStatus.PENDING
    Column(
        Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Orden #${o.number} · ${clockText(o.createdAt)}" + if (o.editedAt != null) " · editada" else "",
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            when (o.status) {
                OrderStatus.PENDING -> {
                    val wait = now - o.createdAt
                    StatusChip("En cocina · ${elapsedText(wait)}", waitColor(wait))
                }
                OrderStatus.DELIVERED -> StatusChip("Entregada", Palette.ok)
                OrderStatus.CANCELLED -> StatusChip("Cancelada", Palette.late)
            }
        }
        o.lines.forEach { l ->
            Row(verticalAlignment = Alignment.Top) {
                QtyBox(l.qty)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(l.name, fontWeight = FontWeight.SemiBold, textDecoration = if (o.status == OrderStatus.CANCELLED) TextDecoration.LineThrough else null)
                    if (l.prep.isNotEmpty()) Text(l.prep, color = Palette.muted, fontSize = 14.sp)
                }
                Text(money(l.subtotalCents), color = Palette.muted)
            }
        }
        if (pending) {
            GridRow(listOf(0, 1, 2), 3) { i ->
                if (i == 1) {
                    ActionButton("Editar", Palette.card, Palette.primary, Modifier.fillMaxWidth(), enabled = connected, onClick = onEdit)
                } else if (i == 0) {
                    ActionButton("Cancelar", Palette.card, Palette.late, Modifier.fillMaxWidth(), enabled = connected) {
                        ctx.run({ ctx.client.setOrderStatus(o.id, OrderStatus.CANCELLED) }, onOk = {
                            ctx.undo.show("Orden #${o.number} cancelada") { ctx.client.setOrderStatus(o.id, OrderStatus.PENDING) }
                        })
                    }
                } else {
                    ActionButton("Entregada", Palette.ok, Color.White, Modifier.fillMaxWidth(), enabled = connected) {
                        ctx.run({ ctx.client.setOrderStatus(o.id, OrderStatus.DELIVERED) }, onOk = {
                            ctx.undo.show("Orden #${o.number} entregada") { ctx.client.setOrderStatus(o.id, OrderStatus.PENDING) }
                        })
                    }
                }
            }
        }
    }
}

/** Billetes rápidos para "¿con cuánto paga?". */
private val BILLS = listOf(10_000L, 20_000L, 30_000L, 50_000L, 100_000L)

/**
 * Cobro de una cuenta: importe (por defecto el saldo), método y confirmación.
 * Efectivo calcula el cambio a partir de con cuánto pagan. Transferencia exige
 * confirmar que el abono llegó. Por defecto también marca como entregado lo que
 * siga en cocina (pagar y entregar suele ser el mismo momento). Con saldo
 * negativo registra una devolución.
 */
@Composable
fun PaymentDialog(ctx: WaiterCtx, state: AppState, acc: Account, onDismiss: () -> Unit) {
    val balance = state.balanceOf(acc)
    val refund = balance < 0
    val max = kotlin.math.abs(balance)
    val hasPending = state.hasPending(acc.id)
    var amount by remember { mutableStateOf(moneyInput(max)) }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    var received by remember { mutableStateOf("") }
    var transferOk by remember { mutableStateOf(false) }
    var deliver by remember { mutableStateOf(hasPending && !refund) }
    var sending by remember { mutableStateOf(false) }
    // Una llave por diálogo: si la red falla y se reintenta, no se duplica el pago.
    val key = remember { UUID.randomUUID().toString() }

    val cents = parseMoney(amount)?.takeIf { it > 0 }
    val cash = method == PayMethod.CASH && !refund
    val receivedCents = if (cash) parseMoney(received) else null
    val change = if (cents != null && receivedCents != null) receivedCents - cents else null
    val valid = cents != null && cents <= max &&
        (refund || method == PayMethod.CASH || transferOk) &&
        (!cash || received.isBlank() || (change != null && change >= 0))

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("${if (refund) "Devolver" else "Cobrar"} · Cuenta ${acc.number}", maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ui.gap)) {
                Text("${tableLabel(acc.table)} · ${if (refund) "a devolver" else "saldo pendiente"} ${money(max)}", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    amount, { amount = it.take(9) },
                    label = { Text("Importe") }, prefix = { Text("$") }, singleLine = true, shape = Ui.smallShape,
                    isError = amount.isNotEmpty() && (cents == null || cents > max),
                    supportingText = { if (cents != null && cents < max) Text("Quedará pendiente ${money(max - cents)}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Grid(PayMethod.entries.toList(), 2) { m ->
                    OptionChip(m.label, m == method) { method = m; transferOk = false }
                }
                if (cash) {
                    Text("¿Con cuánto paga?", fontWeight = FontWeight.Bold)
                    Grid(listOf(-1L) + BILLS, 3) { bill ->
                        val value = if (bill < 0) cents else bill
                        OptionChip(
                            if (bill < 0) "Exacto" else money(bill).substringBefore("."),
                            selected = value != null && receivedCents == value,
                            enabled = value != null && (cents == null || value >= cents),
                        ) { value?.let { received = moneyInput(it) } }
                    }
                    OutlinedTextField(
                        received, { received = it.take(9) },
                        label = { Text("Otra cantidad") }, prefix = { Text("$") }, singleLine = true, shape = Ui.smallShape,
                        isError = received.isNotBlank() && (receivedCents == null || (change ?: 0) < 0),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (change != null) {
                        Row(
                            Modifier.fillMaxWidth().height(64.dp)
                                .background(if (change >= 0) Palette.soft else Palette.late.copy(alpha = 0.12f), Ui.smallShape)
                                .padding(horizontal = Ui.pad),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (change >= 0) "Cambio" else "Falta", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                money(kotlin.math.abs(change)), fontSize = 28.sp, fontWeight = FontWeight.Black,
                                color = if (change >= 0) Palette.primaryDark else Palette.late,
                            )
                        }
                    }
                }
                if (method == PayMethod.TRANSFER && !refund) {
                    CheckRow("Ya verifiqué que la transferencia llegó", transferOk) { transferOk = it }
                }
                if (hasPending && !refund) {
                    CheckRow("Marcar como entregada (sale de cocina)", deliver) { deliver = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !sending, onClick = {
                sending = true
                val signed = if (refund) -cents!! else cents!!
                val rec = receivedCents
                val chg = change
                val del = deliver
                ctx.run({
                    val paymentId = ctx.client.addPayment(key, acc.id, signed, method, rec, del)
                    // Lo más importante primero: el cambio que hay que dar.
                    val msg = when {
                        chg != null && chg > 0 -> "Cambio ${money(chg)} · cobrado ${money(cents)}"
                        refund -> "Devolución de ${money(cents)} registrada"
                        else -> "Pago de ${money(cents)} registrado" + if (del) " · entregada" else ""
                    }
                    ctx.undo.show(msg) { ctx.client.voidPayment(acc.id, paymentId) }
                }, onOk = onDismiss).invokeOnCompletion { sending = false }
            }) { Text(if (sending) "Registrando…" else "Registrar ${cents?.let { money(it) } ?: ""}") }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = Palette.primary))
        Text(label, fontSize = 14.sp)
    }
}
