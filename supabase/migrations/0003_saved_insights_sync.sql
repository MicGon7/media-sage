-- MS-666: saved insights (bookmarked encouragements) sync
-- Run this once via the Supabase SQL editor before the app's sync path will work.
-- Client accesses this table directly (no appServer involvement), scoped per user via RLS.
-- Full content snapshot, not a join table: a saved insight must render on a device that never
-- generated (or cached) that match, so the row carries the whole encouragement, not just a flag.

create table if not exists saved_insight (
  user_id uuid not null references auth.users(id) on delete cascade,
  article_url text not null,
  figure_server_id bigint not null,
  summary text,
  quote_text text not null,
  figure_name text not null,
  figure_role text not null,
  scripture_reference text not null,
  scripture_text text not null,
  explanation text not null,
  connection_themes jsonb not null default '[]'::jsonb,
  match_theme text not null,
  tone text not null,
  figure_image_url text,
  headline_title text not null default '',
  headline_source text not null default '',
  headline_image_url text,
  updated_at timestamptz not null default now(),
  primary key (user_id, article_url)
);

alter table saved_insight enable row level security;

create policy "Users manage their own saved insights"
  on saved_insight
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
