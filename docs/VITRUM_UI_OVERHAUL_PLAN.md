# VITRUM — полный UI overhaul после v2.8.2

**Статус на 2026-11-09:** UI overhaul продолжается в `clone-integration`. Влиты PR #106–#118; последние исправления охватывают безопасный выбор сервера, русские поисковые синонимы Settings и доступность закрытия серверной шторки. `main` не менялся. Остаются аудит фактически незакрытых пунктов PR-01–PR-07, актуализация документации и интеграционный diff-аудит.

**Текущий интеграционный HEAD до следующего PR:** `0d088a9e09c6ef7b36c49ddaa5a07c9a3b9dad37` — merge PR #107.

**Слито в `clone-integration`:**
- PR #87 — общая UI-основа и оболочки (merge `1ffb7e124bd048f2681c9aa36e6bdeef3100549e`).
- PR #88 — Home (merge `a508578085d09eb068a3780962c1958e71ef5d05`).
- PR #89 — каталог серверов (merge `870facb87f1d735c4a47e6ad652a510d3b4447c3`).
- PR #90 — Routing (merge `d97ce66cfba293037c6144f042e8a52e4d0ab088`).
- PR #92 — перенос option chips на компактных экранах (merge `41a78fbe169947a398b435a8e120c9ee0e074efc`).
- PR #91 — минимальный обязательный Clone CI (merge `a626a66911c05b1e1fbd859ddcc36f0ceda53f9e`).
- PR #93 — сгруппированный Settings Hub и расширенный поиск (merge `427bdf1abc022595a1d83583b5532d07db419e25`).
- PR #95 — подтверждение очистки Journal и доступность строк (merge `423366d0ff602bb2512162293173b9abc7cd0d0d`).
- PR #96 — корректная обработка Back в onboarding и устойчивое прокручиваемое окно AddDialog (merge `94a9304cd227a98d7b86172d8b3105c471b30d35`).
- PR #97 — подтверждение очистки истории диагностик Shield (merge `486d7bbb4de10881ddbd098eaa97269471573c34`).
- PR #99 — общие password/restore dialogs для BackupPanel (merge `1e20baf274535f5779fd653a5a4610f63a09c8ae`).
- PR #100 — основные Game actions с минимальной высотой 48 dp (merge `4335a70534861d3bc6065588370329a794b350d0`).
- PR #102 — кнопки действий во всех разделах Settings с минимальной высотой 48 dp (merge `3c0f89f5ad904532d1a74772f2a8c58e5713ec9c`).
- PR #103 — остаточные кнопки Journal и Shield с 48 dp hit targets и правильным порядком spacing-модификаторов (merge `c93998613b40130b47f92755d6b7ab96c56664d4`).
- PR #104 — недостающие 48 dp actions onboarding и корректный порядок spacing/heightIn в Settings (merge `7b6dad33e5e9b96372fab2ee19262692f364a6de`).
- PR #105 — 48 dp для действий Routing: профиль, приложения, базы и правила (merge `965d1ff9c226edf43baa1aa609764cc6f656e606`).
- PR #106 — 48 dp для четырёх welcome actions Home: вставка из буфера, QR камерой/фото и ручное добавление (merge `0ead35cae08525cda665b14e308c543389489e0b`).
- PR #107 — синхронизация `HANDOFF.md` и этого плана после PR #106 (merge `0d088a9e09c6ef7b36c49ddaa5a07c9a3b9dad37`).

**Текущий implementation-slice:** PR #106 merged; PR #107 синхронизировал документы. В текущей ветке расширен `tools/clone-android16-ui.sh`: матрица Settings теперь включает все 9 направлений × RU/EN × dark/light × font scale 1.0/1.3/1.5 (108 UI-снимков), с проверкой ожидаемого заголовка и XML/PNG артефактов. Эта матрица ещё не запускалась; TalkBack, реальные insets и отсутствие побочных `AppState.save()` остаются отдельными gates.

**Минимальный CI:** PR #91 workflow [#42513632660](https://github.com/lolokeksu09/Vitrum/actions/runs/42513632660) — success на exact HEAD `42cfc8ef0e6f65f02712080429ecf282eef86d44`; PR #93 workflow [#42514877483](https://github.com/lolokeksu09/Vitrum/actions/runs/42514877483) — success на exact HEAD `ee1895cc7a29059611be8afb0cb19f174a158881`. Обязательный путь: verified runtime assets, coverage переводов, `assembleDebug` + `assembleClone`, package identity/metadata и APK artifact. Полный unit suite, UI-эмулятор и screenshot matrix не запускаются на каждом PR.

**Ограничение проверки:** Clone build не подтверждает реальный VPN/Xray traffic. TalkBack, системные жесты, реальные insets и ARM64 runtime на Lenovo Legion Y70 остаются отдельной ручной приёмкой.

## 1. Цель и границы

**Цель:** сделать визуально и функционально цельный, современный Android VPN-клиент во **всех** пользовательских поверхностях: обе нижние вкладки, шторка серверов, экраны инструментов, девять разделов настроек, вложенные формы, диалоги и onboarding. Не ограничиваться перекраской и заменой иконок: перестроить визуальную иерархию, группировку информации, состояния, экранные действия и доступность **без изменения сетевой семантики**.

**Важно: текущая архитектура не является пяти-вкладочной.** `MainActivity.Root` + `FloatingNav` содержат **две** нижние вкладки: `HomeTab` и `SettingsTab`. `ServersSheet` открывает `ServersTab` поверх главной; `GameDialog`, `JournalDialog`, `ShieldDialog` — самостоятельные полноэкранные инструменты. `RoutingTab` вызывается из `SettingsTab` через `RoutesPage`. Этот маршрут сохраняем. Не увеличивать число табов ради визуальной новизны, не переносить бизнес-функции в новую навигационную архитектуру без отдельного доказательства пользы.

**Визуальный контракт:**
- Премиальный, спокойный, утилитарный интерфейс: AMOLED `#000000` в тёмной теме, существующий фиолетовый акцент VITRUM и шрифт Manrope. Светлая тема — полноценная, не тёмная с белыми плашками.
- Состояние важнее декора. Только смысловые цвета (подключено/ожидание/ошибка/неизвестно), без стеклянных панелей, бессмысленного glow, бесконечной фоновой анимации, «AI-slop» и дублирующих статусов.
- Единая шкала типографики, отступов (ориентир 4/8/12/16/24/32 dp), спокойных поверхностей и последовательных скруглений. Конечные значения отобрать по реальным скриншотам, не менять механически.
- Кнопка основного действия различима визуально, а статусы не кодируются **только** цветом. Интерактивные зоны не менее 48 dp; текст допускает перенос при font scale 1.3 и 1.5. Без горизонтальной прокрутки обязательных действий.
- Минимальная, функциональная анимация с учётом системной настройки сокращения движения. Сохранить edge-to-edge и корректные отступы системных панелей, клавиатуры, нижней навигации и жестов.

## 2. Инвентаризация реального кода

