package com.axzy.taqueria.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.axzy.taqueria.Giro
import com.axzy.taqueria.Role
import com.axzy.taqueria.Staff
import com.axzy.taqueria.app

/**
 * Giro con el que se ve esta pantalla: la cocina lo toma de su licencia; mesera y pantalla de cocina,
 * de lo que manda la cocina. Sin datos, la taquería de siempre.
 */
@Composable
fun currentGiro(): Giro {
    val app = LocalContext.current.app
    val fromKitchen by app.client.giro.collectAsState()
    return if (app.role == Role.KITCHEN) app.license.giro else fromKitchen
}

/** "Taquero" en una taquería, "Sushero" en un sushi… El admin es admin en todos. */
fun Staff.labelFor(giro: Giro): String = when (this) {
    Staff.ADMIN -> label
    Staff.TAQUERO -> giro.terms.kitchenRole
    Staff.MESERO -> giro.terms.waiterRole
}

/** Color de carne que se pinta: en un giro sin `meatColors` (sushi, pizza…) ningún producto lleva color. */
@Composable
fun com.axzy.taqueria.Product.shownColor(): com.axzy.taqueria.MeatColor =
    if (currentGiro().features.meatColors) color else com.axzy.taqueria.MeatColor.NONE
