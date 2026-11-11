# Выпуск 08: Flyway с поддержкой PostgreSQL 18

Ветка: `chore/flyway-pg18` → `develop` (тест) → `main` (прод)
Пункт плана: №9 (см. [README](README.md))

## Проблема

База в Neon — PostgreSQL 18.6, а Spring Boot 3.5 приносит Flyway 11.7.2, который официально поддерживает PostgreSQL только до 17. При каждом старте в логах:

```text
Flyway upgrade recommended: PostgreSQL 18.6 is newer than this version of Flyway and support has not been tested.
The latest supported version of PostgreSQL is 17.
```

Сейчас миграции применяются, но это работа «на свой страх»: Flyway не тестировался на этой версии базы.

## Что изменилось

* `pom.xml`: `flyway.version = 11.20.3` — последняя версия ветки 11.x (26.01.2026). PostgreSQL 18 поддерживается с 11.13.3. В версиях 11.12–11.20 по заметкам Flyway нет несовместимых изменений; ветку 12.x не берём — в ней есть несовместимые изменения, а Spring Boot 3.5 рассчитан на 11.x.
* Свойство меняет версию и `flyway-core`, и `flyway-database-postgresql`.

### Проверка миграций в CI

Раньше миграции проверялись только на тесте (Render + Neon) после мержа. Теперь в GitHub Actions на каждый PR:

* поднимается чистый PostgreSQL 18 (контейнер `postgres:18`, как в Neon);
* `FlywayMigrationsPostgresTest` прогоняет все миграции через автонастройку Spring Boot — так же, как при старте на Render;
* проверяет: все миграции применены, функция поиска из V16 работает, предупреждения «Flyway upgrade recommended» нет.

Локально тест пропускается (нет базы).

### Тест действительно ловит проблему

Сначала тест запущен в CI **со старым Flyway 11.7.2** на PostgreSQL 18.6: все 16 миграций применились, поиск из V16 работает, но тест упал на предупреждении «Flyway upgrade recommended … The latest supported version of PostgreSQL is 17». Затем добавлена версия 11.20.3 — результат ниже.

## Откат

Rollback деплоя на Render. Схема базы не меняется (новых миграций нет), таблица `flyway_schema_history` совместима.
