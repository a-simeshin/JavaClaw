# Task Completion Checklist

When a coding task is completed, verify:

## Java Backend

1. `./mvnw spotless:apply` — format code
2. `./mvnw -pl <module> test` — run tests for changed module
3. If integration tests touched: run on **both dialects**:
   - `./mvnw -pl javaclaw-app test` (PostgreSQL via Testcontainers)
   - `./mvnw -pl javaclaw-app test -Dtest.dialect=sqlite`
4. If new DB columns/tables: add Flyway migration for BOTH `postgresql/` and `sqlite/`
5. Check no compilation warnings in changed files

## Frontend

1. `pnpm lint` — ESLint
2. `pnpm typecheck` — TypeScript compiler check
3. `pnpm test` — Vitest unit tests
4. If UI changes: start dev server, verify in browser

## General

- No secrets committed (.env, credentials)
- Commit message in English, Conventional Commits format
- Plans cover ALL scenarios (no holes left for "later")

