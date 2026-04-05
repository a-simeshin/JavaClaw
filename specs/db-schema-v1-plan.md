# Plan: DB Schema v1 Specification (Roadmap 0.1 + Phase 1.1 foundation)

## Task Description

Спроектировать DB schema v1 для всех P0 таблиц JavaClaw. Результат — документ `specs/db-schema-v1.md`: Mermaid ER-диаграмма, полный DDL по каждой таблице, обоснование колонок/индексов/FK, миграционный план V4→V10, маппинг на Java entities (для будущих задач 1.2/1.4/1.5), acceptance criteria.

Закрывает пункт роадмапа **0.1** ("Спека: DB schema v1"). Служит контрактом для реализации Flyway миграций в атомарной задаче **1.1** ("DB Schema + Flyway миграции"), которая последует в следующем цикле.

## Objective

По завершении:

- `specs/db-schema-v1.md` существует, содержит полный DDL для 7 новых таблиц (users, conversations, virtual_files, skills, mcp_servers, config) + рефренс на 3 существующих (tasks, recurring_tasks, SPRING_AI_CHAT_MEMORY)
- Mermaid ER-диаграмма с FK-зависимостями рендерится
- DDL валиден для PostgreSQL 17 — проверяется прогоном через Testcontainer
- Для каждой таблицы: обоснование колонок, типов, индексов, constraints, FK rules (ON DELETE CASCADE/RESTRICT)
- Миграционный план V4→V10 с инкрементальным порядком (не ломая существующие V1-V3)
- Mapping на Java records/entities готов к прямому использованию Spring Data JDBC в задачах 1.2/1.4/1.5
- Конвенции: snake_case, VARCHAR(36) UUID + `gen_random_uuid()::varchar`, TIMESTAMP + `DEFAULT now()`, `idx_<table>_<column>` индексы — зафиксированы в спеке
- Интеграционный тест `DbSchemaV1SpecTest` применяет DDL к Testcontainer PostgreSQL, проверяет создание таблиц, вставку/выборку данных, работу FK/UNIQUE/CHECK constraints

## Problem Statement

Текущее состояние persistence — **фрагментировано**:

|       Сущность        |          Хранение          |                    Проблема                    |
|-----------------------|----------------------------|------------------------------------------------|
| Tasks, RecurringTask  | JDBC (V1)                  | OK                                             |
| SPRING_AI_CHAT_MEMORY | JDBC (V2-V3)               | без conversations metadata, нет user ownership |
| Skills                | ConcurrentHashMap в памяти | Теряются при рестарте                          |
| MCP Servers           | ConcurrentHashMap в памяти | Теряются при рестарте                          |
| Virtual Files         | FileSystem (./workspace/)  | Не изолировано per-user                        |
| Configuration         | application.private.yaml   | Не в БД                                        |
| Users                 | Отсутствуют                | Нет модели для Basic Auth (Phase 4.1)          |

Без единой DB-схемы невозможно реализовать:
- Per-user изоляцию (4.2, 4.3)
- Persistence skills/MCP между рестартами (1.4, 1.5)
- Virtual files в БД (1.2)
- Basic Auth с пользователями в БД (Phase 7)
- Мониторинг и RBAC в будущих фазах

Этот документ устраняет неопределённость: каждая будущая JDBC-задача будет реализовывать ИМЕННО этот контракт.

## Solution Approach

**Подход: single-document comprehensive spec** в `specs/db-schema-v1.md` + validation через Testcontainer.

### Принципы проектирования

1. **Совместимость с существующими V1-V3** — ничего не ломаем, только добавляем V4-V10
2. **Nullable ownership** — `owner_id IS NULL` означает глобальный ресурс (доступен всем на чтение); `owner_id = users.id` означает персональный (только этому юзеру)
3. **UUID как VARCHAR(36)** — следуем существующей конвенции `tasks.id`, не вводим тип `UUID` PostgreSQL (упростит Spring Data JDBC маппинг)
4. **Soft-first naming** — username/role как VARCHAR CHECK; для Phase 7 легко мигрировать на нормализованные roles/user_roles таблицы
5. **Нет FK на SPRING_AI_CHAT_MEMORY** — это framework-владеемая таблица Spring AI, меняем её только через ALTER (добавление FK на conversations) в отдельной миграции
6. **TIMESTAMP без TZ** — следуем V1 (`TIMESTAMP NOT NULL DEFAULT now()`), не TIMESTAMPTZ
7. **JSONB для headers** — `mcp_servers.headers` как JSONB для эффективного querying
8. **Fail-fast CHECK constraints** — `transport IN ('stdio','http')`, `role IN ('ADMIN','USER')` на уровне БД

