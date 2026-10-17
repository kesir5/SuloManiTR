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
 *  - Türkei CDS 5J: investing.com (Seitenabruf; kann bei Änderungen der Seite ausfallen)
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
        val cdsD = async(Dispatchers.IO) { runCatching { fetchCds(now) } }
        val bondDs = p.bonds.map { it.isin }.distinct().map { isin ->
            isin to async(Dispatchers.IO) { runCatching { fetchBond(isin, now) } }
        }

        val errors = mutableListOf<String>()
        var market = old

        fxD.await().onSuccess { market = market.copy(usdPer = it) }.onFailure { errors += "Wechselkurse: ${it.msg()}" }
        goldD.await().onSuccess { market = market.copy(goldUsdPerOz = it) }.onFailure { errors += "Gold: ${it.msg()}" }
        cdsD.await().onSuccess { market = market.copy(cds = it) }.onFailure { errors += "Türkei CDS: ${it.msg()}" }

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

    internal fun parseCdsCurrent(html: String): Double? =
        Regex("""aktueller Stand\s+([0-9][0-9.]*,[0-9]+)""").find(html)?.groupValues?.get(1)?.let(::parseGermanNumber)

    internal fun parseCdsHistory(html: String): List<PricePoint> {
        val rowRe = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRe = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tagRe = Regex("""<[^>]+>""")
        val dateRe = Regex("""^(\d{2})\.(\d{2})\.(\d{4})$""")
        val out = mutableListOf<PricePoint>()
        for (row in rowRe.findAll(html)) {
            val cells = cellRe.findAll(row.groupValues[1]).map { tagRe.replace(it.groupValues[1], "").trim() }.toList()
            if (cells.size < 2) continue
            val m = dateRe.matchEntire(cells[0]) ?: continue
            val value = parseGermanNumber(cells[1]) ?: continue
            val date = LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
            out += PricePoint(date.toEpochDay(), value)
        }
        return out.sortedBy { it.epochDay }.distinctBy { it.epochDay }
    }

    private suspend fun fetchCds(now: Long): CdsQuote = withContext(Dispatchers.IO) {
        val current = runCatching { parseCdsCurrent(get(CDS_URL, UA_DESKTOP)) }
        val history = runCatching { parseCdsHistory(get("$CDS_URL-historical-data", UA_DESKTOP)) }.getOrDefault(emptyList())
        val value = current.getOrNull() ?: history.lastOrNull()?.value
            ?: throw current.exceptionOrNull() ?: java.io.IOException("Wert nicht gefunden")
        val points = history.toMutableList()
        // aktuellen Stand als letzten Punkt ergänzen, wenn er vom letzten Historienwert abweicht
        if (points.isEmpty() || kotlin.math.abs(points.last().value - value) > 1e-9) {
            val today = LocalDate.now().toEpochDay()
            if (points.isNotEmpty() && points.last().epochDay == today) points.removeAt(points.lastIndex)
            points += PricePoint(today, value)
        }
        CdsQuote(value = value, history = points, fetchedAt = now)
    }
}
