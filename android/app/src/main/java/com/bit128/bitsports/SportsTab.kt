package com.bit128.bitsports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.sports.League
import com.bit128.bitsports.sports.Leagues
import com.bit128.bitsports.ui.PixelIcon
import com.bit128.bitsports.ui.sportIconName

/** Every game in the sports you watch, one league at a time. */
@Composable
fun SportsTab(vm: BitsportsViewModel) {
    val watched = Leagues.all.filter { it.key in vm.ui.watched }
    Column(Modifier.fillMaxSize()) {
        if (watched.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Spacer(Modifier.height(20.dp))
                Heading("WATCHED SPORTS", size = 18.sp)
                Spacer(Modifier.height(16.dp))
                CardBox { InfoLine("You're not watching any sports. Turn some on in Settings.") }
            }
            return@Column
        }
        val selected = watched.firstOrNull { it.key == vm.sportsLeague } ?: watched.firstOrNull { liveCount(vm, it) > 0 } ?: watched.first()
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            watched.forEach { l -> LeagueChip(l, l == selected, liveCount(vm, l)) { vm.sportsLeague = l.key } }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(6.dp))
            if (selected == Leagues.NHL) NhlBoard(vm) else EspnBoard(vm, selected)
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun liveCount(vm: BitsportsViewModel, l: League) =
    if (l == Leagues.NHL) vm.games.count { it.state.isLive } else vm.espnGames[l.key].orEmpty().count { it.isLive }

@Composable
private fun LeagueChip(l: League, selected: Boolean, live: Int, onClick: () -> Unit) {
    val accent = Color(LocalUi.current.accent)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) Card3 else Card)
            .border(1.dp, if (selected) accent else Line, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIcon(sportIconName(l.icon), if (selected) accent else Muted, 16.dp)
        Spacer(Modifier.width(8.dp))
        Label(l.label.uppercase(), color = if (selected) TextInk else Muted)
        if (live > 0) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).background(Live))
            Spacer(Modifier.width(4.dp))
            Label("$live", color = Live)
        }
    }
}

@Composable
private fun NhlBoard(vm: BitsportsViewModel) {
    Segmented(listOf("Scores", "Standings", "Leaders"), vm.nhlView) { vm.nhlView = it }
    Spacer(Modifier.height(20.dp))
    when (vm.nhlView) {
        1 -> StandingsView(vm)
        2 -> LeadersView(vm)
        else -> NhlScores(vm)
    }
}

@Composable
private fun NhlScores(vm: BitsportsViewModel) {
    val note = vm.nhlNote()
    val games = vm.orderedGames()
    SectionHead("NHL", note.text.ifEmpty { "${games.size} games · tap one for lineups and more" }, noteColor = if (note.warn) Warn else Muted)
    when {
        !vm.gamesLoaded -> CardBox { InfoLine("Loading scores…") }
        games.isEmpty() && vm.gamesError != null -> CardBox {
            InfoLine("Scores show here once the NHL feed answers.", color = Warn)
            RetryButton { vm.refreshGames() }
        }
        games.isEmpty() -> CardBox { InfoLine("No NHL games today.") }
        else -> {
            val followed = vm.followedAbbrevs()
            games.forEach { g ->
                GameCard(g, hero = false, followed = followed, onClick = { vm.openGame(g.id) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun EspnBoard(vm: BitsportsViewModel, league: League) {
    val games = vm.espnGames[league.key]
    val err = vm.espnErrors[league.key]
    SectionHead(
        league.label.uppercase(),
        when {
            err != null -> err
            games == null -> ""
            else -> "${games.size} games · tap one for more"
        },
        noteColor = if (err != null) Warn else Muted,
    )
    when {
        games == null && err == null -> CardBox { InfoLine("Loading scores…") }
        games.isNullOrEmpty() && err != null -> CardBox {
            InfoLine("Scores show here once the feed answers.", color = Warn)
            RetryButton { vm.refreshEspn() }
        }
        games.isNullOrEmpty() -> CardBox { InfoLine("No ${league.label} games right now.") }
        else -> {
            val ordered = games.sortedWith(compareBy({ if (it.isLive) 0 else if (it.isOver) 2 else 1 }, { it.start }))
            ordered.forEach { g ->
                EspnCard(g) { t -> vm.teams.any { Leagues.match(it.sport) == league && t.matches(it.team) } }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Scores from ESPN's public feed.", color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Normal)
}
