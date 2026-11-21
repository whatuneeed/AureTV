# GitHub release kit — AURE TV 0.6.0

Материалы для страницы GitHub Release.

## Статус

- Релиз опубликован: https://github.com/whatuneeed/AureTV/releases/tag/v0.6.0
- APK и `CHECKSUMS.txt` прикреплены к релизу.
- APK: 6 960 986 байт, SHA-256 `df6b96724c3feeef1d6025fc5046a0c890cedacbd89adba04ca5f266639b120a`.

## Состав

| Файл | Назначение |
| --- | --- |
| `RELEASE_NOTES_0.6.0.md` | текст релиза на английском |
| `RELEASE_NOTES_0.6.0.ru.md` | текст релиза на русском |
| `screenshots/00-cover.png` | обложка AURE TV |
| `screenshots/01…10*.jpg` | полноразмерные скриншоты 1920×1080 |
| `AURE-TV-0.6.0.apk` | готовый подписанный APK |
| `CHECKSUMS.txt` | SHA-256 и имя APK |
| `AURE-TV-0.6.0.apk.sha256` | та же сумма в формате `sha256sum` |

Основная страница репозитория — `README.md` в корне; русская версия —
`README.ru.md`. В них вставлена галерея из `screenshots/`.

## Проверка релиза

1. Открыть **Releases → AURE TV 0.6.0**.
2. Убедиться, что `AURE-TV-0.6.0.apk` и `CHECKSUMS.txt` на месте.
3. Сверить SHA-256 с `CHECKSUMS.txt`.
4. Проверить, что скриншоты и README открываются на GitHub.

## Важно

- Это первый публичный релиз; физический Android TV, 4K/HDR, реальный пульт и
  TalkBack ещё не проверены. Дисклеймер уже есть в release notes.
- В 0.6.0 встроенный канал обновлений ещё не был настроен. Начиная с 0.6.1
  сборка проверяет `https://whatuneeed.github.io/AureTV/update.json`, поэтому
  обновиться с 0.6.0 на 0.6.1 можно только вручную, а последующие версии —
  уже из лаунчера.
- APK подписан тем же ключом, который нужен для будущих обновлений.
