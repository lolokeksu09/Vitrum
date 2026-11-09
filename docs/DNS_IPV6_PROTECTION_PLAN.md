# VITRUM 2.5.0 — DNS & IPv6 Protection

Состояние на 2026-11-04: реализация объединена с `main` через PR #64, версия 2.5.0 и release workflow — через PR #65. Пройдены JVM-тесты, сборки debug/clone, Linux Xray 26.3.27 `run -test` для трёх конфигов без Android TUN-входа и Android 16 UI smoke. **Официальный APK 2.5.0 пока не выпущен:** предыдущий запуск публикации был заблокирован старой TUN-проверкой; после исправления release workflow требуется запустить заново. Проверка на ARM64 устройстве не выполнялась.

## Изученные проекты и архитектура

- [Orbot](https://github.com/guardianproject/orbot-android/blob/master/app/src/main/java/org/torproject/android/service/vpn/OrbotVpnManager.java): Android `VpnService.Builder` задаёт IPv4/IPv6-маршруты и DNS-адрес в TUN. [TorConfig.kt](https://github.com/guardianproject/orbot-android/blob/master/app/src/main/java/org/torproject/android/service/tor/TorConfig.kt) задаёт `DNSPort` и `TransPort`. Это архитектурный пример, а не код для копирования в Xray.
- [RethinkDNS](https://github.com/celzero/rethink-app/blob/main/app/src/main/java/com/celzero/bravedns/service/BraveVPNService.kt): DNS-маршруты и системные `addDnsServer` / IPv4/IPv6 catch-all отличаются от режима DNS-only, где разрешён обход отдельных семейств IP.
- [NetGuard](https://github.com/M66B/NetGuard/blob/master/app/src/main/java/eu/faircode/netguard/ServiceSinkhole.java): явные VPN-маршруты и DNS-серверы. Его обработчик пакетов не переносим напрямую в Xray.
- [sing-box](https://github.com/SagerNet/sing-box): отдельный прокси-движок с собственными DNS-правилами; VITRUM сохраняет Xray и существующий TUN.

VITRUM уже устанавливает `0.0.0.0/0`, `::/0` и системный DNS в `RayVpnService.kt`; в `ConfigBuilder.kt` DNS через Xray на порту 53 явно перехватывается только при `doh || blockV6 || fakeDns`. В остальных режимах общий `defaultAction=direct`, геоправила или режим обхода могли влиять на DNS TCP/UDP 53. Запрос DNS из исключённого приложения не входит в VPN и не управляется VITRUM.

## Изменения (проверяемые инварианты)

1. **DNS Route Guard:** при `doh || blockV6 || fakeDns` оставить существующее перенаправление DNS 53 в Xray `dns-out`. В остальных случаях добавить явное правило TCP/UDP 53 → `proxy` (или основной балансировщик авто-подписки). Оно располагается после пользовательских правил, но **раньше** `bypassLan`, `exceptRu`, пресетов и `defaultAction`. Явная пользовательская маршрутизация DNS остаётся доступной.
2. **Порядок и совместимость:** не менять формат профилей и хранилище, приложение `app.rayclient`, VpnService Builder, TUN, Xray, ключ подписи или существующие исключения приложений. Не создавать слушающие TCP-порты.
3. **Диагностика:** отдельно показывать запланированную политику DNS (настройки/маршруты) и фактическую достижимость IPv4/IPv6 через диагностический Xray-сокет. Статус DNS-утечек остаётся `UNKNOWN` без независимого наблюдателя. `blockV6` в маршруте `tun-in` не делает автоматически недоступным IPv6 на `probe-in`.
4. **Тесты:** порты TCP/UDP 53 при default `proxy/direct/block`; DoH/FakeDNS/IPv6 block; порядок пользовательских правил и `bypassLan`/региональных обходов; авто-балансировщик; фиксированные IP-семейства; отсутствие ложного подтверждения DNS leak-free.
5. **Релиз:** CI (`testDebugUnitTest`, `assembleDebug`, `assembleClone`, UI smoke); Xray `run -test` для релевантных конфигов где доступно; signed author APK только после успешной release-сборки. До реальных Android-тестов — pre-release.

## Границы безопасности и ограничения

- Перевод DNS 53 с прямого маршрута на прокси в режимах с `defaultAction=direct` является **изменением поведения**. Локальные резолверы в LAN при отсутствии явного direct-правила могут стать недоступны через прокси; пользователям такого DNS нужно предусмотреть исключение. В существующем UI это должно быть явно описано.
- Приложения, отправляющие DoH/DoT (443/853), частные DNS-резолверы Android, доменные/IP-правила, исключённые приложения и чужой VPN требуют отдельного анализа. Перехват 53 сам по себе не гарантирует отсутствие DNS-утечек.
- Доступность IPv6 через `probe-in` не подтверждает маршруты приложений, а таймаут IPv6 не является доказательством утечки или её отсутствия.
- Агрегированный DNS Monitor нельзя честно реализовать без корректного источника событий Xray/TUN; не добавлять выдуманные счётчики или фоновые сетевые обращения.
- Реальную маршрутизацию в Android и утечки можно подтвердить только независимой проверкой на устройстве.

## Приёмка

- Конфиги с `defaultAction=direct` без специального DNS-режима содержат явное правило TCP/UDP 53 → proxy выше catch-all direct.
- Существующая опция `doh||blockV6||fakeDns` по-прежнему ведёт порт 53 в `dns-out`; `blockV6` не выпускает ::/0 в direct.
- Отдельное правило пользователя для DNS 53 имеет приоритет. Нельзя объявлять политику `100% безопасной` только по конфигу.
- Официальная сборка содержит ту же подпись и проверяется в GitHub Actions.
