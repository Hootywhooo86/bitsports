package com.bit128.bitsports

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

// 128bit family tokens — same as 128bitfit/prototype/app-shell.html and index.html.
// Monochrome chrome; colour only ever means data (team crests, LIVE).
private val Ground = Color(0xFF000000)
private val Card = Color(0xFF0D0D0D)
private val Card2 = Color(0xFF171717)
private val Card3 = Color(0xFF212121)
private val Line = Color(0xFF242424)
private val Line2 = Color(0xFF3A3A3A)
private val TextInk = Color(0xFFFFFFFF)
private val Muted = Color(0xFF8C8C8C)
private val Dim = Color(0xFF6B6B6B)
private val Live = Color(0xFFFF4D6D)
private val Warn = Color(0xFFFFD95E)

private val Pixel = FontFamily(
    Font(R.font.silkscreen_regular, FontWeight.Normal),
    Font(R.font.silkscreen_bold, FontWeight.Bold),
)

private val LocalUi = staticCompositionLocalOf { UiPrefs() }
private val CardShape = RoundedCornerShape(12.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = TeamsStore(applicationContext)
        val prefsStore = PrefsStore(applicationContext)
        setContent {
            val vm: BitsportsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = BitsportsViewModel(store, prefsStore) as T
            })
            val ui = vm.ui
            val density = LocalDensity.current
            val scaled = if (ui.largeText) Density(density.density, density.fontScale * 1.15f) else density
            MaterialTheme(colorScheme = darkColorScheme(background = Ground, surface = Card, primary = Color(ui.accent))) {
                CompositionLocalProvider(LocalUi provides ui, LocalDensity provides scaled) {
                    Surface(color = Ground, modifier = Modifier.fillMaxSize()) {
                        BitsportsScreen(vm)
                    }
                }
            }
        }
    }
}

// ---------- shared pieces ----------

@Composable
fun Heading(text: String, size: TextUnit = 13.sp, color: Color = TextInk) {
    val plain = LocalUi.current.plainHeadings
    Text(
        text,
        color = color,
        fontSize = size,
        fontFamily = if (plain) FontFamily.Default else Pixel,
        fontWeight = FontWeight.Bold,
        letterSpacing = if (plain) 0.sp else 1.5.sp,
    )
}

@Composable
fun Label(text: String, color: Color = Muted) {
    val plain = LocalUi.current.plainHeadings
    Text(
        text, color = color, fontSize = 11.sp,
        fontFamily = if (plain) FontFamily.Default else Pixel,
        fontWeight = if (plain) FontWeight.SemiBold else FontWeight.Normal,
        letterSpacing = if (plain) 0.sp else 1.sp,
    )
}

@Composable
fun SectionHead(title: String, note: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Heading(title)
        Spacer(Modifier.width(12.dp))
        Text(note, color = Muted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(Card).border(1.dp, Line, CardShape),
        content = content,
    )
}

/** The bitsports mark: a pixel puck in flight, on the same 11x6 grid as index.html. */
@Composable
fun PuckMark(color: Color, cell: Dp = 3.dp) {
    val cells = listOf(
        intArrayOf(6, 0, 4, 1), intArrayOf(5, 1, 6, 4), intArrayOf(6, 5, 4, 1),
        intArrayOf(0, 1, 3, 1), intArrayOf(1, 3, 3, 1),
    )
    Canvas(Modifier.size(cell * 11, cell * 6)) {
        val c = cell.toPx()
        cells.forEach { (x, y, w, h) ->
            drawRect(color, topLeft = Offset(x * c, y * c), size = Size(w * c, h * c))
        }
    }
}

@Composable
fun Crest(name: String, size: Dp) {
    val (c1, c2) = teamColors(name)
    Row(Modifier.size(size).clip(RoundedCornerShape(3.dp))) {
        Box(Modifier.weight(1f).fillMaxHeight().background(c1))
        Box(Modifier.weight(1f).fillMaxHeight().background(c2))
    }
}

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
            TitleRow()
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

