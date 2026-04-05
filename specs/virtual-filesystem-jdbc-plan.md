# Plan: Virtual Filesystem в DB (Roadmap 1.2)

## Task Description

Переписать `/api/files/**` REST API с filesystem-backed (`WorkspaceFileService` через NIO.2) на JDBC-backed (новый `VirtualFileService` через `VirtualFileRepository` → таблица `virtual_files`). Таблица уже создана (Flyway V6). Java mapping готов в `specs/db-schema-v1.md`. Контракт OpenAPI не меняется — REST endpoint'ы и DTO остаются идентичными. `WorkspaceFileService` НЕ удаляется — остаётся для future использования агентом (чтение AGENT.md/SOUL.md с диска).

Закрывает пункт роадмапа **1.2** ("Virtual filesystem в DB").

## Objective

По завершении:
- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFile.java` — Spring Data JDBC record (из spec db-schema-v1.md)
- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileRepository.java` — `ListCrudRepository<VirtualFile, String>` + custom queries (findByOwnerIdAndPath, findByOwnerIdIsNull, etc.)
- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileService.java` — CRUD operations: `tree()`, `read(path)`, `write(path, content)`, `delete(path)`, `create(path, content)` — работают через БД
- `FileController` (javaclaw-api-admin) рефакторен — использует `VirtualFileService` вместо `WorkspaceFileService`
- `VirtualFileSeeder` (ApplicationRunner в javaclaw-core) — при старте загружает файлы из `./workspace/` в БД (owner_id=NULL) если их там ещё нет (one-shot seed)
- Все существующие контрактные тесты (`OpenApiContractComplianceTest` files endpoints) остаются зелёными — контракт не сломан
- Новые тесты: VirtualFileService unit + VirtualFileRepository integration (Testcontainers) + обновлённый FileController MockMvc
- Родмап 1.2 → ✅

## Problem Statement

Текущее состояние: файлы хранятся на диске в `./workspace/` через `WorkspaceFileService`. Проблемы:
- Нет per-user изоляции (все юзеры видят один workspace)
- Файлы теряются при контейнерном ре-деплое (если не монтировать volume)
- Не поддерживается horizontal scaling (не общий файловый доступ между инстансами)
- Не готово под Phase 4 Basic Auth где нужна изоляция `owner_id`

Таблица `virtual_files` уже готова с полями: id, owner_id (nullable=global), path, content, content_type, size_bytes, created_at, updated_at + partial unique indexes.

## Solution Approach

**Сохраняем API контракт неизменным** — только заменяем backend слой. `OpenApiContractComplianceTest` должен остаться 28/28 зелёным без правок.

### Compatibility: owner_id в pre-auth эпоху

Spring Security (Phase 4.1) ещё не подключён → `getUserPrincipal()` везде NULL → все операции в REST делаются от "гостя" (owner_id=NULL = глобальные файлы). Это поведение корректное для MVP, будет заменено в Phase 4.3 когда `/api/files/**` получит user-context.

### Path handling

Wildcard `/api/files/**` → FileController.extractPath() уже парсит путь корректно → передаём строку как есть в VirtualFileService. Никакого path-traversal check'а не нужно (БД естественно безопасна) — оставляем normalization (`.normalize()`) и отклоняем пути начинающиеся с `/` или содержащие `..` для consistency с WorkspaceFileService semantics.

### Tree building

`GET /api/files` → `tree()` — строит `FileNodeDto` иерархию из плоского списка путей из БД. Группируем по '/' разделителю, создаём виртуальные "dir" узлы для промежуточных папок, "file" узлы для реальных файлов.

### Seed из workspace/

ApplicationRunner `VirtualFileSeeder` при старте:
1. `SELECT count(*) FROM virtual_files WHERE owner_id IS NULL` → если > 0, скип (уже засиден)
2. Иначе: walk `./workspace/` recursively, для каждого файла INSERT в virtual_files с owner_id=NULL
3. Log: "Seeded N virtual files from workspace/"

Это разовая операция — потом файлы живут только в БД.

## Relevant Files

- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/FileController.java` — **рефакторится** (поменять инъекцию WorkspaceFileService → VirtualFileService)
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/WorkspaceFileService.java` — **сохраняется** (future agent use)
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/FileNodeDto.java` — DTO, не меняется
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/FileContentDto.java` — DTO, не меняется
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/CreateFileRequest.java` — DTO, не меняется
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/Task.java` — эталон JDBC entity
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskRepository.java` — эталон Repository
- `specs/db-schema-v1.md` — секция Java Mapping для VirtualFile
- `javaclaw-core/src/main/resources/db/migration/V6__create_virtual_files.sql` — схема таблицы
- `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiContractComplianceTest.java` — должен остаться зелёным

### New Files

- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFile.java`
- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileRepository.java`
- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileService.java`
- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileSeeder.java` (ApplicationRunner)
- `javaclaw-core/src/test/java/ai/javaclaw/files/VirtualFileServiceTest.java` (unit, Mockito)
- `javaclaw-core/src/test/java/ai/javaclaw/files/VirtualFileRepositoryIntegrationTest.java` (Testcontainers @DataJdbcTest)

## Implementation Phases

### Phase 1: Foundation (entity + repository in core)

- VirtualFile record: `@Table("virtual_files") record VirtualFile(@Id String id, @Column("owner_id") String ownerId, @Column("path") String path, @Column("content") String content, @Column("content_type") String contentType, @Column("size_bytes") long sizeBytes, @Column("created_at") Instant createdAt, @Column("updated_at") Instant updatedAt)` + factory method `newFile()`
- VirtualFileRepository: `ListCrudRepository<VirtualFile, String>` + derived queries:
  - `Optional<VirtualFile> findByOwnerIdIsNullAndPath(String path)` — global file by path
  - `Optional<VirtualFile> findByOwnerIdAndPath(String ownerId, String path)` — personal file
  - `List<VirtualFile> findAllByOwnerIdIsNull()` — все global
  - `List<VirtualFile> findAllByOwnerId(String ownerId)` — все personal
  - `void deleteByOwnerIdIsNullAndPath(String path)`
  - `boolean existsByOwnerIdIsNullAndPath(String path)`

### Phase 2: Core Implementation (service + seeder)

- `VirtualFileService` с методами (mirror WorkspaceFileService API):
  - `FileNodeDto tree()` — строит дерево из findAllByOwnerIdIsNull()
  - `FileContentDto read(String path)` — findByOwnerIdIsNullAndPath или IllegalArgumentException (→ 400 через AdminExceptionHandler)
  - `FileContentDto write(String path, String content)` — upsert: существует → update content/updated_at/size; нет → INSERT
  - `void delete(String path)` — deleteByOwnerIdIsNullAndPath
  - `FileContentDto create(String path, String content)` — INSERT, если существует → IllegalStateException (→ 400)
- Path validation: normalizePath(path) отклоняет null/blank, starts with '/' или содержит ".." → IllegalArgumentException
- contentType inference: по extension (`.md` → "text/markdown", `.txt` → "text/plain", `.json` → "application/json", default "text/plain")
- `VirtualFileSeeder` ApplicationRunner — one-shot seed из ./workspace/

### Phase 3: Integration & Polish (FileController refactor + tests)

- `FileController` меняет constructor injection: `WorkspaceFileService → VirtualFileService`
- Все method body остаются без изменений (API methods имеют идентичную сигнатуру)
- Unit тесты VirtualFileService с Mockito-стабом VirtualFileRepository
- Integration тест VirtualFileRepositoryIntegrationTest через Testcontainers
- Существующий OpenApiContractComplianceTest гоняется — регрессия для контракта

## Team Orchestration

### Team Members

- **Builder-VFS-Core**
  - Name: `builder-vfs-core`
  - Role: Создаёт VirtualFile entity, VirtualFileRepository, VirtualFileService, VirtualFileSeeder в javaclaw-core. Пишет unit тесты.
  - Agent Type: `general-purpose`
  - Model: `sonnet`
  - Resume: true
- **Builder-VFS-API-Refactor**
  - Name: `builder-vfs-api-refactor`
  - Role: Меняет FileController injection с WorkspaceFileService на VirtualFileService. Гоняет OpenApiContractComplianceTest чтобы убедиться что контракт не сломан.
  - Agent Type: `general-purpose`
  - Model: `sonnet`
  - Resume: true
- **Test-Builder-VFS**
  - Name: `test-builder-vfs`
  - Role: Пишет Testcontainers integration test для VirtualFileRepository, проверяет partial unique indexes, CRUD, ownership model.
  - Agent Type: `general-purpose`
  - Model: `sonnet`
  - Resume: true
- **Validator-VFS**
  - Name: `validator-vfs`
  - Role: Запускает все тесты, проверяет что seeder работает, OpenApiContractComplianceTest зелёный, обновляет родмап.
  - Agent Type: `validator`
  - Resume: true

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

Файл: `javaclaw-core/src/test/java/ai/javaclaw/files/VirtualFileServiceTest.java` (Mockito + AssertJ, без Spring)

- `read_existingFile_returnsContent()` — mocked repo returns VirtualFile
- `read_missingFile_throwsIllegalArgumentException()` — empty Optional → exception
- `read_blankPath_throwsIllegalArgumentException()` — empty/null path
- `read_pathWithDotDot_throwsIllegalArgumentException()` — "../etc/passwd"
- `read_absolutePath_throwsIllegalArgumentException()` — "/root/x"
- `write_newFile_insertsVirtualFile()` — verify repo.save called with correct fields
- `write_existingFile_updatesContent()` — updated_at меняется, id сохраняется
- `write_inferContentType_byExtension()` — .md → text/markdown, .json → application/json, default text/plain
- `write_sizeBytes_matchesContentLength()` — size_bytes = content.getBytes(UTF_8).length
- `create_newFile_succeeds()` — вставка нового файла
- `create_existingFile_throwsIllegalStateException()` — файл уже есть
- `create_nullContent_savesEmptyString()` — null content → ""
- `delete_existingFile_callsRepo()` — verify repo.deleteBy...
- `delete_missingFile_silentOrThrows()` — документировать поведение: silent (idempotent)
- `tree_flatListOfFiles_buildsHierarchy()` — 3 файла в разных папках → правильная иерархия FileNodeDto
- `tree_emptyRepo_returnsEmptyRootDir()` — root dir без children
- `tree_fileAtRoot_asDirectChild()` — AGENT.md на корне
- `tree_deepNesting_creates4LevelsDir()` — src/main/java/Main.java → 4 уровня
- `normalizePath_removesLeadingSlash()` — "/foo.md" → "foo.md" (или throws если не разрешаем)

Файл: `javaclaw-core/src/test/java/ai/javaclaw/files/VirtualFileSeederTest.java` (Mockito)

- `seed_emptyDb_copiesWorkspaceFiles()` — mocked repo.count==0, walks temp dir with files, verify save calls
- `seed_alreadySeeded_noop()` — mocked repo.count>0, no save calls
- `seed_missingWorkspaceDir_logsWarning()` — workspace не существует → log warning, no exception

### Integration / API Tests (15%)

Файл: `javaclaw-core/src/test/java/ai/javaclaw/files/VirtualFileRepositoryIntegrationTest.java`

Использует Testcontainers PostgreSQL 17 + @DataJdbcTest (или custom minimal Spring context если @DataJdbcTest не поднимается).

- `save_andFindById_roundtrip()` — INSERT + findById
- `findByOwnerIdIsNullAndPath_returnsGlobalFile()` — INSERT global, find by path
- `findByOwnerIdAndPath_returnsPersonalFile()` — после создания user seed в users
- `findAllByOwnerIdIsNull_returnsOnlyGlobal()` — 2 global + 1 personal → возвращает 2
- `partialUniqueIndex_enforcedOnGlobalPath()` — INSERT второго файла с owner_id=NULL same path → DuplicateKeyException
- `partialUniqueIndex_allowsDifferentUsersSamePath()` — user1 и user2 оба могут иметь foo.md
- `deleteByOwnerIdIsNullAndPath_removes()`
- `existsByOwnerIdIsNullAndPath_returnsTrueFalse()`

Файл: `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiContractComplianceTest.java` (EXISTING, нужно проверить что 28/28 остаются зелёные после рефактора FileController — это регрессионный тест)

### UI E2E Tests (5%)

**НЕ запускаем** — UI для file manager ещё не переписан под users-систему (будет в Phase 4.3 / 5.7). Playwright E2E deferred.

## Step by Step Tasks

### 1. Create VirtualFile entity + VirtualFileRepository

- **Task ID**: create-entity-repo
- **Depends On**: none
- **Assigned To**: builder-vfs-core
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot entity record jdbc jpa maven
- **Parallel**: false
- **Tests**: VirtualFileRepositoryIntegrationTest (создаётся в task 5)
- Прочитать `specs/db-schema-v1.md` секция "Java Mapping" → VirtualFile record
- Прочитать `javaclaw-core/src/main/java/ai/javaclaw/tasks/Task.java` + `TaskRepository.java` как эталон
- Создать `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFile.java` (record с @Table/@Id/@Column, factory `newFile()`)
- Создать `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileRepository.java` — `extends ListCrudRepository<VirtualFile, String>` + derived queries (findByOwnerIdIsNullAndPath, findAllByOwnerIdIsNull, deleteByOwnerIdIsNullAndPath, existsByOwnerIdIsNullAndPath)
- `mvn -pl javaclaw-core clean compile` — чисто

### 2. Create VirtualFileService

- **Task ID**: create-service
- **Depends On**: create-entity-repo
- **Assigned To**: builder-vfs-core
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot entity record error handling exception
- **Parallel**: false
- **Tests**: VirtualFileServiceTest (task 5)
- Создать `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileService.java`
- Methods: `tree()`, `read(path)`, `write(path, content)`, `delete(path)`, `create(path, content)` — identical signatures to WorkspaceFileService
- Path validation: reject null/blank/startsWith("/")/contains("..") → IllegalArgumentException (handled by AdminExceptionHandler → 400)
- contentType inference helper
- Build tree from flat list (group by '/' separator, recursive FileNodeDto)
- `mvn -pl javaclaw-core clean compile` — чисто

### 3. Create VirtualFileSeeder (ApplicationRunner)

- **Task ID**: create-seeder
- **Depends On**: create-service
- **Assigned To**: builder-vfs-core
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot record
- **Parallel**: false
- **Tests**: VirtualFileSeederTest (task 5)
- Создать `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileSeeder.java` (`@Component` + `ApplicationRunner`)
- Inject `VirtualFileRepository` + `@Value("${agent.workspace:file:./workspace/}") Resource workspaceDir`
- `run(ApplicationArguments args)`: if `repository.findAllByOwnerIdIsNull().isEmpty()` → walk workspace directory, save each file as VirtualFile(ownerId=null, path=relativePath, content=Files.readString, contentType=inferred)
- Log total count + "skipped" when already seeded
- Missing workspace dir → log warning, no exception

### 4. Refactor FileController

- **Task ID**: refactor-file-controller
- **Depends On**: create-service
- **Assigned To**: builder-vfs-api-refactor
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot controller exception error handling
- **Parallel**: false
- **Tests**: OpenApiContractComplianceTest (existing regression)
- Заменить `WorkspaceFileService workspaceFileService` → `VirtualFileService virtualFileService` в constructor
- Все method bodies: заменить `workspaceFileService.xxx` → `virtualFileService.xxx`
- API signatures и DTOs не меняются
- `mvn -pl javaclaw-api-admin clean compile` — чисто
- `mvn -pl javaclaw-app test -Dtest=OpenApiContractComplianceTest` — 28/28 зелёные (регрессия контракта)

### 5. Write unit + integration tests

- **Task ID**: write-tests
- **Depends On**: create-entity-repo, create-service, create-seeder, refactor-file-controller
- **Assigned To**: test-builder-vfs
- **Agent Type**: general-purpose
- **Stack**: Java testcontainers integration test jdbc database test repository test assertj allure test structure mockito
- **Parallel**: false
- **Tests**: сами тесты
- `VirtualFileServiceTest` (unit, Mockito, ~19 tests по Testing Strategy)
- `VirtualFileSeederTest` (unit, Mockito, 3 tests)
- `VirtualFileRepositoryIntegrationTest` (Testcontainers PostgreSQL 17, 8 tests) — применить V1-V10 миграции + тестировать репо
- Запустить `mvn -pl javaclaw-core test -Dtest='VirtualFile*Test'` → все зелёные
- Запустить `mvn -pl javaclaw-app test -Dtest='OpenApiContractComplianceTest'` → 28/28 зелёные

### 6. Full validation + roadmap update

- **Task ID**: validate-all
- **Depends On**: create-entity-repo, create-service, create-seeder, refactor-file-controller, write-tests
- **Assigned To**: validator-vfs
- **Agent Type**: validator
- **Stack**: Java Spring Boot maven testcontainers integration test jdbc database test repository test assertj
- **Parallel**: false
- Run: `mvn -pl javaclaw-core,javaclaw-api/javaclaw-api-admin,javaclaw-app -am clean verify 2>&1 | tail -50`
- Verify: все VirtualFile* тесты зелёные
- Verify: OpenApiContractComplianceTest 28/28
- Verify: DbSchemaV1* тесты остаются 31/31 (регрессия)
- Verify: `FileController` действительно использует VirtualFileService (grep)
- Verify: `WorkspaceFileService` НЕ удалён (grep)
- Mark `specs/roadmap.md` 1.2 → ✅
- Update P0 status table row 1.2
- PASS/FAIL verdict

## Acceptance Criteria

- [ ] `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFile.java` существует (record с @Table("virtual_files"))
- [ ] `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileRepository.java` существует (extends ListCrudRepository)
- [ ] `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileService.java` существует
- [ ] `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileSeeder.java` существует (@Component ApplicationRunner)
- [ ] `FileController` использует VirtualFileService (не WorkspaceFileService)
- [ ] WorkspaceFileService сохранён (не удалён, не @Deprecated)
- [ ] 19+ unit тестов VirtualFileServiceTest зелёные
- [ ] 3 unit теста VirtualFileSeederTest зелёные
- [ ] 8 integration тестов VirtualFileRepositoryIntegrationTest зелёные (Testcontainers)
- [ ] OpenApiContractComplianceTest остаётся 28/28 (регрессия контракта)
- [ ] DbSchemaV1* тесты остаются 31/31 (регрессия)
- [ ] `specs/roadmap.md` 1.2 → ✅
- [ ] P0 status table row "1.2 Virtual filesystem в DB" → ✅

## Validation Commands

- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-core test -Dtest='VirtualFile*Test' 2>&1 | tail -20`
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-app test -Dtest='OpenApiContractComplianceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20`
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-core test -Dtest='DbSchemaV1*Test' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20`
- `grep 'VirtualFileService' /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw/javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/FileController.java` — должно найти

## Notes

- **owner_id=NULL везде** — пока Spring Security не подключён, все REST операции работают с глобальными файлами (owner_id=NULL). Phase 4.3 добавит user-context.
- **Path normalization**: reject absolute paths и ".." — это consistency с WorkspaceFileService API. Защита от path traversal не нужна в БД, но semantics одинаковые.
- **E2E deferred** — UI ещё в single-user режиме, Playwright тест бессмыслен пока 4.3 не готов.
- **AGENT.md/SOUL.md seed**: при старте один раз копируются из `./workspace/` в БД с owner_id=NULL. Если user меняет AGENT.md через REST — изменения только в БД, не в файле на диске. DefaultAgent (когда начнёт читать AGENT.md в задаче 2.2) будет читать из БД через VirtualFileService.
- **Contract preservation**: OpenAPI spec НЕ меняется, FileController endpoints идентичны. Единственное изменение: `POST /api/files` с duplicate path теперь вернёт 400 вместо того поведения что было у WorkspaceFileService (там Files.writeString с StandardOpenOption.CREATE_NEW дал IOException → 400 через exception handler). Семантика одинаковая.
- **No @DataJdbcTest if problematic**: если @DataJdbcTest не поднимается на Spring Boot 4.0.3 EA, использовать full @SpringBootTest(classes=JavaClawApplication) + Testcontainers как в существующих тестах.

