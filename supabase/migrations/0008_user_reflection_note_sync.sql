-- MS-736: user's own reflection note sync
-- Run this once via the Supabase SQL editor before the app's sync path will work.
-- Client accesses this table directly (no appServer involvement), scoped per user via RLS.
-- note_text carries the client-side-encrypted form only (MS-737) — never plaintext.

create table if not exists user_reflection_note (
  user_id uuid not null references auth.users(id) on delete cascade,
  id text not null,
  note_text text not null,
  updated_at_millis bigint not null,
  primary key (user_id, id)
);

alter table user_reflection_note enable row level security;

create policy "Users manage their own reflection notes"
  on user_reflection_note
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