### Таблицы (7 новых + 3 существующих для reference)

|              Таблица              |                       Purpose                       | Owner |               FK deps                |
|-----------------------------------|-----------------------------------------------------|-------|--------------------------------------|
| `users`                           | Users для Phase 4.1 auth                            | —     | none                                 |
| `conversations`                   | Metadata для Spring AI chat                         | users | users.id (nullable для guest-режима) |
| `virtual_files`                   | VFS в БД (Phase 1.2)                                | users | users.id (nullable=global)           |
| `skills`                          | Агентные скиллы (Phase 1.4)                         | users | users.id (nullable=global)           |
| `mcp_servers`                     | MCP конфиги (Phase 1.5)                             | users | users.id (nullable=global)           |
| `config`                          | Key-value конфиги (замена application.private.yaml) | users | users.id (nullable=global)           |
| `tasks` *(V1)*                    | Задачи — существует                                 | —     | —                                    |
| `recurring_tasks` *(V1)*          | Cron-задачи — существует                            | —     | —                                    |
| `SPRING_AI_CHAT_MEMORY` *(V2-V3)* | Сообщения — существует                              | —     | conversations (добавим в V5)         |

### Migration sequence (V4 → V10)

```
V4__create_users.sql           — users table + idx
V5__create_conversations.sql   — conversations + ALTER SPRING_AI_CHAT_MEMORY ADD FK
V6__create_virtual_files.sql   — virtual_files + composite unique (owner_id, path)
V7__create_skills.sql          — skills + idx + migration from in-memory (empty)
V8__create_mcp_servers.sql     — mcp_servers + idx
V9__create_config.sql          — config + seed from application.private.yaml (optional)
V10__seed_default_users.sql    — admin/admin + user/user seed users для Phase 4.1
```

## Relevant Files

### Источники конвенций (read-only)

- `javaclaw-core/src/main/resources/db/migration/V1__init_tasks.sql` — текущие конвенции: VARCHAR(36), snake_case, idx_<table>_<col>
- `javaclaw-core/src/main/resources/db/migration/V2__init_chat_memory.sql` — SPRING_AI_CHAT_MEMORY (framework)
- `javaclaw-core/src/main/resources/db/migration/V3__allow_null_content_in_chat_memory.sql`
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/Task.java` — эталонный JDBC entity: @Id, @Column, @Table
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/RecurringTask.java`
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskRepository.java` — Spring Data JDBC CrudRepository pattern

### Источники текущих DTO (для маппинга)

- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillDto.java` — текущая структура Skill
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillStore.java` — in-memory store (миграционный source)
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/mcp/McpServerDto.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/mcp/McpServerStore.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/WorkspaceFileService.java` — filesystem-based источник для virtual_files
- `javaclaw-app/src/main/resources/application.yaml` — datasource конфиг, база javaclaw/javaclaw
- `javaclaw-core/src/main/java/ai/javaclaw/configuration/ConfigurationManager.java` — текущий YAML-based config

### Референсы тестов

- `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiContractComplianceTest.java` — паттерн Testcontainers + @SpringBootTest
- `javaclaw-app/src/test/resources/application-contracttest.yaml` — тест-профиль с Testcontainers PostgreSQL
- Любой существующий Repository-тест с @DataJdbcTest если есть

### New Files

- `specs/db-schema-v1.md` — основная спецификация (ER + DDL + rationale)
- `javaclaw-core/src/test/java/ai/javaclaw/db/DbSchemaV1SpecTest.java` — integration test применяющий DDL из спеки к Testcontainer
- `javaclaw-core/src/test/resources/db-schema-v1/` — DDL fragments extracted from spec (может использоваться тестом)

## Implementation Phases

### Phase 1: Foundation (spec skeleton + conventions section)

