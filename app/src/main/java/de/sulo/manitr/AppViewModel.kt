package de.sulo.manitr

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class AppViewModel(app: Application) : AndroidViewModel(app) {

    data class Event(val message: String, val undo: (() -> Unit)? = null)

    private val vault = Vault(app)
    private val writer = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    var portfolio by mutableStateOf(Portfolio()); private set
    var market by mutableStateOf(Market()); private set
    var displayCcy by mutableStateOf("USD"); private set
    var refreshing by mutableStateOf(false); private set
    var errors by mutableStateOf<List<String>>(emptyList()); private set

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 8)

    val rates: Rates get() = Rates.from(market)

    init {
        vault.load()
            ?.let { runCatching { Json.storeFromJson(it) }.getOrNull() }
            ?.let { portfolio = it.portfolio; market = it.market; displayCcy = it.displayCcy }
    }

    private fun persist() {
        val json = Json.storeToJson(portfolio, market, displayCcy)
        viewModelScope.launch(writer) { runCatching { vault.save(json) } }
    }

    private fun update(block: (Portfolio) -> Portfolio) {
        portfolio = block(portfolio)
        persist()
    }

    fun changeDisplayCcy(c: String) { displayCcy = c; persist() }

    // ---------- Devisen ----------
    fun saveFx(id: Long?, code: String, amount: Double, label: String = "") = update { p ->
        val tag = label.trim().take(12)
        if (id == null) p.copy(fx = p.fx + FxHolding(p.nextId, code, amount, tag), nextId = p.nextId + 1)
        else p.copy(fx = p.fx.map { if (it.id == id) it.copy(code = code, amount = amount, label = tag) else it })
    }

    fun removeFx(id: Long) {
        val item = portfolio.fx.firstOrNull { it.id == id } ?: return
        update { p -> p.copy(fx = p.fx.filterNot { it.id == id }) }
        events.tryEmit(Event("${item.code}${if (item.label.isNotEmpty()) " " + item.label else ""} entfernt") { update { p -> p.copy(fx = p.fx + item) } })
    }

    // ---------- Gold ----------
    fun saveGold(id: Long?, type: String, amount: Double) = update { p ->
        if (id == null) p.copy(gold = p.gold + GoldHolding(p.nextId, type, amount), nextId = p.nextId + 1)
        else p.copy(gold = p.gold.map { if (it.id == id) it.copy(type = type, amount = amount) else it })
    }

    fun removeGold(id: Long) {
        val item = portfolio.gold.firstOrNull { it.id == id } ?: return
        update { p -> p.copy(gold = p.gold.filterNot { it.id == id }) }
        events.tryEmit(Event("${Presets.goldType(item.type).name} entfernt") { update { p -> p.copy(gold = p.gold + item) } })
    }

    // ---------- Bonds ----------
    /** Legt Stammdaten (per ISIN) an/aktualisiert sie und speichert den Bestand. */
    fun saveBond(id: Long?, info: BondInfo, nominal: Double) {
        update { p ->
            val infos = if (p.bondInfos.any { it.isin == info.isin }) p.bondInfos.map { if (it.isin == info.isin) info else it }
            else p.bondInfos + info
            if (id == null) p.copy(bondInfos = infos, bonds = p.bonds + BondHolding(p.nextId, info.isin, nominal), nextId = p.nextId + 1)
            else p.copy(bondInfos = infos, bonds = p.bonds.map { if (it.id == id) it.copy(nominal = nominal) else it })
        }
        if (market.bondQuotes[info.isin] == null) refresh()
    }

    fun removeBond(id: Long) {
        val item = portfolio.bonds.firstOrNull { it.id == id } ?: return
        val name = portfolio.info(item.isin)?.name ?: item.isin
        update { p -> p.copy(bonds = p.bonds.filterNot { it.id == id }) }
        events.tryEmit(Event("$name entfernt") { update { p -> p.copy(bonds = p.bonds + item) } })
    }

    // ---------- Sonstiges ----------
    fun saveMisc(id: Long?, name: String, category: String, currency: String, amount: Double) = update { p ->
        if (id == null) p.copy(misc = p.misc + MiscHolding(p.nextId, name, category, currency, amount), nextId = p.nextId + 1)
        else p.copy(misc = p.misc.map { if (it.id == id) it.copy(name = name, category = category, currency = currency, amount = amount) else it })
    }

    fun removeMisc(id: Long) {
        val item = portfolio.misc.firstOrNull { it.id == id } ?: return
        update { p -> p.copy(misc = p.misc.filterNot { it.id == id }) }
        events.tryEmit(Event("${item.name} entfernt") { update { p -> p.copy(misc = p.misc + item) } })
    }

    // ---------- Mieten ----------
    fun saveRent(id: Long?, property: String, rentTry: Double) = update { p ->
        if (id == null) p.copy(rents = p.rents + RentHolding(p.nextId, property, rentTry), nextId = p.nextId + 1)
        else p.copy(rents = p.rents.map { if (it.id == id) it.copy(property = property, rentTry = rentTry) else it })
    }

    fun removeRent(id: Long) {
        val item = portfolio.rents.firstOrNull { it.id == id } ?: return
        update { p -> p.copy(rents = p.rents.filterNot { it.id == id }) }
        events.tryEmit(Event("${item.property} entfernt") { update { p -> p.copy(rents = p.rents + item) } })
    }

    // ---------- Kurse ----------
    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                val res = MarketData.refreshAll(portfolio, market)
                market = res.market
                errors = res.errors
                persist()
                events.tryEmit(
                    Event(
                        if (res.errors.isEmpty()) "Kurse aktualisiert"
                        else "Aktualisiert – ${res.errors.size} Quelle(n) nicht erreichbar"
                    )
                )
            } finally {
                refreshing = false
            }
        }
    }

    fun refreshIfStale() {
        val u = market.updatedAt
        if (u == null || System.currentTimeMillis() - u > 5 * 60_000) refresh()
    }

    // ---------- Backup ----------
    fun exportBackup(uri: Uri, password: CharArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = runCatching {
                val enc = BackupCrypto.encrypt(Json.backupToJson(portfolio, displayCcy).toByteArray(Charsets.UTF_8), password)
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(enc) }
                "Verschlüsseltes Backup gespeichert"
            }.getOrElse { "Backup fehlgeschlagen: ${it.message}" }
            events.tryEmit(Event(msg))
        }
    }

    fun importBackup(uri: Uri, password: CharArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val plain = BackupCrypto.decrypt(bytes, password)
                Json.backupFromJson(String(plain, Charsets.UTF_8))
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { (p, ccy) ->
                    val oldP = portfolio
                    val oldCcy = displayCcy
                    portfolio = p
                    displayCcy = ccy
                    persist()
                    events.tryEmit(Event("Backup eingespielt") { portfolio = oldP; displayCcy = oldCcy; persist() })
                    refresh()
                }.onFailure { events.tryEmit(Event("Import fehlgeschlagen: ${it.message}")) }
            }
        }
    }
}
