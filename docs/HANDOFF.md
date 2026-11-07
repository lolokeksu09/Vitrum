# Vitrum: состояние проекта

Автор: Lolokeksu. Правила работы для ИИ-агентов: `AGENTS.md` и `CLAUDE.md` в корне; история версий: `CHANGELOG.md`; заметки выпусков с контрольными суммами: `docs/releases/`. Здесь только текущее состояние.

## Техническая дорожная карта после аудита DUORAY-Mobile — реализация начата

- Подробный план: [VITRUM_DUORAY_TECHNICAL_ROADMAP.md](VITRUM_DUORAY_TECHNICAL_ROADMAP.md).
- Три приоритета: контролируемое управление версиями Xray-core; расширенная, но безопасная совместимость с JSON-подписками; выбор сервера по Wi-Fi/mobile/SSID поверх существующей классификации Scenarios.
- Решение по архитектуре: сохранить Kotlin/Compose, VpnService, JNI и текущую модель хранения; не переносить DUORAY-PC core/Rust/Slint и не копировать его исходники.
- Текущий первый шаг: draft [PR #80 — Xray runtime launch probe](https://github.com/lolokeksu09/Vitrum/pull/80). Добавлен отдельный Android instrumentation test для сравнения запуска встроенного Xray из nativeLibraryDir и staged-копии через /system/bin/linker64.
- **Интеграция Clone:** `clone` — отдельный Android build type с package ID `app.rayclient.clone`; он использует общий `app/src/main`, а `app/src/clone` переопределяет только clone-ресурсы. Общие изменения кода автоматически входят в Clone при сборке `assembleClone`. В этом PR CI дополнительно компилирует `assembleCloneAndroidTest`, чтобы runtime probe был доступен и для установленного клона.
- `tools/build.sh tests`, PR checks, release build и Clone pre-release workflow проверяют соответствующие Debug/Clone targets. Отдельный workflow [`xray-runtime-probe.yml`](../.github/workflows/xray-runtime-probe.yml) собирает Clone APK и instrumentation APK для Debug и Clone; он не подписывает и не публикует официальный релиз.
- CI run [#42385838450](https://github.com/lolokeksu09/Vitrum/actions/runs/42385838450) успешно проверил unit tests и Debug instrumentation APK на предыдущей ревизии. После добавления Clone targets требуется успешный новый run; запуск instrumentation tests на физическом ARM64-устройстве ещё не выполнен. Linker runtime не выбран, Xray Core Manager не реализован.
- `RayVpnService`, JNI spawn/TUN FD, Kill Switch, Vault, пользовательские данные, package ID официальной сборки, versionCode/versionName и signing key не менялись. JSON merge semantics и приоритет ручного выбора сервера остаются будущими отдельными этапами.
- План UI overhaul остаётся отдельным документом и отдельной последовательностью работ.

## Полный редизайн Vitrum после 2.8.2 — план утверждён, реализация не начата

- Актуальный подробный план: [VITRUM_UI_OVERHAUL_PLAN.md](VITRUM_UI_OVERHAUL_PLAN.md). Срез: `main` = `cbabfcec84f3a09ef7663df48168a4763b2ec7ce` на 2026-11-05, без открытых PR на момент инвентаризации.
- **Навигация реально двухвкладочная:** `MainActivity.Root` → `HomeTab` / `SettingsTab`. `ServersSheet`, `GameDialog`, `JournalDialog`, `ShieldDialog`, `RoutingTab`, девять разделов настроек и onboarding входят в общий объём; не превращать это автоматически в пять нижних вкладок.
- Направление: premium AMOLED/Manrope/фиолетовый акцент, без декоративного glass и ложных статусов, сохранять Material 3 и светлую тему. Этапы: базовый визуальный срез → UI-система → Главная → Серверы → Маршруты → Настройки → Game/Journal/Shield → onboarding/диалоги → проверки и подписанный pre-release.
- **Статус по этапам:** этап 0 — завершён: [исходный визуальный аудит](VITRUM_UI_BASELINE_STAGE0.md), 41 базовый кадр и 20 дополнительных сценариев; в [Android 16 UI CI #42049696718](https://github.com/lolokeksu09/Vitrum/actions/runs/42049696718) success, артефакт содержит 61 PNG и 61 XML, изображения просмотрены. Отдельные проверки [PR checks #42049696658](https://github.com/lolokeksu09/Vitrum/actions/runs/42049696658) успешны. **PR-01…PR-07 — не начаты**, финальный выпуск редизайна — не начат. Код VPN/TUN/Xray, подпись и хранилище не менялись.
- Перед началом этапа 0 перепроверить `main` и исходные Compose-файлы. `tools/clone-android16-ui.sh` даёт UI-only проверки на x86_64 эмуляторе, **не** доказывает работу реального VPN на ARM64.

## VITRUM 2.8.2 — интеграция улучшений Clone (опубликован 2026-11-05, pre-release)

- PR #75 и #76 уже слиты в `main` на момент подготовки выпуска (основной SHA `8af860a42b8558d00442312c5ab3a339abc41149`), опубликован отдельный Clone pre-release `clone-8af860a42b85`.
- В официальный `app.rayclient` входят доступные меню серверов и профилей, фиксированная кнопка возврата настроек, общая безопасная политика смены сервера в игровом режиме и сброс пустого фильтра журнала. Не нужен перенос кода из отдельного репозитория: Clone и оригинал разделяют исходники.
- `app/build.gradle.kts`: versionName 2.8.2, versionCode 282, исходный сертификат автора. `docs/releases/v2.8.2.md` содержит заметки и ограничения. Выпуск организует одноразовый `publish-v2-8-2.yml`, который проверяет кандидата и запускает стандартный подписанный `build.yml` по тегу.
- **Подтверждено по GitHub Actions:** официальный подписанный `v2.8.2` опубликован в [Releases](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.8.2), workflow [42037376322](https://github.com/lolokeksu09/Vitrum/actions/runs/42037376322) завершился успешно; проверены R8 и авторский сертификат. **Не проверено на физическом Lenovo Legion Y70**. DNS/IPv4/IPv6 leak-free остаётся UNKNOWN; VPN-ядро и TUN не менялись.

