package com.bit128.bitsports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.Muted
import com.bit128.bitsports.fantasy.FantasyNight
import kotlinx.coroutines.delay

/**
 * The Fantasy Report, full screen: for each list, the title card, then every player who played
 * with his points rolling up (MVP in gold with a crown, a rain cloud for a zero), then the total.
 * Tap to skip ahead; it closes itself at the end.
 */
@Composable
fun FantasyReportMoment(nights: List<FantasyNight.ListNight>, onDone: () -> Unit) {
    // Every card with how many frames it plays for.
    val cards = remember(nights) {
        val date = FantasyReportLeds.dateLabel(java.time.LocalDate.now())
        nights.flatMap { n ->
            val mvp = n.mvp
            listOf<Pair<Int, (Int) -> LedGrid>>(14 to { _ -> FantasyReportLeds.title(n.list.name, date) }) +
                n.rows.filter { it.line != null }.map { r -> (FantasyReportLeds.COUNT + 10) to { f: Int -> FantasyReportLeds.player(r, r == mvp, f) } } +
                listOf((FantasyReportLeds.COUNT + 18) to { f: Int -> FantasyReportLeds.total(n, f) })
        }
    }
    var card by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(card) {
        frame = 0
        while (frame < cards[card].first) { delay(110); frame++ }
        if (card < cards.lastIndex) card++ else { delay(1_200); onDone() }
    }
    val grid = remember(card, frame) { cards[card].second(frame) }
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable { if (card < cards.lastIndex) card++ else onDone() }.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        LedBoard(grid)
        Text("Tap to skip", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
