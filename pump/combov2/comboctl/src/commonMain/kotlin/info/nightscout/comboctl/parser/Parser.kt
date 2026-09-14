package info.nightscout.comboctl.parser

import info.nightscout.comboctl.base.DisplayFrame
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Representation of a parsed screen frame.
 */
sealed class ParsedScreen {
    open val isBlinkedOut: Boolean = false

    object MainScreen : ParsedScreen()
    object QuickinfoMainScreen : ParsedScreen()
    object TemporaryBasalRateMenuScreen : ParsedScreen()
    object TemporaryBasalRatePercentageScreen : ParsedScreen()
    object TemporaryBasalRateDurationScreen : ParsedScreen()
    object MyDataMenuScreen : ParsedScreen()
    object MyDataBolusDataScreen : ParsedScreen()
    object MyDataErrorDataScreen : ParsedScreen()
    object MyDataDailyTotalsScreen : ParsedScreen()
    object MyDataTbrDataScreen : ParsedScreen()
    object BasalRate1ProgrammingMenuScreen : ParsedScreen()
    object BasalRateTotalScreen : ParsedScreen()
    object BasalRateFactorSettingScreen : ParsedScreen()
    object TimeAndDateSettingsMenuScreen : ParsedScreen()
    object TimeAndDateSettingsHourScreen : ParsedScreen()
    object TimeAndDateSettingsMinuteScreen : ParsedScreen()
    object TimeAndDateSettingsYearScreen : ParsedScreen()
    object TimeAndDateSettingsMonthScreen : ParsedScreen()
    object TimeAndDateSettingsDayScreen : ParsedScreen()
    object UnrecognizedScreen : ParsedScreen()
}

/**
 * Data class representing a parsed display frame.
 */
data class ParsedDisplayFrame(
    val rawFrame: DisplayFrame,
    val tokens: List<Token>,
    val timestamp: Instant = Instant.fromEpochMilliseconds(0),
    val parsedScreen: ParsedScreen = ParsedScreen.UnrecognizedScreen
)

/**
 * Main parser function to convert a raw DisplayFrame into a ParsedScreen.
 */
fun parseDisplayFrame(frame: DisplayFrame): ParsedScreen {
    val tokens = findTokens(frame)
    return ParsedScreen.UnrecognizedScreen
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
