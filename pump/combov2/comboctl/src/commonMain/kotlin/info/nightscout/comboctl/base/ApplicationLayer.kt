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
 * Interface representing transport layer abstraction.
 */
interface TransportLayer {
    val incomingFrames: Flow<DisplayFrame>
    suspend fun sendPacket(packet: ByteArray): Boolean
}

/**
 * Sealed class representing screen glyphs and character variants used in parser.
 */
sealed class Glyph {
    open val isLarge: Boolean = false

    data class SmallCharacter(val char: Char) : Glyph()
    data class LargeCharacter(val char: Char) : Glyph() {
        override val isLarge: Boolean = true
    }
    object Unknown : Glyph()
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

    /**
     * Constructs and sends a packet to deliver a bolus.
     */
    fun createCMDDeliverBolusPacket(milliUnits: Int): ByteArray {
        val commandByte = 0x01.toByte()
        val arg1 = (milliUnits and 0xFF).toByte()
        val arg2 = ((milliUnits shr 8) and 0xFF).toByte()

        return byteArrayOf(commandByte, arg1, arg2)
    }

    /**
     * Constructs and sends a packet to set a Temporary Basal Rate (TBR).
     */
    fun createCMDSetTBRPercentPacket(percentage: Int, durationMinutes: Int): ByteArray {
        val commandByte = 0x02.toByte()
        val percByte = (percentage and 0xFF).toByte()
        val durLow = (durationMinutes and 0xFF).toByte()
        val durHigh = ((durationMinutes shr 8) and 0xFF).toByte()

        return byteArrayOf(commandByte, percByte, durLow, durHigh)
    }

    /**
     * Constructs and sends a packet to cancel any active TBR.
     */
    fun createCMDCancelTBRPacket(): ByteArray {
        return byteArrayOf(0x03.toByte())
    }

    /**
     * Deliver bolus to pump.
     */
    suspend fun deliverBolus(milliUnits: Int): Boolean {
        if (!_isPumpConnected.value) return false
        val packet = createCMDDeliverBolusPacket(milliUnits)
        return transportLayer.sendPacket(packet)
    }

    /**
     * Set temporary basal rate.
     */
    suspend fun setTBR(percentage: Int, durationMinutes: Int): Boolean {
        if (!_isPumpConnected.value) return false
        val packet = createCMDSetTBRPercentPacket(percentage, durationMinutes)
        return transportLayer.sendPacket(packet)
    }

    /**
     * Cancel active temporary basal rate.
     */
    suspend fun cancelTBR(): Boolean {
        if (!_isPumpConnected.value) return false
        val packet = createCMDCancelTBRPacket()
        return transportLayer.sendPacket(packet)
    }
}
