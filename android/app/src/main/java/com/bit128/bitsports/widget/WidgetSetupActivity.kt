package com.bit128.bitsports.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.*
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.nhl.ShotMark

/** Per-widget choices: which team, and for the rink widget which heat and what kind of board. */
class WidgetSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without saving means "don't add the widget".
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val isRink = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className == RinkWidget::class.java.name
        val store = WidgetPrefs(this)
        val start = store.load(id)
        val followed = AlertScheduler.followedAbbrevs(this).toList()
        val ui = PrefsStore(this).load()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ground, surface = Card, primary = Color(ui.accent))) {
                CompositionLocalProvider(LocalUi provides ui) {
                    Surface(color = Ground, modifier = Modifier.fillMaxSize()) {
                        SetupScreen(isRink, start, followed) { cfg ->
                            store.save(id, cfg)
                            Widgets.refresh(applicationContext)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(isRink: Boolean, start: WidgetConfig, followed: List<String>, onSave: (WidgetConfig) -> Unit) {
    var cfg by remember { mutableStateOf(start) }
    val accent = Color(LocalUi.current.accent)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(28.dp))
            Heading(if (isRink) "SCORE + HEAT MAP" else "TEAM SCORE", size = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isRink) "The score over an LED rink. Each team's shots light up the end under its name; goals light white."
                else "Your team's game today: the score, the clock and the puck drop.",
                color = Muted, fontSize = 15.sp, lineHeight = 21.sp,
            )

            if (isRink) {
                Spacer(Modifier.height(24.dp))
                BoardPreview(cfg, followed)
                Spacer(Modifier.height(24.dp))
                Text("Heat", color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Segmented(listOf("Both teams", "Just mine"), if (cfg.heat == "mine") 1 else 0) { cfg = cfg.copy(heat = if (it == 1) "mine" else "both") }
                Spacer(Modifier.height(20.dp))
                Text("Board", color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Segmented(listOf("LED", "Solid"), if (cfg.board == "solid") 1 else 0) { cfg = cfg.copy(board = if (it == 1) "solid" else "led") }
            }

            Spacer(Modifier.height(28.dp))
            SectionHead("TEAM", if (followed.isEmpty()) "Any NHL team" else "Yours first")
            CardBox {
                TeamRow("My team", followed.firstOrNull()?.let { "${NhlTeams.byAbbrev(it)?.fullName} · follows your list" } ?: "Your first followed NHL team",
                    followed.firstOrNull(), cfg.team == null, accent) { cfg = cfg.copy(team = null) }
                val ordered = followed.mapNotNull { NhlTeams.byAbbrev(it) } + NhlTeams.all.filter { it.abbrev !in followed }.sortedBy { it.city }
                ordered.forEach { t ->
                    HorizontalDivider(color = Line)
                    TeamRow(t.fullName, if (t.abbrev in followed) "Following" else null, t.abbrev, cfg.team == t.abbrev, accent) {
                        cfg = cfg.copy(team = t.abbrev)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.fillMaxWidth().background(Ground).padding(16.dp)) {
            Button(
                onClick = { onSave(cfg) },
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Heading("SAVE WIDGET", color = Color.Black) }
        }
    }
}

@Composable
private fun TeamRow(title: String, note: String?, abbrev: String?, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crest(abbrev?.let { NhlTeams.byAbbrev(it)?.fullName } ?: "", 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextInk, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            if (note != null) Text(note, color = Muted, fontSize = 12.sp)
        }
        if (selected) Label("✓", color = accent)
    }
}

/** The board as it will look, lit with a sample game so the heat and board choices can be seen. */
@Composable
private fun BoardPreview(cfg: WidgetConfig, followed: List<String>) {
    val mine = cfg.team ?: followed.firstOrNull() ?: "EDM"
    val other = if (mine == "CGY") "EDM" else "CGY"
    val bmp = remember(cfg, mine) {
        val sample = sampleShots(mine, other)
        RinkArt.render(
            RinkHeat.grid(sample, awayAbbrev = mine, onlyTeam = if (cfg.heat == "mine") mine else null),
            NhlTeams.byAbbrev(mine), NhlTeams.byAbbrev(other), cfg.board == "solid",
        )
    }
    Column {
        Image(
            bmp.asImageBitmap(), contentDescription = "Preview of the heat map",
            contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text("Preview with a sample game · $mine left, $other right", color = Muted, fontSize = 12.sp)
    }
}

/** A plausible spread of shots for the preview: most from the slot, some from the points. */
private fun sampleShots(mine: String, other: String): List<ShotMark> {
    val spots = listOf(
        80 to 0, 84 to 6, 78 to -8, 70 to 18, 72 to -20, 60 to 0, 55 to 30, 52 to -32, 85 to -3, 66 to 10, 81 to 2, 74 to -4,
        82 to -12, 76 to 14, 63 to -14, 58 to 22, 86 to 10, 79 to 5, 68 to -2, 50 to 8, 83 to -6, 71 to 26, 77 to -24, 64 to 4,
    )
    return spots.mapIndexed { i, (x, y) -> ShotMark(mine, x, y, isGoal = i == 0 || i == 8, period = 1, shooter = null) } +
        spots.take(16).mapIndexed { i, (x, y) -> ShotMark(other, x, -y, isGoal = i == 2, period = 1, shooter = null) }
}
