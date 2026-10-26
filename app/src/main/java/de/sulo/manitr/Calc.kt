package de.sulo.manitr

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** Umrechnungskurse. Alles wird intern in USD gerechnet. */
data class Rates(val usdPer: Map<String, Double>, val goldUsdPerOz: Double, val live: Boolean) {
    fun toUsd(code: String, amount: Double): Double = amount * (usdPer[code] ?: FALLBACK[code] ?: 0.0)
    fun eurPerUsd(): Double = 1.0 / (usdPer["EUR"] ?: FALLBACK.getValue("EUR"))
    fun toEur(usd: Double): Double = usd * eurPerUsd()
    fun convert(usd: Double, target: String): Double = if (target == "EUR") toEur(usd) else usd
    fun goldUsdPerGram(): Double = goldUsdPerOz / 31.1035

    companion object {
        val FALLBACK = mapOf("USD" to 1.0, "EUR" to 1.12, "TRY" to 0.02, "GBP" to 1.33, "CHF" to 1.21)
        fun from(m: Market) = Rates(
            usdPer = if (m.usdPer.isEmpty()) FALLBACK else m.usdPer,
            goldUsdPerOz = m.goldUsdPerOz ?: 4000.0,
            live = m.usdPer.isNotEmpty() && m.goldUsdPerOz != null,
        )
    }
}

enum class PriceSource { LIVE, MANUAL, NONE }

data class BondView(
    val holding: BondHolding,
    val info: BondInfo,
    val quote: BondQuote?,
    val price: Double,            // % vom Nennwert
    val priceSource: PriceSource,
    val mvNative: Double,         // Marktwert in Anleihewährung
    val mvUsd: Double,
    val perPayNative: Double,     // Kupon je Termin, Anleihewährung
    val perPayUsd: Double,
    val next: LocalDate?,
    val ytm: Double?,
)

data class PayRow(
    val holdingId: Long,
    val date: LocalDate,
    val isin: String,
    val name: String,
    val currency: String,
    val nominal: Double,
    val native: Double,
    val usd: Double,
    val eur: Double,
    val days: Long,
    val isMaturity: Boolean,
)

data class Totals(val fx: Double, val gold: Double, val bonds: Double, val misc: Double) {
    val total: Double get() = fx + gold + bonds + misc
}

data class PosRow(val title: String, val sub: String, val usd: Double, val tag: String = "")

object Calc {

    /** Kupontermine im Intervall [from, until] (inkl.), höchstens bis zur Fälligkeit. */
    fun couponDates(info: BondInfo, from: LocalDate, until: LocalDate): List<LocalDate> {
        val end = if (until.isBefore(info.maturity)) until else info.maturity
        if (end.isBefore(from)) return emptyList()
        val freq = if (info.freq in listOf(1, 2, 4)) info.freq else 2
        val step = 12 / freq
        val m0 = info.maturity.monthValue - 1
        val day = info.maturity.dayOfMonth
        val months = (0 until freq).map { (m0 + it * step) % 12 + 1 }
        val out = sortedSetOf<LocalDate>()
        for (y in from.year..end.year) {
            for (mo in months) {
                val ym = YearMonth.of(y, mo)
                val date = ym.atDay(minOf(day, ym.lengthOfMonth()))
                if (!date.isBefore(from) && !date.isAfter(end)) out += date
            }
        }
        return out.toList()
    }

    fun nextCoupon(info: BondInfo, today: LocalDate): LocalDate? =
        couponDates(info, today, info.maturity).firstOrNull()

