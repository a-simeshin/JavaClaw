# Plan: Flyway Migrations V4-V10 — All P0 Tables (Roadmap 1.1)

## Task Description

Создать реальные Flyway-миграции V4-V10 в `javaclaw-core/src/main/resources/db/migration/` на основе уже финализированного DDL из `specs/db-schema-v1.md`. Spec проверена 31 тестом (включая `DbSchemaV1FullMigrationE2ETest` который уже гонял Flyway V1-V10 через temp-файлы). Задача сводится к: извлечь DDL из спеки → записать в правильные файлы → убедиться в зелёном билде.

Закрывает пункт роадмапа **1.1** ("DB Schema + Flyway миграции").

## Objective

По завершении:
- 7 новых файлов миграций в `javaclaw-core/src/main/resources/db/migration/`:
- `V4__create_users.sql`
- `V5__create_conversations.sql`
- `V6__create_virtual_files.sql`
- `V7__create_skills.sql`
- `V8__create_mcp_servers.sql`
- `V9__create_config.sql`
- `V10__seed_default_users.sql`
- `mvn -pl javaclaw-core test -Dtest='DbSchemaV1*Test'` зелёный (31 тест, включая FullMigrationE2ETest)
- `mvn -pl javaclaw-app,javaclaw-core clean verify` зелёный
- `specs/roadmap.md` пункт 1.1 → ✅

## Problem Statement

Без реальных Flyway-файлов:
- `spring-boot:run` против реальной PostgreSQL поднимает только 3 таблицы (tasks, recurring_tasks, SPRING_AI_CHAT_MEMORY)
- Tasks 1.2/1.4/1.5 (VirtualFile, Skills, MCP JDBC migration) не могут начаться — таблицы не существуют
- Phase 4 (Spring Security users) не может начаться

`specs/db-schema-v1.md` содержит весь DDL, `DbSchemaV1FullMigrationE2ETest` уже доказал что он корректен. Задача — перенести из temp-файлов теста в реальные classpath-миграции.

## Solution Approach

**Прямое копирование DDL из spec в файлы миграций.**

### Ключевое: SPRING_AI_CHAT_MEMORY quoting issue

V2 миграция создаёт таблицу без кавычек → PostgreSQL хранит имя в lowercase (`spring_ai_chat_memory`). Поэтому в V5 `ALTER TABLE` нужно использовать **unquoted lowercase**:

```sql
-- ПРАВИЛЬНО (V5):
ALTER TABLE spring_ai_chat_memory
    ADD CONSTRAINT fk_chat_memory_conversation_id
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED;

-- НЕПРАВИЛЬНО:
ALTER TABLE "SPRING_AI_CHAT_MEMORY" -- не найдёт таблицу
```

Это уже зафиксировано в `DbSchemaV1FullMigrationE2ETest` ("unquoted in the E2E context").

### Application startup check

После создания файлов: `mvn -pl javaclaw-app,javaclaw-core -am clean verify`. Если приложение поднимается против real PostgreSQL в CI — зелёно. В тестовой среде используем Testcontainers через существующие `DbSchemaV1FullMigrationE2ETest` и `OpenApiContractComplianceTest`.

## Relevant Files

- `specs/db-schema-v1.md` — **источник DDL** для всех 7 файлов миграций (секция "Migrations V4-V10")
- `javaclaw-core/src/main/resources/db/migration/V1__init_tasks.sql` — эталонный стиль
- `javaclaw-core/src/main/resources/db/migration/V2__init_chat_memory.sql` — создаёт SPRING_AI_CHAT_MEMORY unquoted
- `javaclaw-core/src/test/java/ai/javaclaw/db/DbSchemaV1FullMigrationE2ETest.java` — готовый тест Flyway V1-V10 (уже зелёный с temp-файлами)
- `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiContractComplianceTest.java` — регрессионный тест (использует Testcontainers + Spring Boot)
- `javaclaw-app/src/main/resources/application.yaml` — Flyway enabled: true

