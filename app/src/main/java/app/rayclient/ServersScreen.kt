@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package app.rayclient

import android.content.Intent
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    val internetAvailable = rememberServersInternetAvailable()
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
            RayScreenHeader(I18n.tr("Серверы"), Modifier.padding(top = 16.dp)) {
                IconButton(
                    { AppState.refreshAllSubs() },
                    enabled = AppState.subs.isNotEmpty() && internetAvailable && AppState.subBusy == 0
                ) {
                    if (AppState.subBusy > 0) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(RayIcons.Refresh, I18n.tr("Обновить подписки"))
                }
                FilledIconButton({ AppState.showAdd = true }) { Icon(RayIcons.Plus, I18n.tr("Добавить")) }
            }
            OutlinedTextField(AppState.query, { AppState.query = it }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
                placeholder = { Text(I18n.tr("Поиск по названию")) }, leadingIcon = { Icon(RayIcons.Search, null) },
                trailingIcon = { if (AppState.query.isNotEmpty()) IconButton({ AppState.query = "" }) { Icon(RayIcons.Close, I18n.tr("Очистить")) } },
                shape = RoundedCornerShape(10.dp))
            if (!internetAvailable) {
                RayInlineNotice(
                    I18n.tr("Нет подключения к интернету. Сохранённый список доступен; обновление и замер появятся после восстановления сети."),
                    Modifier.padding(top = 10.dp)
                )
            }
            if (AppState.subBusy > 0) {
                RayInlineNotice(I18n.tr("Обновление подписок…"), Modifier.padding(top = 8.dp))
            }
            if (AppState.pingBusy) {
                RayInlineNotice(I18n.tr("Измерение задержки серверов…"), Modifier.padding(top = 8.dp))
            }
            if (RayVpnService.connected && !RayVpnService.switching && !RayVpnService.busy && !AppState.quickBusy && !RayVpnService.blocked) {
                RayInlineNotice(
                    I18n.tr("Нажмите на другой сервер для переключения без отключения VPN."),
                    Modifier.padding(top = 10.dp)
                )
            }
            if (RayVpnService.switching || RayVpnService.busy || AppState.quickBusy || RayVpnService.blocked) {
                RayInlineNotice(
                    I18n.tr("Дождитесь завершения переключения или подключения, прежде чем выбирать другой сервер."),
                    Modifier.padding(top = 10.dp)
                )
            }
            Segmented(listOf("Все", "Избранное").map(I18n::tr), if (AppState.onlyFav) 1 else 0,
                Modifier.padding(top = 10.dp)) { AppState.onlyFav = it == 1 }
            FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (countries.size >= 2) {
                    var countryMenu by remember { mutableStateOf(false) }
                    Box {
                        FilledTonalButton(
                            onClick = { countryMenu = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(I18n.tr(if (countryNow.isEmpty()) "Все страны" else Countries.title(countryNow, I18n.locale)),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(RayIcons.Chevron, null, Modifier.padding(start = 4.dp).size(16.dp).rotate(90f))
                        }
                        DropdownMenu(countryMenu, { countryMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(I18n.tr("Все страны")) },
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
                    FilledTonalButton(
                        onClick = { sortMenu = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(I18n.tr(if (sortIndex == 0) "Сортировка" else sortLabels[sortIndex]),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(RayIcons.Chevron, null, Modifier.padding(start = 4.dp).size(16.dp).rotate(90f))
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        sortLabels.forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { Text(I18n.tr(title)) },
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
                FilledTonalButton(
                    onClick = { AppState.pingAll() },
                    enabled = AppState.servers.isNotEmpty() && !AppState.pingBusy && internetAvailable,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (AppState.pingBusy) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(I18n.tr("Замерить"))
                }
            }
        }
        if (favList.isNotEmpty()) {
            item { GroupTitle(I18n.tr("Избранное")) }
            items(favList, key = { "fav-" + it.id }) { ServerRow(it, canDelete = it.subId == null, modifier = Modifier.animateItem()) }
        }
        if (manual.isNotEmpty()) {
            item { GroupTitle(I18n.tr("Свои серверы")) }
            items(manual, key = { it.id }) { ServerRow(it, canDelete = true, modifier = Modifier.animateItem()) }
        }
        // Show recovery for a filter with zero matches, distinct from having no servers.
        val filteredEmpty = AppState.servers.isNotEmpty() &&
            (if (AppState.onlyFav) favList.isEmpty() else prep(AppState.servers).isEmpty())
        if (filteredEmpty) item {
            Panel(Modifier.padding(top = 24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(I18n.tr("По выбранным фильтрам серверов нет"), style = MaterialTheme.typography.titleMedium)
                    Text(I18n.tr("Измените поиск или сбросьте фильтры."), Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(
                        onClick = { AppState.query = ""; AppState.countryFilter = ""; AppState.onlyFav = false },
                        modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)
                    ) { Text(I18n.tr("Сбросить фильтры")) }
                }
            }
        }
        if (AppState.servers.isEmpty()) item {
            if (AppState.subs.isEmpty()) {
                RayEmptyState(
                    title = I18n.tr("Пока пусто"),
                    message = I18n.tr("Добавьте ссылку на подписку или сервер кнопкой «+»."),
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                RayEmptyState(
                    title = I18n.tr("Серверов пока нет"),
                    message = I18n.tr("Обновите подписки, чтобы загрузить серверы."),
                    modifier = Modifier.padding(top = 24.dp),
                    actionLabel = I18n.tr("Обновить подписки"),
                    actionEnabled = AppState.subBusy == 0,
                    loading = AppState.subBusy > 0,
                    onAction = { AppState.refreshAllSubs() }
                )
            }
        }
        if (!AppState.onlyFav) AppState.subs.forEach { sub ->
            val matchingServers = prep(AppState.servers.filter { it.subId == sub.id })
            val list = matchingServers.filter { it.link !in AppState.favs }
            if (matchingServers.isNotEmpty()) {
                item(key = "sub-${sub.id}") {
                    var menu by remember { mutableStateOf(false) }
                    Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                Text(sub.name, style = MaterialTheme.typography.titleSmall)
                                val upd = if (sub.updated == 0L) I18n.tr("обновления ещё не было") else I18n.tr("обновлено " + java.text.SimpleDateFormat("d MMM, HH:mm", I18n.locale).format(java.util.Date(sub.updated)))
                                Text(I18n.tr("${AppState.servers.count { it.subId == sub.id }} серверов · $upd"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton({ menu = true }) { Icon(RayIcons.More, I18n.tr("Меню подписки")) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem({ Text(I18n.tr("Обновить")) }, { menu = false; AppState.refreshSub(sub) })
                                    DropdownMenuItem({ Text(I18n.tr(if (sub.json) "Авто-серверы (JSON): выключить" else "Авто-серверы (JSON): включить")) }, { menu = false; AppState.setJson(sub, !sub.json) })
                                    DropdownMenuItem({ Text(I18n.tr("Сведения и поддержка")) }, { menu = false; infoSub = sub })
                                    DropdownMenuItem({ Text(I18n.tr("Копировать адрес")) }, { menu = false; copy(sub.url) })
                                    DropdownMenuItem({ Text(I18n.tr("Удалить подписку"), color = Pal.Red) }, { menu = false; confirmDel = sub })
                                }
                            }
                        }
                        if (sub.total > 0) Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                            val gb = 1024.0 * 1024 * 1024
                            UsageBar((sub.used.toDouble() / sub.total).toFloat())
                            Text(I18n.tr("%.1f из %.0f ГБ".format(sub.used / gb, sub.total / gb) +
                                (if (sub.expire > 0) " · до " + java.text.SimpleDateFormat("d MMM yyyy", I18n.locale).format(java.util.Date(sub.expire * 1000)) else "")),
                                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(list, key = { it.id }) { ServerRow(it, canDelete = false, modifier = Modifier.animateItem()) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    confirmDel?.let { s ->
        RayConfirmDialog(
            title = I18n.tr("Удалить подписку?"),
            message = I18n.tr("Серверы подписки (${AppState.servers.count { it.subId == s.id }}) тоже пропадут из списка."),
            confirmLabel = I18n.tr("Удалить"),
            destructive = true,
            onConfirm = { AppState.deleteSub(s); confirmDel = null },
            onDismiss = { confirmDel = null }
        )
    }
    infoSub?.let { s ->
        RayDialogFrame(
            title = s.name,
            onDismiss = { infoSub = null },
            content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(I18n.tr(s.announce.trim().ifBlank { "Объявлений от сервиса нет." }), style = MaterialTheme.typography.bodyMedium)
                    val dateFmt = java.text.SimpleDateFormat("d MMM yyyy", I18n.locale)
                    if (s.refill > 0) Text(I18n.tr("Сброс трафика: {0}".replace("{0}", dateFmt.format(java.util.Date(s.refill * 1000)))), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.intervalH > 0) Text(I18n.tr("Сервис просит обновлять подписку раз в {0} ч.".replace("{0}", s.intervalH.toString())), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.fallback.isNotBlank()) Text(I18n.tr("Запасной адрес: {0}".replace("{0}", hostOf(s.fallback))), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Row {
                if (s.page.isNotBlank()) TextButton({ runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.page))) } }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Страница подписки")) }
                if (SubMetaParser.httpsUrl(s.support) != null) TextButton({ runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.support))) } }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Поддержка")) }
            } },
            dismissButton = { TextButton({ infoSub = null }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Закрыть")) } }
        )
    }
}

@Composable
internal fun MainActivity.ServerRow(s: Server, canDelete: Boolean, modifier: Modifier = Modifier) {
    val selected = AppState.selectedId == s.id
    val fav = s.link in AppState.favs
    val (flag, nm) = remember(s.name) { splitFlag(s.name) }
    val protocols = chipsOf(s.link).joinToString(" · ") { I18n.tr(it.first) }
    val subscriptionName = s.subId?.let { id -> AppState.subs.firstOrNull { it.id == id }?.name }
    var menu by remember { mutableStateOf(false) }
    var selectionBlocked by remember(s.id) { mutableStateOf(false) }
    var qrAsk by remember { mutableStateOf(false) }; var qrShow by remember { mutableStateOf(false) }
    if (selectionBlocked) ServerChoiceBlockedDialog { selectionBlocked = false }
    if (qrAsk) RayConfirmDialog(
        title = I18n.tr("Показать QR-код сервера?"),
        message = I18n.tr("В QR-коде ключи этого сервера. Любой, кто его отсканирует или сфотографирует, сможет пользоваться сервером. Показывайте только тому, кому доверяете, и закройте окно сразу после сканирования."),
        confirmLabel = I18n.tr("Показать"),
        onConfirm = { qrAsk = false; qrShow = true },
        onDismiss = { qrAsk = false }
    )
    if (qrShow) QrDialog(s.name, s.link) { qrShow = false }
    val shape = RoundedCornerShape(10.dp)
    val scheme = MaterialTheme.colorScheme
    val ping = AppState.pings[s.id]
    Box(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape)
            .background(if (selected) scheme.secondaryContainer else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, scheme.primary.copy(alpha = 0.42f), shape) else Modifier)
            .semantics { this.selected = selected }
            .combinedClickable(
                onClickLabel = I18n.tr("Выбрать сервер"),
                onLongClickLabel = I18n.tr("Действия с сервером"),
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
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                val desc = remember(s.link) { Links.serverDescription(s.link) }
                if (desc != null) Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subscriptionName.isNullOrBlank()) Text(
                    I18n.tr("Подписка") + ": " + subscriptionName,
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(protocols, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant)
                val st = remember(HealthUi.version, s.link) { Health.stats(s.link) }
                if (st != null && st.n >= 3) {
                    Text(I18n.tr("${(st.uptime * 100).roundToInt()}% за сутки" +
                        (if (st.medianMs > 0 && ping == null) " · ${st.medianMs} мс" else "")),
                        Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                val note = AppState.pingNotes[s.id]
                if ((ping ?: 0) < 0 && ping != -2 && !note.isNullOrBlank()) {
                    Text(I18n.tr(note), Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall,
                        color = Pal.Red.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (ping != null) {
                    val pingColor = if (ping == -2) scheme.onSurfaceVariant else if (ping < 0) Pal.Red else pingColor(ping)
                    Text(
                        I18n.tr(if (ping == -2) "Замер…" else if (ping < 0) "Нет ответа" else "Задержка · $ping мс"),
                        Modifier.padding(top = 4.dp),
                        color = pingColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(RayIcons.Check, I18n.tr("Выбран"), Modifier.padding(start = 8.dp).size(17.dp), tint = scheme.primary)
            }
            IconButton({ if (fav) AppState.favs.remove(s.link) else AppState.favs.add(s.link); AppState.save() }) {
                Icon(if (fav) RayIcons.StarFilled else RayIcons.Star, I18n.tr(if (fav) "Убрать из избранного" else "В избранное"), Modifier.size(22.dp), tint = if (fav) Pal.Amber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            }
            IconButton(onClick = { menu = true }) {
                Icon(RayIcons.More, I18n.tr("Действия с сервером"))
            }
        }
        DropdownMenu(menu, { menu = false }) {
            if (!s.isAuto) DropdownMenuItem({ Text(I18n.tr("Копировать адрес")) }, { menu = false; copy(s.link) })
            if (!s.isAuto) DropdownMenuItem({ Text(I18n.tr("Показать QR-код")) }, { menu = false; qrAsk = true })
            DropdownMenuItem({ Text(I18n.tr(if (fav) "Убрать из избранного" else "В избранное")) }, { menu = false; if (fav) AppState.favs.remove(s.link) else AppState.favs.add(s.link); AppState.save() })
            if (canDelete) DropdownMenuItem({ Text(I18n.tr("Удалить"), color = Pal.Red) }, { menu = false; AppState.delete(s) })
        }
    }
}

@Composable
internal fun MainActivity.AddDialog(onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    RayDialogFrame(
        title = I18n.tr("Добавить"),
        onDismiss = onClose,
        content = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(I18n.tr("Адрес подписки, выданный панелью или ботом, либо ссылки серверов: vless://, vmess://, trojan://, ss://, hy2://. Можно сразу несколько, по одной в строке."),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(I18n.tr("Название, если нужно")) })
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, label = { Text(I18n.tr("Ссылка")) })
                TextButton({ text = clipboardText() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(I18n.tr("Взять из буфера")) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ onClose(); scanCamera() }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(I18n.tr("QR камерой")) }
                    FilledTonalButton({ onClose(); scanImage() }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(I18n.tr("QR из фото")) }
                }
            }
        },
        confirmButton = { TextButton({ AppState.addInput(text, name); onClose() }, modifier = Modifier.heightIn(min = 48.dp), enabled = text.isNotBlank()) { Text(I18n.tr("Добавить")) } },
        dismissButton = { TextButton(onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Отмена")) } }
    )

}

// ================================================================ Маршруты



/** Observe validated Internet connectivity without polling or initiating a network request. */
@Composable
private fun rememberServersInternetAvailable(): Boolean {
    val context = LocalContext.current
    var available by remember(context) { mutableStateOf(hasValidatedInternet(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available = hasValidatedInternet(context)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                available = hasValidatedInternet(context)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return available && !(Demo.active && Demo.networkUnavailable)
}

private fun hasValidatedInternet(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val network = manager.activeNetwork ?: return@runCatching false
    val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}.getOrDefault(false)

