## bitsports 0.7.3 — Alert check

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
