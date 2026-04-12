# Plan: Multi-Dialect Persistence Layer + SQLite Standalone Profile

## Task Description

Ввести абстракцию слоя хранения поверх Spring Data JDBC так, чтобы JavaClaw мог запускаться в трёх режимах без изменений бизнес-кода:

1. **postgres** (текущий default, production, horizontal scaling) — работает как сейчас.
2. **sqlite** (новый, standalone) — запуск на пользовательском ПК / VM / VPS / VDS с одним файлом БД, выбор задаётся в `application.yaml` админом (`javaclaw.persistence.dialect: sqlite`). Никакого онбординга для конечного пользователя.
3. **debug** (новый, ручное тестирование и CDP) — отдельный Spring profile, полноценный старт приложения без каких-либо дополнительных изменений кроме активации профиля. По умолчанию использует sqlite с временным файлом, чтобы локальный запуск не требовал Postgres.

Дизайн должен оставить место под будущие диалекты (Oracle, MySQL): добавление нового диалекта должно сводиться к добавлению (а) бина `Dialect`, (б) папки `db/migration/<vendor>`, (в) опционально — dialect-specific реализаций raw-SQL репозиториев, без правки бизнес-логики.

## Objective

После завершения плана:

- Один и тот же бизнес-код собирает и запускает `javaclaw-app` как на Postgres (production), так и на SQLite-файле (standalone), переключение идёт через `javaclaw.persistence.dialect` + активный Spring profile.
- Все 41 Flyway-миграция воспроизведены в диалект-специфичных папках; Flyway подхватывает нужную через `{vendor}` placeholder.
- Все raw-SQL запросы в репозиториях и сервисах проверены и классифицированы: портируемые остаются без изменений, непортируемые изолированы за dialect-specific интерфейсами.
- JSONB-колонки заменены на портируемое TEXT-хранилище с Jackson-конвертерами (см. раздел "JSON storage").
- JobRunr работает на том же DataSource в sqlite-режиме (JobRunr SQLite storage провайдер).
- Существующие тесты (709+152) на Postgres продолжают проходить. Добавлен новый набор тестов, которые запускают миграции и ключевые сценарии на in-process SQLite.
- Появился `debug` Spring profile, позволяющий полноценный manual / CDP запуск одной командой без Postgres.

## Problem Statement

Текущее состояние:

- `javaclaw-core` содержит **39 Flyway-миграций** (V1-V14, V17-V41; V15/V16 отсутствуют — номера пропущены при ранней разработке), написанных на Postgres-диалекте (JSONB, UUID, TIMESTAMPTZ, `gen_random_uuid()`, `ON CONFLICT`, `BIGSERIAL`).
- Приложение жёстко привязано к Postgres: `application.yaml` имеет только `jdbc:postgresql://...`, зависимости `postgresql` и `flyway-database-postgresql`, `spring-boot-starter-data-jdbc`, **Spring Boot 4.0.5**.
- `javaclaw-core/src/main/java/ai/javaclaw/config/JdbcConfig.java` регистрирует `@Primary JdbcCustomConversions` с двумя Postgres-специфичными конвертерами (`MapToJsonbConverter`, `JsonbToMapConverter`), создающими `org.postgresql.util.PGobject`. На SQLite DataSource класс `PGobject` вообще отсутствует в classpath при исключении postgresql-driver, а при его наличии — сломает round-trip.
- ~10 сущностей (`Skill`, `AppUser`, `McpServer`, `VirtualFile`, `AppConfig`, `ConversationSummary`, `ToolExample`, `Memory`, `SkillRoleAllowlist`, `RoleModelAllowlist`, `RoleAgentConfig` — точный список формирует аудит) передают `null` в `@Id` и полагаются на DB-default `gen_random_uuid()`. В коде нет `BeforeConvertCallback` для этих сущностей. Переход на портируемую генерацию UUID обязан быть парным: убрать DB-default **и** добавить Java-side callback, иначе ломается INSERT на обеих БД.
- Spring AI chat memory (`spring.ai.chat.memory.repository.jdbc`, `initialize-schema: never`) — используется `JdbcChatMemoryRepository`. Из коробки Spring AI поддерживает **PostgreSQL, MySQL/MariaDB, SQL Server, HSQLDB, Oracle**. **SQLite в списке нет** — расширение через реализацию `JdbcChatMemoryRepositoryDialect` (SELECT/INSERT/DELETE SQL) и кастомный `schema-sqlite.sql`.
- Репозитории работают через Spring Data JDBC derived queries + часть raw SQL в `JdbcTemplate`/`NamedParameterJdbcTemplate`. Уровень vendor-зависимости raw-SQL слоя не задокументирован.
- JobRunr использует тот же DataSource; Spring Boot starter JobRunr через `SqlStorageProviderFactory.using(dataSource)` автодетектит тип БД (Postgres, MySQL, Oracle, SQL Server, DB2, **SQLite**, Mongo). Отдельный StorageProvider-бин не нужен — достаточно правильного DataSource.
- Развернуть JavaClaw standalone (без Docker Postgres) невозможно: единственный путь — поднимать PG локально.
- **Прод ещё не развёрнут** — проект на стадии develop, релиза не было. Это позволяет делать `git mv` миграций без оглядки на `flyway_schema_history` существующих баз.

Последствия: нельзя быстро отдать инсталляцию техлиду / заказчику для локальной оценки; usability на VPS/VDS упирается в Postgres-обвязку; невозможно добавить Oracle/MySQL без широкого рефакторинга, который придётся делать ad hoc.

## Solution Approach

Архитектурный подход — ввести тонкий "persistence SPI" поверх Spring Data JDBC, обозначив три слоя ответственности:

### 1. Dialect SPI (конфигурация на уровне Spring Data JDBC)

Подтверждено через Spring Data Relational docs (context7):

- Правильная точка расширения — `AbstractJdbcConfiguration` + override `userConverters()` (не устаревший `jdbcCustomConversions()`). Spring сам ассемблит конвертеры из dialect + user-registered, избегая двойной регистрации.
- Диалект авто-определяется через `AbstractJdbcConfiguration.jdbcDialect(NamedParameterJdbcOperations)`. Список встроенных диалектов Spring Data JDBC: H2, HSQLDB, MySQL, MariaDB, Oracle, PostgreSQL, SQL Server, DB2. **SQLite в список НЕ входит** — при подключении sqlite-jdbc без override приложение упадёт на старте с "no suitable dialect".
- → нужна **собственная реализация `JdbcDialect` для SQLite** (extends `AnsiDialect`, переопределяет `LimitClause` на SQLite-форму `LIMIT n OFFSET m`, при необходимости `IdentifierProcessing`, `ArrayColumns.Unsupported`, пустой `IdGeneration`). Размещается в `persistence/dialect/sqlite/SqliteJdbcDialect.java`.
- Существующий `JdbcConfig` **трансформируется**, а не дополняется: его текущий `@Primary JdbcCustomConversions` бин снимается, логика переносится в два новых `@Configuration` класса, взаимно исключающих по `@ConditionalOnProperty`.
- Подход: один `AbstractJdbcConfiguration`-наследник на диалект, каждый override'ит:
  - `jdbcDialect(ops)` — возвращает нужный `Dialect` (Postgres: автодетект из super; SQLite: `new SqliteJdbcDialect()`).
  - `userConverters()` — возвращает список общих портируемых конвертеров (JSON, Instant) + dialect-specific (UUID для SQLite, PGobject JSONB-конвертеры для Postgres).
- Свойство `javaclaw.persistence.dialect` (default `postgresql`) → бин `PersistenceDialectProperties`.
- Будущие диалекты (Oracle, MySQL): добавить значение в enum + наследник `AbstractJdbcConfiguration` + при необходимости свой `JdbcDialect` (Spring Data JDBC уже содержит Oracle/MySQL dialects).

### 2. Schema SPI (миграции через Flyway vendor-folder)

- `spring.flyway.locations: classpath:db/migration/{vendor}` — Flyway подставит `postgresql` или `sqlite` автоматически по типу JDBC URL.
- Существующие **39 миграций** (V1-V14, V17-V41) переезжают в `db/migration/postgresql/V*.sql` через `git mv`, без изменений контента. Пропуски V15/V16 сохраняются как есть. Прод ещё не развёрнут, поэтому ломать `flyway_schema_history` не на чем — repair/baseline не нужны.
- **SQLite-ограничения Flyway** (подтверждено context7):
  - Нет `SELECT ... FOR UPDATE` → concurrent migration недоступен (для standalone single-node не проблема).
  - Нет schemas.
  - **Nested transactions запрещены внутри миграции** — нельзя использовать `BEGIN`/`COMMIT` блоки; Flyway оборачивает миграцию в одну транзакцию автоматически.
