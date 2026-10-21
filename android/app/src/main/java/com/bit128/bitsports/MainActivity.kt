package com.bit128.bitsports

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bit128.bitsports.alerts.AlertNotifier
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.ui.GameScreen
import com.bit128.bitsports.ui.GoalMoment
import com.bit128.bitsports.ui.ScoreFormat

class MainActivity : ComponentActivity() {
    /** A game to open, from a notification tap. The screen picks it up once the view model exists. */
    private var pendingGame by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertNotifier.ensureChannels(applicationContext)
        AlertScheduler.refresh(applicationContext)
        pendingGame = gameFrom(intent)
        val store = TeamsStore(applicationContext)
        val prefsStore = PrefsStore(applicationContext)
        setContent {
            val vm: BitsportsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BitsportsViewModel(store, prefsStore, applicationContext) as T
            })
            LaunchedEffect(pendingGame) {
                pendingGame?.let { vm.openGame(it); pendingGame = null }
            }
            val ui = vm.ui
            val density = LocalDensity.current
            val scaled = if (ui.largeText) Density(density.density, density.fontScale * 1.15f) else density
            MaterialTheme(colorScheme = darkColorScheme(background = Ground, surface = Card, primary = Color(ui.accent))) {
                CompositionLocalProvider(LocalUi provides ui, LocalDensity provides scaled) {
                    Surface(color = Ground, modifier = Modifier.fillMaxSize()) {
                        val open = vm.openGameId
                        if (open != null) {
                            BackHandler { vm.closeGame() }
                            GameScreen(vm, onBack = { vm.closeGame() })
                        } else {
                            BitsportsScreen(vm)
                        }
                        vm.goalMoment?.let { g ->
                            GoalMoment(g, ui.goalStyle, ui.goalSound, onDone = { vm.goalMoment = null })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        gameFrom(intent)?.let { pendingGame = it }
    }

    private fun gameFrom(intent: Intent?): Long? =
        intent?.getLongExtra(AlertNotifier.EXTRA_GAME, -1L)?.takeIf { it > 0 }
}

internal fun teamName(abbrev: String) = NhlTeams.byAbbrev(abbrev)?.fullName ?: abbrev

// ---------- screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitsportsScreen(vm: BitsportsViewModel) {
    val ui = LocalUi.current
    var showCustomize by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(onCustomize = { showCustomize = true })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            TitleRow(vm)
            if (ui.showScores) {
                Spacer(Modifier.height(28.dp))
                ScoresSection(vm)
            }
            Spacer(Modifier.height(36.dp))
            TeamsSection(vm)
            if (ui.showNews) {
                Spacer(Modifier.height(36.dp))
                NewsSection(vm)
            }
            Spacer(Modifier.height(40.dp))
            Footer()
        }
    }
    if (showCustomize) {
        ModalBottomSheet(
            onDismissRequest = { showCustomize = false },
            containerColor = Card,
            contentColor = TextInk,
        ) {
            CustomizeSheet(vm)
        }
    }
}

@Composable
fun TopBar(onCustomize: () -> Unit) {
    val accent = Color(LocalUi.current.accent)
    Column {
        Row(
            Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PuckMark(accent)
            Spacer(Modifier.width(12.dp))
            Text("BITSPORTS", color = TextInk, fontFamily = Pixel, fontSize = 16.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onCustomize,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Line2),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextInk),
                modifier = Modifier.height(36.dp),
            ) { Label("CUSTOMIZE", color = TextInk) }
        }
        HorizontalDivider(color = Line, thickness = 1.dp)
    }
}

/** One sentence about tonight, led by your team's game. */
@Composable
fun TitleRow(vm: BitsportsViewModel) {
    Heading("TODAY", size = 20.sp)
    Spacer(Modifier.height(10.dp))
    val games = vm.orderedGames()
    val live = games.count { it.state.isLive }
    val fin = games.count { it.state.isOver }
    val later = games.size - live - fin
    val lead = games.firstOrNull()
    Text(
        buildAnnotatedString {
            when {
                !vm.gamesLoaded -> append("Checking tonight's NHL games…")
                games.isEmpty() -> append("No NHL games today.")
                else -> {
                    if (lead != null) {
                        withStyle(SpanStyle(color = TextInk, fontWeight = FontWeight.SemiBold)) { append(headline(lead)) }
                        append(". ")
                    }
                    append("$live live, $fin final, $later later.")
                }
            }
        },
        color = Muted, fontSize = 16.sp, lineHeight = 23.sp,
    )
}

