-- Discovered quote sync
-- Run this once via the Supabase SQL editor before the app's sync path will work.
-- Client accesses this table directly (no appServer involvement), scoped per user via RLS.
-- Every quote a user is matched with, bookmarked or not, gets a row here — decoupled from any
-- headline/article, unlike saved_insight. Append-only: quotes are never removed once discovered.

create table if not exists discovered_quote (
  user_id uuid not null references auth.users(id) on delete cascade,
  figure_server_id bigint not null,
  quote_text text not null,
  source text not null default '',
  themes jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now(),
  primary key (user_id, figure_server_id, quote_text)
);

alter table discovered_quote enable row level security;

create policy "Users manage their own discovered quotes"
  on discovered_quote
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
