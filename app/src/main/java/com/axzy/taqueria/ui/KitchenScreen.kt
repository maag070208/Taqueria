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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Canvas
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
import androidx.annotation.DrawableRes
import com.axzy.taqueria.AdminPin
import com.axzy.taqueria.R
import com.axzy.taqueria.Alerts
import com.axzy.taqueria.App
import com.axzy.taqueria.AppState
import com.axzy.taqueria.KitchenServer
import com.axzy.taqueria.KitchenZone
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import com.axzy.taqueria.Order
import com.axzy.taqueria.StationOrder
import com.axzy.taqueria.OrderStatus
import com.axzy.taqueria.OrderStore
import com.axzy.taqueria.DEFAULT_PORT
import com.axzy.taqueria.clockText
import com.axzy.taqueria.elapsedText
import com.axzy.taqueria.isTakeout
import com.axzy.taqueria.tableLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una orden recién llegada se marca como NUEVA durante este tiempo. */
private const val NEW_MS = 60_000L
/** Una orden editada muestra qué cambió durante este tiempo. */
private const val EDIT_MS = 3 * 60_000L

private enum class KitchenPage(val adminOnly: Boolean) { ORDERS(false), AVAILABILITY(false), MENU(true), SALES(true) }

/**
 * Cocina: el taquero ve órdenes, disponibilidad y el QR. Ventas, menú completo, PIN y
 * cambio de rol solo con sesión de admin; salir de admin regresa a las órdenes.
 */
@Composable
fun KitchenScreen(app: App, notice: String?) {
    val store = app.store
    val server = app.server
    val staff = app.staff ?: return
    var page by rememberSaveable { mutableStateOf(KitchenPage.ORDERS) }
    // Si la sesión de admin se cerró (p. ej. la app pasó a segundo plano), no se queda en sus pantallas.
    if (page.adminOnly && !app.admin) page = KitchenPage.ORDERS
    var showQr by rememberSaveable { mutableStateOf(false) }
    var pinMode by remember { mutableStateOf<PinMode?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var pickStation by remember { mutableStateOf(false) }
    val boardState by store.state.collectAsState()
    val drawer = rememberDrawerControl()
    val context = LocalContext.current
    // La pantalla de cocina nunca se apaga.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    fun toOrders() { app.lockAdmin(); page = KitchenPage.ORDERS }
    fun item(@DrawableRes icon: Int, label: String, to: KitchenPage) = DrawerItem(icon, label, page == to) { page = to }
    val zone by app.zone.state.collectAsState()
    // La zona pide permiso de dispositivos cercanos (Android 13+) o de ubicación (antes).
    // Aunque lo nieguen se intenta: la zona responde con el mensaje de qué permiso falta.
    val zonePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { app.zone.start() }
    fun startZone() {
        showQr = true
        val missing = KitchenZone.permissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) app.zone.start() else zonePermissions.launch(missing.toTypedArray())
    }
    val qr = DrawerItem(R.drawable.ic_qr, "Conectar meseras y pantallas (QR)") { showQr = true }
    val viewItem = stationViewItem(boardState, app.stationView) { pickStation = true }
    val zoneItem = if (zone is KitchenZone.State.On) DrawerItem(R.drawable.ic_wifi, "Zona activa") { showQr = true }
        else DrawerItem(R.drawable.ic_wifi, "Activar zona", onClick = ::startZone)
    val groups = if (app.admin) listOf(
        listOf(item(R.drawable.ic_chart, "Ventas", KitchenPage.SALES), item(R.drawable.ic_tag, "Menú y precios", KitchenPage.MENU), qr, zoneItem, viewItem),
        listOf(
            DrawerItem(R.drawable.ic_download, "Respaldo en la Central") { showBackup = true },
            DrawerItem(R.drawable.ic_keypad, "Cambiar PIN") { pinMode = PinMode.CHANGE },
            DrawerItem(R.drawable.ic_swap, "Cambiar rol del teléfono") { confirmExit = true },
            DrawerItem(R.drawable.ic_lock, "Salir de admin", onClick = ::toOrders),
        ),
    ) else listOf(
        listOf(item(R.drawable.ic_orders, "Órdenes", KitchenPage.ORDERS), item(R.drawable.ic_toggle, "Disponibilidad", KitchenPage.AVAILABILITY), qr, zoneItem, viewItem),
        listOf(DrawerItem(R.drawable.ic_key, "Entrar como admin") { pinMode = if (app.adminPin.isSet) PinMode.ENTER else PinMode.CREATE }),
    )

    AppDrawer(drawer, staff, "Teléfono de ${currentGiro().terms.kitchen.lowercase()}", groups) {
        if (page != KitchenPage.ORDERS) BackHandler(onBack = ::toOrders)
        when (page) {
            KitchenPage.ORDERS -> OrdersBoard(store, server, notice, (zone as? KitchenZone.State.On)?.wifi?.ssid, app.license.update, app.stationView, onMenu = drawer::open)
            KitchenPage.AVAILABILITY -> AdminScreen(store, canEdit = false, onMenu = drawer::open)
            KitchenPage.MENU -> AdminScreen(store, canEdit = true, onMenu = drawer::open)
            KitchenPage.SALES -> SalesScreen(store, onMenu = drawer::open)
        }
    }

    if (showQr) {
        ConnectionQrDialog(
            ips = KitchenServer.localIps(), port = DEFAULT_PORT, zone = zone,
            onStartZone = ::startZone, onStopZone = app.zone::stop, onDismiss = { showQr = false },
        )
    }
    pinMode?.let { mode ->
        AdminPinDialog(
            mode,
            onEnter = { pin -> app.enterAdmin(pin).also { if (it == AdminPin.Result.Ok) { pinMode = null; page = KitchenPage.SALES } } },
            onSet = { pin ->
                app.setAdminPin(pin)
                if (mode == PinMode.CREATE) page = KitchenPage.SALES
                pinMode = null
            },
            onDismiss = { pinMode = null },
        )
    }
    if (pickStation) {
        StationPickerDialog(boardState, app.stationView, onPick = { app.updateStationView(it); pickStation = false }, onDismiss = { pickStation = false })
    }
    if (showBackup && app.admin) {
        val current by store.state.collectAsState()
        BackupDialog(app.backupSync, current, onDismiss = { showBackup = false })
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Cambiar el rol de este teléfono?") },
            text = { Text("Las meseras se desconectarán. Las órdenes y el menú se conservan.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; app.applyRole(null) }) { Text("Cambiar") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Cancelar") } },
        )
    }
    CatalogConflictDialog(app.catalogSync)
}