private fun headline(g: Game): String {
    val a = com.bit128.bitsports.alerts.AlertCopy.nick(g.away.abbrev)
    val h = com.bit128.bitsports.alerts.AlertCopy.nick(g.home.abbrev)
    val as_ = g.away.score ?: 0
    val hs = g.home.score ?: 0
    return when {
        !g.state.isLive && !g.state.isOver -> "$a at $h, ${ScoreFormat.status(g).lowercase()}"
        as_ == hs -> "$a and $h tied $as_–$hs" + if (g.state.isOver) "" else ", ${ScoreFormat.status(g).substringAfter("· ")}"
        else -> {
            val (up, dn) = if (as_ > hs) a to h else h to a
            val verb = if (g.state.isOver) "beat" else "lead"
            "$up $verb $dn ${maxOf(as_, hs)}–${minOf(as_, hs)}"
        }
    }
}

@Composable
fun ScoresSection(vm: BitsportsViewModel) {
    val stale = vm.gamesFetchedAt?.let { com.bit128.bitsports.alerts.AlertPlanner.staleLabel(it, vm.now) }
    SectionHead("SCORES", vm.gamesError ?: stale ?: "NHL · tap a game", noteColor = if (vm.gamesError != null) Warn else Muted)
    val followed = vm.followedAbbrevs()
    val games = vm.orderedGames()
    when {
        !vm.gamesLoaded -> CardBox { InfoLine("Loading scores…") }
        games.isEmpty() && vm.gamesError != null -> CardBox {
            InfoLine("Scores will show here once the NHL feed answers.")
            OutlinedButton(
                onClick = { vm.refreshGames() },
                border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
            ) { Text("Retry", color = TextInk) }
        }
        games.isEmpty() -> CardBox { InfoLine("No NHL games today.") }
        else -> games.forEachIndexed { i, g ->
            GameCard(g, hero = i == 0, followed = followed, onClick = { vm.openGame(g.id) })
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun GameCard(g: Game, hero: Boolean, followed: Set<String>, onClick: () -> Unit) {
    val ui = LocalUi.current
    val big = hero && !ui.compactScores
    val hasScore = g.state.isLive || g.state.isOver
    CardBox(Modifier.clip(CardShape).clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = if (ui.compactScores) 10.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = if (ui.compactScores) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val status = ScoreFormat.status(g)
                if (g.state.isLive) {
                    LiveMarker()
                    Spacer(Modifier.weight(1f))
                    Label(status.substringAfter("· ", status))
                } else {
                    Label(status)
                    Spacer(Modifier.weight(1f))
                    Label(if (hasScore) "SOG ${g.away.sog ?: 0}–${g.home.sog ?: 0}" else "NHL")
                }
            }
            ScoreLine(g.away.abbrev, g.away.score, g.home.score, hasScore, big, g.away.abbrev in followed)
            ScoreLine(g.home.abbrev, g.home.score, g.away.score, hasScore, big, g.home.abbrev in followed)
        }
    }
}

@Composable
fun LiveMarker() {
    val blink = LocalUi.current.blinkLive
    val t = rememberInfiniteTransition(label = "live")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "blink")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).alpha(if (!blink || phase < 0.5f) 1f else 0.2f).background(Live))
        Spacer(Modifier.width(7.dp))
        Label("LIVE", color = Live)
    }
}

@Composable
private fun ScoreLine(abbrev: String, pts: Int?, other: Int?, hasScore: Boolean, big: Boolean, mine: Boolean) {
    val ui = LocalUi.current
    val name = teamName(abbrev)
    val losing = hasScore && (pts ?: 0) < (other ?: 0)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (ui.compactScores) 36.dp else if (big) 56.dp else 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crest(name, if (big) 30.dp else 24.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = if (losing) Muted else TextInk,
            fontSize = if (big) 18.sp else 15.sp,
            fontWeight = if (losing) FontWeight.Medium else FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (mine) {
            Label("YOURS", color = Color(ui.accent))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            if (hasScore) (pts ?: 0).toString() else "–",
            color = if (!hasScore || losing) Dim else TextInk,
            fontFamily = Pixel,
            fontSize = if (ui.compactScores) 20.sp else if (big) 36.sp else 26.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 28.dp),
        )
    }
}

