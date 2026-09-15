# BirChat Backend API для Flutter Frontend

Версия: `v0.8 MVP`
Дата обновления: `2026-09-15`
Backend: `Java Spring Boot`
Database: `PostgreSQL / Neon`
File Storage: `Supabase Storage`

Base URL prod:

```text
https://birchat-backend.onrender.com
```

Base URL local:

```text
http://localhost:8080
```

Swagger prod:

```text
https://birchat-backend.onrender.com/swagger-ui.html
```

Swagger local:

```text
http://localhost:8080/swagger-ui.html
```

Health check:

```http
GET /api/health
```

---

# Changelog backend v0.8

## Добавлено в v0.8

### AI history storage

AI mock endpoints теперь сохраняют историю диалога в PostgreSQL/Neon.

Добавлен новый endpoint получения истории AI-чата:

```http
GET /api/companies/{companyId}/ai/history?userId={userId}&limit=50
```

Изменён response у `POST /api/companies/{companyId}/ai/ask`.

Теперь ответ дополнительно содержит:

```text
threadId
messageId
```

Логика `POST /ai/ask` теперь такая:

```text
1. Backend проверяет, что userId является active-участником компании.
2. Backend находит или создаёт default AI-thread для пары companyId + userId.
3. Сохраняет вопрос пользователя как AI message с role = USER.
4. Генерирует mock-ответ.
5. Сохраняет ответ AI как AI message с role = ASSISTANT и model = mock.
6. Возвращает ответ Flutter.
```

Новые таблицы:

```text
birchat.ai_threads
birchat.ai_messages
```

Важно для Flutter: AI всё ещё работает в mock-режиме, но история уже сохраняется и может отображаться как обычный AI-чат.

---

# Changelog backend v0.7

## Добавлено в v0.7

### AI mock endpoints

Добавлены mock endpoints для экрана AI Director:

```http
POST /api/companies/{companyId}/ai/ask?userId={userId}
GET  /api/companies/{companyId}/ai/director/summary/today?userId={userId}
```

На текущем этапе это не настоящая OpenAI-интеграция. Backend возвращает тестовые mock-ответы, но контракт уже можно подключать во Flutter.

Общие правила:

```text
1. Backend проверяет, что userId является active-участником компании.
2. Для чужого пользователя возвращается NOT_A_MEMBER.
3. Даты возвращаются в UTC с Z.
4. model в ответе равен mock.
```

---

# Changelog backend v0.6

## Добавлено в v0.6

### Поиск по компании

Добавлен endpoint поиска по данным компании:

```http
GET /api/companies/{companyId}/search?userId={userId}&query={text}&limit=20
```

На текущем этапе поиск работает по двум источникам:

```text
1. Сообщения общего чата — поле chat_messages.content
2. Файлы компании — поле company_files.original_file_name
```

Ответ возвращается единым списком результатов с типом `MESSAGE` или `FILE`, отсортированным по `createdAt DESC`.

Важно для Flutter: поиск по файлам ищет только по имени файла, не по содержимому файла.

---

# Changelog backend v0.5

## Добавлено в v0.5

### Удаление аккаунта

Добавлен endpoint удаления текущего пользователя:

```http
DELETE /api/users/me?userId={userId}
```

Успешный ответ:

```http
204 No Content
```

Логика удаления:

```text
1. Пользователь становится inactive.
2. Пользователь выходит из всех компаний.
3. Сообщения пользователя остаются в рабочей переписке компании.
4. Файлы пользователя остаются в компании.
5. Номер телефона освобождается.
6. Повторный вход с тем же номером создаёт нового пользователя.
```

Решение по кейсу `LAST_DIRECTOR`:

```text
Удаление аккаунта не блокируем.
LAST_DIRECTOR не используется.
Если удаляемый пользователь был единственным директором компании и в компании есть другие active-участники, backend назначает директором самого старого active-участника.
Если других active-участников нет, компания остаётся без active-участников.
```

### Настоящая SMS-авторизация

`POST /api/auth/send-code` больше не возвращает `testCode`.

Теперь backend:

```text
1. Нормализует номер телефона.
2. Генерирует случайный 4-значный код.
3. Сохраняет hash кода в PostgreSQL/Neon.
4. Отправляет SMS через SMSC.kz.
5. Ограничивает повторную отправку кода cooldown-ом.
6. Ограничивает количество неверных попыток.
7. Поддерживает demo-номер для App Store Review без реальной отправки SMS.
```

Параметры текущего flow:

```text
Длина кода: 4 цифры
TTL кода: 300 секунд
Лимит попыток: 5
Повторная отправка: не чаще 1 раза в 60 секунд
```

Новые ошибки auth-flow:

```text
CODE_EXPIRED       — срок действия кода истёк
TOO_MANY_ATTEMPTS  — превышено количество попыток или слишком частая отправка кода
```

### Хостинг без засыпания

Prod backend переведён на платный Render-инстанс без sleep. Для клиента контракт не меняется.

---

# Changelog backend v0.4

## Добавлено в v0.4

### Файлы компании

Добавлены API для загрузки файлов, получения списка файлов, получения карточки файла и получения временной ссылки на скачивание:

```http
POST /api/companies/{companyId}/files/upload?userId={userId}
GET  /api/companies/{companyId}/files?userId={userId}
GET  /api/companies/{companyId}/files/{fileId}?userId={userId}
GET  /api/companies/{companyId}/files/{fileId}/download-url?userId={userId}
```

Файлы физически хранятся в `Supabase Storage`, а metadata хранится в PostgreSQL/Neon в таблице `birchat.company_files`.

Важно для Flutter: `fileUrl` напрямую не открывать. Для открытия файла нужно вызывать `/download-url` и использовать поле `downloadUrl`. Ссылка временная.

### Вложения в сообщениях чата

Добавлен endpoint отправки файла в общий чат:

```http
POST /api/companies/{companyId}/chats/general/messages/file
```

`ChatMessageResponse` теперь всегда содержит поле:

```json
"attachments": []
```

Для обычного текстового сообщения массив пустой. Для сообщения с файлом внутри будет информация о вложении.

---

# Changelog backend v0.3

## Добавлено в v0.3

### Cursor pagination для чата

`GET /api/companies/{companyId}/chats/general/messages` поддерживает:

```text
limit
after
before
```

Правила:

```text
без after/before + limit → последние N сообщений, порядок в ответе старые → новые
after + limit → первые N сообщений после указанного messageId
before + limit → последние N сообщений до указанного messageId
after и before вместе → 400 VALIDATION
```

Курсор работает по паре `(created_at, id)`, чтобы не терять и не дублировать сообщения на границе страницы.

### Unread count