- Создать `specs/db-schema-v1.md` со структурой: Overview, Conventions, ER Diagram, Tables (7 × subsections), Migrations V4-V10, Mapping to Java, Acceptance Criteria
- В секции Conventions зафиксировать: snake_case, VARCHAR(36) + gen_random_uuid(), TIMESTAMP, idx_<table>_<col>, ON DELETE CASCADE для child records, ownership model (nullable owner_id)
- Mermaid ER diagram со всеми 10 таблицами (7 новых + 3 существующих) и FK-рёбрами

### Phase 2: Core Implementation (DDL для всех таблиц)

- Секция "Tables" — 7 подсекций, по каждой:
  - DDL `CREATE TABLE`
  - Обоснование каждой колонки (тип, nullability, default, constraint)
  - Индексы с обоснованием (по каким запросам они нужны)
  - FK constraints (ON DELETE rule + обоснование)
  - UNIQUE constraints
  - CHECK constraints (role, transport, scope)
  - Sample INSERT данные для каждой таблицы (для тестов)
- Секция "Migrations V4→V10" — план инкрементального применения
- Секция "Mapping to Java" — для каждой таблицы эталонная запись `@Table("skills") record Skill(@Id String id, String name, ...)` — готова к копированию в будущие задачи 1.2/1.4/1.5

### Phase 3: Integration & Polish (validation test + spec review)

- `DbSchemaV1SpecTest.java` — integration test через Testcontainers PostgreSQL 17:
  - Применить V1-V3 существующие миграции
  - Применить V4-V10 DDL fragments из спеки
  - Проверить что 10 таблиц созданы (`SELECT table_name FROM information_schema.tables`)
  - INSERT sample данных в каждую таблицу
  - Проверить FK cascades (delete user → conversations/virtual_files/skills удалились)
  - Проверить UNIQUE constraints (duplicate username → exception)
  - Проверить CHECK constraints (invalid role → exception)
  - Проверить индексы (`SELECT indexname FROM pg_indexes`)
- Senior DBA review: naming conventions, indexing strategy, FK rules, migration ordering, rollback-friendliness

## Team Orchestration

### Team Members

- **Builder-DB-Architect**
  - Name: `builder-db-architect`
  - Role: Пишет `specs/db-schema-v1.md` — Mermaid ER + DDL + rationale + migration plan + Java mapping. Следует конвенциям V1.
  - Agent Type: `general-purpose`
  - Resume: true
- **Test-Builder-DB**
  - Name: `test-builder-db`
  - Role: Пишет `DbSchemaV1SpecTest` — Testcontainers PostgreSQL 17, применение DDL fragments из спеки, проверка constraints и FK. Использует паттерн existing contract tests.
  - Agent Type: `general-purpose`
  - Resume: true
- **Validator-Senior-DBA**
  - Name: `validator-senior-dba`
  - Role: Senior DBA review. Верифицирует: (1) все 7 таблиц покрыты (2) конвенции V1 соблюдены (3) FK rules корректны (4) индексы обоснованы (5) migration ordering не ломает V1-V3 (6) DbSchemaV1SpecTest зелёный (7) Java mapping готов к прямому использованию. PASS/FAIL verdict.
  - Agent Type: `validator`
  - Resume: true

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

Файл: `javaclaw-core/src/test/java/ai/javaclaw/db/DbSchemaV1DdlValidationTest.java` (чистый unit, без Spring)

