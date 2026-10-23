package com.bit128.bitsports

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bit128.bitsports.ai.AiResult
import com.bit128.bitsports.ai.FamilyAi
import com.bit128.bitsports.ai.RecapPrompt
import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.alerts.LiveHub
import com.bit128.bitsports.alerts.WatchStore
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GameExtras
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlSource
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.sports.EspnGame
import com.bit128.bitsports.sports.EspnSource
import com.bit128.bitsports.sports.EspnTeam
import com.bit128.bitsports.sports.League
import com.bit128.bitsports.sports.Leagues
import com.bit128.bitsports.ui.ScoreFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

const val MAX_PER_SPORT = 4
const val ALL_TAB = "all"

enum class Tab { HOME, SPORTS, SETTINGS }

/** What a feed's note should say: [text] in warning colour only when the numbers are really stale. */
data class FeedNote(val text: String, val warn: Boolean)

class BitsportsViewModel(
    private val store: TeamsStore,
    private val prefsStore: PrefsStore,
    private val appContext: Context,
    private val source: NhlSource = DirectNhlSource(),
    private val espn: EspnSource = EspnSource(),
) : ViewModel() {
    var teams by mutableStateOf(store.load())
        private set

    var ui by mutableStateOf(prefsStore.load())
        private set

    fun updateUi(p: UiPrefs) {
        val sportsChanged = p.watched != ui.watched
        ui = p
        prefsStore.save(p)
        if (sportsChanged) refreshEspn()
    }

    var tab by mutableStateOf(Tab.HOME)
    var sportsLeague by mutableStateOf<String?>(null)
    /** NHL on the Sports tab: 0 scores, 1 standings, 2 leaders. */
    var nhlView by mutableStateOf(0)

    var activeTab by mutableStateOf(ALL_TAB)
        private set

    var addMessage by mutableStateOf("")
        private set

    var newsItems by mutableStateOf<List<NewsItem>>(emptyList())
        private set
    var newsLoading by mutableStateOf(false)
        private set
    var newsError by mutableStateOf<String?>(null)
        private set

    private val cache = HashMap<String, Pair<List<NewsItem>, Long>>()

    // --- NHL scores ---------------------------------------------------------------------------
    var games by mutableStateOf<List<Game>>(emptyList())
        private set
    var gamesLoaded by mutableStateOf(false)
        private set
    var gamesError by mutableStateOf<String?>(null)
        private set
    var gamesFetchedAt by mutableStateOf<Instant?>(null)
        private set
    /** Next game for each followed NHL team that has none today. */
    var nextGames by mutableStateOf<Map<String, Game>>(emptyMap())
        private set
    var standings by mutableStateOf<List<com.bit128.bitsports.nhl.StandingRow>>(emptyList())
        private set
    var leaders by mutableStateOf<List<com.bit128.bitsports.nhl.LeaderBoard>>(emptyList())
        private set
    var leagueError by mutableStateOf<String?>(null)
        private set
    private var leagueAt: Instant? = null
    /** Ticks every few seconds so "updated Ns ago" labels stay current. */
    var now by mutableStateOf(Instant.now())
        private set

    // --- Other leagues (ESPN) -----------------------------------------------------------------
    var espnGames by mutableStateOf<Map<String, List<EspnGame>>>(emptyMap())
        private set
    var espnErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var espnTeams by mutableStateOf<Map<String, List<EspnTeam>>>(emptyMap())
        private set

    // --- The open game ------------------------------------------------------------------------
    var openGameId by mutableStateOf<Long?>(null)
        private set
    var detail by mutableStateOf<GameDetail?>(null)
        private set
    var extras by mutableStateOf<GameExtras?>(null)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set
    private var detailJob: Job? = null
    private val seenGoals = HashMap<Long, Set<Int>>()

    /** A goal to celebrate on screen (from the watcher, the open game, or a Customize preview). */
    var goalMoment by mutableStateOf<GoalEvent?>(null)

    val watchStore = WatchStore(appContext)

    fun followedAbbrevs(): Set<String> = teams.mapNotNull { nhlTeamOf(it)?.abbrev }.toSet()
    fun orderedGames(): List<Game> = ScoreFormat.order(games, followedAbbrevs())

    /** Today's NHL games with one of your teams in them, live first. */
    fun favoriteNhlGames(): List<Game> {
        val f = followedAbbrevs()
        return orderedGames().filter { it.away.abbrev in f || it.home.abbrev in f }
    }

    /** Games from other leagues with one of your teams in them. */
    fun favoriteEspnGames(): List<EspnGame> = teams.flatMap { t ->
        val league = Leagues.match(t.sport)?.takeIf { it.path != null } ?: return@flatMap emptyList()
        espnGames[league.key].orEmpty().filter { it.involves(t.team) }
    }.distinctBy { it.id }.sortedWith(compareBy({ if (it.isLive) 0 else if (it.isOver) 2 else 1 }, { it.start }))

    private fun nhlTeamOf(t: FollowedTeam) =
        if (Leagues.match(t.sport).let { it == null || it == Leagues.NHL }) NhlTeams.match(t.team) else null

    private var pollJobs: List<Job> = emptyList()

    init {
        refreshNews()
        viewModelScope.launch { while (true) { now = Instant.now(); delay(5_000) } }
        viewModelScope.launch {
            LiveHub.goals.collect { g -> celebrate(g) } // the watcher only follows your teams
        }
    }

    /**
     * Polls only while the app is on screen. Android cuts a backgrounded app's network, so polling
     * there just piles up failures; coming back refreshes at once instead.
     */
    fun onForeground() {
        // Opening the app mid-game starts the watcher if the background start was blocked.
        AlertScheduler.refresh(appContext)
        if (pollJobs.any { it.isActive }) return
        pollJobs = listOf(
            viewModelScope.launch { pollGames() },
            viewModelScope.launch { pollEspn() },
        )
        openGameId?.let { if (detailJob?.isActive != true) openGame(it) }
    }

    fun onBackground() {
        pollJobs.forEach { it.cancel() }
        pollJobs = emptyList()
        detailJob?.cancel()
    }

    private suspend fun pollGames() {
        var failures = 0
        while (true) {
            val ok = refreshGamesOnce()
            failures = if (ok) 0 else failures + 1
            delay(
                when {
                    !ok -> minOf(15_000L * failures, 60_000L) // back off, but never sit on an error for 5 minutes
                    games.any { it.state.isLive } -> 15_000
                    games.any { !it.state.isOver && Duration.between(now, it.startUtc).toMinutes() < 20 } -> 60_000
                    else -> 300_000
                }
            )
        }
    }

    fun refreshGames() { viewModelScope.launch { refreshGamesOnce() } }

    private suspend fun refreshGamesOnce(): Boolean {
        val ok = try {
            games = source.scoreboard()
            gamesFetchedAt = Instant.now()
            gamesError = null
            com.bit128.bitsports.widget.Widgets.pushScoreboard(appContext, games)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            gamesError = "Couldn't reach the NHL feed."
            false
        }
        gamesLoaded = true
        if (ok) {
            refreshNextGames()
            if (leagueAt == null || Duration.between(leagueAt, Instant.now()).toMinutes() >= 30) refreshLeagueOnce()
        }
        return ok
    }

    fun refreshLeague() { viewModelScope.launch { refreshLeagueOnce() } }

    /** Standings and stat leaders move once a night, so every 30 minutes is plenty. */
    private suspend fun refreshLeagueOnce() {
        try {
            standings = source.standings()
            leaders = try { source.leaders() } catch (e: java.io.IOException) { leaders }
            leagueAt = Instant.now()
            leagueError = null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            leagueError = "Couldn't reach the NHL standings."
        }
    }

    fun standingFor(abbrev: String) = standings.firstOrNull { it.abbrev == abbrev }

    /** For followed teams that aren't playing today: when's the next one? Best effort. */
    private suspend fun refreshNextGames() {
        val today = games.flatMap { listOf(it.away.abbrev, it.home.abbrev) }.toSet()
        val idle = followedAbbrevs() - today
        if (idle.isEmpty()) { nextGames = emptyMap(); return }
        val found = HashMap<String, Game>()
        for (a in idle) {
            val known = nextGames[a]?.takeIf { it.startUtc.isAfter(Instant.now()) }
            val g = known ?: try {
                source.nextGames(a).filter { !it.state.isOver && it.startUtc.isAfter(Instant.now()) }.minByOrNull { it.startUtc }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            if (g != null) found[a] = g
        }
        nextGames = found
    }

    fun nhlNote(): FeedNote {
        val at = gamesFetchedAt
        val err = gamesError
        return when {
            err == null && at != null -> FeedNote(AlertPlanner.staleLabel(at, now) ?: "", false)
            err == null -> FeedNote("", false)
            // Recent numbers on screen and a retry already queued: say so quietly.
            at != null && Duration.between(at, now).toMinutes() < 10 ->
                FeedNote("${AlertPlanner.staleLabel(at, now) ?: "Just updated"} · reconnecting", false)
            else -> FeedNote("Can't reach the NHL feed · retrying", true)
        }
    }

    // --- ESPN leagues -------------------------------------------------------------------------

    /** Leagues to keep fresh: the ones you watch, plus any league one of your teams plays in. */
    private fun neededLeagues(): List<League> {
        val keys = ui.watched + teams.mapNotNull { Leagues.match(it.sport)?.key }
        return Leagues.all.filter { it.key in keys && it.path != null }
    }

    private suspend fun pollEspn() {
        while (true) {
            refreshEspnOnce()
            val live = espnGames.values.flatten().any { it.isLive }
            delay(if (live || espnErrors.isNotEmpty()) 45_000 else 300_000)
        }
    }

    fun refreshEspn() { viewModelScope.launch { refreshEspnOnce() } }

    private suspend fun refreshEspnOnce() {
        val results = coroutineScope {
            neededLeagues().map { l ->
                async {
                    l.key to try {
                        Result.success(espn.scoreboard(l))
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Result.failure(e)
                    }
                }
            }.awaitAll()
        }
        val games = espnGames.toMutableMap()
        val errors = HashMap<String, String>()
        results.forEach { (key, r) ->
            r.onSuccess { games[key] = it }.onFailure { errors[key] = "Couldn't reach the scores feed." }
        }
        espnGames = games
        espnErrors = errors
    }

    fun loadTeams(league: League) {
        if (league.path == null || espnTeams.containsKey(league.key)) return
        viewModelScope.launch {
            try {
                espnTeams = espnTeams + (league.key to espn.teams(league))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    // --- The open game ------------------------------------------------------------------------

    fun openGame(id: Long) {
        if (openGameId != id) {
            detail = null
            extras = null
        }
        openGameId = id
        detailError = null
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                try {
                    val d = source.playByPlay(id)
                    noticeGoals(d)
                    detail = d
                    detailError = null
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    detailError = "Couldn't reach the NHL feed."
                }
                // Lineups, box score and line changes ride along: every tick live, less often otherwise.
                val live = detail?.game?.state?.isLive == true
                if (live || tick % 4 == 0) {
                    try {
                        extras = source.extras(id)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                }
                tick++
                delay(if (live) 15_000 else if (detailError != null) 20_000 else 60_000)
            }
        }
    }

    fun closeGame() {
        detailJob?.cancel()
        detailJob = null
        openGameId = null
        detail = null
        extras = null
    }

    /** New goals for a followed team in the open game get the GOAL moment (first load excluded). */
    private fun noticeGoals(d: GameDetail) {
        val ids = GameAnalysis.goals(d).associateBy { it.eventId }
        val seen = seenGoals[d.game.id]
        seenGoals[d.game.id] = ids.keys
        if (seen == null || !d.game.state.isLive) return
        (ids.keys - seen).mapNotNull { ids[it] }.filter { it.teamAbbrev in followedAbbrevs() }.lastOrNull()?.let(::celebrate)
    }

    private var lastCelebrated: Pair<Long, Int>? = null
    private fun celebrate(g: GoalEvent) {
        val key = g.gameId to g.eventId
        if (key == lastCelebrated) return
        lastCelebrated = key
        goalMoment = g
    }

    fun previewGoal() {
        val sample = games.firstOrNull()
        goalMoment = GoalEvent(
            gameId = -1, eventId = (Math.random() * 1e6).toInt(), teamAbbrev = followedAbbrevs().firstOrNull() ?: sample?.home?.abbrev ?: "EDM",
            scorer = "Preview", scorerTotal = null, assists = emptyList(), strength = com.bit128.bitsports.nhl.Strength.EV,
            shotType = null, goalie = null, period = 1, periodType = "REG", timeInPeriod = "00:00", awayScore = 1, homeScore = 0, clipUrl = null,
        )
    }

    fun recapFor(gameId: Long, period: Int): String? = watchStore.recap(gameId, period)

    suspend fun writeRecap(period: Int): AiResult {
        val d = detail ?: return AiResult.Unavailable("failed")
        val (system, user) = RecapPrompt.build(d, period)
        val r = FamilyAi.generate(appContext, system, user)
        if (r is AiResult.Ok) watchStore.saveRecap(d.game.id, period, r.text)
        return r
    }

    // --- Teams + news -------------------------------------------------------------------------

    /** "nhl", "NHL" and "Hockey" are one sport; "Oilers" and "Edmonton Oilers" one team. */
    private fun sameSport(a: String, b: String) =
        slugify(a) == slugify(b) || Leagues.match(a).let { it != null && it == Leagues.match(b) }

    private fun sameTeam(a: FollowedTeam, team: String, sport: String) =
        sameSport(a.sport, sport) && (slugify(a.team) == slugify(team) || NhlTeams.match(a.team).let { it != null && it == NhlTeams.match(team) })

    fun countFor(sport: String) = teams.count { sameSport(it.sport, sport) }

    fun isFollowing(team: String, sport: String) = teams.any { sameTeam(it, team, sport) }

    fun addTeam(team: String, sport: String) {
        addMessage = ""
        if (team.isBlank() || sport.isBlank()) {
            addMessage = "Enter both a team and a sport or league."
            return
        }
        if (isFollowing(team, sport)) {
            addMessage = "$team is already in your $sport list."
            return
        }
        if (countFor(sport) >= MAX_PER_SPORT) {
            addMessage = "$sport is full ($MAX_PER_SPORT of $MAX_PER_SPORT). Remove one to add another."
            return
        }
        teams = teams + FollowedTeam(team.trim(), sport.trim())
        teamsChanged()
    }

    /** One tap in the picker: follow, or unfollow if already followed. */
    fun toggleTeam(team: String, sport: String) {
        val existing = teams.firstOrNull { sameTeam(it, team, sport) }
        if (existing != null) removeTeam(existing) else addTeam(team, sport)
    }

    fun removeTeam(t: FollowedTeam) {
        addMessage = ""
        teams = teams.filter { !(slugify(it.sport) == slugify(t.sport) && slugify(it.team) == slugify(t.team)) }
        if (activeTab == t.key) activeTab = ALL_TAB
        teamsChanged()
    }

    private fun teamsChanged() {
        store.save(teams)
        AlertScheduler.refresh(appContext)
        com.bit128.bitsports.widget.Widgets.refresh(appContext)
        refreshNews()
        refreshEspn()
        viewModelScope.launch { refreshNextGames() }
    }

    fun selectTab(key: String) {
        activeTab = key
        refreshNews()
    }

    fun refreshNews() {
        val targets = if (activeTab == ALL_TAB) teams else teams.filter { it.key == activeTab }
        if (targets.isEmpty()) {
            newsItems = emptyList()
            newsError = null
            newsLoading = false
            return
        }
        newsLoading = true
        newsError = null
        viewModelScope.launch {
            val results = targets.map { t ->
                val cached = cache[t.key]
                if (cached != null && System.currentTimeMillis() - cached.second < 10 * 60 * 1000) {
                    NewsResult.Ok(cached.first)
                } else {
                    val r = NewsRepository.fetch(t.team, t.sport)
                    if (r is NewsResult.Ok) cache[t.key] = r.items to System.currentTimeMillis()
                    r
                }
            }
            val oks = results.filterIsInstance<NewsResult.Ok>()
            if (oks.isEmpty() && results.isNotEmpty()) {
                newsError = "Couldn't reach the news service."
                newsItems = emptyList()
            } else {
                newsItems = oks.flatMap { it.items }.sortedByDescending { it.publishedAt ?: "" }
                newsError = null
            }
            newsLoading = false
        }
    }
}
