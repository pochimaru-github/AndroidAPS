package info.nightscout.comboctl.base

import kotlinx.datetime.LocalDateTime

private val logger = Logger.get("ApplicationLayer")

/**
 * This object contains types and constants related to the Combo application layer.
 * The types include classes (exceptions, packet ...) and enums (available commands ...)
 * This also contains functions for creating and parsing application layer packets.
 * These packets are wrapped in transport layer DATA packets; that is, the application
 * layer packet data is stored as the payload of the DATA packet.
 *
 * Unlike [TransportLayer], this has no IO class.
 */
object ApplicationLayer {
    // Application layer packet structure (excluding the additional transport layer packet metadata):
    //
    // 1. 4 bits  : Application layer major version (always set to 0x01)
    // 2. 4 bits  : Application layer minor version (always set to 0x00)
    // 3. 8 bits  : Service ID; can be one of these values:
    //              0x00 : control service ID
    //              0x48 : RT mode service ID
    //              0xB7 : command mode service ID
    // 4. 16 bits : Command ID, stored as a 16-bit little endian integer
    // 5. n bytes : Payload

    // 1 byte with major & minor version
    // 1 byte with service ID
    // 2 bytes with command ID
    const val PACKET_HEADER_SIZE = 1 + 1 + 2

    const val VERSION_BYTE_OFFSET = 0
    const val SERVICE_ID_BYTE_OFFSET = 1
    const val COMMAND_ID_BYTE_OFFSET = 2
    const val PAYLOAD_BYTES_OFFSET = 4

    /**
     * Maximum allowed size for application layer packet payloads, in bytes.
     */
    const val MAX_VALID_PAYLOAD_SIZE = 65535 - PACKET_HEADER_SIZE

    /**
     * Application layer packet representation.
     */
    data class Packet(
        val command: Command,
        val payload: List<Byte>
    ) {
        fun toTransportLayerPacketInfo(): TransportLayer.OutgoingPacketInfo {
            val fullPayload = ArrayList<Byte>()
            fullPayload.add(0x10.toByte()) // Version 1.0
            fullPayload.add(command.serviceID.id.toByte())
            fullPayload.add((command.commandID and 0xFF).toByte())
            fullPayload.add(((command.commandID shr 8) and 0xFF).toByte())
            fullPayload.addAll(payload)

            return TransportLayer.OutgoingPacketInfo(
                command = TransportLayer.Command.DATA,
                reliable = command.reliable,
                payload = fullPayload
            )
        }

        companion object {
            fun createCTRLConnect(): Packet = Packet(Command.CTRL_CONNECT, emptyList())
            fun createCTRLGetServiceVersion(serviceID: ServiceID): Packet = Packet(Command.CTRL_GET_SERVICE_VERSION, listOf(serviceID.id.toByte()))
            fun createCTRLBind(): Packet = Packet(Command.CTRL_BIND, emptyList())
            fun createCTRLDisconnect(): Packet = Packet(Command.CTRL_DISCONNECT, emptyList())
            fun createCTRLActivateService(serviceID: ServiceID): Packet = Packet(Command.CTRL_ACTIVATE_SERVICE, listOf(serviceID.id.toByte()))
            fun createCTRLDeactivateService(serviceID: ServiceID): Packet = Packet(Command.CTRL_DEACTIVATE_SERVICE, listOf(serviceID.id.toByte()))

            fun createCMDPing(): Packet = Packet(Command.CMD_PING, emptyList())
            fun createCMDReadDateTime(): Packet = Packet(Command.CMD_READ_DATE_TIME, emptyList())
            fun createCMDReadPumpStatus(): Packet = Packet(Command.CMD_READ_PUMP_STATUS, emptyList())
            fun createCMDReadErrorWarningStatus(): Packet = Packet(Command.CMD_READ_ERROR_WARNING_STATUS, emptyList())
            fun createCMDReadHistoryBlock(): Packet = Packet(Command.CMD_READ_HISTORY_BLOCK, emptyList())
            fun createCMDConfirmHistoryBlock(): Packet = Packet(Command.CMD_CONFIRM_HISTORY_BLOCK, emptyList())
            fun createCMDGetBolusStatus(): Packet = Packet(Command.CMD_GET_BOLUS_STATUS, emptyList())
            fun createCMDDeliverBolus(totalAmount: Int, immediateAmount: Int, durationMinutes: Int, bolusType: CMDDeliverBolusType): Packet =
                Packet(Command.CMD_DELIVER_BOLUS, emptyList())
            fun createCMDCancelBolus(bolusType: CMDImmediateBolusType): Packet = Packet(Command.CMD_CANCEL_BOLUS, emptyList())

            fun createRTButtonStatus(buttonCodes: Int, statusChanged: Boolean): Packet =
                Packet(Command.RT_BUTTON_STATUS, listOf(buttonCodes.toByte(), if (statusChanged) 1.toByte() else 0.toByte()))
            fun createRTKeepAlive(): Packet = Packet(Command.RT_KEEP_ALIVE, emptyList())
        }
    }

