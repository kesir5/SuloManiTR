package de.sulo.manitr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
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

data class TRow(val id: Long, val title: String, val chip: String?, val stock: String, val sub: String, val usd: Double, val nativeCcy: String?)

private val COL = 82.dp   // Breite der USD-/EUR-Spalten (wie auf der Übersicht)

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
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val sum = rows.sumOf { it.usd }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(bottom = 88.dp)) {
        SectionTitle(heading)
        SCard(padding = 8.dp, borderColor = grp.color) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(grp.light).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("POSITION", Modifier.weight(1f), color = grp.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text("$ USD", Modifier.width(COL), color = grp.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("€ EUR", Modifier.width(COL), color = grp.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Box(Modifier.width(34.dp))
            }
            if (rows.isEmpty()) {
                Text(emptyText, color = C.Mute, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
            }
            rows.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (i % 2 == 1) grp.light.copy(alpha = .55f) else Color.Transparent)
                        .clickable { onEdit(r.id) }.padding(start = 4.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            r.chip?.let {
                                Text(
                                    it, fontSize = 9.sp, color = grp.color,
                                    modifier = Modifier.padding(start = 5.dp).clip(RoundedCornerShape(6.dp)).background(grp.light).padding(horizontal = 5.dp, vertical = 0.dp),
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.stock, fontSize = 12.5.sp, lineHeight = 15.sp, maxLines = 1)
                            if (r.sub.isNotEmpty()) Muted("  ·  ${r.sub}", size = 9)
                        }
                    }
                    val usdBold = r.nativeCcy == "USD"
                    val eurBold = r.nativeCcy == "EUR"
                    Text(fmt(r.usd), Modifier.width(COL), textAlign = TextAlign.End, fontSize = 12.5.sp, fontWeight = if (usdBold) FontWeight.Bold else FontWeight.Normal)
                    Text(fmt(rates.toEur(r.usd)), Modifier.width(COL), textAlign = TextAlign.End, fontSize = 12.5.sp, fontWeight = if (eurBold) FontWeight.Bold else FontWeight.Normal)
                    IconButton(onClick = { onDelete(r.id) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Entfernen", tint = C.Neg, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(grp.color).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Summe", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Text("${fmt(sum)} $", Modifier.width(COL), color = Color.White, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                Text("${fmt(rates.toEur(sum))} €", Modifier.width(COL), color = Color.White, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                Box(Modifier.width(34.dp))
            }
            Muted(footer, Modifier.padding(top = 4.dp), 9)
        }
        extra()
    }
}

@Composable
fun FxScreen(vm: AppViewModel, onEdit: (Long) -> Unit) {
    val rates = vm.rates
    val rows = vm.portfolio.fx.map {
        TRow(it.id, it.code, null, "${fmt(it.amount, 2)} ${it.code}", "Kurs ${fmt(rates.usdPer[it.code] ?: Rates.FALLBACK[it.code] ?: 0.0, 4)} $", rates.toUsd(it.code, it.amount), it.code)
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
        TRow(it.id, t.name, null, "${fmt(it.amount, if (t.unit == "Stk") 0 else 2)} ${t.unit}", "${fmt(t.fineGrams * rates.goldUsdPerGram(), 2)} $ je ${t.unit}", Calc.goldUsd(it, rates), null)
    }
    HoldingTable(
        "Mein Gold", Grp.GOLD, rows, rates, "Noch kein Gold im Bestand.\nTippe auf „Gold hinzufügen“.",
        "Goldspotpreis ${fmt(rates.goldUsdPerOz, 2)} $/oz · Münzen mit Feingehalt 91,6 % gerechnet",
        onEdit, vm::removeGold,
    )
}

@Composable
fun MiscScreen(vm: AppViewModel, onEdit: (Long) -> Unit, onRent: (Long?) -> Unit) {
    val rates = vm.rates
    val rows = vm.portfolio.misc.map {
        TRow(it.id, it.name, it.category, "${fmt(it.amount, 0)} ${it.currency}", "", rates.toUsd(it.currency, it.amount), it.currency)
    }
    HoldingTable(
        "Meine sonstigen Vermögenswerte", Grp.MISC, rows, rates, "Noch keine Einträge.\nTippe auf „Vermögenswert hinzufügen“.",
        "Wert in beliebiger Währung eingeben · Umrechnung live · Zeile antippen zum Ändern",
        onEdit, vm::removeMisc,
        extra = {
            Spacer(Modifier.height(28.dp))
            RentSection(vm, onRent)
        },
    )
}
