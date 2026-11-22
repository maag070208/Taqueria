package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SalesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val utc: ZoneId = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 21)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun line(id: String, name: String, price: Long, qty: Int) = OrderLine(id, name, price, qty)
    private fun order(account: String, createdAt: Long, vararg lines: OrderLine, status: OrderStatus = OrderStatus.DELIVERED) =
        Order("o-$account-$createdAt", 1, account, "4", 1, lines.toList(), status, createdAt, clientKey = "k-$account-$createdAt")
    private fun pay(id: String, cents: Long, at: Long, method: PayMethod = PayMethod.CASH, voided: Boolean = false, received: Long? = null) =
        Payment(id, cents, method, at, "c-$id", voided, received)
    private fun entry(id: String, table: String, payments: List<Payment>, vararg orders: Order) =
        HistoryEntry(Account(id, table, 1, orders.minOf { it.createdAt }, payments), orders.toList())

    private val asada = line("asada", "Asada", 3500, 4)
    private val refresco = line("refresco", "Refresco", 3000, 1)

    @Test fun `el dia suma lo vendido y lo cobrado sin anulados ni cancelados`() {
        val entries = listOf(
            entry("a", "4", listOf(pay("p1", 17000, at(day, 14)), pay("p2", 5000, at(day, 14), voided = true)),
                order("a", at(day, 13), asada, refresco),
                order("a", at(day, 13) + 1, line("torta", "Torta", 8000, 1), status = OrderStatus.CANCELLED)),
            entry("b", "Llevar", listOf(pay("p3", 9000, at(day, 15), PayMethod.TRANSFER), pay("p4", -1000, at(day, 16))),
                order("b", at(day, 15), line("quesadilla", "Quesadilla Asada", 9000, 1))),
        )
        val r = salesReport(entries, day, day, utc)
        assertEquals(14000 + 3000 + 9000L, r.soldCents)
        assertEquals(17000 + 9000 - 1000L, r.collectedCents)
        assertEquals(16000L, r.collectedBy(PayMethod.CASH))
        assertEquals(9000L, r.collectedBy(PayMethod.TRANSFER))
        assertEquals(1000L, r.refundsCents)
        assertEquals(2, r.accounts)
        assertEquals(13000L, r.averageTicketCents)
        assertEquals(listOf("Asada", "Quesadilla Asada", "Refresco"), r.products.map { it.name })
        assertEquals(4, r.products.first().qty)
        // El anulado se ve en el historial de cobros, aunque no sume.
        assertEquals(listOf("p4", "p3", "p1", "p2"), r.payments.map { it.payment.id })
    }

    @Test fun `solo cuenta lo del rango y desglosa por dia`() {
        val yesterday = day.minusDays(1)
        val entries = listOf(
            entry("a", "1", listOf(pay("p1", 14000, at(yesterday, 20))), order("a", at(yesterday, 19), asada)),
            entry("b", "2", listOf(pay("p2", 3000, at(day, 9))), order("b", at(day, 9), refresco)),
        )
        val today = salesReport(entries, day, day, utc)
        assertEquals(3000L, today.soldCents)
        assertEquals(listOf("p2"), today.payments.map { it.payment.id })

        val week = SalesPeriod.WEEK.range(day)
        val r = salesReport(entries, week.start, day, utc)
        assertEquals(17000L, r.collectedCents)
        assertEquals(listOf(14000L, 3000L), r.days.filter { it.collectedCents > 0 }.map { it.collectedCents })
        assertEquals(day, r.days.last().date)
    }

    @Test fun `periodos de dia semana y mes`() {
        // 21 nov 2026 es sábado: la semana va del lunes 16 al domingo 22.
        assertEquals(LocalDate.of(2026, 11, 16)..LocalDate.of(2026, 11, 22), SalesPeriod.WEEK.range(day))
        assertEquals(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 30), SalesPeriod.MONTH.range(day))
        assertEquals(LocalDate.of(2026, 2, 1)..LocalDate.of(2026, 2, 28), SalesPeriod.MONTH.range(LocalDate.of(2026, 2, 14)))
        assertEquals(LocalDate.of(2026, 10, 21), SalesPeriod.MONTH.shift(day, -1))
        assertEquals("Noviembre 2026", SalesPeriod.MONTH.title(day))
    }

    @Test fun `csv con bloques, montos en pesos y nombres escapados`() {
        val entries = listOf(
            entry("a", "4", listOf(pay("p1", 4500, at(day, 14), received = 5000), pay("p2", 100, at(day, 14), voided = true)),
                order("a", at(day, 13), line("x", "Torta, con \"queso\"", 4500, 1))),
        )
        val csv = salesReport(entries, day, day, utc).toCsv(utc)
        assertTrue(csv.startsWith("﻿Reporte de ventas,2026-11-21,2026-11-21"))
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertTrue("Vendido,45.00" in lines)
        assertTrue("\"Torta, con \"\"queso\"\"\",1,45.00" in lines)
        assertTrue("2026-11-21,14:00,Mesa 4,1,Efectivo,45.00,50.00,No" in lines)
        assertTrue("2026-11-21,14:00,Mesa 4,1,Efectivo,1.00,,Sí" in lines)
    }

    @Test fun `las cuentas archivadas siguen en el reporte despues de reiniciar`() {
        var clock = 1_000_000L
        val db = tmp.newFile("db.json").also { it.delete() }
        val store = OrderStore(db, now = { clock })
        val asadaId = store.state.value.products.first { it.name == "Asada" }.id
        val o = store.createOrder(ClientMsg.CreateOrder(1, "k1", "4", null, listOf(LineRequest(asadaId, 2))))
        store.addPayment(ClientMsg.AddPayment(2, "p1", o.accountId, 7000, PayMethod.CASH, deliver = true))
        assertEquals(1, store.salesEntries().size)

        clock += UNDO_WINDOW_MS + 1
        store.archiveClosed()
        assertTrue(store.state.value.accounts.isEmpty())
        assertEquals(listOf(o.accountId), store.salesEntries().map { it.account.id })

        // Una línea dañada no impide leer el resto.
        File(db.parentFile, "historial.jsonl").appendText("{no es json\n")
        val reopened = OrderStore(db, now = { clock })
        val entries = reopened.salesEntries()
        assertEquals(listOf(o.accountId), entries.map { it.account.id })
        assertEquals(7000L, entries.single().account.paidCents)
    }
}
