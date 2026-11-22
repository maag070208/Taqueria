package com.axzy.taqueria

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ---------------- Periodos ----------------

enum class SalesPeriod(val label: String) { DAY("Día"), WEEK("Semana"), MONTH("Mes") }

/** Fechas (inclusivas) del periodo que contiene [anchor]. Las semanas van de lunes a domingo. */
fun SalesPeriod.range(anchor: LocalDate): ClosedRange<LocalDate> = when (this) {
    SalesPeriod.DAY -> anchor..anchor
    SalesPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it..it.plusDays(6) }
    SalesPeriod.MONTH -> anchor.withDayOfMonth(1).let { it..it.plusMonths(1).minusDays(1) }
}

/** Mismo periodo [steps] veces hacia adelante (o atrás si es negativo). */
fun SalesPeriod.shift(anchor: LocalDate, steps: Long): LocalDate = when (this) {
    SalesPeriod.DAY -> anchor.plusDays(steps)
    SalesPeriod.WEEK -> anchor.plusWeeks(steps)
    SalesPeriod.MONTH -> anchor.plusMonths(steps)
}

private val es = Locale.forLanguageTag("es-MX")
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", es)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", es)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", es)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

/** "sáb 21 nov 2026", "17 nov – 23 nov 2026" o "Noviembre 2026". */
fun SalesPeriod.title(anchor: LocalDate): String {
    val r = range(anchor)
    return when (this) {
        SalesPeriod.DAY -> dayFmt.format(anchor)
        SalesPeriod.WEEK -> "${shortFmt.format(r.start)} – ${shortFmt.format(r.endInclusive)} ${r.endInclusive.year}"
        SalesPeriod.MONTH -> monthFmt.format(anchor).replaceFirstChar { it.titlecase(es) }
    }
}

fun dayLabel(date: LocalDate): String = dayFmt.format(date)
fun shortDayLabel(date: LocalDate): String = shortFmt.format(date)

// ---------------- Reporte ----------------

/** Un cobro con la mesa y la cuenta a la que pertenece. */
data class PaymentRecord(val payment: Payment, val table: String, val accountNumber: Int)

data class ProductSales(val name: String, val qty: Int, val totalCents: Long)

data class DaySales(val date: LocalDate, val accounts: Int, val soldCents: Long, val collectedCents: Long)

/**
 * Ventas de un rango de fechas.
 * - Vendido: órdenes no canceladas creadas en el rango (cobradas o no).
 * - Cobrado: pagos registrados en el rango menos devoluciones; los anulados no cuentan.
 */
data class SalesReport(
    val from: LocalDate,
    val to: LocalDate,
    /** Todos los cobros del rango (también anulados y devoluciones), del más reciente al más antiguo. */
    val payments: List<PaymentRecord>,
    /** De mayor a menor importe. */
    val products: List<ProductSales>,
    val days: List<DaySales>,
    /** Cuentas con al menos una orden no cancelada en el rango. */
    val accounts: Int,
) {
    private val valid get() = payments.filterNot { it.payment.voided }
    val collectedCents get() = valid.sumOf { it.payment.amountCents }
    val refundsCents get() = -valid.filter { it.payment.amountCents < 0 }.sumOf { it.payment.amountCents }
    fun collectedBy(method: PayMethod) = valid.filter { it.payment.method == method }.sumOf { it.payment.amountCents }
    val soldCents get() = products.sumOf { it.totalCents }
    val averageTicketCents get() = if (accounts == 0) 0L else soldCents / accounts
}

fun salesReport(entries: List<HistoryEntry>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SalesReport {
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun inRange(ms: Long) = day(ms).let { it >= from && it <= to }

    val payments = entries.flatMap { e ->
        e.account.payments.filter { inRange(it.at) }.map { PaymentRecord(it, e.account.table, e.account.number) }
    }.sortedByDescending { it.payment.at }
    val orders = entries.flatMap { it.orders }
        .filter { it.status != OrderStatus.CANCELLED && inRange(it.createdAt) }
        .sortedBy { it.createdAt }

    // Por producto y presentación (no por nombre): si se renombró a medio periodo queda el nombre
    // más reciente. "Taco Asada · Maíz" y "· Harina" van separados; los mixtos se suman juntos.
    val products = orders.flatMap { it.lines }.groupBy { it.productId to it.variant }.values
        .map { lines ->
            val last = lines.last()
            val name = last.name + if (last.variant.isEmpty()) "" else " · ${last.variant}"
            ProductSales(name, lines.sumOf { it.qty }, lines.sumOf { it.subtotalCents })
        }
        .sortedWith(compareByDescending<ProductSales> { it.totalCents }.thenBy { it.name })

    val ordersByDay = orders.groupBy { day(it.createdAt) }
    val paidByDay = payments.filterNot { it.payment.voided }.groupBy { day(it.payment.at) }
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.map { d ->
        val o = ordersByDay[d].orEmpty()
        DaySales(d, o.map { it.accountId }.distinct().size, o.sumOf { it.totalCents }, paidByDay[d].orEmpty().sumOf { it.payment.amountCents })
    }.toList()

    return SalesReport(from, to, payments, products, days, orders.map { it.accountId }.distinct().size)
}

// ---------------- CSV ----------------

private fun csvAmount(cents: Long) = BigDecimal(cents).movePointLeft(2).setScale(2).toPlainString()

private fun csvField(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${v.replace("\"", "\"\"")}\"" else v

/**
 * Reporte en CSV (UTF-8 con BOM para que Excel respete los acentos), en bloques:
 * resumen, por día, productos y cobros. Montos en pesos con dos decimales.
 */
fun SalesReport.toCsv(zone: ZoneId = ZoneId.systemDefault()): String {
    val rows = mutableListOf<List<String>>()
    fun row(vararg v: String) { rows += v.toList() }
    row("Reporte de ventas", from.toString(), to.toString())
    row()
    row("Resumen")
    row("Vendido", csvAmount(soldCents))
    row("Cobrado", csvAmount(collectedCents))
    row("Efectivo", csvAmount(collectedBy(PayMethod.CASH)))
    row("Transferencia", csvAmount(collectedBy(PayMethod.TRANSFER)))
    row("Devoluciones", csvAmount(refundsCents))
    row("Cuentas", accounts.toString())
    row("Ticket promedio", csvAmount(averageTicketCents))
    row()
    row("Por día")
    row("Fecha", "Cuentas", "Vendido", "Cobrado")
    days.forEach { row(it.date.toString(), it.accounts.toString(), csvAmount(it.soldCents), csvAmount(it.collectedCents)) }
    row()
    row("Productos")
    row("Producto", "Cantidad", "Importe")
    products.forEach { row(it.name, it.qty.toString(), csvAmount(it.totalCents)) }
    row()
    row("Cobros")
    row("Fecha", "Hora", "Mesa", "Cuenta", "Método", "Monto", "Recibido", "Anulado")
    payments.reversed().forEach { r ->
        val at = Instant.ofEpochMilli(r.payment.at).atZone(zone)
        row(
            at.toLocalDate().toString(), timeFmt.format(at), tableLabel(r.table), r.accountNumber.toString(),
            r.payment.method.label, csvAmount(r.payment.amountCents), r.payment.receivedCents?.let(::csvAmount).orEmpty(),
            if (r.payment.voided) "Sí" else "No",
        )
    }
    return "﻿" + rows.joinToString("\r\n") { it.joinToString(",", transform = ::csvField) } + "\r\n"
}
