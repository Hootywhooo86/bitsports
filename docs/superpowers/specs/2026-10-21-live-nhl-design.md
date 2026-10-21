# bitsports live NHL — design

Date: 2026-10-21 · Status: draft for approval

## Intent

bitsports should be the thing you follow your team with on game night. Right now its
Scores section is three hard-coded sample games. This project makes it real for hockey:
live NHL scores and a live game screen, plus phone alerts as the game happens: a heads-up
before puck drop, every goal with all its details and a fun pixel GOAL moment, the replay
link when it's posted, end-of-period stats, and an AI-written recap of each period
produced on the phone by bitcall's local models.

The family's tone applies: chunky pixel art, fun, never a sportsbook-style ad farm.
Everything the user sees or hears can be customized.

## Goals

1. **Real NHL data** replaces the sample games in Scores on Android and on the web page.
2. **Live game screen** (Android and web): score, clock, shots, power plays; tabs for
   Plays (goals, penalties, period ends; each goal has a Watch button once its clip
   exists), Shot map (pixel rink with every shot and goal by team, filterable by period
   and player), and Periods (per-period stats; on Android also that period's AI recap).
   Recaps are Android-only: the web Periods tab shows stats only. On Android, followed
   games get recaps automatically; any other game shows a "Write recap" button per
   finished period that asks bitcall on demand (hidden when bitcall isn't available).
3. **Android alerts** for games involving a team the user follows:
   - **30 minutes before puck drop:** "Oilers @ Flames starts in 30 min".
   - **Every goal:** the full goal info: scoring team and new score; scorer and season
     goal total; both assists with their season totals where available; strength (even,
     power play, shorthanded, empty net); shot type; goalie scored on; period and time;
     the pixel GOAL picture as the notification's image; the goal sound.
   - **Goal replay:** a follow-up "Watch [scorer]'s goal" alert when the replay clip link
     first appears for that goal (it shows up minutes later); tapping opens the clip.
   - **End of each period** (including OT): score and period stats (shots, power plays,
     hits, faceoff %, period goals).
   - **AI period recap:** 3-4 sentences, punchy and fun, written by bitcall's local model
     from the period's events and stats; arrives as its own alert after the stats one.
   - **Final:** the final score with the three stars if the feed provides them.
4. **Mini scoreboard notification**: while a followed game is live, a persistent
   notification shows crests, abbreviations, score, LIVE marker, period and clock, and
   shots on goal, with "Open game" and "Stop following" actions. It disappears at the final.
   "Stop following" stops watching this one game only (ends the watcher, no more alerts
   for it tonight); the team stays followed and its news continues. Label it so that's
   clear (e.g. "Stop for tonight").
5. **GOAL moment**: when a goal is detected while the app is open, a short full-screen
   pixel animation plays with the horn. Four styles, chosen in Customize:
   **A Goal lamp** (red goal light spinning and flashing), **B Team colour burst** (screen
   floods with the scoring team's colours, giant pixel GOAL!), **C Puck in the net** (the
   logo's puck rips into a shaking net), and **Basic text** (no animation). The approved
   mockups are in `.superpowers/brainstorm/*/content/live-mockups.html` (local, gitignored);
   the build should match them in spirit and go through `/design-taste-frontend` for polish.
6. **Goal horn**: an original short 8-bit horn plus siren wail, generated (not copied from
   a real arena horn), used for the goal alert's notification sound and the in-app moment.
7. **Customize additions** (both platforms where they apply): GOAL style (A/B/C/Basic),
   goal sound on/off, and each alert type on/off (pre-game, goals, replays, period stats,
   AI recap, final). Saved on device, like the existing Customize options.
8. **bitcall becomes the family AI brain**: bitcall gains a way for other 128bit apps to
   send it a prompt and get generated text back from whatever model the user downloaded
   in bitcall. bitsports uses it for period recaps.
9. **One signing key for the 128bit apps**, so apps can trust each other (point 8) and so
   new builds install over old ones without uninstalling. The family key **is the existing
   Bitcall release key** (bitcall repo secrets `BITCALL_KEYSTORE_BASE64`,
   `BITCALL_KEYSTORE_PASSWORD`, `BITCALL_KEY_ALIAS`, `BITCALL_KEY_PASSWORD`), so bitcall
   installs keep updating in place. **In this project only bitcall and bitsports** use it;
   other family apps adopt it when they're next built. bitsports gets a release signing
   config and CI that signs with it from its own repo secrets, already copied from bitcall
   on 2026-10-21: `FAMILY_KEYSTORE_BASE64`, `FAMILY_KEYSTORE_PASSWORD`, `FAMILY_KEY_ALIAS`,
   `FAMILY_KEY_PASSWORD`. If they're ever missing, CI should fail loudly with a one-line
   explanation rather than silently ship a debug-signed APK.
   Existing bitsports installs (debug-signed) need one uninstall; say so in the release
   notes.

## Non-goals

- Other sports. Hockey (NHL) only. Leave the scores code shaped so another league can be
  added later, but don't build that seam out beyond what NHL needs.
- Notifications on the web page.
- Accounts, a server that watches games, or push through Google/Firebase. Everything
  runs on the phone.
- Betting odds or any sportsbook content.
- Play-by-play for games that don't involve a followed team (the live screen works for any
  game the user opens, but alerts and the background watcher are only for followed teams).
- iOS.

## Anti-goals (would make this a failure even if it works)

- **A wrong or stale score shown as live.** The README rule stands: a wrong score is worse
  than a slow one. If the feed hasn't updated recently, say so ("updated 40s ago").
- **Double alerts.** The same goal must never alert twice, including across app restarts
  and when the feed later revises a goal (scorer correction, assist change). A goal that's
  later disallowed should be reflected on the live screen; don't spam a second alert.
- **Silent failure.** If alerts can't run (notification permission denied, battery
  optimization killing the watcher, bitcall not installed), the app tells the user in one
  honest sentence and the rest still works.
- **The AI inventing facts.** The recap prompt gets only the period's real events and
  stats and must not add names, numbers or events that aren't in them. If bitcall isn't
  available, skip the recap alert (the stats alert still goes out); never fake one.
- **Draining the battery outside games.** The fast watching only runs for a followed game
  from shortly before puck drop until the final.

## Constraints and facts already established

**Data source: the NHL's public web API, `https://api-web.nhle.com/v1/`** (free, no key,
unofficial: can change without notice). Verified 2026-10-21:

