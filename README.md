# bitsports

Scores, but chunkier. The 128bit family's pixel sports app: live scores,
standings and game-day quests for the teams you actually care about —
NHL first, every league pixelated. Part of the 128bit family.

**Plus: a real team-news feed.** Follow up to 4 teams per sport — any
team, any sport, typed freely, not a fixed league list — and `index.html`
pulls each team's real news live (Google News RSS via a Supabase edge
function, cached 15 minutes) and shows it as one feed, filterable by team.
Scores stay sample data until a live provider is picked (below); news is
real today.

> Product direction is Daniel's call — this README is a starter brief so
> Carlos (Claude Code) has something concrete to react to. The landing page
> is live, the team-news feature is live and working end to end, and the
> rest of the app (real scores, quests) is unstarted.

## Concept (starter — Daniel decides)

A sports app with two jobs:

1. **Be the best second screen in the room.** Live scores, game clocks,
   standings and schedules in big pixel digits. Favourite teams pinned
   first, alerts only when it matters — goals, lead changes, close
   games late. No grey spreadsheet energy, no sportsbook ad farm.
2. **Make game day a quest.** Checking in at puck drop, riding your
   team's win streak, surviving overtime — bitsports emits game events
   into the shared 128bit feed, and 128bitlife turns being a fan into
   quests and XP. Your fantasy players (from 128bitfantasy) get a star
   on the score line, so the game that matters twice is obvious.

**bitsports vs 128bitfantasy:** fantasy is the GM desk — *your* teams,
lineups, waivers, trades. bitsports is the scoreboard — *the* games,
scores, standings. Fans watch in bitsports; managers manage in
128bitfantasy. One feed underneath both.

## Open questions for Daniel

- **League order:** NHL first is the assumption (it's Daniel's world),
  then NFL, MLB, NBA — or all four at once with thinner coverage each?
  Recommendation: NHL deep, the rest scores-only until NHL feels done.
- **Data provider:** ESPN's public endpoints (free, no key, unofficial —
  fine for an MVP, can change without notice), TheSportsDB (free tier,
  community data), or a paid feed like API-Sports for reliability.
  Recommendation: ESPN to start behind a provider interface so swapping
  is a config change, not a rewrite.
- **How far does "live" go?** Scores + clock only, or play-by-play,
  shot charts, and win probability? Play-by-play multiplies data cost
  and complexity fast. Recommendation: scores/clock/standings for v1,
  scoring plays for NHL in v1.1.
- **Betting:** odds on the score line, yes or no? Recommendation: no —
  the family brand is the anti-sportsbook-ad experience — but it's
  Daniel's call, and affiliate revenue is real money.
- **Widgets:** a live-score home-screen widget is a natural citizen of
  bitlauncher — in scope for v1, or phase two?

## Architecture — real scores, pixel board

```
src/
  scores/      # Provider interface + adapters (ESPN first). One
               # normalized game model for every league: teams,
               # score, clock/period, status, scoring plays.
  board/       # The pixel scoreboard UI: league tabs, pinned teams,
               # game detail (scoring summary, next game), standings.
  alerts/      # Alert rules engine: goals, lead changes, close-late
               # games. User-set noise level; server or on-device
               # polling decision lives here.
  favourites/  # Teams + players followed. Player stars on score lines
               # read the shared feed for 128bitfantasy rosters.
  events.ts    # 128bit shared event schema — emit game.followed,
               # game.checkin, streak.win, recap.read, etc.
               # 128bitlife subscribes for quests + XP.
```

This folder structure is the target once the app has a real build step.
Today everything — including news — lives inline in `index.html`; `src/news/`
below is where that logic moves once there's a bundler.

## Team news — live today

```
index.html                           # team picker (localStorage, 4/sport cap) +
                                      # news feed UI, calls the edge function directly
supabase/team-news.sql               # public.team_news cache table (RLS: public read,
                                      # writes only via the function's service-role key)
supabase/functions/fetch-team-news/  # Deno edge function: Google News RSS -> normalized
                                      # JSON, 15-min cache, serves stale cache if the
                                      # upstream fetch fails
```

How it works: pick a team + type its sport/league freely (no fixed list —
genuinely any team, any sport). Known teams (a small curated set) get their
real colours as a flat two-colour pixel crest; anything else gets a stable
crest hashed from its name, so every team has a consistent identity mark
without needing a real logo. The client calls `fetch-team-news` with the
Supabase publishable key (safe to expose; that's its purpose); the function
checks its 15-minute cache, otherwise fetches and parses Google News RSS,
upserts the results into `team_news`, and returns them. No scraping of
arbitrary sites, no API key, no ToS risk.

Deployed to the shared "128bit play" Supabase project (the same one
`family-feed.sql` runs in) — additive only, nothing else there was touched.
`src/news/` in the tree above is where this moves once the app has a real
frontend build; for now it's inline so it ships today.

Rules:
- **Scores must be fast and right.** Cache aggressively, show the
  data's age honestly ("updated 12s ago"), and never present stale
  numbers as live. A wrong score is worse than a slow one.
- **Provider-swappable.** Every feed goes through the adapter
  interface. Unofficial endpoints break; the app shouldn't.
- **Respect the fan's attention.** Alerts are a promise. Default to
  few, make more one toggle away, never sell the alert slot.
- **Local-first favourites.** Teams, players and alert settings live
  on-device; sync is opt-in like the rest of the family.
- Never store raw credentials in the repo.

## Start-here brief for Claude

```
We're building bitsports — the 128bit family's pixel sports app:
"scores, but chunkier," plus a live team-news feed. Read this README
first. Team news (picker + Google News RSS edge function + feed UI) is
done — see "Team news — live today" above. Don't rebuild it.

Build order:
1. Provider call (ask Daniel): ESPN public endpoints behind an
   adapter interface (recommended), TheSportsDB, or paid API-Sports?
   League order is also Daniel's — NHL first assumed.
2. src/scores/ — normalized game model + ESPN adapter for NHL:
   today's games, live clock, finals, standings.
3. src/board/ — pixel scoreboard: league tabs (NHL live, others
   stubbed), pinned favourites, tap-for-detail scoring summary.
4. src/favourites/ — follow teams locally; star 128bitfantasy
   players on score lines via the shared feed.
5. src/events.ts — shared 128bit event schema; emit game.followed,
   game.checkin. (Check sibling repos for the schema stub.)
6. src/alerts/ — goals + close-late alerts with a noise-level
   setting. Widgets for bitlauncher come after alerts work.

Constraints: stale data labelled honestly, provider-swappable,
attention is sacred. Small commits. Never commit credentials.
```

## 128bit tie-in

bitsports is the family's arena: `game.checkin`, `streak.win`, and
`recap.read` events flow into the shared feed so 128bitlife can issue
game-day quests and XP. It also reads the feed — 128bitfantasy rosters
star your players on its score lines, and its live-score widget is a
first-class citizen on bitlauncher's home screen. Don't silo the data.

## Monetization (family default)

128bit Pro bundle covers bitsports. Core scores, standings and alerts
are free forever. Cosmetic IAPs (team pixel themes, mascot packs) and
Pro-tier depth (play-by-play, advanced stats) later, like the rest of
the family. No betting affiliate links unless Daniel explicitly
decides otherwise.
