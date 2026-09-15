# BirChat Backend · App Store Readiness

Дата обновления: `2026-09-15`
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

Дополнительно после закрытия App Store блокеров реализованы:

```text
1. GET /api/companies/{companyId}/search?userId={userId}&query={text}&limit=20
2. POST /api/companies/{companyId}/ai/ask?userId={userId}
3. GET /api/companies/{companyId}/ai/director/summary/today?userId={userId}
4. GET /api/companies/{companyId}/ai/history?userId={userId}&limit=50
```

Search API используется для поиска по сообщениям общего чата и именам файлов компании.

AI endpoints пока работают в mock-режиме и нужны для подключения экрана AI Director во Flutter. С версии v0.8 история AI-диалога сохраняется в PostgreSQL/Neon. Настоящая OpenAI-интеграция будет отдельным этапом.

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
13. GET /api/companies/{companyId}/search?userId=...&query=...&limit=20
14. GET /api/companies/{companyId}/ai/director/summary/today?userId=...
15. POST /api/companies/{companyId}/ai/ask?userId=...
16. GET /api/companies/{companyId}/ai/history?userId=...&limit=50
17. DELETE /api/users/me?userId=...
```

---

# 7. Дополнительная проверка Search API

Search API не является App Store blocker, но уже доступен на prod и может быть подключён клиентом.

Endpoint:

```http
GET /api/companies/{companyId}/search?userId={userId}&query={text}&limit=20
```

Проверка на prod:

```bash
curl -G \
  "https://birchat-backend.onrender.com/api/companies/{companyId}/search" \
  -H "accept: application/json" \
  --data-urlencode "userId={userId}" \
  --data-urlencode "query=файл" \
  --data-urlencode "limit=20"
```

Ожидаемо возвращается массив результатов. Возможные `type`:

```text
MESSAGE — найдено сообщение общего чата
FILE    — найден файл компании по originalFileName
```

Ошибки:

```text
400 VALIDATION — query пустой, query меньше 2 символов или limit меньше 1
403 NOT_A_MEMBER — пользователь не состоит в компании
```

---

# 8. Дополнительная проверка AI mock + history API

AI mock API не является App Store blocker, но уже доступен на prod и может быть подключён клиентом для экрана AI Director.

С версии `v0.8` backend сохраняет историю AI-диалога в PostgreSQL/Neon.

## 8.1. Проверка AI summary

Endpoint:

```http
GET /api/companies/{companyId}/ai/director/summary/today?userId={userId}
```

Проверка на prod:

```bash
curl -X GET   "https://birchat-backend.onrender.com/api/companies/{companyId}/ai/director/summary/today?userId={userId}"   -H "accept: application/json"
```

Ожидаемо возвращается объект со следующими полями:

```text
title
summary
items
createdAt
```

## 8.2. Проверка AI ask

Endpoint:

```http
POST /api/companies/{companyId}/ai/ask?userId={userId}
```

Проверка на prod:

```bash
curl -X POST   "https://birchat-backend.onrender.com/api/companies/{companyId}/ai/ask?userId={userId}"   -H "Content-Type: application/json"   -H "accept: application/json"   -d '{
    "question": "Что сегодня было в компании?"
  }'
```

Ожидаемо возвращается объект со следующими полями:

```text
threadId
messageId
answer
model = mock
createdAt
```

После успешного вызова в БД должны появиться 2 сообщения AI-чата:

```text
role = USER       — вопрос пользователя
role = ASSISTANT  — mock-ответ AI
```

## 8.3. Проверка AI history

Endpoint:

```http
GET /api/companies/{companyId}/ai/history?userId={userId}&limit=50
```

Проверка на prod:

```bash
curl -X GET   "https://birchat-backend.onrender.com/api/companies/{companyId}/ai/history?userId={userId}&limit=50"   -H "accept: application/json"
```

Ожидаемо возвращается объект:

```text
threadId
messages
```

Каждый элемент `messages` содержит:

```text
id
role = USER / ASSISTANT
content
model
createdAt
```

Если пользователь ещё не задавал вопросы AI, ожидаемо:

```json
{
  "threadId": null,
  "messages": []
}
```

Ошибки:

```text
400 VALIDATION — question пустой, question превышает 5000 символов или limit меньше 1
403 NOT_A_MEMBER — пользователь не состоит в компании
```

Важно: это mock endpoint. Текст ответа не является бизнес-контрактом. Flutter должен ориентироваться на структуру полей, а не на конкретный текст `answer`, `summary` или `items`.

---

# 9. Prod env без секретов

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