@Composable
fun TeamsSection(vm: BitsportsViewModel) {
    val accent = Color(LocalUi.current.accent)
    SectionHead("YOUR TEAMS", "Up to $MAX_PER_SPORT per sport")
    CardBox {
        Column(Modifier.padding(16.dp)) {
            vm.teams.groupBy { it.sport }.forEach { (sport, list) ->
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
                        TextButton(onClick = { vm.removeTeam(t) }) { Text("×", color = Muted, fontSize = 18.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            var teamInput by remember { mutableStateOf("") }
            var sportInput by remember { mutableStateOf("") }
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Card2, unfocusedContainerColor = Card2,
                focusedBorderColor = Line2, unfocusedBorderColor = Line,
                focusedTextColor = TextInk, unfocusedTextColor = TextInk, cursorColor = accent,
            )
            OutlinedTextField(
                value = teamInput, onValueChange = { teamInput = it },
                placeholder = { Text("Team — any team", color = Dim) },
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
            if (vm.addMessage.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(vm.addMessage, color = Warn, fontSize = 13.sp)
            }

            // One-tap adds from today's real games, so an empty screen is one tap from useful.
            val picks = vm.orderedGames().flatMap { listOf(teamName(it.away.abbrev), teamName(it.home.abbrev)) }
                .distinct()
                .filter { n -> vm.teams.none { slugify(it.team) == slugify(n) } }
                .take(4)
            if (picks.isNotEmpty() && vm.countFor("NHL") < MAX_PER_SPORT) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (vm.teams.isEmpty()) "No teams yet. Start with one playing today:" else "Playing today:",
                    color = Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                picks.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { n ->
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Line2, RoundedCornerShape(10.dp))
                                    .clickable { vm.addTeam(n, "NHL") }
                                    .heightIn(min = 44.dp)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Crest(n, 18.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(n, color = TextInk, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun NewsSection(vm: BitsportsViewModel) {
    val accent = Color(LocalUi.current.accent)
    SectionHead("NEWS", if (vm.newsItems.isNotEmpty()) "${vm.newsItems.size} articles" else "")
    CardBox {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            NewsTab("All teams", vm.activeTab == ALL_TAB, accent) { vm.selectTab(ALL_TAB) }
            vm.teams.forEach { t ->
                Spacer(Modifier.width(22.dp))
                NewsTab(t.team, vm.activeTab == t.key, accent) { vm.selectTab(t.key) }
            }
        }
        HorizontalDivider(color = Line)
        when {
            vm.teams.isEmpty() -> InfoLine("Add a team and its real headlines land here, newest first.")
            vm.newsLoading -> InfoLine("Loading headlines…")
            vm.newsError != null -> {
                InfoLine(vm.newsError!!, color = Warn)
                OutlinedButton(
                    onClick = { vm.refreshNews() },
                    border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                ) { Text("Retry", color = TextInk) }
            }
            vm.newsItems.isEmpty() -> InfoLine("No recent coverage found yet.")
            else -> vm.newsItems.take(30).forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = Line)
                NewsRow(item)
            }
        }
    }
}

@Composable
fun NewsTab(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Column(Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick).padding(top = 14.dp)) {
        Text(label, color = if (selected) TextInk else Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.height(2.dp).fillMaxWidth().background(if (selected) accent else Color.Transparent))
    }
}

@Composable
fun InfoLine(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 14.sp, modifier = Modifier.padding(16.dp, 24.dp))
}

@Composable
fun NewsRow(item: NewsItem) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Crest(item.team, 28.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(item.title, color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
            Spacer(Modifier.height(5.dp))
            Text("${item.source} · ${item.team}", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun Footer() {
    HorizontalDivider(color = Line)
    Spacer(Modifier.height(32.dp))
    Text("BITSPORTS", color = TextInk, fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(16.dp))
    Text("Part of the 128bit family. Your teams stay on this device. Scores from the NHL's public feed.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(48.dp))
}

// ---------- customize ----------

@Composable
fun CustomizeSheet(vm: BitsportsViewModel) {
    val ui = vm.ui
    val ctx = LocalContext.current
    fun set(p: UiPrefs) {
        vm.updateUi(p)
        AlertScheduler.refresh(ctx)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        Heading("CUSTOMIZE", size = 14.sp)
        Spacer(Modifier.height(8.dp))

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
        Option("Goal moment", "What fills the screen when your team scores. Also used for the goal notification art.") {
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
        Option("Game alerts", "For your NHL teams, even with the app closed.") {
            AlertPermissionLine()
            ToggleRow("30 minutes before puck drop", ui.alertPregame) { set(ui.copy(alertPregame = it)) }
            ToggleRow("Goals", ui.alertGoals) { set(ui.copy(alertGoals = it)) }
            ToggleRow("Goal replays", ui.alertReplays) { set(ui.copy(alertReplays = it)) }
            ToggleRow("End-of-period stats", ui.alertPeriods) { set(ui.copy(alertPeriods = it)) }
            ToggleRow("AI period recap (needs bitcall)", ui.alertRecap) { set(ui.copy(alertRecap = it)) }
            ToggleRow("Final score", ui.alertFinal) { set(ui.copy(alertFinal = it)) }
        }
        Option("Show", null) {
            ToggleRow("Scores", ui.showScores) { vm.updateUi(ui.copy(showScores = it)) }
            ToggleRow("News", ui.showNews) { vm.updateUi(ui.copy(showNews = it)) }
            ToggleRow("Blinking LIVE marker", ui.blinkLive) { vm.updateUi(ui.copy(blinkLive = it)) }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { vm.updateUi(UiPrefs()) },
            border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Reset to defaults", color = TextInk, fontWeight = FontWeight.SemiBold) }
    }
}

/** Says, in one line, why alerts can't work yet, with the button that fixes it. Re-checks on return. */
@Composable
private fun AlertPermissionLine() {
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
private fun Option(title: String, note: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(title, color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (note != null) {
            Spacer(Modifier.height(4.dp))
            Text(note, color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
    HorizontalDivider(color = Line)
}