В `GET /api/companies/{companyId}/home?userId={userId}` в объект `generalChat` добавлено поле:

```json
"unreadCount": 0
```

### Отметка чата прочитанным

Добавлен endpoint:

```http
POST /api/companies/{companyId}/chats/general/read?userId={userId}
```

Он сохраняет, до какого сообщения пользователь прочитал чат.

---

# Changelog backend v0.2

## Добавлено в v0.2

### Профиль пользователя

Добавлены методы:

```http
GET /api/users/me?userId={userId}
PUT /api/users/me?userId={userId}
```

### Единый формат ошибок

Ошибка теперь возвращает поле `code`.

Пример:

```json
{
  "timestamp": "2026-09-09T10:20:00.123Z",
  "status": 409,
  "error": "CONFLICT",
  "code": "ALREADY_MEMBER",
  "message": "Пользователь уже состоит в этой компании",
  "path": "/api/companies/{companyId}/employees"
}
```

### Даты с timezone

Все даты в API возвращаются в UTC-формате с `Z`.

Было:

```json
"createdAt": "2026-08-28T00:32:36.349339"
```

Стало:

```json
"createdAt": "2026-08-28T00:32:36.349339Z"
```

### Проверка членства

Backend проверяет членство пользователя в компании для:

```http
GET  /api/companies/{companyId}/chats/general/messages
POST /api/companies/{companyId}/chats/general/messages
GET  /api/companies/{companyId}/employees
```

### Нормализация телефона

Backend приводит телефон к формату:

```text
+77011234567
```

Примеры входных значений:

```text
87011234567
7011234567
+7 (701) 123-45-67
```

---

# 1. Общая информация

На текущем этапе backend работает в MVP-режиме:

* авторизация по телефону работает через реальные SMS-коды;
* SMS отправляется через SMSC.kz;
* `POST /api/auth/send-code` больше не возвращает `testCode`;
* код подтверждения состоит из 4 цифр;
* срок жизни кода — 5 минут;
* есть ограничение количества попыток ввода кода;
* есть ограничение повторной отправки SMS;
* есть demo-номер для App Store Review без реальной отправки SMS;
* JWT пока не реализован;
* вместо JWT временно используется `userId`;
* для некоторых методов временно передается `actorUserId`, чтобы понять, кто выполняет действие;
* даты возвращаются в UTC с `Z`;
* файлы хранятся в Supabase Storage, metadata файлов — в PostgreSQL/Neon;
* реализован поиск по сообщениям общего чата и именам файлов компании;
* AI mock endpoints сохраняют историю диалога в PostgreSQL/Neon;
* prod backend работает на платном Render-инстансе без sleep.

Позже `userId` и `actorUserId` будут заменены на получение пользователя из JWT-токена.

---

# 2. Авторизация

## 2.1. Отправка кода

### Endpoint

```http
POST /api/auth/send-code
```

### Request body

```json
{
  "phone": "+77005554433"
}
```

Backend нормализует телефон. Можно отправлять:

```text
+77005554433
87005554433
7005554433
+7 (700) 555-44-33
```

### Response

```json
{
  "message": "Код подтверждения отправлен"
}
```

### Важное изменение v0.5

Поле `testCode` больше не возвращается.

Было в mock-режиме:

```json
{
  "message": "Код подтверждения отправлен на номер +77005554433",
  "testCode": "1111"
}
```

Стало:

```json
{
  "message": "Код подтверждения отправлен"
}
```

Подсказку с тестовым кодом на клиенте больше не показывать. Если Flutter показывает подсказку только при наличии поля `testCode`, она исчезнет автоматически.

### Логика

```text
1. Backend нормализует номер телефона.
2. Проверяет cooldown повторной отправки.
3. Создает случайный 4-значный код.
4. Сохраняет hash кода в таблицу birchat.auth_codes.
5. Отправляет SMS через SMSC.kz.
6. Возвращает только message.
```

### Demo-номер для App Store Review

Для demo-номера SMS не отправляется. Код фиксированный и передается отдельно в App Store Review Notes.

Demo-номер и demo-код не нужно хардкодить на клиенте. Клиент работает через тот же flow:

```text
POST /api/auth/send-code
POST /api/auth/verify-code
```

### Ошибки

```text
400 VALIDATION — некорректный номер телефона
400 TOO_MANY_ATTEMPTS — код уже был отправлен, повторите позже
400 BAD_REQUEST — SMS-провайдер не смог отправить сообщение или не настроен
```

---

## 2.2. Проверка кода

### Endpoint

```http
POST /api/auth/verify-code
```

### Request body

```json
{
  "phone": "+77005554433",
  "code": "4821"
}
```

`code` — 4 цифры из SMS.

### Response

```json
{
  "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "phone": "+77005554433",
  "fullName": "Новый пользователь",
  "displayName": "Пользователь",
  "initials": "П",
  "accessToken": "mock-access-token-598586f3-9c44-4eb0-9c65-f280cb5eee85"
}
```

`accessToken` пока остается mock-значением. JWT будет добавлен отдельным этапом.

### Ошибки

Неверный код:

```json
{
  "timestamp": "2026-09-11T18:20:00.123Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "code": "INVALID_CODE",
  "message": "Неверный код подтверждения",
  "path": "/api/auth/verify-code"
}
```

Код истёк:

```json
{
  "timestamp": "2026-09-11T18:20:00.123Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "code": "CODE_EXPIRED",
  "message": "Срок действия кода истёк",
  "path": "/api/auth/verify-code"
}
```

Превышено количество попыток:

```json
{
  "timestamp": "2026-09-11T18:20:00.123Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "code": "TOO_MANY_ATTEMPTS",
  "message": "Превышено количество попыток ввода кода",
  "path": "/api/auth/verify-code"
}
```

### Логика

```text
1. Backend нормализует телефон.
2. Ищет последний активный код по номеру.
3. Проверяет срок действия кода.
4. Проверяет лимит попыток.
5. Сравнивает hash введенного кода с hash в БД.
6. При успешной проверке помечает код consumed=true.
7. Если пользователь с таким телефоном уже есть в базе, возвращает существующего пользователя.
8. Если пользователя нет, создает нового пользователя.
```

Если аккаунт ранее был удалён, его номер телефона освобожден. Поэтому повторный вход с тем же номером создаст нового пользователя.

Новый пользователь создается с именем-заглушкой:

```text
fullName: Новый пользователь
displayName: Пользователь
initials: П
```

Потом пользователь может обновить профиль через `PUT /api/users/me`.

---

# 3. Профиль пользователя

## 3.1. Получить профиль

### Endpoint

```http
GET /api/users/me?userId={userId}
```

### Example

