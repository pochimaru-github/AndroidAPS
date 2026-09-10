package info.nightscout.comboctl.parser

import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.Glyph
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Result of parsing a screen.
 */
sealed class ParseResult<out T> {
    data class Success<out T>(val value: T, val nextIndex: Int) : ParseResult<T>()
    data class Failure(val message: String, val index: Int) : ParseResult<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure

    fun getOrNull(): T? = (this as? Success)?.value
}

/**
 * Context for parsing.
 */
data class ParseContext(
    val frame: DisplayFrame,
    val currentIndex: Int = 0
) {
    val currentGlyph: Glyph?
        get() = frame.glyphs.getOrNull(currentIndex)

    fun advance(count: Int = 1): ParseContext = copy(currentIndex = currentIndex + count)
}

/**
 * Base parser interface.
 */
interface Parser<out T> {
    fun parse(context: ParseContext): ParseResult<T>
}

// ---------------------------------------------------------------------------
// Basic Parsers
// ---------------------------------------------------------------------------

class SingleGlyphParser(private val expectedGlyph: Glyph) : Parser<Glyph> {
    override fun parse(context: ParseContext): ParseResult<Glyph> {
        val glyph = context.currentGlyph
            ?: return ParseResult.Failure("End of input reached, expected $expectedGlyph", context.currentIndex)

        return if (glyph == expectedGlyph) {
            ParseResult.Success(glyph, context.currentIndex + 1)
        } else {
            ParseResult.Failure("Expected $expectedGlyph, got $glyph", context.currentIndex)
        }
    }
}

class SingleGlyphTypeParser(private val predicate: (Glyph) -> Boolean, private val description: String) : Parser<Glyph> {
    override fun parse(context: ParseContext): ParseResult<Glyph> {
        val glyph = context.currentGlyph
            ?: return ParseResult.Failure("End of input, expected $description", context.currentIndex)

        return if (predicate(glyph)) {
            ParseResult.Success(glyph, context.currentIndex + 1)
        } else {
            ParseResult.Failure("Expected $description, got $glyph", context.currentIndex)
        }
    }
}

class StringParser(private val expectedString: String) : Parser<String> {
    override fun parse(context: ParseContext): ParseResult<String> {
        var current = context
        val sb = StringBuilder()

        for (char in expectedString) {
            val glyph = current.currentGlyph
                ?: return ParseResult.Failure("End of input while parsing string '$expectedString'", current.currentIndex)

            val charFromGlyph = glyphToChar(glyph)
            if (charFromGlyph == char) {
                sb.append(charFromGlyph)
                current = current.advance()
            } else {
                return ParseResult.Failure("Expected '$char', got '$charFromGlyph' in string '$expectedString'", current.currentIndex)
            }
        }

        return ParseResult.Success(sb.toString(), current.currentIndex)
    }
}

class IntegerParser(private val length: Int) : Parser<Int> {
    override fun parse(context: ParseContext): ParseResult<Int> {
        var current = context
        var result = 0

        for (i in 0 until length) {
            val glyph = current.currentGlyph
                ?: return ParseResult.Failure("End of input while parsing integer of length $length", current.currentIndex)

            val digit = glyphToDigit(glyph)
                ?: return ParseResult.Failure("Expected digit, got $glyph", current.currentIndex)

            result = result * 10 + digit
            current = current.advance()
        }

        return ParseResult.Success(result, current.currentIndex)
    }
}

class DecimalParser(private val integerLength: Int, private val fractionLength: Int) : Parser<Int> {
    override fun parse(context: ParseContext): ParseResult<Int> {
        val intParser = IntegerParser(integerLength)
        val intResult = intParser.parse(context)
        if (intResult is ParseResult.Failure) return intResult

        val nextCtx1 = context.copy(currentIndex = (intResult as ParseResult.Success).nextIndex)
        val dotGlyph = nextCtx1.currentGlyph
            ?: return ParseResult.Failure("End of input expecting decimal point", nextCtx1.currentIndex)

        if (!isDecimalPoint(dotGlyph)) {
            return ParseResult.Failure("Expected decimal point, got $dotGlyph", nextCtx1.currentIndex)
        }

        val nextCtx2 = nextCtx1.advance()
        val fracParser = IntegerParser(fractionLength)
        val fracResult = fracParser.parse(nextCtx2)
        if (fracResult is ParseResult.Failure) return fracResult

        val successFrac = fracResult as ParseResult.Success
        val totalFactor = powerOfTen(fractionLength)
        val totalValue = intResult.value * totalFactor + successFrac.value

        // Standardize to milli-units (factor 1000)
        val milliUnits = if (fractionLength == 1) {
            totalValue * 100
        } else if (fractionLength == 2) {
            totalValue * 10
        } else {
            totalValue
        }

        return ParseResult.Success(milliUnits, successFrac.nextIndex)
    }

