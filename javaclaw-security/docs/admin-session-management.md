# Admin Session Management

## Current MVP (self-service only)

Every authenticated user can manage their **own** sessions through the
following endpoints, all contracted in `specs/openapi.yaml` and tagged
`auth`:

| Method |           Path            |               Description               |
|--------|---------------------------|-----------------------------------------|
| GET    | `/api/auth/sessions`      | List active sessions for current user   |
| DELETE | `/api/auth/sessions/{id}` | Revoke a specific session               |
| POST   | `/api/auth/logout-all`    | Revoke **all** sessions of current user |

Implementation lives in `UserSessionService` and `SessionTokenService`
(module `javaclaw-security`). Storage: `user_session` table (V39), one row
per issued cookie. Revocation deletes the row and invalidates the cookie
on the next request via `SessionAuthenticationFilter`.

## Horizontal Scaling Notes

- `lastUsedAt` throttling is performed in-memory
  (`ConcurrentHashMap<String,Instant>` inside `SessionTokenService`). This
  is safe because stale `lastUsedAt` only affects the "inactivity sliding
  window"; it does **not** weaken revocation (revocation always goes
  through the DB).
- For multi-instance deployments, switch the throttle map to Redis (or
  skip throttling — write `lastUsedAt` on every request). The contract
  `SessionTokenService` SPI is stable so implementations can be swapped
  without touching controllers.
- **Risk fixation** (see `specs/auth-module-cookie-session-architecture.md`
  Risk 5): before going horizontally, either implement
  `RedisSessionTokenService` or accept that sessions survive per-node
  cleanup anomalies.

## Roadmap: Admin UI (Phase 15.x)

The goal is to give users with `PERM_SESSION_ADMIN` full visibility and
control over other users' sessions — required for security incident
response (compromised password, lost laptop, offboarding).

### Backend

- [ ] **V44 migration**: add `auth_audit_log.actor_user_id` nullable column
  to distinguish "user revoked own session" from
  "admin X revoked user Y's session".
- [ ] New permission constant `PERM_SESSION_ADMIN` + role mapping (default:
  `ROLE_ADMIN` only). Wire through `PermissionService`.
- [ ] REST endpoints (all `@PreAuthorize("hasAuthority('PERM_SESSION_ADMIN')")`):
  - `GET /api/admin/users/{userId}/sessions` — list all sessions of a
    target user with `ipAddress`, `userAgent`, `createdAt`, `lastUsedAt`.
  - `DELETE /api/admin/users/{userId}/sessions/{sessionId}` — revoke one.
  - `DELETE /api/admin/users/{userId}/sessions` — revoke all (force
    logout). Emit `auth_audit_log` with
    `event='admin_logout_all'` and `actor_user_id`.
  - `GET /api/admin/sessions?page=&size=&filter=` — global paginated
    session listing with filters `userId`, `ipAddress`, `activeOnly`.
- [ ] `AuthAuditController.list(...)` — paginated audit log view with
  filters `{event, userId, actorUserId, from, to}`; backed by
  `auth_audit_log` table.

### Observability / Analytics

- [ ] `GET /api/admin/sessions/stats` — returns:
  - `activeSessions` (total count from `user_session`),
  - `concurrentSessionsByUser` (top 20 users by active session count),
  - `loginsByHour` (last 24h, grouped),
  - `failedLoginsByHour` (last 24h, grouped).
- [ ] Prometheus metrics already emitted by
  `SessionTokenService`: `javaclaw_sessions_active`,
  `javaclaw_login_success_total`, `javaclaw_login_failure_total`.

### Frontend (React SPA)

- [ ] New admin route `/admin/sessions`:
  - Users tab — searchable list, expand-row shows sessions, per-row
    "Logout all devices" button.
  - Audit tab — paginated `auth_audit_log` with filter chips.
  - Stats tab — small dashboard (sparkline, top users).
- [ ] Reuse existing admin shell (`AdminLayout`) and permission guard
  `<RequirePermission perm="PERM_SESSION_ADMIN">`.

### Tests

- [ ] Unit: `SessionAdminControllerTest` — permission denial for non-admin,
  revoke-all semantics, audit event emission.
- [ ] Integration: `SessionAdminIT` — full flow, verifies that target
  user's next request returns `401` after admin-forced logout.
- [ ] E2E: `AdminSessionManagementE2ETest` — admin UI forces user B out;
  user B's SPA tab receives `401` on next request and redirects to login.

## Acceptance Criteria

- [ ] Admin can see every active session across the system with IP + UA.
- [ ] Admin can kill a single session or all sessions of any user.
- [ ] Every admin action is captured in `auth_audit_log` with
  `actor_user_id` populated.
- [ ] Self-service endpoints (`/api/auth/sessions`,
  `/api/auth/logout-all`) continue to work and are unaffected by the
  admin endpoints.
- [ ] `SessionTokenService` SPI unchanged — admin backend reuses existing
  `revoke(sessionId)` and `revokeAllForUser(userId)` methods.

