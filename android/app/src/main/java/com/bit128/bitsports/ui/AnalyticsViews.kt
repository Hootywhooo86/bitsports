package com.bit128.bitsports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.*
import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.nhl.Chances
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GameExtras
import com.bit128.bitsports.nhl.Tape
import com.bit128.bitsports.nhl.WinProb
import com.bit128.bitsports.nhl.Xg
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun pct(v: Double) = "${(v * 100).roundToInt()}%"
private fun signed(v: Double, places: Int = 1) = (if (v >= 0) "+" else "") + "%.${places}f".format(Locale.ROOT, v)

/**
 * Win probability: your team's chances now, what a goal would do, the game's chart, and the
 * biggest moments, movers and pressure. [homeShare]: the home side's chance at puck drop from
 * the odds, when known.
 */
@Composable
fun WinProbCard(g: Game, d: GameDetail, homeShare: Double?, mine: String?) {
    val s = remember(d, homeShare, mine) { WinProb.summary(d, homeShare, mine) }
    val me = mine ?: if (s.now >= 0.5) g.home.abbrev else g.away.abbrev
    val them = if (me == g.home.abbrev) g.away.abbrev else g.home.abbrev
    val mineHome = me == g.home.abbrev
    val (mi, ti) = TeamInk.of(me) to TeamInk.of(them)
    val myNow = if (mineHome) s.now else 1 - s.now
    SectionHead("WIN PROBABILITY", if (homeShare != null) "Score, clock and the odds" else "Score and clock")
    CardBox {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Label("$me TO WIN", color = Color(mi))
                    Text(pct(myNow), color = TextInk, fontFamily = Pixel, fontSize = 40.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Label(them, color = Color(ti))
                    Text(pct(1 - myNow), color = TextInk, fontFamily = Pixel, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            // The split bar: your side's share in your colour, the rest in theirs.
            PixelGrid(remember(myNow) { LedGrid(128, 4).also { b -> val x = (myNow * 128).roundToInt(); b.fill(0, 0, x, 4, mi); b.fill(x + 1, 0, 128 - x - 1, 4, ti) } })
            if (!g.state.isOver) {
                Spacer(Modifier.height(6.dp))
                val goalFor = s.goalTeam
                val ifMine = if (mineHome) s.ifGoal else 1 - s.ifGoal
                Text("A ${goalFor} goal now makes it ${pct(if (goalFor == me) ifMine else 1 - ifMine)} for $goalFor", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            PixelGrid(remember(s, mineHome) { AnalyticsLeds.winProb(s.timeline, mineHome, mi, ti) })
            Spacer(Modifier.height(12.dp))
            val start = if (mineHome) s.atPuckDrop else 1 - s.atPuckDrop
            val tiles = listOf(
                Triple("EXCITEMENT PACE", WinProb.excitementPercentile(s.excitementPace).let { p -> if (p >= 0.5) "Top ${((1 - p) * 100).roundToInt().coerceAtLeast(10)}%" else "Bottom ${(p * 100).roundToInt().coerceAtLeast(10)}%" }, "vs this season"),
                Triple("BIGGEST SWING", s.biggest?.let { "+" + (it.change * 100).roundToInt() + "%" } ?: "–",
                    s.biggest?.let { "${(it.scorer ?: it.team).substringAfterLast(' ')} ${AlertPlanner.periodLabel(it.period, if (it.period > 3) "OT" else "REG")} ${it.clock}" } ?: "no goals yet"),
                Triple("$me LOW POINT", s.lowPoint?.let { pct(if (mineHome) it.home else 1 - it.home) } ?: "–",
                    s.lowPoint?.let { "${it.awayScore}-${it.homeScore} at ${it.second / 60}:${"%02d".format(it.second % 60)}" } ?: ""),
                Triple("$me FAVOURED", pct(s.favouredShare), "of the game so far"),
                Triple("AT PUCK DROP", pct(start), "$me then, ${pct(myNow)} now"),
            )
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, big, small) -> Tile(label, big, small, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (s.movers.isNotEmpty()) {
                Label("BIGGEST MOVERS")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.movers.forEach { (name, v) ->
                        Tile(name.substringAfterLast(' ').uppercase(), signed(v * 100, 0) + "%", null, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Label("PRESSURE · xG per 2 min, $me up, $them down")
            Spacer(Modifier.height(6.dp))
            PixelGrid(remember(d, mineHome) { AnalyticsLeds.pressure(Xg.shots(d), mineHome, mi, ti) })
        }
    }
}

@Composable
private fun Tile(label: String, big: String, small: String?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Card2).padding(10.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(big, color = TextInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        small?.let { Text(it, color = Dim, fontSize = 11.sp, maxLines = 1) }
    }
}

/** Shots and chances: expected goals and the race chart, then special teams, goaltending, physicality. */
@Composable
fun ChancesCard(g: Game, d: GameDetail, x: GameExtras?) {
    val c = remember(d) { Chances.of(d) }
    val (ai, hi) = TeamInk.pair(g.away.abbrev, g.home.abbrev)
    fun stat(label: String) = x?.gameStats?.firstOrNull { it.label == label }
    SectionHead("SHOTS AND CHANCES", "${g.away.abbrev} · ${g.home.abbrev}")
    CardBox {
        Column(Modifier.padding(16.dp)) {
            Row { Label("EXPECTED GOALS"); Spacer(Modifier.weight(1f))
                Text("%.1f".format(Locale.ROOT, c.awayXg), color = Color(ai), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(" · ", color = Dim, fontSize = 13.sp)
                Text("%.1f".format(Locale.ROOT, c.homeXg), color = Color(hi), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(6.dp))
            PixelGrid(remember(c) { AnalyticsLeds.xgRace(c, ai, hi) })
            Spacer(Modifier.height(10.dp))
            @Composable fun row(label: String, a: String, h: String, better: Int) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a, color = if (better < 0) Color(ai) else TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(88.dp))
                    Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text(h, color = if (better > 0) Color(hi) else TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(88.dp))
                }
                HorizontalDivider(color = Line)
            }
            fun cmp(a: Double, h: Double) = if (a > h) -1 else if (h > a) 1 else 0
            row("EXPECTED GOALS", "%.1f".format(Locale.ROOT, c.awayXg), "%.1f".format(Locale.ROOT, c.homeXg), cmp(c.awayXg, c.homeXg))
            row("CLOSE RANGE · AVG", "${c.awayClose} · ${c.awayAvgDist.roundToInt()} ft", "${c.homeClose} · ${c.homeAvgDist.roundToInt()} ft", cmp(c.awayClose.toDouble(), c.homeClose.toDouble()))
            Spacer(Modifier.height(8.dp)); Label("SPECIAL TEAMS")
            stat("Power play")?.let { row("POWER PLAY", it.away, it.home, 0) }
            stat("Faceoffs")?.let { row("FACEOFF %", it.away, it.home, cmp(it.awayShare.toDouble(), 1.0 - it.awayShare)) }
            val tm = listOf("Takeaways", "Giveaways").map { stat(it) }
            if (tm.all { it != null }) {
                val a = (tm[0]!!.away.toIntOrNull() ?: 0) - (tm[1]!!.away.toIntOrNull() ?: 0)
                val h = (tm[0]!!.home.toIntOrNull() ?: 0) - (tm[1]!!.home.toIntOrNull() ?: 0)
                row("TURNOVER MARGIN", signed(a.toDouble(), 0), signed(h.toDouble(), 0), cmp(a.toDouble(), h.toDouble()))
            }
            Spacer(Modifier.height(8.dp)); Label("GOALTENDING")
            row("SAVE %", c.awaySavePct?.let { "%.3f".format(Locale.ROOT, it).removePrefix("0") } ?: "–", c.homeSavePct?.let { "%.3f".format(Locale.ROOT, it).removePrefix("0") } ?: "–",
                cmp(c.awaySavePct ?: 0.0, c.homeSavePct ?: 0.0))
            row("SAVED ABOVE EXP.", signed(c.awayGsax), signed(c.homeGsax), cmp(c.awayGsax, c.homeGsax))
            row("HIGH-DANGER SAVES", "${c.awayHd.first}/${c.awayHd.second}", "${c.homeHd.first}/${c.homeHd.second}", 0)
            Spacer(Modifier.height(8.dp)); Label("PHYSICALITY")
            listOf("Hits" to "HITS", "Blocks" to "BLOCKS", "Penalty min" to "PIM").forEach { (k, l) ->
                stat(k)?.let { row(l, it.away, it.home, if (k == "Penalty min") 0 else cmp(it.away.toDoubleOrNull() ?: 0.0, it.home.toDoubleOrNull() ?: 0.0)) }
            }
            Spacer(Modifier.height(6.dp))
            Text("Expected goals: bitsports' model from this season's NHL shots (distance, angle, shot type, strength).", color = Dim, fontSize = 11.sp)
        }
    }
}

/** The season tape: chance share, finishing, bounces and PDO for both teams, with league ranks. */
@Composable
fun TapeCard(g: Game, all: Map<String, Tape>) {
    val a = all[g.away.abbrev] ?: return
    val h = all[g.home.abbrev] ?: return
    val (ai, hi) = TeamInk.pair(g.away.abbrev, g.home.abbrev)
    SectionHead("THE TAPE", "Season to date · ${minOf(a.games, h.games)}+ games")
    CardBox {
        Column(Modifier.padding(16.dp)) {
            fun ord(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
            data class R(val title: String, val sub: String, val sel: (Tape) -> Double, val fmt: (Double) -> String, val key: String)
            listOf(
                R("Chance share", "5-on-5 expected goals", { it.chanceShare }, { pct(it) }, "chance"),
                R("Finishing", "goals vs expected /gm", { it.finishing }, { signed(it, 2) }, "finishing"),
                R("Bounces", "+ goalie /gm", { it.bounces }, { signed(it, 2) }, "bounces"),
                R("PDO", "5v5 sh% + sv%", { it.pdo }, { "%.1f".format(Locale.ROOT, it) }, "pdo"),
            ).forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(r.sub, color = Dim, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row {
                            Text(r.fmt(r.sel(a)), color = Color(ai), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(" · ", color = Dim, fontSize = 14.sp)
                            Text(r.fmt(r.sel(h)), color = Color(hi), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${ord(a.ranks[r.key] ?: 0)} · ${ord(h.ranks[r.key] ?: 0)}", color = Dim, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val (lo, hiV) = AnalyticsLeds.range(all.values, r.sel)
                PixelGrid(remember(r.key, a, h) { AnalyticsLeds.tapeTrack(r.sel(a), r.sel(h), lo, hiV, ai, hi) })
                Spacer(Modifier.height(12.dp))
            }
            Row { Text("BEHIND", color = Dim, fontSize = 10.sp); Spacer(Modifier.weight(1f)); Text("LEAGUE", color = Dim, fontSize = 10.sp); Spacer(Modifier.weight(1f)); Text("AHEAD", color = Dim, fontSize = 10.sp) }
            Spacer(Modifier.height(6.dp))
            Text("Data: MoneyPuck.com", color = Dim, fontSize = 11.sp)
        }
    }
}
