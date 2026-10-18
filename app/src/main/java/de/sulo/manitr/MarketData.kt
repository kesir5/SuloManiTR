package de.sulo.manitr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class RefreshResult(val market: Market, val errors: List<String>)

/**
 * Live-Abruf aller Kurse:
 *  - Wechselkurse: Frankfurter-API (EZB-Referenzkurse), kostenlos, ohne Schlüssel
 *  - Gold: gold-api.com (XAU/USD), kostenlos, ohne Schlüssel
 *  - Eurobonds: Börse-Frankfurt-Kursverlauf (Tageskurse, ca. 400 Tage)
 *  - Türkei CDS 5J: worldgovernmentbonds.com (JSON-Schnittstelle, Tageswerte seit 2015); Fallback investing.com
 * Schlägt eine Quelle fehl, bleibt der zuletzt gespeicherte Wert erhalten.
 */
object MarketData {
    private const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private val BOND_EXCHANGES = listOf("XFRA", "XSTU", "XETR")

    suspend fun refreshAll(p: Portfolio, old: Market): RefreshResult = coroutineScope {
        val now = System.currentTimeMillis()
        val fxD = async(Dispatchers.IO) { runCatching { fetchFx() } }
        val goldD = async(Dispatchers.IO) { runCatching { fetchGold() } }
        val cdsD = async(Dispatchers.IO) { runCatching { fetchCds(now, old.cds) } }
        val bondDs = p.bonds.map { it.isin }.distinct().map { isin ->
            isin to async(Dispatchers.IO) { runCatching { fetchBond(isin, now) } }
        }

        val errors = mutableListOf<String>()
        var market = old

        fxD.await().onSuccess { market = market.copy(usdPer = it) }.onFailure { errors += "Wechselkurse: ${it.msg()}" }
        goldD.await().onSuccess { market = market.copy(goldUsdPerOz = it) }.onFailure { errors += "Gold: ${it.msg()}" }
        cdsD.await().onSuccess { market = market.copy(cds = it.quote); if (it.historyNote != null) errors += "CDS-Verlauf (${it.historyNote})" }.onFailure { errors += "Türkei CDS: ${it.msg()}" }

        val quotes = old.bondQuotes.toMutableMap()
        for ((isin, d) in bondDs) {
            d.await().onSuccess { q ->
                if (q != null) quotes[isin] = q else errors += "$isin: kein Kurs gefunden"
            }.onFailure { errors += "$isin: ${it.msg()}" }
        }
        market = market.copy(bondQuotes = quotes, updatedAt = now)
        RefreshResult(market, errors)
    }

    private fun Throwable.msg() = message ?: javaClass.simpleName

