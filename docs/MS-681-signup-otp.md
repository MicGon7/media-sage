# MS-681: Sign-Up with Email OTP Verification and a Profiles Table

## What Changed
Added a sign-up path alongside the existing sign-in flow. A new user enters email, password, and
display name; Supabase sends a 6-digit code by email instead of a magic link; entering the code
completes verification and creates a `profiles` row via the same direct-to-Supabase RLS pattern
established in MS-51 (`day_assignment`, `saved_insight`, `memorized_quote`).

## Why OTP Instead of a Magic Link
A code stays fully in-app — no deep-link handling to build, no context switch to Mail and back.
Supabase's `verifyEmailOtp` API treats `MAGIC_LINK` and `SIGNUP` as the same underlying mechanism
(`OtpType.Email.SIGNUP`), so this is a template-configuration choice on the Supabase project (send
a numeric code, not a link) rather than a different client-side flow — the sign-up template in the
Supabase dashboard needs to be set to send `{{ .Token }}`, not `{{ .ConfirmationURL }}`.

## The Two-Step Supabase Auth Call
`client.auth.signUpWith(Email) { email; password; data = buildJsonObject { put("full_name", ...) } }`
creates the (unverified) account and stores the display name in `user_metadata.full_name` — the
same key `AuthRepositoryImpl.currentSession()`/`observeAuthState()` already read for `UserSession.displayName`,
which had no writer until this ticket. Verification is a separate call,
`client.auth.verifyEmailOtp(type = OtpType.Email.SIGNUP, email, token)`, which only then establishes
an authenticated session — RLS requires that session, so the `profiles` row can only be pushed
*after* verification succeeds, not at sign-up time.

## Why a `profiles` Table Instead of Just `user_metadata`
`user_metadata` alone would have satisfied this ticket's AC (display name visible after sign-up)
with zero new infrastructure. A `profiles` table was chosen instead — mirroring the `day_assignment`
/ `saved_insight` / `memorized_quote` shape exactly — because `user_metadata` is private to the
owning session and can't be queried or joined from Postgrest, so any future feature that needs to
show *another* user's display name (a leaderboard, shared content, etc.) would require a server
endpoint to expose it. A `profiles` row is a normal queryable table today, with a more permissive
read policy available later if that need arises. The table is write-only from the client for now —
no `fetch()` was added to `ProfileRemoteDataSource`, since `UserSession.displayName` (from
`user_metadata`) already covers this ticket's read path and an unused method would be dead code.

## What This Doesn't Cover Yet
- **Not yet smoke-tested against a live Supabase project.** The `signUpWith`/`verifyEmailOtp` API
  shapes were verified against the `auth-kt` 3.6.0 sources directly (extracted from the Gradle
  cache), and the code compiles on both Android and iOS targets, but the actual round-trip against
  Supabase's servers — including the OTP-vs-magic-link email template setting — has not been run
  live. This needs manual verification before merge.
- **The `0005_profiles_sync.sql` migration has to be run manually** via the Supabase SQL editor,
  same as every prior migration in `supabase/migrations/` — there's no automated migration runner.
- **Editing a profile after sign-up** isn't in scope — the Settings "Edit Profile" row now displays
  the name but doesn't yet let a user change it.
