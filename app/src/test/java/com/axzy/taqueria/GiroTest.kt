package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GiroTest {
    @Test
    fun sinProductoEsLaTaqueriaDeSiempre() {
        val g = giroOf(null, null)
        assertEquals("Taquero", g.terms.kitchenRole)
        assertTrue(g.features.mixes && g.features.meatColors && g.features.freeExtras)
    }

    @Test
    fun centralViejaSinTermsNiFeaturesSigueSiendoTaqueria() {
        val g = giroOf(ProductRef("TAQUERIA", "Taquería"), null)
        assertEquals(GiroTerms(), g.terms)
        assertEquals(GiroFeatures(), g.features)
    }

    @Test
    fun sushiTraeSusPalabrasYSoloSusPartes() {
        val g = giroOf(
            ProductRef("SUSHI", "Sushi", terms = mapOf("kitchenRole" to "Sushero", "item" to "Rollo", "kitchen" to " "), features = mapOf("freeExtras" to true)),
            IconSetRef("set-1", 2),
        )
        assertEquals("Sushero", g.terms.kitchenRole)
        assertEquals("Mesero", g.terms.waiterRole)
        assertEquals("Rollo", g.terms.item)
        assertEquals("Cocina", g.terms.kitchen)
        assertFalse(g.features.mixes)
        assertFalse(g.features.meatColors)
        assertTrue(g.features.freeExtras)
        assertEquals(IconSetRef("set-1", 2), g.iconSet)
    }

    @Test
    fun licenciaGuardadaPorLa122SeLeeSinGiro() {
        // El JSON que guardó la 1.2.x no trae giro: se lee como taquería.
        val old = """{"token":"axc_x","status":"ACTIVE","tenantName":"Tacos","validUntilMs":1}"""
        val cache = AppJson.decodeFromString<LicenseCache>(old)
        assertEquals(Giro(), cache.giro)
    }

    @Test
    fun respuestaMultiNegocioLlenaElGiro() {
        val body = """{"status":"ACTIVE","tenant":{"name":"Sushi Makis"},"validUntil":"2026-12-01T00:00:00Z","serverTime":"2026-11-22T00:00:00Z",
            "product":{"key":"SUSHI","name":"Sushi","terms":{"kitchenRole":"Sushero"},"features":{"mixes":false}},
            "iconSet":{"id":"s1","version":3},"catalog":{"version":7}}"""
        val cache = LicenseCache("axc_x").withResponse(AppJson.decodeFromString(body), 0)
        assertEquals("SUSHI", cache.giro.key)
        assertEquals("Sushero", cache.giro.terms.kitchenRole)
        assertEquals(IconSetRef("s1", 3), cache.giro.iconSet)
        assertEquals(7, cache.catalogVersion)
    }
}
