# Vitrum Lite ARM32 — Samsung Galaxy Tab S 10.5 LTE

## Цель
Третий независимый Android-пакет `app.rayclient.lite` для SM-T805 (Exynos 5420, ARMv7, 3 ГБ RAM) на Android 6.0.1 (API 23).
Он **не является** урезанным визуальным режимом основного APK, а собирается отдельно из исходников. Оригинал и клон не изменяются.

## Что сохраняется
- Добавление одиночной ссылки VLESS или Trojan с TLS/Reality и TCP/WS.
- Системный запрос VPN, TUN, отдельная VPN-служба и уведомление.
- Кнопки Подключить/Отключить и отображение ошибок.
- Защита от петли: UID самой программы исключён из VPN при помощи VpnService.Builder.addDisallowedApplication.

## Что убрано
- Jetpack Compose/Material3, анимации, 300dp-кнопка, эффекты и сложные вкладки.
- Геобазы, обход регионов, игровая телеметрия и длительные постоянные замеры.
- Автоподписки, QR-сканер, root-инструменты и сложные правила маршрутизации.
- Сохранение ссылок между запусками: они существуют только в поле текущего экрана.

## Архитектура
- Java + стандартные Android Views; без библиотек UI.
- Отдельный `:lite` Gradle module: API23, ARMv7, пакет `app.rayclient.lite`.
- Xray v26.3.27 cross-compiled from upstream tag via `GOOS=android GOARCH=arm GOARM=7` and NDK r27c. No Linux binary is repackaged as an Android executable.
- `libspawn.so` cross-compiled for the same 32-bit architecture; Android application-private native lib location used for exec.

## Критерии приёмки
CI проверяет: Go build, JNI, ELF32 ARM DYN/PIE, minSdk23, single ABI, APK signature, Gradle compilation. Это **не** подтверждает работу Xray на Android 6.
Для физического SM-T805 обязательно проверить:
1. Установка APK, открытие и поворот экрана.
2. Импорт валидной ссылки VLESS/Trojan, корректную ошибку для неподдерживаемого VMess.
3. Системный VPN-диалог, запуск Xray, реальный доступ к IPv4 и IPv6, DNS и отсутствие утечек.
4. Отключение, отозванное разрешение VPN, краш Xray и корректное завершение.
5. Контроль температуры, RAM и нагрузки в течение 15–20 минут.
6. Проверка обновления подписи: одноразовые debug-ключи несовместимы с предыдущей установкой.

**Ограничение:** у Android 6.0.1 старые security patches; успешная VPN-сессия не исправляет уязвимости самой ОС.

## Создание APK
На Linux с Android SDK 35, NDK 27.2.12479018, Go 1.26 и JDK21:
```bash
bash tools/build-lite-arm32.sh
./gradlew :lite:assembleDebug
bash tools/verify-lite-apk.sh lite/build/outputs/apk/debug/lite-debug.apk
```
Результат отдельно в GitHub Actions, затем отдельный экспериментальный prerelease `lite-...`, без изменения официального `v1.0.0`.
