# Known Pre-existing Flaky / Broken Tests

Discovered 2026-04-13 on `develop` (commit 914760c) — confirmed pre-existing via `git stash` of the memory auto-config work. **Not caused by our changes. Safe to ignore when running core / app smoke.**

## 1. Timezone midnight race — `AgentQuotaQueryRepositorySqliteImplTest.resetDailyZeroesUsageAndStampsToday`

**Location:** `javaclaw-core/src/test/java/ai/javaclaw/persistence/sqlite/AgentQuotaQueryRepositorySqliteImplTest.java:66`

**Symptom:** `expected: "2026-04-13" but was: "2026-04-12"` (or similar date off-by-one).

**Root cause:** test asserts `reset_date == LocalDate.now()` (JVM local time, MSK / Europe/Moscow), but `resetDaily` repository writes `CURRENT_DATE` / `DATE('now')` in SQLite (UTC). When local time is after midnight MSK but before midnight UTC (i.e. 00:00–03:00 MSK), the two values differ by one day → flake.

**Fix direction (not applied):** either force the repo to use local-date (Java side) or switch the assertion to `LocalDate.now(ZoneOffset.UTC)`. The Postgres twin at `AgentQuotaQueryRepositoryPgImplTest.java:73` has the same shape — fix both in lockstep.

**Workaround:** run tests outside the 00:00–03:00 MSK window, or skip explicitly via `-Dtest='!AgentQuotaQueryRepositorySqliteImplTest#resetDailyZeroesUsageAndStampsToday'`.

## 2. Flyway SQLite migration count drift — `SqliteSmokeApplicationTest.flywayAppliedAllSqliteMigrations`

**Location:** `javaclaw-app/src/test/java/ai/javaclaw/SqliteSmokeApplicationTest.java:97`

**Symptom:** `Expecting actual: 39 to be greater than or equal to: 40`

**Root cause:** hardcoded expected minimum `>= 40` no longer matches the actual SQLite migration chain after the recent squash refactor (commits 9a96896 "squash chat memory migrations", 7fb18fc "remove duplicate and orphaned chat memory SQL files"). The squash reduced count by one; the assertion lower bound was not updated.

**Fix direction (not applied):** either drop the magic number and assert a real invariant (e.g. "all V1..Vn present in order, no gaps"), or bump the minimum to `39`. Prefer the invariant form so future squashes don't break it again.

**Workaround:** none other than skipping the specific test.

## Bottom line

These two failures are orthogonal to the `javaclaw-memory` / `ChatMemory` work. Any smoke / full-build run started in the MSK late-night window OR after the migration squash will report 1–2 failures on the above tests. Do **not** treat these as regressions of memory auto-config changes.
