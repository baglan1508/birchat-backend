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
| 0.3 | `aiEnabled` | тест и прод, с 23.11 |
| 0.4 | `POST /employees` без чужого профиля | тест и прод, с 23.11 |
| 0.5 | Убрать сотрудника | тест и прод, с 23.11 |
| 1 | Push на Android (FCM) | тест и прод, с 23.11; пуши пойдут, когда владелец добавит ключ Firebase |
| — | `general` вместо id общего чата везде | тест и прод, с 23.11 |
| 2 | Личные чаты | тест и прод, с 23.11 |
| 3 | Ответ и пересылка полями | тест и прод, с 23.11 |
| 4 | Редактирование | тест и прод, с 23.11 |
| 6 | Упоминания `@` | тест и прод, с 23.11 |
| 7 | Реакции | тест и прод, с 23.11 |
| 8 | Закреплённое сообщение | тест и прод, с 23.11 |
| 9 | Поиск и файлы по одному чату | тест и прод, с 23.11 |
| 10 | Длительность голосового | тест и прод, с 23.11 |
| 11 | Страница вокруг сообщения | тест и прод, с 23.11 |
| 12 | Удаление группы | тест и прод, с 23.11 |
| 13 | Группы создаёт не только директор | тест и прод, с 23.11 |
| 14 | AI видит и группы | тест и прод, с 23.11 |
| 15 | Тихий push с бейджем | тест и прод, с 23.11 |
| 5 | Аватары | ждём `BACKEND_PROFILE_TASKS.md` |

---

## Проверка пушей: тестовый пуш

```http
POST /api/devices/test
Authorization: Bearer {accessToken}
→ 200 [ { "platform", "environment", "tokenEnd", "result", "status", "reason", "removed" } ]
```

Отправляет тестовое уведомление на все устройства пользователя и сразу возвращает ответ Apple / Google по каждому: `SENT` — принято; `INVALID_TOKEN` — токен недействителен (устройство удалено, зарегистрируйте заново); `FAILED` — см. `status` и `reason`; `NOT_CONFIGURED` — на сервере нет ключа. Не чаще раза в 10 секунд. Вызывать можно из Postman/curl со своим токеном или из отладочного меню.

Частые причины: `BadDeviceToken` — окружение не совпадает со сборкой (Xcode — `sandbox`, TestFlight и App Store — `production`); `DeviceTokenNotForTopic` — Bundle ID не `kz.birchat.app`.

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

## 1. Push на Android (FCM)

```http
POST /api/devices
{ "platform": "ANDROID", "token": "<FCM registration token>", "locale": "ru" }
→ 204
```

* `environment` для Android не нужен (можно передавать `production` — сервер его игнорирует). Для iOS — как раньше, обязателен.
* Токен FCM хранится как есть (регистр важен). `DELETE /api/devices/{token}` — тот же токен.
* Формат сообщения — как в ТЗ:

```json
{
  "notification": { "title": "Nomad Trade · Склад Актобе", "body": "Нурлан: По оплате ждём…" },
  "data": { "companyId": "…", "chatId": "…", "messageId": "…", "title": "Nomad Trade · Склад Актобе", "body": "Нурлан: По оплате ждём…" },
  "android": { "priority": "HIGH", "notification": { "channel_id": "messages", "tag": "<chatId>" } }
}
```

  Общий чат — `title` = компания; группа — «компания · группа»; личный чат — имя автора, `body` без «Имя:»; упоминание — «Нурлан упомянул(а) вас: …».
* Те же правила, что у APNs: всем участникам, кроме автора и тех, кто смотрит чат (`viewing`); упомянутым — всегда.
* Мёртвый токен (`UNREGISTERED`) сервер удаляет сам — регистрируйте устройство при каждом запуске и после входа.
* Канал уведомлений `messages` создаёт приложение. `google-services.json` с пакетом `kz.birchat.app` — у владельца проекта Firebase.

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

---

## 7. Реакции

```http
POST   …/chats/{chatId}/messages/{messageId}/reactions   { "emoji": "👍" }   → 204
DELETE …/chats/{chatId}/messages/{messageId}/reactions/{emoji}              → 204
```

* Набор: 👍 ❤️ 😂 😮 😢 🙏; другое — `400 VALIDATION`. Одна реакция человека на сообщение: новая заменяет прежнюю. `DELETE` чужой или несуществующей реакции — тоже `204`. Эмодзи в пути кодируйте (`Uri.encodeComponent`).
* В `ChatMessageResponse`: `reactions: [{ "emoji": "👍", "count": 3, "mine": true }]`.
* WebSocket: `{ "type": "message.reactions", "companyId", "chatId", "messageId", "reactions": [{ "emoji", "count", "userIds": [] }] }` — без `mine`, сравнивайте `userIds` со своим id. В остальных событиях `mine` всегда `false`.
* Без пушей, без отражения в `lastMessage`.

