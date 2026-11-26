package com.axzy.taqueria

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

class IconStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun png(seed: Int) = Base64.getEncoder().encodeToString(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, seed.toByte()))
    private fun set(version: Int, vararg keys: String) = IconSetDownload("pizzeria", "Pizzería", version, icons = keys.map { IconDownload(it, png = png(it.length)) })

    @Test fun `guarda el set, lo busca por llave, por nombre y su generico`() = runBlocking {
        val store = IconStore(tmp.newFolder())
        store.install(set(2, "pizza_pepperoni", "pizza_generica", "ing_pepperoni", "../malo"))
        val meta = store.meta.value!!
        assertEquals(setOf("pizza_pepperoni", "pizza_generica", "ing_pepperoni"), meta.keys)
        assertNotNull(store.file("pizza_pepperoni"))
        assertNull(store.file("pizza_hawaiana"))
        // Por nombre gana el platillo, no el ingrediente.
        assertEquals(store.file("pizza_pepperoni"), store.byName("pepperoni"))
        assertEquals(store.file("pizza_generica"), store.generic())
        assertTrue(store.has(IconSetRef("pizzeria", 2)))
        assertFalse(store.has(IconSetRef("pizzeria", 3)))
    }

    @Test fun `una version nueva reemplaza todo y sobrevive a reabrir`() = runBlocking {
        val dir = tmp.newFolder()
        IconStore(dir).install(set(1, "pizza_hawaiana"))
        IconStore(dir).install(set(2, "pizza_margherita"))
        val reopened = IconStore(dir)
        assertEquals(2, reopened.meta.value!!.version)
        assertNull(reopened.file("pizza_hawaiana"))
        assertNotNull(reopened.file("pizza_margherita"))
        assertEquals("pizzeria", reopened.download()!!.id)
        // Solo queda la carpeta actual: nada de sets viejos a medias.
        assertEquals(listOf("actual"), dir.list()!!.toList())
    }

    @Test fun `quitar el set vuelve a los iconos de la app`() = runBlocking {
        val store = IconStore(tmp.newFolder())
        store.install(set(1, "rollo_dragon"))
        store.syncFromCentral(null, null)
        assertNull(store.meta.value)
        assertNull(store.file("rollo_dragon"))
    }
}
