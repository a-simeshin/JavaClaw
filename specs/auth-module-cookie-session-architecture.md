# Plan: javaclaw-security — кардинально новый модуль аутентификации/авторизации

## Task Description

Спроектировать и реализовать полностью новый Maven-модуль `javaclaw-security`, заменяющий текущую реализацию HTTP Basic + `localStorage` креды. Новый модуль должен:

1. **Аутентифицировать пользователя** через форму `{username, password}` → opaque session-token в httpOnly cookie, хранящийся в БД (`user_session`).
2. **Авторизовывать** запросы через Spring Security (`PermissionEvaluator` поверх существующего `PermissionService`) и URL-level `.hasAuthority(PERM_*)`.
3. **Работать с SSE** (`EventSource`) без костылей: cookie отправляется автоматически.
4. **Не ломать контракт JavaClaw будущего**: слои, SPI и расширяемость заложены сразу под OIDC (Relying Party), TOTP/Email/SMS/WebAuthn 2FA, без переписывания API при их добавлении.
5. **Сопровождаться** unit + integration + Playwright E2E тестами по пирамиде 80/15/5.
6. **Big-bang cutover**: старый `javaclaw-app/.../security`, `frontend/store/auth.ts`, Basic-based E2E — удаляются. Никакого `@Deprecated`.

## Objective

После завершения:

- Нет ни одного `Authorization: Basic …`, ни одного обращения к `localStorage.getItem('javaclaw.auth.*')`, ни одного `?auth=` в SSE URL.
- `SecurityConfig` больше не содержит `.httpBasic(...)`.
- Пользователь логинится через `POST /api/auth/login`, получает `Set-Cookie: JCLAW_SESSION=...; HttpOnly; Secure; SameSite=Lax`, cookie хранится 24 часа (fixed TTL).
- Открытие диалога в UI и завершение стрима ЛЛМ **не вызывают** нативного браузерного prompt'а (устраняется баг #41).
- `user_session` в PostgreSQL хранит активные сессии; `POST /api/auth/logout-all-devices` реально обнуляет их.
- AuthZ на `conversation/task/skill/mcp` реализована через `@PreAuthorize("hasPermission(#id, 'conversation', 'read')")` с кастомным `PermissionEvaluator`, делегирующим на `PermissionService`.
- 2FA SPI (`SecondFactorProvider`) заложена, `NoopSecondFactorProvider` включён по умолчанию — будущий `TotpSecondFactorProvider` не потребует менять контроллер логина.
- OIDC-ветка документирована в `javaclaw-security/docs/oidc-relying-party-roadmap.md` с конкретным чек-листом интеграции через Spring Security `oauth2Login()`.
- Все 1187+ существующих тестов зелёные; добавлены новые unit/integration/E2E тесты (см. Testing Strategy).

## Problem Statement

**Текущее состояние** (зафиксировано на develop @ d1bc956, Phase 13 RBAC complete):

- Frontend хранит `btoa(user:pass)` в `localStorage.javaclaw.auth.credentials`; `http.ts#buildAuthHeaders()` ставит `Authorization: Basic ...` на каждый запрос.
- Backend: `SecurityConfig` (`javaclaw-app/src/main/java/ai/javaclaw/security/SecurityConfig.java`) использует `httpBasic(withDefaults())`, `STATELESS`, дефолтный `BasicAuthenticationEntryPoint`, который ставит `WWW-Authenticate: Basic realm="…"`.
- `JdbcUserDetailsService` строит `UserDetails` со scopes `ROLE_*` + `PERM_*`.
- SSE endpoint `/api/chat/notifications/{id}` (`NotificationController.java:19-22`) защищён `.authenticated()`; фронт пытается передать auth через `?auth=Basic%20...`, но бэкенд этот query-параметр никак не разбирает → запрос уходит без `Authorization` → `BasicAuthenticationEntryPoint` возвращает `401` + `WWW-Authenticate: Basic`, браузер показывает **нативный модальный prompt**. Это баг открытого тикета.
- `PermissionService` вызывается напрямую из контроллеров (в `ConversationController`, `SkillController`, `ChatRestController`) — разбросано, без единого декларативного стиля.
- E2E (`PlaywrightE2ETestBase.loginViaStorage()`, `.login()`) явно пишет Basic creds в `localStorage` — любое изменение auth-контракта требует ручного обновления базового класса.
- 2FA/OIDC — не заложено никак, при попытке добавить придётся ломать контракт login-flow.

**Боли**:

1. Браузерный prompt нельзя выключить без перепроектирования auth.
2. Пароль в `localStorage` = XSS-инъекция читает пароль, не только сессию.
3. Нет революк/аудита сессий: administrator не может «разлогинить всех».
4. SSE-auth — костыль на костыле.
5. AuthZ расползается по контроллерам, не центализована.
6. Нет структуры под OIDC/2FA — расширение потребует big-rewrite.

## Solution Approach

### Концепция: три независимых слоя

```
┌────────────────────────────────────────────────────────────────┐
│  Слой 1. Primary Authentication (как доказать, кто ты)         │
│  - Local: username+password → Argon2id (MVP)                   │
│  - Future: OIDC Relying Party (Keycloak/Yandex ID)             │
│  - Future: 2FA challenge (TOTP/Email/SMS/WebAuthn)             │
│  - SPI: AuthenticationProvider (Spring) + SecondFactorProvider │
└────────────────────────────────────────────────────────────────┘
                            ↓ выдаёт Authentication
┌────────────────────────────────────────────────────────────────┐
│  Слой 2. Session Carrier (как ходить с identity по запросам)   │
│  - Opaque token (32 random bytes, base64url) в httpOnly cookie │
│  - SHA-256 hash в DB (таблица user_session)                    │
│  - Fixed 24h TTL, revoke через DB UPDATE                       │
│  - SPI: SessionTokenService                                    │
│  - Реализация: OpaqueSessionTokenService (MVP, DB-backed)      │
│  - Future: JwtSessionTokenService (за тем же SPI)              │
└────────────────────────────────────────────────────────────────┘
                            ↓ резолвит Authentication per request
┌────────────────────────────────────────────────────────────────┐
│  Слой 3. Authorization (что тебе можно)                        │
│  - URL-level: .hasAuthority("PERM_*") в SecurityFilterChain    │
│  - Method-level: @PreAuthorize(hasPermission(#id, type, act))  │
│  - Бридж: JavaClawPermissionEvaluator → PermissionService      │
│  - AuthorityExtractor маппит role_permissions → GrantedAuth    │
└────────────────────────────────────────────────────────────────┘
```

**Ключевой инвариант**: никакой код за пределами слоя 1 **не знает**, как именно аутентифицировался пользователь. Слой 2 не знает про пароли, слой 3 не знает про cookie. Переход на OIDC = подключение `OAuth2LoginConfigurer` в SecurityFilterChain, успешный callback дёргает тот же `SessionTokenService.issue(authentication)` — остальной код не трогается.

### Flow: Login

```
Browser                      AuthController           AuthenticationService    SessionTokenService    user_session DB
   │                                │                          │                        │                    │
   │─POST /api/auth/login──────────▶│                          │                        │                    │
   │   {username, password}         │                          │                        │                    │
   │                                │─authenticate(u,p)───────▶│                        │                    │
   │                                │                          │─DaoAuthProvider────┐   │                    │
   │                                │                          │◀───UserDetails─────┘   │                    │
   │                                │                          │─SecondFactor.check()   │                    │
   │                                │                          │     → NONE (MVP)       │                    │
   │                                │◀──Authentication─────────│                        │                    │
   │                                │─issue(auth, ip, ua)──────────────────────────────▶│                    │
   │                                │                          │                        │─INSERT session────▶│
   │                                │                          │                        │◀───id, raw_token───│
   │                                │◀─────cookie value────────────────────────────────│                    │
   │                                │─auditService.logLoginSuccess()                    │                    │
   │◀─200 + Set-Cookie: JCLAW_SESSION=<raw_token>; HttpOnly; Secure; SameSite=Lax        │                    │
   │  Body: {user: {...}, sessionExpiresAt}                                              │                    │
```