| Поверхность | Текущий Compose/источник | Что должно остаться работоспособным |
| --- | --- | --- |
| Корень, переходы, нижние вкладки | `MainActivity.kt` (`Root`), `Ui.kt` (`FloatingNav`) | Главная/Настройки, Back, системные insets, диалоги глобальных результатов, разрешение Android VPN |
| Тема и компоненты | `Ui.kt`: `RayTheme`, `Panel`, `GroupTitle`, `SwitchRow`, `RaySwitch`, `Segmented`, `Pill`, `FloatingNav`; `RayIcons.kt` | Светлая/тёмная темы, контраст, семантика TalkBack, шрифт Manrope |
| Главная | `HomeScreen.kt`: `HomeTab`, `PowerButton`, `WelcomePanel`; `HomeStatusUi.kt` | Реальные состояния VPN и доказательств соединения, выбранный сервер, подключение, быстрый выбор, статистика |
| Серверы и подписки | `Sheet.kt` (`ServersSheet`), `ServersScreen.kt` (`ServersTab`, `ServerRow`, `AddDialog`) | Поиск, избранное, страны, сортировки, пинг, подписки, добавление/удаление, жест закрытия, выбор сервера |
| Безопасный выбор | `ServerChoiceUi.kt`, `ServerChoicePolicy.kt` | При активном, занятом или blocked TUN — никаких самовольных изменений `selectedId`; существующие подтверждения |
| Маршрутизация | `RoutingScreen.kt` (`RoutingTab`, `AppPicker`), `SettingsScreen.kt` (`RoutesPage`) | Профили, правила и их порядок, предупреждение о `direct`, исключения, выбор приложений, предварительный разбор маршрута |
| Настройки | `SettingsScreen.kt` (`SettingsTab`, `SettingsProtection`, `SettingsConnection`, `SettingsAutomation`, `SettingsNetwork`, `SettingsAppearance`, `SettingsService`, `SettingsHelp`); `AboutScreen.kt` (`SettingsAbout`) | Поиск, девять направлений: Защита, Маршруты, Подключение, Автоматизация, Сеть и DNS, Оформление, Сервис, Справка, О приложении |
| Игровой режим | `GameScreen.kt` (`GameDialog`) | Режим, live latency/jitter/loss, тест серверов, безопасное переключение, выбор игр, реальные ограничения измерения HTTP |
| Журнал | `JournalScreen.kt` (`JournalDialog`) | Трафик/События VPN, фильтры, группировка, сброс, очистка, ограниченный безопасный отчёт, правила из записей |
| Щит | `ShieldScreen.kt` (`ShieldDialog`) | Проверяемые/неизвестные факты, score, DNS Route Audit, отдельное согласие на direct-тест, приватность |
| Первый запуск/диалоги | `Onboarding.kt`, `MainActivity.kt` (`DiagDialog` через `SettingsScreen.kt`), `ServersScreen.kt`, `RoutingScreen.kt` | Шаги 1–3, импорт ссылок только после подтверждения, QR, ошибки, Back, сохранение состояния |
| Переводы и CI | `app/src/main/res/raw/i18n_en.tsv`, `Demo.kt`, `tools/clone-android16-ui.sh`, `.github/workflows/pr-check.yml`, `.github/workflows/clone-android16-ui.yml` | Русский/английский, настоящая UI-иерархия и скриншоты эмулятора, сценарии с синтетическими данными |

**Уже существует:** v2.8.0–2.8.2 убрали основную избыточную декоративность и улучшили часть контролов. Не возвращать стеклянный/анимированный старый UI; не переписывать заново работающую политику выбора серверов из PR #75/#76. Новый план **дополняет** `docs/UI_DESIGN_V2_8.md`; исторические замечания о старом orb/градиентах не описывают текущий код.

## 3. Обязательные требования (сквозная приёмка)

- **UX-01 / полнота:** ни одна из перечисленных поверхностей не осталась со старым визуальным языком; явно проверить главную, серверную шторку, все **9** направлений настроек, Game, Journal, Shield, добавление сервера, правила, подтверждения, onboarding и информационные диалоги.
- **UX-02 / честность:** визуальные статусы `off`, `connecting`, `connected`, `switching`, `blocked`, `error`, `unknown` не спутаны. «Xray запущен» ≠ «трафик проверен» ≠ «утечек нет». В Щите нет визуального «всё защищено» при `UNKNOWN`.
- **UX-03 / навигация:** табы остаются Главная/Настройки, шторка и инструменты имеют видимый Back/Close; Back, свайп, клавиатура и закрытие вложенных окон не теряют ввод/выбор. Действия не прячутся в горизонтальной прокрутке.
- **UX-04 / совместимость:** сохранить `app.rayclient`, формат настроек и подписок, TUN/Xray, kill switch, DNS/routing priority, шифрование, сертификат. Не добавлять разрешения, SDK-зависимости, root-возможности или новые сетевые запросы ради дизайна.
- **UX-05 / доступность:** контраст текста и меток не ниже WCAG AA для применимых элементов, области нажатия >= 48 dp, TalkBack читаемые labels/roles/states, читаемость при `font_scale 1.0 / 1.3 / 1.5`; горизонтальный overflow и усечение критических действий блокируют PR.
- **UX-06 / локализация:** все новые видимые русские строки переводятся через существующий механизм и `i18n_en.tsv`; английская версия не содержит русских новых меток без предусмотренного механизма.
- **UX-07 / сохранение состояний:** поиск и фильтры, выбранный профиль, избранное, активное состояние и раскрытые панели сохраняются/сбрасываются **только по существующему контракту**. UI не меняет сервер, DNS, правила или подписку при простой прокрутке/перерисовке.
- **UX-08 / проверка:** на каждом поведенческом PR — релевантный regression test и CI; на каждом визуальном PR — актуальные скриншоты изменённого приложения, ручной просмотр без обрезки/оверлапов и отчёт по тёмной/светлой теме. Успешная сборка не заменяет визуальную проверку.
- **UX-09 / поставка:** объединять поэтапно после обязательных gates. Официальный APK только через проверенный `build.yml` и авторскую подпись; пока нет подтверждённых ARM64 runtime тестов — только pre-release, не «stable».

## 4. Последовательность реализации: независимые проверяемые PR

### Этап 0 — зафиксировать эталон (перед изменением UI)

**Задача:** из `v2.8.2` воспроизвести пользовательские сценарии и снять реальные исходные кадры. Использовать demo-данные **только для UI**, не выдавать их за VPN. Сверить тёмную и светлую темы, русский/английский, короткое окно, шрифт 1.0/1.3/1.5; 360/390/430 dp — целевые диапазоны. Проверить существующие скриншоты CI и скрипт.

**Изменения:** по необходимости только `Demo.kt` и `tools/clone-android16-ui.sh` для покрытия и воспроизводимости; без изменения business logic.

**Готово, если:** есть карта переходов и скриншоты текущего UI с SHA сборки, перечень фактически неохваченных состояний, перечень дефектов для последующих PR. Не притворяться, что fixture проверяет сеть.