- В `db/migration/sqlite/` создаётся параллельный набор V1-V14, V17-V41 (**39 файлов**, те же номера версий) с эквивалентным смыслом в SQLite-диалекте:
  - `JSONB` → `TEXT` (+ см. раздел "JSON storage")
  - `UUID` → `TEXT` (хранение как `toString()`, без дефолтных генераторов на уровне БД)
  - `TIMESTAMPTZ` → `TEXT` (ISO-8601 UTC) или `INTEGER` (epoch ms), выбор фиксируется один раз для всего проекта — TEXT выбран как более отлаживаемый
  - `BIGSERIAL` / `SERIAL` → `INTEGER PRIMARY KEY AUTOINCREMENT` (для одиночных PK) либо `INTEGER NOT NULL` с явной генерацией в Java для суррогатных ключей
  - `gen_random_uuid()` — не используется на уровне БД, все UUID генерируются в Java (`UUID.randomUUID()`) — заодно устраняется vendor-зависимость и в Postgres-миграциях (отдельный аудит-таск)
  - `ON CONFLICT ... DO UPDATE` → `INSERT OR REPLACE` / `INSERT ... ON CONFLICT ... DO UPDATE` (SQLite 3.24+ поддерживает upsert с теми же ключевыми словами, но отличается в деталях)
  - Индексы без `CONCURRENTLY`, без partial index predicate if needed (SQLite поддерживает partial indexes — проверить in-place)
- Rule of thumb: все новые миграции пишутся в обе папки в одном PR, иначе CI не пройдёт (validation-таск это проверит).

### 3. Query SPI (изоляция непортируемых raw-SQL)

- Аудит всех `JdbcTemplate`/`NamedParameterJdbcTemplate`/`@Query` в `javaclaw-core` и других модулях. Каждая точка классифицируется:
  - **Portable** — обычные derived queries Spring Data JDBC или ANSI-SQL: остаётся как есть.
  - **Dialect-sensitive** — используется `jsonb_extract`, `array_agg`, `ON CONFLICT`, `RETURNING`, `FOR UPDATE SKIP LOCKED` и т. п.: выделяется в интерфейс `XxxQueryRepository` с дефолтной Postgres-реализацией и SQLite-реализацией под `@ConditionalOnProperty`. Интерфейс лежит рядом с основным репозиторием, реализации — в пакете `persistence/<vendor>`.
- Пример структуры:

  ```
  core/persistence/
    api/       ConversationRepository.java          (Spring Data JDBC interface)
    api/       ConversationSearchRepository.java    (custom, dialect-sensitive)
    postgres/  ConversationSearchRepositoryPgImpl.java
    sqlite/    ConversationSearchRepositorySqliteImpl.java
    dialect/   PersistenceDialect.java
    dialect/   PostgresJdbcConfiguration.java
    dialect/   SqliteJdbcConfiguration.java
  ```
- Все существующие raw-SQL под Postgres-only: если запрос можно переписать как derived query или под ANSI-SQL — делаем это **в рамках этого плана** (уменьшает vendor surface). Если нельзя — оформляем как Dialect-sensitive интерфейс с обеими реализациями.

### JSON storage — обсуждение и решение

Пользователь поднял вопрос: "зачем JSONB когда есть BLOB?". Проведённый в рамках таска `audit-jsonb` аудит должен ответить строго, но текущая гипотеза и плановое решение:

- В 20+ миграциях колонки объявлены как `JSONB`. Их содержимое (Spring AI chat memory, audit logs, role_agent_config, conversation_summaries, tool_examples, memories) в коде **в 99% случаев читается/пишется целиком** через Jackson — бизнес-логика не делает `col @> '{...}'` или `col->>'field'` в SQL.
- Плюс Postgres JSONB над TEXT/BYTEA — только два: (а) валидация формата при вставке, (б) индексируемые GIN-запросы по вложенным полям.
- Если аудит подтверждает отсутствие SQL-запросов по вложенным полям — **всё заменяется на `TEXT` хранение с Jackson-конвертером** (`ObjectNode ↔ String`, `Map<String,Object> ↔ String`). Это портируется вообще между любыми СУБД без изменений.
- Если аудит найдёт точки, где SQL действительно заходит внутрь JSON — они классифицируются как Dialect-sensitive и получают две реализации: Postgres (JSONB operators) + SQLite (`json_extract` из json1, который включён в sqlite-jdbc). Такие места будут явно перечислены в отчёте аудита.
- BLOB (binary JSON) не используем — теряется читаемость при отладке в `sqlite3 CLI` / `psql`, а выигрыш только в компактности.
- Для обратной совместимости Postgres-данных: в рамках плана **Postgres-миграции остаются на JSONB** (никакой миграции уже существующих prod-баз), но Java-код ходит через общий конвертер `TEXT ↔ JSON`, который на Postgres работает через JDBC `setObject(..., Types.OTHER)` + явный cast в SQL. Таким образом одна и та же сущность сериализуется одинаково в обе БД.

### 4. Spring AI Chat Memory (отдельная точка расширения)

Подтверждено через Spring AI docs (context7): `JdbcChatMemoryRepository` поддерживает PostgreSQL, MySQL/MariaDB, SQL Server, HSQLDB, Oracle. **SQLite отсутствует.** Точка расширения — реализация интерфейса `JdbcChatMemoryRepositoryDialect` (три метода: SELECT, INSERT, DELETE messages).

Решение:

- Реализовать `SqliteJdbcChatMemoryRepositoryDialect` с SQL, совместимым со схемой `SPRING_AI_CHAT_MEMORY` в SQLite-форме.
- Зарегистрировать как `@Bean` в `SqliteJdbcConfiguration` — Spring AI auto-config подхватит кастомный диалект, если он найден в контексте (следуя паттерну `JdbcChatMemoryRepositoryDialect.from(DataSource)` расширяется через implementer).
- Свойство `spring.ai.chat.memory.repository.jdbc.initialize-schema: never` оставляем — схему создаёт Flyway.
- Миграция V2 (`init_chat_memory`) в `db/migration/sqlite/V2__init_chat_memory.sql` должна создавать таблицу `SPRING_AI_CHAT_MEMORY` с колонками, совместимыми с тем SQL, который возвращает наш `SqliteJdbcChatMemoryRepositoryDialect`. Фиксируем DDL в design-таске `design-spi` после чтения исходника `schema-postgresql.sql` из Spring AI.

### Summary of architectural outcome

- Добавление Oracle / MySQL в будущем = создать `OracleJdbcConfiguration` (Spring Data JDBC уже содержит Oracle/MySQL `JdbcDialect` — custom реализация не нужна), папку `db/migration/oracle`, при необходимости реализовать dialect-sensitive интерфейсы в `persistence/oracle/`, и Spring AI из коробки поддерживает обе БД — свой `JdbcChatMemoryRepositoryDialect` не потребуется. Ни бизнес-код, ни доменная модель не меняются.
- Standalone-запуск: админ пишет в `application.yaml`:

  ```yaml
  javaclaw:
    persistence:
      dialect: sqlite
  spring:
    datasource:
      url: jdbc:sqlite:./data/javaclaw.db
      driver-class-name: org.sqlite.JDBC
  ```

  — и всё. Flyway сам накатывает sqlite-миграции, Spring Data JDBC использует `SqliteJdbcDialect`, Spring AI — `SqliteJdbcChatMemoryRepositoryDialect`, JobRunr через `SqlStorageProviderFactory.using(dataSource)` автодетектит SQLite и использует `SqLiteStorageProvider` (всё это — в main jobrunr jar, отдельный артефакт не нужен).

## Relevant Files

Существующие файлы, которые будут прочитаны и/или модифицированы:

