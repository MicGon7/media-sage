-- MS-740: shared per-account reflection-note encryption key
-- Run this once via the Supabase SQL editor before cross-device note sync will actually decrypt.
-- key_material is Base64-encoded raw AES-256 key bytes. This table is the only place this key
-- exists outside a device's own secure local storage — protected by RLS the same as every other
-- per-user table, and only ever inserted (never updated), so a primary-key conflict is how a
-- racing second device detects it lost the one-time key-provisioning race and must adopt this row.

create table if not exists user_reflection_note_key (
  user_id uuid not null references auth.users(id) on delete cascade,
  key_material text not null,
  created_at timestamptz not null default now(),
  primary key (user_id)
);

alter table user_reflection_note_key enable row level security;

create policy "Users manage their own reflection note key"
  on user_reflection_note_key
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
