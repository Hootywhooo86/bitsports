package com.bit128.bitsports

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.sports.EspnGame
import com.bit128.bitsports.sports.EspnState
import com.bit128.bitsports.sports.EspnTeam
import com.bit128.bitsports.ui.ScoreFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Score cards and small pieces shared by the Home, Sports and game screens.

@Composable
fun GameCard(g: Game, hero: Boolean, followed: Set<String>, onClick: () -> Unit) {
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
                    Label(if (hasScore) "SOG ${g.away.sog ?: 0}–${g.home.sog ?: 0}" else "NHL ›")
                }
            }
            ScoreLine(teamName(g.away.abbrev), teamColors(teamName(g.away.abbrev)), g.away.score, g.home.score, hasScore, big, g.away.abbrev in followed)
            ScoreLine(teamName(g.home.abbrev), teamColors(teamName(g.home.abbrev)), g.home.score, g.away.score, hasScore, big, g.home.abbrev in followed)
        }
    }
}

/** A game from another league. Tap to open the extra detail ESPN gives: venue, TV, leaders, situation. */
@Composable
fun EspnCard(g: EspnGame, mine: (EspnTeam) -> Boolean) {
    val ui = LocalUi.current
    var open by remember(g.id) { mutableStateOf(false) }
    val hasScore = g.state != EspnState.PRE
    CardBox(Modifier.clip(CardShape).clickable { open = !open }.animateContentSize()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = if (ui.compactScores) 10.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = if (ui.compactScores) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (g.isLive) {
                    LiveMarker()
                    Spacer(Modifier.weight(1f))
                    Label(g.status.uppercase())
                } else {
                    Label(espnWhen(g).uppercase())
                    Spacer(Modifier.weight(1f))
                    Label(g.league.label.uppercase() + if (open) " ‹" else " ›")
                }
            }
            for ((t, other) in listOf(g.away to g.home, g.home to g.away)) {
                ScoreLine(
                    t.name, espnColors(t), t.score?.toIntOrNull(), other.score?.toIntOrNull(), hasScore, false, mine(t),
                    record = t.record, rawScore = t.score,
                )
            }
            if (open) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(10.dp))
                g.situation?.let { Text(it, color = TextInk, fontSize = 14.sp, lineHeight = 20.sp); Spacer(Modifier.height(8.dp)) }
                g.leaders.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Label(l.label.uppercase())
                        Spacer(Modifier.width(10.dp))
                        Text("${l.who} · ${l.line}", color = TextInk, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val where = listOfNotNull(g.venue, g.tv.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "TV: "))
                if (where.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(where.joinToString(" · "), color = Muted, fontSize = 12.sp)
                }
                if (g.leaders.isEmpty() && g.situation == null && where.isEmpty()) Text("No extra detail for this one yet.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

fun espnColors(t: EspnTeam): Pair<Color, Color> =
    if (t.color != null) Color(t.color) to Color(t.altColor ?: 0xFF000000) else teamColors(t.name)

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)

/** "7:00 PM" today, "Sun 1:00 PM" another day, ESPN's own words once it's started. */
fun espnWhen(g: EspnGame, zone: ZoneId = ZoneId.systemDefault()): String =
    if (g.state != EspnState.PRE) g.status else whenText(g.start, zone)

fun whenText(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = at.atZone(zone)
    return if (t.toLocalDate() == LocalDate.now(zone)) TIME.format(t) else DAY_TIME.format(t)
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
fun ScoreLine(
    name: String,
    colors: Pair<Color, Color>,
    pts: Int?,
    other: Int?,
    hasScore: Boolean,
    big: Boolean,
    mine: Boolean,
    record: String? = null,
    rawScore: String? = null,
) {
    val ui = LocalUi.current
    val losing = hasScore && (pts ?: 0) < (other ?: 0)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (ui.compactScores) 36.dp else if (big) 56.dp else 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crest(colors, if (big) 30.dp else 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = if (losing) Muted else TextInk,
                fontSize = if (big) 18.sp else 15.sp,
                fontWeight = if (losing) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (record != null && !ui.compactScores) Text(record, color = Dim, fontSize = 12.sp)
        }
        if (mine) {
            Label("YOURS", color = Color(ui.accent))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            if (hasScore) rawScore ?: (pts ?: 0).toString() else "–",
            color = if (!hasScore || losing) Dim else TextInk,
            fontFamily = Pixel,
            fontSize = if (ui.compactScores) 20.sp else if (big) 36.sp else 26.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 28.dp),
        )
    }
}

@Composable
internal fun Crest(colors: Pair<Color, Color>, size: Dp) {
    Row(Modifier.size(size).clip(RoundedCornerShape(3.dp))) {
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.first))
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.second))
    }
}

@Composable
fun InfoLine(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 14.sp, modifier = Modifier.padding(16.dp, 24.dp))
}

@Composable
fun RetryButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(10.dp),
        modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
    ) { Text("Retry", color = TextInk) }
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
            vm.teams.isEmpty() -> InfoLine("Follow a team in Settings and its real headlines land here, newest first.")
            vm.newsLoading -> InfoLine("Loading headlines…")
            vm.newsError != null -> {
                InfoLine(vm.newsError!!, color = Warn)
                RetryButton { vm.refreshNews() }
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
    Text(
        "Part of the 128bit family. Your teams stay on this device. NHL from the NHL's public feed; other leagues from ESPN's.",
        color = Muted, fontSize = 13.sp,
    )
    Spacer(Modifier.height(48.dp))
}
