package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminPinTest {
    private val prefs = HashMap<String, String>()
    private var clock = 1_000_000L
    private val pin = AdminPin(
        get = { prefs[it] },
        put = { values -> values.forEach { (k, v) -> if (v == null) prefs.remove(k) else prefs[k] = v } },
        now = { clock },
    )

    @Test fun storesOnlyAHash() {
        assertFalse(pin.isSet)
        pin.set("4821")
        assertTrue(pin.isSet)
        assertFalse(prefs.values.any { "4821" in it })
        assertEquals(AdminPin.Result.Ok, pin.check("4821"))
    }

    @Test fun wrongPinCountsDownThenLocks() {
        pin.set("4821")
        assertEquals(AdminPin.Result.Wrong(4), pin.check("0000"))
        repeat(3) { pin.check("0000") }
        assertEquals(AdminPin.Result.Locked(30), pin.check("0000"))
        // Bloqueado: ni el PIN correcto entra hasta que pase el tiempo.
        assertTrue(pin.check("4821") is AdminPin.Result.Locked)
        clock += AdminPin.LOCK_MS
        assertEquals(AdminPin.Result.Ok, pin.check("4821"))
    }

    @Test fun successResetsFailures() {
        pin.set("4821")
        repeat(4) { pin.check("1111") }
        assertEquals(AdminPin.Result.Ok, pin.check("4821"))
        assertEquals(AdminPin.Result.Wrong(4), pin.check("1111"))
    }

    @Test fun rejectsMalformedPins() {
        assertFalse(AdminPin.isValid("123"))
        assertFalse(AdminPin.isValid("12a4"))
        assertTrue(AdminPin.isValid("0007"))
    }
}
