package de.sulo.manitr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sulo.manitr.PricePoint
import de.sulo.manitr.Rates
import de.sulo.manitr.de
import de.sulo.manitr.fmt
import java.time.LocalDate

object C {
    val Bg = Color(0xFFF7F9FC)
    val Pri = Color(0xFF1A4F9C)
    val PriC = Color(0xFFD8E6FF)
    val Navy = Color(0xFF10233F)
    val Ink = Color(0xFF1A1C20)
    val Mute = Color(0xFF6A7280)
    val Line = Color(0xFFE2E6EC)
    val Pos = Color(0xFF14804A)
    val Neg = Color(0xFFBA1A1A)
    val Soon = Color(0xFFF4C7C3)
    val Mid = Color(0xFFFDEBC8)
    val Ok = Color(0xFFE3F1E5)
}

enum class Grp(val title: String, val glyph: String, val color: Color, val light: Color) {
    FX("Devisen", "€", Color(0xFF1565C0), Color(0xFFE3EEFC)),
    GOLD("Gold", "Au", Color(0xFFB7791F), Color(0xFFFFF3D6)),
    BONDS("Eurobonds", "≣", Color(0xFF2E7D5B), Color(0xFFDEF3E8)),
    MISC("Sonstiges", "◆", Color(0xFF7B4BB7), Color(0xFFEFE6FB)),
}

@Composable
fun SuloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Pri, onPrimary = Color.White, primaryContainer = C.PriC, onPrimaryContainer = C.Navy,
            secondaryContainer = C.PriC, onSecondaryContainer = C.Navy,
            background = C.Bg, surface = Color.White, onSurface = C.Ink, surfaceVariant = Color(0xFFEEF2F8),
        ),
        content = content,
    )
}

@Composable
fun SCard(
    modifier: Modifier = Modifier,
    container: Color = Color.White,
    borderColor: Color = C.Line,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, borderColor),
    ) { Column(Modifier.padding(padding), content = content) }
}

@Composable
fun Glyph(text: String, color: Color, size: Dp = 24.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 11, align: TextAlign = TextAlign.Unspecified) {
    Text(text, color = C.Mute, fontSize = size.sp, lineHeight = (size + 2).sp, textAlign = align, modifier = modifier)
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), color = C.Mute, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

fun sym(ccy: String) = when (ccy) { "USD" -> "$"; "EUR" -> "€"; else -> ccy }

fun money(usd: Double, ccy: String, rates: Rates, digits: Int = 0) = "${fmt(rates.convert(usd, ccy), digits)} ${sym(ccy)}"

/** Liniendiagramm. [higherIsBad]: Anstieg wird rot gezeichnet (z. B. CDS). */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier, height: Dp = 110.dp, higherIsBad: Boolean = false) {
    if (values.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Kein Verlauf verfügbar", color = C.Mute, fontSize = 12.sp)
        }
        return
    }
    val up = values.last() >= values.first()
    val color = if (up != higherIsBad) C.Pos else C.Neg
    val mn = values.min()
    val mx = values.max()
    val rg = if (mx - mn < 1e-9) 1.0 else mx - mn
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        fun x(i: Int) = i / (values.size - 1f) * w
        fun y(v: Double) = (pad + (1 - (v - mn) / rg) * (h - 2 * pad)).toFloat()
        val line = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(area, color.copy(alpha = 0.12f))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x(values.lastIndex), y(values.last())))
    }
}

@Composable
fun ChartBlock(points: List<PricePoint>, decimals: Int = 2, higherIsBad: Boolean = false, height: Dp = 110.dp) {
    Column {
        LineChart(points.map { it.value }, height = height, higherIsBad = higherIsBad)
        if (points.size >= 2) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Muted(LocalDate.ofEpochDay(points.first().epochDay).de(), size = 10)
                Muted("Tief ${fmt(points.minOf { it.value }, decimals)} · Hoch ${fmt(points.maxOf { it.value }, decimals)}", size = 10)
                Muted(LocalDate.ofEpochDay(points.last().epochDay).de(), size = 10)
            }
        }
    }
}

@Composable
fun DropdownField(
    label: String,
    valueText: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = valueText, onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (enabled) Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(key) })
            }
        }
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        suffix = suffix?.let { s -> { Text(s) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true, placeholder: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        placeholder = placeholder?.let { p -> { Text(p) } }, modifier = modifier,
    )
}

@Composable
fun FormDialog(
    title: String,
    confirmText: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) } },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = C.Neg)) { Text("Entfernen") }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
fun InfoBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEF6FF)).padding(10.dp)) {
        Text(text, fontSize = 13.sp, color = LocalContentColor.current)
    }
}