/**
 * El menú cambió en la Central y aquí a la vez. Nada se pierde: la Central guarda cada versión y,
 * si se usa la suya, el menú de aquí queda apartado en el teléfono.
 */
@Composable
private fun CatalogConflictDialog(sync: com.axzy.taqueria.CatalogSync) {
    val conflict by sync.conflict.collectAsState()
    val c = conflict ?: return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun choose(useCentral: Boolean) {
        busy = true
        scope.launch { sync.resolve(useCentral); busy = false }
    }
    val who = c.remote.author.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("La Central cambió el menú") },
        text = {
            Text(
                "En la Central$who cambiaron el menú y aquí también hubo cambios. ¿Cuál se queda? " +
                    "Las cuentas abiertas no se tocan.",
            )
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { choose(true) }) { Text("Usar el de la Central") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { choose(false) }) { Text("Quedarme con el de aquí") } },
    )
}

/**
 * Monitor pasivo: el taquero no toca nada. Un ticket por mesa (la más antigua
 * primero) con cada orden, su cuenta y sus grupos de preparación.
 */
@Composable
private fun OrdersBoard(
    store: OrderStore, server: KitchenServer, notice: String?, zoneSsid: String?, update: com.axzy.taqueria.LatestApp?,
    stationId: String?, onMenu: () -> Unit,
) {
    val state by store.state.collectAsState()
    val waiters by server.clientCount.collectAsState()
    val screens by server.screenCount.collectAsState()
    val serverError by server.error.collectAsState()
    val ips = remember(waiters) { KitchenServer.localIps().joinToString("  ") }
    val who = (if (waiters == 1) "1 mesera" else "$waiters meseras") + if (screens > 0) " · ${if (screens == 1) "1 pantalla" else "$screens pantallas"}" else ""
    OrdersView(
        state = state,
        stationId = stationId,
        subtitle = serverError ?: "${if (waiters > 0) "●" else "○"} $who · ${zoneSsid?.let { "Zona «$it»" } ?: "IP $ips"}",
        subtitleColor = when {
            serverError != null -> Palette.late
            waiters > 0 -> Palette.ok
            else -> Palette.muted
        },
        notice = notice,
        update = update,
        onMenu = onMenu,
    )
}

