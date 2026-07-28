-- MS-669: memorized quote sync
-- Run this once via the Supabase SQL editor before the app's sync path will work.
-- Client accesses this table directly (no appServer involvement), scoped per user via RLS.
-- Singleton-per-user row: only one quote is ever memorized at a time, so pushing a new pin
-- simply upserts (replaces) this user's single row — there is no delete/tombstone action, since
-- this feature has no "unmemorize to nothing", only "replace with a different pin".

create table if not exists memorized_quote (
  user_id uuid not null references auth.users(id) on delete cascade,
  figure_server_id bigint not null,
  quote_text text not null,
  source text not null default '',
  themes jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now(),
  primary key (user_id)
);

alter table memorized_quote enable row level security;

create policy "Users manage their own memorized quote"
  on memorized_quote
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
