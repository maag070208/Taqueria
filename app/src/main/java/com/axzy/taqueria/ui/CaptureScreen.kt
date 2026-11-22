package com.axzy.taqueria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.axzy.taqueria.MAX_PERSONS
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.R
import com.axzy.taqueria.AppState
import com.axzy.taqueria.Category
import com.axzy.taqueria.DraftLine
import com.axzy.taqueria.Link
import com.axzy.taqueria.MeatColor
import com.axzy.taqueria.Product
import com.axzy.taqueria.QueuedOrder
import com.axzy.taqueria.TAKEOUT
import com.axzy.taqueria.money
import com.axzy.taqueria.parseMoney
import com.axzy.taqueria.tableLabel
import kotlinx.coroutines.launch

private sealed interface Step {
    data object Table : Step
    data object Account : Step
    data object Menu : Step
    data class InCategory(val name: String) : Step
    data object Summary : Step
}

private const val OTHER_TABLE = "Otra…"

/** Montos rápidos para las órdenes por monto. */
private val AMOUNTS = listOf(5_000L, 10_000L, 20_000L)

/** Colores de la carne: fondo, texto y barra del renglón. */
fun MeatColor.colors(): Triple<Color, Color, Color> = when (this) {
    MeatColor.GRAY -> Triple(Color(0xFFF1EFE8), Color(0xFF444441), Color(0xFF888780))
    MeatColor.RED -> Triple(Color(0xFFFCEBEB), Color(0xFF791F1F), Color(0xFFE24B4A))
    MeatColor.BROWN -> Triple(Color(0xFFFAEEDA), Color(0xFF633806), Color(0xFFBA7517))
    MeatColor.NONE -> Triple(Palette.card, Palette.text, Palette.line)
}

/** Precio unitario de un renglón del borrador: el monto en órdenes por monto, si no el de su presentación. */
private fun AppState.unitPrice(l: DraftLine): Long {
    val p = products.firstOrNull { it.id == l.productId } ?: return l.amountCents ?: 0
    return if (categoryOf(p).byAmount) l.amountCents ?: 0 else p.priceOf(l.variant) ?: l.amountCents ?: 0
}

/** "Mixto (Asada + Adobada) · Harina"; la presentación solo si el producto tiene varias. */
private fun lineTitle(p: Product, l: DraftLine, category: Category): String {
    val base = if (category.byAmount) "${p.name} · ${money(l.amountCents ?: 0)}" else p.name
    val mix = if (l.mix.isEmpty()) "" else " (${l.mix.joinToString(" + ")})"
    // La de siempre no se repite en cada renglón; solo se marca la otra (Harina).
    val variant = if (p.variants.size > 1 && l.variant != p.defaultVariant) " · ${l.variant}" else ""
    return base + mix + variant
}