- `pom.xml` — корневой POM, Spring Boot **4.0.5**, Flyway, JobRunr. Нужен для сверки координат новых зависимостей.
- `javaclaw-app/pom.xml` — сюда добавляется только `org.xerial:sqlite-jdbc` (последняя стабильная, совместимая с Java 21+ и virtual threads). Для JobRunr SQLite **отдельный артефакт не нужен** — `SqLiteStorageProvider` бандлится в main `jobrunr` jar, `SqlStorageProviderFactory.using(dataSource)` автодетектит тип. `flyway-database-sqlite` также **не нужен** — SQLite поддержка встроена в `flyway-core`. Существующие `postgresql` / `flyway-database-postgresql` остаются.
- `javaclaw-core/pom.xml` — test-scope `sqlite-jdbc` для интеграционных тестов.
- `javaclaw-core/src/main/java/ai/javaclaw/config/JdbcConfig.java` — существующий класс, регистрирующий `@Primary JdbcCustomConversions` с `MapToJsonbConverter` / `JsonbToMapConverter` (оба создают PGobject). **Будет трансформирован**: аннотации `@Configuration` и `@Bean` сняты, логика перенесена в новый `PostgresJdbcConfiguration`. Сам файл либо удаляется, либо превращается в документирующий `package-info` — решается в `design-spi`.
- `javaclaw-core/src/main/java/ai/javaclaw/mcp/converter/MapToJsonbConverter.java`, `JsonbToMapConverter.java` — существующие Postgres-specific конвертеры. **Перемещаются** в пакет `ai/javaclaw/persistence/converter/postgres/` и регистрируются только из `PostgresJdbcConfiguration`.
- `javaclaw-app/src/main/resources/application.yaml` — из него `spring.datasource.*` и `jobrunr.*` переносятся в профильные оверлеи; добавляется `javaclaw.persistence.*` блок и `spring.flyway.locations: classpath:db/migration/{vendor}`.
- `javaclaw-app/src/main/resources/application.private.yaml` — существующий private-оверлей, его семантика не меняется (остаётся optional import).
- `javaclaw-core/src/main/resources/db/migration/V1__init_tasks.sql` … `V41__relax_user_session_user_id.sql` — **39** миграций (V1-V14, V17-V41; V15/V16 отсутствуют) существуют в этой папке и будут физически перемещены в `db/migration/postgresql/` через `git mv`, без изменения контента.
- `javaclaw-core/src/test/resources/` — существующая папка тест-ресурсов, сюда добавится sqlite test yaml.
- **Hard-coded Flyway locations в существующих test yamls** — `javaclaw-core/src/test/resources/application-test.yaml`, `javaclaw-e2e/src/test/resources/application-it.yaml`, `application-e2e.yaml`, `application-live.yaml`. Все четыре сейчас содержат `spring.flyway.locations: classpath:db/migration` (плоский путь). После `schema-git-mv` эти оверрайды нужно **обязательно** обновить на `classpath:db/migration/{vendor}`, иначе весь 709+152 test suite упадёт с "no migrations found". Это явно закреплено в таске `schema-git-mv`.
- `javaclaw-core/src/main/java/` — тут находятся все текущие `*Repository.java` и места с `JdbcTemplate` / `NamedParameterJdbcTemplate`, которые таск `audit-raw-sql` перечислит точно. На этапе планирования конкретные файлы не известны — они появляются как выход аудита.

### New Files

Все следующие файлы создаются в рамках плана:

**Persistence SPI (javaclaw-core):**
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/dialect/PersistenceDialect.java` — enum `{ POSTGRESQL, SQLITE }`.
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/dialect/PersistenceDialectProperties.java` — `@ConfigurationProperties("javaclaw.persistence")`.
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/dialect/PostgresJdbcConfiguration.java` — `@Configuration @ConditionalOnProperty(... havingValue="postgresql", matchIfMissing=true)` extends `AbstractJdbcConfiguration`; переопределяет `userConverters()` для регистрации PGobject/JSONB конвертеров.
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/dialect/SqliteJdbcConfiguration.java` — аналог для sqlite; переопределяет `jdbcDialect(ops)` → возвращает `SqliteJdbcDialect`, и `userConverters()` → UUID/Instant/JSON (TEXT) конвертеры.
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/dialect/sqlite/SqliteJdbcDialect.java` — кастомный `JdbcDialect` для SQLite. Extends `AnsiDialect`, переопределяет `LimitClause` (SQLite: `LIMIT n OFFSET m`), `ArrayColumns.Unsupported`, `IdentifierProcessing.ANSI`, пустой `IdGeneration`. **Новая реализация, т.к. Spring Data JDBC не содержит SQLite из коробки.**
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/converter/JsonToStringConverter.java` — Jackson `Converter<ObjectNode,String>` + `Converter<Map<String,Object>,String>`. Общий для обоих диалектов.
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/converter/StringToJsonConverter.java` — обратное направление.
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/converter/sqlite/UuidToStringConverter.java` / `StringToUuidConverter.java` — только в sqlite-профиле (постгрес драйвер умеет UUID нативно).
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/converter/sqlite/InstantToStringConverter.java` / `StringToInstantConverter.java` — ISO-8601 UTC для sqlite.
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/converter/postgres/MapToJsonbConverter.java`, `JsonbToMapConverter.java` — перенесённые существующие PGobject-конвертеры.

**Spring AI SQLite dialect (javaclaw-core):**
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/chatmemory/SqliteJdbcChatMemoryRepositoryDialect.java` — реализация `JdbcChatMemoryRepositoryDialect` с SQL для SELECT/INSERT/DELETE сообщений в `SPRING_AI_CHAT_MEMORY`, адаптированным под SQLite. Регистрируется `@Bean` только в sqlite-профиле.

**ID generation callbacks (javaclaw-core):**
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/id/SkillIdGeneratorCallback.java`, `AppUserIdGeneratorCallback.java`, `McpServerIdGeneratorCallback.java`, `VirtualFileIdGeneratorCallback.java`, `AppConfigIdGeneratorCallback.java`, `ConversationSummaryIdGeneratorCallback.java`, `ToolExampleIdGeneratorCallback.java`, `MemoryIdGeneratorCallback.java`, `SkillRoleAllowlistIdGeneratorCallback.java`, `RoleModelAllowlistIdGeneratorCallback.java`, `RoleAgentConfigIdGeneratorCallback.java` — по одному `BeforeConvertCallback<T>` на каждую сущность, у которой PK был завязан на `gen_random_uuid()`. Точный список уточняется в таске `audit-id-generation`. Паттерн берётся из существующих `TaskIdGeneratorCallback`, `TaskExecutionIdGeneratorCallback`, `ApprovalRequestIdGeneratorCallback`, `RecurringTaskIdGeneratorCallback`, `DeliveryQueueIdGeneratorCallback`.

**Migrations (javaclaw-core):**
- `javaclaw-core/src/main/resources/db/migration/postgresql/V1__init_tasks.sql` … `V41__relax_user_session_user_id.sql` — **39** физически перенесённых (git mv) существующих файлов, без изменения контента. Дополнительно — правка в V4/V7/V8 и других (**таск `drop-db-uuid-defaults`**): удаление `DEFAULT gen_random_uuid()` из PK-колонок, т.к. генерация уходит в Java.
- `javaclaw-core/src/main/resources/db/migration/sqlite/V1__init_tasks.sql` … `V41__relax_user_session_user_id.sql` — **39 новых миграций** (те же номера версий, включая пропуски V15/V16), ручной порт из Postgres в SQLite-диалект. V2 (`init_chat_memory`) создаёт `SPRING_AI_CHAT_MEMORY` в форме, совместимой с `SqliteJdbcChatMemoryRepositoryDialect`.

**Config (javaclaw-app):**
- `javaclaw-app/src/main/resources/application-postgres.yaml` — прод-профиль с `jdbc:postgresql://...` и JobRunr Postgres настройками.
- `javaclaw-app/src/main/resources/application-sqlite.yaml` — `jdbc:sqlite:${SQLITE_FILE:./data/javaclaw.db}`, `javaclaw.persistence.dialect: sqlite`.
- `javaclaw-app/src/main/resources/application-debug.yaml` — `spring.profiles.include: sqlite` + `SQLITE_FILE=${java.io.tmpdir}/javaclaw-debug.db`, больше никаких изменений.

**Design artifacts (specs/):**
- `specs/audit-jsonb.md` — выход таска `audit-jsonb`.
- `specs/audit-raw-sql.md` — выход таска `audit-raw-sql`.
- `specs/persistence-spi-design.md` — выход таска `design-spi`.

