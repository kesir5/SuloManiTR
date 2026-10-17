package de.sulo.manitr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.sulo.manitr.AppViewModel
import de.sulo.manitr.BondInfo
import de.sulo.manitr.Calc
import de.sulo.manitr.Presets
import de.sulo.manitr.Rates
import de.sulo.manitr.de
import de.sulo.manitr.fmt
import de.sulo.manitr.parseDateInput
import de.sulo.manitr.parseInput
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun currencyOptions() = Presets.currencies.map { (k, v) -> k to "$k – $v" }

@Composable
fun FxDialog(vm: AppViewModel, id: Long?, onDismiss: () -> Unit) {
    val item = vm.portfolio.fx.firstOrNull { it.id == id }
    val used = vm.portfolio.fx.map { it.code }.toSet()
    val free = currencyOptions().filter { it.first !in used || it.first == item?.code }
    var code by remember { mutableStateOf(item?.code ?: free.firstOrNull()?.first ?: "") }
    var amount by remember { mutableStateOf(item?.amount?.let { plain(it) } ?: "") }
    val value = parseInput(amount)
    val rates = vm.rates
    FormDialog(
        title = if (item == null) "Devise hinzufügen" else "Devise ändern",
        confirmText = if (item == null) "Hinzufügen" else "Speichern",
        confirmEnabled = code.isNotEmpty() && value != null && value > 0,
        onDismiss = onDismiss,
        onConfirm = { vm.saveFx(item?.id, code, value!!); onDismiss() },
        onDelete = item?.let { { vm.removeFx(it.id); onDismiss() } },
    ) {
        if (free.isEmpty()) Text("Alle verfügbaren Währungen sind bereits angelegt.")
        DropdownField("Währung", currencyOptions().firstOrNull { it.first == code }?.second ?: code, free, { code = it }, enabled = item == null)
        NumberField("Betrag", amount, { amount = it }, suffix = code)
        if (value != null && value > 0) {
            val usd = rates.toUsd(code, value)
            InfoBox("≙ ${fmt(usd)} $ · ${fmt(rates.toEur(usd))} € (Live-Kurs)")
        }
    }
}

@Composable
fun GoldDialog(vm: AppViewModel, id: Long?, onDismiss: () -> Unit) {
    val item = vm.portfolio.gold.firstOrNull { it.id == id }
    var type by remember { mutableStateOf(item?.type ?: Presets.goldTypes.first().key) }
    var amount by remember { mutableStateOf(item?.amount?.let { plain(it) } ?: "") }
    val value = parseInput(amount)
    val rates = vm.rates
    val t = Presets.goldType(type)
    FormDialog(
        title = if (item == null) "Gold hinzufügen" else "Gold ändern",
        confirmText = if (item == null) "Hinzufügen" else "Speichern",
        confirmEnabled = value != null && value > 0,
        onDismiss = onDismiss,
        onConfirm = { vm.saveGold(item?.id, type, value!!); onDismiss() },
        onDelete = item?.let { { vm.removeGold(it.id); onDismiss() } },
    ) {
        DropdownField("Art", t.name, Presets.goldTypes.map { it.key to it.name }, { type = it })
        NumberField("Menge", amount, { amount = it }, suffix = t.unit)
        if (value != null && value > 0) {
            val usd = value * t.fineGrams * rates.goldUsdPerGram()
            InfoBox("≙ ${fmt(usd)} $ · ${fmt(rates.toEur(usd))} € (Spotpreis ${fmt(rates.goldUsdPerOz, 0)} $/oz, ${fmt(t.fineGrams, 3)} g Feingold je ${t.unit})")
        }
    }
}

