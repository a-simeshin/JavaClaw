# REST API Documentation

## Путь к контракту

**Единственный источник истины** для всех REST API контрактов: `specs/openapi.yaml`

Все endpoint'ы, параметры, схемы и примеры определены в этом файле. Документация, тесты и клиенты генерируются из этого контракта.

## Валидация локально

Проверить синтаксис и корректность OpenAPI схемы:

```bash
npx @redocly/cli lint specs/openapi.yaml
```

Запускайте перед коммитом при изменении контракта.

## Просмотр Swagger UI

Открыть интерактивную документацию API:

```bash
npx @redocly/cli preview-docs specs/openapi.yaml
```

Откроется http://localhost:8080 с полной документацией, примерами запросов и возможностью пробовать endpoint'ы в браузере.

## Генерация TypeScript клиента

Сгенерировать типы фронтенда из контракта:

```bash
npx openapi-typescript specs/openapi.yaml -o javaclaw-frontend/src/api/schema.ts
```

Используйте в компонентах:

```typescript
import type { components } from '../api/schema'

type ChatRequest = components['schemas']['ChatRequest']
type ChatResponse = components['schemas']['ChatResponse']
```

## Запуск contract-compliance тестов

Убедиться, что реальная реализация соответствует контракту:

```bash
mvn -pl javaclaw-app test -Dtest='OpenApi*Test'
```

**Тесты находятся в:** `javaclaw-app/src/test/java/ai/javaclaw/contract/`

Эти тесты используют swagger-request-validator для проверки, что все ответы контроллеров точно соответствуют OpenAPI схеме.

## Добавление нового endpoint (governance rule)

**Обязательный порядок:**

1. **STEP 1:** Добавить endpoint в `specs/openapi.yaml` **ПЕРВЫМ** (spec-first подход)
   - Определить операцию, параметры, request/response схемы
   - Добавить примеры в `examples`
2. **STEP 2:** Запустить валидацию

   ```bash
   npx @redocly/cli lint specs/openapi.yaml
   ```
3. **STEP 3:** Написать `@Test` в `OpenApiContractComplianceTest` (красный тест)
   - Тест должен проверить соответствие ответа контракту
4. **STEP 4:** Реализовать контроллер (зелёный тест)
   - Создать метод в `@RestController`
   - Вернуть ответ в формате контракта
5. **STEP 5:** Запустить полный тест-suite

   ```bash
   mvn verify
   ```

   Должны пройти все тесты включая contract-compliance.

## Tech Stack

- **Spring Boot:** 4.0.3
- **swagger-parser:** 2.1.27 (парсинг OpenAPI)
- **swagger-request-validator-mockmvc:** 2.44.9 (contract-compliance тестирование)

## Особенности

### SSE Streaming (Server-Sent Events)

Документация по потоковой передаче данных находится в описании `POST /api/chat/send` в `specs/openapi.yaml`.

Фронтенд использует Vercel AI SDK v4 для работы со Stream-response'ами.

### Безопасность (Phase 4.1)

В контракте определена схема `basicAuth`, но **пока не включена в обработку запросов** до фазы реализации Phase 4.1.

Готовьтесь к миграции на Spring Security при добавлении аутентификации.
