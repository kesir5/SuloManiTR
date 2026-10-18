package de.sulo.manitr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class CoreTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val cq19 = Presets.bonds.first { it.isin == "US900123CQ19" }
    private val eur = Presets.bonds.first { it.isin == "XS3123715297" }
    private val rates = Rates(mapOf("USD" to 1.0, "EUR" to 1.1236, "TRY" to 1 / 49.5), 4166.1, true)

    @Test
    fun couponDates_semiannual() {
        val d = Calc.couponDates(cq19, today, today.plusDays(365))
        assertEquals(listOf(LocalDate.of(2026, 10, 24), LocalDate.of(2027, 4, 24)), d)
    }

    @Test
    fun couponDates_annualAndMaturityCap() {
        assertEquals(LocalDate.of(2027, 8, 17), Calc.nextCoupon(eur, today))
        val all = Calc.couponDates(cq19, today, LocalDate.of(2040, 1, 1))
        assertEquals(LocalDate.of(2028, 10, 24), all.last())      // nie über die Fälligkeit hinaus
        assertEquals(5, all.size)
    }

    @Test
    fun couponDates_dayClampedToMonthEnd() {
        val b = BondInfo("X", "x", "USD", 5.0, 2, LocalDate.of(2030, 8, 31))
        val d = Calc.couponDates(b, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))
        assertEquals(listOf(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 8, 31)), d)
    }

    @Test
    fun payAmounts_matchSheet() {
        // 458.000 $ × 6,125 % / 2 = 14.026,25 $
        val h = BondHolding(1, cq19.isin, 458_000.0)
        val v = Calc.bondView(h, cq19, null, rates, today)
        assertEquals(14_026.25, v.perPayNative, 0.001)
        assertEquals(14_026.25 / 1.1236, rates.toEur(v.perPayUsd), 0.01)
        // EUR-Anleihe: 39.000 € × 5,20 % = 2.028 € → in USD
        val e = Calc.bondView(BondHolding(2, eur.isin, 39_000.0), eur, null, rates, today)
        assertEquals(2_028.0, e.perPayNative, 0.001)
        assertEquals(2_028.0 * 1.1236, e.perPayUsd, 0.01)
    }

    @Test
    fun schedule_onlyNext_and_12m() {
        val views = listOf(
            Calc.bondView(BondHolding(1, cq19.isin, 100_000.0), cq19, null, rates, today),
            Calc.bondView(BondHolding(2, eur.isin, 10_000.0), eur, null, rates, today),
        )
        assertEquals(2, Calc.schedule(views, rates, today, onlyNext = true).size)
        val rows = Calc.schedule(views, rates, today, onlyNext = false)
        assertEquals(3, rows.size)          // 24.10.26, 24.04.27, 17.08.27
        assertTrue(rows.all { it.days in 0..365 })
    }

    @Test
    fun ytm_parBondEqualsCoupon() {
        val b = BondInfo("Y", "y", "USD", 6.0, 2, LocalDate.of(2031, 10, 17))
        val y = Calc.ytm(b, 100.0, today)
        assertNotNull(y)
        assertEquals(6.0, y!!, 0.05)
        assertTrue(Calc.ytm(b, 90.0, today)!! > 6.0)     // Kurs unter pari → höhere Rendite
        assertTrue(Calc.ytm(b, 110.0, today)!! < 6.0)
    }

    @Test
    fun totals_sumAllClasses() {
        val p = Portfolio(
            fx = listOf(FxHolding(1, "EUR", 1000.0, "Bank A"), FxHolding(2, "USD", 500.0), FxHolding(3, "EUR", 250.0, "Tresor")),
            gold = listOf(GoldHolding(3, "ounce", 1.0)),
            bonds = listOf(BondHolding(4, cq19.isin, 10_000.0)),
            misc = listOf(MiscHolding(5, "Auto", "Fahrzeug", "EUR", 10_000.0)),
        )
        val m = Market(bondQuotes = mapOf(cq19.isin to BondQuote(cq19.isin, 99.0, 98.9, today, emptyList(), "t", 0)))
        val t = Calc.totals(p, m, rates, today)
        assertEquals(1250 * 1.1236 + 500, t.fx, 0.001)
        assertEquals(4166.1, t.gold, 0.001)
        assertEquals(9_900.0, t.bonds, 0.001)
        assertEquals(11_236.0, t.misc, 0.001)
        assertEquals(t.fx + t.gold + t.bonds + t.misc, t.total, 0.0001)
    }

    @Test
    fun backupCrypto_roundTrip_and_wrongPassword() {
        val plain = "geheim äöü € 123".toByteArray()
        val enc = BackupCrypto.encrypt(plain, "pw1234".toCharArray())
        assertEquals(String(plain), String(BackupCrypto.decrypt(enc, "pw1234".toCharArray())))
        try {
            BackupCrypto.decrypt(enc, "falsch".toCharArray()); fail("erwartet: Exception")
        } catch (e: BackupCrypto.BadPasswordOrFile) { /* ok */ }
    }

    @Test
    fun json_roundTrip() {
        val p = Portfolio(
            fx = listOf(FxHolding(1, "TRY", 850_000.0)), gold = listOf(GoldHolding(2, "ata", 3.0)),
            bonds = listOf(BondHolding(3, cq19.isin, 458_000.0)),
            bondInfos = Presets.bonds + BondInfo("ZZ", "Test", "EUR", 4.0, 1, LocalDate.of(2030, 1, 1), 95.5),
            misc = listOf(MiscHolding(4, "Wohnung", "Immobilie", "TRY", 5_200_000.0)),
            rents = listOf(RentHolding(7, "Wohnung Kadıköy", 45_000.0), RentHolding(8, "Laden İzmir", 60_000.0)), nextId = 9,
        )
        val m = Market(
            usdPer = mapOf("USD" to 1.0, "EUR" to 1.12), goldUsdPerOz = 4000.0, updatedAt = 123L,
            bondQuotes = mapOf(cq19.isin to BondQuote(cq19.isin, 99.0, null, today, listOf(PricePoint(1, 98.0), PricePoint(2, 99.0)), "BF", 5)),
            cds = CdsQuote(247.33, listOf(PricePoint(10, 245.27), PricePoint(11, 247.33)), 6),
        )
        val back = Json.storeFromJson(Json.storeToJson(p, m, "EUR"))
        assertEquals(p, back.portfolio)
        assertEquals(m, back.market)
        assertEquals("EUR", back.displayCcy)
        assertEquals(2.06, back.market.cds!!.change!!, 0.001)
        val (bp, ccy) = Json.backupFromJson(Json.backupToJson(p, "USD"))
        assertEquals(p, bp); assertEquals("USD", ccy)
    }

    @Test
    fun parsers() {
        assertEquals(247.33, parseGermanNumber("247,33")!!, 0.0)
        assertEquals(1234.5, parseInput("1.234,5")!!, 0.0)
        assertEquals(1234.5, parseInput("1234.5")!!, 0.0)
        assertNull(parseInput("abc"))
        assertEquals(LocalDate.of(2028, 10, 24), parseDateInput("24.10.2028"))
        assertEquals(LocalDate.of(2028, 10, 24), parseDateInput("2028-10-24"))
        assertNull(parseDateInput("quatsch"))

        val fx = MarketData.parseFx("""{"amount":1.0,"base":"USD","date":"2026-10-16","rates":{"CHF":0.82396,"EUR":0.88865,"TRY":49.527}}""")
        assertEquals(1 / 0.88865, fx.getValue("EUR"), 1e-9)
        assertEquals(1.0, fx.getValue("USD"), 0.0)
        assertEquals(4166.100098, MarketData.parseGold("""{"currency":"USD","price":4166.100098,"symbol":"XAU"}"""), 1e-9)

        val q = MarketData.parseBondHistory(
            "X", """{"s":"ok","c":[96.16,97.16],"t":[1791504000,1792108800],"v":[0,0]}""", "t", 0
        )!!
        assertEquals(97.16, q.price, 0.0); assertEquals(96.16, q.prevClose!!, 0.0)
        assertEquals(LocalDate.of(2026, 10, 16), q.asOf)
        assertNull(MarketData.parseBondHistory("X", """{"s":"no_data"}""", "t", 0))

        assertEquals(247.33, MarketData.parseCdsCurrent("... Anleihen Rendite und aktueller Stand 247,33 - Charts ...")!!, 0.0)
        val html = """<table><tr><th>Datum</th><th>Zuletzt</th></tr>
            <tr><td><span>05.10.2026</span></td><td>255,29</td><td>x</td></tr>
            <tr><td>02.10.2026</td><td>255,69</td></tr></table>"""
        val h = MarketData.parseCdsHistory(html)
        assertEquals(2, h.size)
        assertEquals(255.69, h.first().value, 0.0)          // aufsteigend sortiert
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), h.last().epochDay)

        // englische Tabelle (www.investing.com)
        val en = MarketData.parseCdsHistory("<table><tr><td>Oct 05, 2026</td><td>1,255.29</td></tr><tr><td>10/02/2026</td><td>255.69</td></tr></table>")
        assertEquals(2, en.size); assertEquals(1255.29, en.last().value, 0.0)
        // eingebettetes JSON
        val js = MarketData.parseCdsHistory("{\"data\":[{\"rowDateRaw\":1759622400,\"rowDateTimestamp\":\"2026-10-05T00:00:00Z\",\"last_close\":\"255.29\",\"last_closeRaw\":\"255.29\"},{\"rowDateTimestamp\":\"2026-10-02T00:00:00Z\",\"last_closeRaw\":255.69}]}")
        assertEquals(2, js.size); assertEquals(255.29, js.last().value, 0.0)
        // worldgovernmentbonds.com (JSON); Wochenend-Kopie wird entfernt
        val wgb = MarketData.parseWgbCds("{\"success\":true,\"result\":{\"quote\":{\"2\":{\"CLOSE_VAL\":245.27,\"DATA_VAL\":\"2026-10-16\"},\"1\":{\"CLOSE_VAL\":243.28,\"DATA_VAL\":\"2026-10-14\"},\"3\":{\"CLOSE_VAL\":245.27,\"DATA_VAL\":\"2026-10-17\"},\"4\":{\"CLOSE_VAL\":247.33,\"DATA_VAL\":\"2026-10-19\"}}}}")
        assertEquals(listOf(243.28, 245.27, 247.33), wgb.map { it.value })
        // Zusammenführen
        val merged = MarketData.mergeHistory(listOf(PricePoint(1, 1.0), PricePoint(2, 2.0)), listOf(PricePoint(2, 5.0), PricePoint(3, 3.0)))
        assertEquals(listOf(1.0, 5.0, 3.0), merged.map { it.value })
    }
}
