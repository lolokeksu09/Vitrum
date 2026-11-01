# VITRUM — Android runtime regression matrix

Manual verification checklist, **not a record of successful tests**. Do not mark any row as passed without device evidence.

Target: Android 14, 15 and 16; arm64; verified APK hash and commit.

| Scenario | Action | Expected |
| --- | --- | --- |
| Foreground-service rejection | Disable/restrict service launch using device-supported testing tools | No Xray after rejection; visible failure and stopped service |
| Background startup | Connect from tile, widget, schedule and boot where supported | VPN starts or a clear refusal, no crash |
| Network changes | Switch Wi-Fi to mobile and back while carrying traffic | Reconnect; kill switch does not leak |
| Xray crash | Terminate the Xray child in a controlled test | Recover or keep traffic blocked if kill switch is enabled |
| VPN permission revoked | Revoke VPN in system Settings | Cleanup, UI and notification consistent |
| Always-on and lockdown | Enable Android Always-on and block traffic without VPN, restart | System policy honored; excluded apps separately checked |
| AutoStore storage failure | Fail storage/Keystore write in instrumentation | Old list/selected server intact; error shown |
| Corrupt JSON cache | Corrupt test-only autosub.dat and restart | Never overwritten, no silent loss |
| Backup over 8 MB | Select oversized .vitrumbak | Rejected with bounded reading |
| Author-signed update | Check available official release | Only author-signed APK offered |
| Debug update | Check from ephemeral debug-fallback APK | No incompatible in-place update offered |
| Accessibility | Home, Servers, Routing, Shield, Settings at font 1.0, 1.3, 1.5, TalkBack, dark theme | Controls identifiable and not clipped |
| Home short height | Rotate/use small window height while connected and disconnected | Connect, status, server card and controls reachable by scrolling, no screen overlap |
| UI scaled text | Set 1.3 and 1.5 font scale | Group header help, segmented, floating tabs and all controls visible at minimum 48 dp targets |
| Servers empty results | Add only a test server, search nonsense and activate favorites | Filter-empty explanation and reset; selection and favorites not mutated |
| Routing bypass warning | Enable selected-only or exclude-apps, and/or an explicit direct rule | The screen warns of possible bypass; no unexpected route config changes |
| Shield unknown evidence | Run without network self-check and then with it | Unknown is labeled and not presented as verified protection |
| Settings search | Find DNS, Protection, Root; search nonexistent words | Accurate filter, informative no-results, reset and Back navigation |
| Diagnostics privacy | Trigger a diagnosis, navigate away, then open result | Result remains visible and shows a warning before copy |
| Two-app isolation | Install official and clone on same phone | Both icons available, distinct settings, at most one VPN active |
| Clone replace/install | Install new ephemeral-signed clone over older clone | If signature differs Android refuses update; back up clone before uninstall |

Record Android version, device, APK checksum, commit, reproducible steps, expected/actual and redacted logcat. Never include credentials or subscription addresses.

## Ручное переключение сервера A → B без отключения VPN (PR #56)

План для ARM64 Android 16; не помечать PASS, пока тест реально не выполнен.

| Сценарий | Действие | Ожидается |
| --- | --- | --- |
| Обычная смена | Подключить A, нажать B на экране «Серверы» | TUN остаётся открытым; статус «Переключение»; выбранным становится B только после ответа HTTP через его Xray Unix-сокет |
| Повторная смена | Из B вернуться в A | Один активный PID Xray; отображаемое имя и реальный маршрут согласованы |
| Неотвечающий сервер | Во время A выбрать заведомо неотвечающий тестовый B | B не сохраняется как выбранный; старый A восстановлен; не показывать B как «Подключено» |
| Отказ восстановления | Имитировать сбой подготовки/запуска и A, и B | VPN TUN остаётся открытым, Xray остановлен, blocked=true; никаких обещаний leak-free без отдельного теста |
| STOP в процессе | Нажать STOP до окончания DNS/HTTP новой цели | Старый watcher не возобновляет Xray и не меняет selectedId после STOP |
| Серия нажатий | Быстро нажать B, C, D | Выполняется максимум одна смена за раз; выбранный ID соответствует фактической проверенной цели |
| Сеть и IPv6 | Повторить Wi-Fi↔LTE, включить/выключить IPv6 и проверить внешнюю сеть браузером под TUN | Результат зафиксирован для каждого семейства отдельно; DNS/IPv6 остаются UNKNOWN без наблюдений |
| Lockdown | Включить системную блокировку трафика без VPN | Внешний трафик в окно остановки Xray не появляется; проверить браузером внутри VPN |

Указывать SHA APK, версию Android, сценарий, наблюдение и PASS/FAIL/UNKNOWN; не прикладывать сырые ссылки серверов, ключи или пользовательские DNS-имена. Эмуляторные CI-тесты не подтверждают безопасность реальной маршрутизации.
