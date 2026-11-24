# Authentication module (`workflow-engine-auth`)

Standalone auth module for the workflow engine. Provides real user login with
strong password hashing and signed, revocable session tokens — replacing the
original "any `X-User-Id` header is trusted" behaviour.

## Why a separate module

- **Boundary is explicit**: users, hashing, tokens and revocation live in one
  place, independent of the engine and the server.
- **Embed anywhere**: it auto-configures when on the classpath; the server
  depends on it as a library (single process, no extra deployment).
- **Backwards compatible**: the legacy `X-User-Id` header still works for
  service callers (MCP, E2E) via a switch, so existing integrations keep running.

## Quick start (default config)

A few users are seeded from `application.yml` for local development:

| Username | Password   | Roles  |
|----------|------------|--------|
| admin    | admin123   | ADMIN  |
| testuser | test123    | USER   |

Open the web app — you'll be sent to the login page. After signing in, a
session cookie (`wf_session`, HttpOnly + SameSite=Lax) is set and every API
call carries it automatically.

## HTTP API

| Method | Path                | Auth | Purpose |
|--------|---------------------|------|---------|
| POST   | `/api/auth/login`   | no   | `{username, password}` → `{token, user, expiresInSeconds}`, sets cookie |
| POST   | `/api/auth/register`| no   | `{username, password, displayName?, email?}` → same as login, sets cookie, `201` |
| POST   | `/api/auth/logout`  | yes  | revokes token server-side, clears cookie |
| GET    | `/api/auth/me`      | yes  | `{authenticated, user, source}` |
| GET    | `/api/auth/status`  | no   | `{enabled, headerCompat, registrationEnabled}` probe |

`source` is `token` (cookie / Bearer) or `header` (legacy `X-User-Id`).

### Registration

Public self-signup is on by default. Rules enforced server-side:

- Username: 3–32 chars, `[A-Za-z0-9_.-]` only, case-insensitively unique.
- Password: at least `auth.min-password-length` (default 6) chars, BCrypt-hashed.
- New accounts receive only `auth.default-roles` (default `[USER]`) — never ADMIN.
- On success the account is signed in immediately (cookie set, `201` + token body).
- Errors: `400` invalid input, `409` duplicate username, `403` when disabled.

**Persistence**: registered users live in the `auth_user` table via `JdbcUserStore`
(auto-created, self-seeded from `auth.users`) on every non-`memory` profile, so
they survive restarts. The `memory` profile uses the in-memory store (users are
ephemeral — fine for E2E/demo). Disable signup entirely with
`auth.registration-enabled: false` (or `AUTH_REGISTRATION_ENABLED=false`) for
invite-only deployments.

## Configuration

All settings live under `auth.*` (see `application.yml`):

| Key | Default | Meaning |
|-----|---------|---------|
| `auth.enabled` | `true` | master switch |
| `auth.secret` | dev value | **HMAC signing secret — override in production** |
| `auth.token-ttl` | `8h` | token lifetime (`30m`, `8h`, `7d`, …) |
| `auth.cookie-name` | `wf_session` | session cookie name |
| `auth.cookie-secure` | `false` | set `Secure` flag (enable behind HTTPS) |
| `auth.header-compat-enabled` | `true` | accept legacy `X-User-Id` as identity |
| `auth.registration-enabled` | `true` | allow public self-registration |
| `auth.default-roles` | `[USER]` | roles granted to registered users |
| `auth.min-password-length` | `6` | minimum password length on signup |
| `auth.users[*]` | — | bootstrap users |

### Production hardening

1. Put real credentials in `application-local.yml` (gitignored) or env vars:
   ```yaml
   auth:
     secret: <long random string, 32+ chars>
     cookie-secure: true
     header-compat-enabled: false
     users:
       - username: alice
         password-hash: '$2a$10$...'   # BCrypt hash, no plaintext in config
         roles: [ADMIN]
   ```
   Generate a hash with any BCrypt tool (e.g. `htpasswd -bnBC 10 "" <password>`).
2. Set `header-compat-enabled: false` once MCP/E2E callers are migrated to
   Bearer tokens — then only real logins are accepted.
3. Terminate TLS at the proxy and keep `cookie-secure: true`.

## How it works

- `AuthenticationFilter` resolves identity per request, in priority order:
  1. session cookie `wf_session`
  2. `Authorization: Bearer <token>`
  3. `X-User-Id` header (only when `header-compat-enabled`)
- On success it writes the identity into a `X-User-Id` request header via a
  wrapper — existing controllers and `TenantGuard` need no changes.
- Tokens are compact HMAC-SHA256 signed payloads (`sub`, `jti`, `iat`, `exp`).
  Logout adds the `jti` to an in-process revocation set, so the token dies
  immediately rather than waiting for expiry.
- Passwords are BCrypt-hashed on load; plaintext values in config are never
  retained.

> **Note:** the revocation set is in-memory. On server restart, tokens issued
> before the restart remain valid until expiry (their signature/expiry are
> still checked). For cross-restart revocation, back the set with Redis — the
> hook is `TokenService.revoke/verify`.

## Tests

- `workflow-engine-auth`: `AuthModuleTest` — hashing, token round-trip,
  tamper/expiry/revocation.
- `workflow-engine-server`: `AuthIntegrationTest` — filter paths, header
  fallback, compat switch, public endpoints.
- Web E2E: `auth-login.spec.ts` (browser flow) + `auth.spec.ts` (API).