**Tests:**
- `javaclaw-core/src/test/resources/application-sqlite-test.yaml` — `jdbc:sqlite::memory:`.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/converter/JsonConverterTest.java` — unit round-trip.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/converter/UuidConverterTest.java` — unit round-trip.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/converter/InstantConverterTest.java` — unit round-trip.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/dialect/PersistenceDialectPropertiesTest.java` — биндинг yaml.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/dialect/sqlite/SqliteJdbcDialectTest.java` — LimitClause / IdentifierProcessing корректность.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/dialect/SqliteJdbcConfigurationTest.java` — регистрация бинов, отсутствие конфликта с `PostgresJdbcConfiguration`.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/SqliteFlywayBootIntegrationTest.java` — прогон всех sqlite-миграций на чистом `jdbc:sqlite::memory:`.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/SqliteRepositoryIntegrationTest.java` — CRUD ключевых сущностей (`Task`, `Skill`, `AppUser`, `Conversation`, `McpServer`) на sqlite, проверка BeforeConvertCallback-генерации UUID.
- `javaclaw-core/src/test/java/ai/javaclaw/persistence/chatmemory/SqliteChatMemoryIntegrationTest.java` — сохранение/чтение сообщений через `JdbcChatMemoryRepository` + `SqliteJdbcChatMemoryRepositoryDialect`.
- `javaclaw-app/src/test/java/ai/javaclaw/app/SqliteSmokeApplicationTest.java` — @SpringBootTest profile=sqlite, health=UP.
- `javaclaw-app/src/test/java/ai/javaclaw/app/DebugProfileSmokeTest.java` — @SpringBootTest profile=debug, полный boot.
- `javaclaw-app/src/test/java/ai/javaclaw/app/JobRunrSqliteIntegrationTest.java` — recurring task через auto-configured `SqlStorageProviderFactory.using(sqliteDataSource)`.

## Implementation Phases

### Phase 1: Discovery & Design

- Аудит JSONB-колонок: grep по `JSONB` в `db/migration/**.sql`, затем для каждой — поиск SQL-запросов, заходящих внутрь JSON (`->`, `->>`, `@>`, `jsonb_path_exists`, `jsonb_extract_path`). Результат → `specs/audit-jsonb.md`.
- Аудит raw SQL: grep по `JdbcTemplate`, `NamedParameterJdbcTemplate`, `@Query`, `queryForObject`, `queryForList`, `update(` в `javaclaw-core`, `javaclaw-api`, `javaclaw-security`. Для каждого — классификация portable / dialect-sensitive. Результат → `specs/audit-raw-sql.md`.
- Аудит ID-генерации: grep по `DEFAULT gen_random_uuid()` в миграциях + поиск сущностей, где `@Id` заполняется через DB. Точный список → `specs/audit-id-generation.md`.
- Аудит Spring AI chat memory: прочитать `schema-postgresql.sql` из Spring AI, определить структуру `SPRING_AI_CHAT_MEMORY` и минимальный SQL-набор, нужный для `SqliteJdbcChatMemoryRepositoryDialect`. Результат включается в `specs/persistence-spi-design.md`.
- Спроектировать `PersistenceDialect` enum, `PersistenceDialectProperties`, два `AbstractJdbcConfiguration`-наследника, `SqliteJdbcDialect` (`AnsiDialect` + overrides), `SqliteJdbcChatMemoryRepositoryDialect`. Всё — в `specs/persistence-spi-design.md`.

### Phase 2: Foundation Refactor

- Трансформировать существующий `JdbcConfig`: убрать `@Primary JdbcCustomConversions` бин, перенести PGobject-конвертеры в `persistence/converter/postgres/`.
- Добавить `BeforeConvertCallback` для всех сущностей из списка `audit-id-generation` (паттерн из существующих 5 IdGeneratorCallback в task architecture).
- Удалить `DEFAULT gen_random_uuid()` из соответствующих колонок в существующих Postgres-миграциях (новая миграция `V42__drop_db_uuid_defaults.sql` в `postgresql/`, т.к. редактировать прошлые миграции нельзя).
- Прогнать весь существующий Postgres Testcontainers suite — 709+152 зелёные, никаких регрессий.

### Phase 3: Multi-Dialect SPI Implementation

- Ввести `PersistenceDialect` enum, `PersistenceDialectProperties`.
- Реализовать `SqliteJdbcDialect` (custom `JdbcDialect`).
- Реализовать `PostgresJdbcConfiguration` и `SqliteJdbcConfiguration` (extends `AbstractJdbcConfiguration`, `@ConditionalOnProperty`, `userConverters()` override).
- Реализовать JSON/UUID/Instant конвертеры.
- Unit-тесты на диалект и конвертеры.

### Phase 4: Schema Port

- `git mv javaclaw-core/src/main/resources/db/migration/V*.sql javaclaw-core/src/main/resources/db/migration/postgresql/` — все 39 файлов.
- Добавить `spring.flyway.locations: classpath:db/migration/{vendor}` в базовый `application.yaml`.
- Создать 39 файлов `db/migration/sqlite/V*.sql` по правилам SQLite-диалекта (см. раздел Schema SPI). V2 — специально под `SqliteJdbcChatMemoryRepositoryDialect`.
- Реализовать `SqliteJdbcChatMemoryRepositoryDialect` и зарегистрировать как `@Bean` в `SqliteJdbcConfiguration`.
- Запустить `SqliteFlywayBootIntegrationTest` — зелёный.

### Phase 5: Query SPI & Repository Refactor

- По списку из `audit-raw-sql.md`: portable оставить, rewritable переписать, dialect-sensitive вынести в `XxxQueryRepository` + две реализации.
- `SqliteRepositoryIntegrationTest` и `SqliteChatMemoryIntegrationTest` зелёные.
- Существующие Postgres-тесты зелёные.

### Phase 6: Runtime Profiles & Smoke

- `application-postgres.yaml`, `application-sqlite.yaml`, `application-debug.yaml`.
- `application.yaml` базовый — без `spring.datasource.url`.
- JobRunr — **без отдельного бина**, auto-config сам выберет `SqLiteStorageProvider` по DataSource URL.
- Smoke-тесты: `SqliteSmokeApplicationTest`, `DebugProfileSmokeTest`, `JobRunrSqliteIntegrationTest`.

### Phase 7: Validation

- **Автоматическая**: полный прогон `./mvnw clean verify` — все 709+152 + новые зелёные. Ручной smoke `spring-boot:run -Dspring-boot.run.profiles=sqlite|debug`.
- **Ручная CDP-валидация**: 12 критичных пользовательских сценариев через реальный Chrome × 2 профиля (sqlite, postgresql) = 24 прогона. Сценарии перечислены в таске `manual-cdp-validation` (логин, чат, chat-memory persistence, ad-hoc task, recurring task, skill CRUD, virtual file CRUD, MCP server, user management, SSE streaming, JobRunr dashboard, logout/login cycle). Результат — `specs/manual-validation-report.md` со скриншотами и консоль-логом.
- Документация: минимальный snippet запуска в sqlite-режиме в `README.md`.

## Team Orchestration

- Ты как техлид оркестрируешь команду через `TaskCreate` / `TaskUpdate` / `Task`. Сам код не пишешь.
- Каждому билдеру — свой роль и минимальный контекст через **Stack** keywords.
- Sequential там, где есть зависимости по схеме; parallel — где независимо (например, конвертеры vs. аудит raw SQL).
- После каждой имплементационной фазы — короткая проверка статуса через `TaskList`.

### Team Members

- Builder
  - Name: architect-dialects
  - Role: Phase 1 — аудиты (JSONB, raw SQL, ID-генерация, Spring AI schema), проектирование Dialect SPI, фиксация решений в `specs/audit-*.md` и `specs/persistence-spi-design.md`.
  - Agent Type: general-purpose
  - Resume: true
- Builder
  - Name: builder-foundation
  - Role: Phase 2 — трансформация существующего `JdbcConfig`, добавление `BeforeConvertCallback` для всех non-callback-ed сущностей, снятие `DEFAULT gen_random_uuid()` в Postgres через V42. Существующий Postgres-suite остаётся зелёным.
  - Agent Type: general-purpose
  - Resume: true
- Builder
  - Name: builder-dialect-spi
  - Role: Phase 3 — `PersistenceDialect` enum, свойства, `SqliteJdbcDialect`, `PostgresJdbcConfiguration`, `SqliteJdbcConfiguration`, JSON/UUID/Instant конвертеры и их unit-тесты.
  - Agent Type: general-purpose
  - Resume: true
- Builder
  - Name: builder-schema
  - Role: Phase 4 — `git mv` Postgres-миграций в `postgresql/`, создание 39 sqlite-портов, `SqliteJdbcChatMemoryRepositoryDialect`, V2 sqlite совместимая с ним.
  - Agent Type: general-purpose
  - Resume: true
- Builder
  - Name: builder-repositories
  - Role: Phase 5 — рефакторинг dialect-sensitive raw SQL в Query SPI по результатам `audit-raw-sql.md`.
  - Agent Type: general-purpose
  - Resume: true
- Builder
  - Name: builder-runtime
  - Role: Phase 6 — профили `postgres`/`sqlite`/`debug`, базовый `application.yaml` без `spring.datasource.url`. JobRunr оставляем на auto-config — никаких StorageProvider бинов.
  - Agent Type: general-purpose
  - Resume: true