**Статус:** baseline и UI smoke PR #87 получены; PR #87 merged в `clone-integration` после checks и Android 16 smoke [#42449283233](https://github.com/lolokeksu09/Vitrum/actions/runs/42449283233) / [#42449283447](https://github.com/lolokeksu09/Vitrum/actions/runs/42449283447). Smoke проверяет синтетический Demo, не реальный VPN/Xray. Ручные accessibility/insets gates остаются.
- Визуальное наблюдение: на Servers при ширине около 360 dp / font scale 1.3 обрезаются длинное название сервера и подпись подписки. Исправление относится к PR-03, не смешивать его с общей оболочкой диалогов.
- Smoke подтверждает UI-сценарии с синтетическим Demo, а не реальный VPN. Он не проверяет TalkBack, ручной WCAG-контраст, полный open/Back/drag/insets для `ServersSheet` или физический ARM64 runtime. Этап 0 по скриншотам закрыт; доступность и системные insets остаются отдельными gates.

### PR-01 — единая UI-система и оболочка

**Статус:** implementation-slice merged в Clone через PR #87. Общая UI-система `RayDialogFrame` / `RayConfirmDialog`, `RayScreenHeader`, `RayEmptyState`, `RayInlineNotice` подключена к нужным экранам; callbacks и владельцы состояния сохранены. PR checks и Android 16 UI smoke на точном head прошли. Ручные gates TalkBack, contrast, нижние insets `ServersSheet` и полная матрица dark/light + font scales не закрыты.
**Автоматизированная проверка:** `tools/clone-android16-ui.sh` проверяет Home → `ServersSheet` → system Back → повторное открытие → drag-to-close; PR #87 smoke прошёл. PR #88 расширяет Home state matrix, пустой первый запуск и compact-height/font scale 1.5; его smoke выполняется.
**Открытые gates PR-01:** нижние insets `ServersSheet`, TalkBack и контраст на устройстве; проверка тёмной/светлой темы и font scale 1.0/1.3/1.5 на полном наборе экранов. Поэтому implementation/CI slice готов, но PR-01 ещё не закрыт как полностью принятый UX-этап.

**Файлы:** `Ui.kt`, `DialogUi.kt`, `RayUiPrimitives.kt`, `MainActivity.kt`, `ServersScreen.kt`, `RoutingScreen.kt`, `SettingsScreen.kt`, `JournalScreen.kt`, `ShieldScreen.kt`, локализация `i18n_en.tsv`; `Sheet.kt` остаётся в scope для последующей сверки overlay/Back/insets.

**Работа:** нормализовать уровни поверхности/типографику/высоты контрола, заголовки экранов, индикацию выбора, повторяющиеся пустые/ошибочные/загрузочные состояния, унифицированные диалоги, текстовые иконки, отступы, безопасные системные края. Согласовать общую политику наложений и Back/Close. Сохранить 2-tab навигацию и самостоятельность экранов.

**Приёмка:** скриншоты корневой навигации/шторки/диалога в dark+light; TalkBack и font 1.5 без потерянных действий; контроль неизменности обработчиков; существующая Clone smoke зелёная.

### PR-02 — Главная: одно главное действие

**Статус:** PR #88 merged в `clone-integration` (merge `a508578085d09eb068a3780962c1958e71ef5d05`). Home перестроен вокруг последовательности статус VPN → основное connect/disconnect/add action → отдельный выбор сервера → доказательства соединения → статистика. Вторичные входы Journal/Game и авто-выбор отделены от выбора сервера. `HomeStatusPolicy` отображает SWITCHING явно; существующие сетевые callbacks не менялись. PR #106 добавляет 48 dp для четырёх welcome actions без изменения обработчиков добавления сервера. Аппаратная и ручная accessibility-приёмка остаются отдельными gates.

**Файлы:** `HomeScreen.kt`, использовать существующий `HomeStatusUi.kt` и статус из `RayVpnService`; `Ui.kt` только если требуется общий компонент.

**Работа:** выстроить порядок **статус VPN → connect/disconnect → выбранный сервер → достоверность состояния → вторичная статистика**. В блоке подключения: OFF/CONNECTING/CONNECTED/SWITCHING/BLOCKED/ERROR без ложного успеха; не дублировать подписи. Сервер — отдельное явно нажимаемое действие, которое открывает существующую шторку. Важные быстрые входы Journal/Game/Shield остаются доступны; статистика не оттесняет кнопку за пределы маленького экрана. Первое использование: понятный `WelcomePanel` с существующими методами добавления.

**Приёмка:** PR #88 добавляет synthetic smoke для OFF / CONNECTING / CONNECTED / SWITCHING / BLOCKED / ERROR, пустого набора серверов и 360×640 dp с font scale 1.5; unit tests фиксируют SWITCHING и приоритет BLOCKED. После CI требуется визуально проверить скриншоты Home и отдельно отметить, что физическое устройство и реальный VPN runtime не проверены. Основной action вызывает прежний `toggle`; сетевой контракт не переопределён.

### PR-03 — Серверы: каталог вместо перегруженного списка

**Статус:** PR #89 merged в `clone-integration` (merge `870facb87f1d735c4a47e6ad652a510d3b4447c3`). Вошли компактные карточки, поиск/фильтры/сортировка, группировка подписок, безопасный выбор, offline/refresh/ping states и защита активного сервера. Остаются ручная visual/accessibility-приёмка и проверки на физическом устройстве.

**Файлы:** `ServersScreen.kt`, `Sheet.kt`, при необходимости только UI-вызывающая часть `ServerChoiceUi.kt` (не менять `ServerChoicePolicy.kt`).

**Работа:** единый компактный toolbar с поиском/добавлением, видимые «Все / Избранное», выбор страны/сортировки/замера; карточка строки с названием, принадлежностью к подписке, latency, текущим выбором, доступным меню. Группы подписок читаемы и не дублируют действия каждой строки. Нет серверов / фильтр пуст / нет сети / измерение / обновление подписки имеют разные состояния. Надёжно сохранять вертикальную прокрутку и отдельный жест закрытия шторки.

**Приёмка:** активный, неактивный и заблокированный TUN; длинные названия, несколько стран, фильтры, favorite, подписки и удаление с подтверждением. Выбор не обходится через прямое изменение `selectedId`.

### PR-04 — Маршруты: первичный сценарий и сложные правила

**Статус:** PR #90 merged в `clone-integration` (merge `d97ce66cfba293037c6144f042e8a52e4d0ab088`): счётчики показывают только включённые правила, move-up/down controls доступны, удаление подтверждается с возможностью отмены. PR #105 добавляет недостающие 48 dp размеры интерактивных действий страницы; логика маршрутизации не меняется.
**Файлы:** `RoutingScreen.kt`, вызывающая страница `RoutesPage` в `SettingsScreen.kt`.

**Работа:** профиль/добавление/копирование/удаление в понятном верхнем блоке; ниже — краткое объяснение действующей схемы, затем программы/предустановки/пользовательские правила/расширенные стратегии. Пояснять эффект `direct`, приоритет первого совпадения, режим «Только выбранные», применение после переподключения. Сложные поля и диалоги читаемы без вложенных горизонтальных chips.