```http
GET /api/users/me?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85
```

### Response

```json
{
  "id": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "phone": "+77011234567",
  "fullName": "Баглан Ерталапов",
  "displayName": "Баглан",
  "initials": "БЕ",
  "avatarUrl": null,
  "isActive": true
}
```

### Ошибки

```text
400 VALIDATION — отсутствует userId или некорректный UUID
404 USER_NOT_FOUND — пользователь не найден
```

---

## 3.2. Обновить профиль

### Endpoint

```http
PUT /api/users/me?userId={userId}
```

### Request body

```json
{
  "fullName": "Баглан Ерталапов"
}
```

### Response

```json
{
  "id": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "phone": "+77011234567",
  "fullName": "Баглан Ерталапов",
  "displayName": "Баглан",
  "initials": "БЕ",
  "avatarUrl": null,
  "isActive": true
}
```

### Логика

Backend обновляет `fullName`, а `displayName` и `initials` пересчитывает автоматически.

---

## 3.3. Удалить аккаунт

### Endpoint

```http
DELETE /api/users/me?userId={userId}
```

### Example

```http
DELETE /api/users/me?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85
```

### Response

```http
204 No Content
```

Тело ответа отсутствует.

### Что делает backend

```text
1. Пользователь становится inactive.
2. Все active membership пользователя становятся INACTIVE.
3. Сообщения пользователя остаются в чатах компании.
4. Файлы пользователя остаются в компании.
5. Номер телефона освобождается.
6. Повторный вход с тем же номером создаёт нового пользователя.
```

### Поведение для единственного директора

Удаление аккаунта не блокируется.

```text
LAST_DIRECTOR не используется.
```

Если удаляемый пользователь был единственным директором компании:

```text
1. Если в компании есть другие active-участники, backend назначает директором самого старого active-участника.
2. Если других active-участников нет, компания остаётся без active-участников.
```

### Ошибки

```text
400 VALIDATION — отсутствует userId или некорректный UUID
404 USER_NOT_FOUND — пользователь не найден
```

### Использование во Flutter

Используется на экране профиля:

```text
Профиль → Удалить аккаунт → Подтверждение → DELETE /api/users/me?userId=...
```

После `204 No Content` клиент должен очистить локальные данные пользователя и вернуть его на экран входа.


---

# 4. Компании

## 4.1. Получить список компаний пользователя

### Endpoint

```http
GET /api/companies/my?userId={userId}
```

### Example

```http
GET /api/companies/my?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85
```

### Response

```json
[
  {
    "id": "5479c4a8-f805-4d0d-bbc8-87a45af85b93",
    "name": "BirChat Demo Company",
    "field": "Торговля",
    "logoUrl": null,
    "initial": "B",
    "color": "#2563EB",
    "employees": 1,
    "role": "DIRECTOR",
    "roleLabel": "Директор",
    "position": "Директор"
  }
]
```

### Использование во Flutter

Используется на экране выбора компании.

Поле `role` нужно для логики доступа:

```text
DIRECTOR
ACCOUNTANT
BUYER
WAREHOUSE
EMPLOYEE
ADMIN
```

Поле `roleLabel` используется для отображения пользователю.

---

## 4.2. Создать компанию

### Endpoint

```http
POST /api/companies
```

### Request body

```json
{
  "ownerUserId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "name": "Test Company From API",
  "field": "IT услуги",
  "logoUrl": null,
  "color": "#7C3AED"
}
```

### Response

```json
{
  "id": "d44d1d6a-d3c2-49c8-8df3-6a9f8ff0c88c",
  "name": "Test Company From API",
  "field": "IT услуги",
  "logoUrl": null,
  "initial": "T",
  "color": "#7C3AED",
  "employees": 1,
  "role": "DIRECTOR",
  "roleLabel": "Директор",
  "position": "Директор"
}
```

### Что делает backend

При создании компании backend автоматически:

1. создает запись в `companies`;
2. добавляет владельца в `company_members`;
3. назначает владельцу роль `DIRECTOR`;
4. создает общий чат компании `GENERAL`.

---

## 4.3. Получить данные главного экрана компании

### Endpoint

```http
GET /api/companies/{companyId}/home?userId={userId}
```

### Example

```http
GET /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/home?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85
```

### Response

```json
{
  "company": {
    "id": "5479c4a8-f805-4d0d-bbc8-87a45af85b93",
    "name": "BirChat Demo Company",
    "field": "Торговля",
    "logoUrl": null,
    "initial": "B",
    "color": "#2563EB"
  },
  "generalChat": {
    "chatId": "94cf383b-0c02-4b90-b0f0-33a3ae73c4e4",
    "name": "Общий чат компании",
    "messagesCount": 4,
    "unreadCount": 0,
    "lastMessage": "Сообщение отправлено через Java backend из Swagger",
    "lastMessageAt": "2026-09-09T10:20:00.123Z"
  },
  "aiDirector": {
    "available": true,
    "label": "AI Director"
  }
}
```

### Использование во Flutter

Используется для главного экрана компании.

`generalChat.unreadCount` — количество непрочитанных сообщений для текущего пользователя.

`aiDirector.available = true` означает, что пользователь может видеть AI Director.

Сейчас AI Director доступен для роли:

```text
DIRECTOR
```

---

# 5. Общий чат компании

## 5.1. Получить сообщения общего чата

### Endpoint

```http
GET /api/companies/{companyId}/chats/general/messages?userId={userId}
```

### Query params

| name | type | required | description |
|---|---|---|---|
| userId | UUID | yes | ID текущего пользователя |
| limit | int | no | Максимум сообщений. Default `50`, max `100` |
| after | UUID | no | ID сообщения, после которого нужно получить новые сообщения |
| before | UUID | no | ID сообщения, до которого нужно получить старые сообщения |

### Правила cursor pagination

```text
без after/before + limit → последние N сообщений, порядок в ответе старые → новые
after + limit → первые N сообщений после after messageId
before + limit → последние N сообщений до before messageId, порядок в ответе старые → новые
after и before вместе → 400 VALIDATION
```

Курсор работает по паре `(created_at, id)`, чтобы не было потерь или дублей, если несколько сообщений имеют одинаковое время создания.

### Examples

Открытие чата:

```http
GET /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/chats/general/messages?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85&limit=50
```

Получить новые сообщения после последнего сообщения:

```http
GET /api/companies/{companyId}/chats/general/messages?userId={userId}&after={lastMessageId}&limit=50
```

Получить старые сообщения при скролле вверх:

```http
GET /api/companies/{companyId}/chats/general/messages?userId={userId}&before={oldestMessageId}&limit=50
```

### Response

