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
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.alerts.LiveHub
import com.bit128.bitsports.alerts.WatchStore
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlSource
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.ui.ScoreFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

const val MAX_PER_SPORT = 4
const val ALL_TAB = "all"

class BitsportsViewModel(
    private val store: TeamsStore,
    private val prefsStore: PrefsStore,
    private val appContext: Context,
    private val source: NhlSource = DirectNhlSource(),
) : ViewModel() {
    var teams by mutableStateOf(store.load())
        private set

    var ui by mutableStateOf(prefsStore.load())
        private set

    fun updateUi(p: UiPrefs) {
        ui = p
        prefsStore.save(p)
    }

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
    /** Ticks every few seconds so "updated Ns ago" labels stay current. */
    var now by mutableStateOf(Instant.now())
        private set

    // --- The open game ------------------------------------------------------------------------
    var openGameId by mutableStateOf<Long?>(null)
        private set
    var detail by mutableStateOf<GameDetail?>(null)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set
    private var detailJob: Job? = null
    private val seenGoals = HashMap<Long, Set<Int>>()

    /** A goal to celebrate on screen (from the watcher, the open game, or a Customize preview). */
    var goalMoment by mutableStateOf<GoalEvent?>(null)

    val watchStore = WatchStore(appContext)

    fun followedAbbrevs(): Set<String> = teams.mapNotNull { NhlTeams.match(it.team)?.abbrev }.toSet()
    fun orderedGames(): List<Game> = ScoreFormat.order(games, followedAbbrevs())

    init {
        refreshNews()
        viewModelScope.launch { pollGames() }
        viewModelScope.launch { while (true) { now = Instant.now(); delay(5_000) } }
        viewModelScope.launch {
            LiveHub.goals.collect { g -> celebrate(g) } // the watcher only follows your teams
        }
    }

    private suspend fun pollGames() {
        while (true) {
            refreshGamesOnce()
            delay(if (games.any { it.state.isLive }) 15_000 else 300_000)
        }
    }

    fun refreshGames() { viewModelScope.launch { refreshGamesOnce() } }

    private suspend fun refreshGamesOnce() {
        try {
            games = source.scoreboard()
            gamesFetchedAt = Instant.now()
            gamesError = null
            com.bit128.bitsports.widget.Widgets.pushScoreboard(appContext, games)
        } catch (e: Exception) {
            gamesError = "Couldn't reach the NHL feed."
        }
        gamesLoaded = true
    }

    fun openGame(id: Long) {
        openGameId = id
        detail = null
        detailError = null
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            while (true) {
                try {
                    val d = source.playByPlay(id)
                    noticeGoals(d)
                    detail = d
                    detailError = null
                } catch (e: Exception) {
                    detailError = "Couldn't reach the NHL feed."
                }
                delay(if (detail?.game?.state?.isLive == true) 15_000 else 120_000)
            }
        }
    }

    fun closeGame() {
        detailJob?.cancel()
        detailJob = null
        openGameId = null
        detail = null
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

    // --- Teams + news (unchanged behaviour) ---------------------------------------------------

    fun countFor(sport: String) = teams.count { slugify(it.sport) == slugify(sport) }

    fun addTeam(team: String, sport: String) {
        addMessage = ""
        if (team.isBlank() || sport.isBlank()) {
            addMessage = "Enter both a team and a sport or league."
            return
        }
        val dupe = teams.any { slugify(it.sport) == slugify(sport) && slugify(it.team) == slugify(team) }
        if (dupe) {
            addMessage = "$team is already in your $sport list."
            return
        }
        if (countFor(sport) >= MAX_PER_SPORT) {
            addMessage = "$sport is full ($MAX_PER_SPORT of $MAX_PER_SPORT). Remove one to add another."
            return
        }
        teams = teams + FollowedTeam(team.trim(), sport.trim())
        store.save(teams)
        AlertScheduler.refresh(appContext)
        com.bit128.bitsports.widget.Widgets.refresh(appContext)
        refreshNews()
    }

    fun removeTeam(t: FollowedTeam) {
        teams = teams.filter { !(slugify(it.sport) == slugify(t.sport) && slugify(it.team) == slugify(t.team)) }
        store.save(teams)
        AlertScheduler.refresh(appContext)
        com.bit128.bitsports.widget.Widgets.refresh(appContext)
        if (activeTab == t.key) activeTab = ALL_TAB
        refreshNews()
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