## 8. Закреплённое сообщение

```http
POST   …/chats/{chatId}/pin   { "messageId": "…" }   → 204
DELETE …/chats/{chatId}/pin                          → 204
```

* Одно на чат. Общий чат — директор, группа — директор или создатель (`canManage`), личный — любой из двоих; иначе `403 FORBIDDEN`.
* `pinnedMessage` (`ChatMessageResponse` или `null`) — в `ChatDetailsResponse` (`GET /chats/{chatId}`) и в `generalChat` у `GET /companies/{id}/home`.
* WebSocket: `{ "type": "chat.pinned", "companyId", "chatId", "message": { … } | null }`.
* Закреплённое удалили у всех — закреп снимается (придёт `chat.pinned` с `null`).

## 9. Поиск и файлы по одному чату

```http
GET /api/companies/{companyId}/search?query=…&limit=50&chatId={chatId|general}&type={all|messages|files}
GET /api/companies/{companyId}/files?limit=50&chatId={chatId|general}&kind={documents|media|voice}&before={fileId}
```

* Все новые параметры необязательные. `chatId=general` — сообщения общего чата и файлы компании без группы. Чужая группа — `403 CHAT_ACCESS_DENIED`.
* `kind`: `media` — `image/*` и `video/*`, `voice` — `audio/*`, `documents` — остальное.
* `before` — следующая страница: файлы старше этого файла (передавайте `id` последнего файла из предыдущей страницы).
* Файлы, у которых все сообщения удалены у всех, больше не показываются.

## 10. Длительность голосового

* `POST /files/upload-url`: необязательное `durationMs` (целое, мс).
* Возвращается в `ChatAttachmentResponse` (вложения сообщений) и `CompanyFileResponse` (`durationMs`, у старых файлов `null`). Длительность в имени файла больше не нужна.

## 11. Страница истории вокруг сообщения

```http
GET …/chats/{chatId}/messages?around={messageId}&limit=50
```

`limit/2` сообщений до, само сообщение и остальные после — по возрастанию времени, как обычная страница. Дальше — обычные `before` / `after`. Вместе с `after` или `before` — `400 VALIDATION`. Работает и на `/chats/general/messages`.

## 12. Удаление группы

```http
DELETE /api/companies/{companyId}/chats/{chatId}   → 204
```

* Только `canManage` (директор или создатель), только `GROUP`: общий чат — `400 VALIDATION`, личный — `403 FORBIDDEN`.
* Группа пропадает у всех из списка, поиска, файлов и AI. Открытый чат отвечает `403 CHAT_ACCESS_DENIED` (уже обрабатывается).
* WebSocket всем, кто был в группе: `{ "type": "chat.deleted", "companyId", "chatId" }`.

## 13. Группы создаёт не только директор

* `PUT /api/companies/{companyId}` `{ "membersCanCreateGroups": true }` — только директор. Поле `membersCanCreateGroups` в `CompanyResponse`.
* Если включено — `POST /chats` доступен всем (иначе сотруднику `403 FORBIDDEN`). Создатель получает `canManage` для своей группы: переименовать, добавить и убрать участников, удалить, закрепить. Директор управляет любой группой.

## 14. AI видит и группы

* `/ai/ask`: у сотрудника — общий чат и группы, где он состоит; у директора — общий чат и все группы. **Личные чаты в AI не попадают ни у кого** (в т.ч. у сотрудника — его собственные: там сообщения второго человека).
* `GET /ai/director/summary/today` — новое поле:

```json
{
  "title": "Сводка за сегодня", "summary": "…", "items": ["…"], "createdAt": "…",
  "sections": [
    { "chatId": "…", "name": "Общий чат", "summary": "…" },
    { "chatId": "…", "name": "Склад Актобе", "summary": "…" }
  ]
}
```

  `sections` — только чаты, где за день были сообщения; у сводок, сделанных до выпуска, — пустой список.
* Всё — только при `aiEnabled` (0.3).

## 15. Тихий push с бейджем после прочтения

* После `POST …/read` сервер отправляет на устройства пользователя пуш только с `badge`, если общее число непрочитанных изменилось. Ничего не показывается и не звучит, цифра на иконке обновляется.
* `content-available` в нём нет: Apple не разрешает фоновый пуш с `badge`. Если приложению нужно обновлять данные в фоне — скажите, сделаем отдельным пушем.

