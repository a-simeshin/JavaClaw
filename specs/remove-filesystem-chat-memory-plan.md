# Plan: Remove FileSystemChatMemoryRepository (Roadmap 0.2)

## Task Description

Удалить мёртвый код `FileSystemChatMemoryRepository` из `javaclaw-core`. Класс является legacy-артефактом от раннего MVP (до миграции на PostgreSQL): сохранял историю чата в YAML-файлы на диске. Полностью заменён `JdbcAppendableChatMemoryRepository` (`@Primary` bean, таблица `SPRING_AI_CHAT_MEMORY`, миграции V2/V3). Удаление подтверждает что persistence чат-мемори полностью переведён на PostgreSQL и убирает риск случайного регресса.

Закрывает последний незакрытый пункт **Phase 0.2** в родмапе ("Удалить FileSystem chat memory").

## Objective

По завершении:
- `javaclaw-core/src/main/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepository.java` удалён
- `javaclaw-core/src/test/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepositoryTest.java` удалён
- `mvn clean verify` проходит полностью (все модули зелёные)
- `mvn -pl javaclaw-core test` для chat memory тестов (JdbcAppendableChatMemoryRepositoryTest если есть) зелёное
- Родмап пункт 0.2 "Удалить FileSystem chat memory" → `[x]`
- Git diff содержит только удаления, никаких правок в других файлах

## Problem Statement

`FileSystemChatMemoryRepository` — dead code:
- НЕ имеет Spring-аннотаций (@Component/@Repository/@Bean/@Configuration) — НЕ регистрируется как bean
- НОЛЬ production-использований по всей кодовой базе (`grep 'new FileSystemChatMemoryRepository\|FileSystemChatMemoryRepository\.'` → пусто)
- Единственный ссылающийся файл — его собственный unit test
- `@Value("${agent.workspace:Unknown}")` — использует property, но это shared property (WorkspaceFileService), НЕ удаляется
- Импортирует `YamlDocument`/`YamlParser` — shared утилиты (4 использования в main), НЕ удаляются

Риск оставления: кто-то случайно восстанавливает его как bean или дублирует логику. Каждый новый контрибьютор тратит время разбираясь какую имплементацию использовать.

## Solution Approach

**Прямое удаление — без @Deprecated, без fallback:**

1. Перед удалением: `git grep` подтверждает 0 production usages (уже сделано)
2. Удалить 2 файла (класс + тест)
3. `mvn clean verify` — подтвердить зелёный билд
4. Запустить `JdbcAppendableChatMemoryRepositoryTest` если существует — подтвердить чат-мемори через БД работает
5. Запустить `OpenApiContractComplianceTest` — подтвердить что конверсейшн-эндпоинты работают (они используют chat memory через Spring AI)
6. Обновить родмап

**Почему не @Deprecated first**: проект ещё не релизился, внешних пользователей нет, memory-репозитории — internal infrastructure. Нет причин тянуть @Deprecated-фазу.

## Relevant Files

- `javaclaw-core/src/main/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepository.java` — **удалить**
- `javaclaw-core/src/test/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepositoryTest.java` — **удалить**
- `javaclaw-core/src/main/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepository.java` — сохранить, это `@Primary` активная имплементация
- `javaclaw-core/src/test/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepositoryTest.java` (если существует) — сохранить и прогнать
- `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiContractComplianceTest.java` — прогнать (косвенно использует chat memory через GET /api/conversations)

## Team Orchestration

### Team Members

- **Builder-Refactor**
  - Name: `builder-refactor-remove`
  - Role: Удаляет 2 файла, запускает `mvn verify`, чинит если что-то сломалось. Никаких правок других файлов — если нужна правка, возвращает ошибку для анализа.
  - Agent Type: `general-purpose`
  - Model: `haiku` (тривиальная задача)
  - Resume: true
- **Validator**
  - Name: `validator-refactor`
  - Role: Подтверждает: (1) 2 файла удалены, (2) `mvn clean verify` зелёный, (3) git diff содержит только удаления, (4) chat-memory-related тесты зелёные. Отмечает в роадмапе.
  - Agent Type: `validator`
  - Resume: true

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e** — для удаления мёртвого кода нет НОВЫХ тестов. Полагаемся на существующие регрессионные:

### Unit Tests (80%)

- `JdbcAppendableChatMemoryRepositoryTest` — если существует, прогнать чтобы убедиться что активная имплементация чат-мемори работает
- Remaining tests in `javaclaw-core` — прогнать как regression

### Integration / API Tests (15%)

- `OpenApiContractComplianceTest` — тесты для `/api/conversations`, `/api/conversations/{id}/messages` покрывают chat memory через REST (уже 30 тестов, запускать все)
- `DbSchemaV1SpecTest`, `DbSchemaV1FullMigrationE2ETest` — не связаны, но должны остаться зелёными

