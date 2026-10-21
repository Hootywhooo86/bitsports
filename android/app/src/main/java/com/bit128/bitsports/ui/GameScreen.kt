package com.bit128.bitsports.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.*
import com.bit128.bitsports.ai.AiResult
import com.bit128.bitsports.ai.FamilyAi
import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.alerts.GameWatchService
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlTeams
import kotlinx.coroutines.launch

/** One game: score, shot map, every goal, period stats with an AI recap, three stars. */
@Composable
fun GameScreen(vm: BitsportsViewModel, onBack: () -> Unit) {
    val id = vm.openGameId ?: return
    val d = vm.detail
    val game = d?.game ?: vm.games.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize()) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Label("‹ SCORES", color = TextInk) }
                Spacer(Modifier.weight(1f))
                val stale = d?.fetchedAt?.let { AlertPlanner.staleLabel(it, vm.now) }
                Text(vm.detailError ?: stale ?: "", color = if (vm.detailError != null) Warn else Muted, fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp))
            }
            HorizontalDivider(color = Line)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            if (game == null) {
                Text("Loading game…", color = Muted, fontSize = 14.sp)
                return@Column
            }
            Scoreboard(game)
            Spacer(Modifier.height(12.dp))
            WatchButton(game, vm.followedAbbrevs())
            if (d == null) {
                Spacer(Modifier.height(24.dp))
                Text(if (vm.detailError != null) "Play-by-play isn't available right now." else "Loading play-by-play…", color = Muted, fontSize = 14.sp)
                return@Column
            }
            if (d.plays.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Nothing yet. Shots, goals and stats fill in once the puck drops.", color = Muted, fontSize = 14.sp)
                return@Column
            }
            val away = NhlTeams.byAbbrev(game.away.abbrev)
            val home = NhlTeams.byAbbrev(game.home.abbrev)

            Spacer(Modifier.height(32.dp))
            SectionHead("SHOT MAP", "${game.away.abbrev} → · ← ${game.home.abbrev} · white = goal")
            CardBox { Box(Modifier.padding(10.dp)) { ShotMap(GameAnalysis.shots(d), away, home) } }

            val goals = GameAnalysis.goals(d)
            Spacer(Modifier.height(32.dp))
            SectionHead("GOALS", if (goals.isEmpty()) "None yet" else "${goals.size}")
            if (goals.isNotEmpty()) CardBox {
                goals.forEachIndexed { i, g ->
                    if (i > 0) HorizontalDivider(color = Line)
                    GoalRow(g)
                }
            }

            val periods = finishedPeriods(d)
            if (periods.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                SectionHead("PERIODS", "${game.away.abbrev} – ${game.home.abbrev}")
                periods.forEach { (p, type) ->
                    PeriodCard(vm, d, p, type)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (d.threeStars.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHead("THREE STARS", "")
                CardBox {
                    d.threeStars.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = Line)
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Label("★".repeat(i + 1).padEnd(3, ' '), color = Warn)
                            Spacer(Modifier.width(12.dp))
                            Text(s, color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** Periods that are over (or in intermission), in order. Shootouts aren't periods here. */
internal fun finishedPeriods(d: GameDetail): List<Pair<Int, String>> {
    val c = d.game.clock
    return d.plays.filter { it.periodType != "SO" }.map { it.period to it.periodType }.distinct().sortedBy { it.first }
        .filter { (p, _) -> d.game.state.isOver || c == null || p < c.period || (p == c.period && c.inIntermission) }
}

@Composable
private fun Scoreboard(g: Game) {
    val hasScore = g.state.isLive || g.state.isOver
    CardBox {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (g.state.isLive) LiveMarker() else Label(ScoreFormat.status(g))
                Spacer(Modifier.weight(1f))
                if (g.state.isLive) Label(ScoreFormat.status(g).substringAfter("· "))
                else if (hasScore) Label("SOG ${g.away.sog ?: 0}–${g.home.sog ?: 0}")
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBlock(g.away.abbrev, Modifier.weight(1f))
                Text(
                    if (hasScore) "${g.away.score ?: 0}–${g.home.score ?: 0}" else "VS",
                    color = TextInk, fontFamily = Pixel, fontSize = 40.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                TeamBlock(g.home.abbrev, Modifier.weight(1f))
            }
            if (g.state.isLive) {
                Spacer(Modifier.height(10.dp))
                Text("Shots ${g.away.sog ?: 0}–${g.home.sog ?: 0}", color = Muted, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TeamBlock(abbrev: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(teamName(abbrev), 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(AlertCopy.nick(abbrev), color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Starts the background watcher (mini scoreboard + alerts) for a game that isn't over. */
@Composable
private fun WatchButton(g: Game, followed: Set<String>) {
    if (g.state.isOver || !g.scheduleOk) return
    val ctx = LocalContext.current
    var started by remember(g.id) { mutableStateOf(false) }
    val yours = g.away.abbrev in followed || g.home.abbrev in followed
    OutlinedButton(
        onClick = { started = GameWatchService.start(ctx, g.id) },
        enabled = !started,
        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(
            when {
                started -> "Watching · mini scoreboard is in your notifications"
                yours -> "Watch now (starts on its own 15 min before puck drop)"
                else -> "Watch this game with alerts"
            },
            color = if (started) Muted else TextInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GoalRow(g: GoalEvent) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        Crest(teamName(g.teamAbbrev), 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${g.scorer}${g.scorerTotal?.let { " ($it)" }.orEmpty()}",
                color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(AlertCopy.goalDetail(g).lines().drop(1).joinToString(" · "), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Label("${AlertPlanner.periodLabel(g.period, g.periodType).uppercase()} ${g.timeInPeriod} · ${g.awayScore}–${g.homeScore}")
        }
        g.clipUrl?.let { url ->
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(Card2)
                    .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Label("▶ CLIP", color = TextInk) }
        }
    }
}

@Composable
private fun PeriodCard(vm: BitsportsViewModel, d: GameDetail, period: Int, type: String) {
    val s = GameAnalysis.periodStats(d, period)
    val scope = rememberCoroutineScope()
    var recap by remember(d.game.id, period) { mutableStateOf(vm.recapFor(d.game.id, period)) }
    var note by remember(d.game.id, period) { mutableStateOf<String?>(null) }
    var busy by remember(d.game.id, period) { mutableStateOf(false) }
    CardBox {
        Column(Modifier.padding(16.dp)) {
            Heading(AlertPlanner.periodLabel(period, type).uppercase())
            Spacer(Modifier.height(10.dp))
            StatRow("Goals", "${s.awayGoals}", "${s.homeGoals}")
            StatRow("Shots", "${s.awayShots}", "${s.homeShots}")
            StatRow("Power play", s.awayPp, s.homePp)
            StatRow("Hits", "${s.awayHits}", "${s.homeHits}")
            StatRow("Faceoffs", "${s.awayFaceoffPct}%", "${s.homeFaceoffPct}%")
            Spacer(Modifier.height(10.dp))
            when {
                recap != null -> Text(recap!!, color = TextInk, fontSize = 14.sp, lineHeight = 20.sp)
                else -> {
                    note?.let { Text(it, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(8.dp)) }
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                when (val r = vm.writeRecap(period)) {
                                    is AiResult.Ok -> recap = r.text
                                    is AiResult.Unavailable -> note = FamilyAi.reasonText(r.reason)
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) { Text(if (busy) "Writing recap…" else "AI recap", color = if (busy) Muted else TextInk, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, away: String, home: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(away, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
        Text(label, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(home, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
    }
}