- `score/now` redirects (307) to `score/YYYY-MM-DD`. Each game has `id`, `gameState`
  (FUT, PRE, LIVE, CRIT, OFF, FINAL), `startTimeUTC`, `awayTeam`/`homeTeam` (abbrev, score),
  `clock`, `period`, `periodDescriptor`, and `goals[]`. Each goal carries `name`,
  `teamAbbrev`, `goalsToDate`, `assists[]`, `strength` (ev/pp/sh), `goalModifier`,
  `timeInPeriod`, `awayScore`/`homeScore`, and `highlightClipSharingUrl` (the replay link,
  present once the clip exists).
- `gamecenter/{id}/play-by-play` has `plays[]` with `typeDescKey` (goal, shot-on-goal,
  missed-shot, blocked-shot, penalty, period-end, game-end, hit, faceoff, …), each with
  `details.xCoord`/`yCoord`, `shotType`, `goalieInNetId`, player ids, and running SOG.
  `rosterSpots` maps player ids to names. `homeTeamDefendingSide` tells which end is which.
- Responses carry `Cache-Control: max-age=17`, so polling faster than ~15s gains nothing.
- **No CORS headers.** A browser page can't call it directly. The web page goes through a
  new Supabase edge function (same project and pattern as `fetch-team-news`) that proxies
  only these NHL paths with a short cache. Android calls the NHL API directly.

**Matching followed teams.** Users type teams freely ("Edmonton Oilers", "oilers", "EDM").
NHL alerts need each followed hockey team mapped to an NHL team abbreviation; unmatched
free-text teams just don't get NHL alerts (news still works for them).

