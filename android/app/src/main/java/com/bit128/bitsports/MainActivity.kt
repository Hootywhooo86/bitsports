package com.bit128.bitsports

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bit128.bitsports.alerts.AlertNotifier
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.ui.GameScreen
import com.bit128.bitsports.ui.GoalMoment
import com.bit128.bitsports.ui.PixelIcon

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
            // Feeds poll only while bitsports is on screen, and refresh the moment it comes back.
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val obs = LifecycleEventObserver { _, e ->
                    when (e) {
                        Lifecycle.Event.ON_START -> vm.onForeground()
                        Lifecycle.Event.ON_STOP -> vm.onBackground()
                        else -> {}
                    }
                }
                lifecycle.addObserver(obs)
                onDispose { lifecycle.removeObserver(obs) }
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
                            AppShell(vm)
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

/** Three places: Home (your teams), Sports (everything you watch), Settings (everything you can change). */
@Composable
fun AppShell(vm: BitsportsViewModel) {
    if (vm.tab != Tab.HOME) BackHandler { vm.tab = Tab.HOME }
    Column(Modifier.fillMaxSize()) {
        TopBar(vm)
        Box(Modifier.weight(1f)) {
            when (vm.tab) {
                Tab.HOME -> HomeTab(vm)
                Tab.SPORTS -> SportsTab(vm)
                Tab.SETTINGS -> SettingsTab(vm)
            }
        }
        BottomNav(vm)
    }
}

@Composable
fun TopBar(vm: BitsportsViewModel) {
    val accent = Color(LocalUi.current.accent)
    Column {
        Row(
            Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PuckMark(accent)
            Spacer(Modifier.width(12.dp))
            Text("BITSPORTS", color = TextInk, fontFamily = Pixel, fontSize = 16.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            val live = vm.games.count { it.state.isLive } + vm.espnGames.values.flatten().count { it.isLive }
            if (live > 0) {
                Row(Modifier.clickable { vm.tab = Tab.SPORTS }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiveMarker()
                    Spacer(Modifier.width(6.dp))
                    Label("$live", color = Live)
                }
            }
        }
        HorizontalDivider(color = Line, thickness = 1.dp)
    }
}

@Composable
private fun BottomNav(vm: BitsportsViewModel) {
    val accent = Color(LocalUi.current.accent)
    Column {
        HorizontalDivider(color = Line, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().background(Ground).height(64.dp)) {
            listOf(Tab.HOME to "home", Tab.SPORTS to "sports", Tab.SETTINGS to "settings").forEach { (tab, icon) ->
                val on = vm.tab == tab
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { vm.tab = tab },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // A pixel underline marks the current tab, like a cursor on an old menu.
                    Box(Modifier.width(24.dp).height(3.dp).background(if (on) accent else Color.Transparent))
                    Spacer(Modifier.height(8.dp))
                    PixelIcon(icon, if (on) accent else Muted, 20.dp)
                    Spacer(Modifier.height(6.dp))
                    Label(tab.name, color = if (on) TextInk else Muted)
                }
            }
        }
    }
}
