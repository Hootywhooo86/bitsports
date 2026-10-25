## bitsports 0.9.0 — your teams' games in Bitcal

- **Every game of your NHL teams, on your calendar.** Sign in once under **Settings → Bitcal**, with the same 128bit account you use for Bitcal and 128bit Play. bitsports then adds a **"bitsports · NHL"** calendar to Bitcal with the whole regular season for every NHL team you follow: home games as "Oilers vs Hurricanes", away games as "Oilers @ Flames", with the arena, TV channels and your radio call. Playoff games are added once they're scheduled. Preseason games are left out.
- **Keeps itself up to date.** Changed start times, postponements and final scores ("Oilers 6–5 Hurricanes · W (SO)") update every morning, after each of your games, whenever you open the app, and right away when you change teams. If you stop following a team, its games are removed. Any game you edit yourself in Bitcal is left alone.
- **Update now**, an on/off switch, sign out, and "Remove the games from Bitcal" are all in Settings.

## From 0.8.2 — live widgets keep pace with the app

- **During a live game the widgets now refresh as often as the app**: right after each new copy the NHL publishes, about every 20 seconds, instead of every 30. The app's scores list now runs on the same timing, so the widget and the app change together.
- The NHL's free feed only publishes a new copy about every 20 seconds, so that's the fastest anything built on it can update.

## From 0.8.1 — widgets that keep themselves current

Widgets no longer wait for you to open the app.

- **Always on a schedule.** While you have a bitsports widget, it refreshes every 30 seconds while a game it shows is live, every minute in the half hour before puck drop, and every 15 minutes the rest of the time, so finals and the next day's games turn up on their own.
- **Fixed: updating bitsports or restarting your phone stopped widget refreshes.** Android wipes an app's alarms on both; the widgets' timer is now set again straight away.
- **Fixed: one failed check left widgets stale for up to 30 minutes.** A miss (phone waking up, switching between Wi-Fi and data) now retries in 2 minutes.

## From 0.8.0 — listen live, and plays as soon as the NHL posts them

- **♪ Listen live.** The game screen has a Listen live button for your team's radio call, and the live mini scoreboard in your notifications has one too. Oilers games open **880 CHED**'s official player, which keeps playing with the screen off. Other teams get a TuneIn search for their radio call, or paste your own station's listen-live link in **Settings → Goals + alerts → Radio**.
- **Faster live updates.** The NHL's public feed publishes a fresh copy about every 20 seconds, so second-by-second isn't possible from it. Live games now check right as each new copy comes out, instead of on a fixed 15-second timer that could leave you up to 35 seconds behind. New plays, goals and the clock show up within about a second of the NHL posting them, in the app, in goal alerts and on the mini scoreboard.

## From 0.7.4 — your team's goals, even if you allowed notifications late

- **Fixed: goals were thrown away while notifications were still off.** Opening bitsports during your team's game starts watching straight away, and Android asks "Allow notifications?" at the same moment. The first check ran before you tapped Allow, saw the goals so far, couldn't post them, and still counted them as announced, so they never came. Now nothing counts as announced until Android actually lets it through; the moment you allow notifications, the next check (within 15 seconds) sends them.
- **Catch-up alert.** When bitsports starts watching a game that's already under way, you get one alert with the score, how many goals so far and the latest scorer (on the animated LED board) instead of a burst of old goals. Every goal after that gets its own alert. If the game is already over, you just get the final.

## From 0.7.3 — Alert check

Goal alerts still weren't reaching some phones. The alert logic itself checks out against real games (tonight's CAR @ EDM produced all ten goal alerts, the period ends and the final), so what's left is something on the phone stopping them. This build shows exactly what.

- **Settings → Goals + alerts → Alert check**: a light for each thing goal alerts need, with the button that fixes it:
  NHL team followed · notifications allowed · Goal alerts channel on · alarms allowed · battery not Restricted · battery optimisation off (recommended).
  Under it, what bitsports last did: tonight's schedule, whether it's watching right now, the last check of the game, the last alert sent and the last problem. If your team is playing and it isn't watching, a **Watch now** button starts it.
- **Home** shows a "Game alerts are blocked" card whenever any of those is failing, with a button straight to Alert check.
- **Fixed: a goal could be marked as announced even when its notification failed**, so it was never sent. Each alert now posts on its own, and if the animated LED version can't be posted the same alert goes out as plain text.
- A failing mini scoreboard or widget update can no longer stop alerts.

## From 0.7.2 — cleaner LED spacing

Every LED letter now has at least one dark pixel between it and any line, stripe, bar, sprite or other text, diagonals included. A test now draws every board in the app across hundreds of game states and fails the build if any letter touches anything.

Fixed along the way:
- **Scoreboard widget**: two-digit scores (11-10) ran into the home team's code, and a long clock (3OT 11:59) could collide with the PP/EN marker. Each part of the row now has its own space; with a PP or EN marker a long clock drops its period.
- **Goal notification**: the period and time no longer sit on top of the assists line.
- **"Next up" on the jumbotron**: a long matchup drops to regular size instead of running into the hockey sticks.
- **Burst goal moment**: the bursting rings stay clear of the letters.

## From 0.7.1 — goal moments in LED letters

- **Settings → Goals + alerts → Goal moment** now shows all four styles as live animated previews, two by two. Tap one to pick it.
- **Every goal moment is drawn in the same 128-pixel LED letters as the rest of the app**, team and scorer included:
  - **Lamp**: the red goal light flashes and sweeps its beams over GOAL!
  - **Burst**: rings in your team's two colours burst out behind GOAL!
  - **Puck**: a puck slides into the net, the net shakes, GOAL!
  - **Text only**: just the LED letters, blinking white and team colour.

## From 0.7.0 — animated LED alerts and a new heat map

**Animated 128-pixel notifications.** Game alerts now play on an LED board right in the notification shade: a strip that flips through frames when collapsed or popping up, and the full 128-LED board when you expand it.

- **Goal**: the goal lamp flashes red over a giant GOAL!, then the scorer with their goal count, power play / shorthanded and who assisted, then the new score.
- **End of period**: the score, then shots, hits and goals as head-to-head pixel bars in each team's colours, then faceoffs and the power play.
- **Final**: the final score and who won, then the three stars one at a time, gold stars and all.
- **Live mini scoreboard**: the jumbotron itself, flipping to the last goal; between periods the Zamboni laps the board.

**New heat map, 128 pixels.** The score + heat map widget now draws a real rink (rounded boards, lines, circles, faceoff dots, creases and nets) and each team's shooting zones glow in its colour, five shades from faint to white-hot, with goals as white sparks. Choose round LEDs or solid pixels when you set up the widget. The same heat map is in the app: Game → Shot map → Heat (Shots still shows every shot as a dot).

Still in this build: alerts arrive with the app closed (0.6.1). Open bitsports once after installing to allow notifications, then use Settings → Send a test alert to see the new animated goal alert.

Install: download the APK below and open it. It installs over the previous version.
