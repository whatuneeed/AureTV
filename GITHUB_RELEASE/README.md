# GitHub release kit — AURE TV 0.6.2

Материалы для страницы GitHub Release. Релиз **опубликован**, APK и checksums
загружены, канал обновлений переключён на 0.6.2.

## Статус

- Версия: **0.6.2**, `versionCode 156`.
- APK: `AURE-TV-0.6.2.apk`, 6 961 038 байт.
- SHA-256: `fb27b0902ed3bc8a04740d50916074b43e10785508e991a655e7bce031cde4e7`.
- Подпись: APK Signature Scheme v2, тот же сертификат, что у 0.6.1.
- Релиз: https://github.com/whatuneeed/AureTV/releases/tag/v0.6.2
- Metadata: `docs/update.json` обновлён workflow до `versionCode 156`.

## Состав

| Файл | Назначение |
| --- | --- |
| `RELEASE_NOTES_0.6.2.md` | текст релиза на английском |
| `RELEASE_NOTES_0.6.2.ru.md` | текст релиза на русском |
| `screenshots/00-cover.png` | обложка AURE TV |
| `screenshots/01…10*.jpg` | полноразмерные скриншоты 1920×1080 |
| `AURE-TV-0.6.2.apk` | подготовленный подписанный APK (не в git) |
| `CHECKSUMS.txt` | SHA-256 и имя актуального APK |
| `CHECKSUMS-0.6.2.txt` | отдельная сумма для 0.6.2 |
| `AURE-TV-0.6.2.apk.sha256` | та же сумма в формате `sha256sum` |

`AURE-TV-0.6.1.apk`, `CHECKSUMS-0.6.1.txt` и release notes 0.6.1 остаются как
история предыдущего релиза.

## Проверка перед публикацией

1. Убедиться, что release gate прошёл для текущего APK.
2. Тег `v0.6.2` и GitHub Release созданы, APK и `CHECKSUMS.txt` прикреплены.
3. Workflow `publish-update.yml` обновил `docs/update.json` на 0.6.2
   (`versionCode 156`).
4. Проверены HTTPS-скачивание APK, совпадение SHA-256 и metadata.

## Важно

- 0.6.0 не умеет обновлять себя; 0.6.1 — первая сборка с каналом обновлений.
- APK подписан тем же ключом, который нужен для будущих обновлений.
- Физический Android TV, реальные пульты, 4K/HDR и TalkBack по-прежнему
  остаются непроверенными.
