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
