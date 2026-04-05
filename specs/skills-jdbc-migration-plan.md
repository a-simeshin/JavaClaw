# Plan: Skills JDBC Migration (Roadmap 1.4)

## Task Description

Мигрировать `SkillStore` (in-memory `ConcurrentHashMap`) на PostgreSQL JDBC. Таблица `skills` уже создана (Flyway V7). Java mapping готов в `specs/db-schema-v1.md`. Паттерн идентичен уже сделанному **1.2 VirtualFile** — entity+Repository в `javaclaw-core`, SkillService в `javaclaw-api-admin`, `SkillStore` **удаляется**.

Закрывает роадмап **1.4** ("Skills в DB").

## Objective

По завершении:
- `javaclaw-core/src/main/java/ai/javaclaw/skills/Skill.java` — Spring Data JDBC record (@Table("skills"))
- `javaclaw-core/src/main/java/ai/javaclaw/skills/SkillRepository.java` — `ListCrudRepository<Skill, String>` + derived queries (partial unique на owner_id)
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillService.java` — thin service: маппит `Skill ↔ SkillDto`, handle partial update merge (из SkillStore.update)
- `SkillController` рефакторен: `SkillStore → SkillService`
- `SkillStore.java` **удалён**
- OpenApiContractComplianceTest 28/28 (контракт не меняется)
- Новые тесты: SkillServiceTest (unit) + SkillRepositoryIntegrationTest (Testcontainers) + рефактор SkillControllerTest если есть
- `specs/roadmap.md` 1.4 → ✅

## Problem Statement

Текущее состояние: `SkillStore` — in-memory ConcurrentHashMap. Проблемы:
- Skills теряются при рестарте
- Не поддерживает scaling между инстансами
- Не готово к Phase 4 (per-user скиллы через owner_id)
- Комментарий в SkillStore явно указывает: "a future task will wire this to a persistent store (JDBC)" — это и есть эта задача

Таблица `skills` уже готова: id, owner_id (nullable=global), name, description, content, enabled, created_at, updated_at + partial unique indexes.

## Solution Approach

**Идентичный паттерн с 1.2 VirtualFile.** Контракт REST API не меняется (`SkillDto` остаётся тем же, endpoints те же).

### Маппинг Skill entity ↔ SkillDto

| Entity field |  DTO field  |                                      Note                                       |
|--------------|-------------|---------------------------------------------------------------------------------|
| id           | id          | UUID string                                                                     |
| ownerId      | —           | NULL (global) для MVP                                                           |
| name         | name        | required                                                                        |
| description  | description | nullable                                                                        |
| content      | —           | prompt-текст скилла, пока в DTO нет (можно оставить в БД для будущего task 2.5) |
| enabled      | enabled     | boolean                                                                         |
| createdAt    | —           | internal                                                                        |
| updatedAt    | —           | internal                                                                        |

`Skill.content` не в `SkillDto` — это ОК, DTO контракт не меняется, поле зарезервировано для будущего (агент будет читать content при активации skill в 2.5 task).

### Partial update merge

В текущем SkillStore.update: если поле в patch == NULL, сохраняем текущее значение. Перенесём эту логику в SkillService.update.

### Global-only в MVP

Все skills создаются с `owner_id = NULL` (глобальные) до Phase 4.3. Phase 4.3 добавит user-context в SkillController.

## Relevant Files

- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillController.java` — **рефакторится** (инъекция SkillService вместо SkillStore)
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillStore.java` — **удаляется**
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillDto.java` — DTO, не меняется
- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFile.java` — **эталон** entity (паттерн из 1.2)
- `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFileRepository.java` — **эталон** Repository
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/VirtualFileService.java` — **эталон** Service
- `javaclaw-core/src/test/java/ai/javaclaw/files/VirtualFileRepositoryIntegrationTest.java` — **эталон** integration-теста с Testcontainers
- `specs/db-schema-v1.md` — Java Mapping → Skill record
- `javaclaw-core/src/main/resources/db/migration/V7__create_skills.sql` — схема таблицы
- `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiContractComplianceTest.java` — регрессия контракта

### New Files

- `javaclaw-core/src/main/java/ai/javaclaw/skills/Skill.java`
- `javaclaw-core/src/main/java/ai/javaclaw/skills/SkillRepository.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillService.java`
- `javaclaw-api/javaclaw-api-admin/src/test/java/ai/javaclaw/api/admin/skills/SkillServiceTest.java` (unit, Mockito)
- `javaclaw-core/src/test/java/ai/javaclaw/skills/SkillRepositoryIntegrationTest.java` (Testcontainers @DataJdbcTest)

## Team Orchestration

### Team Members

