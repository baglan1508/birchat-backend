# BirChat Beta v2 — JWT, iteration 1

Only profile `beta` uses JWT. Production is unchanged except backward-compatible optional response fields and added endpoints (refresh/logout will not work on prod until implemented there). Do not deploy this version to prod yet.

## Apply

Copy patch paths into your existing backend, overwriting the temporary `config/SecurityConfig.java` if you created it in IntelliJ. Keep `.git` and secrets out of archives.

Add `BETA_JWT_SECRET_BASE64` to IntelliJ's beta run environment using a fresh 32-byte random value:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Never reuse OTP secret as JWT signing secret. Never check either into git.

The startup will automatically run `V10__create_refresh_tokens.sql` on the EMPTY/ISOLATED Neon beta database. Do not apply it against the current production.

### Endpoints

- POST `/api/auth/send-code` — same request/response. Beta SMS disabled except configured demo phone.
- POST `/api/auth/verify-code` — original fields + `refreshToken`, `tokenType`, `expiresIn` in beta, `accessToken` is signed JWT. OTP valid for 5 min, max 5 tries.
- POST `/api/auth/refresh` with `{ "refreshToken": "..." }` — rotated access/refresh tokens.
- POST `/api/auth/logout` with `{ "refreshToken": "..." }` — revoke refresh token; does NOT immediately revoke already issued access JWT; TTL 15 min.
- GET `/api/auth/me` with `Authorization: Bearer <accessToken>` — user from verified JWT claims.

### Security boundary

All existing companies/chats/files/AI APIs are deliberately DENIED on beta until phase 2 binds every userId, actorUserId and ownerUserId to authenticated principal. The JWT cannot safely be used there without this audit. Swagger and health remain public.

### OTP rollback correction

`AuthService.verifyCode` no longer wraps the OTP validation in an outer transaction; `SmsCodeService.verifyCode` commits increments even when ApiException is thrown. This fixes rollback of failed-attempt counts. A race-concurrency hardening pass (locking OTP rows) is recommended before public release.

### Minimal smoke test

1. Startup logs should show successful Flyway migration version 10.
2. Query `select count(*) from birchat.refresh_tokens;` (table exists).
3. Configure beta demo phone and code; call POST send-code, then verify-code.
4. Verify response returns JWT starting `eyJ`, `refreshToken`, `expiresIn: 900`.
5. Call GET `/api/auth/me` with Bearer token (200); without token (401).
6. Call refresh; check old refresh token no longer works; call logout with new refresh token.
7. Confirm GET `/api/companies` is denied on beta; no trusted-userId endpoints exposed yet.

### Not implemented yet

Telegram Gateway and identity-binding across company/chat/file/AI are separate follow-on patches. We have not run Maven or live Neon tests in the artifact environment; compile and run against beta before deployment.
