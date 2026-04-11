# OIDC Relying Party Roadmap

## Overview

JavaClaw currently authenticates via HTTP form login + cookie session
(`JCLAW_SESSION`). This roadmap describes how to plug an external OpenID
Connect / OAuth2 Identity Provider (Keycloak, Yandex ID, Auth0, Azure AD,
corporate Sberbank ID) as a **Relying Party** without touching the rest of
the authorization / authentication stack.

Key design invariant: `SessionTokenService.issue(Authentication)` already
accepts **any** `org.springframework.security.core.Authentication`, including
`OAuth2AuthenticationToken` and `OidcUser`. That means OIDC login only has to
produce a valid `Authentication` object and let the existing session code
mint a `JCLAW_SESSION` cookie — no changes in `ChatRestController`,
`PermissionService`, RBAC, CSRF, SSE or the React SPA are required.

## Activation Flag

```
javaclaw:
  security:
    oidc:
      enabled: false   # default; set true to expose /oauth2/authorization/*
```

When `auth.oidc.enabled=false` the OAuth2 client filter chain is **not**
registered (conditional on property), so production deploys that run
username/password-only are unaffected.

## Implementation Checklist

### 1. Dependencies

- [ ] Add `org.springframework.boot:spring-boot-starter-oauth2-client` to
  `javaclaw-security/pom.xml`.
- [ ] Add `org.springframework.security:spring-security-oauth2-jose` (brought
  transitively but pin explicitly for reproducibility).

### 2. Client Registration

- [ ] Create `application-oidc.yaml` profile with:

  ```yaml
  spring:
    security:
      oauth2:
        client:
          registration:
            keycloak:
              client-id: javaclaw
              client-secret: ${KEYCLOAK_CLIENT_SECRET}
              authorization-grant-type: authorization_code
              redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
              scope: openid, profile, email
            yandex:
              client-id: ${YANDEX_CLIENT_ID}
              client-secret: ${YANDEX_CLIENT_SECRET}
              authorization-grant-type: authorization_code
              redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
              scope: login:email, login:info
          provider:
            keycloak:
              issuer-uri: https://sso.example.com/realms/javaclaw
            yandex:
              authorization-uri: https://oauth.yandex.ru/authorize
              token-uri: https://oauth.yandex.ru/token
              user-info-uri: https://login.yandex.ru/info
              user-name-attribute: login
  ```
- [ ] Reference each provider by `registrationId` exactly in docs / UI.

### 3. Spring Security Filter Chain

- [ ] In `SecurityConfig` add a conditional block
  `@ConditionalOnProperty("javaclaw.security.oidc.enabled")` that calls
  `http.oauth2Login(oauth2 -> oauth2.successHandler(oidcLoginSuccessHandler))`.
- [ ] CSRF stays on (Spring Security wires the OAuth2 redirect endpoints
  into the ignore list automatically).
- [ ] Ensure session creation policy remains `IF_REQUIRED` so the success
  handler can cleanly mint `JCLAW_SESSION`.

### 4. OidcLoginSuccessHandler

- [ ] Implement `OidcLoginSuccessHandler implements AuthenticationSuccessHandler`:
  1. Extract `OidcUser` (or `OAuth2User`) from the `Authentication`.
  2. Resolve local `AppUser` by `email` (preferred) or `sub` claim.
  3. If missing → call `UserProvisioningService.provisionFromOidc(claims)`
     which creates the `users` row with a random bcrypt password and the
     default role configured via `javaclaw.security.oidc.default-role`.
  4. Call `SessionTokenService.issue(authentication, request, response)` —
     this writes the `JCLAW_SESSION` cookie and stores a row in
     `user_session` exactly like form login.
  5. Emit `AuthAuditService.logLoginSuccess(username, "oidc:" + registrationId,
     ip, userAgent)`.
  6. Redirect to `/` (or `state`-bounded redirectTarget if present).

### 5. User Provisioning

- [ ] `UserProvisioningService.provisionFromOidc(OidcUser claims, String provider)`:
  - `username = claims.getEmail()` (fallback: `provider + ":" + sub`).
  - `displayName = claims.getFullName()`.
  - `passwordHash = bcrypt(UUID.randomUUID())` (never used for login).
  - `externalProvider = provider`, `externalSubject = claims.getSubject()`.
- [ ] V43 migration: add `users.external_provider VARCHAR(64)` +
  `users.external_subject VARCHAR(255)` + unique index
  `(external_provider, external_subject)`.
- [ ] Account linking: if an `AppUser` with the same email already exists,
  update its `external_provider/subject` instead of creating a duplicate.
  Log `auth_audit_log.event='account_linked'`.

### 6. UI Integration

- [ ] `/login` page: add a section «Войти через» with one button per enabled
  provider. Button is a plain `<a href="/oauth2/authorization/{id}">`.
- [ ] React SPA: `GET /api/auth/providers` returns
  `[{id:"keycloak", displayName:"Keycloak SSO"}]`. Frontend reads it and
  conditionally renders SSO buttons.
- [ ] After OIDC redirect the React app hits `GET /api/auth/me` — no code
  change required, because the cookie is already set by the success handler.

### 7. Logout

- [ ] Standard `POST /api/auth/logout` still works (revokes local session).
- [ ] Optional: implement OIDC RP-initiated logout via
  `OidcClientInitiatedLogoutSuccessHandler` for providers that support
  `end_session_endpoint`.

### 8. Tests

- [ ] Unit: `OidcLoginSuccessHandlerTest` — mocks `OidcUser`, verifies
  `SessionTokenService.issue()` call and audit log entry.
- [ ] Unit: `UserProvisioningServiceTest` — first login creates user,
  second login reuses existing user.
- [ ] Integration (`@SpringBootTest` + `MockOAuth2Server`): end-to-end
  authorization_code flow produces `JCLAW_SESSION` cookie and
  `GET /api/auth/me` returns the provisioned user.
- [ ] E2E (Playwright, `auth.oidc.enabled=true` profile): click «Войти через
  Keycloak» → mock IdP → land on `/` authenticated.

## Rollout Plan

1. Merge code behind `javaclaw.security.oidc.enabled=false` (default off).
2. Enable in staging with a Keycloak test realm.
3. Document provider-specific claim mappings per deployment.
4. Enable in production once account-linking audit is verified.

## Out of Scope (separate roadmaps)

- Second factor enforcement on top of OIDC — see `two-factor-roadmap.md`.
- Admin UI for linking / unlinking accounts — see `admin-session-management.md`.

