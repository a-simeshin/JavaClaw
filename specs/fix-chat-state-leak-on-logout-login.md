# Plan: Fix chat/conversation state leak across logout → login

## Task Description

After logging out via the header user-menu and logging in as a different user, the main chat panel still renders the previous user's last active conversation (messages, reasoning block, timestamp) — even though the sidebar correctly reloads the new user's own conversation list. Reproduced via CDP on `http://localhost:8080/chat` on 2026-04-11: admin had active conversation `web-525029dd-cd98-4b81-a145-162ceb1e80bb` ("скажи одно слово: тест" → "тест"). After `logout()` + login as `user/user`, the sidebar showed 3 user conversations, but the main area still rendered admin's conversation content. Backend behaves correctly: `GET /api/conversations/{adminConvId}/messages` returns **403 Forbidden** for `user`. This is a **pure frontend state-leak bug** between React SPA sessions.

## Objective

After logout, zero per-user state must remain in the SPA. After a subsequent login (any user — same or different), the app must start from a clean slate: no stale conversation id, no stale messages, no stale notifications/approvals/unread counts, no stale React Query cache. The chat main area must show the empty state until the user explicitly picks or creates a conversation.

## Problem Statement

The logout handler in `javaclaw-frontend/src/components/app-header.tsx:35-38` only calls `useAuth.logout()` (clears `authAtom`) and then SPA-navigates to `/login` via TanStack Router. Because this is an in-memory SPA navigation (not a full page reload), every Jotai atom and every React Query cache entry survives. On the next login, the `/chat` route remounts `<ChatPage>` which reads `activeConversationIdAtom` — still holding the previous user's conversation id — and `useJavaClawChat` immediately re-hydrates the AI SDK hook from the **React Query cache** (`["conversation-messages", <prev-id>]`, `staleTime: 30_000`). The cached messages render instantly, even though the backend would return 403 on a real refetch.

Leaked state surfaces include, at minimum:

- `store/chat.ts` → `activeConversationIdAtom`, `isStreamingAtom`
- `store/conversations.ts` → `conversationSearchAtom`
- `store/notifications.ts` → `notificationQueueAtom`
- `store/tasks.ts` → `pendingApprovalsAtom`, `activeTaskCountAtom`, `unreadNotificationsAtom`
- React Query cache: `["conversations"]`, `["conversation-messages", id]`, `["tasks"]`, `["approvals"]`, `["notifications"]`, `["audit-*"]`, `["mcp-*"]`, `["files-*"]`, etc.
- AI SDK `useChat` internal messages state (keyed by `chatSessionId`) — reset transitively once `activeConversationIdAtom` becomes `null`, because `use-chat.ts` already calls `helpersRef.current.setMessages([])` on that transition.

The 401 auto-redirect path (`api/http.ts:80-81` → `window.location.href = "/login"`) does a **full page reload** and is therefore NOT affected — in-memory state is wiped by the browser. Only the explicit SPA logout path leaks.

## Solution Approach

Introduce a single, centralised `resetClientState()` routine that wipes all per-user client state in one place, and invoke it at every session boundary:

1. **Primary fix (logout)** — before navigating to `/login`, call `resetClientState()`. After this point every atom and React Query cache entry is back at its initial value.
2. **Defense in depth (login)** — call `resetClientState()` inside `useAuth.login()` **before** persisting the new `authAtom`, so that even if the user lands on `/login` with stale state (e.g. opened a second tab, pressed back), a successful login starts with a clean slate.
3. **Defense in depth (session restore)** — if `getMe()` in `useAuth`'s mount effect resolves to a **different** user id than what the app previously observed (tracked in a module-level ref), also call `resetClientState()`. Guards against browser tab reuse where cookie points to a different user than the in-memory atoms.

The reset routine lives in `src/lib/client-reset.ts` and is exposed via a `useResetClientState()` hook that binds Jotai setters + `useQueryClient()`. Jotai atoms are reset by calling their setter with the initial value (we export the initial values alongside each atom to keep them DRY). React Query cache is wiped via `queryClient.clear()` — this is cheaper and safer than per-key invalidation and guarantees no leftover entries.

Rationale for `queryClient.clear()` over `invalidateQueries()`:
- `invalidate` keeps cached data and just marks it stale → a new user can still momentarily see cached data before the refetch runs.
- `clear()` removes entries outright → observers will go into `isLoading`/empty state → no flash of previous user's data.

No backend changes required — the backend already returns 403 correctly.