- Builder
  - Name: builder-tests
  - Role: Phase 6/7 — все тесты из Testing Strategy, соотношение 80/15/5.
  - Agent Type: general-purpose
  - Resume: true
- Validator
  - Name: validator-multi-dialect
  - Role: Phase 7 — автоматическая валидация: `mvn clean verify`, ручной `spring-boot:run` в трёх режимах, проверка acceptance criteria.
  - Agent Type: validator
  - Resume: false
- Builder
  - Name: builder-e2e-cdp
  - Role: Phase 7 — ручная валидация 12 критичных пользовательских сценариев через реальный Chrome (CDP) на обоих профилях sqlite и postgresql. Фиксирует скриншоты, консоль-ошибки, network, GIF-записи сложных сценариев. Результат — `specs/manual-validation-report.md`.
  - Agent Type: general-purpose
  - Resume: true

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

- `JsonToStringConverterTest`, `StringToJsonConverterTest` — round-trip `ObjectNode` и `Map<String,Object>` через Jackson, null-safety, edge cases (пустой object, массивы, unicode).
- `UuidToStringConverterTest`, `StringToUuidConverterTest` — round-trip, null, некорректные строки.
- `PersistenceDialectPropertiesTest` — биндинг yaml→properties, дефолт `postgresql`, валидация неизвестных значений (fail fast).
- `SqliteJdbcConfigurationTest` / `PostgresJdbcConfigurationTest` — `@ContextConfiguration` с mock DataSource, проверка что регистрируется правильный `Dialect` и `JdbcCustomConversions`.
- Для каждого Dialect-sensitive репозитория, вынесенного в Query SPI — unit-тест на каждую реализацию (postgres через Testcontainers или мок, sqlite через in-memory).

### Integration / API Tests (15%)

- `SqliteFlywayBootIntegrationTest` — поднять Flyway против `jdbc:sqlite::memory:`, прогнать все V1-V41 sqlite-миграции, assert что `flyway.info()` покрывает все версии и schema применена.
- `SqliteRepositoryIntegrationTest` — `@SpringBootTest` с profile=sqlite (in-memory), CRUD по ключевым сущностям: `Conversation`, `Task`, `User`, `Skill`, `ChatMemory`, `AuditLog`. Assert что Jackson-конвертеры JSON корректно работают через full stack.
- `PostgresFlywayBootIntegrationTest` (уже существующий / эквивалент) — без изменений, гарантия обратной совместимости.
- `JobRunrSqliteIntegrationTest` — простой recurring task через JobRunr с sqlite-StorageProvider, assert выполнения.

### UI E2E Tests (5%)

- `SqliteSmokeApplicationTest` — `@SpringBootTest(webEnvironment=RANDOM_PORT)` с profile=sqlite и SQLITE_FILE=временный файл, запрос `GET /actuator/health` → `UP`, `GET /api/chat/conversations` → 200 (с bootstrap admin-пользователем).
- `DebugProfileSmokeTest` — то же самое с profile=debug, гарантия что debug-профиль стартует без доп. настройки и что фронт (React SPA index.html) отдаётся.
- Full Selenide e2e под sqlite **не** запускаем в рамках этого плана — полный e2e остаётся на Postgres. Достаточно boot-smoke.

## Step by Step Tasks

- Перед стартом: `TaskCreate` для каждого пункта ниже, `TaskUpdate` со связками `addBlockedBy`, затем `Task` с `subagent_type: general-purpose` и правильным `Stack` полем.

### 1. Audit JSONB usage

- **Task ID**: audit-jsonb
- **Depends On**: none
- **Assigned To**: architect-dialects
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc
- **Parallel**: true
- **Tests**: нет — результат документ.
- `specs/audit-jsonb.md`: таблица `колонка | таблица | миграция | есть SQL внутрь JSON | решение (TEXT / dialect-split)` + для dialect-split вариантов — SQL для обоих диалектов.

### 2. Audit raw SQL

- **Task ID**: audit-raw-sql
- **Depends On**: none
- **Assigned To**: architect-dialects
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc
- **Parallel**: true
- **Tests**: нет.
- `specs/audit-raw-sql.md`: таблица `файл:строка | SQL-фрагмент | вердикт | целевое действие`. Признаки dialect-sensitive: `ON CONFLICT`, `RETURNING`, `::uuid`, `::jsonb`, `gen_random_uuid`, `array_agg`, `FOR UPDATE SKIP LOCKED`, `INTERVAL`, Postgres-функции.

### 3. Audit ID generation

- **Task ID**: audit-id-generation
- **Depends On**: none
- **Assigned To**: architect-dialects
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc entity
- **Parallel**: true
- **Tests**: нет.
- Найти все колонки с `DEFAULT gen_random_uuid()` в миграциях. Для каждой — определить соответствующую Spring Data JDBC сущность (@Table name).
- Определить, какие сущности **уже** имеют `BeforeConvertCallback` (в task architecture их 5). Для остальных — зафиксировать в `specs/audit-id-generation.md` список на добавление callback'а.
- Убедиться, что в бизнес-коде нет прямых вызовов `gen_random_uuid()` — если есть, пометить как правки в Phase 2.

### 4. Audit Spring AI chat memory schema

- **Task ID**: audit-spring-ai-schema
- **Depends On**: none
- **Assigned To**: architect-dialects
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc
- **Parallel**: true
- **Tests**: нет.
- Найти исходник `schema-postgresql.sql` (и остальных `schema-*.sql`) в артефактах Spring AI из classpath проекта. Зафиксировать структуру `SPRING_AI_CHAT_MEMORY`: колонки, типы, индексы.
- Сформулировать необходимый SQL для `SqliteJdbcChatMemoryRepositoryDialect` (SELECT по conversation_id с ORDER BY timestamp ASC, INSERT, DELETE по conversation_id).
- Результат включить в `specs/persistence-spi-design.md`.

### 5. Design Persistence SPI

- **Task ID**: design-spi
- **Depends On**: audit-jsonb, audit-raw-sql, audit-id-generation, audit-spring-ai-schema
- **Assigned To**: architect-dialects
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc
- **Parallel**: false
- **Tests**: нет (design-only).
- `specs/persistence-spi-design.md`: enum `PersistenceDialect`, properties, два `AbstractJdbcConfiguration`, `SqliteJdbcDialect` (override list), правила регистрации конвертеров через `userConverters()`, правила Query SPI split, пакетирование, `SqliteJdbcChatMemoryRepositoryDialect` сигнатуры SQL, финальный список `BeforeConvertCallback`-ов.
- Подтвердить точные координаты maven: `org.xerial:sqlite-jdbc` последняя стабильная (проверить совместимость со Spring Boot 4.0.5 и Java 21 virtual threads). Подтвердить, что JobRunr SQLite `SqlStorageProviderFactory.using(dataSource)` работает без отдельного артефакта на версии JobRunr из `pom.xml`.
- **Явно верифицировать Spring AI auto-config механизм регистрации диалекта**: прочитать исходник `JdbcChatMemoryAutoConfiguration` (или эквивалент в используемой версии Spring AI) и подтвердить, что кастомный `JdbcChatMemoryRepositoryDialect`-бин в контексте подхватывается автоматически. Если auto-config требует dialect как конструкторный аргумент `JdbcChatMemoryRepository`, то решение: в `SqliteJdbcConfiguration` регистрировать собственный `@Bean JdbcChatMemoryRepository` (а не только dialect), и исключить `JdbcChatMemoryAutoConfiguration` через `@EnableAutoConfiguration(exclude=...)` в sqlite-профиле. Решение фиксируется в `specs/persistence-spi-design.md` перед задачей `spring-ai-sqlite-dialect`.
- **Решить по V10 seed_default_users**: оставить Postgres V10 на `gen_random_uuid()` или добавить новую Postgres-миграцию, выравнивающую ID с фиксированными литералами. SQLite V10 безусловно — литеральные UUID (см. таск `schema-sqlite-port`).

### 6. Transform existing JdbcConfig

- **Task ID**: transform-jdbc-config
- **Depends On**: design-spi
- **Assigned To**: builder-foundation
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc
- **Parallel**: false
- **Tests**: существующий Postgres Testcontainers suite — должен остаться зелёным. Unit: проверить что `ApplicationContext` с профилем по умолчанию поднимается и содержит ровно один `JdbcCustomConversions`.
- Снять `@Configuration`/`@Bean @Primary` с существующего `JdbcConfig`, удалить класс (или превратить в package-info).
- Переместить `MapToJsonbConverter`/`JsonbToMapConverter` из `ai.javaclaw.mcp.converter` в `ai.javaclaw.persistence.converter.postgres`.
- Временно зарегистрировать PGobject-конвертеры в новом `PostgresJdbcConfiguration` (будет создан таском `dialect-spi-impl`, но на этом этапе фиксируется хотя бы временный `@Configuration` для прохождения тестов).

