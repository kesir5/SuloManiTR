package de.sulo.manitr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sulo.manitr.AppViewModel
import de.sulo.manitr.Calc
import de.sulo.manitr.Rates
import de.sulo.manitr.SkHolding
import de.sulo.manitr.fmt
import de.sulo.manitr.parseInput
import java.util.Locale

private val SK_CURRENCIES = listOf("USD", "EUR", "TRY", "CHF", "GBP")
private val SK_UNITS = listOf("BETRAG" to "Betrag", "GRAMM" to "Gramm", "FREI" to "frei")

private val W_CUR = 40.dp
private val W_VAL = 56.dp
private val W_UNIT = 38.dp
private val W_USD = 58.dp
private val W_EUR = 58.dp
private val W_DEL = 18.dp
private val LINE = Color(0xFFD5DBE6)

private fun unitShort(u: String) = when (u) { "GRAMM" -> "g"; "FREI" -> "frei"; else -> "Betr." }

private fun numText(v: Double): String =
    if (v == 0.0) "" else if (v == Math.floor(v) && Math.abs(v) < 1e12) v.toLong().toString()
    else String.format(Locale.GERMANY, "%.4f", v).trimEnd('0').trimEnd(',')

@Composable
private fun VLine() = Box(Modifier.width(0.5.dp).fillMaxHeight().background(LINE))

/** Reiter „SK“: Excel-artiges Raster zum Anlegen und Löschen von Positionen. */
@Composable
fun SkScreen(vm: AppViewModel) {
    val rates = vm.rates
    val items = vm.portfolio.sk
    val usd = items.map { Calc.skUsd(it, rates) }
    val sum = usd.sumOf { it ?: 0.0 }
    val hdr = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = C.Teal)

    Column1 {
        SectionTitle("SK")
        SCard(padding = 6.dp, borderColor = C.Teal) {
            // Kopfzeile
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(C.TealL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("BEZEICHNUNG", Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 5.dp), style = hdr, maxLines = 1)
                VLine(); Text("WHG", Modifier.width(W_CUR).padding(horizontal = 3.dp), style = hdr)
                VLine(); Text("WERT", Modifier.width(W_VAL).padding(horizontal = 3.dp), style = hdr, textAlign = TextAlign.End)
                VLine(); Text("EINH.", Modifier.width(W_UNIT).padding(horizontal = 3.dp), style = hdr)
                VLine(); Text("$ USD", Modifier.width(W_USD).padding(horizontal = 3.dp), style = hdr, textAlign = TextAlign.End, maxLines = 1)
                VLine(); Text("€ EUR", Modifier.width(W_EUR).padding(horizontal = 3.dp), style = hdr, textAlign = TextAlign.End, maxLines = 1)
                Spacer(Modifier.width(W_DEL + 0.5.dp))
            }
            if (items.isEmpty()) {
                Text(
                    "Noch keine Positionen.\nTippe unten auf „Zeile hinzufügen“.", color = C.Mute, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                )
            }
            items.forEachIndexed { i, h -> SkRow(h, usd[i], rates, i % 2 == 1, vm) }

            // Zeile hinzufügen
            Text(
                "+ Zeile hinzufügen", Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, C.Teal, RoundedCornerShape(6.dp)).clickable { vm.saveSk(null, "", "EUR", 0.0, "BETRAG") }.padding(vertical = 7.dp),
                color = C.Teal, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )

            // Gesamtsumme
            val usdT = "${fmt(sum)} $"
            val eurT = "${fmt(rates.toEur(sum))} €"
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(C.Teal).padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Gesamtsumme", Modifier.weight(1f).padding(start = 4.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.width(W_CUR + W_VAL + W_UNIT + 2.dp))
                Text(usdT, Modifier.width(W_USD).padding(end = 3.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = fitSp(usdT, 11f).sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(0.5.dp))
                Text(eurT, Modifier.width(W_EUR).padding(end = 3.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = fitSp(eurT, 11f).sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(W_DEL + 0.5.dp))
            }
            Muted("Betrag = Wert × Live-Kurs der Währung · Gramm = Feingold × Goldpreis · frei = ohne Umrechnung (nicht in der Summe) · Felder direkt antippen und tippen", Modifier.padding(top = 4.dp), 9)
        }
    }
}

