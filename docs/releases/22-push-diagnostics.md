# Выпуск 22: тестовый пуш, лог отправок, бейдж одним запросом

Ветка: `feature/push-diagnostics` → `develop` (тест) → `main` (прод)
Только backend, приложение менять не нужно.

## Зачем

На вопрос «работают ли пуши» по логам нельзя было ответить: успешные отправки не записывались, а проверить доставку можно было только перепиской двух аккаунтов. Бейдж для каждого получателя считался через список чатов каждой его компании — сотни запросов на одно сообщение в большой компании.

## Что изменилось

### Тестовый пуш

```http
POST /api/devices/test
Authorization: Bearer {accessToken}
→ 200
[
  { "platform": "IOS", "environment": "sandbox", "tokenEnd": "…bb22cc33", "result": "SENT", "status": 200, "reason": null, "removed": false },
  { "platform": "IOS", "environment": "production", "tokenEnd": "…dd44", "result": "INVALID_TOKEN", "status": 400, "reason": "BadDeviceToken", "removed": true }
]
```

* Уведомление «BirChat — Тестовое уведомление» («Сынақ хабарламасы» для `kk`) на все устройства пользователя, сразу, с ответом Apple / Google по каждому.
* `result`: `SENT` — принято к доставке; `INVALID_TOKEN` — токен недействителен, устройство удалено (`removed: true`); `FAILED` — другая ошибка, см. `status` и `reason`; `NOT_CONFIGURED` — на сервере нет ключа (APNs или Firebase).
* Пустой список — у пользователя нет зарегистрированных устройств.
* Не чаще раза в 10 секунд, иначе `429 TOO_MANY_ATTEMPTS`.
* Частые причины: `BadDeviceToken` — сборка из Xcode зарегистрировалась с `production` (или TestFlight с `sandbox`); `DeviceTokenNotForTopic` — Bundle ID приложения не `kz.birchat.app`; `InvalidProviderToken` — неверный ключ APNs.

### Лог отправок

Одна строка на сообщение: `Push sent. messageId=… devices=N sent=N invalid=N failed=N`. Тестовый пуш тоже пишет строку `Test push. userId=… results=[IOS/sandbox=SENT, …]`. В Render → Logs поиск по `Push sent`.

### Бейдж одним запросом

`ChatMessageRepository.UNREAD_TOTAL_SQL` — непрочитанные по всем чатам всех компаний одним запросом по тем же правилам, что `unreadCount` в списке чатов. Используется для пушей о сообщениях, тихого бейджа и тестового пуша.

## Тесты

* `PushNotificationServiceTest`: тестовый пуш — ответ по каждому устройству, мёртвый токен удалён, Android без ключа — `NOT_CONFIGURED`, повтор раньше 10 секунд — `429`; бейдж ограничен 9999.
* `FlywayMigrationsPostgresTest`: `UNREAD_TOTAL_SQL` на настоящем PostgreSQL — прочитанное, удалённое, свои сообщения, своя и чужая группа, удалённая группа, личный чат, директор.

## Откат

Rollback деплоя на Render. Миграций нет.