```json
[
  {
    "id": "98e50edc-dac3-46d7-85e5-a52653b0e6c2",
    "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
    "authorName": "Азамат",
    "authorInitials": "АН",
    "type": "TEXT",
    "text": "Всем привет! Это первое тестовое сообщение в BirChat.",
    "createdAt": "2026-09-09T10:20:00.123Z",
    "attachments": []
  },
  {
    "id": "message-uuid",
    "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
    "authorName": "Азамат",
    "authorInitials": "АН",
    "type": "DOCUMENT",
    "text": "Файл во вложении",
    "createdAt": "2026-09-09T10:25:00.123Z",
    "attachments": [
      {
        "fileId": "62911e68-2554-4f5a-a478-ebf8321e3c6a",
        "fileName": "91cbde75-84ff-47f2-b990-51cb21949df8.txt",
        "originalFileName": "FPSMonitor.txt",
        "contentType": "text/plain",
        "fileSize": 98568
      }
    ]
  }
]
```

### Ошибки

```text
400 VALIDATION — отсутствует userId, неправильный UUID, limit < 1, after и before вместе
403 NOT_A_MEMBER — пользователь не состоит в компании
404 MESSAGE_NOT_FOUND — after/before message не найден
```

### Использование во Flutter

Используется на экране общего чата.

Для определения своих сообщений на Flutter:

```dart
final isMine = message.userId == currentUserId;
```

Backend поле `isMine` не возвращает.

Рекомендуемый polling:

```text
1. При открытии чата вызвать GET messages?userId=...&limit=50
2. Каждые 4–5 секунд, пока экран открыт, вызывать GET messages?userId=...&after={lastMessageId}&limit=50
3. При скролле вверх вызывать GET messages?userId=...&before={oldestMessageId}&limit=50
4. Мержить сообщения по id, чтобы список не мигал
```

---

## 5.2. Отправить текстовое сообщение в общий чат

### Endpoint

```http
POST /api/companies/{companyId}/chats/general/messages
```

### Example

```http
POST /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/chats/general/messages
```

### Request body

```json
{
  "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "text": "Сообщение отправлено через Java backend из Swagger"
}
```

### Response

```json
{
  "id": "message-uuid",
  "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "authorName": "Азамат",
  "authorInitials": "АН",
  "type": "TEXT",
  "text": "Сообщение отправлено через Java backend из Swagger",
  "createdAt": "2026-09-09T10:32:36.349Z",
  "attachments": []
}
```

### Ограничения

```text
text обязателен
text не должен превышать 5000 символов
пользователь должен состоять в компании
```

---

## 5.3. Отправить файл в общий чат

### Endpoint

```http
POST /api/companies/{companyId}/chats/general/messages/file
```

### Request body

```json
{
  "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "fileId": "62911e68-2554-4f5a-a478-ebf8321e3c6a",
  "text": "Файл во вложении"
}
```

`text` необязательный, но если передан, не должен превышать 5000 символов.

### Response

```json
{
  "id": "message-uuid",
  "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "authorName": "Азамат",
  "authorInitials": "АН",
  "type": "DOCUMENT",
  "text": "Файл во вложении",
  "createdAt": "2026-09-09T10:35:00.123Z",
  "attachments": [
    {
      "fileId": "62911e68-2554-4f5a-a478-ebf8321e3c6a",
      "fileName": "91cbde75-84ff-47f2-b990-51cb21949df8.txt",
      "originalFileName": "FPSMonitor.txt",
      "contentType": "text/plain",
      "fileSize": 98568
    }
  ]
}
```

### Тип сообщения

Backend определяет `type` по `contentType` файла:

```text
image/* → IMAGE
audio/* → VOICE
остальное → DOCUMENT
```

### Ошибки

```text
400 VALIDATION — отсутствует userId или fileId
403 NOT_A_MEMBER — пользователь не состоит в компании
404 FILE_NOT_FOUND — файл не найден или не принадлежит компании
```

---

## 5.4. Отметить общий чат прочитанным

### Endpoint

```http
POST /api/companies/{companyId}/chats/general/read?userId={userId}
```

### Request body

```json
{
  "messageId": "message-uuid"
}
```

### Response

```json
{
  "chatId": "94cf383b-0c02-4b90-b0f0-33a3ae73c4e4",
  "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "lastReadMessageId": "message-uuid",
  "lastReadMessageCreatedAt": "2026-09-09T10:35:00.123Z",
  "unreadCount": 0
}
```

### Использование во Flutter

Когда пользователь открыл чат и увидел последние сообщения, Flutter может вызвать этот метод с `messageId` последнего видимого сообщения.

После вызова backend пересчитает `unreadCount` для пользователя.

---

# 6. Файлы компании

## 6.1. Загрузить файл

### Endpoint

```http
POST /api/companies/{companyId}/files/upload?userId={userId}
```

### Content-Type

```text
multipart/form-data
```

### Form data

| name | type | required | description |
|---|---|---|---|
| file | file | yes | Файл для загрузки |

### curl example

```bash
curl -X POST \
  "https://birchat-backend.onrender.com/api/companies/{companyId}/files/upload?userId={userId}" \
  -H "accept: */*" \
  -F "file=@FPSMonitor.txt;type=text/plain"
```

Не нужно вручную указывать header `Content-Type: multipart/form-data`, потому что curl сам добавляет boundary.

### Response

```json
{
  "id": "62911e68-2554-4f5a-a478-ebf8321e3c6a",
  "companyId": "5479c4a8-f805-4d0d-bbc8-87a45af85b93",
  "uploadedBy": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "uploadedByName": "Азамат",
  "fileName": "91cbde75-84ff-47f2-b990-51cb21949df8.txt",
  "originalFileName": "FPSMonitor.txt",
  "contentType": "text/plain",
  "fileSize": 98568,
  "fileUrl": "https://cukokjzhsriszjofnagj.supabase.co/storage/v1/object/birchat-files/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/2026-09-09/91cbde75-84ff-47f2-b990-51cb21949df8.txt",
  "createdAt": "2026-09-09T09:18:02.1405672Z"
}
```

### Важное правило

`id` — это внутренний `fileId` в нашей БД. Его нужно использовать в API:

```http
GET /api/companies/{companyId}/files/{fileId}?userId={userId}
GET /api/companies/{companyId}/files/{fileId}/download-url?userId={userId}
POST /api/companies/{companyId}/chats/general/messages/file
```

`fileUrl` напрямую не открывать во Flutter, потому что bucket private. Для открытия файла использовать `/download-url`.

---

## 6.2. Получить список файлов компании

### Endpoint

```http
GET /api/companies/{companyId}/files?userId={userId}
```

### Query params

