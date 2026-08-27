# BirChat Backend API для Flutter Frontend

Версия: `v0.1 MVP`
Backend: `Java Spring Boot`
Database: `PostgreSQL`
Base URL local:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

---

# 1. Общая информация

На текущем этапе backend работает в MVP-режиме:

* авторизация по телефону mock-логикой;
* SMS пока не отправляется реально;
* тестовый код подтверждения всегда `1111`;
* JWT пока не реализован;
* вместо JWT временно используется `userId`;
* для некоторых методов временно передается `actorUserId`, чтобы понять, кто выполняет действие.

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

### Response

```json
{
  "message": "Код подтверждения отправлен. Для теста используйте код 1111",
  "testCode": "1111"
}
```

### Использование во Flutter

Этот метод вызывается на экране входа после ввода номера телефона.

Пока код всегда:

```text
1111
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
  "code": "1111"
}
```

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

### Логика

Если пользователь с таким телефоном уже есть в базе, backend возвращает существующего пользователя.

Если пользователя нет, backend создает нового пользователя.

---

# 3. Компании

## 3.1. Получить список компаний пользователя

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

## 3.2. Создать компанию

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

## 3.3. Получить данные главного экрана компании

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
    "lastMessage": "Сообщение отправлено через Java backend из Swagger",
    "lastMessageAt": "2026-08-28T00:32:36.349339"
  },
  "aiDirector": {
    "available": true,
    "label": "AI Director"
  }
}
```

### Использование во Flutter

Используется для главного экрана компании.

`aiDirector.available = true` означает, что пользователь может видеть AI Director.

Сейчас AI Director доступен для роли:

```text
DIRECTOR
```

---

# 4. Общий чат компании

## 4.1. Получить сообщения общего чата

### Endpoint

```http
GET /api/companies/{companyId}/chats/general/messages
```

### Example

```http
GET /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/chats/general/messages
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
    "createdAt": "2026-08-28T00:02:08.051135"
  }
]
```

### Использование во Flutter

Используется на экране общего чата.

Для определения своих сообщений на Flutter:

```dart
final isMine = message.userId == currentUserId;
```

Backend поле `isMine` не возвращает.

---

## 4.2. Отправить сообщение в общий чат

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
  "createdAt": "2026-08-28T00:32:36.349339"
}
```

### Ограничения

На текущем этапе поддерживается только тип сообщения:

```text
TEXT
```

Позже будут добавлены:

```text
DOCUMENT
VOICE
IMAGE
SYSTEM
```

---

# 5. Сотрудники компании

## 5.1. Получить список сотрудников

### Endpoint

```http
GET /api/companies/{companyId}/employees
```

### Example

```http
GET /api/companies/5479c4a8-f805-4d0d-bbc8-87a45af85b93/employees
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
    "joinedAt": "2026-08-28T00:00:00"
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

---

## 5.2. Добавить сотрудника

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
  "joinedAt": "2026-08-28T00:00:00"
}
```

### Логика

Добавлять сотрудников могут только пользователи с ролью:

```text
DIRECTOR
ADMIN
```

Если пользователь с таким номером телефона уже существует, backend использует существующую запись.

Если пользователя нет, backend создает нового пользователя.

---

# 6. Роли

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

# 7. Рекомендуемая структура Flutter API слоя

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

lib/data/repositories/auth_repository.dart
lib/data/repositories/company_repository.dart
lib/data/repositories/chat_repository.dart
lib/data/repositories/employee_repository.dart
```

---

# 8. Рекомендуемый ApiClient на Flutter

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
            baseUrl: 'http://localhost:8080',
            connectTimeout: const Duration(seconds: 10),
            receiveTimeout: const Duration(seconds: 20),
            headers: {
              'Content-Type': 'application/json',
            },
          ),
        );
}
```

Для Android emulator `localhost` может не работать. Тогда использовать:

```text
http://10.0.2.2:8080
```

Для реального телефона нужно использовать IP компьютера в локальной сети, например:

```text
http://192.168.1.10:8080
```

---

# 9. Что сейчас готово для подключения Flutter

Готовые backend API:

```text
POST /api/auth/send-code
POST /api/auth/verify-code

GET  /api/companies/my
POST /api/companies
GET  /api/companies/{companyId}/home

GET  /api/companies/{companyId}/chats/general/messages
POST /api/companies/{companyId}/chats/general/messages

GET  /api/companies/{companyId}/employees
POST /api/companies/{companyId}/employees
```

---

# 10. Что будет добавлено позже

Следующие API будут добавляться по мере разработки:

```text
Файлы:
POST /api/companies/{companyId}/files/upload
GET  /api/companies/{companyId}/files
GET  /api/companies/{companyId}/files/{fileId}

AI:
POST /api/companies/{companyId}/ai/ask
GET  /api/companies/{companyId}/ai/director/summary/today

Поиск:
GET  /api/companies/{companyId}/search

История отправок:
GET  /api/companies/{companyId}/share-history
POST /api/companies/{companyId}/share-history

Профиль:
GET /api/users/me
PUT /api/users/me

Настройки компании:
GET /api/companies/{companyId}/settings
PUT /api/companies/{companyId}/settings
```

---

# 11. Временные технические ограничения

На текущем этапе:

* нет настоящего JWT;
* нет Spring Security;
* нет реальной отправки SMS;
* нет WebSocket;
* нет загрузки файлов;
* нет AI-интеграции;
* нет ролей на уровне permissions;
* нет refresh token;
* нет обработки ошибок в едином формате.

---

# 12. Рекомендации для Flutter-разработки

Frontend может начинать подключение в таком порядке:

1. `POST /api/auth/send-code`
2. `POST /api/auth/verify-code`
3. сохранить `userId` локально;
4. `GET /api/companies/my?userId=...`
5. выбрать компанию;
6. `GET /api/companies/{companyId}/home?userId=...`
7. открыть чат;
8. `GET /api/companies/{companyId}/chats/general/messages`
9. отправить сообщение через `POST /api/companies/{companyId}/chats/general/messages`

---

# 13. Пример frontend flow

```text
LoginScreen
    ↓ POST /api/auth/send-code

VerifyCodeScreen
    ↓ POST /api/auth/verify-code
    ↓ save userId

SelectCompanyScreen
    ↓ GET /api/companies/my?userId=...

HomeScreen
    ↓ GET /api/companies/{companyId}/home?userId=...

ChatScreen
    ↓ GET /api/companies/{companyId}/chats/general/messages
    ↓ POST /api/companies/{companyId}/chats/general/messages
```
