## bitsports 0.6.1 — game alerts that actually arrive

Goal, period and final alerts weren't reaching phones. Three causes, all fixed:

- **bitsports never asked to send notifications.** Android 13+ keeps an app silent until you allow it, and the only Allow button was deep in Settings. Now it asks as soon as you follow an NHL team, and Home shows a "Game alerts are off" card with the one-tap fix whenever they can't reach you.
- **The live watcher couldn't start on its own.** Android 14 switches off exact alarms for new installs, and without one Android won't let an app start watching a game in the background. bitsports now uses the alarm permission Android grants at install, so the watcher starts 15 minutes before puck drop with the app closed.
- **No backup.** If Android still blocks the watcher, bitsports now checks the game once a minute from an alarm and sends the same alerts until the watcher can take over. The morning schedule check also retries every 30 minutes if the phone was offline, instead of waiting for you to open the app.

Also new: **Settings → Goals + alerts → Send a test alert** posts a sample goal alert so you can check it reaches you before a real game.

You don't need to open the app on game day. After installing this once, open it one time so it can ask for notifications; from then on alerts run by themselves.

Install: download the APK below and open it. It installs over the previous version.