### Flow: последующий запрос (REST или SSE)

```
Browser                SessionCookieAuthFilter    SessionTokenService     Controller
   │                           │                          │                    │
   │─GET /api/conversations   │                          │                    │
   │  Cookie: JCLAW_SESSION=..─▶                          │                    │
   │                           │─resolve(token)──────────▶│                    │
   │                           │                          │─sha256 + SELECT──┐ │
   │                           │                          │◀─UserSession+User┘ │
   │                           │◀──Authentication─────────│                    │
   │                           │─SecurityContextHolder.set()                  │
   │                           │─filterChain.doFilter()─────────────────────▶│
   │                           │                                              │─@PreAuthorize─┐
   │                           │                                              │◀───────────────┘
   │◀──200 + JSON──────────────────────────────────────────────────────────────│
```

SSE работает ровно так же — браузер автоматически добавляет cookie к `EventSource` запросу, фильтр не различает REST и SSE.

### Почему opaque token, а не JWT

|         Критерий         |                  Opaque + DB (выбрано)                   |              JWT               |
|--------------------------|----------------------------------------------------------|--------------------------------|
| Revoke сессии            | `UPDATE user_session SET revoked_at=NOW()` — моментально | blacklist/короткий TTL+refresh |
| Размер cookie            | 43 байта                                                 | 500+ байт                      |
| Ротация ключей           | нет ключей                                               | обязательна                    |
| Админ-UI «кто залогинен» | простой `SELECT`                                         | требует отдельной таблицы      |
| XSS token leak           | можно revoke сразу                                       | живёт до expiry                |
| Horizontal scale         | работает через общую БД                                  | работает из коробки            |
| Будущее                  | Swap реализации `SessionTokenService` на JWT — бесплатно | —                              |

Opaque выбран, потому что (а) у нас уже PostgreSQL, (б) revoke/audit критичны для enterprise, (в) SPI изолирует от JWT-специфики.

### Почему Argon2id

OWASP 2024 рекомендация. Spring Security содержит `Argon2PasswordEncoder` (требует BouncyCastle). Используется через `DelegatingPasswordEncoder` с id `argon2` как default; старые bcrypt-хэши всё ещё валидируются и при успешном логине **автоматически перехешируются** через `PasswordEncoder.upgradeEncoding()` hook в `DaoAuthenticationProvider`. Миграция паролей — прозрачная для пользователя.

### Почему CSRF включён, SameSite=Lax

- **SameSite=Strict** ломает некоторые OIDC callback-сценарии (редирект с IdP домена) — заложимся на Lax сейчас, чтобы future-OIDC не требовал перенастройки cookie.
- `CookieCsrfTokenRepository.withHttpOnlyFalse()` — cookie `XSRF-TOKEN` читается JS, фронт ставит `X-XSRF-TOKEN` в header. Двойная защита.
- Login endpoint (`POST /api/auth/login`) исключён из CSRF, т.к. это bootstrap. Остальные mutating endpoints проверяются.

## Relevant Files

### Файлы для ЧТЕНИЯ (baseline)

