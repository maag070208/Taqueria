package com.axzy.taqueria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.ModifierGroup
import com.axzy.taqueria.OrderStore
import com.axzy.taqueria.PRODUCT_EMOJIS
import com.axzy.taqueria.Product
import com.axzy.taqueria.defaultEmoji
import com.axzy.taqueria.money
import com.axzy.taqueria.moneyInput
import com.axzy.taqueria.parseMoney

/** Elemento en edición; `null` dentro = uno nuevo. */
private data class EditingProduct(val product: Product?)
private data class EditingGroup(val group: ModifierGroup?)

@Composable
fun AdminScreen(store: OrderStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editingProduct by remember { mutableStateOf<EditingProduct?>(null) }
    var editingGroup by remember { mutableStateOf<EditingGroup?>(null) }
    val grouped = state.products.groupBy { it.category }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(title = "Menú", subtitle = "${state.products.size} productos · ${state.modifierGroups.size} tipos de modificación", onBack = onBack)
        Segmented(listOf("Productos", "Modificaciones"), tab, { tab = it }, Modifier.padding(horizontal = Ui.pad))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Ui.pad),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tab == 0) {
                grouped.forEach { (category, products) ->
                    item(key = "h-$category") { SectionTitle(category, Modifier.padding(top = 8.dp)) }
                    items(products, key = { it.id }) { p ->
                        ProductRow(
                            p,
                            onClick = { editingProduct = EditingProduct(p) },
                            onUp = { scope.runStore(context) { store.moveProduct(p.id, -1) } },
                            onDown = { scope.runStore(context) { store.moveProduct(p.id, +1) } },
                            onToggle = { on ->
                                scope.runStore(context) { store.saveProduct(p.id, p.name, p.priceCents, p.category, on, p.emoji, p.modifierGroupIds) }
                            },
                        )
                    }
                }
            } else {
                item(key = "hint") {
                    Text("Exclusiva: solo una opción (ej. Con salsa / Sin salsa). Combinable: varias a la vez.", color = Palette.muted, fontSize = 13.sp)
                }
                items(state.modifierGroups, key = { it.id }) { g ->
                    Column(
                        Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape)
                            .clickable { editingGroup = EditingGroup(g) }.padding(Ui.pad),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusChip(if (g.exclusive) "Exclusiva" else "Combinable", if (g.exclusive) Palette.primary else Palette.warn)
                        }
                        Text(g.options.joinToString(" · "), color = Palette.muted)
                        val uses = state.products.count { g.id in it.modifierGroupIds }
                        Text("En $uses ${if (uses == 1) "producto" else "productos"}", color = Palette.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Palette.card).padding(Ui.pad)) {
            ActionButton(
                if (tab == 0) "+ Agregar producto" else "+ Agregar tipo de modificación",
                Palette.primary, Color.White, Modifier.fillMaxWidth(),
            ) { if (tab == 0) editingProduct = EditingProduct(null) else editingGroup = EditingGroup(null) }
        }
    }

    editingProduct?.let { e ->
        ProductDialog(
            product = e.product,
            categories = grouped.keys.toList(),
            groups = state.modifierGroups,
            onDismiss = { editingProduct = null },
            onSave = { name, cents, category, emoji, groupIds ->
                editingProduct = null
                scope.runStore(context) { store.saveProduct(e.product?.id, name, cents, category, e.product?.active ?: true, emoji, groupIds) }
            },
            onDelete = e.product?.let { p -> { editingProduct = null; scope.runStore(context) { store.deleteProduct(p.id) } } },
        )
    }
    editingGroup?.let { e ->
        GroupDialog(
            group = e.group,
            onDismiss = { editingGroup = null },
            onSave = { name, options, exclusive ->
                editingGroup = null
                scope.runStore(context) { store.saveModifierGroup(e.group?.id, name, options, exclusive) }
            },
            onDelete = e.group?.let { g -> { editingGroup = null; scope.runStore(context) { store.deleteModifierGroup(g.id) } } },
        )
    }
}