**Приёмка:** не меняется порядок применения и сохранения правил, импортированный профиль из подписки остаётся read-only, операции подтверждаются, прогноз маршрута не изображается как результат реального трафика.

### PR-05 — Настройки: все 9 направлений и вложенные страницы

**Файлы:** `SettingsScreen.kt`, `AboutScreen.kt`; общие элементы — `Ui.kt`.

**Работа:** единая архитектура списка и вложенной страницы для **Защиты, Маршрутов, Подключения, Автоматизации, Сети и DNS, Оформления, Сервиса, Справки, О приложении**. Заголовок и Back не исчезают при прокрутке; категории и switch-строки едины; важное действие раньше экспертных опций; пояснения доступны по запросу, но не обрезаны без раскрытия. Рискованные действия/переключения — с прежними подтверждениями. Поиск/сброс, состояния загрузки и ошибки настроек, тёмная/светлая темы.

**Приёмка:** пройти все 9 страниц, подпункты и возврат после прокрутки; поиск DNS, защита, сервис и пустой результат; чтение текста при font 1.5; никаких побочных `AppState.save()` от простого открытия страницы.

**Статус на 2026-11-08:** PR #92 (option chips) и PR #93 (группировка девяти направлений, доступные строки, поиск по параметрам и локализация) merged в `clone-integration`. PR #102 merged: кнопки действий в `SettingsScreen.kt` получили минимум 48 dp без изменения обработчиков и настроек. Полный приёмочный проход всех страниц, вложенных экранов и font scale 1.5 остаётся открытым; открытие разделов не должно вызывать `AppState.save()`.

### PR-06 — инструменты: Game, Journal, Shield

**Файлы:** `GameScreen.kt`, `JournalScreen.kt`, `ShieldScreen.kt`; `HomeScreen.kt` только для точек входа.

**Аудит текущего Clone:** Game уже отделяет live TUN metrics, server test и app picker; server selection идёт через `chooseServerFromUi`. Shield разделяет BAD/WARN, UNKNOWN, OK и INFO; прямой IP требует отдельного согласия. PR #95 merged: очистка истории Journal теперь требует подтверждения, строки записей имеют доступные размеры. PR #97 merged: очистка истории диагностик Shield также требует подтверждения. PR #96 merged: исправлены Back на первом шаге onboarding и сохранение/прокрутка AddDialog.

**Работа:**
- **Game:** различать live-замер по TUN и тест списка серверов; дать читабельные ping/jitter/loss, ошибки и отсутствие ответов; выбор лучшего сервера только через `chooseServerFromUi`. Игры/список приложений — отдельная спокойная секция.
- **Journal:** два представления («Трафик» / «События VPN»), читаемые фильтры, дата/тип/состояние, пустой лог vs пустой фильтр, сброс/очистка, предупреждение при копировании отчёта; сохранить ограничения хранения и приватность.
- **Shield:** разделить «проверено», «проблема», «не проверено», локальный аудит конфигурации и ручные тесты; ясный CTA для проверки; root-возможности не предлагать как обязательные. Никогда не называть `UNKNOWN` безопасным результатом.

**Приёмка:** в каждом экране реальны Back/Close, loading/empty/error, font 1.5; у Shield остаётся явное согласие на direct IP; у Game blocked сервер не переписывается; у Journal новый фильтр не стирает историю.

### PR-07 — onboarding, диалоги и доведение покрытия

**Файлы:** `Onboarding.kt`, `ServersScreen.kt` (`AddDialog`), `SettingsScreen.kt` (`DiagDialog`), диалоги `RoutingScreen.kt`, `MainActivity.kt`, `app/src/main/res/raw/i18n_en.tsv` и тестовые демонстрационные данные `Demo.kt`.

**Работа:** завершить единый визуальный язык первого запуска и подтверждений; на каждом из 3 шагов видимые Далее/Назад/Пропустить при малой высоте/IME. Не прятать опасные предупреждения, не облегчать добавление внешней ссылки без подтверждения, не копировать приватные диагностические данные без предупреждения. Свести все оставшиеся различия в заголовках, кнопках, меню, пустых состояниях и формах.

**Приёмка:** все три шага, импорт/QR/ручное добавление, ошибки/отмена, появление системной клавиатуры, русская и английская строки, Back; ни одного потерянного критического действия.

### Этап 8 — интеграционная приёмка и официальный выпуск

**Файлы:** `tools/clone-android16-ui.sh` и соответствующий workflow при необходимости; тесты только на доказанные сценарии; `CHANGELOG.md`, `docs/HANDOFF.md`, `README.md`, `docs/releases/` — **после выбора и проверки следующей версии**, без гадания по номеру заранее.

**Gate для обычного Clone PR:** `python3 tools/i18n/dict_cov.py`, `./gradlew assembleDebug assembleClone`, `tools/verify-clone-apk.sh` через обязательный CI workflow и APK artifact. Полный `testDebugUnitTest`, instrumentation target, Xray fixture suite и Android Emulator UI matrix не запускаются на каждом PR. Регрессионные тесты выполняются адресно, когда меняется соответствующая логика; полный screenshot/state matrix запускается вручную, когда нужна визуальная приёмка. Артефакты скриншотов скачивать и визуально проверять, не объявлять их корректными по наличию файла.

**Ручная матрица:** Android 14–16 где доступны устройства, обязательно ARM64 Lenovo Legion Y70; font 1.0/1.3/1.5; dark/light; RU/EN; компактная высота, широкий/узкий экран, TalkBack, аппаратная/системная Back; состояние OFF/CONNECTED/SWITCHING/BLOCKED и отсутствие сети. Проверка физического устройства принадлежит владельцу и записывается в `docs/ANDROID_RUNTIME_CHECKS.md` **только с реальными доказательствами**.

**Публикация:** общий UI код проверяется в Clone, затем официальный `app.rayclient` со старым сертификатом; штатный `build.yml` валидирует R8 и SHA-256 сертификата `8cb8b675f97239948f0b93ac4208464e856397d6141268798129c00337205c61`. Пока аппаратные VPN-тесты не подтверждены, только pre-release. Ни один debug/clone APK не считается официальным обновлением.

## 5. Контроль регрессий, риски и критерий завершения

**Риски:**
1. UI-блокировка или неверная подпись состояния при `blocked`/switching может создать ложную уверенность. Лечение: не трогать `RayVpnService`, `ServerChoicePolicy`, `VpnTruth`; проверить оригинальные callbacks и матрицу состояний.
2. Повторный Compose UI может непреднамеренно вызывать `AppState.save()`, терять поля при `remember`/Back либо скрывать меню при IME. Лечение: event-boundary проверки, focused regression + эмулятор, `rememberSaveable` где контракт требует восстановления.
3. Длинные настройки/правила создают nested-scroll, невидимый Back или оверлап нижней панели. Лечение: один scroll owner на страницу, фиксированный header, insets, снимки в 360/390/430 и font 1.5.
4. Взятие реальных URL/логов для скриншотов нарушит приватность. Лечение: только синтетический `Demo.kt`, без IP/токенов/адресов из пользовательских подписок.
5. Полный одномоментный rewrite тяжело откатить. Лечение: отдельные небольшие PR-01…PR-07, откат независимого визуального PR без миграции хранилища, смежные изменения только по доказанным зависимостям.