- `javaclaw-app/src/main/java/ai/javaclaw/security/SecurityConfig.java` — текущая конфигурация (будет удалена).
- `javaclaw-app/src/main/java/ai/javaclaw/security/JdbcUserDetailsService.java` — переедет в `javaclaw-security`.
- `javaclaw-app/src/main/java/ai/javaclaw/api/SystemController.java` — `/api/me` переедет в `AuthController`.
- `javaclaw-core/src/main/java/ai/javaclaw/users/AppUser.java` + `AppUserRepository.java` — остаются как есть.
- `javaclaw-core/src/main/java/ai/javaclaw/users/Permission.java` — enum, 41 значение.
- `javaclaw-core/src/main/java/ai/javaclaw/users/PermissionService.java` — не трогаем; оборачиваем в `PermissionEvaluator`.
- `javaclaw-core/src/main/java/ai/javaclaw/users/UserResolver.java` — остаётся, используется `PermissionEvaluator`.
- `javaclaw-core/src/main/resources/db/migration/V4__create_users.sql` — таблица users.
- `javaclaw-core/src/main/resources/db/migration/V29__create_auth_audit_log.sql` — auth_audit_log.
- `javaclaw-core/src/main/resources/db/migration/V31__create_role_permissions.sql` — role_permissions.
- `javaclaw-core/src/main/resources/db/migration/V32__create_custom_roles.sql` — custom_roles.
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/controller/NotificationController.java` — SSE endpoint, будет просто `.authenticated()`.
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/controller/ConversationController.java` — `@PreAuthorize` рефакторинг.
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/controller/ChatRestController.java` — аналогично.
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillController.java` — аналогично.
- `javaclaw-frontend/src/store/auth.ts` — полностью переписывается.
- `javaclaw-frontend/src/api/http.ts` — `buildAuthHeaders` удалить, добавить `credentials: "include"` + CSRF header.
- `javaclaw-frontend/src/hooks/use-auth.ts` — переписывается под `/api/auth/login`.
- `javaclaw-frontend/src/hooks/use-task-notifications.ts` — убрать `?auth=`.
- `javaclaw-frontend/src/components/auth/login-form.tsx` — вызывает новый `useAuth`.
- `javaclaw-frontend/src/routes/login.tsx` — почти без изменений.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/support/PlaywrightE2ETestBase.java` — `loginViaStorage()` → `loginViaApi()`.
- `pom.xml` (корень) — добавить `<module>javaclaw-security</module>`.
- `javaclaw-app/pom.xml` — заменить implicit security dep на `<dependency>javaclaw-security</dependency>`.

### New Files

**Maven-модуль** `javaclaw-security/`:

- `pom.xml` — parent, deps: spring-boot-starter-security, starter-webmvc, starter-data-jdbc, javaclaw-core, bouncycastle-provider, flyway-core.
- `src/main/resources/db/migration/V39__create_user_session.sql` — новая таблица сессий.
- `src/main/resources/db/migration/V40__extend_auth_audit_event_types.sql` — расширить допустимые `event_type` (session_created, session_revoked, logout, logout_all).
- `src/main/resources/application-security.yaml` — дефолты `javaclaw.security.session.*`, `javaclaw.security.cookie.*`.

**Package `ai.javaclaw.security.config`**:

- `SecurityConfig.java` — главный `SecurityFilterChain` bean.
- `AuthenticationConfig.java` — `AuthenticationManager`, `DaoAuthenticationProvider`, `PasswordEncoder` (Argon2id default, bcrypt legacy).
- `MethodSecurityConfig.java` — `@EnableMethodSecurity`, регистрация `PermissionEvaluator`.
- `CookieProperties.java` — `@ConfigurationProperties("javaclaw.security.cookie")`: name, path, domain, secure, sameSite, maxAgeSeconds.
- `SessionProperties.java` — ttl (24h), cleanupInterval.
- `ApiAuthenticationEntryPoint.java` — `HttpStatusEntryPoint(UNAUTHORIZED)` без `WWW-Authenticate`.

**Package `ai.javaclaw.security.session`**:

- `UserSession.java` — record: id, tokenHash, userId, createdAt, expiresAt, lastUsedAt, remoteAddr, userAgent, revokedAt, revocationReason.
- `UserSessionRepository.java` — Spring Data JDBC: `findByTokenHashAndRevokedAtIsNull`, `findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter`, `revokeById`, `revokeAllByUserId`, `deleteExpired`.
- `SessionTokenService.java` — SPI: `IssuedToken issue(Authentication, ClientInfo, Duration)`, `Authentication resolve(String rawToken)`, `void revoke(String rawToken, String reason)`, `int revokeAllForUser(String userId, String reason)`.
- `IssuedToken.java` — record: rawToken, sessionId, expiresAt.
- `ClientInfo.java` — record: remoteAddr, userAgent.
- `OpaqueSessionTokenService.java` — impl DB-backed: генерация `SecureRandom(32)`, `SHA-256` hash, upsert в `user_session`. Throttle `lastUsedAt` (обновлять не чаще 1 раза в 60 сек) чтобы не долбить БД на каждый GET.
- `SessionCookieAuthFilter.java` extends `OncePerRequestFilter` — читает cookie, `SessionTokenService.resolve()`, ставит `Authentication` в `SecurityContextHolder`, на invalid cookie — вешает `javaclaw.security.session.invalid=true` атрибут (не 401 сам, пусть дальше EntryPoint решает).
- `SessionCookieWriter.java` — утилита для создания/очистки `Cookie` с учётом `CookieProperties`.
- `SessionCleanupJob.java` — Spring `@Scheduled` раз в час чистит `expires_at < NOW()` + `revoked_at < NOW() - 7 days`.

**Package `ai.javaclaw.security.authn`**:

- `AuthenticationService.java` — фасад: `LoginResult login(String u, String p, ClientInfo)`; внутри `AuthenticationManager.authenticate()` → `SecondFactorProvider.challenge()` → `SessionTokenService.issue()` → `AuthAuditService.logLoginSuccess()`.
- `LoginResult.java` — sealed interface: `Success(IssuedToken, UserInfo)`, `SecondFactorRequired(challengeId, List<SecondFactorMethod>)`, `Failed(reason)`.
- `UserInfo.java` — DTO: id, username, roles (List<String>), authorities (List<String>), email (nullable).
- `JdbcUserDetailsService.java` — перенесён из `javaclaw-app`, маппит `AppUser` + permissions → `org.springframework.security.core.userdetails.User`.
- `AuthorityMapper.java` — `Set<Permission> → Set<GrantedAuthority>` формата `PERM_<NAME>` + `ROLE_<role>`.
- `twofactor/SecondFactorProvider.java` — SPI: `boolean isRequired(UserInfo)`, `SecondFactorChallenge issueChallenge(UserInfo)`, `boolean verify(challengeId, code)`.
- `twofactor/SecondFactorChallenge.java` — record.
- `twofactor/SecondFactorMethod.java` — enum: NONE, TOTP, EMAIL_OTP, SMS_OTP, WEBAUTHN.
- `twofactor/NoopSecondFactorProvider.java` — `isRequired` всегда `false`. Default `@ConditionalOnMissingBean`.
- `twofactor/README.md` — документация как добавить `TotpSecondFactorProvider` (GoogleAuth lib, `user_totp_secret` таблица, enroll endpoint).

**Package `ai.javaclaw.security.authz`**:

- `JavaClawPermissionEvaluator.java` implements `org.springframework.security.access.PermissionEvaluator` — `hasPermission(auth, targetId, targetType, permission)` делегирует на `PermissionService.userHasPermission()` + resource-level checks (через `ConversationSharingService`, `SkillService` и т.п. в зависимости от `targetType`).
- `PermissionResolvers.java` — map `targetType → Function<(userId, resourceId, action), Boolean>` для расширяемости без правки `PermissionEvaluator`.

**Package `ai.javaclaw.security.web`**:

- `AuthController.java` — `/api/auth/**`:
  - `POST /login` → `LoginRequest{username, password}` → `LoginResponse{user: UserInfo, sessionExpiresAt}` + Set-Cookie.
  - `POST /logout` → revoke current, clear cookie → 204.
  - `POST /logout-all` → revoke all sessions for user → 204.
  - `GET /me` → текущий `UserInfo` (заменяет `SystemController./api/me`).
  - `GET /sessions` → `List<SessionInfoDto>` активных сессий.
  - `DELETE /sessions/{id}` → revoke конкретной сессии.
- `LoginRequest.java`, `LoginResponse.java`, `SessionInfoDto.java` — DTO records.
- `AuthExceptionHandler.java` — `@RestControllerAdvice`, маппит `BadCredentialsException → 401`, `LockedException → 423`, `DisabledException → 403`.
- `CsrfCookieController.java` — `GET /api/auth/csrf` — триггерит Spring CsrfFilter на выдачу cookie.

**Package `ai.javaclaw.security.audit`**:

- `AuthEventType.java` — enum: LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, LOGOUT_ALL, SESSION_CREATED, SESSION_REVOKED, SECOND_FACTOR_CHALLENGE, SECOND_FACTOR_SUCCESS, SECOND_FACTOR_FAILURE, ACCESS_DENIED.
- `AuthAuditService.java` — оборачивает существующий `auth_audit_log`, методы `log*()`.
- `AuthEventListener.java` — слушает Spring Security `AuthenticationSuccessEvent`, `AuthenticationFailureEvent`, пишет в audit.

**Документация**:

- `javaclaw-security/docs/oidc-relying-party-roadmap.md` — чек-лист: добавить `spring-boot-starter-oauth2-client`, сконфигурировать `ClientRegistration` (Keycloak/Yandex ID), написать `OAuth2LoginSuccessHandler` который дёргает `SessionTokenService.issue()`, добавить profile `auth.oidc.enabled=true`, UI добавляет кнопку «Войти через Keycloak».
- `javaclaw-security/docs/two-factor-roadmap.md` — план добавления TOTP (таблица `user_totp_secret`, enroll flow, challenge flow, recovery codes).
- `javaclaw-security/docs/admin-session-management.md` — будущий план админ-UI для revoke чужих сессий.

**Frontend (новые)**:

- `javaclaw-frontend/src/api/auth.ts` — `login(u, p)`, `logout()`, `logoutAll()`, `getMe()`, `listSessions()`, `revokeSession(id)`.
- `javaclaw-frontend/src/lib/csrf.ts` — читает `XSRF-TOKEN` cookie, возвращает значение для header.

**E2E (новые)**:

- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/LoginLogoutE2ETest.java`
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/InvalidCredentialsE2ETest.java`
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/SessionExpiredE2ETest.java`
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/MultiDeviceLogoutE2ETest.java`
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/CsrfProtectionE2ETest.java`

## Implementation Phases

### Phase 1: Foundation (модуль + схема БД + SPI)

1. Создать `javaclaw-security` Maven-модуль с минимальным pom; подключить в корневой `pom.xml`.
2. `V39__create_user_session.sql` + `V40__extend_auth_audit_event_types.sql`.
3. Определить **интерфейсы** всех SPI: `SessionTokenService`, `SecondFactorProvider`, `PermissionEvaluator`, `AuthAuditService`. Интерфейсы — сначала без реализаций — чтобы зафиксировать контракты.
4. Перенести `JdbcUserDetailsService` из `javaclaw-app` в `javaclaw-security.authn`; оставить его **без изменений логики**, только с новой упаковкой.
5. `CookieProperties` + `SessionProperties` как `@ConfigurationProperties`.

### Phase 2: Core Implementation (реализации)

6. `OpaqueSessionTokenService` — issue/resolve/revoke, SHA-256 hash в БД, SecureRandom.
7. `SessionCookieAuthFilter` + `SessionCookieWriter`.
8. Argon2id `PasswordEncoder` bean через `DelegatingPasswordEncoder`; auto-upgrade старых bcrypt-хэшей.
9. `AuthenticationService` — оркестрирует `AuthenticationManager` + `SecondFactorProvider` + `SessionTokenService`.
10. `NoopSecondFactorProvider` (default).
11. `JavaClawPermissionEvaluator` + `PermissionResolvers` — бридж на `PermissionService`.
12. `MethodSecurityConfig` — регистрирует evaluator в `DefaultMethodSecurityExpressionHandler`.
13. `SecurityConfig` — новый `SecurityFilterChain`: STATELESS, `RequestAttributeSecurityContextRepository`, `ApiAuthenticationEntryPoint`, CSRF cookie repo, matchers, `addFilterBefore(sessionCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)`. **БЕЗ `httpBasic`**.
14. `AuthController` + DTO + `AuthExceptionHandler`.
15. `AuthAuditService` + `AuthEventListener`.
16. `SessionCleanupJob` — Spring `@Scheduled`.

### Phase 3: Integration & Polish (миграция потребителей)

17. Удалить `SecurityConfig`, `JdbcUserDetailsService`, `SystemController./api/me` из `javaclaw-app`. Подключить `javaclaw-security` как dependency.
18. Рефакторинг `ConversationController`, `ChatRestController`, `SkillController`, `McpServerController` — заменить inline `PermissionService`-вызовы на `@PreAuthorize("hasPermission(#id, 'conversation', 'read')")`. **Все остальные controllers** остаются как есть; `Principal principal` параметр — допустим.
19. `NotificationController` — ничего не меняем (cookie придёт автоматически).
20. Frontend: `store/auth.ts` переписать без localStorage-паролей; хранить только `user: UserInfo | null` + `sessionExpiresAt`. `http.ts` — удалить `buildAuthHeaders`, добавить `credentials: "include"` + CSRF header injection. `use-auth.ts` — через `auth.ts` (новый api client). `use-task-notifications.ts` — убрать `?auth=`, просто `new EventSource(url)`. `login-form.tsx` — без изменений контракта.
21. E2E: `PlaywrightE2ETestBase.loginViaStorage()` → `loginViaApi()` через `APIRequestContext.post("/api/auth/login")`. Playwright `storageState` сохраняет cookies автоматически.
22. OpenAPI: добавить `/api/auth/**` в `specs/openapi.yaml`.
23. Документация: `javaclaw-security/docs/*.md` (OIDC roadmap, 2FA roadmap, admin session mgmt).
24. **Удалить устаревшее**: старый `SecurityConfig`, `SystemController./api/me`, `store/auth.ts` Basic-логику, `PlaywrightE2ETestBase.loginViaStorage()` (заменено на `loginViaApi()`). Никаких `@Deprecated` — жёсткое удаление.

## Team Orchestration

- Я оркестрирую команду через `TaskCreate`/`TaskUpdate` и деплою builders через `Task`.
- Я не пишу код сам — только планы, координирую, читаю диффы.

### Team Members

- Builder
  - Name: **builder-security-backend**
  - Role: Реализует javaclaw-security module: Java code, Flyway, SPI, unit-тесты backend.
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: **builder-controllers-refactor**
  - Role: Рефакторит существующие контроллеры (`ConversationController`, `ChatRestController`, `SkillController`, `McpServerController`) на `@PreAuthorize("hasPermission(...)")`, удаляет inline PermissionService-вызовы.
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: **builder-frontend-auth**
  - Role: Переписывает frontend auth: `store/auth.ts`, `api/http.ts`, `api/auth.ts` (новый), `lib/csrf.ts` (новый), `hooks/use-auth.ts`, `hooks/use-task-notifications.ts`, `components/auth/login-form.tsx`. Включает unit-тесты (Vitest/Jest).
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: **builder-integration-tests**
  - Role: Пишет интеграционные тесты (Testcontainers PostgreSQL, MockMvc, реальные HTTP flow login→SSE→logout).
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: **builder-e2e-playwright**
  - Role: Переписывает `PlaywrightE2ETestBase`, добавляет новые E2E тесты для login/logout/session/CSRF.
  - Agent Type: builder
  - Resume: true
- Validator
  - Name: **validator-final**
  - Role: Прогон `mvn verify`, проверка зелёных тестов, проверка acceptance criteria, grep остатков Basic/localStorage.
  - Agent Type: validator
  - Resume: false
- Plan Reviewer
  - Name: **plan-reviewer-pre-build**
  - Role: Проверка плана перед запуском билда.
  - Agent Type: plan-reviewer
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

**javaclaw-security backend**:

- `OpaqueSessionTokenServiceTest` — issue/resolve/expire/revoke happy path + edge cases (null token, wrong hash, double-revoke, expired, revoked_at set). Mock `UserSessionRepository`.
- `SessionCookieAuthFilterTest` — valid cookie → sets `SecurityContext`; no cookie → пропускает; invalid cookie → пропускает (EntryPoint discards); expired → очищает cookie; corrupt value → 400.
- `SessionCookieWriterTest` — правильные флаги httpOnly/Secure/SameSite/Path/Max-Age.
- `JdbcUserDetailsServiceTest` — loadByUsername: happy, not found, inactive (disabled=true).
- `AuthorityMapperTest` — Set<Permission> → Set<GrantedAuthority> с префиксом `PERM_*`.
- `AuthenticationServiceTest` — валидный login → Success; bad password → Failed; 2FA required (mock SecondFactorProvider) → SecondFactorRequired.
- `Argon2PasswordEncoderConfigTest` — matches + upgradeEncoding старого bcrypt.
- `NoopSecondFactorProviderTest` — `isRequired=false`, `issueChallenge` throws UnsupportedOperationException.
- `JavaClawPermissionEvaluatorTest` — для каждого `targetType` (conversation, task, skill, mcp) mock resolver и проверить delegate.
- `PermissionResolversTest` — регистрация/получение.
- `AuthControllerTest` (`@WebMvcTest(AuthController.class)`) — login 200 + Set-Cookie; bad creds 401; logout 204 + Clear-Cookie; /me 200 + body; /sessions список; revoke 204.
- `AuthExceptionHandlerTest` — BadCredentials→401, Disabled→403, Locked→423.
- `AuthAuditServiceTest` — каждый `AuthEventType` пишет в репо с правильными полями.
- `AuthEventListenerTest` — Spring Security `AuthenticationSuccessEvent`/`AuthenticationFailureEvent` → audit.
- `SessionCleanupJobTest` — удаляет expired+revoked-старше-7дней.
- `CookiePropertiesTest` — десериализация yaml.
- `LoginRequestValidationTest` — blank/too-long/invalid username → 400.

**Frontend**:

- `api/auth.test.ts` — mock fetch, проверяем `credentials: "include"`, CSRF header, body shape.
- `hooks/use-auth.test.ts` — login success обновляет atom, logout очищает.
- `lib/csrf.test.ts` — читает `XSRF-TOKEN` cookie.
- `api/http.test.ts` — 401 → вызов `onUnauthorized` (но БЕЗ redirect-в-login если path уже `/login`).

### Integration / API Tests (15%)

Все интеграционные тесты в `javaclaw-security/src/test/java/.../integration/` с Testcontainers PostgreSQL + реальным Flyway:

- `AuthenticationIntegrationTest` — full flow: `/api/auth/login` → `Set-Cookie` → `/api/auth/me` → `/api/auth/logout` → `/api/auth/me` returns 401.
- `SessionRevocationIntegrationTest` — два параллельных login-а в разных Playwright-контекстах, `/logout-all` → обе сессии инвалидированы.
- `SessionExpiryIntegrationTest` — вручную `UPDATE user_session SET expires_at = NOW() - '1h'` → следующий запрос → 401 + очистка cookie.
- `SseCookieAuthIntegrationTest` — login → открыть `/api/chat/notifications/{conv}` через WebMvcTest с реальным cookie header → 200 + приходят события. Критично: проверяет, что `EventSource`-like запрос работает без `?auth=`.
- `PermissionEvaluatorIntegrationTest` — `@PreAuthorize("hasPermission(#id, 'conversation', 'read')")` реально блокирует чужую conversation (owner A, тест делает запрос под B → 403).
- `CsrfProtectionIntegrationTest` — POST без `X-XSRF-TOKEN` → 403; POST с правильным token → 200.
- `Argon2UpgradeIntegrationTest` — seed user с bcrypt hash → login → проверить DB: password_hash начинается с `{argon2id}`.
- `AuthAuditLogIntegrationTest` — login success/fail оба пишутся в `auth_audit_log`.

### UI E2E Tests (5%)

Playwright, `javaclaw-e2e`:

- `LoginLogoutE2ETest` — UI: заполнить форму → redirect на /chat → проверить cookie присутствует → кнопка logout → redirect на /login → cookie очищена.
- `InvalidCredentialsE2ETest` — неверный пароль → показан error, cookie НЕ установлена.
- `SessionExpiredE2ETest` — login → `context.clearCookies()` имитирует expiry → клик по защищённой странице → redirect на /login.
- `MultiDeviceLogoutE2ETest` — два Playwright context (два устройства) → оба залогинены → logout-all из одного → второй context при следующем запросе получает 401 и редирект.
- `CsrfProtectionE2ETest` — проверить, что при отсутствии CSRF header POST /api/conversations даёт 403 (через `page.request.post()` без preflight).
- **Регрессионные** (проверяем, что старые тесты работают): существующие `ChatFlowE2ETest`, `AdminPromptsE2ETest`, `AutoSummarizationE2ETest` — должны зелёными пройти после миграции базового класса. **Главная проверка**: открытие диалога и завершение стрима **не вызывают** никакого `dialog` event в Playwright (проверить через `page.on("dialog", ...)` — если событие поймано, тест падает).

## Step by Step Tasks

### 1. Создание модуля javaclaw-security и скелета

- **Task ID**: security-module-bootstrap
- **Depends On**: none
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven controller entity
- **Parallel**: false
- **Tests**: Unit: CookiePropertiesTest, SessionPropertiesTest — загрузка из yaml.
- Создать директорию `javaclaw-security/` с `pom.xml` (parent — root JavaClaw, deps: spring-boot-starter-security, starter-webmvc, starter-data-jdbc, javaclaw-core, bouncycastle-provider, flyway-core, lombok, test: junit5, mockito, assertj, testcontainers-postgres).
- Добавить `<module>javaclaw-security</module>` в корневой `pom.xml`.
- Создать пакеты `ai.javaclaw.security.{config,session,authn,authn.twofactor,authz,web,audit}`.
- Написать `CookieProperties` и `SessionProperties` с дефолтами: `cookie.name=JCLAW_SESSION`, `cookie.path=/`, `cookie.secure=true`, `cookie.sameSite=Lax`, `cookie.maxAgeSeconds=86400`, `session.ttl=PT24H`, `session.cleanupInterval=PT1H`.
- Создать `application-security.yaml` с дефолтами.
- Создать `javaclaw-security/docs/{oidc-relying-party-roadmap,two-factor-roadmap,admin-session-management}.md` — скелеты с описанием.

### 2. Flyway миграции V39 + V40

- **Task ID**: flyway-auth-v39-v40
- **Depends On**: security-module-bootstrap
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot jpa jdbc
- **Parallel**: false
- **Tests**: Integration: миграции применяются на Testcontainers Postgres без ошибок; проверка индексов через `information_schema`.
- `V39__create_user_session.sql`: таблица `user_session` с колонками `id VARCHAR(36) PK`, `token_hash VARCHAR(64) UNIQUE NOT NULL`, `user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE`, `created_at TIMESTAMPTZ NOT NULL`, `expires_at TIMESTAMPTZ NOT NULL`, `last_used_at TIMESTAMPTZ NOT NULL`, `remote_addr VARCHAR(64)`, `user_agent VARCHAR(512)`, `revoked_at TIMESTAMPTZ`, `revocation_reason VARCHAR(64)`. Индексы: `idx_user_session_user_id`, `idx_user_session_token_hash` (UNIQUE), `idx_user_session_expires_at`.
- `V40__extend_auth_audit_event_types.sql`: если есть CHECK constraint на `event_type` в `auth_audit_log` — расширить на `session_created, session_revoked, logout, logout_all, second_factor_*`. Если CHECK нет — пустая no-op миграция с комментарием.

### 3. Session domain + SPI

- **Task ID**: session-domain-spi
- **Depends On**: flyway-auth-v39-v40
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot jpa jdbc record mockito
- **Parallel**: false
- **Tests**: Unit: `OpaqueSessionTokenServiceTest` (issue/resolve/revoke happy + edge + throttled lastUsedAt), `UserSessionRepositoryTest` через `@DataJdbcTest`.
- `UserSession` record, `UserSessionRepository` (Spring Data JDBC).
- `SessionTokenService` SPI + `IssuedToken`, `ClientInfo` records.
- `OpaqueSessionTokenService` реализация: `SecureRandom` → 32 байта → base64url → SHA-256 hash → upsert. `resolve()`: hash → `findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter` → построить `UsernamePasswordAuthenticationToken` с authorities из `JdbcUserDetailsService.loadUserByUsername()`. `revoke()`: `UPDATE`. Throttle `lastUsedAt` обновлением раз в 60 сек на сессию (держать `ConcurrentHashMap<sessionId, Instant>` последнего update).

### 4. Authentication layer: password encoder, UserDetailsService, authn service

- **Task ID**: authentication-core
- **Depends On**: session-domain-spi
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller mockito
- **Parallel**: false
- **Tests**: Unit: `Argon2PasswordEncoderConfigTest`, `JdbcUserDetailsServiceTest`, `AuthenticationServiceTest`, `AuthorityMapperTest`.
- Перенести `JdbcUserDetailsService` из `javaclaw-app` в `ai.javaclaw.security.authn`.
- `AuthenticationConfig` bean: `DelegatingPasswordEncoder` с default `argon2` (параметры: m=19456, t=2, p=1 — OWASP 2024), legacy `bcrypt` для старых хэшей; `DaoAuthenticationProvider` с этим encoder'ом.
- `AuthorityMapper`, `UserInfo` DTO.
- `AuthenticationService` фасад с `login()` методом, внутри вызывает `AuthenticationManager.authenticate()`, потом `SecondFactorProvider.isRequired()` + `SessionTokenService.issue()`, возвращает `LoginResult`.

### 5. Second-factor SPI + Noop реализация

- **Task ID**: second-factor-spi
- **Depends On**: authentication-core
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot mockito
- **Parallel**: false
- **Tests**: Unit: `NoopSecondFactorProviderTest`.
- `SecondFactorProvider` interface, `SecondFactorChallenge`, `SecondFactorMethod` enum.
- `NoopSecondFactorProvider` impl (default, `@ConditionalOnMissingBean`).
- `twofactor/README.md` — пошаговый план добавления TOTP: (1) добавить `user_totp_secret` таблицу V41, (2) подключить `com.warrenstrange:googleauth` dep, (3) реализовать `TotpSecondFactorProvider`, (4) добавить `POST /api/auth/totp/enroll`, `POST /api/auth/totp/verify`, (5) `AuthController` расширить обработку `SecondFactorRequired`.

### 6. Session cookie filter и entry point

- **Task ID**: session-cookie-filter
- **Depends On**: session-domain-spi
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller mockito
- **Parallel**: false
- **Tests**: Unit: `SessionCookieAuthFilterTest` (mock SessionTokenService), `SessionCookieWriterTest`, `ApiAuthenticationEntryPointTest`.
- `SessionCookieAuthFilter extends OncePerRequestFilter`: читает cookie `JCLAW_SESSION`, `tokenService.resolve()`, `SecurityContextHolder.getContext().setAuthentication(...)`. Никаких 401 изнутри фильтра — если cookie invalid, просто пропускает (дальше `.authenticated()` matcher вызовет entry point).
- `SessionCookieWriter` utility.
- `ApiAuthenticationEntryPoint extends HttpStatusEntryPoint(UNAUTHORIZED)` — **БЕЗ** `WWW-Authenticate` header. Это ключ к устранению браузерного prompt.

### 7. AuthZ bridge: PermissionEvaluator

- **Task ID**: permission-evaluator-bridge
- **Depends On**: security-module-bootstrap
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller mockito
- **Parallel**: false
- **Tests**: Unit: `JavaClawPermissionEvaluatorTest`, `PermissionResolversTest`.
- `JavaClawPermissionEvaluator implements PermissionEvaluator`: `hasPermission(auth, targetId, targetType, permission)` → достаёт `targetType` из `PermissionResolvers`, если `null` → fallback на `permissionService.userHasPermission(auth.getName(), Permission.valueOf(permission.toString().toUpperCase()))`.
- `PermissionResolvers` bean: registry `Map<String, PermissionCheck>`, бинс заполняется через `@PostConstruct` от каждого resolver'а (conversation, task, skill, mcp). **Критично — admin-bypass сохраняется**: `conversation` resolver = `permissionService.userHasPermission(userId, CONVERSATION_ACCESS_ALL) || conversationSharingService.canRead(conversationId, userId)`. Аналогично `task` = `TASK_ACCESS_ALL || ownership-check`, `skill` и `mcp` — через `userHasPermission` по соответствующему permission'у. **Нельзя** делать stub resolver на `userHasPermission` без ownership+CONVERSATION_ACCESS_ALL — это silent regression admin-доступа.
- `MethodSecurityConfig` — `@EnableMethodSecurity(prePostEnabled=true)`, регистрирует `DefaultMethodSecurityExpressionHandler` с нашим evaluator'ом.

### 8. SecurityFilterChain и AuthController

- **Task ID**: security-config-and-auth-controller
- **Depends On**: session-cookie-filter, authentication-core, second-factor-spi, permission-evaluator-bridge
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller exception error handling
- **Parallel**: false
- **Tests**: Unit: `AuthControllerTest` (@WebMvcTest), `AuthExceptionHandlerTest`, `SecurityConfigMatchersTest` (MockMvc проверка matchers).
- `SecurityConfig` bean: `SecurityFilterChain`: `.csrf(CookieCsrfTokenRepository.withHttpOnlyFalse(), ignore /api/auth/login, /api/auth/csrf)`, `.sessionManagement(STATELESS)`, `.securityContext(requireExplicitSave(false) + RequestAttributeSecurityContextRepository)`, `.exceptionHandling(ApiAuthenticationEntryPoint, AccessDeniedHandlerImpl)`, `.authorizeHttpRequests(...)` — все текущие matchers плюс `/api/auth/login`, `/api/auth/csrf` permitAll. `.addFilterBefore(sessionCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)`. **БЕЗ** `.httpBasic(...)`.
- `AuthController` — все endpoints: `/login`, `/logout`, `/logout-all`, `/me`, `/sessions`, `/sessions/{id}` DELETE, `/csrf`.
- `LoginRequest`, `LoginResponse`, `SessionInfoDto` records.
- `AuthExceptionHandler` `@RestControllerAdvice`.

### 9. Audit

- **Task ID**: auth-audit
- **Depends On**: security-config-and-auth-controller
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot jpa jdbc mockito
- **Parallel**: false
- **Tests**: Unit: `AuthAuditServiceTest`, `AuthEventListenerTest`.
- `AuthEventType` enum.
- `AuthAuditService` — обёртка над существующим `auth_audit_log` JDBC.
- `AuthEventListener` — `@EventListener` на `AuthenticationSuccessEvent`, `AbstractAuthenticationFailureEvent`.

### 10. Session cleanup job

- **Task ID**: session-cleanup-job
- **Depends On**: session-domain-spi
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot
- **Parallel**: false
- **Tests**: Unit: `SessionCleanupJobTest`.
- `@Component @Scheduled(fixedDelayString = "${javaclaw.security.session.cleanup-interval:PT1H}")` — вызывает `UserSessionRepository.deleteExpired()`.

### 11. Удаление старого кода в javaclaw-app

- **Task ID**: remove-legacy-security
- **Depends On**: security-config-and-auth-controller
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven
- **Parallel**: false
- **Tests**: Compile check — `mvn -pl javaclaw-app compile`.
- Удалить `javaclaw-app/src/main/java/ai/javaclaw/security/SecurityConfig.java`, `JdbcUserDetailsService.java`.
- Удалить `SystemController./api/me` метод (заменён `AuthController./api/auth/me`).
- Добавить `<dependency>javaclaw-security</dependency>` в `javaclaw-app/pom.xml`.
- Удалить старый `spring-boot-starter-security` если подключался явно (подтянется транзитивно).

### 12. Рефакторинг контроллеров на @PreAuthorize

- **Task ID**: preauthorize-refactor
- **Depends On**: remove-legacy-security
- **Assigned To**: builder-controllers-refactor
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller exception error handling
- **Parallel**: false
- **Tests**: Unit: обновить существующие `ConversationControllerTest`, `ChatRestControllerTest`, `SkillControllerTest`, `McpServerControllerTest` — добавить `@WithMockUser` + проверку 403 для чужих ресурсов **И** `@WithMockUser(authorities="PERM_CONVERSATION_ACCESS_ALL")` + проверку 200 на чужую conversation (admin-bypass регрессия).
- В `ConversationController`: на `messages()`, `create()`, `delete()` — `@PreAuthorize("hasPermission(#id, 'conversation', 'read')")` / `'write'` / `'delete'`. Внутренние `sharingService.isAdmin()` вызовы — удалить, эту логику переносит resolver. **Критично**: `conversation` resolver в `PermissionResolvers` должен реализовывать admin-bypass через `CONVERSATION_ACCESS_ALL` permission: `permissionService.userHasPermission(userId, CONVERSATION_ACCESS_ALL) || sharingService.canRead(conversationId, userId)`. Это сохраняет текущую семантику «admin видит всё». Аналогично для `task` через `TASK_*_ALL` если есть.
- В `ChatRestController`: `@PreAuthorize("hasAuthority('PERM_CHAT_SEND')")` + проверку role-model allowlist оставить в коде (это бизнес-правило, не authZ).
- В `SkillController`, `McpServerController`: аналогично.
- Обновить `PermissionResolvers` — зарегистрировать реальные resolvers:
  - `conversation` → `ConversationSharingService.canRead(conversationId, userId)`
  - `task` → `TaskPermissionService.canAccess(taskId, userId)` (если есть, иначе fallback на ownership).
  - `skill`, `mcp` — делегируют на `PermissionService.userHasPermission()`.

### 13. Frontend: rewrite auth store, http, hooks

- **Task ID**: frontend-auth-rewrite
- **Depends On**: security-config-and-auth-controller
- **Assigned To**: builder-frontend-auth
- **Agent Type**: builder
- **Stack**: React vite component hook useState useEffect tsx
- **Parallel**: true
- **Tests**: Unit: `api/auth.test.ts`, `hooks/use-auth.test.ts`, `api/http.test.ts`, `lib/csrf.test.ts` (vitest).
- Создать `src/api/auth.ts` с `login`, `logout`, `logoutAll`, `getMe`, `listSessions`, `revokeSession`. Все методы используют `apiJson` с `credentials: "include"`.
- Переписать `src/api/http.ts`: удалить `buildAuthHeaders`, `getStoredCredentials`; добавить `credentials: "include"` к `fetch`; добавить middleware, читающий `XSRF-TOKEN` cookie и ставящий `X-XSRF-TOKEN` header на POST/PUT/PATCH/DELETE.
- Создать `src/lib/csrf.ts` — `getCsrfToken(): string | null` из cookie, `ensureCsrfCookie()` — `GET /api/auth/csrf` если cookie отсутствует.
- Переписать `src/store/auth.ts`: атом хранит `{ user: UserInfo | null, sessionExpiresAt: string | null }`. Убрать все `localStorage.setItem("javaclaw.auth.*", ...)`. На старте приложения — `getMe()` для восстановления состояния по cookie.
- Переписать `src/hooks/use-auth.ts`: `login(u, p)` → `auth.login({username: u, password: p})` → обновить atom; `logout()` → `auth.logout()` → очистить atom + redirect на `/login`.
- Переписать `src/hooks/use-task-notifications.ts`: убрать `buildAuthHeaders` + `?auth=`, оставить просто `new EventSource(/api/chat/notifications/${id})`. На `onerror` с `readyState === CLOSED` — не reconnect'ить бесконечно, а закрыть.
- `src/components/auth/login-form.tsx` — контракт `useAuth.login` не меняется.

### 14. Документация: OpenAPI + модульные docs

- **Task ID**: docs-openapi-and-module
- **Depends On**: security-config-and-auth-controller
- **Assigned To**: builder-security-backend
- **Agent Type**: builder
- **Stack**: Java Spring Boot
- **Parallel**: true
- **Tests**: N/A (documentation-only).
- Добавить в `specs/openapi.yaml` секцию `/api/auth/*` с полными схемами request/response.
- Заполнить `javaclaw-security/docs/oidc-relying-party-roadmap.md` — конкретные шаги интеграции Spring Security OAuth2 Client: pom dep, `application.yaml` клиенты, `OAuth2LoginSuccessHandler` код, миграция пользователей (email → username mapping).
- Заполнить `javaclaw-security/docs/two-factor-roadmap.md` — TOTP, Email OTP, SMS OTP, WebAuthn: таблицы, flow, endpoints.
- Заполнить `javaclaw-security/docs/admin-session-management.md` — будущий UI для админа.

### 15. E2E: PlaywrightE2ETestBase rewrite + новые тесты auth

- **Task ID**: e2e-playwright-rewrite
- **Depends On**: remove-legacy-security, frontend-auth-rewrite
- **Assigned To**: builder-e2e-playwright
- **Agent Type**: builder
- **Stack**: Java selenide e2e page object
- **Parallel**: false
- **Tests**: E2E: `LoginLogoutE2ETest`, `InvalidCredentialsE2ETest`, `SessionExpiredE2ETest`, `MultiDeviceLogoutE2ETest`, `CsrfProtectionE2ETest`.
- Переписать `PlaywrightE2ETestBase.loginViaStorage()` → `loginViaApi()`: через `APIRequestContext.post("/api/auth/login", ...)`, Playwright `storageState` сохраняет cookies.
- Оставить `.login()` метод (UI flow) — использует заполнение формы.
- Добавить в базу `registerNoDialogGuard()` — `page.onDialog(d -> fail("Unexpected native dialog: " + d.message()))`. Это regression-страховка против баг #41.
- Написать 5 новых E2E тестов из списка Testing Strategy.
- Обновить все существующие E2E тесты — смена `loginViaStorage` → `loginViaApi` (автоматически, тесты сами не меняют контракт).

### 16. Integration tests

- **Task ID**: integration-tests
- **Depends On**: security-config-and-auth-controller, auth-audit, session-cleanup-job, preauthorize-refactor
- **Assigned To**: builder-integration-tests
- **Agent Type**: builder
- **Stack**: Java testcontainers integration test mockmvc jdbc test
- **Parallel**: true
- **Tests**: Integration: все из секции Integration / API Tests выше (8 тестов).
- Все через `@SpringBootTest` + Testcontainers Postgres + `WebTestClient` или `MockMvc` с включённым `SpringSecurity`.
- `SseCookieAuthIntegrationTest` — критичный тест, подтверждающий что SSE работает с cookie без `?auth=`.
- `Argon2UpgradeIntegrationTest` — seed пользователя с bcrypt hash через прямой `INSERT`, выполнить login, проверить через `SELECT password_hash` что стал `{argon2id}`.

### N-1. Write tests (финальный сбор тестового покрытия + E2E)

- **Task ID**: write-tests
- **Depends On**: integration-tests, e2e-playwright-rewrite, frontend-auth-rewrite, preauthorize-refactor, docs-openapi-and-module
- **Assigned To**: builder-integration-tests
- **Agent Type**: builder
- **Stack**: Java MockMvc Mockito assertj allure test structure Testcontainers React jest testing-library tsx selenide e2e
- **Parallel**: false
- Review coverage report `mvn jacoco:report -pl javaclaw-security` — требование `>= 80%` line coverage.
- Добрать недостающие unit-тесты.
- Проверить test naming + Allure annotations (`@Feature("Auth")`, `@Story("Login")` и т.п.).
- Frontend: `vitest run --coverage`, добрать покрытие.
- Playwright: прогон всех E2E с `registerNoDialogGuard()`.

### N. CDP Acceptance Tests (Chrome DevTools Protocol)

- **Task ID**: cdp-acceptance
- **Depends On**: write-tests
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot React selenide e2e
- **Parallel**: false
- Использовать MCP-инструменты Chrome DevTools (`mcp__chrome-devtools__*`) для acceptance-тестирования живого приложения в браузере.
- Сценарий 1 — **Login → cookie set, NO auth prompt**:
  - Открыть `/login`, ввести credentials, нажать кнопку submit.
  - Проверить через `list_network_requests` что `/api/auth/login` вернул `200` и `Set-Cookie: JCLAW_SESSION=`.
  - Проверить через `get_console_message` что нет ошибок `401` / `ERR_NETWORK_CHANGED`.
  - Проверить redirect на `/chat`.
- Сценарий 2 — **Открытие диалога из списка — NO browser auth prompt**:
  - Залогиниться, кликнуть на существующий диалог в sidebar.
  - Слушать `list_network_requests` — убедиться что ни один запрос не вернул `401 + WWW-Authenticate: Basic`.
  - Проверить что `handle_dialog` не был вызван (нет нативного prompt).
- Сценарий 3 — **Завершение SSE стрима — NO prompt**:
  - Отправить сообщение, дождаться завершения потоковой генерации.
  - Проверить сеть: `/api/chat/notifications/{id}` — статус `200`, cookie в request headers (не `?auth=`).
  - Проверить что нет `401` в `list_network_requests`.
- Сценарий 4 — **Logout — cookie удалена**:
  - Нажать logout.
  - Проверить network: `/api/auth/logout` → `204`, в `Set-Cookie` присутствует `JCLAW_SESSION=; Max-Age=0`.
  - Попытаться открыть `/chat` → redirect на `/login`.
- Сценарий 5 — **CSRF protection** (опционально, если CookieCsrfRepository настроен):
  - Через `evaluate_script` прочитать `document.cookie` — проверить наличие `XSRF-TOKEN`.
  - Через `evaluate_script` сделать `fetch('/api/conversations', {method:'POST', ...})` БЕЗ `X-XSRF-TOKEN` header → ожидать `403`.
- Сценарий 6 — **SSE EventSource без `?auth=` в URL**:
  - Через `list_network_requests` найти запрос к `/api/chat/notifications/` — убедиться что URL **не содержит** `?auth=`.

Каждый сценарий оформить как отдельный шаг с явным PASS/FAIL статусом и скриншотом ключевого состояния через `take_screenshot`.

### N+1. Final validation

- **Task ID**: validate-all
- **Depends On**: cdp-acceptance
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot maven Testcontainers React vite selenide
- **Parallel**: false
- Запустить `mvn -T 1C clean verify` — все модули, все unit + integration зелёные.
- Прогнать Playwright E2E: `mvn -pl javaclaw-e2e verify -Pe2e`.
- `grep` по репозиторию: не осталось `Authorization: Basic`, `javaclaw.auth.credentials`, `getStoredCredentials`, `buildAuthHeaders`, `?auth=`, `httpBasic(`, `BasicAuthenticationEntryPoint` (кроме docs).
- Проверить acceptance criteria (все чекбоксы ниже).
- Подтвердить что CDP acceptance тесты (шаг N) все PASS.

## Acceptance Criteria

- [ ] Создан Maven-модуль `javaclaw-security` в корневом `pom.xml`.
- [ ] Созданы и применяются Flyway миграции `V39__create_user_session.sql` + `V40__extend_auth_audit_event_types.sql`.
- [ ] `SessionTokenService` реализован как `OpaqueSessionTokenService`, token хранится как SHA-256 hash, raw token не сохраняется.
- [ ] `POST /api/auth/login` возвращает 200 + `Set-Cookie: JCLAW_SESSION=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=86400` + body `{user: {...}, sessionExpiresAt}`.
- [ ] `POST /api/auth/logout` возвращает 204 + `Set-Cookie: JCLAW_SESSION=; Max-Age=0`; сессия в БД помечена `revoked_at=NOW()`.
- [ ] `POST /api/auth/logout-all` revoke'ает все активные сессии пользователя.
- [ ] `GET /api/auth/me` с валидным cookie возвращает `UserInfo`; без cookie → 401 **без** `WWW-Authenticate` header.
- [ ] `GET /api/auth/sessions` возвращает список активных сессий текущего пользователя (с ip/ua/createdAt/lastUsedAt).
- [ ] `DELETE /api/auth/sessions/{id}` revoke'ает конкретную сессию.
- [ ] `SecurityConfig` НЕ содержит `httpBasic(...)`. `grep -r "httpBasic" javaclaw-` возвращает пусто (кроме `docs/`).
- [ ] `BasicAuthenticationEntryPoint` не используется. `ApiAuthenticationEntryPoint(HttpStatusEntryPoint)` возвращает 401 без `WWW-Authenticate`.
- [ ] `PasswordEncoder` = `DelegatingPasswordEncoder` с default `argon2`; legacy bcrypt всё ещё валидируется и upgrade'ится при успешном логине.
- [ ] `JavaClawPermissionEvaluator` зарегистрирован; `@PreAuthorize("hasPermission(#id, 'conversation', 'read')")` работает и блокирует чужую conversation с 403.
- [ ] **Admin-bypass регрессия отсутствует**: пользователь с `PERM_CONVERSATION_ACCESS_ALL` получает 200 на чужую conversation через те же `@PreAuthorize` endpoints (проверено тестом).
- [ ] `ConversationController`, `ChatRestController`, `SkillController`, `McpServerController` переведены на `@PreAuthorize`, inline `PermissionService`-вызовы удалены.
- [ ] `NotificationController` не требует изменений кода (cookie работает сам).
- [ ] `use-task-notifications.ts` больше не содержит `?auth=` / `buildAuthHeaders`.
- [ ] Frontend: `store/auth.ts` НЕ хранит пароль в localStorage. `grep -r "javaclaw.auth.credentials" javaclaw-frontend/src` пусто.
- [ ] `api/http.ts` ставит `credentials: "include"` + `X-XSRF-TOKEN` header.
- [ ] `PlaywrightE2ETestBase.loginViaApi()` работает; все существующие E2E зелёные.
- [ ] Ручной smoke: (1) открытие диалога из списка **не** вызывает браузерный prompt, (2) завершение генерации сообщения **не** вызывает prompt.
- [ ] `SecondFactorProvider` SPI + `NoopSecondFactorProvider` зарегистрированы; в login-flow встроен вызов `isRequired()` (сейчас всегда `false`).
- [ ] `javaclaw-security/docs/oidc-relying-party-roadmap.md`, `two-factor-roadmap.md`, `admin-session-management.md` заполнены.
- [ ] `specs/openapi.yaml` содержит `/api/auth/*` контракты.
- [ ] Все 1187+ существующих тестов зелёные; добавлены новые unit (>=30), integration (>=8), E2E (>=5) тесты.
- [ ] Line coverage `javaclaw-security` >= 80% (jacoco).
- [ ] `grep -r "Authorization.*Basic" javaclaw-` пусто (кроме docs).
- [ ] CDP acceptance тесты (6 сценариев) все PASS: login cookie, no auth prompt при открытии диалога, no auth prompt при завершении стрима, logout очищает cookie, CSRF 403 без X-XSRF-TOKEN, SSE URL не содержит `?auth=`.
- [ ] `mvn -T 1C clean verify` зелёный.

## Validation Commands

- `mvn -T 1C clean verify` — полный билд + unit + integration.
- `mvn -pl javaclaw-security jacoco:report` — coverage report.
- `mvn -pl javaclaw-e2e verify -Pe2e` — Playwright E2E.
- `grep -rn 'httpBasic\|BasicAuthenticationEntryPoint\|Authorization.*Basic\|javaclaw.auth.credentials\|buildAuthHeaders\|?auth=' javaclaw-app javaclaw-api javaclaw-core javaclaw-frontend/src` — должно быть пусто (кроме `javaclaw-security/docs/`).
- `cd javaclaw-frontend && pnpm test --run --coverage` — frontend coverage.
- Ручной smoke-тест: старт `mvn spring-boot:run -pl javaclaw-app`, открыть `http://localhost:8080`, залогиниться, открыть диалог, отправить сообщение, проверить DevTools Network — **нет** `401 + WWW-Authenticate: Basic`, **нет** нативного auth prompt.

## Notes

- **Версии**: Spring Boot 4.0.5, Spring Security 6.x (реально 7.x как часть Spring Framework 7), Java 21, PostgreSQL 16, Flyway 10.x.
- **Новые зависимости**: `org.bouncycastle:bcprov-jdk18on:1.79` — пин версии явно в `javaclaw-security/pom.xml`, т.к. BouncyCastle **не входит** в managed BOM Spring Boot 4.0.5. Нужен для `Argon2PasswordEncoder` высокопроизводительной имплементации; без него Spring Security сделает fallback на чистый Java, работает, но медленнее. Использовать именно `bcprov-jdk18on` (не `bcprov-jdk15on` — устарел).
- **Не трогаем** `jobrunr`, `spring-ai-*`, `spring-modulith-*` — они подключены в `javaclaw-core`, авторизация их не касается.
- **Risk 1**: Playwright `storageState` capture. Убедиться что `loginViaApi` через `APIRequestContext` попадает в тот же browser context, иначе cookie не попадёт в страницу. Решение: использовать `browserContext.request()` (привязан к context), не глобальный `playwright.request()`.
- **Risk 2**: `DaoAuthenticationProvider.upgradeEncoding()` вызывается только если encoder возвращает `true` из `upgradeEncoding(hash)`. `DelegatingPasswordEncoder` корректно возвращает `true` для bcrypt если default не bcrypt. Проверить тестом.
- **Risk 3**: `@PreAuthorize("hasPermission(#id, 'conversation', 'read')")` требует `#id` параметр именно так. Если метод принимает `String conversationId` — нужно `hasPermission(#conversationId, ...)`. Согласовать с builder-controllers-refactor именование параметров.
- **Risk 4 (hard requirement, not optional)**: В Spring Security 6.x/7.x при `SessionCreationPolicy.STATELESS` дефолт `requireExplicitSave=true` и `NullSecurityContextRepository`. **Обязательно** явно сконфигурировать `.securityContext(c -> c.requireExplicitSave(false).securityContextRepository(new RequestAttributeSecurityContextRepository()))`, иначе `SecurityContext`, выставленный `SessionCookieAuthFilter`, не будет виден `AuthorizationFilter` и `@PreAuthorize` — и всё сломается молча. Никакого «если default — не указывать».
- **Risk 5**: Throttling `lastUsedAt` обновления через `ConcurrentHashMap` — это in-memory, не переживает перезапуск и не работает в horizontal scaling. Для MVP норм (один instance), для prod — позже переключить на Redis или делать update на каждый N-й запрос. Зафиксировать в `javaclaw-security/docs/admin-session-management.md`.
- **CSRF и SSE**: CsrfFilter применяется только к mutating методам (POST/PUT/PATCH/DELETE). SSE endpoint — GET, CSRF не нужен. Проверить тестом.
- **OIDC будущее — ключевые точки контракта**:
  - `SessionTokenService.issue(Authentication)` принимает ЛЮБУЮ `Authentication`, включая `OAuth2AuthenticationToken`. Никаких Basic-specific зависимостей.
  - `AuthenticationService.login()` возвращает `LoginResult`, который уже умеет `SecondFactorRequired` — добавление OIDC step-up flow не меняет API.
  - `UserInfo` контракт абстрагирован от источника — OIDC callback конструирует его из `OidcUserInfo` claims.
- **Не в scope MVP** (отложено до следующих задач, но архитектурно заложено):
  - Rate limiting на login (Bucket4j).
  - Password reset flow.
  - OIDC реальная интеграция (только roadmap).
  - TOTP/Email/SMS/WebAuthn реальная имплементация (только SPI + stub).
  - Service-to-service API keys (для MCP/A2A клиентов).
  - Admin UI для управления сессиями других пользователей.