### New Files

- `javaclaw-core/src/main/resources/db/migration/V4__create_users.sql`
- `javaclaw-core/src/main/resources/db/migration/V5__create_conversations.sql`
- `javaclaw-core/src/main/resources/db/migration/V6__create_virtual_files.sql`
- `javaclaw-core/src/main/resources/db/migration/V7__create_skills.sql`
- `javaclaw-core/src/main/resources/db/migration/V8__create_mcp_servers.sql`
- `javaclaw-core/src/main/resources/db/migration/V9__create_config.sql`
- `javaclaw-core/src/main/resources/db/migration/V10__seed_default_users.sql`

## Team Orchestration

### Team Members

- **Builder-Migrations**
  - Name: `builder-migrations`
  - Role: Читает `specs/db-schema-v1.md`, создаёт 7 SQL-файлов с правильным DDL (особое внимание на V5 lowercase ALTER). Запускает компиляцию.
  - Agent Type: `general-purpose`
  - Model: `haiku` (механическое копирование + запись файлов)
  - Resume: true
- **Test-Validator-DB**
  - Name: `test-validator-db`
  - Role: Прогоняет `DbSchemaV1FullMigrationE2ETest` и `OpenApiContractComplianceTest` против реальных classpath-миграций. Отмечает в родмапе.
  - Agent Type: `validator`
  - Resume: true

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

- `DbSchemaV1DdlValidationTest` (уже написан, 16 тестов) — парсит spec и валидирует DDL синтаксически. Регрессия: прогнать после создания файлов.
- Проверка naming conventions файлов миграций: `ls -la V*.sql | grep -E 'V[0-9]+__'`

### Integration / API Tests (15%)

- `DbSchemaV1FullMigrationE2ETest` (уже написан, 2 теста) — реальный Flyway V1-V10 + seed. Теперь V4-V10 будут из classpath вместо temp-файлов → тест должен остаться зелёным.
- `DbSchemaV1SpecTest` (13 тестов) — inline DDL тест через Testcontainers. Параллелен с migration E2E.
- `OpenApiContractComplianceTest` (30 тестов) — Spring Boot поднимается с Testcontainers PostgreSQL, Flyway прогоняет V1-V10, все endpoint'ы работают. Ключевой регрессионный тест.

### UI E2E Tests (5%)

**НЕ запускаем** — правило: E2E только когда logic+DB+UI готовы вместе. Users-система ещё без Spring Security и без UI-авторизации. E2E deferred.

## Step by Step Tasks

### 1. Создать файлы миграций V4-V10

- **Task ID**: create-migration-files
- **Depends On**: none
- **Assigned To**: builder-migrations
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot maven
- **Parallel**: false
- **Tests**: DbSchemaV1DdlValidationTest (existing 16 tests должны остаться зелёными)
- Прочитать секцию "Migrations V4-V10" из `specs/db-schema-v1.md` — извлечь DDL для каждой версии
- **V4__create_users.sql**: CREATE TABLE users + индексы (UNIQUE index на username + idx_users_role + idx_users_active)
- **V5__create_conversations.sql**: CREATE TABLE conversations + индексы + `ALTER TABLE spring_ai_chat_memory ADD CONSTRAINT fk_chat_memory_conversation_id ... DEFERRABLE INITIALLY DEFERRED` (LOWERCASE, без кавычек)
- **V6__create_virtual_files.sql**: CREATE TABLE virtual_files + partial unique indexes (WHERE owner_id IS NOT NULL / WHERE owner_id IS NULL)
- **V7__create_skills.sql**: CREATE TABLE skills + partial unique indexes
- **V8__create_mcp_servers.sql**: CREATE TABLE mcp_servers (transport CHECK IN ('stdio','http')) + headers JSONB + partial unique indexes
- **V9__create_config.sql**: CREATE TABLE config (scope CHECK IN ('global','user')) + partial unique indexes
- **V10__seed_default_users.sql**: INSERT admin/user с `password_hash = NULL` + `ON CONFLICT DO NOTHING`
- Запустить `mvn -pl javaclaw-core clean compile -q` — убедиться в компиляции (SQL-файлы копируются в target, ошибок не должно быть)

