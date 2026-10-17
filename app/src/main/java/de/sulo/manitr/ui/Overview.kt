package de.sulo.manitr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sulo.manitr.AppViewModel
import de.sulo.manitr.Calc
import de.sulo.manitr.PosRow
import de.sulo.manitr.Presets
import de.sulo.manitr.Rates
import de.sulo.manitr.de
import de.sulo.manitr.fmt
import java.time.LocalDate

fun positionsOf(vm: AppViewModel, rates: Rates, today: LocalDate): Map<Grp, List<PosRow>> {
    val p = vm.portfolio
    return mapOf(
        Grp.FX to p.fx.map { PosRow("${it.code} – ${Presets.currencies[it.code] ?: it.code}", "${fmt(it.amount)} ${it.code}", rates.toUsd(it.code, it.amount)) },
        Grp.GOLD to p.gold.map { h ->
            val t = Presets.goldType(h.type)
            PosRow(t.name, "${fmt(h.amount, if (t.unit == "Stk") 0 else 2)} ${t.unit}", Calc.goldUsd(h, rates))
        },
        Grp.BONDS to Calc.bondViews(p, vm.market, rates, today).map {
            PosRow(it.info.name, "Nominal ${fmt(it.holding.nominal)} ${sym(it.info.currency)} · Kurs ${fmt(it.price, 2)} %", it.mvUsd)
        },
        Grp.MISC to p.misc.map { PosRow(it.name, "${it.category} · ${fmt(it.amount)} ${it.currency}", rates.toUsd(it.currency, it.amount)) },
    )
}