@Composable
fun CaptureScreen(ctx: WaiterCtx, state: AppState, pickTable: Boolean, onClose: () -> Unit) {
    val draft = ctx.app.draft
    val link by ctx.client.link.collectAsState()
    val connected = link is Link.Connected
    val editingOrder = draft.editingOrderId?.let { id -> state.orders.firstOrNull { it.id == id } }
    val isEdit = draft.editingOrderId != null
    var step by remember { mutableStateOf<Step>(if (pickTable) Step.Table else if (isEdit) Step.Summary else Step.Menu) }
    var askDiscard by remember { mutableStateOf(false) }
    var askTable by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    val account = draft.accountId?.let { id -> state.accounts.firstOrNull { it.id == id } }
    val openForTable = state.openAccounts().filter { it.table == draft.table && it.cancelledAt == null }
    val total = draft.lines.sumOf { state.unitPrice(it) * it.qty }
    val count = draft.count

    fun pickTableValue(t: String) {
        draft.start(t, null)
        // Para llevar: cada pedido es una cuenta aparte, no se pregunta a cuál agregar.
        step = if (t != TAKEOUT && state.openAccounts().any { it.table == t && it.cancelledAt == null }) Step.Account else Step.Menu
    }

    fun back() {
        when (val s = step) {
            is Step.InCategory -> { draft.open(null); step = if (isEdit) Step.Summary else Step.Menu }
            Step.Summary -> if (isEdit) {
                if (editingOrder != null && draft.differsFrom(editingOrder)) askDiscard = true else { draft.clear(); onClose() }
            } else step = Step.Menu
            Step.Menu -> when {
                isEdit -> step = Step.Summary
                !draft.isEmpty -> askDiscard = true
                pickTable -> step = if (openForTable.isNotEmpty() && draft.table != TAKEOUT) Step.Account else Step.Table
                else -> onClose()
            }
            Step.Account -> step = Step.Table
            Step.Table -> onClose()
        }
    }
    BackHandler { back() }

    val title = when (val s = step) {
        is Step.InCategory -> s.name
        else -> when {
            isEdit -> "Editar orden #${editingOrder?.number ?: ""}"
            draft.table.isEmpty() -> "Nueva orden"
            else -> "${tableLabel(draft.table)} · ${account?.let { "Cuenta ${it.number}" } ?: "Cuenta nueva"}"
        }
    }
    val subtitle = when (val s = step) {
        Step.Table -> "Paso 1 · ¿Dónde?"
        Step.Account -> "Paso 2 · ¿A qué cuenta?"
        Step.Menu -> "${tableLabel(draft.table)} · ¿Qué van a pedir?"
        is Step.InCategory -> if (state.menuCategories().firstOrNull { it.name == s.name }?.byAmount == true) {
            "Elige la carne y el monto"
        } else "Toca la carne · cada toque +1"
        Step.Summary -> "Revisa antes de enviar"
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = title, subtitle = subtitle, onBack = ::back)
        Box(Modifier.weight(1f)) {
            when (val s = step) {
                Step.Table -> TablePicker(
                    state.tableCount, draft.table,
                    openByTable = state.openAccounts().groupingBy { it.table }.eachCount(),
                    onPick = ::pickTableValue, onOther = { askTable = true },
                )
                Step.Account -> AccountPicker(state, openForTable.sortedBy { it.number }) { accId ->
                    draft.start(draft.table, accId)
                    step = Step.Menu
                }
                Step.Menu -> CategoryMenu(ctx, state) { step = Step.InCategory(it) }
                is Step.InCategory -> CategoryCapture(ctx, state, s.name)
                Step.Summary -> Summary(ctx, state, onOpenLine = { line ->
                    val p = state.products.firstOrNull { it.id == line.productId } ?: return@Summary
                    draft.open(line)
                    step = Step.InCategory(p.category)
                })
            }
        }
        when (val s = step) {
            Step.Menu -> BottomBar {
                ActionButton(
                    if (count == 0) "Elige qué van a pedir" else "Revisar · $count · ${money(total)}",
                    Palette.primary, Color.White, Modifier.fillMaxWidth().height(60.dp), enabled = count > 0,
                ) { step = Step.Summary }
            }
            is Step.InCategory -> {
                val inCat = draft.lines.filter { l -> state.products.firstOrNull { it.id == l.productId }?.category == s.name }
                BottomBar {
                    ActionButton(
                        "Listo · ${inCat.sumOf { it.qty }} · ${money(inCat.sumOf { state.unitPrice(it) * it.qty })}",
                        Palette.primary, Color.White, Modifier.fillMaxWidth().height(60.dp),
                    ) { back() }
                }
            }
            Step.Summary -> BottomBar {
                Row(horizontalArrangement = Arrangement.spacedBy(Ui.gap)) {
                    ActionButton("+ Agregar", Palette.card, Palette.primary, Modifier.weight(1f).height(60.dp)) { step = Step.Menu }
                    ActionButton(
                        when {
                            sending -> "Enviando…"
                            isEdit && !connected -> "Sin conexión"
                            count == 0 -> "Sin productos: mejor cancela la orden"
                            isEdit -> "Guardar cambios · ${money(total)}"
                            !connected -> "Guardar · sale al conectar · ${money(total)}"
                            else -> "Enviar a cocina · ${money(total)}"
                        },
                        Palette.primary, Color.White, Modifier.weight(2f).height(60.dp),
                        // Una orden nueva se puede guardar sin conexión; editar sí necesita a cocina.
                        enabled = (connected || !isEdit) && !sending && count > 0,
                    ) {
                        sending = true
                        val key = draft.clientKey
                        val table = draft.table
                        val accId = draft.accountId
                        val lines = draft.lines()
                        val extras = draft.extrasRequest()
                        val editId = draft.editingOrderId
                        val queued = QueuedOrder(key, table, accId, lines, extras, System.currentTimeMillis())
                        fun queue(msg: String) {
                            ctx.app.outbox.add(queued)
                            if (draft.clientKey == key) draft.clear()
                            ctx.toast(msg)
                            onClose()
                        }
                        ctx.scope.launch {
                            if (editId == null && !connected) {
                                queue("Orden guardada · se manda sola al volver la conexión")
                                sending = false
                                return@launch
                            }
                            try {
                                val order = if (editId != null) ctx.client.editOrder(editId, lines, extras)
                                else ctx.client.createOrder(key, table, accId, lines, extras)
                                // Se limpia solo después de que cocina confirmó.
                                if (draft.clientKey == key) draft.clear()
                                ctx.toast(
                                    if (editId != null) "Orden #${order.number} actualizada en cocina"
                                    else "Orden #${order.number} enviada · ${tableLabel(order.table)} · Cuenta ${order.accountNumber}",
                                )
                                onClose()
                            } catch (e: java.io.IOException) {
                                // No se sabe si cocina la recibió: se reintenta con la misma clave, sin duplicar.
                                if (editId == null) queue("Cocina no respondió · la orden se manda sola")
                                else ctx.toast(e.message ?: "No se pudo enviar; vuelve a intentar")
                            } catch (e: Exception) {
                                ctx.toast(e.message ?: "No se pudo enviar; vuelve a intentar")
                            } finally {
                                sending = false
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text(if (isEdit) "¿Descartar los cambios?" else "¿Descartar esta orden?") },
            text = { Text(if (isEdit) "La orden se queda como estaba en cocina." else "Todavía no se ha enviado a cocina.") },
            confirmButton = { TextButton(onClick = { askDiscard = false; draft.clear(); onClose() }) { Text("Descartar", color = Palette.late) } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Seguir capturando") } },
        )
    }
    if (askTable) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askTable = false },
            title = { Text("Mesa") },
            text = { OutlinedTextField(value, { value = it.take(20) }, singleLine = true, shape = Ui.smallShape, placeholder = { Text("Ej. 15, Barra, Juan") }) },
            confirmButton = {
                TextButton(enabled = value.isNotBlank(), onClick = { askTable = false; pickTableValue(value.trim()) }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { askTable = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Palette.card).padding(Ui.pad)) { content() }
}

/**
 * Para llevar va aparte, arriba y a lo ancho; abajo las mesas configuradas en cocina.
 * Las mesas con cuenta abierta van en amarillo con cuántas tienen, para no abrir otra sin querer.
 */
@Composable
private fun TablePicker(tableCount: Int, current: String, openByTable: Map<String, Int>, onPick: (String) -> Unit, onOther: () -> Unit) {
    val tables = (1..tableCount.coerceIn(1, 100)).map { it.toString() } + OTHER_TABLE
    LazyColumn(
        contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
        verticalArrangement = Arrangement.spacedBy(Ui.gap),
    ) {
        item(key = "llevar") {
            val on = current == TAKEOUT
            Row(
                Modifier.fillMaxWidth().height(72.dp)
                    .background(if (on) Palette.warn else Palette.warn.copy(alpha = 0.14f), Ui.shape)
                    .border(1.dp, Palette.warn, Ui.shape)
                    .clickable { onPick(TAKEOUT) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                UiIcon(R.drawable.ic_bag, 26.dp, if (on) Color.White else Palette.text)
                Text(
                    "Para llevar", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = if (on) Color.White else Palette.text, modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        item(key = "h-mesas") { SectionTitle("Mesas", Modifier.padding(top = 4.dp)) }
        gridRows(tables, 4, key = { it }) { t ->
            val selected = t == current
            val open = openByTable[t] ?: 0
            val (bg, line) = when {
                selected -> Palette.primary to Palette.primary
                open > 0 -> Palette.warn.copy(alpha = 0.18f) to Palette.warn
                else -> Palette.card to Palette.line
            }
            Column(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .background(bg, Ui.shape)
                    .border(if (open > 0 && !selected) 2.dp else 1.dp, line, Ui.shape)
                    .clickable { if (t == OTHER_TABLE) onOther() else onPick(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    t, fontSize = if (t.length <= 2) 28.sp else 15.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else Palette.text, style = centeredText,
                )
                if (open > 0) {
                    Text(
                        if (open == 1) "1 cuenta" else "$open cuentas", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else Palette.text, maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountPicker(state: AppState, accounts: List<com.axzy.taqueria.Account>, onPick: (String?) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Ui.pad)) {
        Grid(accounts.map { it as Any } + "new", 2) { item ->
            val acc = item as? com.axzy.taqueria.Account
            Column(
                Modifier.fillMaxWidth().height(110.dp)
                    .background(if (acc == null) Palette.soft else Palette.card, Ui.shape)
                    .border(1.dp, if (acc == null) Palette.primary else Palette.line, Ui.shape)
                    .clickable { onPick(acc?.id) }
                    .padding(Ui.pad),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (acc == null) {
                    Glyph("+", 28.sp, color = Palette.primary, weight = FontWeight.Black)
                    Text("Cuenta nueva", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.primaryDark)
                } else {
                    Text("Cuenta ${acc.number}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(money(state.totalOf(acc.id)), color = Palette.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        val pay = state.payStatus(acc)
                        StatusChip(pay.label, payColor(pay))
                    }
                }
            }
        }
    }
}

/** Menú principal: un botón grande por categoría con lo que ya se lleva de ella. */
@Composable
private fun CategoryMenu(ctx: WaiterCtx, state: AppState, onOpen: (String) -> Unit) {
    val draft = ctx.app.draft
    val active = state.products.filter { it.active }
    val categories = state.menuCategories().filter { c -> active.any { it.category == c.name } }
    LazyColumn(
        contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
        verticalArrangement = Arrangement.spacedBy(Ui.gap),
    ) {
        gridRows(categories, 2, key = { it.name }) { c ->
            val ids = active.filter { it.category == c.name }.map { it.id }.toSet()
            val qty = draft.lines.filter { it.productId in ids }.sumOf { it.qty }
            Column(
                Modifier.fillMaxWidth().height(110.dp)
                    .background(Palette.card, Ui.shape)
                    .border(if (qty > 0) 2.dp else 1.dp, if (qty > 0) Palette.primary else Palette.line, Ui.shape)
                    .clickable { onOpen(c.name) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DishIcon(DishIcons.categoryKeys(c) + DishIcons.productKeys(active.first { it.category == c.name }, c), 56.dp)
                Text(c.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                if (qty > 0) Text("$qty ${c.name.lowercase()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.primary)
            }
        }
        if (categories.isEmpty()) {
            item(key = "empty") {
                Text("Cocina no tiene productos activos", color = Palette.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
        }
    }
}

/** Captura de una categoría: una tarjeta por persona con sus carnes y renglones. */
@Composable
private fun CategoryCapture(ctx: WaiterCtx, state: AppState, categoryName: String) {
    val draft = ctx.app.draft
    val category = state.menuCategories().firstOrNull { it.name == categoryName } ?: Category(categoryName)
    val products = state.products.filter { it.category == categoryName && it.active }
    val ids = products.map { it.id }.toSet()
    val person = draft.person.coerceIn(1, draft.persons)
    Column(Modifier.fillMaxSize()) {
        PersonStepper(
            persons = draft.persons, selected = person,
            countOf = { p -> draft.linesOf(p, ids).sumOf { it.qty } },
            canAdd = draft.persons < MAX_PERSONS,
            onSelect = { draft.open(null); draft.person = it },
            onAdd = { draft.open(null); draft.addPerson() },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
            verticalArrangement = Arrangement.spacedBy(Ui.gap),
        ) {
            item(key = "p$person") { PersonCard(ctx, state, category, products, person) }
        }
    }
}

/**
 * Personas de la mesa como stepper: un círculo numerado por persona unido por una línea,
 * con lo que lleva de esta categoría abajo, y al final el botón + para agregar otra.
 */
@Composable
private fun PersonStepper(persons: Int, selected: Int, countOf: (Int) -> Int, canAdd: Boolean, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val scroll = rememberScrollState()
    // Al agregar o cambiar de persona, se lleva a la vista.
    LaunchedEffect(persons, selected) { if (selected == persons) scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = Ui.pad).padding(bottom = Ui.gap),
        verticalAlignment = Alignment.Top,
    ) {
        (1..persons).forEach { p ->
            val on = p == selected
            val n = countOf(p)
            if (p > 1) Box(Modifier.padding(top = 21.dp).width(18.dp).height(2.dp).background(if (n > 0) Palette.primary else Palette.line))
            Column(
                Modifier.width(56.dp).clickable(onClickLabel = "Persona $p") { onSelect(p) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(44.dp)
                        .background(if (on) Palette.primary else if (n > 0) Palette.soft else Palette.card, CircleShape)
                        .border(2.dp, if (on || n > 0) Palette.primary else Palette.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph("$p", 18.sp, color = if (on) Color.White else if (n > 0) Palette.primaryDark else Palette.text, weight = FontWeight.Black)
                }
                Text(
                    if (n > 0) "$n" else "Persona", fontSize = 12.sp, maxLines = 1,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Palette.primaryDark else Palette.muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (canAdd) {
            Box(Modifier.padding(top = 21.dp).width(18.dp).height(2.dp).background(Palette.line))
            Column(Modifier.width(56.dp).clickable(onClickLabel = "Agregar persona", onClick = onAdd), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(44.dp).background(Palette.card, CircleShape).border(2.dp, Palette.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { UiIcon(R.drawable.ic_plus, 22.dp, Palette.primary) }
                Text("Agregar", fontSize = 12.sp, color = Palette.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(ctx: WaiterCtx, state: AppState, category: Category, products: List<Product>, person: Int) {
    val draft = ctx.app.draft
    val ids = products.map { it.id }.toSet()
    val lines = draft.linesOf(person, ids)
    // Orden por monto: primero se elige la carne y luego cuánto.
    var amountFor by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Persona $person", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val n = lines.sumOf { it.qty }
            if (n > 0) Text("$n ${category.name.lowercase()}", color = Palette.muted, fontSize = 14.sp)
        }
        Grid(products, 3) { p ->
            MeatTile(
                p, DishIcons.productKeys(p, category), qty = draft.qtyOf(person, p.id),
                subtitle = when {
                    category.byAmount -> null
                    else -> p.priceOf(p.defaultVariant)?.let(::money)
                },
                selected = category.byAmount && amountFor == p.id,
            ) {
                if (category.byAmount) amountFor = p.id
                else draft.tap(p, person, state.defaultMods(p), state.mixOptions(p).take(2))
            }
        }
        if (category.byAmount) {
            products.firstOrNull { it.id == amountFor }?.let { p -> AmountPicker(p) { cents -> draft.addAmount(p, person, cents) } }
        }
        lines.forEach { l ->
            val p = products.first { it.id == l.productId }
            // Igual que en las cuentas: deslizar a la izquierda quita el renglón, con Deshacer.
            key(l.id) {
                SwipeCard(
                    leftLabel = "Quitar", rightLabel = "", onSwipeRight = null, shape = Ui.smallShape,
                    onSwipeLeft = {
                        val at = draft.remove(l)
                        ctx.undo.show("Se quitó ${l.qty} ${lineTitle(p, l, category)}") { draft.restore(l, at) }
                    },
                ) { LineCard(ctx, state, category, p, l, open = draft.openLineId == l.id) }
            }
        }
        if (category.freeExtras && state.freeExtras.isNotEmpty()) {
            Text("Extras gratis", color = Palette.muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.freeExtras.forEach { e ->
                    Pill(e, e in draft.extras[person].orEmpty()) { draft.toggleExtra(person, e) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MeatTile(p: Product, iconKeys: List<String>, qty: Int, subtitle: String?, selected: Boolean, onTap: () -> Unit) {
    val (bg, fg, _) = p.color.colors()
    Box(
        Modifier.fillMaxWidth().height(104.dp)
            .background(bg, Ui.smallShape)
            .border(if (selected || qty > 0) 2.dp else 1.dp, if (selected || qty > 0) Palette.primary else Palette.line, Ui.smallShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DishIcon(iconKeys, 40.dp)
            Text(p.name, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = fg, fontSize = 13.sp)
        }
        if (qty > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Palette.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Glyph("$qty", 13.sp, color = Color.White, weight = FontWeight.Black) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountPicker(p: Product, onAdd: (Long) -> Unit) {
    var other by remember(p.id) { mutableStateOf("") }
    val cents = parseMoney(other)
    val (_, _, bar) = p.color.colors()
    Column(
        Modifier.fillMaxWidth().background(Palette.bg, Ui.smallShape).border(1.dp, bar, Ui.smallShape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Orden de ${p.name} · ¿de cuánto?", fontWeight = FontWeight.Bold)
        Grid(AMOUNTS, 3) { a -> OptionChip(money(a).substringBefore("."), false) { onAdd(a) } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                other, { other = it.take(7) },
                label = { Text("Otra cantidad") }, prefix = { Text("$") }, singleLine = true, shape = Ui.smallShape,
                isError = other.isNotEmpty() && (cents == null || cents < 100),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            ActionButton("Agregar", Palette.primary, Color.White, Modifier.width(110.dp), enabled = cents != null && cents >= 100) {
                onAdd(cents!!)
                other = ""
            }
        }
    }
}

/** Renglón: plegado muestra el resumen; desplegado deja configurar presentación, preparación y mixto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineCard(ctx: WaiterCtx, state: AppState, category: Category, p: Product, l: DraftLine, open: Boolean) {
    val draft = ctx.app.draft
    val (_, _, bar) = p.color.colors()
    val unit = state.unitPrice(l)
    // La barra de color se pinta detrás (no como hijo) para no depender de medidas intrínsecas,
    // que los campos de texto no soportan y cortaban el renglón abierto.
    val barColor = if (p.color == com.axzy.taqueria.MeatColor.NONE) Palette.line else bar
    Row(
        Modifier.fillMaxWidth()
            .clip(Ui.smallShape)
            .background(if (open) Palette.bg else Palette.card)
            .drawBehind { drawRect(barColor, size = Size(6.dp.toPx(), size.height)) }
            .border(1.dp, if (open) Palette.primary else Palette.line, Ui.smallShape)
            .padding(start = 6.dp),
    ) {
        Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable { draft.open(if (open) null else l) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!category.byAmount) UiIcon(if (open) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down, 22.dp, Palette.muted)
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(lineTitle(p, l, category), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val prep = (listOf(state.shortPrep(p, l.mods.toList())) + l.note).filter { it.isNotBlank() }.joinToString(" · ")
                        if (prep.isNotEmpty()) Text(prep, color = Palette.muted, fontSize = 13.sp)
                    }
                }
                Text(money(unit * l.qty), color = Palette.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
                Stepper(l.qty, small = true) { draft.setQty(l, it) }
            }
            if (open && !category.byAmount) {
                if (p.variants.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.variants.forEach { v -> Pill("${v.name} ${money(v.priceCents).substringBefore(".")}", l.variant == v.name) { draft.setVariant(l, v.name) } }
                    }
                }
                state.groupsFor(p).forEach { g ->
                    Text(g.name, color = Palette.muted, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.options.forEach { o -> Pill(o, o in l.mods) { draft.setMods(l, state.toggleMod(p, l.mods, o)) } }
                    }
                }
                if (p.mix) {
                    Text("Mixto de · de 2 a 5", color = Palette.muted, fontSize = 12.sp)
                    val options = state.mixOptions(p)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { m ->
                            Pill(m, m in l.mix) {
                                val next = if (m in l.mix) l.mix - m else l.mix + m
                                if (next.size in 2..5) draft.setMix(l, options.filter { it in next })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    l.note, { draft.setNote(l, it) },
                    modifier = Modifier.fillMaxWidth(), shape = Ui.smallShape, singleLine = true,
                    placeholder = { Text("Nota (opcional)") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("+ Otra de ${p.name}", Palette.card, Palette.primary, Modifier.weight(1f)) {
                        draft.newLine(p, l.person, state.defaultMods(p), state.mixOptions(p).take(2), after = l)
                    }
                    ActionButton("Listo", Palette.primary, Color.White, Modifier.width(110.dp), icon = R.drawable.ic_check) { draft.open(null) }
                }
            }
        }
    }
}

/** Resumen por persona; tocar un renglón lo abre en su categoría para cambiarlo. */
@Composable
private fun Summary(ctx: WaiterCtx, state: AppState, onOpenLine: (DraftLine) -> Unit) {
    val draft = ctx.app.draft
    val byId = state.products.associateBy { it.id }
    val persons = (draft.lines.map { it.person } + draft.extras.filterValues { it.isNotEmpty() }.keys).distinct().sorted()
    LazyColumn(
        contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
        verticalArrangement = Arrangement.spacedBy(Ui.gap),
    ) {
        items(persons, key = { "s$it" }) { person ->
            Column(
                Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Persona $person", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                draft.lines.filter { it.person == person }.forEach { l ->
                    val p = byId[l.productId]
                    val category = p?.let { state.categoryOf(it) } ?: Category("")
                    Row(Modifier.fillMaxWidth().clickable { onOpenLine(l) }, verticalAlignment = Alignment.Top) {
                        QtyBox(l.qty)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                if (p == null) "Producto retirado" else listOf(category.singular, lineTitle(p, l, category)).filter { it.isNotBlank() }.joinToString(" "),
                                fontWeight = FontWeight.SemiBold,
                            )
                            val prep = (listOf(state.shortPrep(p, l.mods.toList())) + l.note).filter { it.isNotBlank() }.joinToString(" · ")
                            if (prep.isNotEmpty()) Text(prep, color = Palette.muted, fontSize = 14.sp)
                        }
                        Text(money(state.unitPrice(l) * l.qty), color = Palette.primary, fontWeight = FontWeight.Bold)
                    }
                }
                draft.extras[person]?.takeIf { it.isNotEmpty() }?.let { items ->
                    Text("Extras: ${state.freeExtras.filter { it in items }.plus(items.filter { it !in state.freeExtras }).joinToString(", ")}", color = Palette.muted, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun QtyBox(qty: Int) {
    Box(Modifier.size(32.dp).background(Palette.soft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Glyph("$qty", 15.sp, color = Palette.primaryDark, weight = FontWeight.Black)
    }
}

/** Opción compacta que se acomoda en renglones (preparación, extras, presentación). */
@Composable
fun Pill(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp)
            .background(if (selected) Palette.primary else Palette.card, Ui.smallShape)
            .border(1.dp, if (selected) Palette.primary else Palette.line, Ui.smallShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = centeredText,
            color = if (selected) Color.White else if (enabled) Palette.text else Palette.muted,
        )
    }
}