// Sample games only — bitsports has no live score provider wired in yet (said in the section header).
private data class SampleGame(
    val home: String, val away: String, val hs: Int?, val aws: Int?,
    val status: String, val clock: String = "", val sport: String = "NHL",
)
private val SAMPLE_GAMES = listOf(
    SampleGame("Edmonton Oilers", "Calgary Flames", 4, 2, "live", "3RD · 8:12"),
    SampleGame("Toronto Maple Leafs", "Boston Bruins", 4, 1, "final"),
    SampleGame("Winnipeg Jets", "Colorado Avalanche", null, null, "7:00 PM MT"),
)
private fun shortName(n: String) = n.split(" ").last()

@Composable
fun TitleRow() {
    Heading("TODAY", size = 20.sp)
    Spacer(Modifier.height(10.dp))
    val lead = SAMPLE_GAMES.first { it.status == "live" }
    val hi = maxOf(lead.hs!!, lead.aws!!)
    val lo = minOf(lead.hs, lead.aws)
    val up = if (lead.hs >= lead.aws) lead.home else lead.away
    val dn = if (up == lead.home) lead.away else lead.home
    val live = SAMPLE_GAMES.count { it.status == "live" }
    val fin = SAMPLE_GAMES.count { it.status == "final" }
    val later = SAMPLE_GAMES.size - live - fin
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TextInk, fontWeight = FontWeight.SemiBold)) {
                append("${shortName(up)} lead the ${shortName(dn)} $hi–$lo")
            }
            append(", ${lead.clock.substringAfter(" · ")} left in the 3rd. $live live, $fin final, $later later tonight.")
        },
        color = Muted, fontSize = 16.sp, lineHeight = 23.sp,
    )
}

@Composable
fun ScoresSection(vm: BitsportsViewModel) {
    SectionHead("SCORES", "Sample games · live scores aren't wired in yet")
    val followed = vm.teams.map { slugify(it.team) }.toSet()
    fun mine(g: SampleGame) = slugify(g.home) in followed || slugify(g.away) in followed
    val rank = mapOf("live" to 0, "final" to 1)
    val games = SAMPLE_GAMES.sortedWith(compareBy<SampleGame>({ if (mine(it)) 0 else 1 }, { rank[it.status] ?: 2 }))
    games.forEachIndexed { i, g ->
        GameCard(g, hero = i == 0, followed = followed)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun GameCard(g: SampleGame, hero: Boolean, followed: Set<String>) {
    val ui = LocalUi.current
    val big = hero && !ui.compactScores
    val hasScore = g.status == "live" || g.status == "final"
    CardBox {
        Column(Modifier.padding(horizontal = 18.dp, vertical = if (ui.compactScores) 10.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = if (ui.compactScores) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (g.status == "live") {
                    LiveMarker()
                    Spacer(Modifier.weight(1f))
                    Label(g.clock)
                } else {
                    Label(if (g.status == "final") "FINAL" else "TONIGHT")
                    Spacer(Modifier.weight(1f))
                    Label(if (g.status == "final") g.sport else g.status.uppercase())
                }
            }
            ScoreLine(g.home, g.hs, g.aws, hasScore, big, slugify(g.home) in followed)
            ScoreLine(g.away, g.aws, g.hs, hasScore, big, slugify(g.away) in followed)
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
private fun ScoreLine(name: String, pts: Int?, other: Int?, hasScore: Boolean, big: Boolean, mine: Boolean) {
    val ui = LocalUi.current
    val losing = hasScore && pts!! < other!!
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
            if (hasScore) pts.toString() else "–",
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

            // One-tap adds from today's games, so an empty screen is one tap from useful.
            val picks = SAMPLE_GAMES.flatMap { listOf(it.home to it.sport, it.away to it.sport) }
                .filter { (n, _) -> vm.teams.none { slugify(it.team) == slugify(n) } }
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
                        pair.forEach { (n, s) ->
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Line2, RoundedCornerShape(10.dp))
                                    .clickable { vm.addTeam(n, s) }
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
    Text("Part of the 128bit family. Your teams stay on this device. Nothing is synced.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(48.dp))
}

// ---------- customize ----------

@Composable
fun CustomizeSheet(vm: BitsportsViewModel) {
    val ui = vm.ui
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

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) Card3 else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (i == selected) TextInk else Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = Color(LocalUi.current.accent)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.Black, uncheckedTrackColor = Card3, uncheckedThumbColor = Muted, uncheckedBorderColor = Line2),
        )
    }
}