**Definition of done всего патча:** UX-01…UX-09 выполнены с доказательствами; перечисленные экраны обновлены; тесты и реально просмотренные скриншоты соответствуют точным коммитам; нет нарушений безопасности/маршрутизации/сохранения; есть подписанный pre-release из `main`, Release assets, SHA-256 и заметки выпуска; статус ручной проверки честно указан. «Открыли PR», «собрали APK», «прогнали демо» и «проверили реальный VPN» — разные этапы.

## 6. Правила поддержания актуальности

- Перед каждым этапом заново проверять `main`, номер версии, PR, текущие Compose-файлы и UI smoke workflow.
- После каждого принятого PR обновлять этот статус и `docs/HANDOFF.md` в том же документационном PR. Не оставлять merged PR как draft/open.
- Хранить историю выпусков в `CHANGELOG.md` и `docs/releases/`; не объявлять Clone pre-release официальным релизом.
- PR-02 Home (#88) merged только в `clone-integration`; PR #106 закрывает оставшиеся welcome action targets. PR-03 Servers (#89) merged; PR-04 Routing (#90, #105) merged. PR-05 Settings partial: PR #92/#93 и action targets (#102) merged; ручная приёмка остаётся открытой. PR-06 Journal (#95), Shield (#97), Game actions (#100) и остаточные action targets Journal/Shield (#103) merged; runtime остаётся device gate. PR-07 onboarding/AddDialog (#96), backup dialogs (#99) и onboarding action targets (#104) merged. `main` не изменён.
- Этот документ не утверждает, что полный редизайн реализован. Статус каждого этапа должен сопровождаться ссылкой на commit/PR и проверку; отсутствие доказательств означает «не подтверждено».


---

## Промежуточный аудит реализации — 2026-11-08

Этот аудит отделяет уже существующий код от оставшейся приёмки. Наличие экрана или unit test не считается доказательством полного UX-этапа.

| Этап | Что уже есть в `clone-integration` | Дополнительное покрытие в открытых PR | Осталось |
|---|---|---|---|
| PR-03 Серверы | Поиск, избранное, фильтры страны, сортировка, замер задержки, группы подписок и пустые состояния | PR #89 merged: компактные карточки, безопасные действия для активного маршрута, стабильные ID auto-серверов, offline/refresh/ping states | Ручная visual/accessibility приёмка и физический ARM64/VPN runtime |
| PR-04 Маршруты | Профили, пользовательские правила, порядок Xray, импортированные read-only профили, объяснение маршрутов | PR #90 и PR #105 merged; действия профилей, приложений, баз и правил имеют минимум 48 dp | Ручной TalkBack и проверка сложных списков на устройстве |
| PR-05 Настройки | Hub с поиском, девять направлений, вложенные страницы, About/лицензии; закреплённый PageHeader и раскрытие длинных пояснений в SwitchRow | PR #92/#93 merged; PR #102 merged: все кнопки действий в Settings имеют минимум 48 dp | Пройти все 9 страниц, вложенные состояния, font scale 1.5, TalkBack и отсутствие побочных save() |
| PR-06 Game / Journal / Shield | Game разделяет live TUN metrics, server test и app picker; Shield группирует BAD/WARN, UNKNOWN, OK и INFO, direct-IP требует согласия | PR #95 Journal clear confirmation; PR #97 Shield history clear confirmation; PR #100 Game actions от 48 dp; PR #103 — остаточные 48 dp action targets Journal/Shield (merged) | Проверить Back/Close, empty/filtered states, TalkBack; ручные live VPN measurements остаются device gate |
| PR-07 Onboarding / формы / диалоги | Три шага onboarding, BackHandler, Skip, readiness, QR parsing, DiagDialog и парольные формы backup | PR #96 merged: правильный system Back и scrollable/persisted AddDialog; PR #99 merged: общая оболочка password/restore dialogs; PR #104 merged: восемь дополнительных 48 dp actions и корректный порядок layout-модификаторов | Реальный QR/camera flow, IME/ввод ссылки, резервное восстановление, copy diagnostics и английская визуальная проверка |

### Статус PR и CI на момент аудита

- PR #87–#93, #95–#97, #99, #100, #102–#105 merged только в `clone-integration`; последний merge — PR #105, commit `965d1ff9c226edf43baa1aa609764cc6f656e606`.
- PR #91 lean workflow [#42513632660](https://github.com/lolokeksu09/Vitrum/actions/runs/42513632660) — success на exact HEAD `42cfc8ef0e6f65f02712080429ecf282eef86d44`.
- PR #93 lean workflow [#42514877483](https://github.com/lolokeksu09/Vitrum/actions/runs/42514877483) — success на exact HEAD `ee1895cc7a29059611be8afb0cb19f174a158881`.
- PR #95 lean workflow [#42515931607](https://github.com/lolokeksu09/Vitrum/actions/runs/42515931607) — success на exact HEAD `f2f95b3d28cdf55f45e18fdede0d9bfc8526e48e`; merged.
- PR #96 lean workflow [#42516260396](https://github.com/lolokeksu09/Vitrum/actions/runs/42516260396) — success на exact HEAD `c2c6726da40d8dacff3ddd0c3abc1cd1c63dd867`; merged.
- PR #97 lean workflow [#42516541367](https://github.com/lolokeksu09/Vitrum/actions/runs/42516541367) — success на exact HEAD `64ba9a79ee22606d90c8312d7068b7b9118b4598`; merged.
- PR #99 lean workflow [#42517022495](https://github.com/lolokeksu09/Vitrum/actions/runs/42517022495) — success на exact HEAD `8deb09b692e6c0b8334443f799363d55b12ec7c4`; merge `1e20baf274535f5779fd653a5a4610f63a09c8ae`.
- PR #100 lean workflow [#42518347441](https://github.com/lolokeksu09/Vitrum/actions/runs/42518347441) — success на exact HEAD `aaf0b79ed207264d3c0adceb78a45c1157ebed70`; merged в `clone-integration` commit `4335a70534861d3bc6065588370329a794b350d0`.
- PR #102 lean workflow [#42519241689](https://github.com/lolokeksu09/Vitrum/actions/runs/42519241689) — success на exact HEAD `414f3026986bd81f4387dc56a464eac3c844a205`; merged в `clone-integration` commit `3c0f89f5ad904532d1a74772f2a8c58e5713ec9c`.
- PR #103 lean workflow [#42519862492](https://github.com/lolokeksu09/Vitrum/actions/runs/42519862492) — success на exact HEAD `37d3f8d5bcdb28f7a706fe7c1dc32eed7ccfd47b`; merged в `clone-integration` commit `c93998613b40130b47f92755d6b7ab96c56664d4`.
- PR #104 lean workflow [#42520227926](https://github.com/lolokeksu09/Vitrum/actions/runs/42520227926) — success на exact HEAD `62f348f0a2d61dd84a18cf139778cb79cf78c726`; merged в `clone-integration` commit `7b6dad33e5e9b96372fab2ee19262692f364a6de`.
- PR #105 lean workflow [#42520854643](https://github.com/lolokeksu09/Vitrum/actions/runs/42520854643) — success на exact HEAD `7e297fe4a1ee1229aad744d05904d2a6ac7d1996`; merged в `clone-integration` commit `965d1ff9c226edf43baa1aa609764cc6f656e606`.
- PR #106 merged в `clone-integration`: merge `0ead35cae08525cda665b14e308c543389489e0b`; PR workflow [#42521189452](https://github.com/lolokeksu09/Vitrum/actions/runs/42521189452) — success на точном PR HEAD `ba24c370e7ece69a406d925c4babd0b8c94d7c56`.
- `main` не менялся.

### Интеграционные ограничения

- CI использует Android 16 x86_64 для UI-only сценариев. Он не доказывает работу ARM64 native libraries, TUN, Xray traffic или failover на физическом телефоне.
- Не закрыты: TalkBack, контраст, реальные insets/жесты на Lenovo Legion Y70, Android 14/15 на устройстве, light/dark на всей матрице и настоящий VPN-трафик.
- Полный Definition of Done остаётся открытым до прохождения этих gates. Нельзя объявлять весь UI overhaul завершённым только по зелёному CI.

## Политика минимальной CI-проверки — 2026-11-08

Для обычного PR в Clone обязательны только:
- получение проверенных runtime assets;
- проверка покрытия переводов;
- компиляция assembleDebug и assembleClone;
- проверка package identity/метаданных Clone APK и публикация APK artifact.

Не запускаются на каждом PR:
- полный testDebugUnitTest;
- компиляция instrumentation target assembleCloneAndroidTest;
- Android Emulator / UI smoke / screenshot matrix;
- Xray DNS Route Guard fixture suite.

Полная Android 16 UI matrix оставлена доступной вручную через workflow_dispatch. Регрессионные тесты в репозитории не удаляются: они остаются исходным материалом для точечной проверки при изменении соответствующей логики. При изменениях VPN/TUN, DNS, маршрутизации, failover или kill switch перед интеграцией требуется адресная проверка затронутой логики и Xray-конфигурации; обычный UI-only PR не должен оплачивать полный сетевой/UI прогон.

Эта политика сокращает обязательный CI, но не является доказательством корректности VPN runtime на ARM64 или отсутствия сетевых утечек.


## Дополнение — 2026-11-09: PR #111–#112 и следующий этап

**Фактическое состояние после PR #112:** `clone-integration` включает PR #111 и PR #112. `main` не менялся.

- PR #111 — расширенная Android 16 UI capture matrix: 24 onboarding captures (3 страницы × dark/light × RU/EN × font scale 1.0/1.5) и 36 bottom-scroll Settings captures (9 destinations × dark/light × RU/EN при font scale 1.5). Android 16 matrix run [#42571375656](https://github.com/lolokeksu09/Vitrum/actions/runs/42571375656) завершился success на SHA `79b0e7369fa1e1c165306f82283228fc0c9e3ccc`; artifact [#13654358259](https://github.com/lolokeksu09/Vitrum/actions/runs/42571375656/artifacts/13654358259), 745 files / 47.8 MB. В логах были два предупреждения о недоступности свежего UI tree во время capture; зелёный итог матрицы не закрывает визуальную приёмку этих кадров.
- PR #112 — исправлено сжатие длинных license badges в Settings → About при большом font scale: badge ограничен 124 dp, текст лицензии допускает две строки. Lean CI [#42575725348](https://github.com/lolokeksu09/Vitrum/actions/runs/42575725348) success на exact HEAD `99b04b026c84d492ed8d78fb689ed540132607d1`; merged в `clone-integration` squash commit `5f7da58fe143e6ed8f00ea6d64de34b16167b973`.

**Следующие задачи по порядку:**
1. Продолжать реализацию оставшихся UI и функциональных задач в clone-integration.
2. Поддерживать lean CI как build/package gate: Debug + Clone compile, translations/package metadata и APK artifact.
3. После завершения запланированной разработки подготовить diff-аудит main...clone-integration и отдельный promotion PR. Не вливать в main без явного разрешения автора.
4. После пользовательской приёмки устройства подготовить production/pre-release сборку, проверить авторскую подпись, APK checksum, changelog и release notes.

**Device QA не блокирует дальнейшую разработку.** TalkBack, контраст, font scale, жесты/insets, QR/camera, backup/restore, diagnostics и ARM64 VPN runtime проверяет пользователь самостоятельно на Lenovo Legion Y70 после установки APK. Не запускать дополнительные emulator/screenshot/device QA циклы от имени ассистента.



## Дополнение — 2026-11-09: PR #113–#114 и повторная UI-приёмка

- PR #114 merged в `clone-integration`: onboarding Skip не переносится на две строки при увеличенном font scale. Merge commit `afac23fb08de5ebef0909d6ace277ad166a45e49`.
- PR #113 merged в `clone-integration`: длинная версия компонента в Settings → About больше не ограничена двумя строками и доступна в диалоге подробностей; добавлен английский перевод `Версия: {0}` и регрессионный тест. Lean CI run [#42582120704](https://github.com/lolokeksu09/Vitrum/actions/runs/42582120704) — success на точном PR HEAD `a3f6f1cf3ce23f302ab3e9e0bb99a1df81231569`; Codex review этого же HEAD завершён без замечаний; merge commit `523914d6d1bdcfcc63b772366f5027622cbb2bdf`.
- После merge #113 автоматически запущена Android 16 UI matrix [run #42589999517](https://github.com/lolokeksu09/Vitrum/actions/runs/42589999517) на точном merge SHA `523914d6d1bdcfcc63b772366f5027622cbb2bdf`. На момент записи run выполняется; визуальная приёмка ожидает артефакт и просмотр кадров.

**Следующий шаг:** завершить просмотр свежей матрицы About/onboarding, затем закрыть только те ручные gates, для которых есть физические доказательства. ARM64 VPN runtime, TalkBack, системные insets и реальный трафик не подтверждаются Android 16 x86_64 UI matrix. Ветка `main` остаётся нетронутой.


## Решение по device QA — 2026-11-09

**Владелец проверки физического устройства — пользователь. Эти пункты больше не являются блокирующими задачами разработки и не должны останавливать продвижение Clone UI:**
- TalkBack, accessibility focus и реальные touch targets;
- контраст, light/dark и масштаб шрифта на устройстве;
- Back/Close, системные жесты, IME, QR/camera, insets;
- backup/restore и copy diagnostics;
- ARM64 Xray/TUN, DNS/IPv6, реальный VPN traffic, Wi-Fi↔mobile, failover и kill switch на Lenovo Legion Y70.

Проверки будут выполнены пользователем после установки APK. Не запускать дополнительные emulator/screenshot/TalkBack/device QA циклы от имени ассистента и не требовать их закрытия для продолжения плана. Обычный lean CI для компиляции, package identity и APK artifact сохраняется как инженерный build gate. Его результат не следует трактовать как проверку устройства.

**Обновлённый порядок разработки:** продолжать реализацию оставшихся UI/functional задач в clone-integration, поддерживать документацию и сборочный pipeline; затем подготовить promotion diff/PR отдельно. main не менять без явного разрешения. Device QA остаётся отдельной пользовательской приёмкой перед stable release, но не блокирует разработку следующих задач.


## Итог интеграции PR #115 и выдача Clone APK — 2026-11-09

- PR #115 (Compose accessibility semantics) merged в clone-integration, squash merge `e4ab71985ea238e3d4b702d6e87587cf16da124e`. В PR были `Role.Button` для интерактивных строк, checkbox semantics для app picker, локализованное описание help action и 48 dp touch targets. VPN/routing/persistence logic не менялась.
- Lean CI [run #42590455043](https://github.com/lolokeksu09/Vitrum/actions/runs/42590455043) завершился success; Codex review точного PR HEAD `7e615ba7b5b67e237d88d29ce4ffb22de122df5e` завершён без findings.
- APK artifact [#13660913308](https://github.com/lolokeksu09/Vitrum/actions/runs/42590455043/artifacts/13660913308), файл `app-clone.apk`, SHA-256 `fcebe3f343ffe41c040795e0f08af35a2b08709adb9479e66c8c513212d935b5`. Он собран на точном исходном PR HEAD; merge PR #115 не менял содержимое приложения, только включил этот код в clone-integration.
- Физический device QA выполняет пользователь самостоятельно. Не запускать emulator/screenshot/TalkBack QA от имени ассистента.
- Следующие инженерные задачи: сверить оставшиеся пункты этапов PR-01–PR-07 с кодом и закрыть фактически незавершённые функциональные пробелы; затем diff-аудит и promotion PR. `main` не менять без разрешения.


## Дополнение — 2026-11-09: PR #116–#118

Все три PR влиты squash в `clone-integration`; `main` не менялся.

- **PR #116 — безопасный выбор самого быстрого сервера.** Выбор проходит через общий `chooseServerFromUi()`: при активном VPN используется штатное переключение, при busy/blocked показывается существующее предупреждение, ручной выбор сохраняется через штатный путь. CI PR [#42591896709](https://github.com/lolokeksu09/Vitrum/actions/runs/42591896709) — success на точном PR HEAD `a0589338cf613f5f0a0340061bcbc4d0f76d6b40`. Merge commit: `828756da8c2415edbd2c6101c6328ec7fd4f1761`.
- **PR #117 — русские поисковые синонимы Settings.** Добавлены русские aliases для девяти направлений Settings; regression cases покрывают «блокировка приложения», «автозапуск» и «фрагментация mtu». CI PR [#42592010322](https://github.com/lolokeksu09/Vitrum/actions/runs/42592010322) — success на точном PR HEAD `9e1d4cd89f3eebca0ab46b9e4935c8e6c495bb8f`. Merge commit: `071be70bd8180426f4632baab2f3dc657ff70b90`. Поиск остаётся поиском направлений Settings, а не отдельных контролов внутри страниц.
- **PR #118 — доступное закрытие серверной шторки.** Drag handle увеличен с 28 до 48 dp; добавлено действие TalkBack «Закрыть». Drag, scrim tap и system Back сохранены. CI PR [#42592818835](https://github.com/lolokeksu09/Vitrum/actions/runs/42592818835) — success на точном PR HEAD `77007a9841e62c910dea9272d2c9fa4ed8a5b51a`. Merge commit: `d8cfd86c2c47424cd6455d341975b6b5b67ee7d0`.

**CI после merge:** push runs #42592730615 и #42592738568 для merge commits PR #116/#117 ещё отображались как `in_progress` при последней проверке. Их нельзя считать завершёнными по результатам PR CI. Для PR #118 merge-triggered run ожидает отдельной проверки. Эти build jobs не заменяют физическую приёмку устройства, которую выполняет пользователь.

**Следующий инженерный шаг:** продолжить точечный аудит незавершённых сценариев PR-05–PR-07, не менять VPN/TUN/DNS/routing semantics без конкретного дефекта; затем синхронизировать handoff и подготовить diff-аудит `main...clone-integration`. Полный UI overhaul не объявлять завершённым.


## Дополнение — 2026-11-09: PR #122–#124

- **PR #122 — локализация журнала трафика и событий VPN.** Влит в `clone-integration`, merge commit `f9ad92b3857e8da4cba50001e2b455de0737b06f`. Lean CI #42595398523 — success на точном HEAD `d601b2702035985cd0f552e1789f4a8cc69b4a49`. Логика хранения событий, маршрутизация и создание правил не менялись.
- **PR #123 — локализация «Щита приватности».** Открыт; текущий HEAD `1a615f4652f4a4772d129127ca9fa27d5d2bda61`. CI #42596616074: translation coverage passed, Debug/Clone compilation выполняется. Проверки Shield, сетевые запросы, root-вызовы и VPN-поведение не менялись.
- **PR #124 — локализация нижней навигации.** Открыт; HEAD `9183f86842636c3626c40f4df2dd6c0479b35556`. CI #42596724984 выполняется. Подписи «Главная» и «Настройки» проходят через общий `I18n.tr`; порядок и поведение вкладок не меняются.

Android 16 UI matrix остаётся доступной только через ручной `workflow_dispatch`. Физическая приёмка остаётся за пользователем. Полный UI overhaul не завершён; после PR #123–#124 продолжить аудит оставшихся поверхностей и затем сделать интеграционный diff-аудит. `main` не затронут.

## Дополнение — 2026-11-09: локализация PR #122–#127 завершена

Все перечисленные PR слиты squash только в `clone-integration`; `main` не менялся.

- **PR #122 — Journal:** локализованы журнал трафика и события VPN. Lean CI [#42595398523](https://github.com/lolokeksu09/Vitrum/actions/runs/42595398523) success на точном HEAD `d601b2702035985cd0f552e1789f4a8cc69b4a49`; merge `f9ad92b3857e8da4cba50001e2b455de0737b06f`.
- **PR #123 — Privacy Shield:** локализованы диагностика, DNS, IPv4/IPv6, история и root-разделы. Lean CI [#42596616074](https://github.com/lolokeksu09/Vitrum/actions/runs/42596616074) success на точном HEAD `1a615f4652f4a4772d129127ca9fa27d5d2bda61`; merge `909aac3752fa6726be1965af094b165ee3ac7f53`.
- **PR #124 — bottom navigation:** локализованы подписи «Главная» и «Настройки». Lean CI [#42596724984](https://github.com/lolokeksu09/Vitrum/actions/runs/42596724984) success на точном HEAD `9183f86842636c3626c40f4df2dd6c0479b35556`; merge `30868a50f0fed5f030d937271052fdc9de84602c`.
- **PR #125 — Game:** локализованы игровой режим, live-метрики, тест серверов и выбор игр. Lean CI [#42598455257](https://github.com/lolokeksu09/Vitrum/actions/runs/42598455257) success на точном HEAD `7b19ea228fcd106db9459183c4bff06a62ec5483`; merge `8ed514b6a9db762e804ab90677df0349df254831`.
- **PR #126 — Servers:** локализованы каталог, поиск, фильтры, сортировка, состояния пинга, подписки, QR-предупреждение и TalkBack labels. Lean CI [#42599114240](https://github.com/lolokeksu09/Vitrum/actions/runs/42599114240) success на точном HEAD `e5faf9ddfaab5f2779afbf55521a0b9d0690ee05`; merge `f538db5c7e3fd3d673e15cec43ae13eb381586c4`.
- **PR #127 — Routing:** локализованы сводка маршрутов, режимы приложений, профили, правила, игровые подсети, предпросмотр маршрута и подтверждения. Lean CI [#42599655760](https://github.com/lolokeksu09/Vitrum/actions/runs/42599655760) success на точном HEAD `d14eb42d1469e080865ad58abaf8de246c20a372`; merge `17a73b11bc0b18d2646898ed32057a9c0660757b`.

Во всех PR менялся только интерфейс/словарь переводов; поведение VPN, Xray, DNS, сетевые запросы, маршрутизация и сохранение состояния не менялись. Переводные проверки и Debug/Clone build прошли для каждого PR. Пользовательская физическая приёмка на Lenovo Legion Y70 остаётся отдельным gate и выполняется владельцем; не запускать повторные эмуляторные/UI-matrix циклы.

**Следующий шаг:** продолжить точечную сверку оставшихся пунктов UI-плана с кодом; после устранения подтверждённых пробелов обновить handoff и выполнить diff-аудит `main...clone-integration`. Promotion PR в `main` не создавать/не вливать без отдельного разрешения. Полный UI overhaul и stable release не объявлять завершёнными до пользовательской приёмки устройства.

## Дополнение — 2026-11-09: PR #128–#129 — Home и Settings локализованы

- **PR #128 — Home и app shell:** локализованы домашняя панель, состояние соединения, выбранный сервер, игровой статус, HTTP evidence, пустое состояние, сообщения MainActivity, импорт внешних ссылок, обновление приложения и оставшиеся сообщения onboarding. Lean CI [#42600367598](https://github.com/lolokeksu09/Vitrum/actions/runs/42600367598) success на точном HEAD `ee63c9c73a5b830f299fffa2e78269c64d4443d2`; merge `2b4c5858601a878e7e61ebbc974152db3dd3d768`.
- **PR #129 — Settings:** локализованы все страницы Settings, поля, кнопки, segmented controls, диалоги, динамические статусы, пояснения Kill switch/App Lock/root/DNS/MTU, статистика, диагностика, glossary и accessibility labels. Lean CI [#42600993273](https://github.com/lolokeksu09/Vitrum/actions/runs/42600993273) success на точном HEAD `4dd9fa19fd5de762998f069b20470d72b21b410a`; merge `265f1e109f29fe48a6449321d0a282c502b633a9`.

Оба PR меняли только отображаемые строки и словарь. Callbacks, настройки, persistence, root-команды, VPN/Xray/DNS и маршрутизация не менялись. main не затронут.

**Дальше:** провести финальную сверку UI-плана с текущим кодом и определить только реально незакрытые пункты; затем подготовить diff-аудит main...clone-integration. Не запускать повторные emulator/UI-matrix/TalkBack/device QA циклы — физическую приёмку выполняет пользователь. Promotion в main не выполнять без отдельного разрешения. Полный UI overhaul и stable release не объявлять завершёнными до пользовательской приёмки.

## Дополнение — 2026-11-09: PR #130 — About, Backup и диалоги локализованы

- **PR #130:** локализованы страница About (подпись, источник установки, отчёт, компоненты/лицензии и службы), резервное копирование/восстановление, ошибки backup, блокировка смены сервера, accessibility label щита и подпись профиля подписки. Lean CI [#42601811790](https://github.com/lolokeksu09/Vitrum/actions/runs/42601811790) success на точном HEAD `dbe98fd766161cf7478d49ada460ad1660d1a7ac`; merge `7585b4244f03a2a8dbabd18dd427f6d10a20f7f8`.

Финальная сверка основных UI-файлов: все найденные прямые русские строки в Text/диалогах/подписях переведены либо передаются в общий переводящий компонент (RayConfirmDialog/RayDialogFrame/GroupTitle/SwitchRow). Технические значения, названия протоколов, лицензии и исходный текст отчёта сохранены без перевода там, где это необходимо.

**Оставшиеся gates:** физическая приёмка на Lenovo Legion Y70 (визуальный вид, TalkBack, font scale, реальные системные диалоги и VPN runtime) выполняется пользователем. Emulator/UI matrix и повторные скриншотные проверки не запускать. После этого нужен отдельный diff-аудит main...clone-integration; не вливать в main без отдельного разрешения. UI overhaul не объявлять полностью завершённым до device QA.

## Финальная сверка интеграционной ветки — 2026-11-09

GitHub compare main...clone-integration: **127 commits ahead, 0 behind, 40 changed files**. В diff входят UI-файлы/ресурсы, план/handoff, CI и инструменты Clone; новых файлов релиза или изменений main нет.

Проверены основные non-UI изменения в diff:
- Subscription/server safety: удаление выбранного сервера, удаление подписки, отключение JSON auto-servers и обновление подписки не должны незаметно убрать текущий route во время connected/busy/blocked/switching. При исчезновении активного выбранного сервера обновление подписки отклоняется до отключения VPN.
- Demo isolation: синтетические UI fixtures не должны сохраняться в пользовательский профиль; AppState.save блокируется при активном Demo. Demo states не запускают VPN.
- GeoData: проверенный поток записывается во временный файл, ограничен MAX и публикуется только после SHA-256 проверки; временный файл удаляется при ошибке.
- Pinger: Native.stop вызывается только при положительном PID, чтобы не инициализировать JNI для процесса, который не стартовал.
- FETCH_BINARIES.sh: fallback использует официальный Xray Releases Assets API, asset выбирается по имени из метаданных релиза; SHA-256 .dgst остаётся обязательным.
- PR CI оставлен lean: translation coverage, Debug/Clone compile, Clone package metadata и APK artifact. Полные unit/instrumentation/DNS fixture suites не входят в обязательный PR gate по принятому решению.
- Android 16 UI matrix workflow — только ручной workflow_dispatch; автоматических PR/push триггеров нет. Устаревшая формулировка в AGENTS.md исправлена.

**Решение:** интеграционный diff не вливать автоматически. Из-за 127 commits ahead это отдельная promotion-проверка и отдельное разрешение. Физическая приёмка на устройстве остаётся незавершённым gate; UI overhaul не считается полностью принятым до неё.
