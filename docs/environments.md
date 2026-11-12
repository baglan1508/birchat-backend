# Окружения backend BirChat

| | Прод (production) | Тест (staging) |
|---|---|---|
| Для кого | реальные пользователи | проверка изменений перед продом |
| Render | `birchat-backend`, окружение Production | `birchat-backend-staging`, окружение Staging |
| Тариф Render | Starter (0.5 CPU, 512 MB) | Starter (0.5 CPU, 512 MB) |
| Регион | Frankfurt | Frankfurt |
| Ветка Git | `main` | `develop` |
| База | Neon, база `birchat-stage` (несмотря на имя — это прод) | Neon, бета-проект, база `birchat_beta` |
| Файлы | Supabase, bucket `birchat-files` | Supabase, bucket `birchat-beta-files` |
| Вход | Telegram Gateway (сейчас временно — одинаковый код) | одинаковый код, коды никуда не отправляются |
| AI | GLM | mock (без расходов), при необходимости GLM |
| Sentry | environment `production` | environment `staging` |
| Проверка | `GET /api/health` → `"environment": "production"` | `GET /api/health` → `"environment": "staging"` |

## Как изменения попадают на тест и на прод

```text
feature/... ──PR──▶ develop ──(авто)──▶ staging: проверяем
                       │
                       └──PR «Release»──▶ main ──(авто)──▶ production
```

1. Новая задача — ветка от `develop`, PR **в `develop`**.
2. После мержа тест обновляется сам. Проверяем на тесте.
3. Когда на тесте всё хорошо — PR из `develop` в `main` («Release …»). После мержа обновляется прод.
4. Срочное исправление прода — ветка от `main`, PR в `main`, потом `main` вливается обратно в `develop`.

## Как создан тестовый сервер

Тестовый сервер описан кодом в [`render.yaml`](../render.yaml) (Render Blueprint): тип, регион, тариф, ветка и переменные без секретов. Секреты вводятся в Render при создании Blueprint (`sync: false`) или генерируются Render (`generateValue`). Прод этим файлом не управляется.

## Переменные окружения

Все настройки — переменные окружения на Render. Секреты (пароли, ключи) у теста и прода **разные**: если тестовый ключ утечёт, прод не пострадает.

### Обязательные

| Переменная | Прод | Тест |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | `prod` (тот же набор настроек, различаются только значения) |
| `APP_ENVIRONMENT` | не задавать (= `production`) | `staging` |
| `SPRING_DATASOURCE_URL` | JDBC-адрес продовой базы | JDBC-адрес бета-базы: `jdbc:postgresql://<host>/birchat_beta?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | пользователь продовой базы | пользователь бета-базы |
| `SPRING_DATASOURCE_PASSWORD` | 🔒 | 🔒 свой |
| `AUTH_JWT_SECRET` | 🔒 ≥ 32 символа | 🔒 **другой**, ≥ 32 символа |
| `AUTH_SMS_OTP_SECRET` | 🔒 | 🔒 **другой** |
| `SUPABASE_URL` | URL проекта Supabase | тот же |
| `SUPABASE_SERVICE_KEY` | 🔒 | тот же ключ (отдельный проект Supabase — следующий шаг изоляции) |

### Вход по коду

| Переменная | Прод | Тест |
|---|---|---|
| `OTP_PROVIDER` | не задавать (= `telegram`) | `disabled` — тест никогда не отправит код в Telegram |
| `AUTH_SMS_FIXED_CODE` | временно, до запуска | 4 цифры, всегда |
| `TELEGRAM_GATEWAY_TOKEN` | 🔒 | не задавать |
| `AUTH_SMS_DEMO_PHONE`, `AUTH_SMS_DEMO_CODE` | demo-номер для App Review | не нужны |

### Файлы, AI, мониторинг

| Переменная | Прод | Тест |
|---|---|---|
| `SUPABASE_BUCKET` | `birchat-files` | `birchat-beta-files` |
| `AI_PROVIDER` | `glm` | `mock` (или `glm`, если нужно проверить настоящий AI) |
| `AI_GLM_API_KEY` | 🔒 | только если `AI_PROVIDER=glm` |
| `SENTRY_DSN` | DSN проекта | тот же DSN — события разделяются по environment |

### Необязательные (значения по умолчанию подходят обоим)

`AUTH_JWT_ISSUER`, `AUTH_JWT_ACCESS_TTL_SECONDS` (900), `AUTH_JWT_REFRESH_TTL_DAYS` (30),
`AUTH_SMS_CODE_TTL_SECONDS` (300), `AUTH_SMS_RESEND_COOLDOWN_SECONDS` (60), `AUTH_SMS_MAX_ATTEMPTS` (5),
`AI_GLM_*` (в т.ч. `AI_GLM_THINKING` — `disabled`, режим рассуждений GLM выключен), `AI_SUMMARY_*`, `CLEANUP_ENABLED`, `CLEANUP_CRON`, `DB_POOL_MAX_SIZE` (5), `DB_POOL_MIN_IDLE` (0),
`SENTRY_ENVIRONMENT` (= `APP_ENVIRONMENT`), `SENTRY_TRACES_SAMPLE_RATE` (0).