| name | type | required | description |
|---|---|---|---|
| userId | UUID | yes | ID текущего пользователя |
| limit | int | no | Максимум файлов. Default `50`, max `100` |

### Example

```http
GET /api/companies/{companyId}/files?userId={userId}&limit=50
```

### Response

```json
[
  {
    "id": "62911e68-2554-4f5a-a478-ebf8321e3c6a",
    "companyId": "5479c4a8-f805-4d0d-bbc8-87a45af85b93",
    "uploadedBy": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
    "uploadedByName": "Азамат",
    "fileName": "91cbde75-84ff-47f2-b990-51cb21949df8.txt",
    "originalFileName": "FPSMonitor.txt",
    "contentType": "text/plain",
    "fileSize": 98568,
    "fileUrl": "https://cukokjzhsriszjofnagj.supabase.co/storage/v1/object/birchat-files/companies/...",
    "createdAt": "2026-09-09T09:18:02.1405672Z"
  }
]
```

---

## 6.3. Получить карточку файла

### Endpoint

```http
GET /api/companies/{companyId}/files/{fileId}?userId={userId}
```

### Response

```json
{
  "id": "62911e68-2554-4f5a-a478-ebf8321e3c6a",
  "companyId": "5479c4a8-f805-4d0d-bbc8-87a45af85b93",
  "uploadedBy": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
  "uploadedByName": "Азамат",
  "fileName": "91cbde75-84ff-47f2-b990-51cb21949df8.txt",
  "originalFileName": "FPSMonitor.txt",
  "contentType": "text/plain",
  "fileSize": 98568,
  "fileUrl": "https://cukokjzhsriszjofnagj.supabase.co/storage/v1/object/birchat-files/companies/...",
  "createdAt": "2026-09-09T09:18:02.1405672Z"
}
```

### Ошибки

```text
403 NOT_A_MEMBER — пользователь не состоит в компании
404 FILE_NOT_FOUND — файл не найден или не принадлежит компании
```

---

## 6.4. Получить временную ссылку для скачивания файла

### Endpoint

```http
GET /api/companies/{companyId}/files/{fileId}/download-url?userId={userId}
```

### Query params

| name | type | required | description |
|---|---|---|---|
| userId | UUID | yes | ID текущего пользователя |
| expiresInSeconds | int | no | Время жизни ссылки. Default `300`, min `60`, max `3600` |

### Example

```http
GET /api/companies/{companyId}/files/{fileId}/download-url?userId={userId}&expiresInSeconds=300
```

### Response

```json
{
  "fileId": "62911e68-2554-4f5a-a478-ebf8321e3c6a",
  "fileName": "FPSMonitor.txt",
  "contentType": "text/plain",
  "fileSize": 98568,
  "downloadUrl": "https://cukokjzhsriszjofnagj.supabase.co/storage/v1/object/sign/birchat-files/companies/...",
  "expiresInSeconds": 300,
  "expiresAt": "2026-09-09T09:25:00Z"
}
```

### Использование во Flutter

1. Пользователь нажал на файл.
2. Flutter вызывает `/download-url`.
3. Backend возвращает `downloadUrl`.
4. Flutter открывает `downloadUrl` во внешнем viewer/download manager.

`downloadUrl` временный. После истечения времени жизни нужно запросить новый.

---

# 7. Поиск по компании

## 7.1. Найти сообщения и файлы

### Endpoint

```http
GET /api/companies/{companyId}/search?userId={userId}&query={text}&limit=20
```

### Query params

| name | type | required | description |
|---|---|---|---|
| userId | UUID | yes | ID текущего пользователя |
| query | string | yes | Строка поиска. Минимум 2 символа |
| limit | int | no | Максимум результатов. Default `20`, max `50` |

### Example

```http
GET /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/search?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85&query=файл&limit=20
```

### Response

```json
[
  {
    "type": "MESSAGE",
    "id": "message-uuid",
    "title": "Сообщение от Азамат",
    "text": "search-prod-test-birchat-001",
    "createdAt": "2026-09-13T13:40:00Z",
    "messageId": "message-uuid",
    "fileId": null
  },
  {
    "type": "FILE",
    "id": "file-uuid",
    "title": "search-prod-file-001.txt",
    "text": "text/plain · 12 B",
    "createdAt": "2026-09-13T13:41:00Z",
    "messageId": null,
    "fileId": "file-uuid"
  }
]
```

### Поля ответа

| field | type | description |
|---|---|---|
| type | string | Тип результата: `MESSAGE` или `FILE` |
| id | UUID | Основной ID результата. Для сообщения = `messageId`, для файла = `fileId` |
| title | string | Заголовок для отображения |
| text | string | Текст результата: содержимое сообщения или описание файла |
| createdAt | datetime | Дата создания результата в UTC с `Z` |
| messageId | UUID/null | ID сообщения, если `type = MESSAGE` |
| fileId | UUID/null | ID файла, если `type = FILE` |

### Логика

```text
1. Backend проверяет, что userId является active-участником компании.
2. Ищет query в chat_messages.content.
3. Ищет query в company_files.original_file_name.
4. Объединяет результаты.
5. Сортирует по createdAt DESC.
6. Возвращает максимум limit результатов.
```

### Ограничения MVP

```text
1. Поиск выполняется через LIKE/ILIKE-логику, без PostgreSQL full-text search.
2. Поиск по файлам работает только по originalFileName.
3. Поиск по содержимому PDF/Word/Excel пока не реализован.
4. Поиск выполняется только внутри одной компании.
```

### Ошибки

```text
400 VALIDATION — query пустой, query меньше 2 символов или limit меньше 1
403 NOT_A_MEMBER — пользователь не состоит в компании
```

### Использование во Flutter

Используется на экране поиска внутри выбранной компании.

Для результата `MESSAGE` Flutter может открыть общий чат и подсветить/проскроллить к сообщению позже, когда будет реализована поддержка перехода к конкретному сообщению.

Для результата `FILE` Flutter должен использовать `fileId` и открыть файл через:

```http
GET /api/companies/{companyId}/files/{fileId}/download-url?userId={userId}
```

---

# 8. AI Director mock + history

На текущем этапе AI работает в mock-режиме. Это нужно, чтобы Flutter уже мог подключить экран AI Director, не ожидая настоящей OpenAI-интеграции.

С версии `v0.8` история AI-диалога сохраняется в PostgreSQL/Neon:

```text
birchat.ai_threads  — AI-диалог пользователя внутри компании
birchat.ai_messages — сообщения USER и ASSISTANT внутри AI-диалога
```

Важно: текст ответа пока mock. Но структура API уже близка к будущей OpenAI-интеграции.

## 8.1. Задать вопрос AI

### Endpoint

