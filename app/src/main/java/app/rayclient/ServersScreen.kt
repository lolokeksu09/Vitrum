@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.horizontalScroll
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    // фильтр страны действует, только пока такая страна есть в списке и чипы стран показаны (их две и больше)
    val codes = Countries.available(AppState.servers.map { it.name }).map { it.first }
    val countryNow = AppState.countryFilter.takeIf { codes.size >= 2 && it in codes }.orEmpty()
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
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f), focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)))
            // фильтры в одну прокручиваемую строку: раньше они не помещались, «Замерить» сжималась до столбика букв и раздувала строку
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip({ AppState.pingAll() }, { Text("Замерить") }, enabled = AppState.servers.isNotEmpty() && !AppState.pingBusy,
                    leadingIcon = { if (AppState.pingBusy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(RayIcons.Gauge, null, Modifier.size(16.dp)) })
                FilterChip(!AppState.onlyFav, { AppState.onlyFav = false }, { Text("Все") })
                FilterChip(AppState.onlyFav, { AppState.onlyFav = true }, { Text("Избранное") }, leadingIcon = { Icon(RayIcons.Star, null, Modifier.size(16.dp)) })
                FilterChip(AppState.sortPing, { AppState.sortPing = !AppState.sortPing; if (AppState.sortPing) AppState.sortRating = false; AppState.save() }, { Text("Пинг ↑") })
                FilterChip(AppState.sortRating, { AppState.sortRating = !AppState.sortRating; if (AppState.sortRating) AppState.sortPing = false; AppState.save() }, { Text("Рейтинг") })
            }
            // страны из флагов в названиях серверов: показываем, если их хотя бы две
            val countries = Countries.available(AppState.servers.map { it.name })
            if (countries.size >= 2) Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(AppState.countryFilter.isEmpty(), { AppState.countryFilter = "" }, { Text("Все страны") })
                countries.forEach { (code, n) ->
                    FilterChip(AppState.countryFilter == code, { AppState.countryFilter = if (AppState.countryFilter == code) "" else code },
                        { Text(Countries.flag(code) + " " + Countries.title(code, I18n.locale) + " · " + n, maxLines = 1) })
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
                            Text(sub.name.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val chips = remember(s.link, Pal.dark) { chipsOf(s.link) }
    var menu by remember { mutableStateOf(false) }
    var qrAsk by remember { mutableStateOf(false) }; var qrShow by remember { mutableStateOf(false) }
    if (qrAsk) AlertDialog(onDismissRequest = { qrAsk = false }, title = { Text("Показать QR-код сервера?") },
        text = { Text("В QR-коде ключи этого сервера. Любой, кто его отсканирует или сфотографирует, сможет пользоваться сервером. Показывайте только тому, кому доверяете, и закройте окно сразу после сканирования.") },
        confirmButton = { TextButton({ qrAsk = false; qrShow = true }) { Text("Показать") } }, dismissButton = { TextButton({ qrAsk = false }) { Text("Отмена") } })
    if (qrShow) QrDialog(s.name, s.link) { qrShow = false }
    val shape = RoundedCornerShape(20.dp)
    val ping = AppState.pings[s.id]
    Box(modifier.padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.26f), MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)))
                else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = if (Pal.dark) 0.58f else 0.74f), MaterialTheme.colorScheme.surface.copy(alpha = if (Pal.dark) 0.58f else 0.74f))))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))) else glassEdge(), shape)
            .combinedClickable(onClick = { AppState.selectedId = s.id; AppState.save() }, onLongClick = { menu = true })
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).border(1.dp, glassEdge(), CircleShape), contentAlignment = Alignment.Center) { Text(flag, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(nm, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val desc = remember(s.link) { Links.serverDescription(s.link) }
                if (desc != null) Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    chips.forEach { Tag(it.first, it.second) }
                }
                val st = remember(HealthUi.version, s.link) { Health.stats(s.link) }
                if (st != null && st.n >= 3) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { st.dots.forEach { ok -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (ok) Pal.Green else Pal.Red)) } }
                    Spacer(Modifier.width(6.dp))
                    // медиана «· N мс» только пока нет плашки пинга: два числа в мс рядом путают
                    Text("${(st.uptime * 100).roundToInt()}% за сутки" + (if (st.medianMs > 0 && ping == null) " · ${st.medianMs} мс" else ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                val note = AppState.pingNotes[s.id]
                if ((ping ?: 0) < 0 && ping != -2 && !note.isNullOrBlank()) Text(note, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = Pal.Red.copy(alpha = 0.9f), maxLines = 2)
            }
            ping?.let { p ->
                val c = if (p == -2) MaterialTheme.colorScheme.onSurfaceVariant else if (p < 0) Pal.Red else pingColor(p)
                Box(Modifier.padding(horizontal = 4.dp).widthIn(min = 64.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                    Text(if (p == -2) "…" else if (p < 0) "нет" else "$p мс", color = c, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontSize = 13.sp, maxLines = 1)
                }
            }
            IconButton({ if (fav) AppState.favs.remove(s.link) else AppState.favs.add(s.link); AppState.save() }) {
                Icon(if (fav) RayIcons.StarFilled else RayIcons.Star, "Избранное", Modifier.size(22.dp), tint = if (fav) Pal.Amber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) }
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