### 7. Add BeforeConvertCallback for entities

- **Task ID**: id-generator-callbacks
- **Depends On**: design-spi
- **Assigned To**: builder-foundation
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc entity
- **Parallel**: true
- **Tests**: Unit: по одному тесту на callback, проверка, что `@Id` заполняется UUID если null и сохраняется как есть иначе. Integration: существующий Postgres Testcontainers suite.
- Для каждой сущности из `audit-id-generation.md` — создать `BeforeConvertCallback<T>` по паттерну `TaskIdGeneratorCallback`.
- Зарегистрировать все callback-ы как `@Component` (Spring Data JDBC подхватит автоматически).

### 8. Drop DB uuid defaults (Postgres-side)

- **Task ID**: drop-db-uuid-defaults
- **Depends On**: id-generator-callbacks
- **Assigned To**: builder-foundation
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot flyway postgresql jdbc
- **Parallel**: false
- **Tests**: Integration: `./mvnw -pl javaclaw-core verify` — 709+152 Postgres тестов зелёные.
- Создать новую миграцию `db/migration/V42__drop_db_uuid_defaults.sql` (пока что в старой, не-vendor папке — до таска `schema-port`). `ALTER TABLE ... ALTER COLUMN id DROP DEFAULT` для всех затронутых таблиц.
- Убедиться, что тесты проходят — значит callback-ы из предыдущего таска работают корректно.

### 9. Add sqlite-jdbc dependency & base properties

- **Task ID**: deps-and-properties
- **Depends On**: design-spi
- **Assigned To**: builder-dialect-spi
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot maven
- **Parallel**: true
- **Tests**: Unit: `PersistenceDialectPropertiesTest` — биндинг yaml → POJO, дефолт `postgresql`, fail-fast на неизвестном значении.
- Добавить `org.xerial:sqlite-jdbc` в `javaclaw-app/pom.xml` (runtime scope).
- Добавить `org.xerial:sqlite-jdbc` test-scope в `javaclaw-core/pom.xml`.
- Создать `PersistenceDialect` enum и `PersistenceDialectProperties` в `javaclaw-core/src/main/java/ai/javaclaw/persistence/dialect/`.

### 10. Implement SqliteJdbcDialect

- **Task ID**: sqlite-jdbc-dialect
- **Depends On**: deps-and-properties
- **Assigned To**: builder-dialect-spi
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc
- **Parallel**: false
- **Tests**: Unit: `SqliteJdbcDialectTest` — `LimitClause` возвращает `LIMIT n OFFSET m` для корректных inputs, `ArrayColumns.Unsupported` срабатывает, `IdentifierProcessing` корректный.
- Создать `SqliteJdbcDialect extends AnsiDialect` с overrides согласно design-spi.

### 11. JSON / UUID / Instant converters

- **Task ID**: converters
- **Depends On**: deps-and-properties
- **Assigned To**: builder-dialect-spi
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc jackson
- **Parallel**: true
- **Tests**: Unit: `JsonConverterTest`, `UuidConverterTest`, `InstantConverterTest` — round-trip, null, edge cases, unicode, пустые коллекции.
- Реализовать `JsonToStringConverter`, `StringToJsonConverter` (Jackson), общие для обоих диалектов.
- Реализовать `UuidToStringConverter`, `StringToUuidConverter`, `InstantToStringConverter`, `StringToInstantConverter` — только для sqlite-профиля.

### 12. Implement dialect configurations

- **Task ID**: dialect-spi-impl
- **Depends On**: sqlite-jdbc-dialect, converters, transform-jdbc-config
- **Assigned To**: builder-dialect-spi
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc
- **Parallel**: false
- **Tests**: Unit: `PostgresJdbcConfigurationTest`, `SqliteJdbcConfigurationTest` — ApplicationContextRunner с разными свойствами, проверка наличия правильного `Dialect`, `JdbcCustomConversions` с ожидаемым набором конвертеров, отсутствие второго бина.
- `PostgresJdbcConfiguration extends AbstractJdbcConfiguration`, `@ConditionalOnProperty(... havingValue="postgresql", matchIfMissing=true)`. Override `userConverters()` → PGobject JSONB конвертеры + общие JSON/Instant.
- `SqliteJdbcConfiguration extends AbstractJdbcConfiguration`, `@ConditionalOnProperty(... havingValue="sqlite")`. Override `jdbcDialect(ops)` → `new SqliteJdbcDialect()`. Override `userConverters()` → UUID + Instant + JSON.
- Убедиться, что конфигурации взаимно исключающие (два `@ConditionalOnProperty`).

### 13. Git mv Postgres migrations to vendor folder

- **Task ID**: schema-git-mv
- **Depends On**: drop-db-uuid-defaults, dialect-spi-impl
- **Assigned To**: builder-schema
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot flyway jdbc
- **Parallel**: false
- **Tests**: Integration: существующий Postgres Testcontainers suite — 709+152 зелёные после перемещения.
- **Строгий порядок внутри таска** (нарушение = `NoMigrationFoundException`):
  1. Сначала `git mv javaclaw-core/src/main/resources/db/migration/V*.sql javaclaw-core/src/main/resources/db/migration/postgresql/` — все 39 файлов + V42.
  2. Затем добавить `spring.flyway.locations: classpath:db/migration/{vendor}` в `javaclaw-app/src/main/resources/application.yaml`.
  3. **Обязательно** обновить hard-coded оверрайды в четырёх test yamls:
     - `javaclaw-core/src/test/resources/application-test.yaml` → `spring.flyway.locations: classpath:db/migration/{vendor}`
     - `javaclaw-e2e/src/test/resources/application-it.yaml` → то же
     - `javaclaw-e2e/src/test/resources/application-e2e.yaml` → то же
     - `javaclaw-e2e/src/test/resources/application-live.yaml` → то же
  4. Только после этого прогнать `./mvnw -pl javaclaw-core,javaclaw-app,javaclaw-e2e verify` — Postgres тесты должны подхватить миграции из `postgresql/` папки через `{vendor}` резолвер.

### 14. Port schema to SQLite

- **Task ID**: schema-sqlite-port
- **Depends On**: schema-git-mv
- **Assigned To**: builder-schema
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot flyway jdbc database test
- **Parallel**: false
- **Tests**: Integration: `SqliteFlywayBootIntegrationTest` — Flyway против `jdbc:sqlite::memory:`, все 39+1 миграций применены.
- Создать `db/migration/sqlite/V1__init_tasks.sql` … `V42__drop_db_uuid_defaults.sql` — 39+1 файлов, ручной порт согласно правилам из раздела Schema SPI. V15/V16 пропустить как в Postgres. V2 (chat_memory) — под DDL, которую ожидает `SqliteJdbcChatMemoryRepositoryDialect`.
- **V10 (`seed_default_users`) особый случай**: Postgres-версия использует `gen_random_uuid()::varchar` прямо в `INSERT ... VALUES` (не в DEFAULT, т.е. V42 её не трогает). SQLite-порт V10 **обязан** использовать фиксированные литеральные UUID для seed-рядов: `'00000000-0000-0000-0000-000000000001'` для `admin`, `'00000000-0000-0000-0000-000000000002'` для `user` и т.д. Это seed-данные известных аккаунтов — фиксированные ID даже предпочтительнее для воспроизводимости тестов. То же самое имеет смысл сделать параллельно в Postgres-версии V10 (отдельным коммитом-миграцией, т.к. редактировать уже применённые миграции запрещено — либо новая V43 вида `UPDATE users SET id=... WHERE username='admin'`, либо оставить как есть, поскольку Postgres всё ещё умеет `gen_random_uuid()`). Решение фиксируется в design-spi.
- Убедиться что никаких `BEGIN`/`COMMIT` блоков внутри sqlite-миграций (ограничение Flyway).

### 15. Implement SqliteJdbcChatMemoryRepositoryDialect

- **Task ID**: spring-ai-sqlite-dialect
- **Depends On**: dialect-spi-impl, schema-sqlite-port
- **Assigned To**: builder-schema
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc
- **Parallel**: false
- **Tests**: Integration: `SqliteChatMemoryIntegrationTest` — сохранить 3 сообщения через `JdbcChatMemoryRepository` + sqlite DataSource, прочитать, удалить.
- Реализовать `SqliteJdbcChatMemoryRepositoryDialect implements JdbcChatMemoryRepositoryDialect` с SQL из design-spi.
- Зарегистрировать `@Bean` в `SqliteJdbcConfiguration`.

