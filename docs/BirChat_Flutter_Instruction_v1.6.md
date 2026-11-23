# BirChat backend v1.6 — контракт для приложения

Ответ на `BACKEND_TASKS_v1.6.md`. База — v1.5 (`BirChat_Flutter_Instruction_v1.5.md`), всё оттуда работает как раньше.
Пункты появляются здесь по мере выкладки; у каждого указано, где он уже работает.

Правила те же, что в v1.6: новые поля только добавляются; всё, что меняет сообщения или чат, приходит событием WebSocket всем участникам (включая автора на других устройствах) и доступно опросом; сначала staging.

| | REST | WebSocket |
|---|---|---|
| Тест (staging) | `https://birchat-backend-staging.onrender.com` | `wss://birchat-backend-staging.onrender.com/ws` |
| Прод | `https://birchat-backend.onrender.com` | `wss://birchat-backend.onrender.com/ws` |

## Статус

| # | Задача | Где работает |
|---|---|---|
| 0.1 | Код входа через Telegram | код готов; включение на проде — настройка владельца |
| 0.2 | Ключ APNs | staging и прод, с 16.11 |
| 0.3 | `aiEnabled` | выпуск 18 |
| 0.4 | `POST /employees` без чужого профиля | выпуск 18 |
| 0.5 | Убрать сотрудника | выпуск 18 |
| — | `general` вместо id общего чата везде | выпуск 18 |
| 2 | Личные чаты | выпуск 19 |
| 3 | Ответ и пересылка полями | выпуск 19 |
| 4 | Редактирование | выпуск 19 |
| 6 | Упоминания `@` | выпуск 19 |

---

## 0.3. Согласие компании на AI

```http
PUT /api/companies/{companyId}          (PATCH — то же самое)
{ "aiEnabled": true }
→ 200 CompanyResponse
```

* Только директор, иначе `403 FORBIDDEN`. Поля необязательные: что не передано — не меняется.
* `aiEnabled` (boolean) — новое поле в `CompanyResponse` (`GET /companies/my`, `POST /companies`) и в `company` у `GET /companies/{id}/home`.
* Новые компании — `false`. Уже существующие — `true`.
* Пока `false`:
  * `POST /ai/ask` и `GET /ai/director/summary/today` → `403 AI_DISABLED` (квота не списывается);
  * сводка по расписанию компанию пропускает, фото и PDF модели не отправляются;
  * `GET /ai/history` и `POST /ai/history/clear` работают как обычно.
* После включения AI анализирует только новые файлы: накопленный архив модели не отправляется.

```json
{ "code": "AI_DISABLED", "message": "AI выключен директором компании", "path": "…" }
```

## 0.4. `POST /employees` не раскрывает чужой профиль

Ответ одинаковый для нового и уже зарегистрированного номера:

```json
{
  "memberId": "…", "userId": null,
  "fullName": "Бухгалтер Света", "displayName": "Бухгалтер", "initials": "БС",
  "phone": "+77000000005", "avatarUrl": null,
  "role": "EMPLOYEE", "roleLabel": "Сотрудник", "position": "Бухгалтер",
  "status": "ACTIVE", "joinedAt": "…"
}
```

* `fullName` — имя из запроса, `userId` и `avatarUrl` — `null`.
* В `GET /employees` и в участниках чатов такой сотрудник показывается **под именем от директора и без фото**, пока сам не откроет приложение (`GET /companies/my`). Потом — под своим именем из профиля.
* `userId` в `GET /employees` есть у всех — по нему добавляют в группы. **Если после добавления нужен `userId` (например, сразу добавить в группу), берите его из `GET /employees`**, а не из ответа `POST`.

## 0.5. Убрать сотрудника из компании

```http
DELETE /api/companies/{companyId}/employees/{userId}    → 204
```

| Ошибка | Когда |
|---|---|
| `403 FORBIDDEN` | не директор |
| `400 VALIDATION` | себя или директора |
| `404 USER_NOT_FOUND` | человек не сотрудник компании |

* Человек выходит из общего чата и всех групп компании; его сообщения остаются; устройства не трогаем.
* Его запросы к компании → `403 NOT_A_MEMBER` (уже обрабатывается — возврат к списку компаний). Пуши и события чатов этой компании ему больше не приходят.
* WebSocket:

```json
{ "type": "company.removed", "companyId": "…" }                  // убранному
{ "type": "member.removed", "companyId": "…", "userId": "…" }    // остальным сотрудникам
```

  У событий компании нет `chatId`.
* Опрос для старых клиентов и после переподключения — `GET /employees` (убранного там нет).
* Того же человека можно добавить снова обычным `POST /employees`.

Флаг `AppConfig.removeEmployeeReady` можно включать после проверки на staging.

## `general` вместо id общего чата

* Работает в **любом** пути `/api/companies/{companyId}/chats/general/…`: удаление у всех, `messages/deleted`, `readers`, `GET /chats/general` и всё новое из v1.6.
* WebSocket: `{"type":"viewing","chatId":"general","companyId":"…"}` — общий чат отмечается открытым (без `companyId` сервер не знает, какой общий чат имеется в виду). Можно по-прежнему слать `generalChatId`.
* В событиях WebSocket `chatId` — всегда UUID.

