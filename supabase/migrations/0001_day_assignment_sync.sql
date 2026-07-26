-- MS-51: weekly reporter schedule sync
-- Run this once via the Supabase SQL editor before the app's sync path will work.
-- Client accesses this table directly (no appServer involvement), scoped per user via RLS.

create table if not exists day_assignment (
  user_id uuid not null references auth.users(id) on delete cascade,
  day_of_week integer not null,
  figure_server_id bigint not null,
  lens text,
  updated_at timestamptz not null default now(),
  primary key (user_id, day_of_week)
);

alter table day_assignment enable row level security;

create policy "Users manage their own day assignments"
  on day_assignment
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