### 16. Refactor dialect-sensitive raw SQL

- **Task ID**: query-spi-refactor
- **Depends On**: dialect-spi-impl, audit-raw-sql
- **Assigned To**: builder-repositories
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc controller exception
- **Parallel**: false
- **Tests**: Unit/Integration: для каждого вынесенного Dialect-sensitive репозитория — тесты на обе реализации. Постгрес suite остаётся зелёным.
- По списку из `specs/audit-raw-sql.md`: portable — оставить, rewritable → переписать, dialect-sensitive → вынести в `XxxQueryRepository` + `persistence/postgres/XxxQueryRepositoryPgImpl` + `persistence/sqlite/XxxQueryRepositorySqliteImpl`.
- Бизнес-код зависит только от интерфейса.

### 17. Runtime profiles

- **Task ID**: runtime-profiles
- **Depends On**: dialect-spi-impl, schema-sqlite-port
- **Assigned To**: builder-runtime
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot
- **Parallel**: true
- **Tests**: Smoke: `SqliteSmokeApplicationTest`, `DebugProfileSmokeTest`.
- Вынести `spring.datasource.*` из `application.yaml` в `application-postgres.yaml`.
- Создать `application-sqlite.yaml`: `spring.datasource.url: jdbc:sqlite:${SQLITE_FILE:./data/javaclaw.db}`, `spring.datasource.driver-class-name: org.sqlite.JDBC`, `javaclaw.persistence.dialect: sqlite`, `spring.ai.chat.memory.repository.jdbc.initialize-schema: never`.
- Создать `application-debug.yaml`: `spring.profiles.include: sqlite`, `SQLITE_FILE` → `${java.io.tmpdir}/javaclaw-debug.db`, больше ничего.
- Базовый `application.yaml`: оставить всё кроме `spring.datasource.*` и добавить `spring.flyway.locations: classpath:db/migration/{vendor}` (если не добавлено в `schema-git-mv`).
- JobRunr — **не трогать**, `SqlStorageProviderFactory.using(dataSource)` авто-детектит SQLite.

### 18. Write tests

- **Task ID**: write-tests
- **Depends On**: id-generator-callbacks, transform-jdbc-config, converters, sqlite-jdbc-dialect, dialect-spi-impl, schema-sqlite-port, spring-ai-sqlite-dialect, query-spi-refactor, runtime-profiles
- **Assigned To**: builder-tests
- **Agent Type**: general-purpose
- **Stack**: Java MockMvc Testcontainers integration test database test repository test assertj allure test structure mockito
- **Parallel**: false
- Реализовать все unit-тесты (80%): конвертеры, `SqliteJdbcDialectTest`, `PersistenceDialectPropertiesTest`, `PostgresJdbcConfigurationTest`, `SqliteJdbcConfigurationTest`, `*IdGeneratorCallbackTest`.
- Реализовать integration-тесты (15%): `SqliteFlywayBootIntegrationTest`, `SqliteRepositoryIntegrationTest`, `SqliteChatMemoryIntegrationTest`, `JobRunrSqliteIntegrationTest`.
- Реализовать smoke-тесты (5%): `SqliteSmokeApplicationTest`, `DebugProfileSmokeTest`.
- AssertJ + Allure аннотации как в существующих тестах.

### 19. Validate all (automated)

- **Task ID**: validate-all
- **Depends On**: write-tests
- **Assigned To**: validator-multi-dialect
- **Agent Type**: validator
- **Stack**: Java Spring Boot jdbc Testcontainers integration test database test
- **Parallel**: false
- `./mvnw -T1C clean verify` — все модули зелёные, все 709+152 существующих теста + новые проходят.
- `./mvnw -pl javaclaw-app spring-boot:run -Dspring-boot.run.profiles=sqlite` — приложение стартует, `curl /actuator/health` → UP, `curl /api/chat/conversations` с basic auth admin → 200.
- `./mvnw -pl javaclaw-app spring-boot:run -Dspring-boot.run.profiles=debug` — полный старт, frontend `/` отдаётся, JobRunr dashboard на 8081 доступен.
- `grep -r "JSONB\|gen_random_uuid\|TIMESTAMPTZ" javaclaw-*/src/main/java` — пусто или только под `persistence/postgres/`.
- `ls javaclaw-core/src/main/resources/db/migration/postgresql/ | wc -l` = 40 (39 исходных + V42).
- `ls javaclaw-core/src/main/resources/db/migration/sqlite/ | wc -l` = 40.
- Проверить acceptance criteria полностью.

### 20. Manual E2E validation via CDP on both profiles

- **Task ID**: manual-cdp-validation
- **Depends On**: validate-all
- **Assigned To**: builder-e2e-cdp
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot jdbc chrome cdp browser mcp e2e
- **Parallel**: false
- **Tests**: ручная валидация через реальный браузер (Chrome DevTools Protocol). Не замена автоматическим тестам — дополнительная проверка, что многодиалектный слой не сломал пользовательские сценарии в полноценной среде.
- **Обязательно прогнать оба профиля последовательно**: сначала `sqlite` (новый, критично), затем `postgresql` (регрессия, не должно быть отклонений от develop-бейзлайна).
- **Предусловия**:
  - `OPENROUTER_API_KEY` задан в окружении. Если ключ недоступен, builder-e2e-cdp обязан заранее уведомить пользователя и получить разрешение либо на временный ключ, либо на stub-конфигурацию LLM — сценарий 2 без работающей LLM недетерминирован.
  - Предыдущий `spring-boot:run` от `validate-all` должен быть остановлен. Порты 8080 и 8081 свободны.
- **Подготовка окружения** (повторяется для каждого профиля):
  0. Убедиться что порт занят ли: `lsof -ti:8080 -ti:8081 | xargs -r kill -9` (на macOS/Linux). Иначе `Address already in use` сломает прогон.
  1. Запустить приложение: `./mvnw -pl javaclaw-app spring-boot:run -Dspring-boot.run.profiles=<profile>` (для sqlite указать `-DSQLITE_FILE=/tmp/javaclaw-manual-<profile>.db`, файл удалить перед запуском для чистого старта).
  2. Дождаться `Started Application`, проверить `GET /actuator/health` → `UP`.
  3. Через MCP chrome-devtools tools открыть `http://localhost:8080/`, сделать `take_snapshot` перед началом сценариев, `list_console_messages` — зафиксировать baseline warnings.
  4. **После завершения всех сценариев профиля** — остановить процесс `spring-boot:run` (Ctrl+C или kill по PID), дождаться освобождения портов, только потом переходить ко второму профилю.
- **Критичные сценарии (все — через реальный Chrome, не httpclient)**:
  1. **Login flow**: открыть `/`, увидеть React SPA login form, залогиниться как `admin/admin`, проверить редирект на главную. Скриншот + snapshot.
  2. **Создание conversation и чат-сообщение**: создать новую conversation, отправить одно сообщение, дождаться LLM-ответа. Критерий успеха: HTTP 200 с непустым content в response; в UI появилось сообщение ассистента; в `GET /api/chat/conversations/<id>/messages` ровно 2 сообщения (user + assistant) с корректными ролями. При недоступности LLM (нет ключа / проблема провайдера) — сценарий **не засчитывается как PASS**, требуется заранее разрешить через предусловия.
  3. **Chat memory persistence**: перезагрузить страницу conversation, убедиться что история сообщений подгрузилась (это валидирует `JdbcChatMemoryRepository` + `SqliteJdbcChatMemoryRepositoryDialect` для sqlite).
  4. **Создание task**: через UI создать ad-hoc task, проверить её появление в списке, дождаться выполнения (JobRunr), проверить `GET /api/tasks/<id>` → статус `COMPLETED`. Это валидирует JobRunr sqlite storage.
  5. **Recurring task**: создать recurring task с коротким cron (каждую минуту), подождать 90 секунд, проверить что было минимум 1 execution в `task_executions`. Удалить recurring task.
  6. **Skill управление**: открыть страницу skills, создать новый skill с простым markdown body, проверить сохранение. Валидирует `BeforeConvertCallback` для `Skill` + JSON-конвертеры.
  7. **Virtual files / workspace**: создать файл через UI/API, прочитать, удалить. Валидирует `VirtualFile` + UUID callback.
  8. **McpServer CRUD**: добавить тестовый MCP server (без реального подключения — просто запись), проверить сохранение и отображение в списке.
  9. **User management (ADMIN)**: открыть admin-панель, увидеть seed-пользователей `admin` и `user`. Для sqlite — убедиться что литеральные UUID из V10 `00000000-0000-0000-0000-000000000001` / `...0002` отображаются корректно.
  10. **SSE streaming**: во время чат-сценария (п.2) проверить через `list_network_requests` что установлено SSE-соединение к `/api/chat/stream/*` и в нём идут event-ы.
  11. **JobRunr dashboard**: открыть `http://localhost:8081/dashboard`, увидеть список джоб, проверить что dashboard работает на обоих профилях без исключений.
  12. **Logout / Login cycle**: выйти, убедиться что клиентское состояние очищено (регрессия fix-chat-state-leak-on-logout-login — см. `specs/fix-chat-state-leak-on-logout-login.md`), зайти снова, увидеть пустой/свой список conversations.