- `specFileExists_isReadable()` — файл specs/db-schema-v1.md существует и читается
- `ddlFragments_extractableFromMarkdown()` — парсер извлекает все DDL блоки из ```sql fenced code blocks; ожидается минимум 7 CREATE TABLE + 7 миграционных блоков V4-V10
- `eachDdlBlock_parsesViaJsqlparser()` — каждый DDL-блок валиден по JSqlParser
- `tableNames_followSnakeCaseConvention()` — все имена таблиц lowercase snake_case (кроме legacy SPRING_AI_CHAT_MEMORY)
- `columnNames_followSnakeCase()` — все колонки snake_case
- `primaryKey_presentOnEveryTable()` — каждая CREATE TABLE имеет PRIMARY KEY
- `uuidColumns_useVarchar36()` — все id колонки `VARCHAR(36)` с `DEFAULT gen_random_uuid()::varchar`
- `timestamps_useTimestampNotTimestamptz()` — создано/updated_at — TIMESTAMP (не TIMESTAMPTZ)
- `timestampColumns_haveDefaultNow()` — TIMESTAMP колонки имеют `DEFAULT now()`
- `indexNames_followConvention()` — все индексы названы `idx_<table>_<column>` или `idx_<table>_<col1>_<col2>`
- `checkConstraints_onRoleAndTransportAndScope()` — CHECK constraints для role, transport, scope имеют enum-like проверки
- `foreignKeys_declareOnDeleteRule()` — каждый FK имеет явный ON DELETE CASCADE или RESTRICT
- `mermaidDiagram_presentAndParseable()` — спека содержит ```mermaid ER block валидного синтаксиса
- `migrationPlan_hasAllVersionsV4toV10()` — раздел migrations содержит ровно V4-V10 записи в порядке
- `javaMapping_presentForEachTable()` — спека содержит Java mapping для каждой из 7 новых таблиц
- `acceptanceCriteria_present()` — спека содержит секцию Acceptance Criteria

### Integration / API Tests (15%)

Файл: `javaclaw-core/src/test/java/ai/javaclaw/db/DbSchemaV1SpecTest.java` (Testcontainers PostgreSQL 17)

- `applyingAllDdl_createsAllTables()` — применить V1-V3 + V4-V10 DDL из спеки, verify `information_schema.tables` содержит 10 таблиц
- `usersTable_acceptsValidInserts()` — INSERT admin/user, verify rows fetched
- `usersTable_rejectsDuplicateUsername()` — duplicate INSERT → SQLException
- `usersTable_rejectsInvalidRole()` — role='SUPERADMIN' → CHECK constraint violation
- `conversationsTable_fkToUsers_cascadesOnDelete()` — DELETE user → связанные conversations удалены
- `virtualFilesTable_allowsNullOwnerForGlobal()` — INSERT с owner_id=NULL → успех
- `virtualFilesTable_uniquePathPerOwner()` — duplicate (owner_id, path) для одного user → violation; duplicate (NULL, path) global → ТОЖЕ violation (partial unique index `uq_virtual_files_global_path WHERE owner_id IS NULL`)
- `skillsTable_globalAndPersonalCoexist()` — вставить global skill (owner_id=NULL) и personal (owner_id=user1) — оба существуют
- `mcpServersTable_rejectsInvalidTransport()` — transport='websocket' → violation
- `mcpServersTable_acceptsJsonbHeaders()` — INSERT headers={"Auth": "Bearer x"} → select возвращает jsonb object
- `configTable_compositeKey()` — PK (config_key, scope, owner_id) позволяет одинаковый key в разных scope
- `allIndexesCreated()` — `pg_indexes` содержит ожидаемые индексы
- `flywayHistory_linear()` — после применения V1-V10 `flyway_schema_history` содержит 10 успешных записей без конфликтов

### UI E2E Tests (5%)

DDL не имеет UI. Вместо browser E2E делаем **DB end-to-end**:

Файл: `javaclaw-core/src/test/java/ai/javaclaw/db/DbSchemaV1FullMigrationE2ETest.java`

- `fullMigration_V1ToV10_onPristinePostgres()` — запустить чистый Testcontainer PostgreSQL 17, применить ВСЕ V1-V10 как реальный Flyway (не DDL fragments), verify no errors + final schema state совпадает с ожидаемым
- `seededUsers_matchApplicationYamlDefaults()` — после V10 в users таблице есть admin и user строки (для Phase 4.1 Basic Auth)

## Step by Step Tasks

### 1. Написать specs/db-schema-v1.md — skeleton + conventions + Mermaid ER

- **Task ID**: write-db-spec-foundation
- **Depends On**: none
- **Assigned To**: builder-db-architect
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot entity record maven
- **Parallel**: false
- **Tests**: specFileExists, mermaidDiagram_presentAndParseable
- Прочитать `javaclaw-core/src/main/resources/db/migration/V1__init_tasks.sql`, `V2__init_chat_memory.sql` для конвенций
- Создать `specs/db-schema-v1.md` с секциями: Overview, Conventions, ER Diagram (Mermaid), Tables (заглушки 7), Migrations V4-V10 (заглушки), Java Mapping, Acceptance Criteria
- Секция Conventions: VARCHAR(36) UUIDs, snake_case, TIMESTAMP+DEFAULT now(), idx_<table>_<col>, ownership model, FK ON DELETE rules
- Mermaid ER diagram (users → conversations, users → virtual_files/skills/mcp_servers/config, conversations → SPRING_AI_CHAT_MEMORY)