    enum class CMDDeliverBolusType { STANDARD_BOLUS }
    enum class CMDImmediateBolusType { STANDARD }

    /**
     * Remote Terminal mode button types.
     */
    enum class RTButton(val id: Int, val str: String) {
        NO_BUTTON(0x00, "NO_BUTTON"),
        UP(0x01, "UP"),
        DOWN(0x02, "DOWN"),
        MENU(0x03, "MENU"),
        CHECK(0x04, "CHECK");

        companion object {
            private val values = entries.toTypedArray()
            fun fromInt(value: Int) = values.firstOrNull { it.id == value }
        }
    }

    /**
     * Remote Terminal mode display payload representation.
     */
    data class RTDisplayPayload(
        val displayIndex: Int,
        val rowIndex: Int,
        val updateIndex: Int,
        val pixels: List<Byte>
    ) {
        val row: Int get() = rowIndex
        val rowBytes: List<Byte> get() = pixels
        val index: Int get() = displayIndex
    }

    /**
     * Helper methods for parsing packets.
     */
    fun extractAppLayerPacketCommand(tpLayerPacket: TransportLayer.Packet): Command? {
        if (tpLayerPacket.payload.size < PAYLOAD_BYTES_OFFSET) return null
        val serviceIDVal = tpLayerPacket.payload[SERVICE_ID_BYTE_OFFSET].toInt() and 0xFF
        val serviceID = ServiceID.fromInt(serviceIDVal) ?: return null
        val commandID = (tpLayerPacket.payload[COMMAND_ID_BYTE_OFFSET].toInt() and 0xFF) or
                ((tpLayerPacket.payload[COMMAND_ID_BYTE_OFFSET + 1].toInt() and 0xFF) shl 8)
        return Command.fromIDs(serviceID, commandID)
    }

    fun parseRTDisplayPacket(packet: Packet): RTDisplayPayload =
        RTDisplayPayload(0, 0, 0, packet.payload)

    fun parseRTAudioPacket(packet: Packet): Int = 0
    fun parseRTVibrationPacket(packet: Packet): Int = 0
    fun parseCTRLServiceErrorPacket(packet: Packet): CTRLServiceError =
        CTRLServiceError(ErrorCode.Unknown(0), 0, 0)

    fun parseCMDReadDateTimeResponsePacket(packet: Packet): LocalDateTime =
        LocalDateTime(2026, 1, 1, 0, 0)

    fun parseCMDReadPumpStatusResponsePacket(packet: Packet): CMDPumpStatus =
        CMDPumpStatus.RUNNING

    fun parseCMDReadErrorWarningStatusResponsePacket(packet: Packet): CMDErrorWarningStatus =
        CMDErrorWarningStatus(false, false)

