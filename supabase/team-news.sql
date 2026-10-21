-- bitsports: cache of fetched team news. Public, no PII. Written only by the
-- fetch-team-news edge function (service-role key bypasses RLS); anyone can read.
-- Applied to the shared "128bit play" Supabase project — run once, safe to run again.
create table if not exists public.team_news (
  id bigint generated always as identity primary key,
  sport text not null,
  team_key text not null,
  team_name text not null,
  title text not null,
  link text not null,
  source text,
  published_at timestamptz,
  snippet text,
  fetched_at timestamptz not null default now(),
  unique (team_key, link)
);
create index if not exists team_news_team_key_fetched on public.team_news (team_key, fetched_at desc);
alter table public.team_news enable row level security;
drop policy if exists team_news_public_read on public.team_news;
create policy team_news_public_read on public.team_news for select to anon, authenticated using (true);
-- Writes go through the edge function's service-role key only; no insert/update policy for anon/authenticated.
