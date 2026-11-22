package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KitchenZoneTest {
    @Test fun `la IP de la zona es la nueva y se prefiere la puerta de enlace`() {
        val before = setOf("192.168.10.40", "10.120.3.9")
        assertEquals("192.168.49.1", KitchenZone.zoneHost(before, listOf("192.168.10.40", "10.120.3.9", "192.168.49.1")))
        assertEquals("192.168.49.1", KitchenZone.zoneHost(before, listOf("192.168.10.40", "172.16.0.7", "192.168.49.1")))
        assertEquals("10.4.0.20", KitchenZone.zoneHost(before, listOf("10.4.0.20", "192.168.10.40")))
    }

    @Test fun `si no aparece una IP nueva todavia no hay host`() {
        assertNull(KitchenZone.zoneHost(setOf("192.168.10.40"), listOf("192.168.10.40")))
    }
}