## Relevant Files

Use these files to complete the task:

- `javaclaw-frontend/src/components/app-header.tsx` — `handleLogout` handler; wire the new hook here (lines 29-38).
- `javaclaw-frontend/src/hooks/use-auth.ts` — `logout()` and `login()` callbacks; add reset call, add user-mismatch detection in the mount effect.
- `javaclaw-frontend/src/store/chat.ts` — `activeConversationIdAtom`, `isStreamingAtom`. Export initial values as named constants.
- `javaclaw-frontend/src/store/conversations.ts` — `conversationSearchAtom`.
- `javaclaw-frontend/src/store/notifications.ts` — `notificationQueueAtom`.
- `javaclaw-frontend/src/store/tasks.ts` — `pendingApprovalsAtom`, `activeTaskCountAtom`, `unreadNotificationsAtom`.
- `javaclaw-frontend/src/hooks/use-chat.ts` — `useJavaClawChat`: already resets AI SDK messages when `conversationId` goes to `null` (lines 137-146) — verify this transition fires on logout.
- `javaclaw-frontend/src/components/chat/chat-page.tsx` — consumer of `activeConversationIdAtom`; verify empty state renders when id is null.
- `javaclaw-frontend/src/api/http.ts` — existing 401 → full reload path (reference; not touched).
- `javaclaw-frontend/src/api/auth.ts` — `logout` endpoint caller (reference; not touched).
- `javaclaw-frontend/src/hooks/__tests__/use-auth.test.tsx` — existing auth test file to extend.
- `javaclaw-frontend/src/test/setup.ts` — Vitest setup.

### New Files

- `javaclaw-frontend/src/lib/client-reset.ts` — `useResetClientState()` hook + `CLIENT_RESET_ATOMS` list. Single source of truth for state wipe.
- `javaclaw-frontend/src/lib/__tests__/client-reset.test.tsx` — unit tests for the hook.

## Team Orchestration

- You operate as the team lead and orchestrate the team to execute the plan.
- You're responsible for deploying the right team members with the right context to execute the plan.
- IMPORTANT: You NEVER operate directly on the codebase. You use `Task` and `Task*` tools to deploy team members to the building, validating, testing, deploying, and other tasks.

### Team Members

- Builder
  - Name: `builder-fe-auth-reset`
  - Role: Implement `useResetClientState()` hook and wire it into logout + login + session-restore paths in the React SPA. Export initial values from stores, update `app-header.tsx` and `use-auth.ts`.
  - Agent Type: `builder`
  - Resume: true

- Builder
  - Name: `builder-fe-tests`
  - Role: Write unit + integration tests for the reset hook and the logout/login flows. Keep AI SDK hook behaviour covered.
  - Agent Type: `builder`
  - Resume: true

- Validator
  - Name: `validator-fe`
  - Role: Run lint, typecheck, vitest, and manual acceptance checklist. Verify no regressions in existing auth/chat tests.
  - Agent Type: `validator`
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

Target file: `javaclaw-frontend/src/lib/__tests__/client-reset.test.tsx`

- `useResetClientState resets activeConversationIdAtom to null` — seed atom with `"web-foo"`, call hook, assert `null`.
- `useResetClientState resets isStreamingAtom to false`.
- `useResetClientState clears conversationSearchAtom`.
- `useResetClientState empties notificationQueueAtom`.
- `useResetClientState empties pendingApprovalsAtom and resets activeTaskCountAtom to 0`.
- `useResetClientState resets unreadNotificationsAtom to {}`.
- `useResetClientState calls queryClient.clear() exactly once` — mock `useQueryClient`, spy on `clear`.
- `CLIENT_RESET_ATOMS list is exhaustive` — test that iterates every exported atom under `src/store/*` (via static import list) and asserts it's included, preventing future atoms from silently escaping the reset.

Target file: `javaclaw-frontend/src/hooks/__tests__/use-auth.test.tsx` (extend existing)

- `logout() triggers resetClientState before navigation` — spy via module mock.
- `login() triggers resetClientState before setAuth` — verify ordering (reset first, then new user state written).
- `mount effect calls reset when getMe returns a different user id than last observed` — seed a fake previous-user ref, assert reset spy is called.
- `mount effect does NOT call reset when getMe returns the same user id` — regression guard.

Target file: `javaclaw-frontend/src/hooks/__tests__/use-chat.test.tsx` (extend existing)