### UI E2E Tests (5%)

**НЕ запускаем** — правило из памяти: E2E только когда logic+DB+UI готовы вместе. Это только backend refactor, UI не трогается. Playwright `ChatFlowE2ETest` не прогоняем.

## Step by Step Tasks

### 1. Delete FileSystemChatMemoryRepository files

- **Task ID**: delete-files
- **Depends On**: none
- **Assigned To**: builder-refactor-remove
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot maven
- **Parallel**: false
- **Tests**: регрессионные (unit + integration)
- Удалить: `javaclaw-core/src/main/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepository.java`
- Удалить: `javaclaw-core/src/test/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepositoryTest.java`
- Запустить `mvn -pl javaclaw-core clean compile` — проверить компиляцию
- Если есть компиляционные ошибки (маловероятно, т.к. 0 usages) — не восстанавливать файл, отчитаться для диагностики

### 2. Run regression tests

- **Task ID**: run-regression
- **Depends On**: delete-files
- **Assigned To**: builder-refactor-remove
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot maven testcontainers integration test jdbc database test repository test assertj allure test structure
- **Parallel**: false
- **Tests**: регрессия всего что связано с chat memory
- Запустить `mvn -pl javaclaw-core clean test` (все тесты core модуля)
- Запустить `mvn -pl javaclaw-app test -Dtest='OpenApiContractComplianceTest,OpenApiSchemaValidationTest'` (контрактные)
- Запустить `mvn -pl javaclaw-core test -Dtest='DbSchemaV1*Test'` (новые DB schema тесты)
- Все должны быть зелёные. Если нет — диагностировать причину.

### 3. Validation + roadmap update

- **Task ID**: validate-all
- **Depends On**: delete-files, run-regression
- **Assigned To**: validator-refactor
- **Agent Type**: validator
- **Stack**: Java Spring Boot maven
- **Parallel**: false
- Verify: 2 файла удалены через Glob
- Verify: `git status` показывает только 2 deletions, никаких modifications в других main/test файлах (кроме возможно roadmap.md)
- Run: `mvn clean verify -pl javaclaw-core,javaclaw-app -am 2>&1 | tail -40` — зелёное
- Run: `git grep 'FileSystemChatMemoryRepository'` — только в specs/*.md и .claude/hooks/validators/*.log остались (это приемлемо — историческая документация)
- Отметить в `specs/roadmap.md`: пункт "0.2 Удалить FileSystem chat memory" → `[x]`
- Обновить таблицу статусов P0 в начале roadmap.md (0.2 теперь ✅)
- PASS/FAIL verdict

## Acceptance Criteria

- [ ] `javaclaw-core/src/main/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepository.java` не существует
- [ ] `javaclaw-core/src/test/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepositoryTest.java` не существует
- [ ] `mvn clean verify -pl javaclaw-core,javaclaw-app -am` возвращает BUILD SUCCESS
- [ ] `git grep 'FileSystemChatMemoryRepository' -- 'javaclaw-*/src/'` → пусто (исключая specs/ и .claude/hooks/)
- [ ] `OpenApiContractComplianceTest` 30/30 зелёные (chat memory работает)
- [ ] `DbSchemaV1SpecTest` 13/13 зелёные (DB слой интактен)
- [ ] В `specs/roadmap.md` пункт 0.2 FileSystem chat memory → `[x]`
- [ ] В таблице статусов P0: строка "0.2 Чистка кодовой базы" → ✅ (был ⚠️)

## Validation Commands

- `test ! -f javaclaw-core/src/main/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepository.java && echo GONE`
- `test ! -f javaclaw-core/src/test/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepositoryTest.java && echo GONE`
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && mvn clean verify -pl javaclaw-core,javaclaw-app -am 2>&1 | tail -40`
- `cd /Users/artemsimeisn/IdeaProjects/sber/aihub/JavaClaw && git grep -l 'FileSystemChatMemoryRepository' -- 'javaclaw-*/src/**'` (должно быть пусто)
- `git status --short javaclaw-core javaclaw-app` (проверить что нет неожиданных modifications)

## Notes

- **No new code written** — только удаление двух файлов
- **Не трогаем**: `agent.workspace` property, `YamlDocument`, `YamlParser`, `JdbcAppendableChatMemoryRepository`
- **Documentation references** в `specs/roadmap.md`, `specs/javaclaw-module-restructure.md`, `specs/postgresql-migration-scalable-mvp.md`, `openspec/specs/javaclaw-service/javaclaw-service.md` — оставить как историческую документацию. Grep по ним показывает что они уже описывают `FileSystemChatMemoryRepository` как deprecated/removed.
- **E2E НЕ запускаем** — backend refactor только, UI не трогается (зафиксированное правило).
- **Git diff expectation**: 2 deleted files (~200-300 строк удалено), возможно 1-2 строки в roadmap.md обновлены.