- **Фиксация результатов**: на каждый сценарий — скриншот `take_screenshot` + `list_console_messages` для проверки отсутствия новых ошибок в консоли. Все результаты собираются в `specs/manual-validation-report.md` с таблицей: `сценарий | profile=sqlite | profile=postgresql | вердикт | скриншот | консоль-ошибки`.
- **Явно задокументированные gap'ы CDP-покрытия** (валидируются автоматическими тестами в таске `write-tests`, но не имеют прямого UI-сценария): `AppConfig` (нет страницы настроек как самостоятельного UI-surface), `RoleAgentConfig` / role-based routing (валидируется через `SqliteRepositoryIntegrationTest` и ролевой интеграционный test в Spring Security), `AuditLog` (нет общедоступного UI-просмотра аудита в текущей версии). В начале `manual-validation-report.md` добавить секцию "Coverage notes" с перечислением этих gap'ов и ссылкой на соответствующие автоматические тесты. Если при ручном прогоне будет обнаружено, что к ним есть UI-поверхность, добавить соответствующий сценарий в отчёт.
- **GIF запись** (опционально, но рекомендуется) через `mcp__claude-in-chrome__gif_creator` для самых сложных сценариев (2, 5, 12), чтобы иметь визуальное подтверждение regressions-free.
- **Вердикт таска**: PASS только если **все 12 сценариев × 2 профиля = 24 прогона** зелёные. Любой FAIL → блокирующий багрепорт в отдельный spec и возврат в Phase 5/6.

## Acceptance Criteria

- [ ] `javaclaw.persistence.dialect: postgresql` (дефолт) — `./mvnw clean verify` зелёный, все существующие 709+152 теста проходят без регрессий.
- [ ] `javaclaw.persistence.dialect: sqlite` + `spring.profiles.active=sqlite` — `./mvnw spring-boot:run` стартует на чистом файле, Flyway применяет все 39+1 sqlite-миграций, `GET /actuator/health` → `UP`, `GET /api/chat/conversations` (basic auth admin) → 200.
- [ ] `spring.profiles.active=debug` — полный старт на SQLite с временным файлом, никаких других отклонений от prod-конфига. React SPA отдаётся на `/`, JobRunr dashboard доступен.
- [ ] `db/migration/postgresql/` содержит 40 файлов (39 исходных + V42__drop_db_uuid_defaults). `db/migration/sqlite/` содержит 40 файлов.
- [ ] В бизнес-коде нет `JSONB` / `gen_random_uuid` / `TIMESTAMPTZ` / `PGobject`; все такие места либо портированы на ANSI/Jackson, либо изолированы в `persistence/postgres/`.
- [ ] Существующий `JdbcConfig` удалён/трансформирован; в контексте ровно один `JdbcCustomConversions` в обоих профилях.
- [ ] Все сущности с `@Id` имеют либо `BeforeConvertCallback`, либо явную Java-генерацию UUID перед save. DB-side `DEFAULT gen_random_uuid()` удалён из всех таблиц (V42).
- [ ] `JdbcChatMemoryRepository` работает на sqlite через `SqliteJdbcChatMemoryRepositoryDialect` — `SqliteChatMemoryIntegrationTest` зелёный.
- [ ] JobRunr работает в sqlite-режиме через auto-config `SqlStorageProviderFactory.using(dataSource)` без кастомных бинов — `JobRunrSqliteIntegrationTest` зелёный.
- [ ] Есть документы `specs/audit-jsonb.md`, `specs/audit-raw-sql.md`, `specs/audit-id-generation.md`, `specs/persistence-spi-design.md`.
- [ ] Добавление нового диалекта (Oracle/MySQL) требует только: новый `@Configuration` extends `AbstractJdbcConfiguration` (Dialect уже есть в Spring Data JDBC), папка `db/migration/<vendor>`, опциональные dialect-sensitive импл. Доменный код не трогается. Spring AI поддерживает Oracle/MySQL из коробки — свой `JdbcChatMemoryRepositoryDialect` не нужен.
- [ ] Test pyramid 80/15/5 соблюдён для новых тестов.
- [ ] **Ручная CDP-валидация**: все 12 критичных пользовательских сценариев × 2 профиля (sqlite, postgresql) = 24 прогона — зелёные. Отчёт в `specs/manual-validation-report.md` со скриншотами и консоль-логом для каждого сценария.

## Validation Commands

- `./mvnw -T1C clean verify` — полный build + все тесты (Postgres Testcontainers + новые sqlite).
- `./mvnw -pl javaclaw-core test -Dtest='*Sqlite*'` — только sqlite-тесты.
- `./mvnw -pl javaclaw-app spring-boot:run -Dspring-boot.run.profiles=sqlite` + `curl -sf http://localhost:8080/actuator/health | jq '.status'` → `"UP"`.
- `./mvnw -pl javaclaw-app spring-boot:run -Dspring-boot.run.profiles=debug` — полный старт; `curl http://localhost:8080/` возвращает HTML React SPA; `curl http://localhost:8081/dashboard` (JobRunr) доступен.
- `grep -rE 'JSONB|gen_random_uuid|TIMESTAMPTZ|PGobject' javaclaw-*/src/main/java | grep -v persistence/postgres` — пусто.
- `ls javaclaw-core/src/main/resources/db/migration/postgresql/ | wc -l` = 40.
- `ls javaclaw-core/src/main/resources/db/migration/sqlite/ | wc -l` = 40.

## Notes

Факты, подтверждённые через context7 (Spring Data Relational, Flyway, Spring AI, JobRunr) на момент планирования:

- **Flyway SQLite**: встроено в `flyway-core` — отдельный `flyway-database-sqlite` не нужен. Ограничения: нет `SELECT ... FOR UPDATE`, нет schemas, **nested transactions запрещены внутри миграций** (не использовать `BEGIN`/`COMMIT` блоки). Concurrent migration недоступен — для standalone single-node не проблема.
- **JobRunr SQLite**: `SqLiteStorageProvider` в main jar. `SqlStorageProviderFactory.using(dataSource)` автодетектит тип БД. Отдельный артефакт **не нужен**. Spring Boot starter auto-config сам справится с sqlite DataSource — кастомный `@Bean StorageProvider` не требуется.
- **Spring Data JDBC 4**: правильный hook конвертеров — `userConverters()` (не устаревший `jdbcCustomConversions()`). Встроенные диалекты: H2, HSQLDB, MySQL, MariaDB, Oracle, PostgreSQL, SQL Server, DB2. **SQLite отсутствует** — реализуется кастомный `JdbcDialect` (extends `AnsiDialect`).
- **Spring AI Chat Memory**: `JdbcChatMemoryRepository` поддерживает Postgres, MySQL/MariaDB, SQL Server, HSQLDB, Oracle. **SQLite — НЕ поддерживается** из коробки. Расширение — через реализацию `JdbcChatMemoryRepositoryDialect` (SELECT/INSERT/DELETE). Свойство `spring.ai.chat.memory.repository.jdbc.initialize-schema` (`embedded`/`always`/`never`) оставляем `never`, Flyway создаёт таблицу.
- **sqlite-jdbc + Java 21 virtual threads**: использовать последнюю стабильную версию (>= 3.45). SQLite при serialized writer в standalone-single-node работает корректно с Loom.
- **Spring Boot 4.0.5** — уточнённая версия из корневого `pom.xml` (не 4.0.3).
- **Миграций 39**, не 41 — V15/V16 были пропущены в истории.
- **Flyway schema history**: прод ещё не развёрнут → repair/baseline при `git mv` миграций **не нужны**. План просто делает `git mv`.

Миграция существующих Postgres-данных в SQLite — **вне скоупа**. Standalone пользователи стартуют с нуля.

Oracle/MySQL в будущем — отдельный план, реюзящий Dialect SPI. Доменный код не трогается (именно это мы сейчас закладываем).
