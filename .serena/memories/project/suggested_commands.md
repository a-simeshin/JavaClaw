# Suggested Commands

## Build & Run

```bash
# Full build (compile + test + quality checks)
./mvnw clean verify

# Build without tests (fast compile check)
./mvnw clean package -DskipTests

# Run application (PostgreSQL profile, default)
./mvnw -pl javaclaw-app spring-boot:run

# Run with SQLite debug profile
./mvnw -pl javaclaw-app spring-boot:run -Dspring-boot.run.profiles=debug

# Start dev PostgreSQL
docker compose -f docker-compose.dev.yml up -d
```

## Testing

```bash
# Run all unit+integration tests (PostgreSQL via Testcontainers)
./mvnw test

# Run tests for specific module
./mvnw -pl javaclaw-core test
./mvnw -pl javaclaw-app test

# Run single test class
./mvnw -pl javaclaw-app test -Dtest=TaskApiIntegrationTest

# Run tests on SQLite dialect
./mvnw test -Dtest.dialect=sqlite

# IMPORTANT: Always run tests on BOTH dialects (postgres + sqlite)
```

## Frontend

```bash
cd javaclaw-frontend

# Install deps
pnpm install

# Dev server
pnpm dev

# Build
pnpm build

# Lint + typecheck
pnpm lint
pnpm typecheck

# Unit tests
pnpm test

# E2E tests (Playwright)
pnpm e2e
pnpm e2e:ui  # interactive mode
```

## Code Quality

```bash
# Format (Spotless - Palantir Java Format)
./mvnw spotless:apply

# Check formatting
./mvnw spotless:check

# PMD static analysis
./mvnw pmd:check

# SpotBugs
./mvnw spotbugs:check
```

## Git

```bash
git status
git log --oneline -20
git diff
```

## System Utils (macOS / Darwin)

```bash
ls, find, grep → use Serena tools or Glob/Grep instead
open <file>   # open in default app
pbcopy/pbpaste # clipboard
```

