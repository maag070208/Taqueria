package com.axzy.taqueria

import kotlinx.serialization.Serializable

/**
 * Giro del negocio (taquería, sushi, pizzería…), tal como lo manda la Central en la
 * licencia. Sin datos del giro, la app es la taquería de siempre: así la 1.2.x y una
 * Central vieja se ven igual que antes.
 *
 * Kotlin puro (sin Android) para probarlo en la JVM.
 */
@Serializable
data class Giro(
    val key: String = TAQUERIA,
    val name: String = "Taquería",
    val terms: GiroTerms = GiroTerms(),
    val features: GiroFeatures = GiroFeatures(),
    /** Set de iconos en la Central; null = los iconos de taquería que trae la app. */
    val iconSet: IconSetRef? = null,
) {
    companion object {
        const val TAQUERIA = "TAQUERIA"
    }
}

/** Cómo se llama cada cosa en el giro. */
@Serializable
data class GiroTerms(
    /** Quien prepara: Taquero, Sushero, Pizzero. */
    val kitchenRole: String = "Taquero",
    val waiterRole: String = "Mesero",
    /** La pieza: Taco, Rollo, Pizza. */
    val item: String = "Taco",
    /** Dónde se prepara: Cocina, Barra. */
    val kitchen: String = "Cocina",
)

/** Partes del modelo que usa el giro. La taquería las usa todas. */
@Serializable
data class GiroFeatures(
    /** Productos de varias carnes (campechano). */
    val mixes: Boolean = true,
    /** Barra de color por carne. */
    val meatColors: Boolean = true,
    /** Extras sin costo por persona (cebollitas, chiles…). */
    val freeExtras: Boolean = true,
)

@Serializable
data class IconSetRef(val id: String, val version: Int)

/** Versión vigente del menú del cliente en la Central. */
@Serializable
data class CatalogRef(val version: Int)

/** `product` de la licencia. `terms` y `features` llegan desde la Central multi-negocio. */
@Serializable
data class ProductRef(
    val key: String = Giro.TAQUERIA,
    val name: String = "Taquería",
    val terms: Map<String, String>? = null,
    val features: Map<String, Boolean>? = null,
)

/**
 * Arma el giro con lo que trae la licencia.
 * - Cada palabra que falte o venga vacía se queda como en la taquería.
 * - Sin `features` (Central vieja) se usan todas; con `features`, lo que no venga es `false`.
 */
fun giroOf(product: ProductRef?, iconSet: IconSetRef?): Giro {
    if (product == null) return Giro(iconSet = iconSet)
    val base = GiroTerms()
    fun term(key: String, fallback: String) = product.terms?.get(key)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
    val terms = GiroTerms(
        kitchenRole = term("kitchenRole", base.kitchenRole),
        waiterRole = term("waiterRole", base.waiterRole),
        item = term("item", base.item),
        kitchen = term("kitchen", base.kitchen),
    )
    val features = product.features?.let {
        GiroFeatures(mixes = it["mixes"] == true, meatColors = it["meatColors"] == true, freeExtras = it["freeExtras"] == true)
    } ?: GiroFeatures()
    return Giro(product.key, product.name, terms, features, iconSet)
}