**Background watching (Android).** User chose the on-phone approach and accepted a
persistent notification during games, styled as the mini scoreboard. That notification
belongs to a foreground service that polls every ~15s while a followed game is live.
The 30-minute heads-up and starting the watcher need the day's schedule checked ahead of
time (e.g. a daily check and when the app opens) and exact-ish alarms; the 30-minute
warning should fire within about a minute of the right time.

**Existing code to build on.** bitsports web is one inline `index.html` (no build step).
Android is Kotlin + Jetpack Compose (`android/`, compileSdk 34, minSdk 26, AGP 8.5.2,
Kotlin 1.9.24), with `TeamsStore`/`PrefsStore` SharedPreferences, `NewsRepository`, and
the redesigned `MainActivity.kt` on branch `family-redesign` (not yet merged to main).
Design rules: 128bit family look from `128bitfit/prototype/app-shell.html` (black,
monochrome chrome, colour only on data, Silkscreen + Inter), plus the user's standing
rule that everything is customizable.

**bitcall** (`Hootywhooo86/bitcall`, the app code lives on branch
`claude/zen-curie-7sxzl9`, released as 0.2.x-pre APKs signed with "the Bitcall release
key"). Its `ai/` package has `BitAi`, `LlamaEngine` (llama.cpp via JNI, `generate(system,
user, maxTokens, temperature)`), `ModelCatalog` (Qwen 0.6B-4B GGUF) and `ModelManager`
(models in bitcall's app-specific external storage, which other apps can't read on
Android 11+). Respect bitcall's existing rules: AI off by default, the main model is
released during calls, and AI work must not interfere with an active call.

## Decided by the user

- Hockey only for now; NHL.
- Alerts: 30-min pre-game, every goal, goal replay link, end-of-period stats, AI period
  recap. Shot heat map on the live screen.
- Goal alerts include all the goal info.
- AI recaps come from the user's own downloaded models in bitcall; change bitcall to make
  that possible.
- Same signing key for the 128bit apps: reuse the Bitcall release key; bitcall and
  bitsports in this project, other apps later.
- Live scores on Android and web; alerts on Android.
- On-phone watching with a persistent notification during games, made into a mini
  scoreboard.
- GOAL moment: all three animated styles plus basic text, user picks in Customize.
- Everything pixel, fun, and polished via `/design-taste-frontend`.

## Calls made by Claude (user can override)

- NHL `api-web.nhle.com` instead of ESPN's endpoints (README's earlier suggestion),
  because it has shot coordinates and replay links, which the heat map and video alerts
  need.
- No server and no Firebase.
- Recap voice: punchy, fun, 3-4 sentences, facts only from the supplied data.
- bitcall exposes its AI to other apps as an on-device interface guarded so only apps
  signed with the family key can use it.
- AI recaps are Android-only; the web page shows period stats without a recap. Unfollowed
  games get an on-demand "Write recap" button instead of automatic recaps.
- "Stop following" on the mini scoreboard means stop watching tonight's game, not unfollow
  the team.

## Left to the builder

- How the bitcall interface is shaped (bound service with AIDL/Messenger, a
  ContentProvider, or similar), as long as only family-signed apps can call it, a request
  can't run while bitcall is in a call, and a long generation can't block bitsports.
- How followed-team text maps to NHL teams (a bundled list of the 32 teams with names,
  cities and nicknames is fine), and how the picker nudges users toward a match.
- Polling cadence details within the ~15s floor, back-off on errors, and how "updated Ns
  ago" is shown.
- How goals are de-duplicated (e.g. by game id + event id) and persisted.
- Sound synthesis approach for the horn, and the exact animation implementation per
  platform (Compose Canvas on Android, CSS/SVG on web).
- Data model and module layout (the README's `src/scores/`, `board/`, `alerts/` layout is a
  target, not a requirement for the inline web page).
- Whether the proxy edge function caches in memory or in a table.
- Tests: the parsing of NHL responses, goal de-duplication, team matching and alert
  decisions should be unit tested against saved real responses.
