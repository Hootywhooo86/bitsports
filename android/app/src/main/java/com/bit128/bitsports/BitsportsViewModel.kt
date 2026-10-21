package com.bit128.bitsports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

const val MAX_PER_SPORT = 4
const val ALL_TAB = "all"

class BitsportsViewModel(private val store: TeamsStore) : ViewModel() {
    var teams by mutableStateOf(store.load())
        private set

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

    init {
        refreshNews()
    }

    fun countFor(sport: String) = teams.count { slugify(it.sport) == slugify(sport) }

    fun addTeam(team: String, sport: String) {
        addMessage = ""
        if (team.isBlank() || sport.isBlank()) {
            addMessage = "Enter both a team and a sport/league."
            return
        }
        val dupe = teams.any { slugify(it.sport) == slugify(sport) && slugify(it.team) == slugify(team) }
        if (dupe) {
            addMessage = "$team is already in your $sport list."
            return
        }
        if (countFor(sport) >= MAX_PER_SPORT) {
            addMessage = "$sport is full ($MAX_PER_SPORT/$MAX_PER_SPORT) — remove one to add another."
            return
        }
        teams = teams + FollowedTeam(team.trim(), sport.trim())
        store.save(teams)
        refreshNews()
    }

    fun removeTeam(t: FollowedTeam) {
        teams = teams.filter { !(slugify(it.sport) == slugify(t.sport) && slugify(it.team) == slugify(t.team)) }
        store.save(teams)
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
