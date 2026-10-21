package com.bit128.bitsports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 128bit family tokens — same as 128bitfit/prototype/app-shell.html and index.html.
// Monochrome chrome; colour only ever means data (team crests, LIVE).
internal val Ground = Color(0xFF000000)
internal val Card = Color(0xFF0D0D0D)
internal val Card2 = Color(0xFF171717)
internal val Card3 = Color(0xFF212121)
internal val Line = Color(0xFF242424)
internal val Line2 = Color(0xFF3A3A3A)
internal val TextInk = Color(0xFFFFFFFF)
internal val Muted = Color(0xFF8C8C8C)
internal val Dim = Color(0xFF6B6B6B)
internal val Live = Color(0xFFFF4D6D)
internal val Warn = Color(0xFFFFD95E)

internal val Pixel = FontFamily(
    Font(R.font.silkscreen_regular, FontWeight.Normal),
    Font(R.font.silkscreen_bold, FontWeight.Bold),
)

internal val LocalUi = staticCompositionLocalOf { UiPrefs() }
internal val CardShape = RoundedCornerShape(12.dp)

@Composable
internal fun Heading(text: String, size: TextUnit = 13.sp, color: Color = TextInk) {
    val plain = LocalUi.current.plainHeadings
    Text(
        text, color = color, fontSize = size,
        fontFamily = if (plain) FontFamily.Default else Pixel,
        fontWeight = FontWeight.Bold,
        letterSpacing = if (plain) 0.sp else 1.5.sp,
    )
}

@Composable
internal fun Label(text: String, color: Color = Muted) {
    val plain = LocalUi.current.plainHeadings
    Text(
        text, color = color, fontSize = 11.sp,
        fontFamily = if (plain) FontFamily.Default else Pixel,
        fontWeight = if (plain) FontWeight.SemiBold else FontWeight.Normal,
        letterSpacing = if (plain) 0.sp else 1.sp,
    )
}

@Composable
internal fun SectionHead(title: String, note: String, noteColor: Color = Muted) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Heading(title)
        Spacer(Modifier.width(12.dp))
        Text(note, color = noteColor, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(Card).border(1.dp, Line, CardShape),
        content = content,
    )
}

/** The bitsports mark: a pixel puck in flight, on the same 11x6 grid as index.html. */
@Composable
internal fun PuckMark(color: Color, cell: Dp = 3.dp) {
    val cells = listOf(
        intArrayOf(6, 0, 4, 1), intArrayOf(5, 1, 6, 4), intArrayOf(6, 5, 4, 1),
        intArrayOf(0, 1, 3, 1), intArrayOf(1, 3, 3, 1),
    )
    Canvas(Modifier.size(cell * 11, cell * 6)) {
        val c = cell.toPx()
        cells.forEach { (x, y, w, h) ->
            drawRect(color, topLeft = Offset(x * c, y * c), size = Size(w * c, h * c))
        }
    }
}

@Composable
internal fun Crest(name: String, size: Dp) {
    val (c1, c2) = teamColors(name)
    Row(Modifier.size(size).clip(RoundedCornerShape(3.dp))) {
        Box(Modifier.weight(1f).fillMaxHeight().background(c1))
        Box(Modifier.weight(1f).fillMaxHeight().background(c2))
    }
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) Card3 else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (i == selected) TextInk else Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = Color(LocalUi.current.accent)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.Black, uncheckedTrackColor = Card3, uncheckedThumbColor = Muted, uncheckedBorderColor = Line2),
        )
    }
}