@Composable
private fun ProductRow(p: Product, onClick: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(Palette.card, Ui.shape)
            .border(1.dp, Palette.line, Ui.shape).clickable(onClick = onClick).padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodIcon(p.icon, 44.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (p.active) Palette.text else Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (p.active) money(p.priceCents) else "${money(p.priceCents)} · agotado",
                color = if (p.active) Palette.primary else Palette.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            )
        }
        ArrowButton("↑", onUp)
        ArrowButton("↓", onDown)
        Switch(
            checked = p.active, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.primary, checkedThumbColor = Color.White),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ArrowButton(label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.muted)
    }
}

@Composable
private fun ProductDialog(
    product: Product?,
    categories: List<String>,
    groups: List<ModifierGroup>,
    onDismiss: () -> Unit,
    onSave: (name: String, cents: Long, category: String, emoji: String, groupIds: List<String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(product?.name.orEmpty()) }
    var price by remember { mutableStateOf(product?.let { moneyInput(it.priceCents) }.orEmpty()) }
    var category by remember { mutableStateOf(product?.category ?: categories.firstOrNull() ?: "Tacos") }
    var emoji by remember { mutableStateOf(product?.emoji.orEmpty()) }
    var groupIds by remember { mutableStateOf(product?.modifierGroupIds ?: groups.map { it.id }) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cents = parseMoney(price)
    val valid = name.isNotBlank() && cents != null && category.isNotBlank()
    val shownEmoji = emoji.ifEmpty { defaultEmoji(category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Nuevo producto" else "Editar producto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ui.gap)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nombre") }, singleLine = true, shape = Ui.smallShape, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    price, { price = it.take(9) },
                    label = { Text("Precio") }, prefix = { Text("$") }, singleLine = true, shape = Ui.smallShape,
                    isError = price.isNotEmpty() && cents == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(category, { category = it.take(30) }, label = { Text("Categoría") }, singleLine = true, shape = Ui.smallShape, modifier = Modifier.fillMaxWidth())
                if (categories.isNotEmpty()) Grid(categories, 3) { c -> OptionChip(c, c == category) { category = c } }
                Text("Modificaciones permitidas", fontWeight = FontWeight.Bold)
                if (groups.isEmpty()) Text("Crea tipos en la pestaña Modificaciones.", color = Palette.muted)
                Grid(groups, 2) { g ->
                    OptionChip(g.name, g.id in groupIds) { groupIds = if (g.id in groupIds) groupIds - g.id else groupIds + g.id }
                }
                Text("Ícono", fontWeight = FontWeight.Bold)
                Grid(PRODUCT_EMOJIS, 6) { e ->
                    val on = e == shownEmoji
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(if (on) Palette.soft else Palette.card, Ui.smallShape)
                            .border(if (on) 2.dp else 1.dp, if (on) Palette.primary else Palette.line, Ui.smallShape)
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 20.sp) }
                }
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Eliminar producto", color = Palette.late) }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name, cents!!, category, emoji, groupIds) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar ${product?.name}?") },
            text = { Text("Si solo se acabó, mejor márcalo como agotado con el interruptor.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Eliminar", color = Palette.late) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun GroupDialog(
    group: ModifierGroup?,
    onDismiss: () -> Unit,
    onSave: (name: String, options: List<String>, exclusive: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(group?.name.orEmpty()) }
    var options by remember { mutableStateOf(group?.options?.joinToString("\n").orEmpty()) }
    var exclusive by remember { mutableStateOf(group?.exclusive ?: true) }
    val parsed = options.lines().map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Nuevo tipo de modificación" else "Editar ${group.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ui.gap)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Nombre (ej. Salsa)") }, singleLine = true, shape = Ui.smallShape, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    options, { options = it.take(400) },
                    label = { Text("Opciones, una por renglón") }, shape = Ui.smallShape, minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Grid(listOf(true, false), 2) { ex ->
                    OptionChip(if (ex) "Exclusiva" else "Combinable", ex == exclusive) { exclusive = ex }
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Eliminar tipo", color = Palette.late) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && parsed.isNotEmpty(), onClick = { onSave(name, parsed, exclusive) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