    private fun powerOfTen(n: Int): Int {
        var res = 1
        repeat(n) { res *= 10 }
        return res
    }
}

// ---------------------------------------------------------------------------
// Combinators
// ---------------------------------------------------------------------------

class OptionalParser<T>(private val parser: Parser<T>) : Parser<T?> {
    override fun parse(context: ParseContext): ParseResult<T?> {
        return when (val res = parser.parse(context)) {
            is ParseResult.Success -> ParseResult.Success(res.value, res.nextIndex)
            is ParseResult.Failure -> ParseResult.Success(null, context.currentIndex)
        }
    }
}

class FirstSuccessParser<T>(private val parsers: List<Parser<T>>) : Parser<T> {
    override fun parse(context: ParseContext): ParseResult<T> {
        for (parser in parsers) {
            val res = parser.parse(context)
            if (res is ParseResult.Success) {
                return res
            }
        }
        return ParseResult.Failure("None of the alternative parsers succeeded", context.currentIndex)
    }
}

class SequenceParser<T>(private val parsers: List<Parser<T>>) : Parser<List<T>> {
    override fun parse(context: ParseContext): ParseResult<List<T>> {
        var current = context
        val list = mutableListOf<T>()

        for (parser in parsers) {
            val res = parser.parse(current)
            when (res) {
                is ParseResult.Success -> {
                    list.add(res.value)
                    current = current.copy(currentIndex = res.nextIndex)
                }
                is ParseResult.Failure -> return res
            }
        }

        return ParseResult.Success(list, current.currentIndex)
    }
}

// ---------------------------------------------------------------------------
// Date / Time / Duration Parsers
// ---------------------------------------------------------------------------

class DateParser : Parser<LocalDate> {
    override fun parse(context: ParseContext): ParseResult<LocalDate> {
        val yearParser = IntegerParser(4)
        val monthParser = IntegerParser(2)
        val dayParser = IntegerParser(2)

        val yearRes = yearParser.parse(context)
        if (yearRes is ParseResult.Failure) return yearRes

        var ctx = context.copy(currentIndex = (yearRes as ParseResult.Success).nextIndex)
        ctx = skipSeparator(ctx)

        val monthRes = monthParser.parse(ctx)
        if (monthRes is ParseResult.Failure) return monthRes

        ctx = ctx.copy(currentIndex = (monthRes as ParseResult.Success).nextIndex)
        ctx = skipSeparator(ctx)

        val dayRes = dayParser.parse(ctx)
        if (dayRes is ParseResult.Failure) return dayRes

        val finalIndex = (dayRes as ParseResult.Success).nextIndex

        return try {
            val year = yearRes.value
            val month = monthRes.value
            val day = dayRes.value
            val localDate = LocalDate(year, month, day)
            ParseResult.Success(localDate, finalIndex)
        } catch (e: Exception) {
            ParseResult.Failure("Invalid date: ${e.message}", context.currentIndex)
        }
    }

    private fun skipSeparator(context: ParseContext): ParseContext {
        val glyph = context.currentGlyph ?: return context
        return if (isSeparator(glyph)) context.advance() else context
    }
}

class TimeParser : Parser<LocalTime> {
    override fun parse(context: ParseContext): ParseResult<LocalTime> {
        val hourParser = IntegerParser(2)
        val minParser = IntegerParser(2)

        val hourRes = hourParser.parse(context)
        if (hourRes is ParseResult.Failure) return hourRes

        var ctx = context.copy(currentIndex = (hourRes as ParseResult.Success).nextIndex)
        ctx = skipSeparator(ctx)

        val minRes = minParser.parse(ctx)
        if (minRes is ParseResult.Failure) return minRes

        val finalIndex = (minRes as ParseResult.Success).nextIndex

        return try {
            val localTime = LocalTime(hourRes.value, minRes.value)
            ParseResult.Success(localTime, finalIndex)
        } catch (e: Exception) {
            ParseResult.Failure("Invalid time: ${e.message}", context.currentIndex)
        }
    }

    private fun skipSeparator(context: ParseContext): ParseContext {
        val glyph = context.currentGlyph ?: return context
        return if (isSeparator(glyph)) context.advance() else context
    }
}

