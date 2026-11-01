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

## Issue #40: ручная проверка DNS / IPv4 / IPv6 на ARM64

Это **план**, не результат теста. Проверка HTTP по служебному Xray-сокету внутри Shield не доказывает, что браузер или другие приложения идут через Android TUN. Само приложение VITRUM исключено из TUN для предотвращения VPN-петли.

1. Установить тестовый Clone APK; записать Android/прошивку, commit SHA, SHA-256 APK, дату и тип сети (Wi-Fi или мобильная). Не записывать ключи, ссылки серверов или сырые домены запросов.
2. Выбрать профиль со значением «Программы → Все» и «Прочий трафик → Через VPN». **Явно проверить** отсутствие правил «Напрямую», обхода РФ/локальной сети и исключений для тестового браузера; если исключения нужны, документировать их отдельно.
3. Подключить VPN. В «Щит приватности» вручную нажать «Проверить IPv4 / IPv6». Записать только состояния и время отклика. Успех IPv4/IPv6 означает **HTTP-ответ через служебный сокет Xray**, а не отсутствие утечки. Отказ или таймаут означает UNKNOWN, не «IPv6 заблокирован».
4. В **другом браузере, включённом в Android VPN**, открыть общедоступный DNS leak test и IPv4/IPv6 connectivity test. Зафиксировать поддерживаемые адресные семейства и видимую сеть/резолверы без полного IP или личных DNS-имён. Проверить, что DNS-серверы не принадлежат неожиданному провайдеру, учитывая известные настройки DNS/DoH. Сопоставление является ручным наблюдением, не автоматическим утверждением VITRUM.
5. Отдельно повторить при «Не использовать IPv6» включено/выключено; при включении IPv6 может быть недоступен, но это само по себе не доказывает отсутствие обхода TUN. Повторить на Wi-Fi и мобильной сети, а также при переключении между ними.
6. Повторить с браузером в списке исключённых приложений и с системным Always-on/lockdown: прямой трафик исключённых приложений **ожидаем**, если Android допускает его; не маркировать это утечкой без учёта режима. При lockdown проверять системное поведение отдельно.
7. STOP/переподключение/потеря сети: устаревший результат в Shield должен исчезать; при включённой системной блокировке проверить, что внешний браузер не получает прямой доступ в момент остановки или потери туннеля.

Критерии отчёта: для каждого сценария указать PASS/FAIL/UNKNOWN, наблюдаемый маршрут браузера, настройки приложений/правил и что именно проверялось. Нельзя ставить «DNS/IPv4/IPv6 leak-free» по одному удачному ответу с Unix-сокета. Никакой автоматической телеметрии или фоновых запросов.