    /** Rendite bis Fälligkeit (% p. a.) aus dem Kurs (sauber, ohne Stückzinsen). */
    fun ytm(info: BondInfo, cleanPrice: Double, today: LocalDate): Double? {
        val future = couponDates(info, today, info.maturity)
        if (future.isEmpty() || cleanPrice <= 0) return null
        val f = info.freq.coerceIn(1, 4)
        val cpn = info.coupon / f
        val next = future.first()
        val prev = couponDates(info, today.minusYears(2), today.minusDays(1)).lastOrNull()
            ?: next.minusMonths((12 / f).toLong())
        val period = ChronoUnit.DAYS.between(prev, next).toDouble().coerceAtLeast(1.0)
        val accrued = cpn * ChronoUnit.DAYS.between(prev, today) / period
        val dirty = cleanPrice + accrued

        fun pv(y: Double): Double {
            var s = 0.0
            future.forEachIndexed { i, d ->
                val t = ChronoUnit.DAYS.between(today, d) / 365.25
                val cf = cpn + if (i == future.lastIndex) 100.0 else 0.0
                s += cf / (1 + y / f).pow(t * f)
            }
            return s
        }

        var lo = -0.5
        var hi = 2.0
        if (pv(lo) < dirty || pv(hi) > dirty) return null
        repeat(100) {
            val mid = (lo + hi) / 2
            if (pv(mid) > dirty) lo = mid else hi = mid
        }
        return (lo + hi) / 2 * 100
    }

    fun bondView(h: BondHolding, info: BondInfo, quote: BondQuote?, rates: Rates, today: LocalDate): BondView {
        val (price, src) = when {
            quote != null -> quote.price to PriceSource.LIVE
            info.manualPrice != null -> info.manualPrice to PriceSource.MANUAL
            else -> 100.0 to PriceSource.NONE
        }
        val mvNative = h.nominal * price / 100.0
        val per = h.nominal * info.coupon / 100.0 / info.freq.coerceIn(1, 4)
        return BondView(
            holding = h, info = info, quote = quote, price = price, priceSource = src,
            mvNative = mvNative, mvUsd = rates.toUsd(info.currency, mvNative),
            perPayNative = per, perPayUsd = rates.toUsd(info.currency, per),
            next = nextCoupon(info, today), ytm = ytm(info, price, today),
        )
    }

    fun bondViews(p: Portfolio, m: Market, rates: Rates, today: LocalDate): List<BondView> =
        p.bonds.mapNotNull { h ->
            val info = p.info(h.isin) ?: return@mapNotNull null
            bondView(h, info, m.bondQuotes[h.isin], rates, today)
        }

    /** Auszahlungsplan. [onlyNext] = nur der nächste Termin je Bestand, sonst die nächsten 12 Monate. */
    fun schedule(views: List<BondView>, rates: Rates, today: LocalDate, onlyNext: Boolean): List<PayRow> {
        val rows = mutableListOf<PayRow>()
        for (v in views) {
            val dates = couponDates(v.info, today, if (onlyNext) v.info.maturity else today.plusDays(365))
            for (d in if (onlyNext) dates.take(1) else dates) {
                val usd = v.perPayUsd
                rows += PayRow(
                    holdingId = v.holding.id, date = d, isin = v.info.isin, name = v.info.name,
                    currency = v.info.currency, nominal = v.holding.nominal, native = v.perPayNative,
                    usd = usd, eur = rates.toEur(usd), days = ChronoUnit.DAYS.between(today, d),
                    isMaturity = d == v.info.maturity,
                )
            }
        }
        return rows
    }

    fun totals(p: Portfolio, m: Market, rates: Rates, today: LocalDate): Totals {
        val fx = p.fx.sumOf { rates.toUsd(it.code, it.amount) }
        val gold = p.gold.sumOf { it.amount * Presets.goldType(it.type).fineGrams * rates.goldUsdPerGram() }
        val bonds = bondViews(p, m, rates, today).sumOf { it.mvUsd }
        val misc = p.misc.sumOf { rates.toUsd(it.currency, it.amount) }
        return Totals(fx, gold, bonds, misc)
    }

    /** USD-Wert einer SK-Position; null = ohne Umrechnung (Einheit „frei“). */
    fun skUsd(h: SkHolding, rates: Rates): Double? = when (h.unit) {
        "GRAMM" -> h.value * rates.goldUsdPerGram()
        "FREI" -> null
        else -> rates.toUsd(h.currency, h.value)
    }

    fun goldUsd(h: GoldHolding, rates: Rates): Double =
        h.amount * Presets.goldType(h.type).fineGrams * rates.goldUsdPerGram()
}
