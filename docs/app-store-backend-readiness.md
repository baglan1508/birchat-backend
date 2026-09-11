# BirChat Backend · App Store Readiness

Дата обновления: `2026-09-11`
Backend prod: `https://birchat-backend.onrender.com`
Swagger prod: `https://birchat-backend.onrender.com/swagger-ui.html`

---

# 1. Статус блокеров перед App Store

| # | Блокер | Статус | Комментарий |
|---|---|---|---|
| 1 | Удаление аккаунта | Готово | Реализован `DELETE /api/users/me?userId={userId}` |
| 2 | Реальная SMS | Готово | SMS отправляется через SMSC.kz, `testCode` из ответа убран |
| 3 | Хостинг без sleep | Готово | Render переведён на платный инстанс |
| 4 | JWT вместо `userId` в query | Позже | Не блокирует первую подачу, но нужен до публичного релиза |

---

# 2. Удаление аккаунта

## Endpoint

```http
DELETE /api/users/me?userId={userId}
```

## Успешный ответ

```http
204 No Content
```

## Ошибки

```text
400 VALIDATION — отсутствует userId или некорректный UUID
404 USER_NOT_FOUND — пользователь не найден
```

## Логика удаления

```text
1. Пользователь становится inactive.
2. Пользователь выходит из всех компаний.
3. Сообщения пользователя остаются в рабочей переписке компании.
4. Файлы пользователя остаются в компании.
5. Номер телефона освобождается.
6. Повторный вход с тем же номером создаёт нового пользователя.
```

## Решение по единственному директору

Удаление аккаунта не блокируется.

```text
LAST_DIRECTOR не используется.
```

Если удаляемый пользователь был единственным директором компании:

```text
1. Если есть другие active-участники, backend назначает директором самого старого active-участника.
2. Если других active-участников нет, компания остаётся без active-участников.
```

---

# 3. Реальная SMS-авторизация

## Отправка кода

```http
POST /api/auth/send-code
```

Request:

```json
{
  "phone": "+77005554433"
}
```

Response:

```json
{
  "message": "Код подтверждения отправлен"
}
```

Поле `testCode` больше не возвращается.

## Проверка кода

```http
POST /api/auth/verify-code
```

Request:

```json
{
  "phone": "+77005554433",
  "code": "4821"
}
```

Response:

```json
{
  "userId": "user-uuid",
  "phone": "+77005554433",
  "fullName": "Новый пользователь",
  "displayName": "Пользователь",
  "initials": "П",
  "accessToken": "mock-access-token-user-uuid"
}
```

## Правила SMS-кода

```text
Длина кода: 4 цифры
TTL кода: 300 секунд
Лимит попыток: 5
Повторная отправка: не чаще 1 раза в 60 секунд
Хранение в БД: hash кода, не plain-text
```

## Ошибки

```text
INVALID_CODE       — код неверный
CODE_EXPIRED       — срок действия кода истёк
TOO_MANY_ATTEMPTS  — превышено количество попыток или слишком частая отправка кода
BAD_REQUEST        — ошибка отправки SMS через провайдера или неверная настройка SMS
```

## Demo-доступ для App Store Review

Есть whitelist demo-номер с фиксированным кодом. Для него SMS не отправляется.

```text
Demo phone/code передаются в App Store Review Notes отдельно.
Не публиковать demo-доступ в публичной документации.
```

---

# 4. Render без sleep

Prod backend переведён с Free на платный Render-инстанс.

Проверка:

```http
GET https://birchat-backend.onrender.com/api/health
```

Ожидаемо сервис должен отвечать без cold start после простоя.

---

# 5. JWT

JWT пока не реализован.

Текущий временный режим:

```text
userId передается в query-параметрах.
actorUserId временно используется для действий от имени пользователя, например добавление сотрудника.
accessToken в verify-code пока mock-строка.
```

План перехода:

```text
1. verify-code начинает возвращать настоящий accessToken.
2. Клиент добавляет Authorization: Bearer {token} через ApiClient interceptor.
3. Backend определяет пользователя из токена.
4. На переходный период backend принимает и Bearer token, и старый userId в query.
5. После переходного периода userId/actorUserId из query удаляются.
```

---

# 6. Smoke test перед App Store

Проверить на prod:

```bash
flutter test --tags live --run-skipped test/live_backend_test.dart
```

Минимальный backend smoke вручную:

```text
1. GET /api/health
2. POST /api/auth/send-code
3. POST /api/auth/verify-code
4. GET /api/users/me?userId=...
5. GET /api/companies/my?userId=...
6. GET /api/companies/{companyId}/home?userId=...
7. GET /api/companies/{companyId}/chats/general/messages?userId=...&limit=50
8. POST /api/companies/{companyId}/chats/general/messages
9. POST /api/companies/{companyId}/files/upload?userId=...
10. GET /api/companies/{companyId}/files/{fileId}/download-url?userId=...
11. POST /api/companies/{companyId}/chats/general/messages/file
12. POST /api/companies/{companyId}/chats/general/read?userId=...
13. DELETE /api/users/me?userId=...
```

---

# 7. Prod env без секретов

На Render должны быть настроены переменные:

```env
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...

SUPABASE_URL=...
SUPABASE_SERVICE_KEY=...
SUPABASE_BUCKET=birchat-files

SMS_PROVIDER=smsc
SMSC_LOGIN=...
SMSC_PASSWORD=...
SMSC_SENDER=

AUTH_SMS_CODE_TTL_SECONDS=300
AUTH_SMS_RESEND_COOLDOWN_SECONDS=60
AUTH_SMS_MAX_ATTEMPTS=5
AUTH_SMS_DEMO_PHONE=...
AUTH_SMS_DEMO_CODE=...
AUTH_SMS_OTP_SECRET=...
```

Секреты не хранить в Git.
