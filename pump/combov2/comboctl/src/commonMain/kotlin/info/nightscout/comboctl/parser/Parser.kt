package info.nightscout.comboctl.parser

import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.Glyph
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Representation of a parsed screen frame.
 */
data class ParsedScreen(
    val rawFrame: DisplayFrame,
    val tokens: List<Token>,
    val timestamp: Instant = Instant.fromEpochMilliseconds(0)
)

/**
 * Pattern interface for matching glyphs.
 */
interface Pattern {
    val width: Int
    val height: Int
    val pixels: BooleanArray
    val numSetPixels: Int
}

/**
 * Empty stub map for glyph patterns.
 */
val glyphPatterns: Map<Glyph, Pattern> = emptyMap()

/**
 * Main parser function to convert a raw DisplayFrame into a ParsedScreen.
 */
fun parseDisplayFrame(frame: DisplayFrame): ParsedScreen {
    val tokens = findTokens(frame)
    return ParsedScreen(
        rawFrame = frame,
        tokens = tokens
    )
}

/**
 * Helper function for dateTime parsing with kotlinx.datetime 0.4.1 compatibility.
 */
fun parseDateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int = 0
): Instant {
    val localDateTime = LocalDateTime(year, month, day, hour, minute, second)
    return localDateTime.toInstant(TimeZone.UTC)
}