class DurationParser : Parser<Int> {
    override fun parse(context: ParseContext): ParseResult<Int> {
        val timeParser = TimeParser()
        val res = timeParser.parse(context)
        if (res is ParseResult.Failure) return res

        val success = res as ParseResult.Success
        val totalMinutes = success.value.hour * 60 + success.value.minute
        return ParseResult.Success(totalMinutes, success.nextIndex)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun glyphToChar(glyph: Glyph): Char? {
    return when (glyph) {
        Glyph.DIGIT_0 -> '0'
        Glyph.DIGIT_1 -> '1'
        Glyph.DIGIT_2 -> '2'
        Glyph.DIGIT_3 -> '3'
        Glyph.DIGIT_4 -> '4'
        Glyph.DIGIT_5 -> '5'
        Glyph.DIGIT_6 -> '6'
        Glyph.DIGIT_7 -> '7'
        Glyph.DIGIT_8 -> '8'
        Glyph.DIGIT_9 -> '9'
        Glyph.CHAR_A -> 'A'
        Glyph.CHAR_B -> 'B'
        Glyph.CHAR_C -> 'C'
        Glyph.CHAR_D -> 'D'
        Glyph.CHAR_E -> 'E'
        Glyph.CHAR_F -> 'F'
        Glyph.CHAR_G -> 'G'
        Glyph.CHAR_H -> 'H'
        Glyph.CHAR_I -> 'I'
        Glyph.CHAR_J -> 'J'
        Glyph.CHAR_K -> 'K'
        Glyph.CHAR_L -> 'L'
        Glyph.CHAR_M -> 'M'
        Glyph.CHAR_N -> 'N'
        Glyph.CHAR_O -> 'O'
        Glyph.CHAR_P -> 'P'
        Glyph.CHAR_Q -> 'Q'
        Glyph.CHAR_R -> 'R'
        Glyph.CHAR_S -> 'S'
        Glyph.CHAR_T -> 'T'
        Glyph.CHAR_U -> 'U'
        Glyph.CHAR_V -> 'V'
        Glyph.CHAR_W -> 'W'
        Glyph.CHAR_X -> 'X'
        Glyph.CHAR_Y -> 'Y'
        Glyph.CHAR_Z -> 'Z'
        Glyph.SPACE -> ' '
        Glyph.MINUS -> '-'
        Glyph.PLUS -> '+'
        Glyph.SLASH -> '/'
        Glyph.COLON -> ':'
        Glyph.DOT -> '.'
        else -> null
    }
}

private fun glyphToDigit(glyph: Glyph): Int? {
    return when (glyph) {
        Glyph.DIGIT_0 -> 0
        Glyph.DIGIT_1 -> 1
        Glyph.DIGIT_2 -> 2
        Glyph.DIGIT_3 -> 3
        Glyph.DIGIT_4 -> 4
        Glyph.DIGIT_5 -> 5
        Glyph.DIGIT_6 -> 6
        Glyph.DIGIT_7 -> 7
        Glyph.DIGIT_8 -> 8
        Glyph.DIGIT_9 -> 9
        else -> null
    }
}

private fun isDecimalPoint(glyph: Glyph): Boolean {
    return glyph == Glyph.DOT || glyph == Glyph.COMMA
}

private fun isSeparator(glyph: Glyph): Boolean {
    return glyph == Glyph.SLASH || glyph == Glyph.DOT || glyph == Glyph.MINUS || glyph == Glyph.COLON
}

// ---------------------------------------------------------------------------
// Screen Parser Definitions & Models
// ---------------------------------------------------------------------------

sealed class ParsedScreen {
    data class MainScreen(val content: MainScreenContent) : ParsedScreen()
    data class AlertScreen(val content: AlertScreenContent) : ParsedScreen()
    data class MenuScreen(val title: String) : ParsedScreen()
    data class MyDataScreen(val title: String) : ParsedScreen()
    data class UnknownScreen(val frame: DisplayFrame) : ParsedScreen()
}

data class MainScreenContent(
    val isStopped: Boolean = false,
    val tbrPercent: Int? = null,
    val tbrRemainingMinutes: Int? = null,
    val activeBolusMilliUnits: Int? = null
)

data class AlertScreenContent(
    val alertCode: Int,
    val isWarning: Boolean
)

object ToplevelScreenParser {
    fun parseDisplayFrame(frame: DisplayFrame): ParsedScreen {
        val context = ParseContext(frame)

        // Try main screen parser
        NormalMainScreenParser().parse(context).getOrNull()?.let {
            return ParsedScreen.MainScreen(it)
        }

        // Try alert screen parser
        AlertScreenParser().parse(context).getOrNull()?.let {
            return ParsedScreen.AlertScreen(it)
        }

        return ParsedScreen.UnknownScreen(frame)
    }
}

class NormalMainScreenParser : Parser<MainScreenContent> {
    override fun parse(context: ParseContext): ParseResult<MainScreenContent> {
        // Simple heuristic parser for main screen
        return ParseResult.Success(MainScreenContent(), context.currentIndex)
    }
}

class AlertScreenParser : Parser<AlertScreenContent> {
    override fun parse(context: ParseContext): ParseResult<AlertScreenContent> {
        val codeParser = IntegerParser(2)
        val res = codeParser.parse(context)
        return if (res is ParseResult.Success) {
            ParseResult.Success(AlertScreenContent(res.value, isWarning = false), res.nextIndex)
        } else {
            ParseResult.Failure("Not an alert screen", context.currentIndex)
        }
    }
}

fun parseDisplayFrame(frame: DisplayFrame): ParsedScreen {
    return ToplevelScreenParser.parseDisplayFrame(frame)
}
