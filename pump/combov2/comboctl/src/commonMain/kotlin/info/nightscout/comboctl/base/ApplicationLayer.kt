package info.nightscout.comboctl.base

import info.nightscout.comboctl.parser.ParsedScreen
import info.nightscout.comboctl.parser.parseDisplayFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Large symbols used in display parser.
 */
enum class LargeSymbol {
    CLOCK,
    WARNING,
    CHECKMARK,
    CROSS,
    BATTERY
}

/**
 * Sealed class representing screen glyphs used in parser.
 */
sealed class Glyph {
    data class SmallCharacter(val char: Char) : Glyph()
    data class LargeCharacter(val char: Char) : Glyph()
    data class LargeSymbolGlyph(val symbol: LargeSymbol) : Glyph()
    object Unknown : Glyph()

    companion object {
        val LargeSymbol = info.nightscout.comboctl.base.LargeSymbol
    }
}

/**
 * Interface representing transport layer abstraction.
 */
interface TransportLayer {
    val incomingFrames: Flow<DisplayFrame>
    suspend fun sendPacket(packet: ByteArray): Boolean
}

/**
 * Application layer handling low-level packet construction, command processing,
 * and status updates for Accu-Chek Combo pump communication.
 */
class ApplicationLayer(
    private val transportLayer: TransportLayer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _currentParsedScreen = MutableStateFlow<ParsedScreen?>(null)
    val currentParsedScreen: StateFlow<ParsedScreen?> = _currentParsedScreen.asStateFlow()

    private val _isPumpConnected = MutableStateFlow(false)
    val isPumpConnected: StateFlow<Boolean> = _isPumpConnected.asStateFlow()

    init {
        scope.launch {
            transportLayer.incomingFrames.collect { frame ->
                handleIncomingFrame(frame)
            }
        }
    }

    private fun handleIncomingFrame(frame: DisplayFrame) {
        val parsed = parseDisplayFrame(frame)
        _currentParsedScreen.value = parsed
    }

    fun connect() {
        _isPumpConnected.value = true
    }

    fun disconnect() {
        _isPumpConnected.value = false
    }

    fun createCMDDeliverBolusPacket(milliUnits: Int): ByteArray {
        val commandByte = 0x01.toByte()
        val arg1 = (milliUnits and 0xFF).toByte()
        val arg2 = ((milliUnits shr 8) and 0xFF).toByte()
        return byteArrayOf(commandByte, arg1, arg2)
    }

    fun createCMDSetTBRPercentPacket(percentage: Int, durationMinutes: Int): ByteArray {
        val commandByte = 0x02.toByte()
        val percByte = (percentage and 0xFF).toByte()
        val durLow = (durationMinutes and 0xFF).toByte()
        val durHigh = ((durationMinutes shr 8) and 0xFF).toByte()
        return byteArrayOf(commandByte, percByte, durLow, durHigh)
    }

    fun createCMDCancelTBRPacket(): ByteArray {
        return byteArrayOf(0x03.toByte())
    }

    suspend fun deliverBolus(milliUnits: Int): Boolean {
        if (!_isPumpConnected.value) return false
        val packet = createCMDDeliverBolusPacket(milliUnits)
        return transportLayer.sendPacket(packet)
    }

    suspend fun setTBR(percentage: Int, durationMinutes: Int): Boolean {
        if (!_isPumpConnected.value) return false
        val packet = createCMDSetTBRPercentPacket(percentage, durationMinutes)
        return transportLayer.sendPacket(packet)
    }

    suspend fun cancelTBR(): Boolean {
        if (!_isPumpConnected.value) return false
        val packet = createCMDCancelTBRPacket()
        return transportLayer.sendPacket(packet)
    }
}
