package com.axzy.taqueria.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.R
import com.axzy.taqueria.AppState
import com.axzy.taqueria.MeatColor
import com.axzy.taqueria.ModifierGroup
import com.axzy.taqueria.OrderStore
import com.axzy.taqueria.Product
import com.axzy.taqueria.Variant
import com.axzy.taqueria.money
import com.axzy.taqueria.moneyInput
import com.axzy.taqueria.parseMoney

/** Elemento en edición; `null` dentro = uno nuevo. */
private data class EditingProduct(val product: Product?)
private data class EditingGroup(val group: ModifierGroup?)

@Composable
/** Con [canEdit] es el menú completo del admin; sin él, el taquero solo marca qué se agotó. */
fun AdminScreen(store: OrderStore, canEdit: Boolean, onMenu: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    if (!canEdit) tab = 0
    var editingProduct by remember { mutableStateOf<EditingProduct?>(null) }
    var editingGroup by remember { mutableStateOf<EditingGroup?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val categories = state.menuCategories().map { it.name }
    val grouped = categories.associateWith { c -> state.products.filter { it.category == c } }.filterValues { it.isNotEmpty() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (canEdit) {
            TopBar(title = "Menú", subtitle = "${state.products.size} productos · ${state.tableCount} mesas", onMenu = onMenu) {
                SquareButton(R.drawable.ic_reset, onClick = { confirmReset = true }, description = "Menú predeterminado")
            }
            Segmented(listOf("Productos", "Preparación", "Ajustes"), tab, { tab = it }, Modifier.padding(horizontal = Ui.pad))
        } else {
            val off = state.products.count { !it.active }
            TopBar(title = "Disponibilidad", subtitle = if (off == 0) "Todo disponible" else if (off == 1) "1 agotado" else "$off agotados", onMenu = onMenu)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Ui.pad),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                0 -> grouped.forEach { (category, products) ->
                    item(key = "h-$category") { SectionTitle(category, Modifier.padding(top = 8.dp)) }
                    items(products, key = { it.id }) { p ->
                        ProductRow(
                            p, state.categoryOf(p),
                            onClick = if (canEdit) ({ editingProduct = EditingProduct(p) }) else null,
                            onUp = if (canEdit) ({ scope.runStore(context) { store.moveProduct(p.id, -1) } }) else null,
                            onDown = if (canEdit) ({ scope.runStore(context) { store.moveProduct(p.id, +1) } }) else null,
                            onToggle = { on -> scope.runStore(context) { store.saveProduct(p.copy(active = on)) } },
                        )
                    }
                }
                1 -> {
                    item(key = "hint") {
                        Text(
                            "Exclusiva: solo una opción. Combinable: varias a la vez. En una exclusiva puedes definir " +
                                "con qué arranca cada renglón y qué marca cada opción (ej. Puro verde → Sin salsa, Sin cebolla).",
                            color = Palette.muted, fontSize = 13.sp,
                        )
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
                            if (g.defaultOption.isNotEmpty()) Text("Arranca en: ${g.defaultOption}", fontSize = 13.sp)
                            g.implies.forEach { (o, marks) -> Text("$o → ${marks.joinToString(", ")}", fontSize = 13.sp, color = Palette.primaryDark) }
                            val uses = state.products.count { g.id in it.modifierGroupIds }
                            Text("En $uses ${if (uses == 1) "producto" else "productos"}", color = Palette.muted, fontSize = 13.sp)
                        }
                    }
                }
                else -> item(key = "settings") {
                    SettingsPanel(
                        state,
                        onTables = { n -> scope.runStore(context) { store.setTableCount(n) } },
                        onExtras = { list -> scope.runStore(context) { store.saveFreeExtras(list) } },
                    )
                }
            }
        }
        if (canEdit && tab != 2) {
            Box(Modifier.fillMaxWidth().background(Palette.card).padding(Ui.pad)) {
                ActionButton(
                    if (tab == 0) "+ Agregar producto" else "+ Agregar tipo de preparación",
                    Palette.primary, Color.White, Modifier.fillMaxWidth(),
                ) { if (tab == 0) editingProduct = EditingProduct(null) else editingGroup = EditingGroup(null) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("¿Usar el menú predeterminado?") },
            text = { Text("Se reemplazan productos, precios, preparación, categorías y extras. Las órdenes, cuentas, ventas y el número de mesas se conservan.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; scope.runStore(context) { store.resetCatalog() } }) { Text("Reemplazar") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
        )
    }

    editingProduct?.let { e ->
        ProductDialog(
            product = e.product,
            categories = categories,
            groups = state.modifierGroups,
            onDismiss = { editingProduct = null },
            onSave = { p ->
                editingProduct = null
                scope.runStore(context) { store.saveProduct(p) }
            },
            onDelete = e.product?.let { p -> { editingProduct = null; scope.runStore(context) { store.deleteProduct(p.id) } } },
        )
    }
    editingGroup?.let { e ->
        GroupDialog(
            group = e.group,
            others = state.modifierGroups.filter { it.id != e.group?.id },
            onDismiss = { editingGroup = null },
            onSave = { name, options, exclusive, default, implies ->
                editingGroup = null
                scope.runStore(context) { store.saveModifierGroup(e.group?.id, name, options, exclusive, default, implies) }
            },
            onDelete = e.group?.let { g -> { editingGroup = null; scope.runStore(context) { store.deleteModifierGroup(g.id) } } },
        )
    }
}

private fun priceText(p: Product) =
    if (p.variants.isEmpty()) money(p.priceCents) else p.variants.joinToString(" · ") { "${it.name} ${money(it.priceCents)}" }

@Composable
private fun ProductRow(
    p: Product, category: com.axzy.taqueria.Category,
    onClick: (() -> Unit)?, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onToggle: (Boolean) -> Unit,
) {
    val (bg, _, bar) = p.color.colors()
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(Palette.card, Ui.shape)
            .border(1.dp, Palette.line, Ui.shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(if (p.color == MeatColor.NONE) Color.Transparent else bar, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)))
        Box(Modifier.padding(start = 6.dp).size(44.dp).background(if (p.color == MeatColor.NONE) Palette.soft else bg, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            DishIcon(DishIcons.productKeys(p, category), 36.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                p.name + if (p.mix) " · mixto" else "", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = if (p.active) Palette.text else Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (p.active) priceText(p) else "${priceText(p)} · agotado",
                color = if (p.active) Palette.primary else Palette.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        onUp?.let { ArrowButton(R.drawable.ic_arrow_up, it) }
        onDown?.let { ArrowButton(R.drawable.ic_arrow_down, it) }
        Switch(
            checked = p.active, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.primary, checkedThumbColor = Color.White),
            modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = "Disponible ${category.name} ${p.name}" },
        )
    }
}

