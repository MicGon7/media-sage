-- MS-664: past briefings / daily reflections sync
-- Run this once via the Supabase SQL editor before the app's sync path will work.
-- Client accesses this table directly (no appServer involvement), scoped per user via RLS.
-- Append-only: reflections are generated once and never edited, so there is no tombstone column.

create table if not exists daily_reflection (
  user_id uuid not null references auth.users(id) on delete cascade,
  epoch_day bigint not null,
  tone text not null,
  theme text not null,
  figure_server_id bigint not null,
  scripture_reference text not null,
  scripture_text text not null,
  insight text not null,
  implication text not null,
  inspiration text not null,
  sources jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now(),
  primary key (user_id, epoch_day, tone, theme)
);

alter table daily_reflection enable row level security;

create policy "Users manage their own daily reflections"
  on daily_reflection
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
