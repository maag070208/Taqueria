package com.axzy.taqueria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.AppState
import com.axzy.taqueria.Link
import com.axzy.taqueria.Product
import com.axzy.taqueria.money
import com.axzy.taqueria.tableLabel
import kotlinx.coroutines.launch

private enum class Step { TABLE, ACCOUNT, PRODUCTS, SUMMARY }

private const val OTHER_TABLE = "Otra…"
private val TABLES = (1..12).map { it.toString() } + "Llevar"

@Composable
fun CaptureScreen(ctx: WaiterCtx, state: AppState, pickTable: Boolean, onClose: () -> Unit) {
    val draft = ctx.app.draft
    val link by ctx.client.link.collectAsState()
    val connected = link is Link.Connected
    val editingOrder = draft.editingOrderId?.let { id -> state.orders.firstOrNull { it.id == id } }
    val isEdit = draft.editingOrderId != null
    var step by remember { mutableStateOf(if (pickTable) Step.TABLE else if (isEdit) Step.SUMMARY else Step.PRODUCTS) }
    var editing by remember { mutableStateOf<String?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    var askTable by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    val byId = state.products.associateBy { it.id }
    val account = draft.accountId?.let { id -> state.accounts.firstOrNull { it.id == id } }
    val openForTable = state.openAccounts().filter { it.table == draft.table && it.cancelledAt == null }
    val total = draft.groups.sumOf { (byId[it.productId]?.priceCents ?: 0) * it.qty }
    val count = draft.groups.sumOf { it.qty }

    fun pickTableValue(t: String) {
        draft.start(t, null)
        step = if (state.openAccounts().any { it.table == t && it.cancelledAt == null }) Step.ACCOUNT else Step.PRODUCTS
    }

    fun back() {
        when {
            editing != null -> editing = null
            isEdit && step == Step.PRODUCTS -> step = Step.SUMMARY
            isEdit -> if (editingOrder != null && draft.differsFrom(editingOrder)) askDiscard = true else { draft.clear(); onClose() }
            step == Step.SUMMARY -> step = Step.PRODUCTS
            !draft.isEmpty -> askDiscard = true
            step == Step.PRODUCTS && pickTable -> step = if (openForTable.isNotEmpty()) Step.ACCOUNT else Step.TABLE
            step == Step.ACCOUNT -> step = Step.TABLE
            else -> onClose()
        }
    }
    BackHandler { back() }

    val title = when {
        isEdit -> "Editar orden #${editingOrder?.number ?: ""}"
        draft.table.isEmpty() -> "Nueva orden"
        else -> "${tableLabel(draft.table)} · ${account?.let { "Cuenta ${it.number}" } ?: "Cuenta nueva"}"
    }

    editing?.let { pid ->
        val product = byId[pid]
        if (product == null) { editing = null; return }
        ProductEditor(ctx, state, product, onDone = { editing = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = title,
            subtitle = if (isEdit && step == Step.SUMMARY) "${tableLabel(draft.table)} · Cuenta ${account?.number ?: ""}" else stepHint(step),
            onBack = ::back,
        )
        Box(Modifier.weight(1f)) {
            when (step) {
                Step.TABLE -> TablePicker(draft.table, onPick = ::pickTableValue, onOther = { askTable = true })
                Step.ACCOUNT -> AccountPicker(state, openForTable.sortedBy { it.number }) { accId ->
                    draft.start(draft.table, accId)
                    step = Step.PRODUCTS
                }
                Step.PRODUCTS -> ProductGrid(ctx, state, onEdit = { editing = it })
                Step.SUMMARY -> Summary(ctx, state, onEdit = { editing = it })
            }
        }
        when (step) {
            Step.PRODUCTS -> BottomBar {
                ActionButton(
                    if (count == 0) "Toca los productos para agregarlos" else "Revisar · $count · ${money(total)}  →",
                    Palette.primary, Color.White, Modifier.fillMaxWidth().height(60.dp), enabled = count > 0,
                ) { step = Step.SUMMARY }
            }
            Step.SUMMARY -> BottomBar {
                Row(horizontalArrangement = Arrangement.spacedBy(Ui.gap)) {
                    ActionButton("+ Agregar", Palette.card, Palette.primary, Modifier.weight(1f).height(60.dp)) { step = Step.PRODUCTS }
                    ActionButton(
                        when {
                            sending -> "Enviando…"
                            !connected -> "Sin conexión"
                            count == 0 -> "Sin productos: mejor cancela la orden"
                            isEdit -> "Guardar cambios · ${money(total)}"
                            else -> "Enviar a cocina · ${money(total)}"
                        },
                        Palette.primary, Color.White, Modifier.weight(2f).height(60.dp),
                        enabled = connected && !sending && count > 0,
                    ) {
                        sending = true
                        val key = draft.clientKey
                        val table = draft.table
                        val accId = draft.accountId
                        val lines = draft.lines()
                        val editId = draft.editingOrderId
                        ctx.scope.launch {
                            try {
                                val order = if (editId != null) ctx.client.editOrder(editId, lines)
                                else ctx.client.createOrder(key, table, accId, lines)
                                // Se limpia solo después de que cocina confirmó.
                                if (draft.clientKey == key) draft.clear()
                                ctx.toast(
                                    if (editId != null) "Orden #${order.number} actualizada en cocina"
                                    else "Orden #${order.number} enviada · ${tableLabel(order.table)} · Cuenta ${order.accountNumber}",
                                )
                                onClose()
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

private fun stepHint(step: Step) = when (step) {
    Step.TABLE -> "Paso 1 · Elige la mesa"
    Step.ACCOUNT -> "Paso 2 · ¿A qué cuenta?"
    Step.PRODUCTS -> "Toca = +1 · mantén presionado para modificar"
    Step.SUMMARY -> "Revisa y divide la preparación"
}

@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Palette.card).padding(Ui.pad)) { content() }
}

@Composable
private fun TablePicker(current: String, onPick: (String) -> Unit, onOther: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Ui.pad)) {
        Grid(TABLES + OTHER_TABLE, 4) { t ->
            val selected = t == current
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .background(if (selected) Palette.primary else Palette.card, Ui.shape)
                    .border(1.dp, if (selected) Palette.primary else Palette.line, Ui.shape)
                    .clickable { if (t == OTHER_TABLE) onOther() else onPick(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t, fontSize = if (t.length <= 2) 28.sp else 15.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else Palette.text,
                )
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
                    Text("+", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Palette.primary)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductGrid(ctx: WaiterCtx, state: AppState, onEdit: (String) -> Unit) {
    val draft = ctx.app.draft
    val listState = rememberLazyListState()
    val products = state.products.filter { it.active }
    val sections = products.groupBy { it.category }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = columnsFor(maxWidth - Ui.pad * 2, 104.dp, 3..6)
        val sectionIndex = remember(sections, cols) {
            var i = 0
            sections.mapValues { (_, list) -> i.also { i += 1 + (list.size + cols - 1) / cols } }
        }
        Column(Modifier.fillMaxSize()) {
            if (sections.size > 1) {
                val cats = sections.keys.toList()
                val jump: (String) -> Unit = { cat -> ctx.scope.launch { listState.animateScrollToItem(sectionIndex[cat] ?: 0) } }
                if (cats.size <= 4) {
                    Box(Modifier.padding(start = Ui.pad, end = Ui.pad, bottom = 12.dp)) {
                        GridRow(cats, cats.size) { cat -> CategoryChip(sections.getValue(cat).first().icon, cat, Modifier.fillMaxWidth()) { jump(cat) } }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { items(cats) { cat -> CategoryChip(sections.getValue(cat).first().icon, cat) { jump(cat) } } }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
                verticalArrangement = Arrangement.spacedBy(Ui.gap),
            ) {
                sections.forEach { (category, list) ->
                    item(key = "h-$category") { SectionTitle(category) }
                    gridRows(list, cols, key = { it.id }) { p ->
                        val groups = draft.groupsOf(p.id)
                        ProductTile(
                            p, draft.qtyOf(p.id), customized = groups.size > 1 || groups.any { !it.isPlain },
                            onAdd = { draft.add(p.id) }, onRemove = { draft.removeOne(p.id) },
                            onLongPress = { if (draft.qtyOf(p.id) == 0) draft.add(p.id); onEdit(p.id) },
                        )
                    }
                }
                if (products.isEmpty()) {
                    item(key = "empty") {
                        Text("Cocina no tiene productos activos", color = Palette.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(64.dp).background(Palette.card, Ui.smallShape).border(1.dp, Palette.line, Ui.smallShape)
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 20.sp)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductTile(p: Product, qty: Int, customized: Boolean, onAdd: () -> Unit, onRemove: () -> Unit, onLongPress: () -> Unit) {
    val selected = qty > 0
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .background(Palette.card, Ui.shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.primary else Palette.line, Ui.shape)
            .combinedClickable(onClick = onAdd, onLongClick = onLongPress),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FoodIcon(p.icon, 40.dp)
            Text(
                p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Text(money(p.priceCents), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.primary)
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).background(Palette.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("$qty", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp) }
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(28.dp)
                    .background(Palette.soft, RoundedCornerShape(8.dp)).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Text("−", color = Palette.primaryDark, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            if (customized) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).background(Palette.warn, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("✎", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
            }
        }
    }
}

/** Resumen: cada producto con sus grupos de preparación; tocar uno abre el editor. */
@Composable
private fun Summary(ctx: WaiterCtx, state: AppState, onEdit: (String) -> Unit) {
    val draft = ctx.app.draft
    val byId = state.products.associateBy { it.id }
    LazyColumn(
        contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
        verticalArrangement = Arrangement.spacedBy(Ui.gap),
    ) {
        items(draft.productIds(), key = { it }) { pid ->
            val p = byId[pid] ?: return@items
            val groups = draft.groupsOf(pid)
            Column(
                Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape)
                    .clickable { onEdit(pid) }.padding(Ui.pad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodIcon(p.icon, 40.dp)
                    Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Text("× ${draft.qtyOf(pid)}", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(money(p.priceCents * draft.qtyOf(pid)), color = Palette.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.End)
                }
                groups.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QtyBox(g.qty)
                        Text(
                            (g.mods.toList() + g.note).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "Normal" },
                            modifier = Modifier.padding(start = 10.dp),
                            color = if (g.isPlain) Palette.muted else Palette.text,
                            fontWeight = if (g.isPlain) FontWeight.Normal else FontWeight.SemiBold,
                        )
                    }
                }
                Text("✎ Modificar o dividir preparación", color = Palette.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun QtyBox(qty: Int) {
    Box(Modifier.size(32.dp).background(Palette.soft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Text("$qty", fontWeight = FontWeight.Black, color = Palette.primaryDark)
    }
}

/**
 * Editor de un producto: sus grupos de preparación, cada uno con cantidad,
 * modificaciones y nota propias. "Dividir" separa una pieza del grupo más grande.
 */
@Composable
private fun ProductEditor(ctx: WaiterCtx, state: AppState, product: Product, onDone: () -> Unit) {
    val draft = ctx.app.draft
    val groups = draft.groupsOf(product.id)
    if (groups.isEmpty()) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val modGroups = state.groupsFor(product)
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = product.name,
            subtitle = "Total ${draft.qtyOf(product.id)} · ${groups.size} ${if (groups.size == 1) "grupo" else "grupos"} de preparación",
            onBack = onDone,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
            verticalArrangement = Arrangement.spacedBy(Ui.gap),
        ) {
            itemsIndexed(groups, key = { _, g -> System.identityHashCode(g) }) { i, g ->
                Column(
                    Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (groups.size == 1) "Todos" else "Grupo ${i + 1}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Stepper(g.qty) { draft.setQty(g, it) }
                    }
                    modGroups.forEach { mg ->
                        Text(
                            if (mg.exclusive) "${mg.name} · elige una" else "${mg.name} · puedes combinar",
                            color = Palette.muted, fontSize = 13.sp,
                        )
                        Grid(mg.options, 3) { opt ->
                            OptionChip(opt, opt in g.mods) { draft.toggleMod(g, opt, mg) }
                        }
                    }
                    OutlinedTextField(
                        g.note, { draft.setNote(g, it) },
                        modifier = Modifier.fillMaxWidth(), shape = Ui.smallShape, singleLine = true,
                        placeholder = { Text("Nota de este grupo (opcional)") },
                    )
                }
            }
        }
        BottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(Ui.gap)) {
                ActionButton(
                    "✂ Dividir preparación", Palette.card, Palette.primary, Modifier.weight(1f).height(60.dp),
                    enabled = groups.any { it.qty > 1 },
                ) { draft.split(product.id) }
                ActionButton("Listo", Palette.primary, Color.White, Modifier.weight(1f).height(60.dp), onClick = onDone)
            }
        }
    }
}
