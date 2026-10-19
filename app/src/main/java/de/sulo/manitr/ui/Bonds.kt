package de.sulo.manitr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sulo.manitr.AppViewModel
import de.sulo.manitr.BondView
import de.sulo.manitr.Calc
import de.sulo.manitr.PayRow
import de.sulo.manitr.PriceSource
import de.sulo.manitr.Rates
import de.sulo.manitr.de
import de.sulo.manitr.deShort
import de.sulo.manitr.fmt
import de.sulo.manitr.fmtSigned
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class SchedKey { DATE, NAME, USD }
private enum class BondSort(val label: String) {
    NEXT("Nächste Auszahlung"), MV("Marktwert"), PRICE("Kurs"), YIELD("Rendite"), MATURITY("Fälligkeit"), NAME("Name")
}

private val PERIODS = listOf("1M" to 30, "3M" to 90, "1J" to 365)
private val CDS_PERIODS = listOf("3T" to 3, "1W" to 7, "1M" to 30, "3M" to 91, "1J" to 365, "3J" to 1095)
private val DATE_W = 76.dp
private val OV_W = 78.dp
private val COL = 78.dp   // Breite der USD-/EUR-Spalten

@Composable
fun BondsScreen(vm: AppViewModel, onEdit: (Long) -> Unit) {
    val today = LocalDate.now()
    val rates = vm.rates
    val views = Calc.bondViews(vm.portfolio, vm.market, rates, today)

    var onlyNext by rememberSaveable { mutableStateOf(true) }
    var schedKey by rememberSaveable { mutableStateOf(SchedKey.DATE) }
    var schedAsc by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf(BondSort.NEXT) }
    var sortAsc by rememberSaveable { mutableStateOf(true) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }   // "s:<holdingId>:<date>" oder "c:<holdingId>"
    var period by rememberSaveable { mutableStateOf("3M") }

    val rows = Calc.schedule(views, rates, today, onlyNext).sortedWith { a, b ->
        val c = when (schedKey) {
            SchedKey.DATE -> a.date.compareTo(b.date)
            SchedKey.NAME -> a.name.compareTo(b.name, ignoreCase = true)
            SchedKey.USD -> a.usd.compareTo(b.usd)
        }
        (if (schedAsc) c else -c).takeIf { it != 0 } ?: a.date.compareTo(b.date)
    }
    val sorted = views.sortedWith { a, b ->
        val c = when (sort) {
            BondSort.NEXT -> compareValues(a.next, b.next)
            BondSort.MV -> a.mvUsd.compareTo(b.mvUsd)
            BondSort.PRICE -> a.price.compareTo(b.price)
            BondSort.YIELD -> compareValues(a.ytm, b.ytm)
            BondSort.MATURITY -> a.info.maturity.compareTo(b.info.maturity)
            BondSort.NAME -> a.info.name.compareTo(b.info.name, ignoreCase = true)
        }
        if (sortAsc) c else -c
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(bottom = 88.dp)) {
        // ---------- Überblick: ISIN · Nominal · Marktwert ----------
        SectionTitle("Meine Bonds im Überblick")
        SCard(padding = 8.dp, borderColor = Grp.BONDS.color) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Grp.BONDS.light).padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ISIN", Modifier.weight(1f), color = Grp.BONDS.color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Text("NOMINAL", Modifier.width(OV_W), color = Grp.BONDS.color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1)
                Text("MARKTWERT $", Modifier.width(OV_W), color = Grp.BONDS.color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1)
                Text("MARKTWERT €", Modifier.width(OV_W), color = Grp.BONDS.color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1)
            }
            if (views.isEmpty()) {
                Text("Noch keine Eurobonds im Bestand.\nTippe auf „Eurobond hinzufügen“.", color = C.Mute, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
            }
            views.forEachIndexed { i, v ->
                val key = "t:${v.holding.id}"
                Column(Modifier.fillMaxWidth().background(if (i % 2 == 1) Color(0xFFEFF6F2) else Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { IsinLink(v.info.isin, { detail = if (detail == key) null else key }, 12) }
                        Text("${fmt(v.holding.nominal)} ${sym(v.info.currency)}", Modifier.width(OV_W), textAlign = TextAlign.End, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        Text(fmt(v.mvUsd), Modifier.width(OV_W), textAlign = TextAlign.End, fontSize = 12.sp, fontWeight = if (v.info.currency == "USD") FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false)
                        Text(fmt(rates.toEur(v.mvUsd)), Modifier.width(OV_W), textAlign = TextAlign.End, fontSize = 12.sp, fontWeight = if (v.info.currency == "EUR") FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false)
                    }
                    if (detail == key) BondDetail(v, rates, period) { period = it }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)).background(Grp.BONDS.color).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Summe", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.width(OV_W))
                Text("${fmt(views.sumOf { it.mvUsd })} $", Modifier.width(OV_W), color = Color.White, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                Text("${fmt(views.sumOf { rates.toEur(it.mvUsd) })} €", Modifier.width(OV_W), color = Color.White, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
            Muted("Fett = Anleihewährung · Marktwert = Nominal × aktueller Kurs · ISIN antippen für Kurs & Chart", Modifier.padding(top = 4.dp), 9)
        }

        CdsCard(vm)

        // ---------- Auszahlungsplan ----------
        SectionTitle("Auszahlungsplan")
        SCard(padding = 8.dp, borderColor = Grp.BONDS.color) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFE8EDF5)).padding(2.dp)) {
                listOf(true to "Nächste je Anleihe", false to "Nächste 12 Monate").forEach { (flag, label) ->
                    Text(
                        label, textAlign = TextAlign.Center, fontSize = 12.sp,
                        fontWeight = if (onlyNext == flag) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (onlyNext == flag) C.Pri else C.Ink,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (onlyNext == flag) Color.White else Color.Transparent)
                            .clickable { onlyNext = flag; detail = null }.padding(vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // Kopfzeile
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Grp.BONDS.light).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortHeader("Datum", schedKey == SchedKey.DATE, schedAsc, Modifier.width(DATE_W)) { if (schedKey == SchedKey.DATE) schedAsc = !schedAsc else { schedKey = SchedKey.DATE; schedAsc = true }; detail = null }
                SortHeader("Eurobond", schedKey == SchedKey.NAME, schedAsc, Modifier.weight(1f)) { if (schedKey == SchedKey.NAME) schedAsc = !schedAsc else { schedKey = SchedKey.NAME; schedAsc = true }; detail = null }
                SortHeader("$ USD", schedKey == SchedKey.USD, schedAsc, Modifier.width(COL), end = true) { if (schedKey == SchedKey.USD) schedAsc = !schedAsc else { schedKey = SchedKey.USD; schedAsc = false }; detail = null }
                Text("€ EUR", Modifier.width(COL), color = Grp.BONDS.color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
            if (rows.isEmpty()) {
                Text("Noch keine Eurobonds im Bestand.\nTippe auf „Eurobond hinzufügen“.", color = C.Mute, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
            }
            rows.forEachIndexed { i, r ->
                val key = "s:${r.holdingId}:${r.date}"
                Column(Modifier.fillMaxWidth().background(if (i % 2 == 1) Color(0xFFEFF6F2) else Color.White)) {
                    PayRowItem(r, onIsin = { detail = if (detail == key) null else key })
                    if (detail == key) {
                        val v = views.firstOrNull { it.holding.id == r.holdingId }
                        if (v != null) BondDetail(v, rates, period) { period = it }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)).background(Grp.BONDS.color).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Summe", Modifier.width(DATE_W), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${rows.size} Zahlungen", Modifier.weight(1f), color = Color.White, fontSize = 11.sp)
                Text("${fmt(rows.sumOf { it.usd })} $", Modifier.width(COL), color = Color.White, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                Text("${fmt(rows.sumOf { it.eur })} €", Modifier.width(COL), color = Color.White, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
            Muted("Fett = Anleihewährung · Betrag = Nominal × Kupon (live umgerechnet) · ohne Rückzahlung des Nennwerts · ISIN antippen für Kurs & Chart", Modifier.padding(top = 4.dp), 9)
        }

        // ---------- Meine Eurobonds ----------
        SectionTitle("Meine Eurobonds")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BondSort.values().forEach { s ->
                FilterChip(
                    selected = sort == s,
                    onClick = { if (sort == s) sortAsc = !sortAsc else { sort = s; sortAsc = s != BondSort.MV } },
                    label = { Text(s.label + if (sort == s) (if (sortAsc) " ↑" else " ↓") else "", fontSize = 12.sp) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        sorted.forEach { v ->
            BondCard(v, rates, today, onEdit = { onEdit(v.holding.id) }, onDelete = { vm.removeBond(v.holding.id) },
                onIsin = { val k = "c:${v.holding.id}"; detail = if (detail == k) null else k })
            if (detail == "c:${v.holding.id}") {
                BondDetail(v, rates, period) { period = it }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SortHeader(text: String, active: Boolean, asc: Boolean, modifier: Modifier, end: Boolean = false, onClick: () -> Unit) {
    Text(
        text.uppercase() + if (active) (if (asc) " ▲" else " ▼") else "",
        color = if (active) C.Pri else Grp.BONDS.color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 5.dp),
    )
}

@Composable
private fun PayRowItem(r: PayRow, onIsin: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(DATE_W)) {
            Text(r.date.deShort(), fontSize = 12.sp, lineHeight = 14.sp)
            DueBadge(r.days, Modifier.padding(top = 2.dp))
        }
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            Text(r.name, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                IsinLink(r.isin, onIsin)
                Text("  ${fmt(r.nominal)} ${sym(r.currency)}", fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1)
                if (r.isMaturity) Muted("  · Fälligkeit", size = 9)
            }
        }
        Text(fmt(r.usd), Modifier.width(COL), textAlign = TextAlign.End, fontSize = 12.sp, fontWeight = if (r.currency == "USD") FontWeight.Bold else FontWeight.Normal)
        Text(fmt(r.eur), Modifier.width(COL), textAlign = TextAlign.End, fontSize = 12.sp, fontWeight = if (r.currency == "EUR") FontWeight.Bold else FontWeight.Normal)
    }
}

/** Fälligkeits-Badge: rot ≤ 14 Tage, orange ≤ 60 Tage, sonst grün. */
@Composable
private fun DueBadge(days: Long, modifier: Modifier = Modifier) {
    val (bg, fg) = when {
        days <= 14 -> Color(0xFFD32F2F) to Color.White
        days <= 60 -> Color(0xFFF9A825) to Color(0xFF3B2A00)
        else -> Color(0xFF2E7D32) to Color.White
    }
    val text = when (days) { 0L -> "heute"; 1L -> "morgen"; else -> "$days Tage" }
    Text(
        text, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun IsinLink(isin: String, onClick: () -> Unit, size: Int = 10) {
    Text(isin, color = C.Pri, fontSize = size.sp, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun BondCard(v: BondView, rates: Rates, today: LocalDate, onEdit: () -> Unit, onDelete: () -> Unit, onIsin: () -> Unit) {
    SCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sym(v.info.currency), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(if (v.info.currency == "USD") Color(0xFF2E7D32) else Color(0xFF1565C0)),
                    )
                    Text("  ${v.info.name}", fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row {
                    IsinLink(v.info.isin, onIsin)
                    Muted(" · Kupon ${fmt(v.info.coupon, 3)} % · fällig ${v.info.maturity.de()}", size = 11)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Entfernen", tint = C.Neg, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val chg = v.quote?.prevClose?.takeIf { it > 0 }?.let { (v.price / it - 1) * 100 }
        Row(Modifier.fillMaxWidth()) {
            Metric("Nominal (Bestand)", "${fmt(v.holding.nominal)} ${sym(v.info.currency)}", Modifier.weight(1f))
            Column(Modifier.weight(1f)) {
                Muted("Kurs · Rendite")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmt(v.price, 2)} %", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (chg != null) Text(" ${fmtSigned(chg)}", color = if (chg >= 0) C.Pos else C.Neg, fontSize = 11.sp)
                    Text(" · ${v.ytm?.let { fmt(it, 2) + " %" } ?: "–"}", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("Marktwert $", "${fmt(v.mvUsd)} $", Modifier.weight(1f))
            Metric("Marktwert €", "${fmt(rates.toEur(v.mvUsd))} €", Modifier.weight(1f))
        }
        if (v.priceSource != PriceSource.LIVE) {
            Muted(if (v.priceSource == PriceSource.MANUAL) "Kein Live-Kurs – manueller Kurs wird verwendet." else "Kein Kurs verfügbar – Nennwert (100 %) angesetzt. Aktualisieren oder manuellen Kurs eintragen.", Modifier.padding(top = 4.dp), 10)
        }
        val next = v.next
        if (next != null) {
            val d = ChronoUnit.DAYS.between(today, next)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEF6FF)).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Muted("Nächste Zahlung", size = 11)
                    Text(next.de(), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    DueBadge(d, Modifier.padding(top = 3.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("+ ${fmt(v.perPayUsd)} $", color = C.Pos, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
                    Text("+ ${fmt(rates.toEur(v.perPayUsd))} €", color = C.Pos, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Muted(label)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun BondDetail(v: BondView, rates: Rates, period: String, onPeriod: (String) -> Unit) {
    val q = v.quote
    val days = PERIODS.first { it.first == period }.second
    val hist = q?.history.orEmpty().let { h ->
        if (h.isEmpty()) h else h.filter { it.epochDay >= h.last().epochDay - days }
    }
    val chp = hist.firstOrNull()?.value?.takeIf { it > 0 }?.let { (v.price / it - 1) * 100 }
    val dayChg = q?.prevClose?.takeIf { it > 0 }?.let { (v.price / it - 1) * 100 }
    val s = sym(v.info.currency)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F7FD)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Muted("Aktueller Kurs · ${v.info.isin}")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${fmt(v.price, 2)} %", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    if (dayChg != null) Text("  ${fmtSigned(dayChg)} %", color = if (dayChg >= 0) C.Pos else C.Neg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Muted("${fmt(v.price * 10, 2)} $s je 1.000 $s Nominal")
                Muted(
                    when (v.priceSource) {
                        PriceSource.LIVE -> "Stand ${q?.asOf?.de() ?: "–"} · ${q?.source ?: ""}"
                        PriceSource.MANUAL -> "Manueller Kurs (kein Live-Kurs gefunden)"
                        PriceSource.NONE -> "Kein Kurs gefunden – Nennwert angesetzt"
                    }, size = 10,
                )
            }
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFE3EAF5)).padding(2.dp)) {
                PERIODS.forEach { (p, _) ->
                    Text(
                        p, fontSize = 12.sp, fontWeight = if (p == period) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (p == period) C.Pri else C.Ink,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (p == period) Color.White else Color.Transparent)
                            .clickable { onPeriod(p) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ChartBlock(hist, 2)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("Veränderung $period", chp?.let { fmtSigned(it) + " %" } ?: "–", Modifier.weight(1f))
            Metric("Rendite", v.ytm?.let { fmt(it, 2) + " %" } ?: "–", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("Kupon", "${fmt(v.info.coupon, 3)} % ${when (v.info.freq) { 1 -> "jährl."; 4 -> "vierteljährl."; else -> "halbjährl." }}", Modifier.weight(1f))
            Metric("Fälligkeit", v.info.maturity.de(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("Marktwert $", "${fmt(v.mvUsd)} $", Modifier.weight(1f))
            Metric("Marktwert €", "${fmt(rates.toEur(v.mvUsd))} €", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CdsCard(vm: AppViewModel) {
    val cds = vm.market.cds
    var period by rememberSaveable { mutableStateOf("3M") }
    SectionTitle("Länderrisiko")
    SCard(borderColor = Color(0xFFE6C3BF)) {
        Muted("Türkei CDS 5 Jahre (USD)")
        if (cds == null) {
            Text("Noch nicht abgerufen", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            Muted("Wird beim Aktualisieren von worldgovernmentbonds.com geladen. Falls die Seite nicht erreichbar ist, bleibt das Feld leer.", Modifier.padding(top = 4.dp), 10)
            return@SCard
        }
        val all = cds.history
        val days = CDS_PERIODS.first { it.first == period }.second
        val hist = if (all.isEmpty()) all else all.filter { it.epochDay >= all.last().epochDay - days }.let { if (it.size >= 2) it else all.takeLast(2) }
        val dayChg = cds.change
        val dayPct = cds.changePct
        val perChg = if (hist.size >= 2) hist.last().value - hist.first().value else null
        val perPct = if (hist.size >= 2 && hist.first().value != 0.0) perChg!! / hist.first().value * 100 else null
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt(cds.value, 2), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                if (dayChg != null && dayPct != null) {
                    // höherer CDS = höheres Risiko → rot
                    Text("  ${fmtSigned(dayChg)} (${fmtSigned(dayPct)} %)", color = if (dayChg > 0) C.Neg else C.Pos, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val last = all.lastOrNull()
                Muted("Stand ${if (last != null) LocalDate.ofEpochDay(last.epochDay).de() else "–"}", size = 10)
            }
        }
        Spacer(Modifier.height(8.dp))
        // Zeitraum-Schalter
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF3E6E4)).padding(2.dp)) {
            CDS_PERIODS.forEach { (p, _) ->
                val on = p == period
                Text(
                    p, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Color.White else C.Ink, maxLines = 1,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Color(0xFFB3463C) else Color.Transparent)
                        .clickable { period = p }.padding(vertical = 6.dp),
                )
            }
        }
        if (perChg != null && perPct != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Muted("Veränderung $period", size = 11)
                Text(
                    "${fmtSigned(perChg)} (${fmtSigned(perPct)} %)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (perChg > 0) C.Neg else C.Pos,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        ChartBlock(hist, 2, higherIsBad = true, height = 90.dp)
        if (all.size < 2) Muted("Der Verlauf wird mit jeder Aktualisierung länger – die Quelle lieferte keine Verlaufsdaten (Details auf der Übersicht).", Modifier.padding(top = 4.dp), 10)
        Muted("Höher = höheres Ausfallrisiko (Rot bei Anstieg) · Quelle: ${cds.source}", Modifier.padding(top = 6.dp), 10)
    }
}
