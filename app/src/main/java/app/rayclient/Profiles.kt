package app.rayclient

import androidx.compose.runtime.*
import java.util.UUID

// ---------- профили маршрутизации ----------
internal fun AppState.snapshotOf(p: Profile) = Profile(p.id, p.name, rules.toList(), presets.toSet(), exceptRu, useWhitelist, domainStrategy, defaultAction)

internal fun AppState.loadProfile(p: Profile) { rules.clear(); rules.addAll(p.rules); presets.clear(); presets.addAll(p.presets); exceptRu = p.exceptRu; useWhitelist = p.wl; domainStrategy = p.ds; defaultAction = p.dflt }

/** Маршрутизация из заголовка подписки (если панель её передаёт). */
internal fun AppState.subProfile(): Profile? = subs.firstNotNullOfOrNull { s -> if (s.routing.isBlank()) null else HappRouting.parse(s.routing) }
    ?.let { Profile("sub", "Из подписки", it.rules, emptySet(), false, false, it.domainStrategy) }

internal fun AppState.activateProfile(id: String) {
    if (id == activeProfile) return
    if (activeProfile != "sub") profiles.indexOfFirst { it.id == activeProfile }.let { if (it >= 0) profiles[it] = snapshotOf(profiles[it]) }
    val target = if (id == "sub") subProfile() else profiles.firstOrNull { it.id == id }
    if (target == null) { message = "Профиль недоступен"; return }
    loadProfile(target); activeProfile = id; save()
}

internal fun AppState.newProfile(name: String, copy: Boolean) {
    if (activeProfile != "sub") profiles.indexOfFirst { it.id == activeProfile }.let { if (it >= 0) profiles[it] = snapshotOf(profiles[it]) }
    val id = UUID.randomUUID().toString()
    val p = if (copy) Profile(id, name, rules.toList(), presets.toSet(), exceptRu, useWhitelist, domainStrategy, defaultAction) else Profile(id, name, emptyList(), emptySet(), false, false, 1, 0)
    profiles += p; loadProfile(p); activeProfile = id; save()
}

internal fun AppState.renameProfile(id: String, name: String) { profiles.indexOfFirst { it.id == id }.let { if (it >= 0) profiles[it] = Profile(id, name, profiles[it].rules, profiles[it].presets, profiles[it].exceptRu, profiles[it].wl, profiles[it].ds, profiles[it].dflt) }; save() }

internal fun AppState.deleteProfile(id: String) {
    if (profiles.size <= 1) return
    profiles.removeAll { it.id == id }
    if (activeProfile == id) { val f = profiles.first(); loadProfile(f); activeProfile = f.id }
    save()
}

internal fun AppState.profileName(id: String) = if (id == "sub") "Из подписки" else profiles.firstOrNull { it.id == id }?.name ?: ""

