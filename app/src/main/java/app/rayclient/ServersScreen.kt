@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package app.rayclient

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.rotate
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri

@Composable
internal fun MainActivity.ServersTab() {
    var confirmDel by remember { mutableStateOf<Sub?>(null) }
    var infoSub by remember { mutableStateOf<Sub?>(null) }
    val countries = Countries.available(AppState.servers.map { it.name })
    val countryNow = AppState.countryFilter.takeIf { code ->
        countries.size >= 2 && countries.any { it.first == code }
    }.orEmpty()
    fun prep(list: List<Server>) = list.filter { AppState.query.isBlank() || it.name.contains(AppState.query, true) }
        .filter { countryNow.isEmpty() || Countries.flagCode(it.name) == countryNow }
        .let { l -> if (AppState.sortRating) l.sortedByDescending { s -> Health.rank(s.link, AppState.pings[s.id]) } else if (AppState.sortPing) l.sortedBy { s -> AppState.pings[s.id]?.takeIf { it > 0 } ?: Int.MAX_VALUE } else l }
    val favList = prep(AppState.servers.filter { it.link in AppState.favs })
    val manual = if (AppState.onlyFav) emptyList() else prep(AppState.servers.filter { it.subId == null && it.link !in AppState.favs })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = LocalTabBottom.current)) {
        item {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Серверы", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton({ AppState.refreshAllSubs() }, enabled = AppState.subs.isNotEmpty()) {
                    if (AppState.subBusy > 0) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(RayIcons.Refresh, "Обновить подписки") }
                FilledIconButton({ AppState.showAdd = true }) { Icon(RayIcons.Plus, "Добавить") }
            }
            OutlinedTextField(AppState.query, { AppState.query = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                placeholder = { Text("Поиск по названию") }, leadingIcon = { Icon(RayIcons.Search, null) },
                trailingIcon = { if (AppState.query.isNotEmpty()) IconButton({ AppState.query = "" }) { Icon(RayIcons.Close, "Очистить") } },
                shape = RoundedCornerShape(10.dp))
            if (RayVpnService.connected && !RayVpnService.switching && !RayVpnService.busy && !AppState.quickBusy && !RayVpnService.blocked) {
                Text("Нажмите на другой сервер для переключения без отключения VPN.",
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (RayVpnService.switching || RayVpnService.busy || AppState.quickBusy || RayVpnService.blocked) {
                Text("Дождитесь завершения переключения или подключения, прежде чем выбирать другой сервер.",
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Segmented(listOf("Все", "Избранное"), if (AppState.onlyFav) 1 else 0,
                Modifier.padding(top = 10.dp)) { AppState.onlyFav = it == 1 }
            FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (countries.size >= 2) {
                    var countryMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { countryMenu = true }) {
                            Text(if (countryNow.isEmpty()) "Все страны" else Countries.title(countryNow, I18n.locale))
                            Icon(RayIcons.Chevron, null, Modifier.size(16.dp).rotate(90f))
                        }
                        DropdownMenu(countryMenu, { countryMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Все страны") },
                                onClick = { AppState.countryFilter = ""; countryMenu = false },
                                trailingIcon = if (countryNow.isEmpty()) ({ Icon(RayIcons.Check, null) }) else null
                            )
                            countries.forEach { (code, count) ->
                                DropdownMenuItem(
                                    text = { Text(Countries.flag(code) + " " + Countries.title(code, I18n.locale) + " · " + count) },
                                    onClick = { AppState.countryFilter = code; countryMenu = false },
                                    trailingIcon = if (countryNow == code) ({ Icon(RayIcons.Check, null) }) else null
                                )
                            }
                        }
                    }
                }
                var sortMenu by remember { mutableStateOf(false) }
                val sortLabels = listOf("По умолчанию", "Пинг ↑", "Рейтинг")
                val sortIndex = if (AppState.sortRating) 2 else if (AppState.sortPing) 1 else 0
                Box {
                    TextButton(onClick = { sortMenu = true }) {
                        Text(if (sortIndex == 0) "Сортировка" else sortLabels[sortIndex])
                        Icon(RayIcons.Chevron, null, Modifier.size(16.dp).rotate(90f))
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        sortLabels.forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    AppState.sortPing = index == 1
                                    AppState.sortRating = index == 2
                                    AppState.save()
                                    sortMenu = false
                                },
                                trailingIcon = if (sortIndex == index) ({ Icon(RayIcons.Check, null) }) else null
                            )
                        }
                    }
                }
                TextButton(onClick = { AppState.pingAll() }, enabled = AppState.servers.isNotEmpty() && !AppState.pingBusy) {
                    if (AppState.pingBusy) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Замерить")
                }
            }
        }
        if (favList.isNotEmpty()) {
            item { GroupTitle("Избранное") }
            items(favList, key = { "fav-" + it.id }) { ServerRow(it, canDelete = it.subId == null, modifier = Modifier.animateItem()) }
        }
        if (manual.isNotEmpty()) {
            item { GroupTitle("Свои серверы") }
            items(manual, key = { it.id }) { ServerRow(it, canDelete = true, modifier = Modifier.animateItem()) }
        }
        // Show recovery for a filter with zero matches, distinct from having no servers.
        val filteredEmpty = AppState.servers.isNotEmpty() &&
            (if (AppState.onlyFav) favList.isEmpty() else prep(AppState.servers).isEmpty())
        if (filteredEmpty) item {
            Panel(Modifier.padding(top = 24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("По выбранным фильтрам серверов нет", style = MaterialTheme.typography.titleMedium)
                    Text("Измените поиск или сбросьте фильтры.", Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(
                        onClick = { AppState.query = ""; AppState.countryFilter = ""; AppState.onlyFav = false },
                        modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)
                    ) { Text("Сбросить фильтры") }
                }
            }
        }
        if (AppState.servers.isEmpty()) item {
            Panel(Modifier.padding(top = 24.dp)) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Пока пусто", style = MaterialTheme.typography.titleMedium)
                Text("Добавьте ссылку на подписку или сервер кнопкой «+».", Modifier.padding(top = 4.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        if (!AppState.onlyFav) AppState.subs.forEach { sub ->
            val list = prep(AppState.servers.filter { it.subId == sub.id && it.link !in AppState.favs })
            item(key = "sub-${sub.id}") {
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(sub.name, style = MaterialTheme.typography.titleSmall)
                            val upd = if (sub.updated == 0L) "обновления ещё не было" else "обновлено " + java.text.SimpleDateFormat("d MMM, HH:mm", I18n.locale).format(java.util.Date(sub.updated))
                            Text("${AppState.servers.count { it.subId == sub.id }} серверов · $upd", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton({ menu = true }) { Icon(RayIcons.More, "Меню подписки") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Обновить") }, { menu = false; AppState.refreshSub(sub) })
                                DropdownMenuItem({ Text(if (sub.json) "Авто-серверы (JSON): выключить" else "Авто-серверы (JSON): включить") }, { menu = false; AppState.setJson(sub, !sub.json) })
                                DropdownMenuItem({ Text("Сведения и поддержка") }, { menu = false; infoSub = sub })
                                DropdownMenuItem({ Text("Копировать адрес") }, { menu = false; copy(sub.url) })
                                DropdownMenuItem({ Text("Удалить подписку", color = Pal.Red) }, { menu = false; confirmDel = sub })
                            }
                        }
                    }
                    if (sub.total > 0) Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                        val gb = 1024.0 * 1024 * 1024
                        UsageBar((sub.used.toDouble() / sub.total).toFloat())
                        Text("%.1f из %.0f ГБ".format(sub.used / gb, sub.total / gb) +
                            (if (sub.expire > 0) " · до " + java.text.SimpleDateFormat("d MMM yyyy", I18n.locale).format(java.util.Date(sub.expire * 1000)) else ""),
                            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(list, key = { it.id }) { ServerRow(it, canDelete = false, modifier = Modifier.animateItem()) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    confirmDel?.let { s ->
        AlertDialog(onDismissRequest = { confirmDel = null }, title = { Text("Удалить подписку?") },
            text = { Text("Серверы подписки (${AppState.servers.count { it.subId == s.id }}) тоже пропадут из списка.") },
            confirmButton = { TextButton({ AppState.deleteSub(s); confirmDel = null }) { Text("Удалить", color = Pal.Red) } },
            dismissButton = { TextButton({ confirmDel = null }) { Text("Отмена") } })
    }
    infoSub?.let { s ->
        AlertDialog(onDismissRequest = { infoSub = null }, title = { Text(s.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(s.announce.trim().ifBlank { "Объявлений от сервиса нет." }, style = MaterialTheme.typography.bodyMedium)
                val dateFmt = java.text.SimpleDateFormat("d MMM yyyy", I18n.locale)
                if (s.refill > 0) Text("Сброс трафика: " + dateFmt.format(java.util.Date(s.refill * 1000)), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.intervalH > 0) Text("Сервис просит обновлять подписку раз в ${s.intervalH} ч.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.fallback.isNotBlank()) Text("Запасной адрес: ${hostOf(s.fallback)}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { Row {
                if (s.page.isNotBlank()) TextButton({ runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.page))) } }) { Text("Страница подписки") }
                if (SubMetaParser.httpsUrl(s.support) != null) TextButton({ runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.support))) } }) { Text("Поддержка") }
            } },
            dismissButton = { TextButton({ infoSub = null }) { Text("Закрыть") } })
    }
}

