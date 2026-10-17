package de.sulo.manitr

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** (De-)Serialisierung von Portfolio und Marktdaten über org.json. */
object Json {
    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { f(getJSONObject(it)) }
    }

    // ---------- Portfolio ----------
    fun portfolioToJson(p: Portfolio): JSONObject = JSONObject().apply {
        put("nextId", p.nextId)
        put("fx", JSONArray(p.fx.map { JSONObject().put("id", it.id).put("code", it.code).put("amount", it.amount) }))
        put("gold", JSONArray(p.gold.map { JSONObject().put("id", it.id).put("type", it.type).put("amount", it.amount) }))
        put("bondInfos", JSONArray(p.bondInfos.map {
            JSONObject().put("isin", it.isin).put("name", it.name).put("currency", it.currency)
                .put("coupon", it.coupon).put("freq", it.freq).put("maturity", it.maturity.toString())
                .put("manualPrice", it.manualPrice ?: JSONObject.NULL)
        }))
        put("bonds", JSONArray(p.bonds.map { JSONObject().put("id", it.id).put("isin", it.isin).put("nominal", it.nominal) }))
        put("misc", JSONArray(p.misc.map {
            JSONObject().put("id", it.id).put("name", it.name).put("category", it.category)
                .put("currency", it.currency).put("amount", it.amount)
        }))
    }

    fun portfolioFromJson(o: JSONObject): Portfolio {
        val infos = o.optJSONArray("bondInfos").mapObjects {
            BondInfo(
                isin = it.getString("isin"), name = it.getString("name"), currency = it.getString("currency"),
                coupon = it.getDouble("coupon"), freq = it.getInt("freq"),
                maturity = LocalDate.parse(it.getString("maturity")), manualPrice = it.optDoubleOrNull("manualPrice"),
            )
        }
        return Portfolio(
            fx = o.optJSONArray("fx").mapObjects { FxHolding(it.getLong("id"), it.getString("code"), it.getDouble("amount")) },
            gold = o.optJSONArray("gold").mapObjects { GoldHolding(it.getLong("id"), it.getString("type"), it.getDouble("amount")) },
            bondInfos = infos.ifEmpty { Presets.bonds },
            bonds = o.optJSONArray("bonds").mapObjects { BondHolding(it.getLong("id"), it.getString("isin"), it.getDouble("nominal")) },
            misc = o.optJSONArray("misc").mapObjects {
                MiscHolding(it.getLong("id"), it.getString("name"), it.getString("category"), it.getString("currency"), it.getDouble("amount"))
            },
            nextId = o.optLong("nextId", 1),
        )
    }

    // ---------- Marktdaten ----------
    private fun historyToJson(h: List<PricePoint>) = JSONArray(h.map { JSONArray().put(it.epochDay).put(it.value) })
    private fun historyFromJson(a: JSONArray?): List<PricePoint> =
        if (a == null) emptyList() else (0 until a.length()).map { PricePoint(a.getJSONArray(it).getLong(0), a.getJSONArray(it).getDouble(1)) }

    fun marketToJson(m: Market): JSONObject = JSONObject().apply {
        put("usdPer", JSONObject(m.usdPer as Map<*, *>))
        put("goldUsdPerOz", m.goldUsdPerOz ?: JSONObject.NULL)
        put("updatedAt", m.updatedAt ?: JSONObject.NULL)
        put("bondQuotes", JSONArray(m.bondQuotes.values.map {
            JSONObject().put("isin", it.isin).put("price", it.price).put("prevClose", it.prevClose ?: JSONObject.NULL)
                .put("asOf", it.asOf?.toString() ?: JSONObject.NULL).put("source", it.source)
                .put("fetchedAt", it.fetchedAt).put("history", historyToJson(it.history))
        }))
        m.cds?.let { put("cds", JSONObject().put("value", it.value).put("fetchedAt", it.fetchedAt).put("history", historyToJson(it.history))) }
    }

    fun marketFromJson(o: JSONObject): Market {
        val rates = o.optJSONObject("usdPer")?.let { r -> r.keys().asSequence().associateWith { r.getDouble(it) } } ?: emptyMap()
        val quotes = o.optJSONArray("bondQuotes").mapObjects {
            BondQuote(
                isin = it.getString("isin"), price = it.getDouble("price"), prevClose = it.optDoubleOrNull("prevClose"),
                asOf = if (it.isNull("asOf")) null else LocalDate.parse(it.getString("asOf")),
                history = historyFromJson(it.optJSONArray("history")), source = it.optString("source", ""),
                fetchedAt = it.optLong("fetchedAt", 0),
            )
        }.associateBy { it.isin }
        val cds = o.optJSONObject("cds")?.let {
            CdsQuote(it.getDouble("value"), historyFromJson(it.optJSONArray("history")), it.optLong("fetchedAt", 0))
        }
        return Market(
            usdPer = rates, goldUsdPerOz = o.optDoubleOrNull("goldUsdPerOz"), bondQuotes = quotes, cds = cds,
            updatedAt = if (o.has("updatedAt") && !o.isNull("updatedAt")) o.getLong("updatedAt") else null,
        )
    }

    // ---------- Gesamtdatei ----------
    fun storeToJson(p: Portfolio, m: Market, displayCcy: String): String = JSONObject()
        .put("version", 1).put("displayCcy", displayCcy)
        .put("portfolio", portfolioToJson(p)).put("market", marketToJson(m)).toString()

    data class Stored(val portfolio: Portfolio, val market: Market, val displayCcy: String)

    fun storeFromJson(s: String): Stored {
        val o = JSONObject(s)
        return Stored(
            portfolio = portfolioFromJson(o.getJSONObject("portfolio")),
            market = o.optJSONObject("market")?.let(::marketFromJson) ?: Market(),
            displayCcy = o.optString("displayCcy", "USD"),
        )
    }

    /** Backup enthält nur das Portfolio (keine Kurs-Zwischenspeicher). */
    fun backupToJson(p: Portfolio, displayCcy: String): String = JSONObject()
        .put("app", "SuloManiTR").put("version", 1).put("displayCcy", displayCcy)
        .put("portfolio", portfolioToJson(p)).toString()

    fun backupFromJson(s: String): Pair<Portfolio, String> {
        val o = JSONObject(s)
        require(o.optString("app") == "SuloManiTR") { "Keine Sülo-ManiTR-Datei" }
        return portfolioFromJson(o.getJSONObject("portfolio")) to o.optString("displayCcy", "USD")
    }
}
