# Two-Factor Authentication Roadmap

## Architecture Invariant

The `SecondFactorProvider` SPI is already wired into the login pipeline:

```java
interface SecondFactorProvider {
    boolean isRequired(AppUser user);
    SecondFactorChallenge issueChallenge(AppUser user);
    boolean verify(String challengeId, String code);
}
```

`NoopSecondFactorProvider` is the default bean and always returns
`isRequired=false`, so login flow stays single-factor out of the box.
Swapping in a real provider is a **drop-in** change — the controller
(`AuthController.login`) already branches on `LoginResult.SECOND_FACTOR_REQUIRED`
and returns HTTP `202 Accepted` with a `challengeId` body:

```json
{ "status": "second_factor_required", "challengeId": "ch_abcd…", "methods": ["totp"] }
```

The React SPA must handle `202` and render a code entry step; the second
`POST /api/auth/login/second-factor { challengeId, code }` completes the
flow and mints `JCLAW_SESSION`.

## TOTP (RFC 6238) — Phase 1

### Schema

- [ ] **V41 migration**: `user_totp_secret`

  ```sql
  CREATE TABLE user_totp_secret (
      user_id          BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
      secret_encrypted BYTEA       NOT NULL,  -- AES-GCM via JceSecretEncryptor
      enabled          BOOLEAN     NOT NULL DEFAULT FALSE,
      created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
      last_used_at     TIMESTAMPTZ
  );
  ```
- [ ] **V41b migration**: `user_totp_recovery_code`

  ```sql
  CREATE TABLE user_totp_recovery_code (
      id          BIGSERIAL PRIMARY KEY,
      user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      code_hash   VARCHAR(128) NOT NULL,         -- bcrypt of the 10-digit code
      used_at     TIMESTAMPTZ
  );
  ```

### Backend

- [ ] Add `com.warrenstrange:googleauth:1.5.0` dependency in `javaclaw-security/pom.xml`.
- [ ] `TotpSecondFactorProvider implements SecondFactorProvider`:
  - `isRequired(user)` → `userTotpSecretRepository.findByUserId().enabled`.
  - `issueChallenge(user)` → stores `challengeId → userId` in in-memory
    `Caffeine` cache with 5-minute TTL; returns `SecondFactorChallenge`.
  - `verify(challengeId, code)` → loads secret, calls
    `GoogleAuthenticator.authorize(secret, code)` with ±1 window; falls
    back to recovery code lookup (`bcrypt.matches`); marks
    `last_used_at` / `used_at`.
- [ ] `TotpEnrollmentService`:
  - `startEnrollment(user)` → generates secret, stores with `enabled=false`,
    returns `otpauth://` URI and QR PNG (via `zxing`).
  - `confirmEnrollment(user, code)` → verifies; if OK sets `enabled=true`
    and generates 10 one-time recovery codes (returned **once**).
- [ ] REST endpoints (all require active session + CSRF):
  - `POST /api/auth/totp/enroll` → `{ otpauthUri, qrPngBase64 }`.
  - `POST /api/auth/totp/confirm { code }` → `{ recoveryCodes: [...] }`.
  - `POST /api/auth/totp/disable { currentPassword }` → `204`.
  - `POST /api/auth/login/second-factor { challengeId, code }` → `200` with
    `JCLAW_SESSION` cookie set (reuses `SessionTokenService.issue`).

### Audit

- [ ] Every `enroll`, `confirm`, `disable`, `verify success/failure` writes
  to `auth_audit_log` with `event` in
  `{totp_enroll, totp_confirm, totp_disable, totp_success, totp_failure}`.
- [ ] Lock account after 5 consecutive failures in 10 minutes
  (reuse existing `LoginAttemptService`).

### Tests

- [ ] Unit: `TotpSecondFactorProviderTest`, `TotpEnrollmentServiceTest`.
- [ ] Integration: `AuthControllerTotpIT` — full two-step login flow.
- [ ] E2E: `TotpLoginE2ETest` — enroll via API, then login with generated code.

## Email OTP — Phase 2

- [ ] **V42 migration**: `otp_challenge`

  ```sql
  CREATE TABLE otp_challenge (
      id          VARCHAR(64) PRIMARY KEY,
      user_id     BIGINT NOT NULL,
      code_hash   VARCHAR(128) NOT NULL,
      channel     VARCHAR(16)  NOT NULL,  -- 'email' | 'sms'
      expires_at  TIMESTAMPTZ  NOT NULL,
      used        BOOLEAN      NOT NULL DEFAULT FALSE
  );
  ```
- [ ] `EmailOtpProvider implements SecondFactorProvider` — sends 6-digit
  code via `spring-boot-starter-mail`. Templates in
  `javaclaw-security/src/main/resources/templates/otp-email.html`.
- [ ] Endpoints:
  - `POST /api/auth/otp/email/send` (authenticated or challenge-bound).
  - `POST /api/auth/otp/email/verify { challengeId, code }`.
- [ ] Throttle: max 3 sends per hour per user; `Retry-After` header on 429.

## SMS OTP — Phase 3

- [ ] Pluggable `SmsGateway` SPI; default impl is `NoopSmsGateway`
  (logs only). Reuse `otp_challenge` table with `channel='sms'`.
- [ ] Config:

  ```yaml
  javaclaw.security.otp.sms:
    enabled: false
    gateway: noop   # or 'smsru', 'twilio' via separate modules
  ```

## WebAuthn / Passkeys — Phase 4

- [ ] Add `com.webauthn4j:webauthn4j-spring-security-core` dependency.
- [ ] **V43 migration**: `user_webauthn_credential(id, user_id,
  credential_id, public_key_cose, sign_count, aaguid, created_at)`.
- [ ] Registration ceremony endpoints:
  - `POST /api/auth/webauthn/register/options`
  - `POST /api/auth/webauthn/register/finish`
- [ ] Authentication ceremony endpoints:
  - `POST /api/auth/webauthn/assertion/options`
  - `POST /api/auth/webauthn/assertion/finish`
- [ ] `WebAuthnSecondFactorProvider` plugs into the same SPI — password
  login + passkey second step, or (future) passkey-only login.

## Acceptance Criteria (TOTP MVP)

- [ ] Admin can enroll TOTP on their own account via `/settings/security`.
- [ ] After enrollment, next login returns `202` and requires the 6-digit
  code; wrong code → `401`, right code → `JCLAW_SESSION` cookie.
- [ ] Recovery code consumed on use; each code works exactly once.
- [ ] All 1187+ existing tests stay green; no changes required in
  authorization / permission tests.