### 2. Описать 7 новых таблиц в specs/db-schema-v1.md

- **Task ID**: write-db-tables
- **Depends On**: write-db-spec-foundation
- **Assigned To**: builder-db-architect
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot entity record
- **Parallel**: false
- **Tests**: eachDdlBlock_parsesViaJsqlparser, tableNames_followSnakeCaseConvention, primaryKey_presentOnEveryTable, uuidColumns_useVarchar36, checkConstraints_onRoleAndTransportAndScope, foreignKeys_declareOnDeleteRule
- Полный DDL для: `users`, `conversations`, `virtual_files`, `skills`, `mcp_servers`, `config`
- Для каждой: DDL + обоснование колонок + индексы + FK + UNIQUE + CHECK + sample INSERT
- `users`: id VARCHAR(36), username VARCHAR(64) UNIQUE, password_hash VARCHAR(255) nullable, role VARCHAR(32) CHECK IN ('ADMIN','USER'), active BOOLEAN, created_at, updated_at
- `conversations`: id VARCHAR(256) PK (матчит SPRING_AI_CHAT_MEMORY.conversation_id), title VARCHAR(255), user_id VARCHAR(36) FK nullable REFERENCES users(id) ON DELETE SET NULL, created_at, updated_at
- `virtual_files`: id VARCHAR(36) PK, owner_id VARCHAR(36) FK nullable REFERENCES users(id) ON DELETE CASCADE, path VARCHAR(512), content TEXT, content_type VARCHAR(100), size_bytes BIGINT, created_at, updated_at. **Partial UNIQUE indexes**: `CREATE UNIQUE INDEX uq_virtual_files_user_path ON virtual_files (owner_id, path) WHERE owner_id IS NOT NULL;` + `CREATE UNIQUE INDEX uq_virtual_files_global_path ON virtual_files (path) WHERE owner_id IS NULL;`
- `skills`: id VARCHAR(36) PK, owner_id VARCHAR(36) FK nullable ON DELETE CASCADE, name VARCHAR(120), description VARCHAR(500), content TEXT, enabled BOOLEAN, created_at, updated_at. **Partial UNIQUE indexes**: `uq_skills_user_name (owner_id, name) WHERE owner_id IS NOT NULL` + `uq_skills_global_name (name) WHERE owner_id IS NULL`
- `mcp_servers`: id VARCHAR(36) PK, owner_id VARCHAR(36) FK nullable ON DELETE CASCADE, name VARCHAR(120), transport VARCHAR(10) CHECK IN ('stdio','http'), command TEXT, url TEXT, headers JSONB, enabled BOOLEAN, created_at, updated_at. **Partial UNIQUE indexes**: `uq_mcp_servers_user_name (owner_id, name) WHERE owner_id IS NOT NULL` + `uq_mcp_servers_global_name (name) WHERE owner_id IS NULL`
- `config`: id VARCHAR(36) PK (gen_random_uuid), config_key VARCHAR(255), scope VARCHAR(16) CHECK IN ('global','user'), owner_id VARCHAR(36) nullable FK REFERENCES users(id) ON DELETE CASCADE, config_value TEXT, created_at, updated_at. **UNIQUE enforcement через два partial index'а**: `CREATE UNIQUE INDEX uq_config_user_scoped ON config (config_key, scope, owner_id) WHERE owner_id IS NOT NULL;` + `CREATE UNIQUE INDEX uq_config_global_scoped ON config (config_key, scope) WHERE owner_id IS NULL;` — PostgreSQL не поддерживает expressions в PRIMARY KEY, поэтому surrogate `id` + partial unique indexes.

### 3. Описать migration plan V4-V10 + Java mapping