---

## 2. Личные чаты

```http
POST /api/companies/{companyId}/chats/direct
{ "userId": "<собеседник>" }
→ 200 ChatDetailsResponse      // существующий чат или новый
```

* Любой сотрудник с любым сотрудником компании. Идемпотентно: на пару людей один чат.
* С собой — `400 VALIDATION`; собеседник не сотрудник компании — `404 USER_NOT_FOUND`.
* `type: "DIRECT"`, `name: null`, `canManage: false`, в `members` двое.
* `GET /chats` (`ChatSummaryResponse`): новое поле `peer` — у `DIRECT`, у остальных `null`:

```json
{ "chatId": "…", "type": "DIRECT", "name": null, "membersCount": 2, "unreadCount": 1,
  "lastMessage": "Привет", "lastMessageAt": "…", "createdAt": "…",
  "peer": { "userId": "…", "fullName": "Нурлан Ахметов", "displayName": "Нурлан", "initials": "НА", "avatarUrl": null } }
```

  Чат без сообщений в списке не показывается: создавайте его в момент открытия, он появится с первым сообщением.
* Сообщения, файлы, прочтение, удаление, `readers`, события WebSocket и push — через те же `/chats/{chatId}/…`. Файлы личного чата видны только двоим.
* Нельзя: переименовать (`PATCH` → `403 FORBIDDEN`), добавлять и убирать участников (`403 FORBIDDEN`). Директор чужие личные чаты не видит — ни в списке, ни в поиске, ни в файлах (`GET /chats/{id}` → `403 CHAT_ACCESS_DENIED`).
* Push: `title` — имя автора, `body` — только текст, без `subtitle`.
* Собеседника убрали из компании: чат остаётся в списке, переписка видна, отправка → `403 CHAT_ACCESS_DENIED`.

## 3. Ответ и пересылка — полями

Запрос `POST …/messages`:

```json
{ "text": "Да, до пятницы", "replyToMessageId": "…", "forwardedFromMessageId": "…", "mentions": ["…"] }
```

`POST …/messages/file` принимает `replyToMessageId` и `mentions` (пересылка — только через `POST …/messages`).

Ответ (`ChatMessageResponse`, новые поля):

```json
"replyTo": { "id": "…", "userId": "…", "authorName": "Нурлан", "text": "Первые 100 символов…", "type": "TEXT", "deleted": false },
"forwardedFrom": { "messageId": "…", "userId": "…", "authorName": "Айгерим" },
"editedAt": null,
"mentions": [{ "userId": "…", "fullName": "Айгерим Сатпаева" }]
```

* `replyToMessageId` — только неудалённое сообщение этого же чата, иначе `404 MESSAGE_NOT_FOUND`. Исходное удалили потом — `replyTo` остаётся с `text: null`, `deleted: true`. У вложения `text` — подпись, а без неё имя файла; `type` — тип исходного сообщения.
* `forwardedFromMessageId` — сообщение из чата этой компании, где отправитель состоит. `text` необязателен: без него берётся текст исходного. Тип и вложение — как у исходного: к новому сообщению прикрепляется **тот же файл**, и он открывается у всех в чате, куда переслали (`download-urls` его отдаёт). Пересылка пересланного показывает первоисточник.
* Старые сообщения со служебной строкой `> …` / `>> …` в тексте остаются как есть — клиенту нужно продолжать их понимать.

## 4. Редактирование сообщения

```http
PATCH /api/companies/{companyId}/chats/{chatId}/messages/{messageId}
{ "text": "Исправленный текст", "mentions": ["…"] }       // mentions — необязательно
→ 200 ChatMessageResponse
```

| Ошибка | Когда |
|---|---|
| `403 FORBIDDEN` | не автор |
| `403 EDIT_EXPIRED` | прошло больше 48 часов |
| `400 VALIDATION` | пустой текст у текстового сообщения (подпись файла стереть можно) |

* `editedAt` в `ChatMessageResponse` (`null`, если не редактировали). `mentions`, если переданы, заменяют прежние.
* WebSocket: `{ "type": "message.updated", "companyId", "chatId", "message": { … } }`.
* Опрос — по образцу `deleted`:

```http
GET …/chats/{chatId}/messages/updated?since={checkedAt}
→ { "messages": [ ChatMessageResponse… ], "checkedAt": "…" }
```

  При открытии чата — без `since` (вернётся пустой список и серверное время).
* Push не отправляется.

## 6. Упоминания `@`

* В запросе `mentions: [userId]` (и в `POST …/messages/file`, и в `PATCH`). Сервер оставляет только участников этого чата, себя и остальных молча отбрасывает. Текст не меняет.
* В ответе `mentions: [{ userId, fullName }]`.
* Упомянутому push приходит **даже если чат у него открыт**: «Нурлан упомянул(а) вас: …» / «Нурлан сізді атап өтті: …» — по языку устройства. Остальным — обычный.
* Лента «Меня упомянули» — позже, данные для неё уже хранятся.

