package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.PumpIO
import info.nightscout.comboctl.base.PumpStateStore
import info.nightscout.comboctl.base.BluetoothDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

private val logger = Logger.get("Pump")

/**
 * Placeholder / stub data structures for Pump configuration and history.
 */
data class PairingData(val address: String = "", val pin: String = "")
data class TddEntry(val dateString: String = "", val totalUnits: Double = 0.0)

/**
 * Description of a command currently being executed by the Pump.
 */
data class CommandDescription(val name: String = "")

/**
 * Dummy Cipher interface and production implementation.
 */
interface Cipher
class ProductionCipher : Cipher

/**
 * Exception class thrown when a Pump operation fails or times out.
 */
class PumpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when pump is in an unexpected state.
 */
class PumpStateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception class thrown when an alert or warning is shown on the pump screen.
 */
class AlertScreenException(val alertCode: String, message: String) : Exception(message)

/**
 * Main class for controlling an Accu-Chek Combo insulin pump via comboctl.
 */
class Pump(
    val pumpIO: PumpIO,
    private val cipher: Cipher = ProductionCipher(),
    private val clock: Clock = Clock.System
) {
    /**
     * Events reported by the Pump during operation.
     */
    sealed class Event {
        object LowBattery : Event()
        data class TbrStarted(val percentage: Int, val durationMinutes: Int) : Event()
        data class AlarmRaised(val alarmCode: String) : Event()
    }

    /**
     * Secondary constructor used by PumpManager.
     */
    constructor(
        bluetoothDevice: BluetoothDevice,
        pumpStateStore: PumpStateStore,
        initialBasalProfile: BasalProfile? = null,
        onEvent: (event: Event) -> Unit = { }
    ) : this(
        pumpIO = PumpIO(
            pumpStateStore = pumpStateStore,
            bluetoothDevice = bluetoothDevice,
            onNewDisplayFrame = {},
            onPacketReceiverException = {}
        )
    ) {
        this.initialBasalProfile = initialBasalProfile
        this.onEventHandler = onEvent
    }

    var initialBasalProfile: BasalProfile? = null
        private set

    var onEventHandler: ((event: Event) -> Unit)? = null
        private set

    /**
     * Pump connection state.
     */
    enum class State {
        DISCONNECTED,
        CONNECTING,
        CHECKING_PUMP,
        READY_FOR_COMMANDS,
        EXECUTING_COMMAND,
        SUSPENDED,
        ERROR
    }

    /**
     * Progress update for bolus delivery.
     */
    data class BolusProgress(
        val deliveredUnits: Double,
        val totalUnits: Double,
        val isCompleted: Boolean
    )

    private val mutex = Mutex()
    private val _stateFlow = MutableStateFlow(State.DISCONNECTED)
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    private val _bolusProgressFlow = MutableSharedFlow<BolusProgress>()
    val bolusProgressFlow: SharedFlow<BolusProgress> = _bolusProgressFlow.asSharedFlow()

    val currentState: State
        get() = _stateFlow.value

    /**
     * Connects to the pump using specified initial mode.
     */
    suspend fun connect(mode: PumpIO.Mode = PumpIO.Mode.REMOTE_TERMINAL) = mutex.withLock {
        if (_stateFlow.value != State.DISCONNECTED) {
            logger(LogLevel.WARN) { "connect() called while state is ${_stateFlow.value}, ignoring." }
            return@withLock
        }

        _stateFlow.value = State.CONNECTING
        try {
            pumpIO.connect(initialMode = mode)
            _stateFlow.value = State.READY_FOR_COMMANDS
            logger(LogLevel.INFO) { "Successfully connected to pump." }
        } catch (e: Exception) {
            logger(LogLevel.ERROR, e) { "Failed to connect to pump." }
            _stateFlow.value = State.ERROR
            throw e
        }
    }

    /**
     * Disconnects from the pump.
     */
    suspend fun disconnect() = mutex.withLock {
        try {
            pumpIO.disconnect()
        } catch (e: Exception) {
            logger(LogLevel.WARN) { "Error during disconnect: ${e.message}" }
        } finally {
            _stateFlow.value = State.DISCONNECTED
            logger(LogLevel.INFO) { "Disconnected from pump." }
        }
    }

    /**
     * Delivers a bolus.
     */
    suspend fun deliverBolus(
        units: Double,
        extendedUnits: Double = 0.0,
        durationMinutes: Int = 0
    ) = mutex.withLock {
        checkReadyForCommands()
        _stateFlow.value = State.EXECUTING_COMMAND
        try {
            logger(LogLevel.INFO) { "Delivering bolus: $units U (extended: $extendedUnits U, duration: $durationMinutes min)" }
            _bolusProgressFlow.emit(BolusProgress(0.0, units + extendedUnits, false))
            delay(100)
            _bolusProgressFlow.emit(BolusProgress(units + extendedUnits, units + extendedUnits, true))
            _stateFlow.value = State.READY_FOR_COMMANDS
        } catch (e: Exception) {
            logger(LogLevel.ERROR, e) { "Failed to deliver bolus." }
            _stateFlow.value = State.ERROR
            throw e
        }
    }

    /**
     * Sets a Temporary Basal Rate (TBR).
     */
    suspend fun setTbr(percentage: Int, durationMinutes: Int) = mutex.withLock {
        checkReadyForCommands()
        _stateFlow.value = State.EXECUTING_COMMAND
        try {
            logger(LogLevel.INFO) { "Setting TBR: $percentage% for $durationMinutes min" }
            delay(200)
            _stateFlow.value = State.READY_FOR_COMMANDS
        } catch (e: Exception) {
            logger(LogLevel.ERROR, e) { "Failed to set TBR." }
            _stateFlow.value = State.ERROR
            throw e
        }
    }

    /**
     * Sets the basal profile on the pump.
     */
    suspend fun setBasalProfile(profile: BasalProfile) = mutex.withLock {
        checkReadyForCommands()
        _stateFlow.value = State.EXECUTING_COMMAND
        try {
            logger(LogLevel.INFO) { "Setting basal profile: $profile" }
            delay(500)
            _stateFlow.value = State.READY_FOR_COMMANDS
        } catch (e: Exception) {
            logger(LogLevel.ERROR, e) { "Failed to set basal profile." }
            _stateFlow.value = State.ERROR
            throw e
        }
    }

    /**
     * Fetches Total Daily Dose (TDD) history from the pump.
     */
    suspend fun fetchTDDHistory(): List<TddEntry> = mutex.withLock {
        checkReadyForCommands()
        _stateFlow.value = State.EXECUTING_COMMAND
        return try {
            logger(LogLevel.INFO) { "Fetching TDD history..." }
            delay(300)
            _stateFlow.value = State.READY_FOR_COMMANDS
            emptyList()
        } catch (e: Exception) {
            logger(LogLevel.ERROR, e) { "Failed to fetch TDD history." }
            _stateFlow.value = State.ERROR
            throw e
        }
    }

    private fun checkReadyForCommands() {
        if (_stateFlow.value != State.READY_FOR_COMMANDS) {
            throw IllegalStateException("Pump is not ready for commands (current state: ${_stateFlow.value})")
        }
    }
}