- **Builder-Skills-JDBC**
  - Name: `builder-skills-jdbc`
  - Role: Создаёт Skill entity + SkillRepository в javaclaw-core, SkillService в javaclaw-api-admin, рефакторит SkillController, удаляет SkillStore. Копирует паттерн VirtualFile из 1.2.
  - Agent Type: `general-purpose`
  - Model: `sonnet`
  - Resume: true
- **Test-Builder-Skills**
  - Name: `test-builder-skills`
  - Role: Пишет SkillServiceTest (unit, Mockito) + SkillRepositoryIntegrationTest (Testcontainers PG 17). Регрессия: OpenApiContractComplianceTest.
  - Agent Type: `general-purpose`
  - Model: `sonnet`
  - Resume: true
- **Validator-Skills**
  - Name: `validator-skills`
  - Role: Запускает все тесты, проверяет что SkillStore удалён, обновляет родмап.
  - Agent Type: `validator`
  - Resume: true

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

Файл: `javaclaw-api/javaclaw-api-admin/src/test/java/ai/javaclaw/api/admin/skills/SkillServiceTest.java` (Mockito + AssertJ, без Spring)

- `list_returnsAllSkills_mappedToDto()` — mock repo.findAllByOwnerIdIsNull() → List[3 Skill] → verify 3 DTO
- `list_empty_returnsEmptyList()`
- `create_generatesUuid_savesEntity()` — verify Skill.ownerId == NULL, id is UUID, name/description/enabled from DTO
- `create_returnsDtoWithGeneratedId()`
- `update_existingSkill_mergesFields()` — patch with null name → keeps current name; patch with null description → keeps current
- `update_existingSkill_replacesNonNullFields()` — patch with new name → name updated
- `update_missingSkill_throwsNoSuchElementException()` → 404 via AdminExceptionHandler
- `update_preservesId()` — id из path, не из DTO
- `delete_existingSkill_callsRepoDelete()`
- `delete_missingSkill_throwsNoSuchElementException()` → 404
- `mapToDto_copiesAllFields()` — Skill → SkillDto маппинг

### Integration / API Tests (15%)

Файл: `javaclaw-core/src/test/java/ai/javaclaw/skills/SkillRepositoryIntegrationTest.java`

Testcontainers PostgreSQL 17 + @DataJdbcTest (следуя паттерну VirtualFileRepositoryIntegrationTest).

- `save_findById_roundtrip()` — INSERT + findById
- `findAllByOwnerIdIsNull_returnsOnlyGlobal()` — 2 global + 1 personal → returns 2
- `partialUniqueIndex_enforcedOnGlobalName()` — INSERT второго с owner_id=NULL same name → DuplicateKeyException
- `existsByOwnerIdIsNullAndName_returnsTrueFalse()`
- `save_withContent_persists()` — content поле сохраняется
- `deleteById_removes()`

Регрессия: `OpenApiContractComplianceTest` (4 skills endpoints + 2 negative test — всего 4 теста из 28 касаются skills) должен остаться зелёным.

### UI E2E Tests (5%)

**НЕ запускаем** — UI для skills ещё без users-контекста (Phase 4.3). Playwright deferred.

## Step by Step Tasks

### 1. Create Skill entity + SkillRepository

- **Task ID**: create-skill-entity-repo
- **Depends On**: none
- **Assigned To**: builder-skills-jdbc
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot entity record jdbc jpa maven
- **Parallel**: false
- **Tests**: SkillRepositoryIntegrationTest (task 4)
- Прочитать `javaclaw-core/src/main/java/ai/javaclaw/files/VirtualFile.java` и `VirtualFileRepository.java` как эталон
- Прочитать `specs/db-schema-v1.md` секцию "Java Mapping" для Skill
- Создать `javaclaw-core/src/main/java/ai/javaclaw/skills/Skill.java` (record с @Table("skills"), factory `newGlobal()`)
- Создать `javaclaw-core/src/main/java/ai/javaclaw/skills/SkillRepository.java` — `extends ListCrudRepository<Skill, String>` + derived queries: `findAllByOwnerIdIsNull()`, `findByOwnerIdIsNullAndName(name)`, `existsByOwnerIdIsNullAndName(name)`
- `mvn -pl javaclaw-core clean compile` — чисто

### 2. Create SkillService + refactor SkillController + delete SkillStore

- **Task ID**: refactor-skill-controller
- **Depends On**: create-skill-entity-repo
- **Assigned To**: builder-skills-jdbc
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot controller exception error handling record
- **Parallel**: false
- **Tests**: SkillServiceTest (task 4), OpenApiContractComplianceTest regression
- Создать `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillService.java`:
  - Inject SkillRepository
  - Methods: `List<SkillDto> list()`, `SkillDto create(SkillDto draft)`, `SkillDto update(String id, SkillDto patch)`, `void delete(String id)`
  - Mapping helpers: `toDto(Skill)`, `toEntity(SkillDto, ownerId)`
  - `update()`: load existing, merge nullable fields (partial update semantics из SkillStore)
  - `update()`, `delete()`: throw NoSuchElementException если не найден
