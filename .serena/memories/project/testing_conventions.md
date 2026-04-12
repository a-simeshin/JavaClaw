# Testing Conventions

## Philosophy

- Integration tests (real DB via Testcontainers) = primary safety net
- Unit tests with mocks = edge cases + coverage boost to 80%+ JaCoCo

## CRITICAL RULES

1. **Write test → run immediately → fix until green** (never leave broken tests)
2. **Both dialects**: Every integration test must pass on PostgreSQL AND SQLite (`-Dtest.dialect=sqlite`)
3. **E2E only at end**: Browser/Playwright tests only when logic + DB + UI all ready together

## Test Structure

- **Naming**: `method_condition_expectedResult` (e.g., `createOrder_withValidItems_returns201`)
- **Given-When-Then**: Comments `// given`, `// when`, `// then` are MANDATORY
- **AssertJ**: Fluent assertions only (not JUnit assertEquals)
- **@Nested classes**: Group tests by method/scenario with `@DisplayName`

## Integration Tests

- Base class: `IntegrationTestBase` in `javaclaw-app/src/test/java/ai/javaclaw/integration/`
- Annotations: `@SpringBootTest(MOCK)`, `@AutoConfigureMockMvc`, `@ActiveProfiles(resolver=...)`
- Profile resolver: `IntegrationTestProfileResolver` — picks `contracttest` (PG) or `sqlite` based on `test.dialect`
- Security: `TestSecurityConfig` — all MockMvc requests default to ROLE_ADMIN
- PostgreSQL: Testcontainers via TC JDBC driver (`application-contracttest.yaml`)
- SQLite: Temp file per process (`jdbc:sqlite:/tmp/javaclaw-it-<pid>.db`)

## E2E Tests

- Located in `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/`
- Playwright-based browser tests in `e2e/playwright/`
- Live API tests in `e2e/integration/`
- Frontend Playwright config in `javaclaw-frontend/playwright.config.ts`

## Priority

1. Positive scenarios (happy path) first
2. Critical negative (404, 400, 409, 401/403)
3. Edge cases (unit tests with mocks)