/**
 * Tablero de tickets: lo usan la cocina principal y las pantallas de cocina extra.
 * Con [stationId] solo se ven los renglones de esa estación.
 */
@Composable
fun OrdersView(
    state: AppState, stationId: String?, subtitle: String, subtitleColor: Color, notice: String?,
    update: com.axzy.taqueria.LatestApp?, onMenu: () -> Unit, offset: Long = 0,
) {
    val context = LocalContext.current
    val now = rememberNow() + offset
    val station = state.stations.firstOrNull { it.id == stationId }
    val pending = state.ordersForStation(station?.id, state.orders.filter { it.status == OrderStatus.PENDING })
    // Un ticket por mesa; para llevar, uno por cliente (cuenta), en su propia sección.
    val tickets = pending.groupBy { if (isTakeout(it.order.table)) "${it.order.table}#${it.order.accountId}" else it.order.table }.values
        .map { orders -> orders.sortedBy { it.order.createdAt } }
        .sortedBy { it.first().order.createdAt }
    val (takeout, byTable) = tickets.partition { isTakeout(it.first().order.table) }

    // Suena y vibra cuando entra una orden nueva o cambia una existente.
    var known by remember { mutableStateOf<Map<String, Long?>?>(null) }
    val current = pending.associate { it.order.id to it.order.editedAt }
    LaunchedEffect(current) {
        val before = known
        if (before != null && current.any { (id, edited) -> id !in before || before[id] != edited }) {
            Alerts.beep()
            Alerts.vibrate(context)
        }
        known = current
    }

    val giro = currentGiro()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(
            title = "${station?.name ?: state.kitchenName.ifBlank { giro.terms.kitchen }} · ${pending.size} ${if (pending.size == 1) "orden" else "órdenes"}",
            subtitle = subtitle,
            subtitleColor = subtitleColor,
            onMenu = onMenu,
        )
        if (notice != null) {
            Text(
                notice, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.pad).padding(bottom = Ui.gap)
                    .background(Palette.warn.copy(alpha = 0.2f), Ui.smallShape).padding(12.dp),
            )
        }
        update?.let { UpdateBanner(it, Modifier.padding(horizontal = Ui.pad).padding(bottom = Ui.gap)) }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (tickets.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Illustration(R.drawable.ico_taco_generico, 96.dp)
                    Text("Sin órdenes pendientes", color = Palette.muted, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                val cols = columnsFor(maxWidth - Ui.pad * 2, 260.dp, 1..5)
                LazyColumn(
                    contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
                    verticalArrangement = Arrangement.spacedBy(Ui.gap),
                ) {
                    if (takeout.isNotEmpty()) {
                        item(key = "h-llevar") { SectionTitle("Para llevar · ${takeout.size}") }
                        gridRows(takeout, cols, key = { "l-" + it.first().order.accountId }) { orders -> TableTicket(state, orders, now) }
                    }
                    if (byTable.isNotEmpty()) {
                        if (takeout.isNotEmpty()) item(key = "h-mesas") { SectionTitle("Mesas · ${byTable.size}", Modifier.padding(top = 8.dp)) }
                        gridRows(byTable, cols, key = { "m-" + it.first().order.table }) { orders -> TableTicket(state, orders, now) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableTicket(state: AppState, parts: List<StationOrder>, now: Long) {
    val orders = parts.map { it.order }
    val elsewhere = parts.sumOf { it.elsewhere }
    val first = orders.first()
    val elapsed = now - first.createdAt
    val color = waitColor(elapsed)
    val pulse = if (isCritical(elapsed)) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, 0.45f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a").value
    } else 1f

    // Ticket de comanda: encabezado del color de la espera y, abajo, cada orden separada por persona.
    // fillMaxSize: todos los tickets de una fila miden lo mismo.
    Column(Modifier.fillMaxSize().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape)) {
        Row(
            Modifier.fillMaxWidth()
                .background(color.copy(alpha = pulse), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isTakeout(first.table)) UiIcon(R.drawable.ic_bag, 26.dp, Color.White, Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isTakeout(first.table)) "Para llevar · Cuenta ${first.accountNumber}" else tableLabel(first.table),
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // El tiempo siempre en texto: el color no es la única señal.
                Text("Espera ${elapsedText(elapsed)}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Text(clockText(first.createdAt), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            orders.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(color = Palette.line, thickness = 2.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Cuenta ${o.accountNumber} · Ticket #${o.number} · ${clockText(o.createdAt)}",
                        color = Palette.muted, fontSize = 14.sp, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val edited = o.editedAt != null && now - o.editedAt < EDIT_MS
                    if (edited) StatusChip("EDITADA", Palette.warn, filled = true)
                    else if (now - o.createdAt < NEW_MS && orders.size > 1) StatusChip("NUEVA", Palette.primary, filled = true)
                }
                val previous = if (o.editedAt != null && now - o.editedAt < EDIT_MS) o.revisions.lastOrNull() else null
                o.persons.forEach { person ->
                    DashedLine()
                    PersonSection(state, o, person, previous)
                }
                previous?.filter { it !in o.lines }?.forEach { l ->
                    Text(
                        "Quitar: ${l.qty} ${state.labelOf(l)}" + if (l.mods.isNotEmpty()) " · ${l.mods.joinToString(" · ")}" else "",
                        color = Palette.late, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
            if (elsewhere > 0) {
                Text(
                    "+ $elsewhere ${if (elsewhere == 1) "renglón" else "renglones"} en otras estaciones",
                    color = Palette.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Lo de una persona en el ticket: su etiqueta, cada renglón con cantidad y preparación en chips, y sus extras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonSection(state: AppState, o: Order, person: Int, previous: List<com.axzy.taqueria.OrderLine>?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Persona $person", color = PersonChip.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.background(PersonChip.bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
        )
        o.lines.filter { it.person == person }.forEach { l ->
            val changed = previous != null && l !in previous
            Row(
                Modifier.fillMaxWidth()
                    .background(if (changed) Palette.warn.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(44.dp).background(Palette.bg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Glyph("${l.qty}", 20.sp, weight = FontWeight.Black)
                }
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.labelOf(l), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (l.mods.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            l.mods.forEach { m -> TicketChip(m, Palette.bg, Palette.text) }
                        }
                    }
                    if (l.note.isNotBlank()) {
                        Row(
                            Modifier.background(Palette.warn.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UiIcon(R.drawable.ic_warning, 16.dp, Palette.text, Modifier.padding(end = 4.dp))
                            Text(l.note, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        o.extrasOf(person).takeIf { it.isNotEmpty() }?.let { items ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Extras:", color = Palette.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp))
                items.forEach { TicketChip(it, Palette.soft, Palette.primaryDark) }
            }
        }
    }
}

private object PersonChip {
    val bg = Color(0xFFDCEAFB)
    val text = Color(0xFF1F5FA8)
}

@Composable
private fun TicketChip(text: String, bg: Color, fg: Color) {
    Text(
        text, color = fg, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Separador punteado, como el corte de un ticket. */
@Composable
private fun DashedLine() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            Palette.line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

/** Ejecuta una operación del almacén fuera del hilo principal y avisa si falla. */
fun CoroutineScope.runStore(context: Context, block: () -> Unit) = launch {
    val error = withContext(Dispatchers.IO) { runCatching(block).exceptionOrNull() }
    if (error != null) Toast.makeText(context, error.message ?: "Error", Toast.LENGTH_SHORT).show()
}