- **Task ID**: write-db-migrations-mapping
- **Depends On**: write-db-tables
- **Assigned To**: builder-db-architect
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot entity record record maven
- **Parallel**: false
- **Tests**: migrationPlan_hasAllVersionsV4toV10, javaMapping_presentForEachTable
- Секция Migrations V4→V10: содержимое каждого файла миграции + обоснование ordering
- V4 users (самая первая т.к. все ссылаются)
- V5 conversations + ALTER SPRING_AI_CHAT_MEMORY ADD CONSTRAINT fk_conversation_id FK ON DELETE CASCADE
- V6 virtual_files (зависит от users)
- V7 skills (зависит от users)
- V8 mcp_servers (зависит от users)
- V9 config (зависит от users)
- V10 seed default admin+user (id UUID, bcrypt null пока, role ADMIN/USER, active true) — подготовка для Phase 4.1
- Секция "Mapping to Java": для каждой из 7 таблиц готовый `@Table("...") record Entity(@Id String id, ...)` с точными именами `@Column("snake_case")`, типами (Instant, String, Map<String,String> для headers через custom converter)

### 4. Написать DbSchemaV1DdlValidationTest (unit)

- **Task ID**: write-unit-ddl-validation
- **Depends On**: write-db-migrations-mapping
- **Assigned To**: test-builder-db
- **Agent Type**: general-purpose
- **Stack**: Java assertj test structure test naming
- **Parallel**: true (параллельно с задачей 5)
- **Tests**: это сами тесты
- Добавить в pom.xml (scope=test) зависимость `com.github.jsqlparser:jsqlparser:5.x` если отсутствует
- `DbSchemaV1DdlValidationTest` (pure JUnit 5 + AssertJ, без Spring)
- Парсер markdown извлекает DDL блоки (```sql fences) из specs/db-schema-v1.md
- 16 @Test методов из Testing Strategy → Unit Tests
- Запустить `mvn -pl javaclaw-core test -Dtest=DbSchemaV1DdlValidationTest`

### 5. Написать DbSchemaV1SpecTest + DbSchemaV1FullMigrationE2ETest (integration)

- **Task ID**: write-integration-spec-test
- **Depends On**: write-db-migrations-mapping
- **Assigned To**: test-builder-db
- **Agent Type**: general-purpose
- **Stack**: Java testcontainers integration test jdbc database test repository test assertj allure
- **Parallel**: true (параллельно с задачей 4)
- **Tests**: это сами тесты
- `DbSchemaV1SpecTest` — Testcontainers PostgreSQL 17, извлекает DDL из спеки, применяет, запускает 13 @Test методов из Testing Strategy → Integration Tests
- `DbSchemaV1FullMigrationE2ETest` — применяет РЕАЛЬНЫЕ Flyway V1-V3 из classpath + ВРЕМЕННЫЕ V4-V10 (из спеки через temp directory), проверяет schema_history
- Использовать `org.testcontainers:postgresql:1.20.x` (уже в parent pom) + `postgres:17-alpine` image
- Запустить `mvn -pl javaclaw-core test -Dtest='DbSchemaV1*'`

### 6. Validation + senior DBA review + roadmap update

- **Task ID**: validate-all
- **Depends On**: write-db-migrations-mapping, write-unit-ddl-validation, write-integration-spec-test
- **Assigned To**: validator-senior-dba
- **Agent Type**: validator
- **Stack**: Java testcontainers integration test jdbc database test
- **Parallel**: false
- Верифицировать специфику:
  1. `specs/db-schema-v1.md` существует, содержит все 6 секций
  2. Mermaid ER block валидный (пропустить через `npx @mermaid-js/mermaid-cli` если доступно, иначе ручной grep)
  3. 7 новых таблиц описаны в отдельных subsections
  4. DDL следует V1 конвенциям (VARCHAR(36), TIMESTAMP, snake_case, idx_*)
  5. Migration plan V4-V10 покрыт и order корректен
  6. Java mapping готов для Spring Data JDBC
  7. `mvn -pl javaclaw-core test -Dtest='DbSchemaV1*'` всё зелёное
  8. DbSchemaV1FullMigrationE2ETest подтверждает что все V1-V10 применяются без конфликтов
- Senior DBA review чеклист: naming, indexing strategy (какие запросы будут, какие индексы нужны), FK cascade rules (корректны ли для ownership), nullable strategy, какие CHECK не покрыты, есть ли редкие кейсы (orphan config rows при DELETE user)
- Если PASS: отметить в `specs/roadmap.md` пункт 0.1 "Спека: DB schema v1" как `[x]`
- Вернуть PASS/FAIL verdict

## Acceptance Criteria

- [ ] Файл `specs/db-schema-v1.md` существует и содержит секции: Overview, Conventions, ER Diagram, Tables (×7), Migrations (V4-V10), Java Mapping, Acceptance Criteria
- [ ] Mermaid ER диаграмма отражает все 10 таблиц и FK-связи
- [ ] 7 новых таблиц описаны полным DDL: users, conversations, virtual_files, skills, mcp_servers, config, (+ ALTER SPRING_AI_CHAT_MEMORY)
- [ ] Каждая таблица: DDL + rationale колонок + индексы + FK rules + UNIQUE + CHECK + sample data
- [ ] Migration plan: V4→V10 с порядком и обоснованием
- [ ] Java mapping: для каждой таблицы готовый `record` с `@Id`/`@Column`/`@Table`
- [ ] DbSchemaV1DdlValidationTest: 16 unit тестов, все зелёные
- [ ] DbSchemaV1SpecTest: 13 integration тестов через Testcontainers, все зелёные
- [ ] DbSchemaV1FullMigrationE2ETest: полный Flyway V1-V10 run зелёный, schema_history линейный
- [ ] Senior DBA PASS verdict от validator
- [ ] `specs/roadmap.md` пункт 0.1 "Спека: DB schema v1" → `[x]`

## Validation Commands

- `grep -c '^## ' specs/db-schema-v1.md` — минимум 7 разделов
- `grep -c '```sql' specs/db-schema-v1.md` — минимум 14 DDL блоков (7 CREATE TABLE + 7 миграций + extras)
- `grep '```mermaid' specs/db-schema-v1.md` — Mermaid diagram present
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-core test -Dtest='DbSchemaV1*Test' 2>&1 | tail -30`
- `grep -E '^V[0-9]+__' specs/db-schema-v1.md | sort -u` — V4 V5 V6 V7 V8 V9 V10

## Notes

- **PostgreSQL 17** используется (docker-compose.dev.yml, tests). Функции `gen_random_uuid()` доступны без расширения (встроено с PG 13+).
- **Spring Data JDBC** (не JPA) — используется только `@Id`, `@Table`, `@Column`, `@MappedCollection`. Маппинг Map<String,String> для headers JSONB потребует custom converter (документировать в Java Mapping секции).
- **Миграции не создаются в этой задаче** — только описываются в спеке. Реализация отдельными файлами `V4__*.sql`-`V10__*.sql` — задача atomic piece 1.1 (следующий цикл).
- **Seed default users в V10** — password_hash=NULL пока, Phase 4.1 заполнит через BCryptPasswordEncoder. active=true, role=ADMIN для admin, role=USER для user. Соответствует будущей Basic Auth конфигурации.
- **Ownership nullable strategy**: в PostgreSQL `NULL != NULL` в UNIQUE constraints, поэтому дублирующиеся `(NULL, 'AGENT.md')` пары в virtual_files БУДУТ разрешены — нужен либо partial unique index (`CREATE UNIQUE INDEX ... WHERE owner_id IS NULL`), либо COALESCE в уникальности. Документировать выбор в спеке.
- **SPRING_AI_CHAT_MEMORY remains unchanged** структурой — только добавляется FK. Spring AI framework-owned таблица, не трогаем content/type/timestamp колонки.
- **Seed users password_hash=NULL semantics**: в V10 seed admin/user с `password_hash=NULL` означает "вход запрещён" до настройки. Phase 4.1 BCryptPasswordEncoder заполнит хэш через ALTER / UPDATE. **Критично**: AuthenticationProvider в Phase 4.1 ДОЛЖЕН явно отклонять login если `password_hash IS NULL` (не трактовать пустой пароль как валидный). Зафиксировать в AuthService contract.
- **Ownership UNIQUE policy (финальное решение)**: два глобальных ресурса (owner_id=NULL) с одинаковым именем/path ЗАПРЕЩЕНЫ — решаем через partial unique indexes `WHERE owner_id IS NULL`. Документировать в Conventions секции spec.

