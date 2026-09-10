package info.nightscout.comboctl.parser

import info.nightscout.comboctl.base.DISPLAY_FRAME_HEIGHT
import info.nightscout.comboctl.base.DISPLAY_FRAME_WIDTH
import info.nightscout.comboctl.base.DisplayFrame
import kotlin.math.sign

/**
 * Structure containing details about a match discovered in a [DisplayFrame].
 *
 * The match is referred to as a "token", similar to lexical tokens
 * in lexical analyzers.
 *
 * This is the result of a pattern search in a display frame.
 *
 * @property pattern The pattern for which a match was found.
 * @property glyph [Glyph] associated with the pattern.
 * @property x X-coordinate of the location of the token in the display frame.
 * @property y Y-coordinate of the location of the token in the display frame.
 */
data class Token(
    val pattern: Pattern,
    val glyph: Glyph,
    val x: Int,
    val y: Int
)

/**
 * List of tokens found in the display frame by the [findTokens] function.
 */
typealias Tokens = List<Token>

/**
 * Checks if the region at the given coordinates matches the given pattern.
 *
 * This is used for finding tokens in a frame.
 *
 * @param displayFrame [DisplayFrame] that contains the region to match the pattern against.
 * @param pattern Pattern to match with the region in the display frame.
 * @param x X-coordinate of the region in the display frame.
 * @param y Y-coordinate of the region in the display frame.
 * @return true if the region matches the pattern. false in case of mismatch
 *         or if the coordinates would place the pattern (partially) outside
 *         of the bounds of the display frame.
 */
fun checkIfPatternMatchesAt(displayFrame: DisplayFrame, pattern: Pattern, x: Int, y: Int): Boolean {
    if ((x < 0) || (y < 0) ||
        ((x + pattern.width) > DISPLAY_FRAME_WIDTH) ||
        ((y + pattern.height) > DISPLAY_FRAME_HEIGHT))
        return false

    for (py in 0 until pattern.height) {
        for (px in 0 until pattern.width) {
            val patternPixel = pattern.pixels[px + py * pattern.width]
            val framePixel = displayFrame.getPixelAt(
                x + px,
                y + py
            )

            if (patternPixel != framePixel)
                return false
        }
    }

    return true
}

/**
 * Look for regions in the display frame that can be turned into tokens.
 *
 * @param displayFrame [DisplayFrame] to search for tokens.
 * @return Tokens found in this frame.
 */
fun findTokens(displayFrame: DisplayFrame): Tokens {
    val tokens = mutableListOf<Token>()

    var y = 0

    while (y < DISPLAY_FRAME_HEIGHT) {
        var x = 0

        while (x < DISPLAY_FRAME_WIDTH) {
            for ((glyph, pattern) in glyphPatterns) {
                if (checkIfPatternMatchesAt(displayFrame, pattern, x, y)) {
                    tokens.add(Token(pattern, glyph, x, y))
                    x += pattern.width - 1
                    break
                }
            }

            x++
        }

        y++
    }

    val tokensToRemove = mutableSetOf<Token>()

    for (tokenB in tokens) {
        for (tokenA in tokens) {
            val tokenAx1 = tokenA.x
            val tokenAy1 = tokenA.y
            val tokenAx2 = tokenA.x + tokenA.pattern.width - 1
            val tokenAy2 = tokenA.y + tokenA.pattern.height - 1

            val tokenBx1 = tokenB.x
            val tokenBy1 = tokenB.y
            val tokenBx2 = tokenB.x + tokenB.pattern.width - 1
            val tokenBy2 = tokenB.y + tokenB.pattern.height - 1

            val xd1 = (tokenBx2 - tokenAx1)
            val xd2 = (tokenBx1 - tokenAx2)
            val yd1 = (tokenBy2 - tokenAy1)
            val yd2 = (tokenBy1 - tokenAy2)

            val tokensOverlap = (xd1.sign != xd2.sign) && (yd1.sign != yd2.sign)

            if (tokensOverlap) {
                if (tokenA.glyph.isLarge && !tokenB.glyph.isLarge)
                    tokensToRemove.add(tokenB)
                else if (!tokenA.glyph.isLarge && tokenB.glyph.isLarge)
                    tokensToRemove.add(tokenA)
                else if (tokenA.pattern.numSetPixels > tokenB.pattern.numSetPixels)
                    tokensToRemove.add(tokenB)
                else if (tokenA.pattern.numSetPixels < tokenB.pattern.numSetPixels)
                    tokensToRemove.add(tokenA)
            }
        }
    }

    if (tokensToRemove.isNotEmpty())
        tokens.removeAll(tokensToRemove)

    return tokens
}
