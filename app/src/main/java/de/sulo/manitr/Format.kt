package de.sulo.manitr

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DE: Locale = Locale.GERMANY
private val DATE_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

fun fmt(v: Double, digits: Int = 0): String {
    val nf = NumberFormat.getNumberInstance(DE)
    nf.minimumFractionDigits = digits
    nf.maximumFractionDigits = digits
    return nf.format(v)
}

fun fmtSigned(v: Double, digits: Int = 2): String = (if (v >= 0) "+" else "") + fmt(v, digits)

fun LocalDate.de(): String = format(DATE_LONG)
fun LocalDate.deShort(): String = format(DATE_SHORT)

/** Akzeptiert „1234.5“, „1234,5“ und „1.234,5“. */
fun parseInput(s: String): Double? {
    val t = s.trim().replace(" ", "")
    if (t.isEmpty()) return null
    val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return normalized.toDoubleOrNull()
}

/** Deutsche Zahl wie „1.234,56“ aus Webseiten. */
fun parseGermanNumber(s: String): Double? =
    s.trim().replace(".", "").replace(',', '.').toDoubleOrNull()

/** Akzeptiert TT.MM.JJJJ und JJJJ-MM-TT. */
fun parseDateInput(s: String): LocalDate? {
    val t = s.trim()
    return runCatching {
        if (t.contains('.')) LocalDate.parse(t, DATE_LONG) else LocalDate.parse(t)
    }.getOrNull()
}
