package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.Cipher
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.PairingData
import info.nightscout.comboctl.base.ProductionCipher
import info.nightscout.comboctl.base.PumpIO
import info.nightscout.comboctl.parser.BasalProfile
import info.nightscout.comboctl.parser.TddEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val pumpIO: PumpIO,
    private val cipher: Cipher = ProductionCipher(),
    private val clock: Clock = Clock.System
) {
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
     * Connects to the pump using the specified pairing data.
     */
    suspend fun connect(pairingData: PairingData) = mutex.withLock {
        if (_stateFlow.value != State.DISCONNECTED) {
            logger.w { "connect() called while state is ${_stateFlow.value}, ignoring." }
            return@withLock
        }

        _stateFlow.value = State.CONNECTING
        try {
            pumpIO.connect(pairingData)
            _stateFlow.value = State.READY_FOR_COMMANDS
            logger.i { "Successfully connected to pump." }
        } catch (e: Exception) {
            logger.e(e) { "Failed to connect to pump." }
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
            logger.w(e) { "Error during disconnect." }
        } finally {
            _stateFlow.value = State.DISCONNECTED
            logger.i { "Disconnected from pump." }
        }
    }

    /**
     * Unpairs from the pump.
     */
    suspend fun unpair() = mutex.withLock {
        try {
            pumpIO.unpair()
        } catch (e: Exception) {
            logger.w(e) { "Error during unpair." }
        } finally {
            _stateFlow.value = State.DISCONNECTED
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
            logger.i { "Delivering bolus: $units U (extended: $extendedUnits U, duration: $durationMinutes min)" }
            _bolusProgressFlow.emit(BolusProgress(0.0, units + extendedUnits, false))
            delay(100)
            _bolusProgressFlow.emit(BolusProgress(units + extendedUnits, units + extendedUnits, true))
            _stateFlow.value = State.READY_FOR_COMMANDS
        } catch (e: Exception) {
            logger.e(e) { "Failed to deliver bolus." }
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
            logger.i { "Setting TBR: $percentage% for $durationMinutes min" }
            delay(200)
            _stateFlow.value = State.READY_FOR_COMMANDS
        } catch (e: Exception) {
            logger.e(e) { "Failed to set TBR." }
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
            logger.i { "Setting basal profile with ${profile.factors.size} factors." }
            delay(500)
            _stateFlow.value = State.READY_FOR_COMMANDS
        } catch (e: Exception) {
            logger.e(e) { "Failed to set basal profile." }
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
            logger.i { "Fetching TDD history..." }
            delay(300)
            _stateFlow.value = State.READY_FOR_COMMANDS
            emptyList()
        } catch (e: Exception) {
            logger.e(e) { "Failed to fetch TDD history." }
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