```http
POST /api/companies/{companyId}/ai/ask?userId={userId}
```

### Query params

| name | type | required | description |
|---|---|---|---|
| userId | UUID | yes | ID текущего пользователя |

### Request body

```json
{
  "question": "Что сегодня было в компании?"
}
```

### Response

```json
{
  "threadId": "thread-uuid",
  "messageId": "assistant-message-uuid",
  "answer": "AI mock: я пока работаю в тестовом режиме.
Позже здесь будет ответ на основе сообщений, файлов и памяти компании.

Ваш вопрос: Что сегодня было в компании?
",
  "model": "mock",
  "createdAt": "2026-09-15T15:45:00Z"
}
```

### Поля ответа

| field | type | description |
|---|---|---|
| threadId | UUID | ID default AI-thread пользователя внутри компании |
| messageId | UUID | ID сохранённого сообщения AI с role = `ASSISTANT` |
| answer | string | Ответ AI для отображения пользователю |
| model | string | На текущем этапе всегда `mock` |
| createdAt | datetime | Дата создания ответа в UTC с `Z` |

### Логика

```text
1. Backend проверяет, что userId является active-участником компании.
2. Находит default AI-thread для companyId + userId.
3. Если thread ещё нет — создаёт его.
4. Сохраняет вопрос пользователя в ai_messages с role = USER.
5. Генерирует mock-ответ.
6. Сохраняет mock-ответ в ai_messages с role = ASSISTANT и model = mock.
7. Возвращает threadId, messageId, answer, model, createdAt.
```

### Ограничения

```text
question обязателен
question не должен превышать 5000 символов
пользователь должен быть active-участником компании
```

### Ошибки

```text
400 VALIDATION — question пустой или превышает 5000 символов
403 NOT_A_MEMBER — пользователь не состоит в компании
```

### Использование во Flutter

Используется на экране AI Director, когда пользователь отправляет вопрос. Ответ можно показать как новое сообщение AI.

После успешного ответа Flutter может либо сразу добавить `answer` в локальный список сообщений, либо заново запросить историю через `GET /ai/history`.

---

## 8.2. Получить сводку AI Director за сегодня

### Endpoint

```http
GET /api/companies/{companyId}/ai/director/summary/today?userId={userId}
```

### Query params

| name | type | required | description |
|---|---|---|---|
| userId | UUID | yes | ID текущего пользователя |

### Example

```http
GET /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/ai/director/summary/today?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85
```

### Response

```json
{
  "title": "Сводка за сегодня",
  "summary": "AI mock: позже здесь будет краткая сводка по сообщениям, файлам и активности компании за сегодня.",
  "items": [
    "Сообщения общего чата будут анализироваться позже",
    "Файлы компании будут учитываться позже",
    "Память компании будет добавлена отдельным этапом",
    "Интеграция с OpenAI будет подключена после mock-этапа"
  ],
  "createdAt": "2026-09-15T15:45:00Z"
}
```

### Поля ответа

| field | type | description |
|---|---|---|
| title | string | Заголовок блока сводки |
| summary | string | Основной текст сводки |
| items | array[string] | Короткие пункты для отображения списком |
| createdAt | datetime | Дата создания сводки в UTC с `Z` |

### Ошибки

```text
403 NOT_A_MEMBER — пользователь не состоит в компании
```

### Использование во Flutter

Используется для первого экрана AI Director или блока “Сводка за сегодня”.

Важно: текущий endpoint возвращает mock-данные. Не нужно строить бизнес-логику на конкретном тексте `summary` и `items`, потому что после подключения OpenAI содержимое ответа изменится.

---

## 8.3. Получить историю AI-чата

### Endpoint

```http
GET /api/companies/{companyId}/ai/history?userId={userId}&limit=50
```

### Query params

| name | type | required | description |
|---|---|---|---|
| userId | UUID | yes | ID текущего пользователя |
| limit | int | no | Максимум сообщений. Default `50`, max `100` |

### Example

```http
GET /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/ai/history?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85&limit=50
```

### Response

```json
{
  "threadId": "thread-uuid",
  "messages": [
    {
      "id": "user-message-uuid",
      "role": "USER",
      "content": "Что сегодня было в компании?",
      "model": null,
      "createdAt": "2026-09-15T15:44:59Z"
    },
    {
      "id": "assistant-message-uuid",
      "role": "ASSISTANT",
      "content": "AI mock: я пока работаю в тестовом режиме...",
      "model": "mock",
      "createdAt": "2026-09-15T15:45:00Z"
    }
  ]
}
```

Если истории ещё нет, backend возвращает:

```json
{
  "threadId": null,
  "messages": []
}
```

### Поля ответа

| field | type | description |
|---|---|---|
| threadId | UUID/null | ID default AI-thread. `null`, если пользователь ещё не задавал вопросы AI |
| messages | array | Список сообщений AI-чата в порядке старые → новые |
| messages[].id | UUID | ID AI-сообщения |
| messages[].role | string | `USER` или `ASSISTANT` |
| messages[].content | string | Текст вопроса или ответа |
| messages[].model | string/null | Для `ASSISTANT` сейчас `mock`, для `USER` — `null` |
| messages[].createdAt | datetime | Дата создания сообщения в UTC с `Z` |

### Логика

```text
1. Backend проверяет, что userId является active-участником компании.
2. Ищет default AI-thread для companyId + userId.
3. Если thread нет — возвращает threadId = null и messages = [].
4. Если thread есть — возвращает последние limit сообщений.
5. Сообщения возвращаются в порядке старые → новые.
```

### Ошибки

```text
400 VALIDATION — limit меньше 1
403 NOT_A_MEMBER — пользователь не состоит в компании
```

### Использование во Flutter

Используется при открытии экрана AI Director, чтобы восстановить историю переписки пользователя с AI внутри выбранной компании.

Рекомендуемый flow:

```text
1. Открыть AI Director screen.
2. Вызвать GET /ai/history?userId=...&limit=50.
3. Показать messages как чат.
4. При отправке вопроса вызвать POST /ai/ask.
5. Добавить ответ в список или повторно вызвать GET /ai/history.
```

---

# 9. Сотрудники компании

## 9.1. Получить список сотрудников

### Endpoint

```http
GET /api/companies/{companyId}/employees?userId={userId}
```

### Example

```http
GET /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/employees?userId=598586f3-9c44-4eb0-9c65-f280cb5eee85
```

### Response

