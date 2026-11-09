# Vitrum: инструкции для Claude Code

Полные правила: @AGENTS.md
Актуальное состояние проекта: docs/HANDOFF.md.
Публичная история выпусков: CHANGELOG.md и GitHub Releases.

- Отвечай по-русски и кратко; сохраняй точные имена файлов, переменных и технологий.
- Разделяй факты из исходников, результаты CI и проверку на физическом устройстве.
- Не заявляй о проверке на устройстве: её выполняет автор.
- Не печатай и не коммить ключ подписи, пароль, токены, URL подписок и реальные данные серверов.
- Не нарушай инварианты безопасности из AGENTS.md.
- Не меняй VPN/TUN/DNS/routing semantics без конкретного дефекта и отдельного согласования.
- Основная сборка — .github/workflows/build.yml; Lite ARM32 — .github/workflows/lite.yml; PR checks — .github/workflows/pr-check.yml.
- Устаревшие publisher/retry/probe workflows удалены. Не используй старые release notes как описание текущего состояния.
- Команды сборки: tools/SETUP_ENV.sh, tools/FETCH_BINARIES.sh --linux, tools/build.sh tests|debug|release.
