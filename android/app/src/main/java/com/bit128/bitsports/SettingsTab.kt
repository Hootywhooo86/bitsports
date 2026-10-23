package com.bit128.bitsports

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bit128.bitsports.alerts.AlertNotifier
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.sports.League
import com.bit128.bitsports.sports.Leagues
import com.bit128.bitsports.ui.PixelIcon
import com.bit128.bitsports.ui.sportIconName

private const val OTHER = "other"

/** Everything you can change lives here: your teams, the sports you watch, and the look. */
@Composable
fun SettingsTab(vm: BitsportsViewModel) {
    val ui = vm.ui
    val ctx = LocalContext.current
    fun set(p: UiPrefs) {
        vm.updateUi(p)
        AlertScheduler.refresh(ctx)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Heading("SETTINGS", size = 18.sp)
        Spacer(Modifier.height(24.dp))

        SectionHead("YOUR TEAMS", "Up to $MAX_PER_SPORT per sport")
        FollowedList(vm)
        Spacer(Modifier.height(14.dp))
        TeamPicker(vm)

        Spacer(Modifier.height(32.dp))
        SectionHead("WATCHED SPORTS", "Shown on the Sports tab")
        CardBox {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Leagues.all.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(sportIconName(l.icon), Muted, 16.dp)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            ToggleRow(l.label, l.key in ui.watched) { on ->
                                vm.updateUi(ui.copy(watched = if (on) ui.watched + l.key else ui.watched - l.key))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionHead("LOOK", "")
        CardBox {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Option("Accent", "Buttons, selected tabs and the logo. Team colours and LIVE never change.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ACCENTS.forEach { (name, value) ->
                            val on = ui.accent == value
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .border(2.dp, if (on) TextInk else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable(onClickLabel = name) { vm.updateUi(ui.copy(accent = value)) }
                                    .padding(5.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(value))
                            )
                        }
                    }
                }
                Option("Scores", "Big pixel box scores, or a tight list.") {
                    Segmented(listOf("Big", "Compact"), if (ui.compactScores) 1 else 0) { vm.updateUi(ui.copy(compactScores = it == 1)) }
                }
                Option("Text size", null) {
                    Segmented(listOf("Normal", "Large"), if (ui.largeText) 1 else 0) { vm.updateUi(ui.copy(largeText = it == 1)) }
                }
                Option("Headings", "Pixel type like the rest of 128bit, or plain.") {
                    Segmented(listOf("Pixel", "Plain"), if (ui.plainHeadings) 1 else 0) { vm.updateUi(ui.copy(plainHeadings = it == 1)) }
                }
                Option("Home page", null, divider = false) {
                    ToggleRow("News under your games", ui.showNews) { vm.updateUi(ui.copy(showNews = it)) }
                    ToggleRow("Blinking LIVE marker", ui.blinkLive) { vm.updateUi(ui.copy(blinkLive = it)) }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionHead("GOALS + ALERTS", "NHL teams you follow")
        CardBox {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Option("Goal moment", "What fills the screen when your team scores with the app open. Goal alerts play the animated LED board.") {
                    Segmented(GOAL_STYLES.map { it.first }, GOAL_STYLES.indexOfFirst { it.second == ui.goalStyle }.coerceAtLeast(0)) {
                        vm.updateUi(ui.copy(goalStyle = GOAL_STYLES[it].second))
                    }
                    Spacer(Modifier.height(4.dp))
                    ToggleRow("8-bit goal horn", ui.goalSound) { vm.updateUi(ui.copy(goalSound = it)) }
                    OutlinedButton(
                        onClick = { vm.previewGoal() },
                        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) { Text("Preview", color = TextInk, fontWeight = FontWeight.SemiBold) }
                }
                Option("Game alerts", "For your NHL teams, even with the app closed.", divider = false) {
                    AlertPermissionLine()
                    ToggleRow("30 minutes before puck drop", ui.alertPregame) { set(ui.copy(alertPregame = it)) }
                    ToggleRow("Goals", ui.alertGoals) { set(ui.copy(alertGoals = it)) }
                    ToggleRow("Goal replays", ui.alertReplays) { set(ui.copy(alertReplays = it)) }
                    ToggleRow("End-of-period stats", ui.alertPeriods) { set(ui.copy(alertPeriods = it)) }
                    ToggleRow("AI period recap (needs bitcall)", ui.alertRecap) { set(ui.copy(alertRecap = it)) }
                    ToggleRow("Final score", ui.alertFinal) { set(ui.copy(alertFinal = it)) }
                    TestAlertButton(vm)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { vm.updateUi(UiPrefs()) },
            border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Reset look and alerts to defaults", color = TextInk, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun FollowedList(vm: BitsportsViewModel) {
    CardBox {
        Column(Modifier.padding(16.dp)) {
            if (vm.teams.isEmpty()) {
                Text("No teams yet. Pick a sport below and tap your teams.", color = Muted, fontSize = 14.sp)
                return@Column
            }
            vm.teams.groupBy { it.sport }.entries.forEachIndexed { i, (sport, list) ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Label(sport.uppercase(), color = TextInk)
                    Spacer(Modifier.weight(1f))
                    Label("${list.size} OF $MAX_PER_SPORT")
                }
                list.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Card2)
                            .heightIn(min = 44.dp)
                            .padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Crest(t.team, 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(t.team, color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.removeTeam(t) }) { Text("Remove", color = Muted, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

/** Pick a league, tap teams to follow them. "Other" keeps the type-any-team-any-sport promise. */
@Composable
private fun TeamPicker(vm: BitsportsViewModel) {
    val accent = Color(LocalUi.current.accent)
    var leagueKey by remember { mutableStateOf(Leagues.NHL.key) }
    val league = Leagues.byKey(leagueKey)
    CardBox {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text("Add teams", color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (Leagues.all.map { it.key to it.label } + (OTHER to "Other")).forEach { (key, label) ->
                    val on = key == leagueKey
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) Card3 else Card2)
                            .border(1.dp, if (on) accent else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { leagueKey = key }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Label(label.uppercase(), color = if (on) TextInk else Muted) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (league == null) FreeformAdd(vm) else LeagueTeams(vm, league)
            }
            if (vm.addMessage.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(vm.addMessage, color = Warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

private data class PickTeam(val name: String, val colors: Pair<Color, Color>)

@Composable
private fun LeagueTeams(vm: BitsportsViewModel, league: League) {
    LaunchedEffect(league.key) { vm.loadTeams(league) }
    val options: List<PickTeam>? = if (league == Leagues.NHL) {
        NhlTeams.all.sortedBy { it.fullName }.map { PickTeam(it.fullName, Color(it.colors.first) to Color(it.colors.second)) }
    } else {
        vm.espnTeams[league.key]?.map { PickTeam(it.name, espnColors(it)) }
    }
    if (options == null) {
        Text("Loading ${league.label} teams…", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    val accent = Color(LocalUi.current.accent)
    Column {
        Text("${vm.countFor(league.label)} of $MAX_PER_SPORT picked · tap to follow or unfollow", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        options.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { t ->
                    val on = vm.isFollowing(t.name, league.label)
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Card3 else Color.Transparent)
                            .border(1.dp, if (on) accent else Line2, RoundedCornerShape(10.dp))
                            .clickable { vm.toggleTeam(t.name, league.label) }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Crest(t.colors, 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(t.name, color = if (on) TextInk else Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (on) Label("✓", color = accent)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FreeformAdd(vm: BitsportsViewModel) {
    val accent = Color(LocalUi.current.accent)
    var teamInput by remember { mutableStateOf("") }
    var sportInput by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Card2, unfocusedContainerColor = Card2,
        focusedBorderColor = Line2, unfocusedBorderColor = Line,
        focusedTextColor = TextInk, unfocusedTextColor = TextInk, cursorColor = accent,
    )
    Column {
        Text("Any team, any sport. You get its news; scores only for the leagues above.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = teamInput, onValueChange = { teamInput = it },
            placeholder = { Text("Team", color = Dim) },
            singleLine = true, shape = RoundedCornerShape(11.dp), colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = sportInput, onValueChange = { sportInput = it },
                placeholder = { Text("Sport or league", color = Dim) },
                singleLine = true, shape = RoundedCornerShape(11.dp), colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    vm.addTeam(teamInput, sportInput)
                    if (vm.addMessage.isEmpty()) { teamInput = ""; sportInput = "" }
                },
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                modifier = Modifier.height(56.dp).weight(1f),
            ) { Heading("ADD TEAM", color = Color.Black) }
        }
    }
}

/** Posts a sample goal alert right now, so you can check alerts reach you before a real game. */
@Composable
private fun TestAlertButton(vm: BitsportsViewModel) {
    val ctx = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            val sent = AlertNotifier.test(ctx, vm.ui, vm.followedAbbrevs().firstOrNull())
            note = if (sent) "Sent. If nothing showed up, check bitsports in your phone's notification settings."
            else "Notifications are off for bitsports. Tap Allow above."
        },
        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) { Text("Send a test alert", color = TextInk, fontWeight = FontWeight.SemiBold) }
    note?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = Muted, fontSize = 12.sp)
    }
}

/** Says, in one line, why alerts can't work yet, with the button that fixes it. Re-checks on return. */
@Composable
internal fun AlertPermissionLine() {
    val ctx = LocalContext.current
    var problem by remember { mutableStateOf(AlertScheduler.problem(ctx)) }
    val recheck = { problem = AlertScheduler.problem(ctx); AlertScheduler.refresh(ctx) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) recheck() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val p = problem ?: return
    Text(p, color = Warn, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            when {
                !AlertNotifier.canPost(ctx) && Build.VERSION.SDK_INT >= 33 ->
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                !AlertNotifier.canPost(ctx) -> ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
                Build.VERSION.SDK_INT >= 31 -> ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                )
            }
        },
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(LocalUi.current.accent), contentColor = Color.Black),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) { Text("Allow", fontWeight = FontWeight.SemiBold) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Option(title: String, note: String?, divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(title, color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (note != null) {
            Spacer(Modifier.height(4.dp))
            Text(note, color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
    if (divider) HorizontalDivider(color = Line)
}
