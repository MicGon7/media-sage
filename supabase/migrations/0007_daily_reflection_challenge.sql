-- MS-688: add nullable reflection challenge question to daily_reflection sync
-- Reflections generated before this change simply have a null challenge.

alter table daily_reflection add column if not exists challenge text;
