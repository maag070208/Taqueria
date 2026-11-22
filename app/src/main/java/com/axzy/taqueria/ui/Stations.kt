package com.axzy.taqueria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.AppState
import com.axzy.taqueria.R
import com.axzy.taqueria.Station

/** Qué muestra esta pantalla: "Ver: todas las órdenes" o "Ver: Asada". */
fun stationViewItem(state: AppState, stationId: String?, onClick: () -> Unit) =
    DrawerItem(R.drawable.ic_toggle, "Ver: ${state.stations.firstOrNull { it.id == stationId }?.name ?: "todas las órdenes"}", onClick = onClick)

/** Cada pantalla de cocina elige si ve todo (espejo) o solo una estación. */
@Composable
fun StationPickerDialog(state: AppState, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué muestra esta pantalla?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Option("Todas las órdenes", "Cada pantalla en \"todas\" ve el ticket completo (en paralelo).", current == null || state.stations.none { it.id == current }) { onPick(null) }
                state.stations.forEach { st ->
                    Option(st.name, st.summary(), current == st.id) { onPick(st.id) }
                }
                if (state.stations.isEmpty()) {
                    Text(
                        "Para repartir la orden entre taqueros (asada, adobada…), el admin crea estaciones en Menú → Ajustes.",
                        color = Palette.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun Option(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Palette.muted, fontSize = 13.sp)
        }
    }
}

private fun Station.summary() = (categories + products).joinToString(", ").ifEmpty { "Sin productos" }

/** Admin → Ajustes: nombre de la cocina y estaciones (quién prepara qué). */
@Composable
fun StationsPanel(state: AppState, onName: (String) -> Unit, onSave: (Station) -> Unit, onDelete: (String) -> Unit) {
    var name by remember(state.kitchenName) { mutableStateOf(state.kitchenName) }
    var editing by remember { mutableStateOf<Station?>(null) }
    Column(
        Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Nombre de esta cocina", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Lo ven las meseras y las pantallas. Útil si hay varias cocinas independientes en el local.", color = Palette.muted, fontSize = 13.sp)
        OutlinedTextField(name, { name = it.take(30) }, singleLine = true, shape = Ui.smallShape, placeholder = { Text("Cocina") }, modifier = Modifier.fillMaxWidth())
        ActionButton("Guardar nombre", Palette.primary, Color.White, Modifier.fillMaxWidth(), enabled = name.trim() != state.kitchenName) { onName(name) }
    }
    Column(
        Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Estaciones de cocina", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "Reparte cada orden entre taqueros: cada pantalla elige su estación y solo ve lo suyo, al mismo tiempo que las demás. " +
                "Una pantalla en \"todas\" ve el ticket completo. Lo que no es de ninguna estación solo sale en \"todas\".",
            color = Palette.muted, fontSize = 13.sp,
        )
        state.stations.forEach { st ->
            Row(
                Modifier.fillMaxWidth().background(Palette.bg, Ui.smallShape).clickable { editing = st }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(st.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(st.summary(), color = Palette.muted, fontSize = 13.sp, maxLines = 2)
                }
                Text("Editar", color = Palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        ActionButton("+ Nueva estación", Palette.card, Palette.primary, Modifier.fillMaxWidth()) { editing = Station("", "") }
    }
    editing?.let { st ->
        StationDialog(state, st, onSave = { onSave(it); editing = null }, onDelete = { onDelete(st.id); editing = null }, onDismiss = { editing = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationDialog(state: AppState, station: Station, onSave: (Station) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(station.name) }
    var categories by remember { mutableStateOf(station.categories.toSet()) }
    var products by remember { mutableStateOf(station.products.toSet()) }
    val allCategories = state.menuCategories().map { it.name }
    // Por nombre: "Asada" agrupa el taco, el quesataco y el burrito de asada.
    val allNames = state.products.filter { !it.mix }.map { it.name }.distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (station.id.isBlank()) "Nueva estación" else "Editar ${station.name}") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, singleLine = true, shape = Ui.smallShape, placeholder = { Text("Ej. Asada, Plancha, Bebidas") }, modifier = Modifier.fillMaxWidth())
                Text("Categorías completas", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allCategories.forEach { c -> PickChip(c, c in categories) { categories = categories.toggle(c) } }
                }
                Text("Productos (por nombre, en todas sus categorías)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Un mixto sale en cada estación de las carnes que lleva.", color = Palette.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allNames.forEach { n -> PickChip(n, n in products) { products = products.toggle(n) } }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (categories.isNotEmpty() || products.isNotEmpty()),
                onClick = { onSave(station.copy(name = name, categories = categories.toList(), products = products.toList())) },
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (station.id.isNotBlank()) TextButton(onClick = onDelete) { Text("Borrar", color = Palette.late) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

private fun Set<String>.toggle(v: String) = if (v in this) this - v else this + v

@Composable
private fun PickChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else Palette.text,
        fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(if (selected) Palette.primary else Palette.bg, RoundedCornerShape(50))
            .border(1.dp, if (selected) Palette.primary else Palette.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