- `useJavaClawChat clears AI SDK messages when activeConversationIdAtom becomes null` — already partially covered; add an explicit assertion that `setMessages([])` was called.

### Integration / API Tests (15%)

All integration tests use React Testing Library + Vitest + Jotai `Provider` with a fresh `createStore()` per test + a fresh `QueryClient` per test. Follow existing patterns from `hooks/__tests__/use-chat.test.tsx` and `components/chat/__tests__/*.test.tsx`.

Target file: `javaclaw-frontend/src/components/__tests__/app-header.test.tsx` (new)

1. `logout click → навигация на /login И полная очистка chat-стейта` — seed `activeConversationIdAtom = "web-foo"`, seed cache `["conversation-messages", "web-foo"]` c массивом сообщений, click "Выйти". Assertions:
   - `authApi.logout` вызван 1 раз
   - `store.get(activeConversationIdAtom) === null`
   - `queryClient.getQueryData(["conversation-messages", "web-foo"]) === undefined`
   - `queryClient.getQueryCache().getAll()` пустой
   - navigation-spy получил `{ to: "/login" }` ПОСЛЕ reset (ordering check через mock call order)
2. `logout при падении /api/auth/logout всё равно чистит локальный стейт` — mock `authApi.logout` → reject, click "Выйти", assert всё тот же cleanup + navigation произошли (try/finally branch).
3. `logout не вызывает reset дважды при повторном клике` — guard от двойных кликов (debounce/disable state, если он есть; иначе — просто подтвердить идемпотентность).

Target file: `javaclaw-frontend/src/hooks/__tests__/use-auth-state-isolation.test.tsx` (new, фокус на изоляцию двух юзеров)

1. `login user B после login user A чистит стейт от user A` — (a) вызов `login("user-a", ...)`, записываем в атомы admin-like данные (`activeConversationIdAtom = "conv-a"`, `notificationQueueAtom = [{taskId:"t-a"}]`), (b) вызов `login("user-b", ...)`, assert что перед `setAuth` произошёл полный reset → атомы в initial, queryClient пустой.
2. `mount-effect обнаруживает смену user id через /api/auth/me` — seed localStorage/ref с `"user-a"`, запустить хук, `getMe()` → `"user-b"`, assert reset + новый user id в authAtom.
3. `mount-effect НЕ вызывает reset при том же user id` — идентичный id → reset spy not called (регрессионный guard, чтобы reload не терял query cache в одного и того же юзера).
4. `mount-effect НЕ вызывает reset при первом логине (stored ref === null)` — holistic: свежий запуск приложения не должен зря дёргать clear().

Target file: `javaclaw-frontend/src/components/chat/__tests__/chat-page.test.tsx` (new, small)

1. `renders empty state when activeConversationIdAtom is null` — guards the contract the fix relies on.
2. `при переходе conversationId "conv-foo" → null main-area ре-рендерится на empty state и не показывает старые сообщения` — точная симуляция SPA-logout без перезагрузки.

Target file: `javaclaw-frontend/src/hooks/__tests__/use-chat.test.tsx` (extend existing)

1. `useJavaClawChat clears AI SDK messages when conversationId → null` — assert `setMessages([])` был вызван (ссылаясь на существующий блок в `use-chat.ts:137-146`).
2. `useJavaClawChat не подтягивает cached messages после queryClient.clear()` — seed cache, `clear()`, смена conversationId → assert `historyQuery.data === undefined` и main area пуста.

### UI E2E Tests (5%)

**Harness есть** — Playwright уже настроен в `javaclaw-frontend/playwright.config.ts`, тесты лежат в `javaclaw-frontend/e2e/*.e2e.ts`, есть существующий pattern route-mocking (`chat-flow.e2e.ts`, `user-isolation.e2e.ts`). Новый e2e тест следует этому же паттерну.

Target file: `javaclaw-frontend/e2e/auth-state-leak.e2e.ts` (new)

Тест #1: **admin → logout → user видит чистый chat (основной регрессионный сценарий этого бага)**