```json
[
  {
    "memberId": "member-uuid",
    "userId": "598586f3-9c44-4eb0-9c65-f280cb5eee85",
    "fullName": "Азамат Нурланов",
    "displayName": "Азамат",
    "initials": "АН",
    "phone": "+77011234567",
    "avatarUrl": null,
    "role": "DIRECTOR",
    "roleLabel": "Директор",
    "position": "Директор",
    "status": "ACTIVE",
    "joinedAt": "2026-09-09T10:00:00Z"
  }
]
```

### Использование во Flutter

Используется на экранах:

```text
company-settings
add-employee
profile
```

### Ошибки

```text
400 VALIDATION — отсутствует userId или неправильный UUID
403 NOT_A_MEMBER — пользователь не состоит в компании
404 COMPANY_NOT_FOUND — компания не найдена
```

---

## 9.2. Добавить сотрудника

### Endpoint

```http
POST /api/companies/{companyId}/employees?actorUserId={actorUserId}
```

### Example

```http
POST /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/employees?actorUserId=598586f3-9c44-4eb0-9c65-f280cb5eee85
```

### Request body

```json
{
  "phone": "+77007778899",
  "fullName": "Айжан Сейдахметова",
  "roleCode": "ACCOUNTANT",
  "position": "Бухгалтер"
}
```

Backend нормализует телефон перед поиском/созданием пользователя.

### Response

```json
{
  "memberId": "member-uuid",
  "userId": "user-uuid",
  "fullName": "Айжан Сейдахметова",
  "displayName": "Айжан",
  "initials": "АС",
  "phone": "+77007778899",
  "avatarUrl": null,
  "role": "ACCOUNTANT",
  "roleLabel": "Бухгалтер",
  "position": "Бухгалтер",
  "status": "ACTIVE",
  "joinedAt": "2026-09-09T10:00:00Z"
}
```

### Логика

Добавлять сотрудников могут только пользователи с ролью:

```text
DIRECTOR
ADMIN
```

Если пользователь с таким номером телефона уже существует, backend использует существующую запись.

Если пользователь уже существует, но у него имя-заглушка `Новый пользователь / Пользователь / П`, backend обновляет ФИО на значение из `fullName`.

Если пользователя нет, backend создает нового пользователя.

### Ошибки

```text
400 VALIDATION — ошибка валидации или роль не найдена
403 FORBIDDEN — недостаточно прав
409 ALREADY_MEMBER — пользователь уже состоит в компании
```

---

# 10. Роли

На текущем этапе доступны роли:

```text
DIRECTOR    — Директор
ACCOUNTANT  — Бухгалтер
BUYER       — Закупщик
WAREHOUSE   — Склад
EMPLOYEE    — Сотрудник
ADMIN       — Администратор
```

---

# 11. Формат ошибок

Все обработанные ошибки возвращаются в едином формате:

```json
{
  "timestamp": "2026-09-09T10:20:00.123Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "code": "VALIDATION",
  "message": "Параметр userId обязателен",
  "path": "/api/companies/{companyId}/employees"
}
```

## Возможные HTTP statuses

```text
400 BAD_REQUEST — ошибка валидации или некорректный запрос
403 FORBIDDEN — нет прав или пользователь не состоит в компании
404 NOT_FOUND — сущность не найдена
409 CONFLICT — конфликт, например пользователь уже состоит в компании
500 INTERNAL_SERVER_ERROR — внутренняя ошибка сервера
```

## Особенности auth-ошибок

Для `POST /api/auth/send-code` и `POST /api/auth/verify-code` Flutter должен ориентироваться на поле `code`:

```text
INVALID_CODE       — показать пользователю, что код неверный
CODE_EXPIRED       — предложить запросить новый код
TOO_MANY_ATTEMPTS  — временно заблокировать повторную отправку/ввод
BAD_REQUEST        — показать общую ошибку отправки SMS
```

## Возможные error code

```text
INVALID_CODE        — неверный код подтверждения
CODE_EXPIRED        — срок действия кода истёк
TOO_MANY_ATTEMPTS   — превышено количество попыток или слишком частая отправка кода
USER_NOT_FOUND      — пользователь не найден
COMPANY_NOT_FOUND   — компания не найдена
CHAT_NOT_FOUND      — чат не найден
MESSAGE_NOT_FOUND   — сообщение не найдено
FILE_NOT_FOUND      — файл не найден
ROLE_NOT_FOUND      — роль не найдена
NOT_A_MEMBER        — пользователь не состоит в компании
FORBIDDEN           — недостаточно прав
ALREADY_MEMBER      — пользователь уже состоит в компании
VALIDATION          — ошибка валидации запроса
BAD_REQUEST         — некорректный запрос или ошибка внешнего провайдера
STORAGE_ERROR       — ошибка файлового storage
INTERNAL_ERROR      — внутренняя ошибка сервера
```

## Рекомендация для Flutter

Логику лучше строить по полю `code`, а не по тексту `message`.

`message` можно показывать пользователю, но не использовать как бизнес-дискриминатор.

---

# 12. Рекомендуемая структура Flutter API слоя

Рекомендуется добавить во Flutter проект такие папки:

```text
lib/data/api/
lib/data/repositories/
lib/data/models/
```

Пример:

```text
lib/data/api/api_client.dart
lib/data/api/auth_api.dart
lib/data/api/company_api.dart
lib/data/api/chat_api.dart
lib/data/api/employee_api.dart
lib/data/api/file_api.dart

lib/data/repositories/auth_repository.dart
lib/data/repositories/company_repository.dart
lib/data/repositories/chat_repository.dart
lib/data/repositories/employee_repository.dart
lib/data/repositories/file_repository.dart
```

---

# 13. Рекомендуемый ApiClient на Flutter

Для запросов можно использовать `dio`.

```yaml
dependencies:
  dio: ^5.7.0
```

Пример базового клиента:

```dart
import 'package:dio/dio.dart';

class ApiClient {
  final Dio dio;

  ApiClient()
      : dio = Dio(
          BaseOptions(
            baseUrl: 'https://birchat-backend.onrender.com',
            connectTimeout: const Duration(seconds: 15),
            receiveTimeout: const Duration(seconds: 60),
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
            },
          ),
        );
}
```

Для локального backend:

```text
http://localhost:8080
```

Для Android emulator `localhost` может не работать. Тогда использовать:

```text
http://10.0.2.2:8080
```

Для реального телефона нужно использовать IP компьютера в локальной сети, например:

```text
http://192.168.1.10:8080
```

Для Render используется HTTPS:

```text
https://birchat-backend.onrender.com
```

---

# 14. Что сейчас готово для подключения Flutter

Готовые backend API:

