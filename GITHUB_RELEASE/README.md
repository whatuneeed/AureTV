# GitHub release kit — AURE TV 0.6.3

Материалы для страницы GitHub Release. Версия подготовлена к публикации:
APK собран, тесты пройдены, release gate пройден.

## Статус

- Версия: **0.6.3**, `versionCode 157`.
- APK: `AURE-TV-0.6.3.apk`, 6 961 042 байта.
- SHA-256: `762fec93a9313c3f76abcbeb2ae57cd7e2f5171cf645d92d5ecdaa3ca43d57d6`.
- Подпись: APK Signature Scheme v2, тот же сертификат, что у 0.6.2.
- Metadata: `docs/update.json` остаётся на 0.6.2 до создания релиза; workflow
  `publish-update.yml` переключит его на 0.6.3 после публикации.

## Состав

| Файл | Назначение |
| --- | --- |
| `RELEASE_NOTES_0.6.3.md` | текст релиза на английском |
| `RELEASE_NOTES_0.6.3.ru.md` | текст релиза на русском |
| `screenshots/00-cover.png` | обложка AURE TV |
| `screenshots/01…10*.jpg` | полноразмерные скриншоты 1920×1080 |
| `AURE-TV-0.6.3.apk` | подготовленный подписанный APK (не в git) |
| `CHECKSUMS.txt` | SHA-256 и имя актуального APK |
| `CHECKSUMS-0.6.3.txt` | отдельная сумма для 0.6.3 |
| `AURE-TV-0.6.3.apk.sha256` | та же сумма в формате `sha256sum` |

`AURE-TV-0.6.0…0.6.2.apk` и их release notes остаются как история предыдущих
релизов.

## Проверка перед публикацией

1. Release gate прошёл для текущего APK.
2. Тег `v0.6.3` и GitHub Release созданы, APK и `CHECKSUMS.txt` прикреплены.
3. Workflow `publish-update.yml` обновил `docs/update.json` на 0.6.3
   (`versionCode 157`).
4. Проверены HTTPS-скачивание APK, совпадение SHA-256 и metadata.
5. Проверено обновление 0.6.2 → 0.6.3 через штатный экран обновления.

## Важно

- 0.6.0 не умеет обновлять себя; 0.6.1 — первая сборка с каналом обновлений.
- APK подписан тем же ключом, который нужен для будущих обновлений.
- Физический Android TV, реальные пульты, 4K/HDR и TalkBack по-прежнему
  остаются непроверенными.
