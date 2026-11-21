package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LicenseTest {
    private val day = 24 * 60 * 60 * 1000L
    private val t0 = Instant.parse("2026-11-21T12:00:00Z").toEpochMilli()

    private fun response(status: String = "ACTIVE", validDays: Long = 15, serverMs: Long = t0) = LicenseResponse(
        status = status,
        tenant = NameRef("Tacos El Güero"),
        validUntil = Instant.ofEpochMilli(serverMs + validDays * day).toString(),
        serverTime = Instant.ofEpochMilli(serverMs).toString(),
    )

    @Test fun sinCodigoNoEstaActivada() {
        assertEquals(LicenseState.NotActivated, evaluateLicense(null, t0))
    }

    @Test fun activaFuncionaSinInternetHastaSuFecha() {
        val cache = LicenseCache("tma_x").withResponse(response(), t0)
        assertTrue(evaluateLicense(cache, t0 + 10 * day) is LicenseState.Active)
        assertEquals(LockReason.EXPIRED, (evaluateLicense(cache, t0 + 16 * day) as LicenseState.Locked).reason)
    }

    @Test fun avisaCuandoEstaPorVencer() {
        val cache = LicenseCache("tma_x").withResponse(response(), t0)
        assertEquals(false, (evaluateLicense(cache, t0 + day) as LicenseState.Active).expiresSoon)
        assertEquals(true, (evaluateLicense(cache, t0 + 13 * day) as LicenseState.Active).expiresSoon)
    }

    @Test fun suspendidaSigueBloqueadaSinInternet() {
        val cache = LicenseCache("tma_x").withResponse(response(status = "SUSPENDED", validDays = 0), t0)
        assertEquals(LockReason.SUSPENDED, (evaluateLicense(cache, t0) as LicenseState.Locked).reason)
    }

    @Test fun revocadaBloquea() {
        val cache = LicenseCache("tma_x").withResponse(response(), t0).copy(revoked = true)
        assertEquals(LockReason.REVOKED, (evaluateLicense(cache, t0) as LicenseState.Locked).reason)
    }

    @Test fun atrasarElRelojNoAlargaLaLicencia() {
        val cache = LicenseCache("tma_x").withResponse(response(), t0).copy(maxSeenMs = t0 + 20 * day)
        assertEquals(LockReason.EXPIRED, (evaluateLicense(cache, t0) as LicenseState.Locked).reason)
    }

    @Test fun usaLaHoraDelServidorSiElTelefonoVaAtrasado() {
        // El teléfono marca 30 días antes que el servidor.
        val phone = t0 - 30 * day
        val cache = LicenseCache("tma_x").withResponse(response(), phone)
        assertTrue(evaluateLicense(cache, phone + 10 * day) is LicenseState.Active)
        assertEquals(LockReason.EXPIRED, (evaluateLicense(cache, phone + 16 * day) as LicenseState.Locked).reason)
    }
}