@Composable
fun OverviewScreen(vm: AppViewModel) {
    val today = LocalDate.now()
    val rates = vm.rates
    val ccy = vm.displayCcy
    val alt = if (ccy == "USD") "EUR" else "USD"
    val totals = Calc.totals(vm.portfolio, vm.market, rates, today)
    val positions = positionsOf(vm, rates, today)
    val views = Calc.bondViews(vm.portfolio, vm.market, rates, today)
    val next = Calc.schedule(views, rates, today, onlyNext = true).sortedBy { it.date }
    var open by rememberSaveable { mutableStateOf(Grp.values().map { it.name }.toSet()) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 96.dp)) {
        // ----- Gesamtvermögen -----
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF123A78), Color(0xFF1A4F9C), Color(0xFF2A6BC2)))).padding(16.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Gesamtvermögen", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)).padding(2.dp)) {
                        listOf("USD", "EUR").forEach { c ->
                            val on = c == ccy
                            Text(
                                c, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) C.Pri else Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color.White else Color.Transparent)
                                    .clickable { vm.changeDisplayCcy(c) }.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Text(money(totals.total, ccy, rates), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                Text("≙ ${money(totals.total, alt, rates)}", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                val parts = listOf(
                    Triple(Grp.FX, totals.fx, Color(0xFF64A8F0)), Triple(Grp.GOLD, totals.gold, Color(0xFFF2B33D)),
                    Triple(Grp.BONDS, totals.bonds, Color(0xFF4CC38A)), Triple(Grp.MISC, totals.misc, Color(0xFFB58BE8)),
                )
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .2f))) {
                    if (totals.total > 0) parts.forEach { (_, v, col) ->
                        if (v > 0) Box(Modifier.weight((v / totals.total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(col))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    parts.forEach { (g, v, col) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(8.dp).height(8.dp).clip(CircleShape).background(col))
                            Text(" ${g.title.removePrefix("Euro")} ${pct(v, totals.total)}", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ----- Kacheln -----
        val sums = mapOf(Grp.FX to totals.fx, Grp.GOLD to totals.gold, Grp.BONDS to totals.bonds, Grp.MISC to totals.misc)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi(Grp.FX, sums.getValue(Grp.FX), totals.total, rates, ccy, Modifier.weight(1f))
            Kpi(Grp.GOLD, sums.getValue(Grp.GOLD), totals.total, rates, ccy, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi(Grp.BONDS, sums.getValue(Grp.BONDS), totals.total, rates, ccy, Modifier.weight(1f))
            Kpi(Grp.MISC, sums.getValue(Grp.MISC), totals.total, rates, ccy, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        // ----- Nächste Auszahlung -----
        if (next.isNotEmpty()) {
            val first = next.first()
            val same = next.filter { it.date == first.date }
            val sum = same.sumOf { it.usd }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(Grp.BONDS.light)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(Grp.BONDS.color))
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("NÄCHSTE AUSZAHLUNG", color = Grp.BONDS.color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(first.date.de(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Text("in ${first.days} Tagen · ${same.size} Anleihe${if (same.size == 1) "" else "n"}", color = Color(0xFF2A6B34), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("+ ${money(sum, "USD", rates)}", color = C.Pos, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("≙ ${money(sum, "EUR", rates)}", color = Color(0xFF2A6B34), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ----- Kurse -----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RateChip("EUR/USD", fmt(1.0 / rates.eurPerUsd(), 4), Modifier.weight(1f))
            RateChip("USD/TRY", fmt(1.0 / (rates.usdPer["TRY"] ?: Rates.FALLBACK.getValue("TRY")), 2), Modifier.weight(1f))
            RateChip("Gold/oz", "${fmt(rates.goldUsdPerOz, 0)} $", Modifier.weight(1f))
        }
        if (!rates.live) {
            Spacer(Modifier.height(8.dp))
            InfoBox("Noch keine Live-Kurse geladen – es werden Platzhalterkurse verwendet. Tippe oben auf dein Profilbild → „Kurse aktualisieren“ (Internet nötig).")
        }

        // ----- Alle Wertpositionen -----
        SectionTitle("Alle Wertpositionen")
        Grp.values().forEach { g ->
            PositionGroup(g, positions.getValue(g), sums.getValue(g), rates, g.name in open) {
                open = if (g.name in open) open - g.name else open + g.name
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Navy).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("GESAMT", color = Color.White, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text(money(totals.total, "USD", rates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(money(totals.total, "EUR", rates), color = Color(0xFFB7C8E2), fontSize = 12.sp)
            }
        }
        if (vm.errors.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            InfoBox("Nicht erreichbar: " + vm.errors.joinToString("; "))
        }
    }
}

private fun pct(v: Double, total: Double) = if (total > 0) "${fmt(v / total * 100, 0)} %" else "0 %"

@Composable
private fun Kpi(g: Grp, usd: Double, total: Double, rates: Rates, ccy: String, modifier: Modifier) {
    val alt = if (ccy == "USD") "EUR" else "USD"
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(g.light)) {
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp).background(g.color))
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(g.glyph, g.color, 22.dp)
                        Text("  ${g.title}", color = g.color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(pct(usd, total), color = g.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(money(usd, ccy, rates), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(money(usd, alt, rates), fontSize = 12.sp, color = Color(0xFF444444))
            }
        }
    }
}

@Composable
private fun RateChip(label: String, value: String, modifier: Modifier) {
    SCard(modifier, padding = 8.dp) {
        Muted(label, size = 10)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PositionGroup(g: Grp, rows: List<PosRow>, sumUsd: Double, rates: Rates, open: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().background(g.light).clickable(onClick = onToggle).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glyph(g.glyph, g.color, 26.dp)
            Column(Modifier.weight(1f)) {
                Text(g.title, color = g.color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Muted("${rows.size} Position${if (rows.size == 1) "" else "en"}")
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(sumUsd, "USD", rates), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Muted(money(sumUsd, "EUR", rates))
            }
            Text(if (open) "▾" else "▸", color = g.color)
        }
        if (open) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Spacer(Modifier.weight(1f))
                    Muted("USD", Modifier.width(78.dp), 10)
                    Muted("EUR", Modifier.width(78.dp), 10)
                }
                if (rows.isEmpty()) Muted("Keine Positionen", Modifier.padding(vertical = 6.dp), 12)
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = 12.5.sp, maxLines = 1)
                            Muted(r.sub, size = 10)
                        }
                        Text(fmt(r.usd), Modifier.width(78.dp), textAlign = TextAlign.End, fontSize = 12.5.sp)
                        Text(fmt(rates.toEur(r.usd)), Modifier.width(78.dp), textAlign = TextAlign.End, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
