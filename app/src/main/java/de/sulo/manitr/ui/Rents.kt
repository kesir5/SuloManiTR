package de.sulo.manitr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import de.sulo.manitr.Rates
import de.sulo.manitr.fmt

private val TL = 68.dp
private val EU = 56.dp
private val US = 56.dp
private val DEL = 34.dp

/** Abschnitt „Mieten“ auf dem Misc-Bildschirm: Miete in TL, umgerechnet in EUR und USD. */
@Composable
fun RentSection(vm: AppViewModel, onOpen: (Long?) -> Unit) {
    val g = Grp.MISC
    val rates = vm.rates
    val rents = vm.portfolio.rents
    val sumTl = rents.sumOf { it.rentTry }

    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("MIETEN", Modifier.weight(1f), color = C.Mute, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen(null) }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, tint = g.color, modifier = Modifier.size(15.dp))
            Text(" Property", color = g.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
    SCard(padding = 8.dp, borderColor = g.color) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(g.light).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PROPERTY (MIETE / MONAT)", Modifier.weight(1f), color = g.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, maxLines = 1)
            Text("TL", Modifier.width(TL), color = g.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            Text("€ EUR", Modifier.width(EU), color = g.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            Text("$ USD", Modifier.width(US), color = g.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            Box(Modifier.width(DEL))
        }
        if (rents.isEmpty()) {
            Text(
                "Noch keine Properties.\nTippe oben auf „Property“.", color = C.Mute, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            )
        }
        rents.forEachIndexed { i, r ->
            val usd = rates.toUsd("TRY", r.rentTry)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (i % 2 == 1) g.light.copy(alpha = .55f) else Color.Transparent)
                    .clickable { onOpen(r.id) }.padding(start = 4.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.property, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmt(r.rentTry), Modifier.width(TL), textAlign = TextAlign.End, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(fmt(rates.toEur(usd)), Modifier.width(EU), textAlign = TextAlign.End, fontSize = 12.5.sp)
                Text(fmt(usd), Modifier.width(US), textAlign = TextAlign.End, fontSize = 12.5.sp)
                IconButton(onClick = { vm.removeRent(r.id) }, modifier = Modifier.size(DEL)) {
                    Icon(Icons.Default.Delete, contentDescription = "Entfernen", tint = C.Neg, modifier = Modifier.size(18.dp))
                }
            }
        }
        SumRow("Summe / Monat", sumTl, rates, g.light, Color(0xFF4A2C75), Modifier.padding(top = 6.dp))
        SumRow("Summe / Jahr", sumTl * 12, rates, g.color, Color.White, Modifier.padding(top = 4.dp))
        Muted("Umrechnung live (TRY → EUR/USD) · Jahr = 12 × Monatsmiete · Zeile antippen zum Ändern", Modifier.padding(top = 4.dp), 9)
    }
}

@Composable
private fun SumRow(label: String, tl: Double, rates: Rates, bg: Color, fg: Color, modifier: Modifier) {
    val usd = rates.toUsd("TRY", tl)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = fg, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        Text(fmt(tl), Modifier.width(TL), color = fg, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(fmt(rates.toEur(usd)), Modifier.width(EU), color = fg, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(fmt(usd), Modifier.width(US), color = fg, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Box(Modifier.width(DEL))
    }
}
