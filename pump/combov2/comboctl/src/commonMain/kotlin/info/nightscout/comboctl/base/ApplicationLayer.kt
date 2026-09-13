package info.nightscout.comboctl.base

import info.nightscout.comboctl.base.ApplicationLayer.createCMDDeliverBolusPacket
import info.nightscout.comboctl.base.ApplicationLayer.createCMDReadHistoryBlockPacket
import info.nightscout.comboctl.base.ApplicationLayer.parseCMDReadHistoryBlockResponsePacket
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
     * Base class for application layer exceptions.
     *
     * @param message The detail message.
     */
    open class ExceptionBase(message: String) : ComboException(message)

    /**
     * Exception thrown when an application layer packet arrives with an invalid service ID.
     *
     * @property tpLayerPacket Underlying transport layer DATA packet containing the application layer packet data.
     * @property serviceID The invalid service ID.
     * @property payload The application packet's payload.
     */
    class InvalidServiceIDException(
        val tpLayerPacket: TransportLayer.Packet,
        val serviceID: Int,
        val payload: List<Byte>
    ) : ExceptionBase("Invalid/unknown application layer packet service ID 0x${serviceID.toString(16)}")

    /**
     * Exception thrown when an application layer packet arrives with an invalid application layer command ID.
     *
     * @property tpLayerPacket Underlying transport layer DATA packet containing the application layer packet data.
     * @property serviceID Service ID from the application layer packet.
     * @property commandID The invalid application layer command ID.
     * @property payload The application packet's payload.
     */
    class InvalidCommandIDException(
        val tpLayerPacket: TransportLayer.Packet,
        val serviceID: ServiceID,
        val commandID: Int,
        val payload: List<Byte>
    ) : ExceptionBase(
        "Invalid/unknown application layer packet command ID " +
            "0x${commandID.toString(16)} (service ID: ${serviceID.name})"
    )

    /**
     * Exception thrown when a different application layer packet was expected than the one that arrived.
     *
     * More precisely, the arrived packet's command is not the one that was expected.
     *
     * @property appLayerPacket Application layer packet that arrived.
     * @property expectedCommand The command that was expected in the packet.
     */
    class IncorrectPacketException(
        val appLayerPacket: Packet,
        val expectedCommand: Command
    ) : ExceptionBase(
        "Incorrect packet: expected ${expectedCommand.name} " +
            "packet, got ${appLayerPacket.command.name} one"
    )

    /**
     * Exception thrown when the combo sends a CTRL_SERVICE_ERROR packet.
     *
     * These packets notify about errors in the communication between client and Combo
     * at the application layer.
     *
     * @property appLayerPacket Application layer packet that arrived.
     * @property serviceError The service error information from the packet.
     */
    class ServiceErrorException(
        val appLayerPacket: Packet,
        val serviceError: CTRLServiceError
    ) : ExceptionBase(
        "Service error reported by Combo: $serviceError"
    )

    /**
     * Exception thrown when something is wrong with an application layer packet's payload.
     *
     * @property appLayerPacket Application layer packet with the invalid payload.
     * @property message Detail message.
     */
    class InvalidPayloadException(
        val appLayerPacket: Packet,
        message: String
    ) : ExceptionBase(message)

    /**
     * Exception thrown when something a packet's payload data is considered corrupted.
     *
     * This is distinct from [InvalidPayloadException] in that the former is more concerned
     * about parameters like the payload size (example: "expected 15 bytes payload, got 7 bytes"),
     * while this exception is thrown when for example a CRC integrity check indicates that
     * the payload bytes themselves are incorrect.
     *
     * @property appLayerPacket Application layer packet with the corrupted payload.
     * @property message Detail message.
     */
    class PayloadDataCorruptionException(
        val appLayerPacket: Packet,
        message: String
    ) : ExceptionBase(message)

    /**
     * Exception thrown when during an attempt to retrieve history data said data never seems to end.
     *
     * Normally, there will eventually be a packet that indicates that the history
     * has been fully received. If no such packet arrives, then something is wrong.
     *
     * @property message Detail message.
     */
    class InfiniteHistoryDataException(
        message: String
    ) : ExceptionBase(message)

    /**
     * Exception thrown when an application layer packet is received with an error code that indicates an error.
     *
     * All application layer packets that are transmitted to the client via reliable
     * transport layer packet have a 16-bit error code in the first 2 bytes of their
     * payloads. If this error code's value is 0, there's no error. Otherwise, an
     * error occurred. These are not recoverable, so this exception is thrown which
     * causes the packet receiver to fail.
     *
     * @property appLayerPacket Application layer packet with the nonzero error code.
     * @property errorCode Parsed error code.
     */
    class ErrorCodeException(
        val appLayerPacket: Packet,
        val errorCode: ErrorCode
    ) : ExceptionBase("received error code $errorCode in packet $appLayerPacket")

    /**
     * Valid application layer commands.
     *
     * An application layer command is a combination of a service ID, a command ID,
     * and a flag whether or not the command is to be sent with the underlying
     * DATA transport layer packet's reliability flag set or unset. The former
     * two already uniquely identify the command; the "reliable" flag is additional
     * information.
     */
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

            /**
             * Returns the command that has a matching service ID and command ID.
             *
             * @return Command, or null if no matching command exists.
             */
            fun fromIDs(serviceID: ServiceID, commandID: Int) = values.firstOrNull {
                (it.serviceID == serviceID) && (it.commandID == commandID)
            }
        }
    }

    /**
     * Valid application layer command service IDs.
     */
    enum class ServiceID(val id: Int) {

        CONTROL(0x00),
        RT_MODE(0x48),
        COMMAND_MODE(0xB7);

        companion object {

            private val values = ServiceID.entries.toTypedArray()

            /**
             * Converts an int to a service ID.
             *
             * @return ServiceID, or null if the int is not a valid ID.
             */
            fun fromInt(value: Int) = values.firstOrNull { it.id == value }
        }
    }

    /**
     * Class for error codes contained in reliable application layer packets coming from the pump.
     *
     * All application layer packets that are transmitted to the client via reliable
     * transport layer packet have a 16-bit error code in the first 2 bytes of their
     * payloads. This class contains that error code. The [ErrorCode.Known.Code] enum
     * contains all currently known error codes. [ErrorCode.Unknown] is used in case
     * the error code value is not one of the known ones. The toString functions of
     * both [ErrorCode.Known] and [ErrorCode.Unknown] are overridden to provide better
     * descriptions of their contents.
     *
     * The [ErrorCode.fromInt] function is used for converting an integer value to
     * an ErrorCode instance. Said integer comes from the reliable packets.
     */
    sealed class ErrorCode {

        data class Known(val code: Code) : ErrorCode() {

            override fun toString(): String = "error code \"${code.description}\""

            enum class Category {
                GENERAL,
                REMOTE_TERMINAL_MODE,
                COMMAND_MODE
            }

            enum class Code(val value: Int, val category: Category, val description: String) {
                NO_ERROR(0x0000, Category.GENERAL, "No error"),

                UNKNOWN_SERVICE_ID(0xF003, Category.GENERAL, "Unknown service ID"),
                INCOMPATIBLE_AL_PACKET_VERSION(0xF005, Category.GENERAL, "Incompatible application layer packet version"),
                INVALID_PAYLOAD_LENGTH(0xF006, Category.GENERAL, "Invalid payload length"),
                NOT_CONNECTED(0xF056, Category.GENERAL, "Application layer not connected"),
                INCOMPATIBLE_SERVICE_VERSION(0xF059, Category.GENERAL, "Incompatible service version"),
                REQUEST_WITH_UNKNOWN_SERVICE_ID(
                    0xF05A, Category.GENERAL,
                    "Version, activate, deactivate request with unknown service ID"
                ),
                SERVICE_ACTIVATION_NOT_ALLOWED(0xF05C, Category.GENERAL, "Service activation not allowed"),
                COMMAND_NOT_ALLOWED(0xF05F, Category.GENERAL, "Command not allowed (wrong mode)"),

                RT_PAYLOAD_WRONG_LENGTH(0xF503, Category.REMOTE_TERMINAL_MODE, "RT payload wrong length"),
                RT_DISPLAY_INCORRECT_INDEX(
                    0xF505, Category.REMOTE_TERMINAL_MODE,
                    "RT display with incorrect row index, update, or display index"
                ),
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
                return if (foundCode != null)
                    Known(foundCode)
                else
                    Unknown(value)
            }
        }
    }

    /**
     * Error information from CTRL_SERVICE_ERROR packets.
     *
     * The service and command ID are kept as integer on purpose, since
     * it is not known if all possible values are known, so directly
     * having enum types here would not allow for representing unknown
     * values properly.
     *
     * @property errorCode Error code specifying the error.
     * @property serviceIDValue Integer with the value of the
     *           service ID of the command that caused the error.
     * @property commandIDValue Integer with the value of the
     *           command ID of the command that caused the error.
     */
    data class CTRLServiceError(
        val errorCode: ErrorCode,
        val serviceIDValue: Int,
        val commandIDValue: Int
    ) {

        override fun toString(): String {
            var command: Command? = null

            val serviceID = ServiceID.fromInt(serviceIDValue)
            if (serviceID != null)
                command = Command.fromIDs(serviceID, commandIDValue)

            val commandStr =
                if (command != null)
                    "command \"${command.name}\""
                else
                    "service ID 0x${serviceIDValue.toString(16)} command ID 0x${commandIDValue.toString(16)}"

            return "$errorCode $commandStr"
        }
    }

    /**
     * Possible status the pump can be in.
     */
    enum class CMDPumpStatus(val str: String) {

        STOPPED("STOPPED"),
        RUNNING("RUNNING");

        override fun toString() = str
    }

    data class CMDErrorWarningStatus(val errorOccurred: Boolean, val warningOccurred: Boolean)

    /**
     * Command mode history event details.
     *
     * IMPORTANT: Bolus amounts are given in 0.1 IU units,
     * so for example, "57" means 5.7 IU.
     */
    sealed class CMDHistoryEventDetail(val isBolusDetail: Boolean) {

        data class QuickBolusRequested(val bolusAmount: Int) : CMDHistoryEventDetail(isBolusDetail = true)
        data class QuickBolusInfused(val bolusAmount: Int) : CMDHistoryEventDetail(isBolusDetail = true)
        data class StandardBolusRequested(
            val bolusAmount: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class StandardBolusInfused(
            val bolusAmount: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class ExtendedBolusStarted(
            val totalBolusAmount: Int,
            val totalDurationMinutes: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class ExtendedBolusEnded(
            val totalBolusAmount: Int,
            val totalDurationMinutes: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class MultiwaveBolusStarted(
            val totalBolusAmount: Int,
            val immediateBolusAmount: Int,
            val totalDurationMinutes: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class MultiwaveBolusEnded(
            val totalBolusAmount: Int,
            val immediateBolusAmount: Int,
            val totalDurationMinutes: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class NewDateTimeSet(val dateTime: LocalDateTime) : CMDHistoryEventDetail(isBolusDetail = false)
    }

    /**
     * Information about an event in a command mode history block.
     *
     * "Quick bolus of 3.7 IU infused at 2020-03-11 11:55:23" is one example
     * of the information events provide. Each event contains a timestamp
     * and event specific details.
     *
     * Each event has an associated counter value. The way it is currently
     * understood is that these are the values of a unique internal event
     * counter at the time the event occurred, making this a de-facto ID.
     *
     * @property timestamp Timestamp of when the event occurred.
     * @property eventCounter Counter value for this event.
     * @property detail Event specific details (see [CMDHistoryEventDetail]).
     */
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

            if (timestamp != other.timestamp)
                return false

            if (eventCounter != other.eventCounter)
                return false

            if (detail != other.detail)
                return false

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
     * A block of command mode history events.
     *
     * In command mode, history events are communicated in blocks. Each block
     * consists of a list of "events", for example "quick bolus of 0.5 infused".
     * Each event has a timestamp and event specific details. In addition, the
     * block contains extra information about the other available events.
     *
     * To get all available events, the user has to send multiple history block
     * requests according to that extra information. If moreEventsAvailable is
     * true, then there are more history blocks that can be retrieved. Otherwise,
     * this is the last block.
     *
     * A block is retrieved with the CMD_READ_HISTORY_BLOCK command, and arrives
     * as the CMD_READ_HISTORY_BLOCK_RESPONSE command. The former is generated
     * using [createCMDReadHistoryBlockPacket], the latter is parsed using
     * [parseCMDReadHistoryBlockResponsePacket]. The parse function throws an
     * exception if its integrity checks discover that the block seems corrupted.
     * In such a case, the block can be requested again simply by sending the
     * CMD_READ_HISTORY_BLOCK again. If the block is OK, it is confirmed by
     * sending CMD_CONFIRM_HISTORY_BLOCK. This will inform the Combo that the
     * user is done with that block. Afterwards, a CMD_READ_HISTORY_BLOCK
     * command sent to the Combo will result in the next block being returned.
     *
     * In pseudo code:
     *
     * ```
     * while (true) {
     *     sendPacketToCombo(createCMDReadHistoryBlockPacket())
     *     packet = waitForPacketFromCombo(CMD_READ_HISTORY_BLOCK_RESPONSE)
     *
     *     try {
     *         historyBlock = parseCMDReadHistoryBlockResponsePacket(packet)
     *     } catch (exception) {
     *         continue
     *     }
     *
     *     processHistoryBlock(historyBlock)
     *
     *     sendPacketToCombo(createCMDConfirmHistoryBlockPacket())
     *     waitForPacketFromCombo(CMD_CONFIRM_HISTORY_BLOCK_RESPONSE) // actual packet data is not needed here
     *
     *     if (!historyBlock.moreEventsAvailable)
     *         break
     * }
     *
    */