- `beforeEach`: настроить route-моки для admin (`/api/auth/me` → admin, `/api/conversations` → список с 1 диалогом "admin-chat-1" + 2 messages в `/api/conversations/admin-chat-1/messages`), `POST /api/auth/logout` → 204.
- Шаги:
  1. `page.goto("/chat")` → admin видит свой диалог "admin-chat-1" в sidebar, кликает по нему → в main area рендерятся admin messages.
  2. Перехватить route-моки и переназначить их на user ответы (`/api/auth/me` → user, `/api/conversations` → список user'а с диалогом "user-chat-1", `/api/conversations/admin-chat-1/messages` → 403, `/api/conversations/user-chat-1/messages` → user messages).
  3. Открыть header dropdown → клик "Выйти" → assert `URL === /login`.
  4. Login как user через форму (`#username`, `#password`, submit) → assert `URL === /chat`.
  5. **Key assertions (анти-leak)**:
     - В sidebar нет `admin-chat-1`, есть `user-chat-1`.
     - Main area показывает empty state (`ChatEmptyState` locator) — НЕТ текста admin'ских messages.
     - `page.waitForRequest` подтверждает, что **не было** запроса `/api/conversations/admin-chat-1/messages` после login (через request listener + counter).
     - DOM не содержит admin conversation id (`expect(page.locator('body')).not.toContainText("admin-chat-1")`).
  6. Кликнуть в sidebar user-chat-1 → видны именно user messages.

Тест #2: **прямой re-login без logout (user A → user B через /api/auth/me смену)** — defense in depth для mount-effect branch.

- Шаги: login as admin → создать чат → вручную вызвать logout endpoint через `page.evaluate(fetch(...))` без SPA-navigation, затем `page.reload()` с перехватом `/api/auth/me` → user → assert main area empty после reload (регрессионный guard на mount-effect reset).

Тест #3: **сессия закончилась → 401 redirect делает полный reload и не оставляет stale state** — регрессия для альтернативного auth path.

- Шаги: login as admin → создать чат → заменить `/api/chat/send` на 401 → отправить сообщение → assert navigated to `/login` through `window.location.href` (full reload, не SPA) → login как user → main area пуста.

**Login helper** — добавить `e2e/helpers/auth.ts` с функцией `loginAs(page, { username, password, user, conversations, messagesByConvId })` чтобы избежать копипасты между тремя тестами. Переиспользует существующие паттерны `bypassAuth` из `chat-flow.e2e.ts` и `setupRoutesForUser` из `user-isolation.e2e.ts`.

**Запуск:** `cd javaclaw-frontend && pnpm e2e` (требует работающий backend через `BASE_URL=http://localhost:8080`, иначе Playwright сам поднимет `pnpm dev` на 5173 согласно `webServer` в `playwright.config.ts`).

## Step by Step Tasks

### 1. Extract atom initial values and build the reset hook

- **Task ID**: build-reset-hook
- **Depends On**: none
- **Assigned To**: builder-fe-auth-reset
- **Agent Type**: builder
- **Stack**: React hook useState useEffect tsx component
- **Parallel**: false
- **Tests**: Unit: `client-reset.test.tsx` — every atom reset path + `queryClient.clear()` spy + exhaustiveness check.
- Add `export const ACTIVE_CONVERSATION_INITIAL = null` (and similar) next to each atom in `store/chat.ts`, `store/conversations.ts`, `store/notifications.ts`, `store/tasks.ts`. Keep atoms using those constants.
- Create `src/lib/client-reset.ts` exporting `useResetClientState(): () => void` which:
  - Calls `useSetAtom` for every per-user atom listed above.
  - Calls `useQueryClient()`.
  - Returns a stable `useCallback` that sets each atom to its initial value and then calls `queryClient.clear()`.
- Export a `CLIENT_RESET_ATOMS` array (atom → initial value pairs) so tests can iterate it.

### 2. Wire reset into logout handler

- **Task ID**: wire-logout
- **Depends On**: build-reset-hook
- **Assigned To**: builder-fe-auth-reset
- **Agent Type**: builder
- **Stack**: React hook component tsx
- **Parallel**: false
- **Tests**: Integration: `app-header.test.tsx` — logout click clears atom + query cache + navigates.
- In `components/app-header.tsx`: import `useResetClientState`, call it at the top of `AppHeader`, make `handleLogout` async:
  ```tsx
  const resetClientState = useResetClientState()
  const handleLogout = async () => {
    await logout()
    resetClientState()
    void navigate({ to: "/login" })
  }
  ```
- Ensure `logout()` rejection does not block the reset (wrap in `try/finally` — a failed server logout still requires local state to be wiped).

### 3. Wire reset into login + session-restore paths

- **Task ID**: wire-login-restore
- **Depends On**: build-reset-hook
- **Assigned To**: builder-fe-auth-reset
- **Agent Type**: builder
- **Stack**: React hook useEffect tsx
- **Parallel**: true
- **Tests**: Unit: `use-auth.test.tsx` — login reset ordering + mount-effect user-mismatch detection.
- In `hooks/use-auth.ts`:
  - Import `useResetClientState`; call it at the top.
  - In the `login` callback: call `resetClientState()` **before** `setAuth({ user: response.user, ... })`.
  - In the mount effect: keep a `useRef<string | null>` of the last-observed user id. On `getMe()` success, if the new id differs from the stored one AND the stored one was non-null, call `resetClientState()` before `setAuth(...)`. Update ref to the new id afterwards.

### 4. Verify chat-page empty state transition

- **Task ID**: verify-chat-empty
- **Depends On**: build-reset-hook
- **Assigned To**: builder-fe-auth-reset
- **Agent Type**: builder
- **Stack**: React component useEffect tsx
- **Parallel**: true
- **Tests**: Integration: `chat-page.test.tsx` — null id → empty state.
- Audit `components/chat/chat-page.tsx` to confirm it renders `<ChatEmptyState>` when `activeConversationIdAtom` is `null` and there are no in-memory messages. If there is any memoisation or ref that could hold stale derived state, add a reset keyed on `conversationId` going to `null`.
- Confirm `useJavaClawChat` `useEffect` at `hooks/use-chat.ts:137-146` actually fires on the `conversationId → null` transition post-reset (it should — adding a test in task 5 locks this).

### 5. Write unit + integration tests

- **Task ID**: write-unit-integration-tests
- **Depends On**: build-reset-hook, wire-logout, wire-login-restore, verify-chat-empty
- **Assigned To**: builder-fe-tests
- **Agent Type**: builder
- **Stack**: React jest testing-library tsx vitest hook useState useEffect
- **Parallel**: true
- Unit (80%):
  - `src/lib/__tests__/client-reset.test.tsx` — все кейсы из Testing Strategy: каждый атом, queryClient.clear() spy, exhaustiveness-check через iteration над `CLIENT_RESET_ATOMS`.
  - Extend `src/hooks/__tests__/use-auth.test.tsx` — login/logout reset ordering assertions.
  - Extend `src/hooks/__tests__/use-chat.test.tsx` — `conversationId → null` → `setMessages([])` и `queryClient.clear()` → empty historyQuery.
- Integration (15%):
  - `src/components/__tests__/app-header.test.tsx` — все 3 кейса (успех, падение /logout, двойной клик).
  - `src/hooks/__tests__/use-auth-state-isolation.test.tsx` — все 4 кейса mount-effect + login reset.
  - `src/components/chat/__tests__/chat-page.test.tsx` — оба кейса empty state.
- Следовать существующим тест-конвенциям: React Testing Library, Vitest, Jotai `Provider` с `createStore`, `QueryClientProvider` wrapper. Мокать `authApi.logout` / `authApi.login` / `authApi.getMe` через `vi.mock`.

### 6. Write Playwright e2e tests

- **Task ID**: write-e2e-tests
- **Depends On**: build-reset-hook, wire-logout, wire-login-restore, verify-chat-empty
- **Assigned To**: builder-fe-tests
- **Agent Type**: builder
- **Stack**: React tsx playwright e2e testing
- **Parallel**: true
- Создать `javaclaw-frontend/e2e/helpers/auth.ts` с переиспользуемой функцией `loginAs(page, userFixture)` — route mocks для `/api/auth/me`, `/api/auth/login`, `/api/auth/logout`, `/api/conversations*`, `/api/conversations/*/messages`, `/api/tasks*`, `/api/notifications*`, `/api/approvals*`. Следовать паттерну `e2e/chat-flow.e2e.ts::bypassAuth` + `e2e/user-isolation.e2e.ts::setupRoutesForUser`.
- Определить фикстуры: `ADMIN_FIXTURE` (с `admin-chat-1` + 2 messages), `USER_FIXTURE` (с `user-chat-1` + 1 message), ответ 403 на чужие endpoints.
- Создать `javaclaw-frontend/e2e/auth-state-leak.e2e.ts` с тремя тестами из Testing Strategy:
  1. `admin → logout → user видит чистый chat` — основной регрессионный кейс.
  2. `mount-effect reset при смене user id через /api/auth/me + reload`.
  3. `401 full reload path остаётся чистым`.
- В каждом тесте assertions:
  - Sidebar содержит только диалоги нового пользователя.
  - Main area — `ChatEmptyState` локатор виден, admin-текста нет.
  - `page.on("request")` counter подтверждает отсутствие запросов к чужим conversation IDs.
  - DOM не содержит admin conversation id.
- Проверить локально: `pnpm e2e --project=chromium e2e/auth-state-leak.e2e.ts`. Если нужен работающий backend — использовать `BASE_URL=http://localhost:8080 pnpm e2e`.

### 7. Final validation

### 6. Final validation

- **Task ID**: validate-all
- **Depends On**: build-reset-hook, wire-logout, wire-login-restore, verify-chat-empty, write-unit-integration-tests, write-e2e-tests
- **Assigned To**: validator-fe
- **Agent Type**: validator
- **Stack**: React vitest tsx playwright e2e
- **Parallel**: false
- Run: `cd javaclaw-frontend && pnpm lint && pnpm typecheck && pnpm test --run`.
- Run Playwright: `cd javaclaw-frontend && pnpm e2e e2e/auth-state-leak.e2e.ts`. При необходимости — `BASE_URL=http://localhost:8080 pnpm e2e`.
- Verify all new + existing vitest and playwright tests pass (в т.ч. существующий `user-isolation.e2e.ts` не должен сломаться).
- Manually reproduce the original CDP scenario: login as admin → start chat → get response → logout → login as user → confirm main area shows empty state (no admin message, no admin conversation id in state).
- Verify `/api/auth/me` still works normally and that refresh-after-login still restores session cleanly.
- Confirm no regression in `use-chat.test.tsx`, `chat-flow.e2e.ts`, `user-isolation.e2e.ts` и existing chat UI tests.

## Acceptance Criteria

- After clicking logout, navigating to `/login`, and logging in as any user (same or different), the `/chat` main area renders the empty state — NOT the previous user's messages.
- `activeConversationIdAtom` is `null` immediately after logout.
- React Query cache contains zero entries immediately after logout (`queryClient.getQueryCache().getAll().length === 0`).
- All per-user Jotai atoms (`conversationSearchAtom`, `notificationQueueAtom`, `pendingApprovalsAtom`, `activeTaskCountAtom`, `unreadNotificationsAtom`, `isStreamingAtom`) return to their declared initial values immediately after logout.
- New unit tests prove exhaustiveness: if a future contributor adds a new per-user atom without registering it in `CLIENT_RESET_ATOMS`, a test fails.
- `pnpm lint`, `pnpm typecheck`, `pnpm test --run` all green.
- No backend changes.
- No regressions in existing `use-auth.test.tsx`, `use-chat.test.tsx`, `chat-page` tests.

## Validation Commands

- `cd javaclaw-frontend && pnpm lint`
- `cd javaclaw-frontend && pnpm typecheck` (or `pnpm tsc --noEmit`)
- `cd javaclaw-frontend && pnpm test --run`
- Manual CDP repro: login admin → send "test" → receive reply → header menu → "Выйти" → login `user/user` → navigate `/chat` → expect empty state (no "скажи одно слово: тест" / "тест" in main area) → also check `/api/conversations/{prevId}/messages` is NOT called (or returns 403 if it is).

## Notes

- **Scope**: этот план покрывает ТОЛЬКО утечку chat-state (conversations + messages + notifications + approvals + tasks counters) в рамках данного бага. Утечки на страницах Skills / Admin / Files / MCP / Cron / Logs / Audit будут отдельным планом поверх `CLIENT_RESET_ATOMS` (там добавятся новые атомы и e2e сценарии для каждой фичи). Но поскольку reset построен через `queryClient.clear()` + exhaustiveness-тест, добавление новых атомов автоматически подхватится будущими планами без перекомпоновки архитектуры.
- Keep the fix **frontend-only**. Backend already correctly returns 403 — do NOT modify authorization there.
- `queryClient.clear()` is a hard wipe and will cancel any in-flight requests associated with the old user — this is the desired behaviour at a session boundary.
- Jotai outside-of-React resets would require `getDefaultStore()`; we stick to in-hook setters for clarity and testability. Both logout and login sites are already inside React components, so no out-of-React call site exists.
- If `pnpm typecheck` is not defined in `package.json` scripts, use `pnpm exec tsc --noEmit` as a fallback.
- The exhaustiveness test in `client-reset.test.tsx` is the key architectural defence — it prevents this class of bug from silently reappearing when new per-user atoms are added.
- 401 auto-redirect path (`api/http.ts`) does a full page reload and is naturally safe; no changes needed there, but add a one-line code comment noting that it intentionally relies on browser wipe.
