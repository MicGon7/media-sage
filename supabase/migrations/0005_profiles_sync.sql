-- MS-681: profiles sync
-- Run this once via the Supabase SQL editor before the app's sign-up path will populate it.
-- Client accesses this table directly (no appServer involvement), scoped per user via RLS.

create table if not exists profiles (
  user_id uuid not null references auth.users(id) on delete cascade,
  display_name text not null,
  created_at timestamptz not null default now(),
  primary key (user_id)
);

alter table profiles enable row level security;

create policy "Users manage their own profile"
  on profiles
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