    // ---------- HTTP ----------
    private fun get(url: String, userAgent: String = "SuloManiTR/1.0"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9")
            val code = c.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // ---------- Wechselkurse ----------
    internal fun parseFx(body: String): Map<String, Double> {
        val rates = JSONObject(body).getJSONObject("rates")
        val out = linkedMapOf("USD" to 1.0)
        for (k in rates.keys()) {
            val perUsd = rates.getDouble(k)
            if (perUsd > 0) out[k] = 1.0 / perUsd
        }
        return out
    }

    private suspend fun fetchFx(): Map<String, Double> = withContext(Dispatchers.IO) {
        parseFx(get("https://api.frankfurter.dev/v1/latest?base=USD"))
    }

    // ---------- Gold ----------
    internal fun parseGold(body: String): Double = JSONObject(body).getDouble("price")

    private suspend fun fetchGold(): Double = withContext(Dispatchers.IO) {
        parseGold(get("https://api.gold-api.com/price/XAU"))
    }

    // ---------- Anleihen ----------
    internal fun parseBondHistory(isin: String, body: String, source: String, now: Long): BondQuote? {
        val js = JSONObject(body)
        if (js.optString("s") != "ok") return null
        val c = js.optJSONArray("c") ?: return null
        val t = js.optJSONArray("t") ?: return null
        val n = minOf(c.length(), t.length())
        if (n == 0) return null
        val points = (0 until n).map { PricePoint(Math.floorDiv(t.getLong(it), 86_400L), c.getDouble(it)) }
        val last = points.last()
        return BondQuote(
            isin = isin, price = last.value, prevClose = points.getOrNull(n - 2)?.value,
            asOf = LocalDate.ofEpochDay(last.epochDay), history = points, source = source, fetchedAt = now,
        )
    }

    private suspend fun fetchBond(isin: String, now: Long): BondQuote? = withContext(Dispatchers.IO) {
        val to = now / 1000
        val from = to - 400L * 86_400L
        var lastError: Throwable? = null
        for (ex in BOND_EXCHANGES) {
            try {
                val body = get(
                    "https://api.boerse-frankfurt.de/v1/tradingview/history?symbol=$ex%3A$isin&resolution=D&from=$from&to=$to"
                )
                parseBondHistory(isin, body, "Börse Frankfurt ($ex)", now)?.let { return@withContext it }
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (lastError != null) throw lastError
        null
    }

    // ---------- Türkei CDS 5J ----------
    private const val CDS_URL = "https://de.investing.com/rates-bonds/turkey-cds-5-year-usd"
    private const val CDS_URL_EN = "https://www.investing.com/rates-bonds/turkey-cds-5-year-usd"
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    internal fun parseCdsCurrent(html: String): Double? =
        Regex("""aktueller Stand\s+([0-9][0-9.]*,[0-9]+)""").find(html)?.groupValues?.get(1)?.let(::parseGermanNumber)

    private fun parseEnglishNumber(s: String): Double? = s.trim().replace(",", "").toDoubleOrNull()

    /** Tabelle der „Historischen Daten“ (deutsch dd.MM.yyyy / englisch MM/dd/yyyy bzw. „Oct 05, 2026“). */
    private fun parseCdsTable(html: String): List<PricePoint> {
        val rowRe = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRe = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tagRe = Regex("""<[^>]+>""")
        val de = Regex("""(\d{2})\.(\d{2})\.(\d{4})""")
        val us = Regex("""(\d{2})/(\d{2})/(\d{4})""")
        val en = Regex("""([A-Z][a-z]{2})[a-z]*\.? (\d{1,2}), (\d{4})""")
        val out = mutableListOf<PricePoint>()
        for (row in rowRe.findAll(html)) {
            val cells = cellRe.findAll(row.groupValues[1])
                .map { tagRe.replace(it.groupValues[1], "").replace("&nbsp;", " ").replace('\u00a0', ' ').trim() }.toList()
            if (cells.size < 2) continue
            val c0 = cells[0]
            val date: LocalDate
            val value: Double
            val d1 = de.find(c0)
            val d2 = us.find(c0)
            val d3 = en.find(c0)
            try {
                when {
                    d1 != null -> {
                        date = LocalDate.of(d1.groupValues[3].toInt(), d1.groupValues[2].toInt(), d1.groupValues[1].toInt())
                        value = parseGermanNumber(cells[1]) ?: continue
                    }
                    d2 != null -> {
                        date = LocalDate.of(d2.groupValues[3].toInt(), d2.groupValues[1].toInt(), d2.groupValues[2].toInt())
                        value = parseEnglishNumber(cells[1]) ?: continue
                    }
                    d3 != null -> {
                        val mi = MONTHS.indexOf(d3.groupValues[1]) + 1
                        if (mi == 0) continue
                        date = LocalDate.of(d3.groupValues[3].toInt(), mi, d3.groupValues[2].toInt())
                        value = parseEnglishNumber(cells[1]) ?: continue
                    }
                    else -> continue
                }
            } catch (e: java.time.DateTimeException) { continue }
            out += PricePoint(date.toEpochDay(), value)
        }
        return out
    }

    /** Falls die Seite die Daten als eingebettetes JSON liefert (Next.js: rowDateTimestamp / last_closeRaw). */
    private fun parseCdsJson(html: String): List<PricePoint> {
        val out = mutableListOf<PricePoint>()
        val iso = Regex(""""rowDateTimestamp"\s*:\s*"(\d{4})-(\d{2})-(\d{2})[^"]*"[^{}]*?"last_closeRaw"\s*:\s*"?(-?[0-9]+(?:\.[0-9]+)?)"?""")
        for (m in iso.findAll(html)) {
            val v = m.groupValues[4].toDoubleOrNull() ?: continue
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                .onSuccess { out += PricePoint(it.toEpochDay(), v) }
        }
        if (out.isEmpty()) {
            val epoch = Regex(""""rowDateRaw"\s*:\s*"?(\d{9,10})"?[^{}]*?"last_closeRaw"\s*:\s*"?(-?[0-9]+(?:\.[0-9]+)?)"?""")
            for (m in epoch.findAll(html)) {
                val v = m.groupValues[2].toDoubleOrNull() ?: continue
                out += PricePoint(m.groupValues[1].toLong() / 86_400, v)
            }
        }
        return out
    }

    internal fun parseCdsHistory(html: String): List<PricePoint> {
        val table = parseCdsTable(html)
        val pts = if (table.size >= 2) table else parseCdsJson(html).ifEmpty { table }
        return pts.sortedBy { it.epochDay }.distinctBy { it.epochDay }
    }

    /** Alte und neue Punkte zusammenführen (neu gewinnt), höchstens ~13 Monate behalten. */
    internal fun mergeHistory(old: List<PricePoint>, fresh: List<PricePoint>): List<PricePoint> {
        val map = sortedMapOf<Long, Double>()
        old.forEach { map[it.epochDay] = it.value }
        fresh.forEach { map[it.epochDay] = it.value }
        return map.entries.map { PricePoint(it.key, it.value) }.takeLast(400)
    }

    private data class CdsResult(val quote: CdsQuote, val historyNote: String?)

    // ----- Hauptquelle: worldgovernmentbonds.com (JSON, tägliche Werte seit 2015) -----
    private const val WGB_URL = "https://www.worldgovernmentbonds.com/wp-json/common/v1/historical"
    private const val WGB_BODY =
        """{"GLOBALVAR":{"FUNCTION":"CDS","DOMESTIC":true,"DATE_RIF":"2099-12-31","COUNTRY1":{"SYMBOL":"13"},"OBJ1":{"DURATA":60}}}"""

    internal fun parseWgbCds(body: String): List<PricePoint> {
        val root = JSONObject(body)
        if (!root.optBoolean("success", false)) throw java.io.IOException("Antwort ohne Daten")
        val quote = root.getJSONObject("result").getJSONObject("quote")
        val pts = ArrayList<PricePoint>(quote.length())
        for (k in quote.keys()) {
            val o = quote.getJSONObject(k)
            val v = o.optDouble("CLOSE_VAL", Double.NaN)
            val d = o.optString("DATA_VAL", "")
            if (v.isNaN() || v <= 0 || d.length < 10) continue
            val day = runCatching { LocalDate.parse(d.substring(0, 10)) }.getOrNull() ?: continue
            pts += PricePoint(day.toEpochDay(), v)
        }
        val sorted = pts.sortedBy { it.epochDay }.distinctBy { it.epochDay }
        // Wochenenden mit unverändertem Vortageswert sind Kopien → weglassen
        val out = ArrayList<PricePoint>(sorted.size)
        for (p in sorted) {
            val dow = LocalDate.ofEpochDay(p.epochDay).dayOfWeek.value   // 6 = Sa, 7 = So
            if (dow >= 6 && out.isNotEmpty() && out.last().value == p.value) continue
            out += p
        }
        return out
    }

    private fun post(url: String, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 12_000
            c.readTimeout = 25_000
            c.doOutput = true
            c.setRequestProperty("User-Agent", UA_DESKTOP)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Origin", "https://www.worldgovernmentbonds.com")
            c.setRequestProperty("Referer", "https://www.worldgovernmentbonds.com/cds-historical-data/turkey/5-years/")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun fetchCds(now: Long, old: CdsQuote?): CdsResult = withContext(Dispatchers.IO) {
        // 1) worldgovernmentbonds.com
        var wgbError: String? = null
        try {
            val pts = parseWgbCds(post(WGB_URL, WGB_BODY))
            if (pts.size >= 2) {
                val hist = pts.takeLast(400)
                return@withContext CdsResult(CdsQuote(hist.last().value, hist, now, "worldgovernmentbonds.com"), null)
            }
            wgbError = "zu wenige Datenpunkte"
        } catch (e: Exception) { wgbError = e.msg() }
        // 2) Fallback: investing.com
        var current: Double? = null
        var currentErr: Throwable? = null
        var history = emptyList<PricePoint>()
        val notes = mutableListOf<String>()
        try { current = parseCdsCurrent(get(CDS_URL, UA_DESKTOP)) } catch (e: Exception) { currentErr = e }
        for ((label, url) in listOf("de" to "$CDS_URL-historical-data", "en" to "$CDS_URL_EN-historical-data")) {
            try {
                val h = parseCdsHistory(get(url, UA_DESKTOP))
                if (h.size >= 2) { history = h; break }
                notes += "$label: keine Tabelle gefunden"
            } catch (e: Exception) { notes += "$label: ${e.msg()}" }
        }
        val merged = mergeHistory(old?.history.orEmpty(), history)
        val value = current ?: history.lastOrNull()?.value ?: merged.lastOrNull()?.value
            ?: throw currentErr ?: java.io.IOException("Wert nicht gefunden")
        val points = merged.toMutableList()
        // aktuellen Stand als letzten Punkt ergänzen, wenn er vom letzten Historienwert abweicht
        if (points.isEmpty() || kotlin.math.abs(points.last().value - value) > 1e-9) {
            val today = LocalDate.now().toEpochDay()
            if (points.isNotEmpty() && points.last().epochDay == today) points.removeAt(points.lastIndex)
            points += PricePoint(today, value)
        }
        CdsResult(CdsQuote(value = value, history = points, fetchedAt = now, source = "investing.com"), "worldgovernmentbonds: $wgbError" + if (history.size >= 2) "" else ", " + notes.joinToString(", "))
    }
}
