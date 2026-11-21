package com.axzy.taqueria.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.Alerts
import com.axzy.taqueria.KitchenServer
import com.axzy.taqueria.Order
import com.axzy.taqueria.OrderStatus
import com.axzy.taqueria.OrderStore
import com.axzy.taqueria.clockText
import com.axzy.taqueria.elapsedText
import com.axzy.taqueria.tableLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una orden recién llegada se marca como NUEVA durante este tiempo. */
private const val NEW_MS = 60_000L
/** Una orden editada muestra qué cambió durante este tiempo. */
private const val EDIT_MS = 3 * 60_000L

@Composable
fun KitchenScreen(store: OrderStore, server: KitchenServer, notice: String?, onExit: () -> Unit) {
    var showAdmin by rememberSaveable { mutableStateOf(false) }
    // La pantalla de cocina nunca se apaga.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    if (showAdmin) {
        BackHandler { showAdmin = false }
        AdminScreen(store, onBack = { showAdmin = false })
    } else {
        OrdersBoard(store, server, notice, onAdmin = { showAdmin = true }, onExit = onExit)
    }
}

/**
 * Monitor pasivo: el taquero no toca nada. Un ticket por mesa (la más antigua
 * primero) con cada orden, su cuenta y sus grupos de preparación.
 */
@Composable
private fun OrdersBoard(store: OrderStore, server: KitchenServer, notice: String?, onAdmin: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val state by store.state.collectAsState()
    val waiters by server.clientCount.collectAsState()
    val serverError by server.error.collectAsState()
    val now = rememberNow()
    var confirmExit by remember { mutableStateOf(false) }

    val pending = state.orders.filter { it.status == OrderStatus.PENDING }
    val byTable = pending.groupBy { it.table }.values
        .map { orders -> orders.sortedBy { it.createdAt } }
        .sortedBy { it.first().createdAt }

    // Suena y vibra cuando entra una orden nueva o cambia una existente.
    var known by remember { mutableStateOf<Map<String, Long?>?>(null) }
    val current = pending.associate { it.id to it.editedAt }
    LaunchedEffect(current) {
        val before = known
        if (before != null && current.any { (id, edited) -> id !in before || before[id] != edited }) {
            Alerts.beep()
            Alerts.vibrate(context)
        }
        known = current
    }

    val ips = remember(waiters) { KitchenServer.localIps().joinToString("  ") }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(
            title = "Cocina · ${pending.size} ${if (pending.size == 1) "orden" else "órdenes"}",
            subtitle = serverError ?: "${if (waiters > 0) "●" else "○"} ${if (waiters == 1) "1 mesera conectada" else "$waiters meseras conectadas"} · IP $ips",
            subtitleColor = when {
                serverError != null -> Palette.late
                waiters > 0 -> Palette.ok
                else -> Palette.muted
            },
        ) {
            // Configuración, no operación: menú y rol del teléfono.
            SquareButton("📋", onClick = onAdmin)
            SquareButton("⇄", onClick = { confirmExit = true })
        }
        if (notice != null) {
            Text(
                notice, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.pad).padding(bottom = Ui.gap)
                    .background(Palette.warn.copy(alpha = 0.2f), Ui.smallShape).padding(12.dp),
            )
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (byTable.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    FoodIcon("🌮", 96.dp)
                    Text("Sin órdenes pendientes", color = Palette.muted, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                val cols = columnsFor(maxWidth - Ui.pad * 2, 180.dp, 1..6)
                LazyColumn(
                    contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
                    verticalArrangement = Arrangement.spacedBy(Ui.gap),
                ) {
                    gridRows(byTable, cols, key = { it.first().table }) { orders -> TableTicket(orders, now) }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Cambiar el rol de este teléfono?") },
            text = { Text("Las meseras se desconectarán. Las órdenes y el menú se conservan.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onExit() }) { Text("Cambiar") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TableTicket(orders: List<Order>, now: Long) {
    val first = orders.first()
    val elapsed = now - first.createdAt
    val color = waitColor(elapsed)
    val pulse = if (isCritical(elapsed)) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, 0.45f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a").value
    } else 1f

    // fillMaxSize: todos los tickets de una fila miden lo mismo.
    Column(Modifier.fillMaxSize().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape)) {
        Column(
            Modifier.fillMaxWidth()
                .background(color.copy(alpha = pulse), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tableLabel(first.table).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(clockText(first.createdAt), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            // El tiempo siempre en texto: el color no es la única señal.
            Text("ESPERA ${elapsedText(elapsed)}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            orders.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(color = Palette.line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Cuenta ${o.accountNumber} · #${o.number} · ${clockText(o.createdAt)}",
                        color = Palette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val edited = o.editedAt != null && now - o.editedAt < EDIT_MS
                    if (edited) {
                        Text("● EDITADA", color = Palette.warn, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    } else if (now - o.createdAt < NEW_MS && orders.size > 1) {
                        Text("● NUEVA", color = Palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
                val previous = if (o.editedAt != null && now - o.editedAt < EDIT_MS) o.revisions.lastOrNull() else null
                o.lines.forEach { l ->
                    val changed = previous != null && l !in previous
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (changed) Palette.warn.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.Top,
                    ) {
                        QtyBox(l.qty)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(l.name.uppercase(), fontSize = 17.sp, fontWeight = FontWeight.Black)
                            if (l.mods.isNotEmpty()) {
                                Text(l.mods.joinToString(" · ").uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.primaryDark)
                            }
                            if (l.note.isNotBlank()) {
                                Text(
                                    "⚠ ${l.note}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                        .background(Palette.warn.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                previous?.filter { it !in o.lines }?.forEach { l ->
                    Text(
                        "QUITAR: ${l.qty} ${l.name.uppercase()}" + if (l.mods.isNotEmpty()) " · ${l.mods.joinToString(" · ").uppercase()}" else "",
                        color = Palette.late, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
        }
    }
}

/** Ejecuta una operación del almacén fuera del hilo principal y avisa si falla. */
fun CoroutineScope.runStore(context: Context, block: () -> Unit) = launch {
    val error = withContext(Dispatchers.IO) { runCatching(block).exceptionOrNull() }
    if (error != null) Toast.makeText(context, error.message ?: "Error", Toast.LENGTH_SHORT).show()
}