### 2. Прогнать DB тесты против реальных classpath-миграций

- **Task ID**: run-migration-tests
- **Depends On**: create-migration-files
- **Assigned To**: test-validator-db
- **Agent Type**: validator
- **Stack**: Java testcontainers integration test jdbc database test repository test assertj allure test structure maven
- **Parallel**: false
- **Tests**: DbSchemaV1FullMigrationE2ETest (2 тесты) + DbSchemaV1SpecTest (13) + DbSchemaV1DdlValidationTest (16) + OpenApiContractComplianceTest (30)
- `mvn -pl javaclaw-core test -Dtest='DbSchemaV1*Test'` — 31 тест зелёный (теперь FullMigrationE2ETest читает реальные classpath-файлы)
- `mvn -pl javaclaw-app test -Dtest='OpenApiContractComplianceTest' -Dsurefire.failIfNoSpecifiedTests=false` — 30 тестов зелёные
- Проверить Flyway schema_history через SpecTest: строки V1-V10 все со статусом `success`
- Если тест падает — диагностировать: вероятная причина — quoting в V5 ALTER (SPRING_AI_CHAT_MEMORY vs spring_ai_chat_memory), исправить
- Обновить родмап: 1.1 → ✅
- Обновить таблицу статусов: строка "1.1 DB Schema + Flyway" → ✅

## Acceptance Criteria

- [ ] 7 файлов миграций созданы в `javaclaw-core/src/main/resources/db/migration/` (V4-V10)
- [ ] `mvn -pl javaclaw-core compile -q` успешен
- [ ] `mvn -pl javaclaw-core test -Dtest='DbSchemaV1*Test'` — 31/31 зелёных
- [ ] `mvn -pl javaclaw-app test -Dtest='OpenApiContractComplianceTest'` — 30/30 зелёных
- [ ] В `DbSchemaV1FullMigrationE2ETest.seededUsers_matchExpectedDefaults` — admin + user присутствуют в таблице users
- [ ] Flyway schema_history после `fullMigration_V1toV10` содержит ровно 10 записей success
- [ ] `specs/roadmap.md` 1.1 → ✅
- [ ] Строка таблицы статусов "1.1 Flyway миграции" обновлена с ⚠️ → ✅

## Validation Commands

- `ls /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw/javaclaw-core/src/main/resources/db/migration/ | grep -E '^V[4-9]|^V10'` — 7 файлов
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-core test -Dtest='DbSchemaV1*Test' 2>&1 | tail -20`
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-app test -Dtest='OpenApiContractComplianceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20`

## Notes

- **Quoting critical**: V5 ALTER TABLE нужен LOWERCASE `spring_ai_chat_memory` — так V2 создаёт таблицу (без кавычек = lowercase в PG). Это уже проверено в `DbSchemaV1FullMigrationE2ETest`.
- **DbSchemaV1FullMigrationE2ETest** уже пишет V4-V10 как temp-файлы из extracted DDL → тест должен работать и с реальными classpath файлами идентично. Если DDL совпадает — тест просто зелёный без правок.
- **E2E деferred** — без Spring Security и UI-авторизации browser test нет смысла гонять.
- **JSONB для mcp_servers.headers** — DDL содержит `JSONB` тип, PostgreSQL 17 поддерживает из коробки. Никаких extensions не нужно.
- **gen_random_uuid()** — доступна в PG 13+ без расширений. PG 17 — ок.
- **application.yaml**: `flyway.locations` не указан явно → default `classpath:db/migration`. Файлы кладём именно туда — Flyway найдёт автоматически.

