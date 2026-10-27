package de.sulo.manitr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Gespeicherter Gesamtwert der SK-Tabelle. kind: SA, ME oder SA+ME. late = nach dem Stichtag nachgetragen. */
data class SkSnapshot(val epochDay: Long, val usd: Double, val eur: Double, val kind: String, val late: Boolean)

/** Regeln: jeden Samstag und jedes Monatsende ein Eintrag (nur Stichtage der letzten 7 Tage werden nachgetragen). */
object SkHistory {
    const val WINDOW_DAYS = 7L

    fun lastSaturday(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

    fun lastMonthEnd(today: LocalDate): LocalDate {
        val ym = YearMonth.from(today)
        return if (today == ym.atEndOfMonth()) today else ym.minusMonths(1).atEndOfMonth()
    }

    /** Fällige, noch fehlende Stichtage mit Art. */
    fun due(today: LocalDate, existing: List<SkSnapshot>): List<Pair<LocalDate, String>> {
        val sat = lastSaturday(today)
        val me = lastMonthEnd(today)
        val cands = linkedMapOf<LocalDate, String>()
        if (ChronoUnit.DAYS.between(sat, today) <= WINDOW_DAYS) cands[sat] = "SA"
        if (ChronoUnit.DAYS.between(me, today) <= WINDOW_DAYS) cands[me] = if (cands[me] == "SA") "SA+ME" else "ME"
        val have = existing.map { it.epochDay }.toSet()
        return cands.filterKeys { it.toEpochDay() !in have }.map { it.key to it.value }.sortedBy { it.first }
    }

    fun snapshots(today: LocalDate, existing: List<SkSnapshot>, usd: Double, eur: Double): List<SkSnapshot> =
        due(today, existing).map { (d, k) -> SkSnapshot(d.toEpochDay(), usd, eur, k, d != today) }

    fun toJson(list: List<SkSnapshot>): String = JSONArray(list.map {
        JSONObject().put("d", it.epochDay).put("usd", it.usd).put("eur", it.eur).put("k", it.kind).put("late", it.late)
    }).toString()

    fun fromJson(json: String): List<SkSnapshot> {
        val a = JSONArray(json)
        return (0 until a.length()).map { a.getJSONObject(it) }
            .map { SkSnapshot(it.getLong("d"), it.getDouble("usd"), it.getDouble("eur"), it.optString("k", "SA"), it.optBoolean("late", false)) }
            .sortedBy { it.epochDay }
    }
}

/** Verschlüsselte Ablage des Verlaufs (eigene Datei, damit Hintergrund-Auftrag und App sich nicht überschreiben). */
class SkHistoryStore(context: Context) {
    private val vault = Vault(context.applicationContext, "sk_history.bin")

    fun load(): List<SkSnapshot> = synchronized(LOCK) { loadLocked() }

    fun save(list: List<SkSnapshot>) = synchronized(LOCK) { vault.save(SkHistory.toJson(list)) }

    /** Trägt fällige Einträge ein und liefert die komplette Liste. */
    fun recordDue(today: LocalDate, usd: Double, eur: Double): List<SkSnapshot> = synchronized(LOCK) {
        val cur = loadLocked()
        val add = SkHistory.snapshots(today, cur, usd, eur)
        if (add.isEmpty()) return cur
        val all = (cur + add).sortedBy { it.epochDay }
        vault.save(SkHistory.toJson(all))
        all
    }

    private fun loadLocked(): List<SkSnapshot> =
        vault.load()?.let { runCatching { SkHistory.fromJson(it) }.getOrNull() } ?: emptyList()

    private companion object { val LOCK = Any() }
}
