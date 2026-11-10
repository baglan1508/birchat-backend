# Файлы во Flutter: загрузка напрямую в хранилище и показ внутри приложения

Backend v1.4. Касается фото, голосовых, видео и документов в чатах.

## Что меняется

| Было | Стало |
|---|---|
| Файл целиком отправляется на backend (`POST /files/upload`, multipart) | Телефон загружает файл **прямо в хранилище** по ссылке от backend |
| Фото открывается ссылкой в **браузере телефона** | Фото, голосовые и видео показываются **внутри приложения** |
| Ссылка на каждый файл — отдельный запрос | Ссылки на все файлы экрана — **одним запросом** |

Старая загрузка `POST /files/upload` пока работает, но будет отключена после перехода приложения на новую.

---

## 1. Загрузка файла (3 шага)

### Шаг 1. Получить ссылку на загрузку

```http
POST /api/companies/{companyId}/files/upload-url
Authorization: Bearer {accessToken}

{
  "fileName": "IMG_2041.jpg",
  "contentType": "image/jpeg",
  "fileSize": 2345678,
  "chatId": "…"            // для группового чата; для общего чата — не передавать
}
```

Ответ `201`:

```json
{
  "fileId": "7f1c…",
  "uploadUrl": "https://….supabase.co/storage/v1/object/upload/sign/…?token=…",
  "method": "PUT",
  "headers": { "Content-Type": "image/jpeg", "x-upsert": "false" },
  "expiresAt": "2026-11-10T19:40:00Z",
  "maxFileSize": 20971520
}
```

Ограничения:

* размер — до **20 МБ**;
* типы — фото (`image/*`), голосовые (`audio/*`), видео (`video/*`), PDF, Word/Excel/PowerPoint, `text/plain`, `text/csv`, ZIP. Остальное — `400 VALIDATION`.

`contentType` берите из пакета `mime` по расширению или от камеры/рекордера. Для голосовых, записанных рекордером, обычно `audio/m4a` или `audio/aac`.

### Шаг 2. Загрузить файл в хранилище

Запрос идёт **не на backend**, а по `uploadUrl`. Заголовок `Authorization` **не нужен** — права уже в ссылке.

```dart
final res = await http.put(
  Uri.parse(upload.uploadUrl),
  headers: upload.headers,        // Content-Type и x-upsert из ответа шага 1
  body: await file.readAsBytes(),
);
if (res.statusCode != 200) throw UploadFailed(res.body);
```

Для прогресс-бара удобнее `Dio().put(uploadUrl, data: file.openRead(), options: Options(headers: {...upload.headers, Headers.contentLengthHeader: fileSize}), onSendProgress: …)`.

Ссылка действует 2 часа. Если не успели — запросите новую (шаг 1).

### Шаг 3. Подтвердить загрузку

```http
POST /api/companies/{companyId}/files/{fileId}/complete
Authorization: Bearer {accessToken}
```

Ответ — карточка файла (`CompanyFileResponse`), как раньше. Backend проверяет, что файл действительно в хранилище, и записывает настоящие размер и тип.

Ошибки:

```text
400 FILE_NOT_FOUND — файл ещё не загружен (шаг 2 не прошёл)
400 VALIDATION     — файл больше 20 МБ (он будет удалён)
404 FILE_NOT_FOUND — чужой fileId
```

### Шаг 4. Отправить в чат — без изменений

```http
POST /api/companies/{companyId}/chats/{chatId}/messages/file
{ "fileId": "7f1c…", "text": "Накладная" }
```

Пока файл не подтверждён (шаг 3), отправить его в чат нельзя.

Если загрузку прервали и не подтвердили — файл никто не видит, через сутки backend удалит его сам.

---

## 2. Показ внутри приложения (не в браузере)

### Получить ссылки для экрана — одним запросом

Когда на экране чата есть вложения, соберите их `fileId` и запросите ссылки разом:

```http
POST /api/companies/{companyId}/files/download-urls
Authorization: Bearer {accessToken}

{ "fileIds": ["7f1c…", "a0b2…"], "expiresInSeconds": 3600 }
```

```json
[
  {
    "fileId": "7f1c…",
    "contentType": "image/jpeg",
    "fileSize": 2345678,
    "downloadUrl": "https://….supabase.co/storage/v1/object/sign/…?token=…",
    "expiresAt": "2026-11-10T20:40:00Z"
  }
]
```

* До 100 файлов за запрос; `expiresInSeconds` — от 60 до 3600 (по умолчанию 300).
* Файлы, к которым у пользователя нет доступа, просто не возвращаются.
* Держите ссылки в памяти до `expiresAt` и не запрашивайте повторно на каждый polling.

### Фото

Не открывайте ссылку через `url_launcher`. Показывайте картинку в приложении:

```dart
CachedNetworkImage(
  imageUrl: item.downloadUrl,
  cacheKey: item.fileId,          // ВАЖНО: ссылка меняется при каждой выдаче, кешируем по fileId
  fit: BoxFit.cover,
)
```

По нажатию — полноэкранный просмотр с зумом (`photo_view`), с тем же `cacheKey`.

### Голосовые

```dart
await player.setUrl(item.downloadUrl);   // just_audio
await player.play();
```

### Видео

`video_player`: `VideoPlayerController.networkUrl(Uri.parse(item.downloadUrl))`.

### Документы (PDF, Word, Excel)

PDF — во встроенном просмотрщике (`pdfx` или `syncfusion_flutter_pdfviewer`) по `downloadUrl`. Остальные документы — скачать во временную папку и открыть системным приложением (`open_filex`) или через «Поделиться».

### Не используйте `fileUrl`

Поле `fileUrl` в карточке файла — внутренний адрес в хранилище. Хранилище закрытое, по этому адресу файл **не откроется**. Для показа и скачивания — только `downloadUrl` из `download-urls` (или старый `GET /files/{fileId}/download-url`).

### Как понять, что показывать

По полю `type` сообщения (`IMAGE`, `VOICE`, `DOCUMENT`) и `contentType` вложения: `image/*` — картинка, `audio/*` — плеер, `video/*` — видео, остальное — документ.

---

## Чек-лист

- [ ] Загрузка: `upload-url` → `PUT` в хранилище → `complete` → отправка в чат
- [ ] Для групп в `upload-url` передаётся `chatId`
- [ ] Прогресс загрузки и повтор при ошибке (новая ссылка после 2 часов)
- [ ] Ссылки на вложения экрана — одним `download-urls`, кеш до `expiresAt`
- [ ] Фото — `CachedNetworkImage` с `cacheKey: fileId`, полноэкранный просмотр внутри приложения
- [ ] Голосовые — плеер внутри приложения
- [ ] `url_launcher` для фото больше не используется