```text
Health:
GET  /api/health

Auth:
POST /api/auth/send-code
POST /api/auth/verify-code

Users:
GET    /api/users/me?userId={userId}
PUT    /api/users/me?userId={userId}
DELETE /api/users/me?userId={userId}

Companies:
GET  /api/companies/my?userId={userId}
POST /api/companies
GET  /api/companies/{companyId}/home?userId={userId}

Chat:
GET  /api/companies/{companyId}/chats/general/messages?userId={userId}&limit=50
GET  /api/companies/{companyId}/chats/general/messages?userId={userId}&after={messageId}&limit=50
GET  /api/companies/{companyId}/chats/general/messages?userId={userId}&before={messageId}&limit=50
POST /api/companies/{companyId}/chats/general/messages
POST /api/companies/{companyId}/chats/general/messages/file
POST /api/companies/{companyId}/chats/general/read?userId={userId}

Files:
POST /api/companies/{companyId}/files/upload?userId={userId}
GET  /api/companies/{companyId}/files?userId={userId}
GET  /api/companies/{companyId}/files/{fileId}?userId={userId}
GET  /api/companies/{companyId}/files/{fileId}/download-url?userId={userId}

Search:
GET  /api/companies/{companyId}/search?userId={userId}&query={text}&limit=20

AI:
POST /api/companies/{companyId}/ai/ask?userId={userId}
GET  /api/companies/{companyId}/ai/director/summary/today?userId={userId}
GET  /api/companies/{companyId}/ai/history?userId={userId}&limit=50

Employees:
GET  /api/companies/{companyId}/employees?userId={userId}
POST /api/companies/{companyId}/employees?actorUserId={actorUserId}
```

---

# 15. Что будет добавлено позже

Следующие API будут добавляться по мере разработки:

```text
Настоящая OpenAI-интеграция:
POST /api/companies/{companyId}/ai/ask будет возвращать реальный AI-ответ вместо mock

AI context / company memory:
поиск релевантных сообщений и файлов для ответа AI

История отправок:
GET  /api/companies/{companyId}/share-history
POST /api/companies/{companyId}/share-history

Настройки компании:
GET /api/companies/{companyId}/settings
PUT /api/companies/{companyId}/settings
```

---

# 16. Временные технические ограничения

На текущем этапе:

* нет настоящего JWT;
* нет Spring Security;
* `accessToken` в `verify-code` пока остается mock-строкой;
* временно используется `userId` в query-параметрах;
* для добавления сотрудника временно используется `actorUserId`;
* нет WebSocket;
* нет настоящей OpenAI-интеграции; AI работает в mock-режиме, но история AI уже сохраняется;
* нет истории отправок;
* нет настроек компании;
* нет ролей на уровне permissions;
* нет refresh token.

Уже реализовано:

* реальная отправка SMS через SMSC.kz;
* demo-номер для App Store Review без реальной SMS;
* удаление аккаунта через `DELETE /api/users/me`;
* файлы загружаются через backend в Supabase Storage;
* для открытия private-файла используется `/download-url`;
* поиск по сообщениям общего чата и именам файлов компании;
* AI mock endpoints для экрана AI Director;
* хранение истории AI-диалога в PostgreSQL/Neon.

---

# 17. Рекомендации для Flutter-разработки

Frontend может подключать backend в таком порядке:

1. `GET /api/health`
2. `POST /api/auth/send-code`
3. `POST /api/auth/verify-code`
4. сохранить `userId` локально;
5. `GET /api/users/me?userId=...`;
6. если нужно — `PUT /api/users/me?userId=...`;
7. `GET /api/companies/my?userId=...`;
8. выбрать компанию;
9. `GET /api/companies/{companyId}/home?userId=...`;
10. открыть экран AI Director при необходимости;
11. восстановить историю через `GET /api/companies/{companyId}/ai/history?userId=...&limit=50`;
12. получить сводку через `GET /api/companies/{companyId}/ai/director/summary/today?userId=...`;
13. отправлять вопрос через `POST /api/companies/{companyId}/ai/ask?userId=...`;
14. открыть чат;
15. `GET /api/companies/{companyId}/chats/general/messages?userId=...&limit=50`;
16. отправлять текст через `POST /api/companies/{companyId}/chats/general/messages`;
17. загружать файл через `POST /api/companies/{companyId}/files/upload?userId=...`;
18. отправлять файл в чат через `POST /api/companies/{companyId}/chats/general/messages/file`;
19. при открытии файла получать временную ссылку через `/download-url`;
20. искать сообщения и файлы через `GET /api/companies/{companyId}/search?userId=...&query=...`;
21. при просмотре чата отмечать прочтение через `POST /api/companies/{companyId}/chats/general/read?userId=...`;
22. на экране профиля удалять аккаунт через `DELETE /api/users/me?userId=...`.

Для auth-flow после v0.5 важно:

```text
send-code больше не возвращает testCode.
Код приходит только по SMS, кроме demo-номера для App Store Review.
```

---

# 18. Пример frontend flow

```text
LoginScreen
    ↓ POST /api/auth/send-code
    ↓ SMS приходит пользователю

VerifyCodeScreen
    ↓ POST /api/auth/verify-code
    ↓ save userId

ProfileScreen / Bootstrap
    ↓ GET /api/users/me?userId=...
    ↓ PUT /api/users/me?userId=...            optional
    ↓ DELETE /api/users/me?userId=...         account deletion

SelectCompanyScreen
    ↓ GET /api/companies/my?userId=...

HomeScreen
    ↓ GET /api/companies/{companyId}/home?userId=...
    ↓ показывает generalChat.unreadCount
    ↓ показывает aiDirector.available

AiDirectorScreen
    ↓ GET /api/companies/{companyId}/ai/history?userId=...&limit=50
    ↓ GET /api/companies/{companyId}/ai/director/summary/today?userId=...
    ↓ POST /api/companies/{companyId}/ai/ask?userId=...
    ↓ response contains threadId and messageId

ChatScreen
    ↓ GET /api/companies/{companyId}/chats/general/messages?userId=...&limit=50
    ↓ polling: GET /messages?userId=...&after={lastMessageId}&limit=50
    ↓ scroll up: GET /messages?userId=...&before={oldestMessageId}&limit=50
    ↓ POST /api/companies/{companyId}/chats/general/messages
    ↓ POST /api/companies/{companyId}/chats/general/read?userId=...

FilesScreen
    ↓ GET /api/companies/{companyId}/files?userId=...
    ↓ POST /api/companies/{companyId}/files/upload?userId=...
    ↓ GET /api/companies/{companyId}/files/{fileId}/download-url?userId=...

SearchScreen
    ↓ GET /api/companies/{companyId}/search?userId=...&query=...&limit=20

SendFileToChat
    ↓ POST /api/companies/{companyId}/files/upload?userId=...
    ↓ POST /api/companies/{companyId}/chats/general/messages/file
```
