package com.bit128.bitsports

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

// Tokens from the taste-skill "true-black-flat" app-screen sheet, same as index.html.
private val Ground = Color(0xFF000000)
private val Surface = Color(0xFF111111)
private val Raised = Color(0xFF2A2A2A)
private val Rule = Color(0xFF161616)
private val TextInk = Color(0xFFFFFFFF)
private val Muted = Color(0xFFB4B4B4)
private val Faint = Color(0xFF949494)
private val Accent = Color(0xFFC6F24E)
private val Negative = Color(0xFFE5484D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = TeamsStore(applicationContext)
        setContent {
            val vm: BitsportsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = BitsportsViewModel(store) as T
            })
            MaterialTheme(colorScheme = darkColorScheme(background = Ground, surface = Surface, primary = Accent)) {
                Surface(color = Ground) {
                    BitsportsScreen(vm)
                }
            }
        }
    }
}

@Composable
fun BitsportsScreen(vm: BitsportsViewModel) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        TopBar()
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            TitleRow(vm)
            Spacer(Modifier.height(24.dp))
            TeamsSection(vm)
            Spacer(Modifier.height(28.dp))
            TodaySection(vm)
            Spacer(Modifier.height(28.dp))
            NewsSection(vm)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun TopBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ground)
            .border(width = 1.dp, color = Rule)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(Accent))
            Spacer(Modifier.width(10.dp))
            Text("bitsports", color = TextInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun TitleRow(vm: BitsportsViewModel) {
    Text("YOUR TEAMS", color = TextInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    val finding = if (vm.teams.isEmpty()) {
        "Pick up to 4 teams per sport to see their news."
    } else {
        val sports = vm.teams.map { slugify(it.sport) }.distinct().size
        "Following ${vm.teams.size} team${if (vm.teams.size == 1) "" else "s"} across $sports sport${if (sports == 1) "" else "s"}."
    }
    Text(finding, color = Muted, fontSize = 14.sp)
}

@Composable
fun Crest(name: String, size: androidx.compose.ui.unit.Dp) {
    val (c1, c2) = teamColors(name)
    Row(Modifier.size(size).clip(RoundedCornerShape(4.dp))) {
        Box(Modifier.weight(1f).fillMaxHeight().background(c1))
        Box(Modifier.weight(1f).fillMaxHeight().background(c2))
    }
}

@Composable
fun TeamsSection(vm: BitsportsViewModel) {
    Text("Teams", color = TextInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))

    val bySport = vm.teams.groupBy { it.sport }
    bySport.forEach { (sport, list) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(sport.uppercase(), color = Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Surface)
                            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Crest(t.team, 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(t.team, color = TextInk, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("×", color = Faint, fontSize = 14.sp, modifier = Modifier.clickable { vm.removeTeam(t) })
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    var teamInput by remember { mutableStateOf("") }
    var sportInput by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = teamInput, onValueChange = { teamInput = it },
            placeholder = { Text("Team name — any team", color = Faint) },
            modifier = Modifier.weight(1f), singleLine = true,
        )
        OutlinedTextField(
            value = sportInput, onValueChange = { sportInput = it },
            placeholder = { Text("Sport / league — any sport", color = Faint) },
            modifier = Modifier.weight(1f), singleLine = true,
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            vm.addTeam(teamInput, sportInput)
            if (vm.addMessage.isEmpty()) { teamInput = ""; sportInput = "" }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) { Text("ADD TEAM", fontWeight = FontWeight.Bold) }

    if (vm.addMessage.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(vm.addMessage, color = Color(0xFFE5A93B), fontSize = 13.sp)
    }
}

// Sample matchups only — bitsports has no live score provider wired in yet.
private data class SampleGame(val home: String, val away: String, val hs: Int?, val `as`: Int?, val status: String)
private val SAMPLE_GAMES = listOf(
    SampleGame("Edmonton Oilers", "Calgary Flames", 4, 2, "LIVE · 3RD 8:12"),
    SampleGame("Toronto Maple Leafs", "Boston Bruins", 4, 1, "FINAL"),
    SampleGame("Winnipeg Jets", "Colorado Avalanche", null, null, "7:00 PM MT"),
)

@Composable
fun TodaySection(vm: BitsportsViewModel) {
    Text("Today", color = TextInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Sample matchups — bitsports doesn't have a live score provider wired in yet. News below is real.",
        color = Faint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp, top = 2.dp),
    )
    val followed = vm.teams.map { slugify(it.team) }
    val relevant = SAMPLE_GAMES.filter {
        followed.isEmpty() || followed.contains(slugify(it.home)) || followed.contains(slugify(it.away))
    }.ifEmpty { SAMPLE_GAMES }

    relevant.forEach { g ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(14.dp)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crest(g.home, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(g.home, color = TextInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
            Spacer(Modifier.width(12.dp))
            Crest(g.away, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(g.away, color = TextInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (g.hs != null) {
                Text("${g.hs}–${g.`as`}", color = TextInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("—", color = Muted, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(g.status, color = if (g.status.startsWith("LIVE")) Negative else Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NewsSection(vm: BitsportsViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("News", color = TextInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (vm.newsItems.isNotEmpty()) {
            Text("${vm.newsItems.size} articles", color = Faint, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(10.dp))

    Row(Modifier.horizontalScroll(rememberScrollState())) {
        TabChip("All", vm.activeTab == ALL_TAB) { vm.selectTab(ALL_TAB) }
        vm.teams.forEach { t ->
            Spacer(Modifier.width(18.dp))
            TabChip(t.team, vm.activeTab == t.key) { vm.selectTab(t.key) }
        }
    }
    Divider(color = Rule, thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))

    when {
        vm.teams.isEmpty() -> InfoLine("No teams yet — add one above to pull in its real news.")
        vm.newsLoading -> InfoLine("Loading…")
        vm.newsError != null -> {
            InfoLine(vm.newsError!!, color = Color(0xFFE5A93B))
            TextButton(onClick = { vm.refreshNews() }) { Text("Retry", color = TextInk) }
        }
        vm.newsItems.isEmpty() -> InfoLine("No recent coverage found yet.")
        else -> vm.newsItems.take(30).forEach { item -> NewsRow(item) }
    }
}

@Composable
fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) TextInk else Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .border(width = if (selected) 2.dp else 0.dp, color = if (selected) Accent else Color.Transparent)
            .padding(bottom = 2.dp),
    )
}

@Composable
fun InfoLine(text: String, color: Color = Faint) {
    Text(text, color = color, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
fun NewsRow(item: NewsItem) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
            .padding(vertical = 14.dp)
            .border(width = 1.dp, color = Rule).padding(bottom = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Crest(item.team, 26.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.title, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("${item.source} · ${item.team}", color = Faint, fontSize = 12.sp)
        }
    }
}
