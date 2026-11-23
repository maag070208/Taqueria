package com.axzy.taqueria

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * El menú de la cocina tal como lo guarda la Central (`catalog_versions.data`): categorías,
 * productos, preparaciones, extras gratis y estaciones. Cuentas, órdenes y mesas no van.
 *
 * Kotlin puro (sin Android) para probarlo en la JVM.
 */
@Serializable
data class Catalog(
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val modifierGroups: List<ModifierGroup> = emptyList(),
    val freeExtras: List<String> = emptyList(),
    val stations: List<Station> = emptyList(),
) {
    /**
     * Huella del menú para saber si cambió desde la última sincronización. Se calcula sobre el
     * JSON de la app (orden de campos fijo), así que solo se compara con huellas de la app.
     */
    fun fingerprint(): String =
        MessageDigest.getInstance("SHA-256").digest(AppJson.encodeToString(this).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Lo mínimo para no romper la cocina con un menú mal armado en la Central. */
    fun problem(): String? = when {
        products.any { it.id.isBlank() || it.name.isBlank() } -> "Hay productos sin id o sin nombre"
        products.map { it.id }.toSet().size != products.size -> "Hay productos repetidos"
        modifierGroups.map { it.id }.toSet().size != modifierGroups.size -> "Hay preparaciones repetidas"
        else -> null
    }
}

fun AppState.catalog() = Catalog(categories, products, modifierGroups, freeExtras, stations)

/**
 * Pone el menú de la Central sin tocar cuentas ni órdenes (cada renglón ya guarda su nombre y
 * precio). Los agotados son de la cocina: un producto que ya existía conserva su `active`.
 */
fun AppState.withCatalog(c: Catalog): AppState {
    val activeHere = products.associate { it.id to it.active }
    return copy(
        categories = c.categories,
        products = c.products.map { p -> activeHere[p.id]?.let { p.copy(active = it) } ?: p },
        modifierGroups = c.modifierGroups,
        freeExtras = c.freeExtras,
        stations = c.stations,
    )
}

/** Menú de la Central (`GET /catalog` y el `current` de un 409). */
@Serializable
data class RemoteCatalog(
    val version: Int,
    val source: String = "",
    val author: String = "",
    val updatedAt: String = "",
    val data: Catalog,
)

@Serializable
data class CatalogSaveReply(val saved: Boolean, val catalog: RemoteCatalog)

@Serializable
data class CatalogConflictReply(val code: String = "", val current: RemoteCatalog? = null)

@Serializable
data class CatalogSave(val baseVersion: Int, val data: Catalog)
