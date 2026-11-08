# BirChat Beta — Step 3: JWT identity binding and access control

This patch must be applied **on top of Step 2**. Set `SPRING_PROFILES_ACTIVE=beta`. It contains the corrected `application-beta.yml` from the earlier JWT config fix. Do not use production credentials or deploy this patch to production.

## What changed

- `BetaIdentityService` validates the signed JWT `sub`, `typ=access`, and checks that the user still exists and `is_active=true`.
- All 7 legacy business controllers are enabled only outside beta (`@Profile("!beta")`); no change to production mappings/logic.
- 7 new beta controllers exclusively derive the acting user ID from the verified JWT. Any supplied query or JSON identity UUID is **optional** for migration, and if present **must match** the token (otherwise `403 FORBIDDEN`). This means both old and new Flutter request formats can work.
- Existing Company/Chat/Employee/File/User/Search/AI services continue to enforce active company membership. Beta employee creation and AI director summary now require active `DIRECTOR` membership in addition to valid JWT.
- Beta Spring Security uses an explicit HTTP method/path allowlist, requiring a valid JWT and denying all unrelated or future routes by default.
- No new DB migration: V10 is still the latest.

## How to apply

1. Commit/backup your existing code and copy the patch into the project root.
2. Keep the existing beta environment variables unchanged. Do not replace the already generated JWT or OTP secrets.
3. Maven `clean test` and run with `beta`. A clean build is important because both old and new controller beans must obey their profile annotations.
4. Get a new JWT through `POST /api/auth/send-code` and `POST /api/auth/verify-code`.
5. In Swagger Authorize, pass `Bearer <accessToken>` if a security scheme appears; otherwise run the PowerShell test below. **Never paste real tokens in chats or logs.**

## API compatibility

- `GET /api/companies/my?userId=...` — optional `userId`, same for home/read/files/search/AI and `/api/users/me`.
- `POST /api/companies` — optional `ownerUserId` (rest of body unchanged).
- `POST /api/companies/{companyId}/chats/general/messages` — optional `userId` JSON; `text` required.
- `POST /api/companies/{companyId}/chats/general/messages/file` — optional `userId` JSON; `fileId` required.
- `POST /api/companies/{companyId}/employees?actorUserId=...` — optional `actorUserId`, only director succeeds.
- Responses, URLs, and service behaviors remain the same except the new authorization rules.

## Smoke tests (PowerShell)

Set a local variable in a **local** PowerShell session (do not paste the token into this chat):

```powershell
$base = 'http://localhost:8080'
$jwt = Read-Host 'Access Token'
$headers = @{ Authorization = "Bearer $jwt" }

# 200 for the current user's companies
Invoke-RestMethod "$base/api/companies/my" -Headers $headers

# 403 for a forged userId, even with a valid JWT
$someoneElse = [guid]::NewGuid().ToString()
try {
  Invoke-RestMethod "$base/api/companies/my?userId=$someoneElse" -Headers $headers
  throw 'SECURITY FAILURE: forged userId was accepted'
} catch {
  if ($_.Exception.Message -like 'SECURITY FAILURE*') { throw }
  $status = [int]$_.Exception.Response.StatusCode
  if ($status -ne 403) { throw "Expected 403 but got $status" }
  Write-Host 'Forged userId rejected (403): OK'
}

# 401 without a JWT
try {
  Invoke-RestMethod "$base/api/companies/my"
  throw 'SECURITY FAILURE: endpoint allowed anonymous access'
} catch {
  if ($_.Exception.Message -like 'SECURITY FAILURE*') { throw }
  $status = [int]$_.Exception.Response.StatusCode
  if ($status -ne 401) { throw "Expected 401 but got $status" }
  Write-Host 'Anonymous request rejected (401): OK'
}
```

## Boundaries and follow-ups

- Other users may still add an employee using an existing service in *non-beta* profiles; the new `DIRECTOR` restriction is specifically enforced at the beta controller boundary. Production logic was intentionally not modified.
- Existing files are stored in Supabase. Beta uploads require **separate beta** Supabase configuration. Do not point the beta bucket/service key at prod.
- Existing `AI Director` background summarization is independent of the protected HTTP endpoint; there is no additional scheduler security in this step.
- Once the Flutter app migrates, remove legacy userId/ownerUserId/actorUserId parameters rather than keeping them forever.
- Testing JWT status and role permissions under concurrent user/role changes is a further hardening step. Avoid exposing beta externally until it is completed and demo OTP is removed or isolated.

## Verification caveat

The archive was statically inspected, but Maven is unavailable in this artifact environment. Run `mvn clean test` in IntelliJ before startup and report the first compilation error if any. Automated unit tests for identity mismatch, inactive user, wrong token type and director role are included.