@Composable
private fun Column1(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(bottom = 24.dp),
        content = content,
    )
}

@Composable
private fun SkRow(h: SkHolding, usd: Double?, rates: Rates, zebra: Boolean, vm: AppViewModel) {
    var name by remember(h) { mutableStateOf(h.name) }
    var valueText by remember(h) { mutableStateOf(numText(h.value)) }

    fun save(cur: String = h.currency, unit: String = h.unit) {
        vm.saveSk(h.id, name.trim(), cur, parseInput(valueText) ?: 0.0, unit)
    }
    fun commitText() {
        val v = parseInput(valueText) ?: 0.0
        if (name.trim() != h.name || v != h.value) save()
    }

    val cellText = TextStyle(fontSize = 11.5.sp, color = C.Ink)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(if (zebra) C.TealL.copy(alpha = .45f) else Color.Transparent)
            .border(0.5.dp, LINE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bezeichnung
        BasicTextField(
            value = name, onValueChange = { name = it }, textStyle = cellText.copy(fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(C.Teal),
            maxLines = 3, modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 6.dp).onFocusChanged { if (!it.isFocused) commitText() },
            decorationBox = { inner -> Box { if (name.isEmpty()) Text("Bezeichnung", style = cellText, color = Color(0xFFA0A8B5)); inner() } },
        )
        VLine()
        // Währung
        PickCell(if (h.unit == "GRAMM") "–" else "${h.currency} ▾", W_CUR, enabled = h.unit != "GRAMM") { close ->
            SK_CURRENCIES.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { close(); save(cur = c) }) }
        }
        VLine()
        // Wert
        BasicTextField(
            value = valueText, onValueChange = { valueText = it }, singleLine = true, textStyle = cellText.copy(textAlign = TextAlign.End),
            cursorBrush = SolidColor(C.Teal), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(W_VAL).padding(horizontal = 3.dp, vertical = 6.dp).onFocusChanged { if (!it.isFocused) commitText() },
            decorationBox = { inner -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { if (valueText.isEmpty()) Text("0", style = cellText, color = Color(0xFFA0A8B5)); inner() } },
        )
        VLine()
        // Einheit
        PickCell(unitShort(h.unit) + " ▾", W_UNIT, enabled = true) { close ->
            SK_UNITS.forEach { (k, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { close(); save(unit = k) }) }
        }
        VLine()
        // Umrechnung
        val u = usd?.let { fmt(it) } ?: "–"
        val e = usd?.let { fmt(rates.toEur(it)) } ?: "–"
        Text(u, Modifier.width(W_USD).padding(horizontal = 3.dp), style = cellText, fontSize = fitSp(u, 11.5f).sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        VLine()
        Text(e, Modifier.width(W_EUR).padding(horizontal = 3.dp), style = cellText, fontSize = fitSp(e, 11.5f).sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        VLine()
        Box(Modifier.width(W_DEL).fillMaxHeight().clickable { vm.removeSk(h.id) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, contentDescription = "Zeile löschen", tint = C.Neg, modifier = Modifier.size(14.dp))
        }
    }
}

/** Zelle mit Auswahlmenü (Chip + Dropdown). */
@Composable
private fun PickCell(label: String, width: androidx.compose.ui.unit.Dp, enabled: Boolean, menu: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.width(width).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Text(
            label, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
            color = if (enabled) C.Teal else Color(0xFFA0A8B5),
            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(if (enabled) C.TealL else Color.Transparent)
                .then(if (enabled) Modifier.clickable { open = true } else Modifier).padding(horizontal = 3.dp, vertical = 2.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
    }
}
