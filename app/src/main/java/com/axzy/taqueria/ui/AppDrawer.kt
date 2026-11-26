package com.axzy.taqueria.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.AdminPin
import com.axzy.taqueria.Staff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DrawerItem(@DrawableRes val icon: Int, val label: String, val selected: Boolean = false, val onClick: () -> Unit)

/** Abre el drawer de la pantalla actual (lo usa el botón ☰ de [TopBar]). */
class DrawerControl(val state: DrawerState, private val scope: CoroutineScope) {
    fun open() { scope.launch { state.open() } }
}

@Composable
fun rememberDrawerControl(): DrawerControl {
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    return remember(state) { DrawerControl(state, scope) }
}

/**
 * Menú lateral con el rol actual arriba y grupos de opciones separados por una línea.
 * Solo se abre con ☰: deslizar desde el borde chocaría con los gestos de las listas.
 */
@Composable
fun AppDrawer(control: DrawerControl, staff: Staff, subtitle: String, groups: List<List<DrawerItem>>, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = control.state
    BackHandler(enabled = state.isOpen) { scope.launch { state.close() } }
    ModalNavigationDrawer(
        drawerState = state,
        gesturesEnabled = state.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Palette.bg) {
                Column(Modifier.fillMaxHeight().safeDrawingPadding().padding(vertical = Ui.pad)) {
                    Row(Modifier.padding(horizontal = Ui.pad), verticalAlignment = Alignment.CenterVertically) {
                        Illustration(staff.art, 52.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(staff.labelFor(currentGiro()), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(subtitle, color = Palette.muted, fontSize = 13.sp)
                        }
                    }
                    groups.filter { it.isNotEmpty() }.forEach { items ->
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Palette.line)
                        items.forEach { item ->
                            NavigationDrawerItem(
                                icon = { UiIcon(item.icon, tint = if (item.selected) Palette.primaryDark else Palette.text) },
                                label = { Text(item.label, fontSize = 16.sp, fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal) },
                                selected = item.selected,
                                onClick = { scope.launch { state.close() }; item.onClick() },
                                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Palette.soft, unselectedTextColor = Palette.text, selectedTextColor = Palette.primaryDark),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    VersionText(Modifier.padding(horizontal = Ui.pad + 12.dp))
                }
            }
        },
        content = content,
    )
}

/** Qué pide el diálogo de PIN. */
enum class PinMode { ENTER, CREATE, CHANGE }

/**
 * Pide el PIN del admin. Al crear o cambiar se teclea dos veces.
 * [onEnter] responde si el PIN fue correcto; [onSet] guarda uno nuevo.
 */
@Composable
fun AdminPinDialog(mode: PinMode, onEnter: (String) -> AdminPin.Result, onSet: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val twice = mode != PinMode.ENTER
    val ready = AdminPin.isValid(pin) && (!twice || AdminPin.isValid(repeat))

    fun submit() {
        if (!ready) return
        if (twice) {
            if (pin != repeat) { error = "Los PIN no coinciden"; repeat = ""; return }
            onSet(pin)
            return
        }
        when (val r = onEnter(pin)) {
            AdminPin.Result.Ok -> Unit
            is AdminPin.Result.Wrong -> { error = "PIN incorrecto · ${r.triesLeft} ${if (r.triesLeft == 1) "intento" else "intentos"} más"; pin = "" }
            is AdminPin.Result.Locked -> { error = "Demasiados intentos. Espera ${r.secondsLeft} s"; pin = "" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    PinMode.ENTER -> "Entrar como admin"
                    PinMode.CREATE -> "Crea el PIN de admin"
                    PinMode.CHANGE -> "Cambiar PIN de admin"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (mode) {
                        PinMode.ENTER -> "Teclea el PIN de 4 dígitos para ver ventas y editar el menú."
                        PinMode.CREATE -> "Con este PIN se entra a ventas y a editar el menú. No lo compartas con el taquero."
                        PinMode.CHANGE -> "Teclea el PIN nuevo dos veces."
                    },
                    color = Palette.muted,
                )
                PinField(pin, if (twice) "PIN nuevo" else "PIN") { pin = it; error = null }
                if (twice) PinField(repeat, "Repite el PIN") { repeat = it; error = null }
                error?.let { Text(it, color = Palette.late, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = { TextButton(enabled = ready, onClick = ::submit) { Text(if (twice) "Guardar" else "Entrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() }.take(4)) },
        label = { Text(label) }, singleLine = true, shape = Ui.smallShape,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().background(Palette.card),
    )
}