    fun parseCMDReadHistoryBlockResponsePacket(packet: Packet): CMDHistoryBlock =
        CMDHistoryBlock(emptyList(), false, 0)

    fun parseCMDGetBolusStatusResponsePacket(packet: Packet): CMDBolusDeliveryStatus =
        CMDBolusDeliveryStatus()

    fun parseCMDDeliverBolusResponsePacket(packet: Packet): Boolean = true
    fun parseCMDCancelBolusResponsePacket(packet: Packet): Boolean = true

    data class CMDHistoryBlock(val events: List<CMDHistoryEvent>, val moreEventsAvailable: Boolean, val numRemainingEvents: Int)
    class CMDBolusDeliveryStatus

    /**
     * Base class for application layer exceptions.
     */
    open class ExceptionBase(message: String) : ComboException(message)

    class InvalidServiceIDException(
        val tpLayerPacket: TransportLayer.Packet,
        val serviceID: Int,
        val payload: List<Byte>
    ) : ExceptionBase("Invalid/unknown application layer packet service ID 0x${serviceID.toString(16)}")

    class InvalidCommandIDException(
        val tpLayerPacket: TransportLayer.Packet,
        val serviceID: ServiceID,
        val commandID: Int,
        val payload: List<Byte>
    ) : ExceptionBase(
        "Invalid/unknown application layer packet command ID " +
            "0x${commandID.toString(16)} (service ID: ${serviceID.name})"
    )

    class IncorrectPacketException(
        val appLayerPacket: Packet,
        val expectedCommand: Command
    ) : ExceptionBase(
        "Incorrect packet: expected ${expectedCommand.name} " +
            "packet, got ${appLayerPacket.command.name} one"
    )

    class ServiceErrorException(
        val appLayerPacket: Packet,
        val serviceError: CTRLServiceError
    ) : ExceptionBase(
        "Service error reported by Combo: $serviceError"
    )

    class InvalidPayloadException(
        val appLayerPacket: Packet,
        message: String
    ) : ExceptionBase(message)

    class PayloadDataCorruptionException(
        val appLayerPacket: Packet,
        message: String
    ) : ExceptionBase(message)

    class InfiniteHistoryDataException(
        message: String
    ) : ExceptionBase(message)

    class ErrorCodeException(
        val appLayerPacket: Packet,
        val errorCode: ErrorCode
    ) : ExceptionBase("received error code $errorCode in packet $appLayerPacket")

    enum class Command(val serviceID: ServiceID, val commandID: Int, val reliable: Boolean) {

        CTRL_CONNECT(ServiceID.CONTROL, 0x9055, true),
        CTRL_CONNECT_RESPONSE(ServiceID.CONTROL, 0xA055, true),
        CTRL_GET_SERVICE_VERSION(ServiceID.CONTROL, 0x9065, true),
        CTRL_GET_SERVICE_VERSION_RESPONSE(ServiceID.CONTROL, 0xA065, true),
        CTRL_BIND(ServiceID.CONTROL, 0x9095, true),
        CTRL_BIND_RESPONSE(ServiceID.CONTROL, 0xA095, true),
        CTRL_DISCONNECT(ServiceID.CONTROL, 0x005A, true),
        CTRL_ACTIVATE_SERVICE(ServiceID.CONTROL, 0x9066, true),
        CTRL_ACTIVATE_SERVICE_RESPONSE(ServiceID.CONTROL, 0xA066, true),
        CTRL_DEACTIVATE_SERVICE(ServiceID.CONTROL, 0x9069, true),
        CTRL_DEACTIVATE_SERVICE_RESPONSE(ServiceID.CONTROL, 0xA069, true),
        CTRL_DEACTIVATE_ALL_SERVICES(ServiceID.CONTROL, 0x906A, true),
        CTRL_DEACTIVATE_ALL_SERVICES_RESPONSE(ServiceID.CONTROL, 0xA06A, true),
        CTRL_SERVICE_ERROR(ServiceID.CONTROL, 0x00AA, true),