- `SkillController`: constructor changes `SkillStore store` → `SkillService service`. Method bodies меняются: `store.list()` → `service.list()`, etc.
- **Удалить** `SkillStore.java`
- `mvn -pl javaclaw-core,javaclaw-api/javaclaw-api-admin -am clean compile` — чисто
- `mvn -pl javaclaw-app test -Dtest=OpenApiContractComplianceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20` — 28/28 зелёные

### 3. Install javaclaw-core for downstream modules

- **Task ID**: install-core
- **Depends On**: create-skill-entity-repo
- **Assigned To**: builder-skills-jdbc
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot maven
- **Parallel**: false
- **Tests**: none
- Запустить `mvn -pl javaclaw-core install -DskipTests -q` — чтобы javaclaw-api-admin видел Skill/SkillRepository при тестах

### 4. Write tests

- **Task ID**: write-tests
- **Depends On**: refactor-skill-controller, install-core
- **Assigned To**: test-builder-skills
- **Agent Type**: general-purpose
- **Stack**: Java testcontainers integration test jdbc database test repository test assertj allure test structure mockito
- **Parallel**: false
- **Tests**: сами тесты
- `SkillServiceTest` (unit, Mockito) — 11 тестов из Testing Strategy
- `SkillRepositoryIntegrationTest` (Testcontainers, @DataJdbcTest) — 6 тестов
- Запустить `mvn -pl javaclaw-api/javaclaw-api-admin test -Dtest=SkillServiceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20`
- Запустить `mvn -pl javaclaw-core test -Dtest=SkillRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20`
- Запустить `mvn -pl javaclaw-app test -Dtest=OpenApiContractComplianceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20` (регрессия)

### 5. Validation + roadmap update

- **Task ID**: validate-all
- **Depends On**: create-skill-entity-repo, refactor-skill-controller, write-tests
- **Assigned To**: validator-skills
- **Agent Type**: validator
- **Stack**: Java Spring Boot maven testcontainers integration test jdbc database test
- **Parallel**: false
- Verify files: Skill.java, SkillRepository.java, SkillService.java exist
- Verify SkillStore.java удалён (не существует)
- Verify SkillController injects SkillService (grep)
- Verify: 11 SkillServiceTest + 6 SkillRepositoryIntegrationTest + 28 OpenApiContractComplianceTest = все зелёные
- Verify: DbSchemaV1* тесты остаются 31/31, VirtualFile* тесты 32/32 (регрессия)
- Update `specs/roadmap.md`: 1.4 → ✅
- Update P0 status table row: 1.4 ❌ → ✅
- PASS/FAIL verdict

## Acceptance Criteria

- [ ] `javaclaw-core/src/main/java/ai/javaclaw/skills/Skill.java` существует
- [ ] `javaclaw-core/src/main/java/ai/javaclaw/skills/SkillRepository.java` существует
- [ ] `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillService.java` существует
- [ ] `SkillStore.java` не существует (удалён)
- [ ] `SkillController` использует SkillService
- [ ] 11+ unit тестов SkillServiceTest зелёные
- [ ] 6 integration тестов SkillRepositoryIntegrationTest зелёные
- [ ] OpenApiContractComplianceTest остаётся 28/28
- [ ] VirtualFile* и DbSchemaV1* тесты остаются зелёные (регрессия)
- [ ] `specs/roadmap.md` 1.4 → ✅
- [ ] P0 status table row "1.4 Skills в DB" → ✅

## Validation Commands

- `test -f /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw/javaclaw-core/src/main/java/ai/javaclaw/skills/Skill.java && echo EXISTS`
- `test ! -f /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw/javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillStore.java && echo GONE`
- `grep -c 'SkillService' /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw/javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillController.java` — должно быть >= 2
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-core test -Dtest=SkillRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -15`
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-api/javaclaw-api-admin test -Dtest=SkillServiceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -15`
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn -pl javaclaw-app test -Dtest=OpenApiContractComplianceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -15`

## Notes

- **owner_id=NULL в MVP** — до Spring Security (Phase 4.1) все skills global.
- **Skill.content** — новое поле (в DB есть, в DTO пока нет). SkillService заполняет content=NULL при create/update. Phase 2.5 (Skill management @Tool) будет использовать content для передачи агенту.
- **E2E deferred** — UI skills без users-контекста (Phase 4.3).
- **Тест install-core** — проверено в 1.2 что artifact не видится кросс-модулями без `mvn install`. Задача 3 (install-core) устраняет эту проблему.
- **Паттерн 1:1 с 1.2** — builder читает эталон VirtualFile и копирует структуру.

