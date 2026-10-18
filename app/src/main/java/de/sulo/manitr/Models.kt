package de.sulo.manitr

import java.time.LocalDate

/** Devisenbestand, z. B. 12.000 USD */
data class FxHolding(val id: Long, val code: String, val amount: Double, val label: String = "")

/** Goldbestand; [type] verweist auf [GoldType.key] */
data class GoldHolding(val id: Long, val type: String, val amount: Double)

/** Stammdaten einer Anleihe. Kurs wird live geholt, [manualPrice] ist der Fallback. */
data class BondInfo(
    val isin: String,
    val name: String,
    val currency: String,
    val coupon: Double,        // in % p. a.
    val freq: Int,             // Zahlungen pro Jahr: 1, 2 oder 4
    val maturity: LocalDate,
    val manualPrice: Double? = null,   // Kurs in % vom Nennwert
)

/** Bestand an einer Anleihe; [nominal] in der Währung der Anleihe */
data class BondHolding(val id: Long, val isin: String, val nominal: Double)

data class MiscHolding(
    val id: Long,
    val name: String,
    val category: String,
    val currency: String,
    val amount: Double,
)

/** Vermietete Immobilie: Miete pro Monat in türkischen Lira */
data class RentHolding(val id: Long, val property: String, val rentTry: Double)

data class Portfolio(
    val fx: List<FxHolding> = emptyList(),
    val gold: List<GoldHolding> = emptyList(),
    val bondInfos: List<BondInfo> = Presets.bonds,
    val bonds: List<BondHolding> = emptyList(),
    val misc: List<MiscHolding> = emptyList(),
    val rents: List<RentHolding> = emptyList(),
    val nextId: Long = 1,
) {
    fun info(isin: String): BondInfo? = bondInfos.firstOrNull { it.isin == isin }
}

data class GoldType(val key: String, val name: String, val unit: String, val fineGrams: Double)

object Presets {
    val goldTypes = listOf(
        GoldType("gram", "Feingold (Barren)", "g", 1.0),
        GoldType("ounce", "Feinunze", "oz", 31.1035),
        GoldType("ceyrek", "Çeyrek Altın", "Stk", 1.75 * 0.916),
        GoldType("yarim", "Yarım Altın", "Stk", 3.50 * 0.916),
        GoldType("tam", "Tam Altın", "Stk", 7.00 * 0.916),
        GoldType("ata", "Cumhuriyet / Ata Altın", "Stk", 7.216 * 0.916),
    )

    fun goldType(key: String): GoldType = goldTypes.firstOrNull { it.key == key } ?: goldTypes.first()

    /** Währungen, die der Wechselkurs-Dienst (EZB-Referenzkurse) liefert. */
    val currencies = linkedMapOf(
        "TRY" to "Türkische Lira", "USD" to "US-Dollar", "EUR" to "Euro",
        "GBP" to "Britisches Pfund", "CHF" to "Schweizer Franken", "AUD" to "Australischer Dollar",
        "BRL" to "Brasilianischer Real", "CAD" to "Kanadischer Dollar", "CNY" to "Chinesischer Yuan",
        "CZK" to "Tschechische Krone", "DKK" to "Dänische Krone", "HKD" to "Hongkong-Dollar",
        "HUF" to "Ungarischer Forint", "IDR" to "Indonesische Rupiah", "ILS" to "Israelischer Schekel",
        "INR" to "Indische Rupie", "ISK" to "Isländische Krone", "JPY" to "Japanischer Yen",
        "KRW" to "Südkoreanischer Won", "MXN" to "Mexikanischer Peso", "MYR" to "Malaysischer Ringgit",
        "NOK" to "Norwegische Krone", "NZD" to "Neuseeland-Dollar", "PHP" to "Philippinischer Peso",
        "PLN" to "Polnischer Złoty", "RON" to "Rumänischer Leu", "SEK" to "Schwedische Krone",
        "SGD" to "Singapur-Dollar", "THB" to "Thailändischer Baht", "ZAR" to "Südafrikanischer Rand",
    )

    val miscCategories = listOf(
        "Immobilie", "Fahrzeug", "Krypto", "Beteiligung", "Kunst / Sammlung", "Bargeld", "Sonstiges",
    )

    /** Voreinstellungen (Kupon/Fälligkeit bitte bei Bedarf in der App korrigieren). */
    val bonds = listOf(
        BondInfo("US900123CQ19", "Türkei 6,125 % 2028", "USD", 6.125, 2, LocalDate.of(2028, 10, 24)),
        BondInfo("US900123CT57", "Türkei 7,625 % 2029", "USD", 7.625, 2, LocalDate.of(2029, 4, 26)),
        BondInfo("US900123CY43", "Türkei 5,25 % 2030", "USD", 5.25, 2, LocalDate.of(2030, 3, 13)),
        BondInfo("US900123DA57", "Türkei 5,95 % 2031", "USD", 5.95, 2, LocalDate.of(2031, 1, 15)),
        BondInfo("US900123DL13", "Türkei 7,125 % 2032", "USD", 7.125, 2, LocalDate.of(2032, 7, 17)),
        BondInfo("XS3123715297", "Türkei 5,20 % 2031 (EUR)", "EUR", 5.20, 1, LocalDate.of(2031, 8, 17)),
        BondInfo("XS3293834662", "Türkei 5,15 % 2034 (EUR)", "EUR", 5.15, 1, LocalDate.of(2034, 3, 10)),
    )
}

// ---------- Marktdaten ----------

/** Ein Datenpunkt eines Verlaufs; [epochDay] = Tage seit 1970-01-01 */
data class PricePoint(val epochDay: Long, val value: Double)

data class BondQuote(
    val isin: String,
    val price: Double,               // % vom Nennwert, letzter Schlusskurs
    val prevClose: Double?,
    val asOf: LocalDate?,
    val history: List<PricePoint>,
    val source: String,
    val fetchedAt: Long,
)

data class CdsQuote(
    val value: Double,
    val history: List<PricePoint>,
    val fetchedAt: Long,
) {
    val change: Double? get() = if (history.size >= 2) history.last().value - history[history.size - 2].value else null
    val changePct: Double? get() = change?.let { c -> history[history.size - 2].value.takeIf { it != 0.0 }?.let { c / it * 100 } }
}

data class Market(
    val usdPer: Map<String, Double> = emptyMap(),   // Wert von 1 Einheit der Währung in USD
    val goldUsdPerOz: Double? = null,
    val bondQuotes: Map<String, BondQuote> = emptyMap(),
    val cds: CdsQuote? = null,
    val updatedAt: Long? = null,
)