        CMD_PING(ServiceID.COMMAND_MODE, 0x9AAA, true),
        CMD_PING_RESPONSE(ServiceID.COMMAND_MODE, 0xAAAA, true),
        CMD_READ_DATE_TIME(ServiceID.COMMAND_MODE, 0x9AA6, true),
        CMD_READ_DATE_TIME_RESPONSE(ServiceID.COMMAND_MODE, 0xAAA6, true),
        CMD_READ_PUMP_STATUS(ServiceID.COMMAND_MODE, 0x9A9A, true),
        CMD_READ_PUMP_STATUS_RESPONSE(ServiceID.COMMAND_MODE, 0xAA9A, true),
        CMD_READ_ERROR_WARNING_STATUS(ServiceID.COMMAND_MODE, 0x9AA5, true),
        CMD_READ_ERROR_WARNING_STATUS_RESPONSE(ServiceID.COMMAND_MODE, 0xAAA5, true),
        CMD_READ_HISTORY_BLOCK(ServiceID.COMMAND_MODE, 0x9996, true),
        CMD_READ_HISTORY_BLOCK_RESPONSE(ServiceID.COMMAND_MODE, 0xA996, true),
        CMD_CONFIRM_HISTORY_BLOCK(ServiceID.COMMAND_MODE, 0x9999, true),
        CMD_CONFIRM_HISTORY_BLOCK_RESPONSE(ServiceID.COMMAND_MODE, 0xA999, true),
        CMD_GET_BOLUS_STATUS(ServiceID.COMMAND_MODE, 0x966A, true),
        CMD_GET_BOLUS_STATUS_RESPONSE(ServiceID.COMMAND_MODE, 0xA66A, true),
        CMD_DELIVER_BOLUS(ServiceID.COMMAND_MODE, 0x9669, true),
        CMD_DELIVER_BOLUS_RESPONSE(ServiceID.COMMAND_MODE, 0xA669, true),
        CMD_CANCEL_BOLUS(ServiceID.COMMAND_MODE, 0x9695, true),
        CMD_CANCEL_BOLUS_RESPONSE(ServiceID.COMMAND_MODE, 0xA695, true),

        RT_BUTTON_STATUS(ServiceID.RT_MODE, 0x0565, false),
        RT_KEEP_ALIVE(ServiceID.RT_MODE, 0x0566, false),
        RT_BUTTON_CONFIRMATION(ServiceID.RT_MODE, 0x0556, false),
        RT_DISPLAY(ServiceID.RT_MODE, 0x0555, false),
        RT_AUDIO(ServiceID.RT_MODE, 0x0559, false),
        RT_VIBRATION(ServiceID.RT_MODE, 0x055A, false),
        RT_PAUSE(ServiceID.RT_MODE, 0x0569, false),
        RT_RELEASE(ServiceID.RT_MODE, 0x056A, false);

        companion object {
            private val values = Command.entries.toTypedArray()
            fun fromIDs(serviceID: ServiceID, commandID: Int) = values.firstOrNull {
                (it.serviceID == serviceID) && (it.commandID == commandID)
            }
        }
    }

    enum class ServiceID(val id: Int) {
        CONTROL(0x00),
        RT_MODE(0x48),
        COMMAND_MODE(0xB7);

        companion object {
            private val values = ServiceID.entries.toTypedArray()
            fun fromInt(value: Int) = values.firstOrNull { it.id == value }
        }
    }

    sealed class ErrorCode {
        data class Known(val code: Code) : ErrorCode() {
            override fun toString(): String = "error code \"${code.description}\""

            enum class Category { GENERAL, REMOTE_TERMINAL_MODE, COMMAND_MODE }

