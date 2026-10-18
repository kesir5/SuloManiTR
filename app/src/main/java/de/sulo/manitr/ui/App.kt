package de.sulo.manitr.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import de.sulo.manitr.AppViewModel
import de.sulo.manitr.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val label: String, val icon: ImageVector, val title: String, val fab: String?) {
    HOME("Übersicht", NavIcons.Overview, "Sülo ManiTR", null),
    FX("Devisen", NavIcons.Euro, "Devisen", "Devise hinzufügen"),
    GOLD("Gold", NavIcons.GoldBars, "Gold", "Gold hinzufügen"),
    BONDS("Bonds", NavIcons.Bonds, "Eurobonds Türkei", "Eurobond hinzufügen"),
    MISC("Misc.", NavIcons.Misc, "Sonstige Vermögenswerte", "Vermögenswert hinzufügen"),
}

/** Untere Leiste mit doppelt so großen Icons (36 dp) */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFEEF2F8)).navigationBarsPadding().padding(vertical = 8.dp)) {
        Tab.values().forEach { t ->
            val sel = t == selected
            Column(Modifier.weight(1f).clickable { onSelect(t) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(width = 68.dp, height = 44.dp).clip(RoundedCornerShape(22.dp)).background(if (sel) C.PriC else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, contentDescription = t.label, modifier = Modifier.size(36.dp), tint = if (sel) C.Navy else C.Mute)
                }
                Text(t.label, fontSize = 11.sp, color = if (sel) C.Navy else C.Mute, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

private data class DialogReq(val tab: String, val id: Long?)

@Composable
fun AppRoot(vm: AppViewModel, onLock: () -> Unit) {
    val tabs = Tab.values()
    val pager = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()
    val tab = tabs[pager.currentPage]
    var dialogTab by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pwExport by remember { mutableStateOf(false) }
    var pwImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPw by remember { mutableStateOf<CharArray?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.refreshIfStale() }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            val r = snackbar.showSnackbar(e.message, actionLabel = if (e.undo != null) "Rückgängig" else null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) e.undo?.invoke()
        }
    }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = pendingExportPw
        pendingExportPw = null
        if (uri != null && pw != null) vm.exportBackup(uri, pw)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pwImportUri = uri }

    fun openDialog(t: Tab, id: Long?) { dialogTab = t.name; dialogId = id }

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tab.title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (vm.refreshing) Color(0xFFF2B33D) else if (vm.rates.live) Color(0xFF2ECC71) else Color(0xFFBA1A1A)))
                        Muted(
                            "  " + when {
                                vm.refreshing -> "Aktualisiere …"
                                vm.market.updatedAt != null -> "Live · Stand " + SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(vm.market.updatedAt!!))
                                else -> "Noch keine Kursdaten"
                            }
                        )
                    }
                }
                Box {
                    Image(
                        painterResource(R.drawable.profile), contentDescription = "Menü",
                        modifier = Modifier.size(42.dp).border(2.dp, Color.White, CircleShape).clip(CircleShape).clickable { menu = true },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("↻  Kurse aktualisieren") }, onClick = { menu = false; vm.refresh() })
                        DropdownMenuItem(text = { Text("⬇  Backup exportieren (verschlüsselt)") }, onClick = { menu = false; pwExport = true })
                        DropdownMenuItem(text = { Text("⬆  Backup importieren") }, onClick = { menu = false; openDoc.launch(arrayOf("*/*")) })
                        DropdownMenuItem(text = { Text("🔒  App sperren") }, onClick = { menu = false; onLock() })
                    }
                }
            }
        },
        bottomBar = { BottomBar(tab) { t -> scope.launch { pager.animateScrollToPage(t.ordinal) } } },
        floatingActionButton = {
            tab.fab?.let { label ->
                ExtendedFloatingActionButton(
                    onClick = { openDialog(tab, null) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(label) },
                    containerColor = C.Pri, contentColor = Color.White,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
                when (tabs[page]) {
                    Tab.HOME -> OverviewScreen(vm)
                    Tab.FX -> FxScreen(vm) { openDialog(Tab.FX, it) }
                    Tab.GOLD -> GoldScreen(vm) { openDialog(Tab.GOLD, it) }
                    Tab.BONDS -> BondsScreen(vm) { openDialog(Tab.BONDS, it) }
                    Tab.MISC -> MiscScreen(vm, { openDialog(Tab.MISC, it) }, { dialogTab = "RENT"; dialogId = it })
                }
            }
        }
    }

    // ---- Dialoge ----
    val close = { dialogTab = null; dialogId = null }
    when (dialogTab) {
        Tab.FX.name -> FxDialog(vm, dialogId, close)
        Tab.GOLD.name -> GoldDialog(vm, dialogId, close)
        Tab.BONDS.name -> BondDialog(vm, dialogId, close)
        Tab.MISC.name -> MiscDialog(vm, dialogId, close)
        "RENT" -> RentDialog(vm, dialogId, close)
    }
    if (pwExport) {
        PasswordDialog(
            "Backup exportieren", "Die Datei wird mit diesem Passwort verschlüsselt (AES-256). Ohne Passwort ist sie nicht lesbar – bitte gut merken.",
            "Weiter", askRepeat = true, onDismiss = { pwExport = false },
        ) { pw ->
            pendingExportPw = pw
            createDoc.launch("sulo-manitr-backup.sulobk")
        }
    }
    pwImportUri?.let { uri ->
        PasswordDialog(
            "Backup importieren", "Achtung: Der aktuelle Bestand wird ersetzt (Rückgängig direkt danach möglich).",
            "Importieren", askRepeat = false, onDismiss = { pwImportUri = null },
        ) { pw -> vm.importBackup(uri, pw) }
    }
}
