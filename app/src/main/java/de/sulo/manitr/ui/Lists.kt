package de.sulo.manitr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sulo.manitr.AppViewModel
import de.sulo.manitr.Calc
import de.sulo.manitr.Presets
import de.sulo.manitr.Rates
import de.sulo.manitr.fmt

data class TRow(val id: Long, val title: String, val chip: String?, val sub: String, val usd: Double, val nativeCcy: String?)

@Composable
private fun HoldingTable(
    heading: String,
    grp: Grp,
    rows: List<TRow>,
    rates: Rates,
    emptyText: String,
    footer: String,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val sum = rows.sumOf { it.usd }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 96.dp)) {
        SectionTitle(heading)
        SCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Muted("WÄHRUNG / POSITION", Modifier.weight(1f), 10)
                Muted("$ USD", Modifier.width(76.dp), 10)
                Muted("€ EUR", Modifier.width(76.dp), 10)
                Box(Modifier.width(40.dp))
            }
            if (rows.isEmpty()) {
                Text(emptyText, color = C.Mute, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp))
            }
            rows.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(r.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            r.chip?.let {
                                Text(
                                    it, fontSize = 10.sp, color = grp.color,
                                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(grp.light).padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Muted(r.sub, size = 10)
                    }
                    val usdBold = r.nativeCcy == "USD"
                    val eurBold = r.nativeCcy == "EUR"
                    Text(fmt(r.usd), Modifier.width(76.dp), textAlign = TextAlign.End, fontSize = 13.sp, fontWeight = if (usdBold) FontWeight.Bold else FontWeight.Normal)
                    Text(fmt(rates.toEur(r.usd)), Modifier.width(76.dp), textAlign = TextAlign.End, fontSize = 13.sp, fontWeight = if (eurBold) FontWeight.Bold else FontWeight.Normal)
                    IconButton(onClick = { onDelete(r.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Entfernen", tint = C.Neg, modifier = Modifier.size(20.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().size(1.dp).background(C.Line))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(C.PriC).padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End,
            ) {
                Text("Summe", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(fmt(sum), Modifier.width(76.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(fmt(rates.toEur(sum)), Modifier.width(76.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Box(Modifier.width(40.dp))
            }
            Muted(footer, Modifier.padding(top = 6.dp), 10)
        }
    }
}

@Composable
fun FxScreen(vm: AppViewModel, onEdit: (Long) -> Unit) {
    val rates = vm.rates
    val rows = vm.portfolio.fx.map {
        TRow(it.id, it.code, null, "${fmt(it.amount, 2)} ${it.code} · Kurs ${fmt(rates.usdPer[it.code] ?: Rates.FALLBACK[it.code] ?: 0.0, 4)} $", rates.toUsd(it.code, it.amount), it.code)
    }
    HoldingTable(
        "Meine Devisen", Grp.FX, rows, rates, "Noch keine Devisen.\nTippe auf „Devise hinzufügen“.",
        "Tippe auf eine Zeile zum Ändern des Betrags · Kurse: EZB-Referenzkurse (EUR/USD ${fmt(1.0 / rates.eurPerUsd(), 4)})",
        onEdit, vm::removeFx,
    )
}

@Composable
fun GoldScreen(vm: AppViewModel, onEdit: (Long) -> Unit) {
    val rates = vm.rates
    val rows = vm.portfolio.gold.map {
        val t = Presets.goldType(it.type)
        TRow(it.id, t.name, null, "${fmt(it.amount, if (t.unit == "Stk") 0 else 2)} ${t.unit} · ${fmt(t.fineGrams * rates.goldUsdPerGram(), 2)} $ je ${t.unit}", Calc.goldUsd(it, rates), null)
    }
    HoldingTable(
        "Mein Gold", Grp.GOLD, rows, rates, "Noch kein Gold im Bestand.\nTippe auf „Gold hinzufügen“.",
        "Goldspotpreis ${fmt(rates.goldUsdPerOz, 2)} $/oz · Münzen mit Feingehalt 91,6 % gerechnet",
        onEdit, vm::removeGold,
    )
}

@Composable
fun MiscScreen(vm: AppViewModel, onEdit: (Long) -> Unit) {
    val rates = vm.rates
    val rows = vm.portfolio.misc.map {
        TRow(it.id, it.name, it.category, "${fmt(it.amount, 0)} ${it.currency}", rates.toUsd(it.currency, it.amount), it.currency)
    }
    HoldingTable(
        "Meine sonstigen Vermögenswerte", Grp.MISC, rows, rates, "Noch keine Einträge.\nTippe auf „Vermögenswert hinzufügen“.",
        "Wert in beliebiger Währung eingeben · Umrechnung live · Zeile antippen zum Ändern",
        onEdit, vm::removeMisc,
    )
}