@Composable
private fun ArrowButton(@DrawableRes icon: Int, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        UiIcon(icon, 20.dp, Palette.muted)
    }
}

/** Renglón editable de presentación: nombre y precio como texto mientras se escribe. */
private data class VariantInput(val name: String, val price: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductDialog(
    product: Product?,
    categories: List<String>,
    groups: List<ModifierGroup>,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(product?.name.orEmpty()) }
    var price by remember { mutableStateOf(product?.let { moneyInput(it.priceCents) }.orEmpty()) }
    var category by remember { mutableStateOf(product?.category ?: categories.firstOrNull() ?: "Tacos") }
    var groupIds by remember { mutableStateOf(product?.modifierGroupIds ?: groups.map { it.id }) }
    var color by remember { mutableStateOf(product?.color ?: MeatColor.NONE) }
    var mix by remember { mutableStateOf(product?.mix ?: false) }
    var variants by remember { mutableStateOf(product?.variants.orEmpty().map { VariantInput(it.name, moneyInput(it.priceCents)) }) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cents = parseMoney(price)
    val parsedVariants = variants.map { v -> parseMoney(v.price)?.let { Variant(v.name.trim(), it) } }
    val variantsOk = parsedVariants.all { it != null && it.name.isNotEmpty() } &&
        parsedVariants.mapNotNull { it?.name?.lowercase() }.distinct().size == variants.size
    val valid = name.isNotBlank() && category.isNotBlank() && (if (variants.isEmpty()) cents != null else variantsOk)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Nuevo producto" else "Editar producto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ui.gap)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nombre (ej. Asada)") }, singleLine = true, shape = Ui.smallShape, modifier = Modifier.fillMaxWidth())
                Text("Categoría", fontWeight = FontWeight.Bold)
                OutlinedTextField(category, { category = it.take(30) }, singleLine = true, shape = Ui.smallShape, modifier = Modifier.fillMaxWidth())
                if (categories.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { c -> Pill(c, c == category) { category = c } }
                    }
                }

                Text("Precio", fontWeight = FontWeight.Bold)
                if (variants.isEmpty()) {
                    MoneyField(price, { price = it }, "Precio", Modifier.fillMaxWidth())
                } else {
                    Text("Cada presentación con su precio. La primera es la que se pide por defecto.", color = Palette.muted, fontSize = 13.sp)
                    variants.forEachIndexed { i, v ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                v.name, { n -> variants = variants.toMutableList().also { it[i] = v.copy(name = n.take(20)) } },
                                label = { Text("Ej. Maíz") }, singleLine = true, shape = Ui.smallShape, modifier = Modifier.weight(1.2f),
                            )
                            MoneyField(v.price, { pr -> variants = variants.toMutableList().also { it[i] = v.copy(price = pr) } }, "Precio", Modifier.weight(1f))
                            Box(Modifier.size(40.dp).clickable { variants = variants.toMutableList().also { it.removeAt(i) } }, contentAlignment = Alignment.Center) {
                                UiIcon(R.drawable.ic_close, 18.dp, Palette.late)
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    variants = if (variants.isEmpty()) listOf(VariantInput("Maíz", price), VariantInput("Harina", "")) else variants + VariantInput("", "")
                }) { Text(if (variants.isEmpty()) "+ Precio por presentación (maíz / harina…)" else "+ Otra presentación") }

                Text("Color de la tarjeta", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MeatColor.entries.forEach { c -> Pill(c.label, c == color) { color = c } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Es mixto", fontWeight = FontWeight.Bold)
                        Text("Al pedirlo se eligen las carnes (de las de su categoría).", color = Palette.muted, fontSize = 13.sp)
                    }
                    Switch(mix, { mix = it }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.primary, checkedThumbColor = Color.White))
                }

                Text("Preparación permitida", fontWeight = FontWeight.Bold)
                if (groups.isEmpty()) Text("Crea tipos en la pestaña Preparación.", color = Palette.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { g -> Pill(g.name, g.id in groupIds) { groupIds = if (g.id in groupIds) groupIds - g.id else groupIds + g.id } }
                }
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Eliminar producto", color = Palette.late) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val v = parsedVariants.filterNotNull()
                onSave(
                    (product ?: Product("", "", 0, "")).copy(
                        name = name, category = category, emoji = product?.emoji.orEmpty(), modifierGroupIds = groupIds, color = color, mix = mix,
                        variants = v, priceCents = if (v.isEmpty()) cents ?: 0 else v.first().priceCents,
                    ),
                )
            }) { Text("Guardar") }
        },
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
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value, { onChange(it.take(9)) },
        label = { Text(label) }, prefix = { Text("$") }, singleLine = true, shape = Ui.smallShape,
        isError = value.isNotEmpty() && parseMoney(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupDialog(
    group: ModifierGroup?,
    others: List<ModifierGroup>,
    onDismiss: () -> Unit,
    onSave: (name: String, options: List<String>, exclusive: Boolean, default: String, implies: Map<String, List<String>>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(group?.name.orEmpty()) }
    var options by remember { mutableStateOf(group?.options?.joinToString("\n").orEmpty()) }
    var exclusive by remember { mutableStateOf(group?.exclusive ?: true) }
    var default by remember { mutableStateOf(group?.defaultOption.orEmpty()) }
    var implies by remember { mutableStateOf(group?.implies.orEmpty()) }
    val parsed = options.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val otherOptions = others.flatMap { it.options }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Nuevo tipo de preparación" else "Editar ${group.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ui.gap)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Nombre (ej. Va)") }, singleLine = true, shape = Ui.smallShape, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    options, { options = it.take(400) },
                    label = { Text("Opciones, una por renglón") }, shape = Ui.smallShape, minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Grid(listOf(true, false), 2) { ex ->
                    OptionChip(if (ex) "Exclusiva" else "Combinable", ex == exclusive) { exclusive = ex }
                }
                if (exclusive && parsed.isNotEmpty()) {
                    Text("Con qué arranca cada renglón", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Ninguna", default.isEmpty()) { default = "" }
                        parsed.forEach { o -> Pill(o, o == default) { default = o } }
                    }
                    if (otherOptions.isNotEmpty()) {
                        Text("Qué marca cada opción", fontWeight = FontWeight.Bold)
                        Text("Al elegirla en la orden se activan estas; luego la mesera las puede mover.", color = Palette.muted, fontSize = 13.sp)
                        parsed.forEach { o ->
                            val marks = implies[o].orEmpty()
                            Text(o, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                otherOptions.forEach { m ->
                                    Pill(m, m in marks) {
                                        implies = implies + (o to if (m in marks) marks - m else marks + m)
                                    }
                                }
                            }
                        }
                    }
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Eliminar tipo", color = Palette.late) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && parsed.isNotEmpty(), onClick = { onSave(name, parsed, exclusive, default, implies) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Número de mesas que ve la mesera y extras gratis por persona. */
@Composable
private fun SettingsPanel(state: AppState, onTables: (Int) -> Unit, onExtras: (List<String>) -> Unit) {
    var tables by remember(state.tableCount) { mutableIntStateOf(state.tableCount) }
    var extras by remember(state.freeExtras) { mutableStateOf(state.freeExtras.joinToString("\n")) }
    val parsedExtras = extras.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(Ui.gap)) {
        Column(
            Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Mesas", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("La mesera ve de la 1 a la $tables, más \"Para llevar\" aparte.", color = Palette.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(tables) { tables = it.coerceIn(1, 100) }
                Box(Modifier.weight(1f))
                ActionButton("Guardar", Palette.primary, Color.White, Modifier.width(120.dp), enabled = tables != state.tableCount) { onTables(tables) }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Extras gratis", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Se piden por persona y no se cobran. Uno por renglón.", color = Palette.muted, fontSize = 13.sp)
            OutlinedTextField(extras, { extras = it.take(400) }, shape = Ui.smallShape, minLines = 3, modifier = Modifier.fillMaxWidth())
            ActionButton(
                "Guardar extras", Palette.primary, Color.White, Modifier.fillMaxWidth(),
                enabled = parsedExtras != state.freeExtras,
            ) { onExtras(parsedExtras) }
        }
    }
}