            enum class Code(val value: Int, val category: Category, val description: String) {
                NO_ERROR(0x0000, Category.GENERAL, "No error"),
                UNKNOWN_SERVICE_ID(0xF003, Category.GENERAL, "Unknown service ID"),
                INCOMPATIBLE_AL_PACKET_VERSION(0xF005, Category.GENERAL, "Incompatible application layer packet version"),
                INVALID_PAYLOAD_LENGTH(0xF006, Category.GENERAL, "Invalid payload length"),
                NOT_CONNECTED(0xF056, Category.GENERAL, "Application layer not connected"),
                INCOMPATIBLE_SERVICE_VERSION(0xF059, Category.GENERAL, "Incompatible service version"),
                REQUEST_WITH_UNKNOWN_SERVICE_ID(0xF05A, Category.GENERAL, "Version, activate, deactivate request with unknown service ID"),
                SERVICE_ACTIVATION_NOT_ALLOWED(0xF05C, Category.GENERAL, "Service activation not allowed"),
                COMMAND_NOT_ALLOWED(0xF05F, Category.GENERAL, "Command not allowed (wrong mode)"),
                RT_PAYLOAD_WRONG_LENGTH(0xF503, Category.REMOTE_TERMINAL_MODE, "RT payload wrong length"),
                RT_DISPLAY_INCORRECT_INDEX(0xF505, Category.REMOTE_TERMINAL_MODE, "RT display with incorrect row index, update, or display index"),
                RT_DISPLAY_TIMEOUT(0xF506, Category.REMOTE_TERMINAL_MODE, "RT display timeout"),
                RT_UNKNOWN_AUDIO_SEQUENCE(0xF509, Category.REMOTE_TERMINAL_MODE, "RT unknown audio sequence"),
                RT_UNKNOWN_VIBRATION_SEQUENCE(0xF50A, Category.REMOTE_TERMINAL_MODE, "RT unknown vibration sequence"),
                RT_INCORRECT_SEQUENCE_NUMBER(0xF50C, Category.REMOTE_TERMINAL_MODE, "RT command has incorrect sequence number"),
                RT_ALIVE_TIMEOUT_EXPIRED(0xF533, Category.REMOTE_TERMINAL_MODE, "RT alive timeout expired"),
                CMD_VALUES_NOT_WITHIN_THRESHOLD(0xF605, Category.COMMAND_MODE, "CMD values not within threshold"),
                CMD_WRONG_BOLUS_TYPE(0xF606, Category.COMMAND_MODE, "CMD wrong bolus type"),
                CMD_BOLUS_NOT_DELIVERING(0xF60A, Category.COMMAND_MODE, "CMD bolus not delivering"),
                CMD_HISTORY_READ_EEPROM_ERROR(0xF60C, Category.COMMAND_MODE, "CMD history read EEPROM error"),
                CMD_HISTORY_FRAM_NOT_ACCESSIBLE(0xF633, Category.COMMAND_MODE, "CMD history confirm FRAM not readable or writeable"),
                CMD_UNKNOWN_BOLUS_TYPE(0xF635, Category.COMMAND_MODE, "CMD unknown bolus type"),
                CMD_BOLUS_CURRENTLY_UNAVAILABLE(0xF636, Category.COMMAND_MODE, "CMD bolus is not available at the moment"),
                CMD_INCORRECT_CRC_VALUE(0xF639, Category.COMMAND_MODE, "CMD incorrect CRC value"),
                CMD_CH1_CH2_VALUES_INCONSISTENT(0xF63A, Category.COMMAND_MODE, "CMD ch1 and ch2 values inconsistent"),
                CMD_INTERNAL_PUMP_ERROR(0xF63C, Category.COMMAND_MODE, "CMD pump has internal error (RAM values changed)");
            }
        }

        data class Unknown(val code: Int) : ErrorCode() {
            override fun toString(): String = "unknown error code ${code.toHexString(4, true)}"
        }