@Composable
fun MiscDialog(vm: AppViewModel, id: Long?, onDismiss: () -> Unit) {
    val item = vm.portfolio.misc.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var cat by remember { mutableStateOf(item?.category ?: Presets.miscCategories.first()) }
    var ccy by remember { mutableStateOf(item?.currency ?: "EUR") }
    var amount by remember { mutableStateOf(item?.amount?.let { plain(it) } ?: "") }
    val value = parseInput(amount)
    val rates = vm.rates
    FormDialog(
        title = if (item == null) "Vermögenswert hinzufügen" else "Vermögenswert ändern",
        confirmText = if (item == null) "Hinzufügen" else "Speichern",
        confirmEnabled = name.isNotBlank() && value != null && value > 0,
        onDismiss = onDismiss,
        onConfirm = { vm.saveMisc(item?.id, name.trim(), cat, ccy, value!!); onDismiss() },
        onDelete = item?.let { { vm.removeMisc(it.id); onDismiss() } },
    ) {
        TextInput("Bezeichnung", name, { name = it }, placeholder = "z. B. Grundstück, Aktien, Schmuck")
        DropdownField("Kategorie", cat, Presets.miscCategories.map { it to it }, { cat = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField("Währung", ccy, currencyOptions(), { ccy = it }, Modifier.weight(1f))
            NumberField("Wert", amount, { amount = it }, Modifier.weight(1f))
        }
        if (value != null && value > 0) {
            val usd = rates.toUsd(ccy, value)
            InfoBox("≙ ${fmt(usd)} $ · ${fmt(rates.toEur(usd))} € (Live-Kurs)")
        }
    }
}

@Composable
fun BondDialog(vm: AppViewModel, id: Long?, onDismiss: () -> Unit) {
    val holding = vm.portfolio.bonds.firstOrNull { it.id == id }
    val existing = holding?.let { vm.portfolio.info(it.isin) }
    val infos = vm.portfolio.bondInfos
    val rates = vm.rates

    var isin by remember { mutableStateOf(existing?.isin ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var ccy by remember { mutableStateOf(existing?.currency ?: "USD") }
    var coupon by remember { mutableStateOf(existing?.coupon?.let { plain(it) } ?: "") }
    var freq by remember { mutableStateOf(existing?.freq ?: 2) }
    var maturity by remember { mutableStateOf(existing?.maturity?.de() ?: "") }
    var manual by remember { mutableStateOf(existing?.manualPrice?.let { plain(it) } ?: "") }
    var nominal by remember { mutableStateOf(holding?.nominal?.let { plain(it) } ?: "") }

    fun fill(i: BondInfo) {
        isin = i.isin; name = i.name; ccy = i.currency; coupon = plain(i.coupon); freq = i.freq
        maturity = i.maturity.de(); manual = i.manualPrice?.let { plain(it) } ?: ""
    }

    val couponV = parseInput(coupon)
    val matV = parseDateInput(maturity)
    val nominalV = parseInput(nominal)
    val manualV = if (manual.isBlank()) null else parseInput(manual)
    val isinClean = isin.trim().uppercase()
    val valid = isinClean.length in 6..14 && couponV != null && couponV >= 0 && matV != null &&
        nominalV != null && nominalV > 0 && (manual.isBlank() || manualV != null)
    val info = if (valid) BondInfo(isinClean, name.trim().ifBlank { "Türkei ${fmt(couponV!!, 3)} % ${matV!!.year}" }, ccy, couponV!!, freq, matV!!, manualV) else null
    val quote = vm.market.bondQuotes[isinClean]

    FormDialog(
        title = if (holding == null) "Eurobond hinzufügen" else "Eurobond ändern",
        confirmText = if (holding == null) "Hinzufügen" else "Speichern",
        confirmEnabled = valid,
        onDismiss = onDismiss,
        onConfirm = { vm.saveBond(holding?.id, info!!, nominalV!!); onDismiss() },
        onDelete = holding?.let { { vm.removeBond(it.id); onDismiss() } },
    ) {
        if (holding == null) {
            DropdownField(
                "Aus Liste wählen", infos.firstOrNull { it.isin == isinClean }?.let { "${it.name} · ${it.isin}" } ?: "Neue Anleihe (manuell eingeben)",
                listOf("" to "Neue Anleihe (manuell eingeben)") + infos.map { it.isin to "${it.name} · ${it.isin}" },
                { key -> infos.firstOrNull { it.isin == key }?.let(::fill) ?: run { isin = ""; name = "" } },
            )
        }
        TextInput("ISIN", isin, { isin = it.uppercase() }, enabled = holding == null)
        TextInput("Name (optional)", name, { name = it })
        NumberField("Nominal – wie viel besitzt du?", nominal, { nominal = it }, suffix = sym(ccy))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField("Währung", ccy, currencyOptions(), { ccy = it }, Modifier.weight(1f))
            NumberField("Kupon %", coupon, { coupon = it }, Modifier.weight(1f))
        }
        DropdownField(
            "Zahlungen pro Jahr", when (freq) { 1 -> "jährlich"; 4 -> "vierteljährlich"; else -> "halbjährlich" },
            listOf("1" to "jährlich", "2" to "halbjährlich", "4" to "vierteljährlich"), { freq = it.toInt() },
        )
        TextInput("Fälligkeit (TT.MM.JJJJ)", maturity, { maturity = it }, placeholder = "24.10.2028")
        NumberField("Manueller Kurs % (optional, Fallback)", manual, { manual = it }, suffix = "%")
        if (info != null) {
            val today = LocalDate.now()
            val view = Calc.bondView(de.sulo.manitr.BondHolding(0, info.isin, nominalV!!), info, quote, rates, today)
            val next = view.next
            InfoBox(
                buildString {
                    if (next != null) {
                        append("Nächste Auszahlung ${next.de()} (in ${ChronoUnit.DAYS.between(today, next)} T.):\n")
                        append("${fmt(view.perPayNative, 2)} ${sym(ccy)} ≙ ${fmt(view.perPayUsd)} $ · ${fmt(rates.toEur(view.perPayUsd))} €\n")
                    }
                    append("Jährlich: ${fmt(nominalV * info.coupon / 100, 2)} ${sym(ccy)}")
                    append(" · Marktwert: ${fmt(view.mvNative, 2)} ${sym(ccy)}")
                    append(if (quote != null) " (Kurs ${fmt(quote.price, 2)} %)" else " (noch kein Live-Kurs${if (manualV != null) ", manueller Kurs" else ""})")
                }
            )
        }
    }
}

@Composable
fun PasswordDialog(title: String, hint: String, confirmText: String, askRepeat: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val ok = pw.length >= 6 && (!askRepeat || pw == pw2)
    FormDialog(title, confirmText, ok, onDismiss, { onConfirm(pw.toCharArray()); onDismiss() }) {
        Text(hint)
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, label = { Text("Backup-Passwort") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        if (askRepeat) OutlinedTextField(
            value = pw2, onValueChange = { pw2 = it }, label = { Text("Passwort wiederholen") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        if (pw.isNotEmpty() && pw.length < 6) Text("Mindestens 6 Zeichen.")
        if (askRepeat && pw2.isNotEmpty() && pw != pw2) Text("Passwörter stimmen nicht überein.")
    }
}

/** Zahl ohne Tausendertrenner für Eingabefelder (Komma als Dezimaltrenner). */
private fun plain(v: Double): String {
    val s = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    return s.replace('.', ',')
}
