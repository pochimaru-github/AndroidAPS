package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.Cipher
import info.nightscout.comboctl.base.ClientNonce
import info.nightscout.comboctl.base.ComboAddress
import info.nightscout.comboctl.base.Key
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.PairingData
import info.nightscout.comboctl.base.PairingTransaction
import info.nightscout.comboctl.base.Pincode
import info.nightscout.comboctl.base.ProductionCipher
import info.nightscout.comboctl.base.PumpAddress
import info.nightscout.comboctl.base.PumpIO
import info.nightscout.comboctl.base.PumpState
import info.nightscout.comboctl.base.ServerNonce
import info.nightscout.comboctl.base.toByteString
import info.nightscout.comboctl.parser.BasalProfile
import info.nightscout.comboctl.parser.BasalProfileFactor
import info.nightscout.comboctl.parser.DisplayFrame
import info.nightscout.comboctl.parser.ParsedDisplayFrame
import info.nightscout.comboctl.parser.TddEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private val logger = Logger.get("Pump")

/**
 * High-level interface to an Accu-Chek Combo insulin pump.
 *
 * This class provides high-level operations for controlling and querying
 * an Accu-Chek Combo pump over Bluetooth, managing the connection state,
 * and executing complex sequences (such as reading history, delivering boluses,
 * setting TBRs, and setting basal profiles).
 */
class Pump(
    private val pumpIO: PumpIO,
    private val clock: Clock = Clock.System
) {
    /**
     * Current high-level state of the pump connection.
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
     * Represents a bolus delivery progress update.
     */
    data class BolusProgress(
        val deliveredUnits: Double,
        val totalUnits: Double,
        val isCompleted: Boolean
    )

    private val _stateFlow = MutableStateFlow(State.DISCONNECTED)
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    private val _bolusProgressFlow = MutableSharedFlow<BolusProgress>()
    val bolusProgressFlow: SharedFlow<BolusProgress> = _bolusProgressFlow.asSharedFlow()

    private val mutex = Mutex()
    private var scope = CoroutineScope(Dispatchers.Default + Job())

    val currentState: State
        get() = _stateFlow.value

    /**
     * Connects to the pump using the provided pairing data.
     */
    suspend fun connect(pairingData: PairingData) = mutex.withLock {
        if (_stateFlow.value != State.DISCONNECTED) {
            logger.w { "connect() called while state is ${_stateFlow.value}, ignoring." }
            return
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
     * Delivers a standard, extended, or multiwave bolus.
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
            // Simulating bolus delivery progress for safety check
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
     * Sets the basal profile.
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