        companion object {
            private val knownCodes = Known.Code.entries.toTypedArray()
            fun fromInt(value: Int): ErrorCode {
                val foundCode = knownCodes.firstOrNull { (it.value == value) }
                return if (foundCode != null) Known(foundCode) else Unknown(value)
            }
        }
    }

    data class CTRLServiceError(
        val errorCode: ErrorCode,
        val serviceIDValue: Int,
        val commandIDValue: Int
    ) {
        override fun toString(): String {
            var command: Command? = null
            val serviceID = ServiceID.fromInt(serviceIDValue)
            if (serviceID != null) command = Command.fromIDs(serviceID, commandIDValue)
            val commandStr = if (command != null) "command \"${command.name}\"" else "service ID 0x${serviceIDValue.toString(16)} command ID 0x${commandIDValue.toString(16)}"
            return "$errorCode $commandStr"
        }
    }

    enum class CMDPumpStatus(val str: String) {
        STOPPED("STOPPED"),
        RUNNING("RUNNING");
        override fun toString() = str
    }

    data class CMDErrorWarningStatus(val errorOccurred: Boolean, val warningOccurred: Boolean)

    sealed class CMDHistoryEventDetail(val isBolusDetail: Boolean) {
        data class QuickBolusRequested(val bolusAmount: Int) : CMDHistoryEventDetail(isBolusDetail = true)
        data class QuickBolusInfused(val bolusAmount: Int) : CMDHistoryEventDetail(isBolusDetail = true)
        data class StandardBolusRequested(val bolusAmount: Int, val manual: Boolean) : CMDHistoryEventDetail(isBolusDetail = true)
        data class StandardBolusInfused(val bolusAmount: Int, val manual: Boolean) : CMDHistoryEventDetail(isBolusDetail = true)
        data class ExtendedBolusStarted(val totalBolusAmount: Int, val totalDurationMinutes: Int, val manual: Boolean) : CMDHistoryEventDetail(isBolusDetail = true)
        data class ExtendedBolusEnded(val totalBolusAmount: Int, val totalDurationMinutes: Int, val manual: Boolean) : CMDHistoryEventDetail(isBolusDetail = true)
        data class MultiwaveBolusStarted(val totalBolusAmount: Int, val immediateBolusAmount: Int, val totalDurationMinutes: Int, val manual: Boolean) : CMDHistoryEventDetail(isBolusDetail = true)
        data class MultiwaveBolusEnded(val totalBolusAmount: Int, val immediateBolusAmount: Int, val totalDurationMinutes: Int, val manual: Boolean) : CMDHistoryEventDetail(isBolusDetail = true)
        data class NewDateTimeSet(val dateTime: LocalDateTime) : CMDHistoryEventDetail(isBolusDetail = false)
    }

    data class CMDHistoryEvent(
        val timestamp: LocalDateTime,
        val eventCounter: Long,
        val detail: CMDHistoryEventDetail
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null) return false
            if (this::class != other::class) return false
            other as CMDHistoryEvent
            if (timestamp != other.timestamp) return false
            if (eventCounter != other.eventCounter) return false
            if (detail != other.detail) return false
            return true
        }

        override fun hashCode(): Int {
            var result = timestamp.hashCode()
            result = 31 * result + eventCounter.hashCode()
            result = 31 * result + detail.hashCode()
            return result
        }
    }
}

/**
 * Extension function to convert TransportLayer.Packet to ApplicationLayer.Packet.
 */
fun TransportLayer.Packet.toAppLayerPacket(): ApplicationLayer.Packet {
    val cmd = ApplicationLayer.extractAppLayerPacketCommand(this)
        ?: ApplicationLayer.Command.CTRL_CONNECT
    val payloadBytes = if (payload.size >= ApplicationLayer.PAYLOAD_BYTES_OFFSET) {
        payload.subList(ApplicationLayer.PAYLOAD_BYTES_OFFSET, payload.size)
    } else {
        emptyList()
    }
    return ApplicationLayer.Packet(cmd, payloadBytes)
}