@Composable
internal fun MainActivity.ServerRow(s: Server, canDelete: Boolean, modifier: Modifier = Modifier) {
    val selected = AppState.selectedId == s.id
    val fav = s.link in AppState.favs
    val (flag, nm) = remember(s.name) { splitFlag(s.name) }
    val protocols = chipsOf(s.link).joinToString(" · ") { I18n.tr(it.first) }
    var menu by remember { mutableStateOf(false) }
    var selectionBlocked by remember(s.id) { mutableStateOf(false) }
    var qrAsk by remember { mutableStateOf(false) }; var qrShow by remember { mutableStateOf(false) }
    if (selectionBlocked) ServerChoiceBlockedDialog { selectionBlocked = false }
    if (qrAsk) AlertDialog(onDismissRequest = { qrAsk = false }, title = { Text("Показать QR-код сервера?") },
        text = { Text("В QR-коде ключи этого сервера. Любой, кто его отсканирует или сфотографирует, сможет пользоваться сервером. Показывайте только тому, кому доверяете, и закройте окно сразу после сканирования.") },
        confirmButton = { TextButton({ qrAsk = false; qrShow = true }) { Text("Показать") } }, dismissButton = { TextButton({ qrAsk = false }) { Text("Отмена") } })
    if (qrShow) QrDialog(s.name, s.link) { qrShow = false }
    val shape = RoundedCornerShape(10.dp)
    val scheme = MaterialTheme.colorScheme
    val ping = AppState.pings[s.id]
    Box(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(shape)
            .background(if (selected) scheme.secondaryContainer else Color.Transparent)
            .semantics { this.selected = selected }
            .combinedClickable(
                onClickLabel = "Выбрать сервер",
                onLongClickLabel = "Действия с сервером",
                role = Role.Button,
                onClick = {
                    if (chooseServerFromUi(s.id) == ServerChoiceDecision.REQUIRE_DISCONNECT) selectionBlocked = true
                },
                onLongClick = { menu = true }
            )
            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) { Text(flag, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(nm, style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val desc = remember(s.link) { Links.serverDescription(s.link) }
                if (desc != null) Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(protocols, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant)
                val st = remember(HealthUi.version, s.link) { Health.stats(s.link) }
                if (st != null && st.n >= 3) {
                    Text("${(st.uptime * 100).roundToInt()}% за сутки" +
                        (if (st.medianMs > 0 && ping == null) " · ${st.medianMs} мс" else ""),
                        Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                val note = AppState.pingNotes[s.id]
                if ((ping ?: 0) < 0 && ping != -2 && !note.isNullOrBlank()) Text(note, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = Pal.Red.copy(alpha = 0.9f), maxLines = 2)
            }
            ping?.let { p ->
                val c = if (p == -2) MaterialTheme.colorScheme.onSurfaceVariant else if (p < 0) Pal.Red else pingColor(p)
                Text(if (p == -2) "…" else if (p < 0) "нет" else "$p мс",
                    Modifier.padding(start = 8.dp), color = c,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), maxLines = 1)
            }
            IconButton({ if (fav) AppState.favs.remove(s.link) else AppState.favs.add(s.link); AppState.save() }) {
                Icon(if (fav) RayIcons.StarFilled else RayIcons.Star, if (fav) "Убрать из избранного" else "Добавить в избранное", Modifier.size(22.dp), tint = if (fav) Pal.Amber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) }
        }
        DropdownMenu(menu, { menu = false }) {
            if (!s.isAuto) DropdownMenuItem({ Text("Копировать адрес") }, { menu = false; copy(s.link) })
            if (!s.isAuto) DropdownMenuItem({ Text("Показать QR-код") }, { menu = false; qrAsk = true })
            DropdownMenuItem({ Text(if (fav) "Убрать из избранного" else "В избранное") }, { menu = false; if (fav) AppState.favs.remove(s.link) else AppState.favs.add(s.link); AppState.save() })
            if (canDelete) DropdownMenuItem({ Text("Удалить", color = Pal.Red) }, { menu = false; AppState.delete(s) })
        }
    }
}

@Composable
internal fun MainActivity.AddDialog(onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Добавить") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Адрес подписки, выданный панелью или ботом, либо ссылки серверов: vless://, vmess://, trojan://, ss://, hy2://. Можно сразу несколько, по одной в строке.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Название, если нужно") })
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Ссылка") })
            TextButton({ text = clipboardText() }) { Text("Взять из буфера") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ onClose(); scanCamera() }, Modifier.weight(1f)) { Text("QR камерой") }
                FilledTonalButton({ onClose(); scanImage() }, Modifier.weight(1f)) { Text("QR из фото") }
            }
        } },
        confirmButton = { TextButton({ AppState.addInput(text, name); onClose() }) { Text("Добавить") } },
        dismissButton = { TextButton(onClose) { Text("Отмена") } })
}

// ================================================================ Маршруты

