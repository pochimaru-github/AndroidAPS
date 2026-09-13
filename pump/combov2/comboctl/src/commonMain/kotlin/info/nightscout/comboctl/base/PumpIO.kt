package info.nightscout.comboctl.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.ExperimentalTime

private val logger = Logger.get("PumpIO")

private object PumpIOConstants {
    const val MAX_NUM_REGULAR_CONNECTION_ATTEMPTS = 3
    const val NONCE_INCREMENT = 500
}

/**
 * Callback used during pairing for asking the user for the 10-digit PIN.
 *
 * This is passed to [PumpIO.performPairing] when pairing.
 *
 * [previousAttemptFailed] is useful for showing in a GUI that the
 * previously entered PIN seems to be wrong and that the user needs
 * to try again.
 *
 * If the user wants to cancel the pairing instead of entering the
 * PIN, cancelling the coroutine where [PumpIO.performPairing] runs
 * is sufficient.
 *
 * @param previousAttemptFailed true if the user was already asked for
 *        the PIN and the KEY_RESPONSE authentication failed.
 */
typealias PairingPINCallback = suspend (previousAttemptFailed: Boolean) -> PairingPIN

/**
 * Class for high-level Combo pump IO.
 *
 * This implements high level IO actions on top of [TransportLayer.IO].
 * It takes care of pairing, connection setup, remote terminal (RT)
 * commands and RT display reception, and also supports the Combo's
 * command mode. Basically, this class' public API reflects what the
 * user can directly do with the pump (press RT buttons like UP or
 * DOWN, send a command mode bolus etc.).
 *
 * For initiating the Combo pairing, the [performPairing] function is
 * available. This must not be used if a connection was already established
 * via [connect] - pairing is only possible in the disconnected state.
 *
 * For initiating a regular connection, use [connect]. Do not call
 * [connect] again until after disconnecting with [disconnect]. Also
 * see the remarks above about pairing and connecting at the same time.
 *
 * The Combo regularly sends new display frames when running in the
 * REMOTE_TERMINAL (RT) mode. These frames come as the payload of
 * RT_DISPLAY packets. This class reads those packets and extracts the
 * partial frames (the packets only contain portions of a frame, not
 * a full frame). Once enough parts were gathered to assemble a full
 * frame, the frame is emitted via [onNewDisplayFrame]. This callback
 * must not block, since this would otherwise block the dataflow in the
 * internal IO code. This callback is mainly meant for passing the
 * frame to something like a flow or a channel. If its argument is
 * null, it means that there's no frame available. This happens at
 * the beginning in [connect] and during the [switchMode] call.
 *
 * To handle IO at the transport layer, this uses [TransportLayer.IO]
 * internally.
 *
 * In regular connections, the Combo needs "heartbeats" to periodically
 * let it know that the client still exists. If too much time passes since
 * the last heartbeat, the Combo terminates the connection. Each mode has a
 * different type of heartbeat: RT mode has RT_KEEP_ALIVE commands, command
 * mode has CMD_PING commands. To periodically send these, this class runs
 * separate coroutines with loops inside that send these commands. Only one
 * of these two heartbeats are active, depending on the current mode of the
 * Combo.
 * Note that other commands sent to the Combo _also_ count as heartbeats,
 * so RT_KEEP_ALIVE / CMD_PING only need to be sent by the internal heartbeat
 * if no other command has been sent for a while now.
 * In some cases (typically unit tests), a regular connection without
 * a heartbeat is needed. [connect] accepts an argument to start
 * without one for this purpose.
 *
 * The supplied [pumpStateStore] is used during pairing and regular
 * connections. During pairing, a new pump state is set up for the
 * pump that is being paired. It is during pairing that the invariant
 * portion of the pump state is written. During regular (= not pairing)
 * connections, the invariant part is read, not written.
 *
 * This class accesses the pump state in a thread safe manner, ensuring
 * that no two threads access the pump state at the same time. See
 * [PumpStateStore] for details about thread safety.
 *
 * @param pumpStateStore Pump state store to use.
 * @param bluetoothDevice [BluetoothDevice] object to use for
 *   Bluetooth I/O. Must be in a disconnected state when
 *   assigned to this instance.
 * @param onNewDisplayFrame Callback to invoke whenever a new RT
 *   [DisplayFrame] was received.
 * @param onPacketReceiverException Callback to invoked whenever an
 *   exception is thrown inside the transport layer's receiver loop.
 *   This is useful for automatic reconnecting.
 */
class PumpIO(
    private val pumpStateStore: PumpStateStore,
    private val bluetoothDevice: BluetoothDevice,
    private val onNewDisplayFrame: (displayFrame: DisplayFrame?) -> Unit,
    private val onPacketReceiverException: (e: TransportLayer.PacketReceiverException) -> Unit
) {
    // Mutex to synchronize sendPacketWithResponse and sendPacketWithoutResponse calls.
    private val sendPacketMutex = Mutex()

    // RT sequence number. Used in outgoing RT packets.
    private var currentRTSequence: Int = 0

    // Pass IO through the FramedComboIO class since the Combo
    // sends packets in a framed form (See [ComboFrameParser]
    // and [List<Byte>.toComboFrame] for details).
    private val framedComboIO = FramedComboIO(bluetoothDevice)

    private var initialMode: Mode? = null

    private var transportLayerIO = TransportLayer.IO(
        pumpStateStore, bluetoothDevice.address, framedComboIO
    ) { packetReceiverException ->
        // If the packet receiver fails, close the barrier to wake
        // up any caller that is waiting on it.
        rtButtonConfirmationBarrier.close(packetReceiverException)
        // Also forward the exception to the associated callback.
        onPacketReceiverException(packetReceiverException)
    }

    private var internalScopeJob: Job? = null
    private var internalScope: CoroutineScope? = null

    // Job representing the coroutine that runs the CMD ping heartbeat.
    private var cmdPingHeartbeatJob: Job? = null
    // Job representing the coroutine that runs the RT keep-alive heartbeat.
    private var rtKeepAliveHeartbeatJob: Job? = null

    // Members associated with long-pressing buttons in RT mode.
    // Long-pressing is implemented by repeatedly sending RT_BUTTON_STATUS
    // messages until the user "releases" the buttons.
    // (We use a list of Button values in case multiple buttons are being
    // held down at the same time.)
    // We use a Deferred instance instead of Job to be able to catch
    // and store exceptions & rethrow them later.
    private var currentLongRTPressJob: Deferred<Unit>? = null
    private var currentLongRTPressedButtons = listOf<ApplicationLayer.RTButton>()
    private var longRTPressLoopRunning = true

    // A Channel that is used as a "barrier" of sorts to block button
    // pressing functions from continuing until the Combo sends
    // a confirmation for the key press. Up until that confirmation
    // is received, the client must not send any other button press
    // commands to the Combo. To ensure that, this barrier exists.
    // Its payload is a Boolean to let waiting coroutines know whether
    // to finish or to continue any internal loops. The former happens
    // during disconnect. It is set up as a conflated channel. That
    // way, if a confirmation is received before button press commands
    // call receive(), information about the confirmation is not lost
    // (which would happen with a rendezvous channel). And, in case
    // disconnect() is called, it is important to overwrite any other
    // existing value with "false" to stop button pressing commands
    // (hence a conflated channel instead of DROP_OLDEST buffer
    // overflow behavior).
    private var rtButtonConfirmationBarrier = newRtButtonConfirmationBarrier()

    private val displayFrameAssembler = DisplayFrameAssembler()

    // Whether we are in RT or COMMAND mode, or null at startup
    // before an initial mode was set.
    private val _currentModeFlow = MutableStateFlow<Mode?>(null)

    /************************************
     *** PUBLIC FUNCTIONS AND CLASSES ***
     ************************************/

    /**
     * The mode the pump can operate in.
     */
    enum class Mode(val str: String) {
        REMOTE_TERMINAL("REMOTE_TERMINAL"),
        COMMAND("COMMAND");

        override fun toString() = str
    }

    /**
     * Current connection state.
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,

        /**
         * State after a pump command failed, typically because of an IO error.
         *
         * When this state is reached, nothing more can be done with the pump
         * until [disconnect] is called.
         */
        FAILED
    }

    /**
     * Exception thrown after all attempts to establish a regular connection failed.
     *
     * See [connect] for details about the regular connection attempts.
     */
    class ConnectionRequestIsNotBeingAcceptedException :
        ComboIOException("All attempts to have the Combo accept connection request after establishing Bluetooth socket failed")

    /**
     * The pump's Bluetooth address.
     */
    val address: BluetoothAddress = bluetoothDevice.address

    /**
     * Read-only [StateFlow] property that announces when the current [Mode] changes.
     *
     * This flow'value is null until the connection is fully established (at which point
     * the mode is set to [PumpIO.Mode.REMOTE_TERMINAL] or [PumpIO.Mode.COMMAND]), and
     * set back to null again after disconnecting.
     */
    val currentModeFlow = _currentModeFlow.asStateFlow()

    private var _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    /**
     * Read-only [StateFlow] property that notifies about connection state changes.
     */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Returns whether this pump has already been paired.
     *
     * "Pairing" refers to the custom Combo pairing here, not the Bluetooth pairing.
     * It is not possible to get a valid [PumpIO] instance without the device having
     * been paired at the Bluetooth level before anyway.
     *
     * This detects whether the Combo pairing has been performed by looking at the
     * persistent state associated with this [PumpIO] instance. If the state
     * is set to valid values, then the pump is assumed to be paired. If the persistent
     * state is in its initial state (ciphers set to null, key response address
     * set to null), then it is assumed to be unpaired.
     *
     * @return true if the pump is paired.
     */
    fun isPaired() = pumpStateStore.hasPumpState(bluetoothDevice.address)

    /**
     * Performs a pairing procedure with a Combo.
     *
     * This performs the Combo-specific pairing. When this is called,
     * the pump must have been paired at the Bluetooth level already.
     * From Bluetooth's point of view, the pump is already paired with
     * the client at this point. But the Combo itself needs an additional
     * custom pairing. As part of this extra pairing, this function sets
     * up a special temporary pairing connection to the Combo, and terminates
     * that connection before finishing. Manually setting up such a connection
     * is not necessary and not supported by the public API.
     *
     * However, the Bluetooth connection setup and teardown _is_ handled
     * by this function. If the Combo-specific pairing fails, this also
     * automatically unpairs the pump at the Bluetooth level.
     *
     * Cancelling the coroutine this function runs in will abort the pairing
     * process in an orderly fashion.
     *
     * Pairing will initialize a new state for this pump [PumpStateStore] that
     * was passed to the constructor of this class. This state will contain
     * new pairing data, a new pump ID string, and a new initial nonce.
     *
     * The [onPairingPIN] block has two arguments. previousAttemptFailed
     * is set to false initially, and true if this is a repeated call due
     * to a previous failure to apply the PIN. Such a failure typically
     * happens because the user mistyped the PIN, but in rare cases can also
     * happen due to corrupted packets.
     *
     * Note that [onPairingPIN] is called by a coroutine that is run on
     * a different thread than the one that called this function . With
     * some UI frameworks like JavaFX, it is invalid to operate UI controls
     * in coroutines that are not associated with a particular UI coroutine
     * context. Consider using [kotlinx.coroutines.withContext] in
     * [onPairingPIN] for this reason.
     *
     * WARNING: Do not run multiple performPairing functions simultaneously
     * on the same pump. Otherwise, undefined behavior occurs.
     *
     * @param bluetoothFriendlyName The Bluetooth friendly name to use in
     *   REQUEST_ID packets. Use [BluetoothInterface.getAdapterFriendlyName]
     *   to get the friendly name.
     * @param progressReporter [ProgressReporter] for tracking pairing progress.
     * @param onPairingPIN Suspending block that asks the user for
     *   the 10-digit pairing PIN during the pairing process.
     * @throws IllegalStateException if this is ran while a connection
     *   is running.
     * @throws PumpStateAlreadyExistsException if the pump was already
     *   fully paired before.
     * @throws TransportLayer.PacketReceiverException if an exception
     *   is thrown while this function is waiting for a packet.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun performPairing(
        bluetoothFriendlyName: String,
        progressReporter: ProgressReporter<Unit>?,
        onPairingPIN: suspend (newPumpAddress: BluetoothAddress, previousAttemptFailed: Boolean) -> PairingPIN
    ) {
        check(!isPaired()) {
            "Attempting to pair with pump with address ${bluetoothDevice.address} even though it is already paired"
        }

        check(!isIORunning()) {
            "Attempted to perform pairing while pump with address ${bluetoothDevice.address} is connected"
        }

        var doUnpair = true

        framedComboIO.reset()

        coroutineScope {
            try {
                _connectionState.value = ConnectionState.CONNECTING

                withContext(bluetoothDevice.ioDispatcher) {
                    bluetoothDevice.connect()
                }

                _connectionState.value = ConnectionState.CONNECTED

                transportLayerIO.start(packetReceiverScope = this) { tpLayerPacket -> processReceivedPacket(tpLayerPacket) }

                progressReporter?.setCurrentProgressStage(BasicProgressStage.PerformingConnectionHandshake)

                logger(LogLevel.DEBUG) { "Sending pairing connection request" }
                sendPacketWithResponse(
                    TransportLayer.createRequestPairingConnectionPacketInfo(),
                    TransportLayer.Command.PAIRING_CONNECTION_REQUEST_ACCEPTED
                )

                logger(LogLevel.DEBUG) { "Requesting the pump to generate and show the pairing PIN" }
                sendPacketWithoutResponse(TransportLayer.createRequestKeysPacketInfo())

                progressReporter?.setCurrentProgressStage(BasicProgressStage.ComboPairingKeyAndPinRequested)

                logger(LogLevel.DEBUG) { "Requesting the keys from the pump" }
                val keyResponsePacket = sendPacketWithResponse(
                    TransportLayer.createGetAvailableKeysPacketInfo(),
                    TransportLayer.Command.KEY_RESPONSE
                )

                logger(LogLevel.DEBUG) { "Will ask for pairing PIN" }
                var previousPINAttemptFailed = false

                lateinit var keyResponseInfo: KeyResponseInfo
                while (true) {
                    logger(LogLevel.DEBUG) { "Waiting for the PIN to be provided" }

                    val pin = onPairingPIN(bluetoothDevice.address, previousPINAttemptFailed)

                    logger(LogLevel.DEBUG) { "Provided PIN: $pin" }

                    val weakCipher = Cipher(generateWeakKeyFromPIN(pin))
                    logger(LogLevel.DEBUG) { "Generated weak cipher key ${weakCipher.key.toHexString()} out of pairing PIN" }

                    if (keyResponsePacket.verifyAuthentication(weakCipher)) {
                        logger(LogLevel.DEBUG) { "KEY_RESPONSE packet verified" }
                        keyResponseInfo = processKeyResponsePacket(keyResponsePacket, weakCipher)
                        break
                    } else {
                        logger(LogLevel.DEBUG) { "Could not verify KEY_RESPONSE packet; user may have entered PIN incorrectly; asking again for PIN" }
                        previousPINAttemptFailed = true
                    }
                }

                transportLayerIO.setManualInvariantPumpData(
                    InvariantPumpData(
                        clientPumpCipher = keyResponseInfo.clientPumpCipher,
                        pumpClientCipher = keyResponseInfo.pumpClientCipher,
                        keyResponseAddress = keyResponseInfo.keyResponseAddress,
                        pumpID = ""
                    )
                )

                logger(LogLevel.DEBUG) { "Requesting the pump ID from the pump" }
                val idResponsePacket = sendPacketWithResponse(
                    TransportLayer.createRequestIDPacketInfo(bluetoothFriendlyName),
                    TransportLayer.Command.ID_RESPONSE
                )
                val pumpID = processIDResponsePacket(idResponsePacket)

                val newPumpData = InvariantPumpData(
                    clientPumpCipher = keyResponseInfo.clientPumpCipher,
                    pumpClientCipher = keyResponseInfo.pumpClientCipher,
                    keyResponseAddress = keyResponseInfo.keyResponseAddress,
                    pumpID = pumpID
                )
                transportLayerIO.setManualInvariantPumpData(newPumpData)

                val currentSystemDateTime = Clock.System.now()
                val currentSystemTimeZone = TimeZone.currentSystemDefault()
                val currentSystemUtcOffset = currentSystemTimeZone.offsetAt(currentSystemDateTime)

                pumpStateStore.createPumpState(
                    bluetoothDevice.address,
                    newPumpData,
                    currentSystemUtcOffset,
                    CurrentTbrState.NoTbrOngoing
                )

                val firstTxNonce = Nonce(
                    byteArrayListOfInts(
                        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                    )
                )
                pumpStateStore.setCurrentTxNonce(bluetoothDevice.address, firstTxNonce)

                progressReporter?.setCurrentProgressStage(BasicProgressStage.ComboPairingFinishing)

                logger(LogLevel.DEBUG) { "Sending regular connection request" }
                sendPacketWithResponse(
                    TransportLayer.createRequestRegularConnectionPacketInfo(),
                    TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED
                )

                logger(LogLevel.DEBUG) { "Initiating application layer connection" }
                sendPacketWithResponse(
                    ApplicationLayer.createCTRLConnectPacket(),
                    ApplicationLayer.Command.CTRL_CONNECT_RESPONSE
                )

                logger(LogLevel.DEBUG) { "Requesting command mode service version" }
                sendPacketWithResponse(
                    ApplicationLayer.createCTRLGetServiceVersionPacket(ApplicationLayer.ServiceID.COMMAND_MODE),
                    ApplicationLayer.Command.CTRL_GET_SERVICE_VERSION_RESPONSE
                )

                logger(LogLevel.DEBUG) { "Sending BIND command" }
                sendPacketWithResponse(
                    ApplicationLayer.createCTRLBindPacket(),
                    ApplicationLayer.Command.CTRL_BIND_RESPONSE
                )

                logger(LogLevel.DEBUG) { "Reconnecting regular connection" }
                sendPacketWithResponse(
                    TransportLayer.createRequestRegularConnectionPacketInfo(),
                    TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED
                )

                doUnpair = false
                logger(LogLevel.DEBUG) { "Pairing finished successfully - sending CTRL_DISCONNECT to Combo" }
            } catch (e: CancellationException) {
                logger(LogLevel.DEBUG) { "Pairing cancelled - sending CTRL_DISCONNECT to Combo" }
                throw e
            } catch (t: Throwable) {
                logger(LogLevel.ERROR) {
                    "Pairing aborted due to throwable - sending CTRL_DISCONNECT to Combo; " +
                    "throwable details: ${t.stackTraceToString()}"
                }
                throw t
            } finally {
                val disconnectPacketInfo = ApplicationLayer.createCTRLDisconnectPacket()
                transportLayerIO.stop(
                    disconnectPacketInfo.toTransportLayerPacketInfo(),
                    ::disconnectBTDeviceAndCatchExceptions
                )

                _connectionState.value = ConnectionState.DISCONNECTED

                disconnectBTDeviceAndCatchExceptions()

                if (doUnpair) {
                    withContext(bluetoothDevice.ioDispatcher) {
                        bluetoothDevice.unpair()
                    }
                    pumpStateStore.deletePumpState(address)
                }
            }
        }
    }

    suspend fun connect(
        initialMode: Mode = Mode.REMOTE_TERMINAL,
        runHeartbeat: Boolean = true,
        connectProgressReporter: ProgressReporter<Unit>? = null
    ) {
        check(isPaired()) {
            "Attempted to connect without a valid persistent state; pairing may not have been done"
        }
        check(!isIORunning()) {
            "Attempted to connect even though a connection is already ongoing or established"
        }

        displayFrameAssembler.reset()
        onNewDisplayFrame(null)
        rtButtonConfirmationBarrier = newRtButtonConfirmationBarrier()

        val newScopeJob = SupervisorJob()
        val newScope = CoroutineScope(newScopeJob + Dispatchers.Default)

        this.initialMode = initialMode
        this.internalScopeJob = newScopeJob
        this.internalScope = newScope

        framedComboIO.reset()

        logger(LogLevel.DEBUG) { "Pump IO connecting asynchronously" }

        try {
            _connectionState.value = ConnectionState.CONNECTING

            var regularConnectionRequestAccepted = false
            for (regularConnectionAttemptNr in 0 until PumpIOConstants.MAX_NUM_REGULAR_CONNECTION_ATTEMPTS) {
                withContext(bluetoothDevice.ioDispatcher) {
                    bluetoothDevice.connect()
                }

                connectProgressReporter?.setCurrentProgressStage(BasicProgressStage.PerformingConnectionHandshake)

                try {
                    transportLayerIO.start(newScope) { tpLayerPacket -> processReceivedPacket(tpLayerPacket) }

                    logger(LogLevel.DEBUG) { "Sending regular connection request" }

                    sendPacketWithResponse(
                        TransportLayer.createRequestRegularConnectionPacketInfo(),
                        TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED
                    )

                    regularConnectionRequestAccepted = true
                    break
                } catch (e: TransportLayer.PacketReceiverException) {
                    logger(LogLevel.INFO) {
                        "Successfully set up Bluetooth socket, but attempting to send " +
                        "the regular connection request packet failed; exception: ${e.cause}"
                    }
                    logger(LogLevel.INFO) {
                        "Nonce might be wrong; incrementing nonce by ${PumpIOConstants.NONCE_INCREMENT} " +
                        "and retrying (attempt $regularConnectionAttemptNr of " +
                        "${PumpIOConstants.MAX_NUM_REGULAR_CONNECTION_ATTEMPTS})"
                    }

                    transportLayerIO.stop(disconnectPacketInfo = null, ::disconnectBTDeviceAndCatchExceptions)
                    pumpStateStore.incrementTxNonce(bluetoothDevice.address, PumpIOConstants.NONCE_INCREMENT)
                    delay(1000)
                }
            }

            if (!regularConnectionRequestAccepted) {
                logger(LogLevel.ERROR) { "All attempts to request regular connection failed" }
                throw ConnectionRequestIsNotBeingAcceptedException()
            }

            logger(LogLevel.DEBUG) { "Initiating application layer connection" }
            sendPacketWithResponse(
                ApplicationLayer.createCTRLConnectPacket(),
                ApplicationLayer.Command.CTRL_CONNECT_RESPONSE
            )

            switchMode(initialMode, runHeartbeat)

            logger(LogLevel.INFO) { "Pump IO connected" }

            _connectionState.value = ConnectionState.CONNECTED
        } catch (e: CancellationException) {
            disconnect()
            throw e
        } catch (t: Throwable) {
            newScopeJob.cancelAndJoin()
            _connectionState.value = ConnectionState.FAILED
            throw t
        }
    }

    suspend fun disconnect() {
        rtButtonConfirmationBarrier.trySend(false)

        stopCMDPingHeartbeat()
        stopRTKeepAliveHeartbeat()

        val disconnectPacketInfo = ApplicationLayer.createCTRLDisconnectPacket()
        logger(LogLevel.VERBOSE) { "Will send application layer disconnect packet:  $disconnectPacketInfo" }

        transportLayerIO.stop(
            disconnectPacketInfo.toTransportLayerPacketInfo(),
            ::disconnectBTDeviceAndCatchExceptions
        )

        internalScope = null
        internalScopeJob?.cancelAndJoin()
        internalScopeJob = null
        _currentModeFlow.value = null
        onNewDisplayFrame(null)

        logger(LogLevel.DEBUG) { "Pump IO disconnected" }
    }

    suspend fun readCMDDateTime(): LocalDateTime = runPumpIOCall("get current pump datetime", Mode.COMMAND) {
        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDReadDateTimePacket(),
            ApplicationLayer.Command.CMD_READ_DATE_TIME_RESPONSE
        )
        return@runPumpIOCall ApplicationLayer.parseCMDReadDateTimeResponsePacket(packet)
    }

    suspend fun readCMDPumpStatus(): ApplicationLayer.CMDPumpStatus = runPumpIOCall("get pump status", Mode.COMMAND) {
        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDReadPumpStatusPacket(),
            ApplicationLayer.Command.CMD_READ_PUMP_STATUS_RESPONSE
        )
        return@runPumpIOCall ApplicationLayer.parseCMDReadPumpStatusResponsePacket(packet)
    }

    suspend fun readCMDErrorWarningStatus(): ApplicationLayer.CMDErrorWarningStatus =
        runPumpIOCall("get error/warning status", Mode.COMMAND) {
        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDReadErrorWarningStatusPacket(),
            ApplicationLayer.Command.CMD_READ_ERROR_WARNING_STATUS_RESPONSE
        )
        return@runPumpIOCall ApplicationLayer.parseCMDReadErrorWarningStatusResponsePacket(packet)
    }

    suspend fun getCMDHistoryDelta(maxRequests: Int = 40): List<ApplicationLayer.CMDHistoryEvent> {
        require(maxRequests >= 10) { "Maximum amount of requests must be at least 10; caller specified $maxRequests" }

        return runPumpIOCall("get history delta", Mode.COMMAND) {
            val historyDelta = mutableListOf<ApplicationLayer.CMDHistoryEvent>()
            var reachedEnd = false

            for (requestNr in 1 until maxRequests) {
                val packet = sendPacketWithResponse(
                    ApplicationLayer.createCMDReadHistoryBlockPacket(),
                    ApplicationLayer.Command.CMD_READ_HISTORY_BLOCK_RESPONSE
                )

                val historyBlock = try {
                    ApplicationLayer.parseCMDReadHistoryBlockResponsePacket(packet)
                } catch (t: Throwable) {
                    logger(LogLevel.ERROR) {
                        "Could not parse history block; data may have been corrupted; requesting the block again (throwable: $t)"
                    }
                    continue
                }

                sendPacketWithResponse(
                    ApplicationLayer.createCMDConfirmHistoryBlockPacket(),
                    ApplicationLayer.Command.CMD_CONFIRM_HISTORY_BLOCK_RESPONSE
                )

                historyDelta.addAll(historyBlock.events)

                if (!historyBlock.moreEventsAvailable ||
                    (historyBlock.numRemainingEvents <= historyBlock.events.size)
                ) {
                    reachedEnd = true
                    break
                }
            }

            if (!reachedEnd)
                throw ApplicationLayer.InfiniteHistoryDataException(
                    "Did not reach an end of the history event list even after $maxRequests request(s)"
                )

            return@runPumpIOCall historyDelta
        }
    }

    suspend fun getCMDCurrentBolusDeliveryStatus(): ApplicationLayer.CMDBolusDeliveryStatus =
        runPumpIOCall("get current bolus delivery status", Mode.COMMAND) {

        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDGetBolusStatusPacket(),
            ApplicationLayer.Command.CMD_GET_BOLUS_STATUS_RESPONSE
        )

        return@runPumpIOCall ApplicationLayer.parseCMDGetBolusStatusResponsePacket(packet)
    }

    suspend fun deliverCMDStandardBolus(totalBolusAmount: Int): Boolean =
        deliverCMDStandardBolus(
            totalBolusAmount,
            immediateBolusAmount = 0,
            durationInMinutes = 0,
            bolusType = ApplicationLayer.CMDDeliverBolusType.STANDARD_BOLUS
        )

    suspend fun deliverCMDStandardBolus(
        totalBolusAmount: Int,
        immediateBolusAmount: Int,
        durationInMinutes: Int,
        bolusType: ApplicationLayer.CMDDeliverBolusType
    ): Boolean =
        runPumpIOCall("deliver standard bolus", Mode.COMMAND) {

        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDDeliverBolusPacket(
                totalBolusAmount,
                immediateBolusAmount,
                durationInMinutes,
                bolusType
            ),
            ApplicationLayer.Command.CMD_DELIVER_BOLUS_RESPONSE
        )

        return@runPumpIOCall ApplicationLayer.parseCMDDeliverBolusResponsePacket(packet)
    }

    suspend fun cancelCMDStandardBolus(): Boolean = runPumpIOCall("cancel bolus", Mode.COMMAND) {
        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDCancelBolusPacket(ApplicationLayer.CMDImmediateBolusType.STANDARD),
            ApplicationLayer.Command.CMD_CANCEL_BOLUS_RESPONSE
        )

        return@runPumpIOCall ApplicationLayer.parseCMDCancelBolusResponsePacket(packet)
    }

    suspend fun sendShortRTButtonPress(buttons: List<ApplicationLayer.RTButton>) {
        require(buttons.isNotEmpty()) { "Cannot send short RT button press since the specified buttons list is empty" }
        check(currentLongRTPressJob == null) { "Cannot send short RT button press while a long RT button press is ongoing" }

        runPumpIOCall("send short RT button press", Mode.REMOTE_TERMINAL) {
            val buttonCodes = getCombinedButtonCodes(buttons)
            var delayBeforeNoButton = false
            var ignoreNoButtonError = false

            try {
                sendPacketWithoutResponse(ApplicationLayer.createRTButtonStatusPacket(buttonCodes, true))
                rtButtonConfirmationBarrier.receive()
            } catch (e: CancellationException) {
                delayBeforeNoButton = true
                ignoreNoButtonError = true
                throw e
            } catch (t: Throwable) {
                delayBeforeNoButton = true
                ignoreNoButtonError = true
                logger(LogLevel.ERROR) { "Error thrown during short RT button press: ${t.stackTraceToString()}" }
                throw t
            } finally {
                if (delayBeforeNoButton)
                    delay(TransportLayer.PACKET_SEND_INTERVAL_IN_MS)

                try {
                    sendPacketWithoutResponse(
                        ApplicationLayer.createRTButtonStatusPacket(ApplicationLayer.RTButton.NO_BUTTON.id, true)
                    )
                } catch (t: Throwable) {
                    if (ignoreNoButtonError) {
                        logger(LogLevel.DEBUG) {
                            "Ignoring error that was thrown while sending NO_BUTTON to end short button press; exception: $t"
                        }
                    } else {
                        logger(LogLevel.ERROR) {
                            "Error thrown while sending NO_BUTTON to end short button press; exception ${t.stackTraceToString()}"
                        }
                        throw t
                    }
                }
            }
        }
    }

    suspend fun sendShortRTButtonPress(button: ApplicationLayer.RTButton) =
        sendShortRTButtonPress(listOf(button))

    suspend fun startLongRTButtonPress(buttons: List<ApplicationLayer.RTButton>, keepGoing: (suspend () -> Boolean)? = null) {
        require(buttons.isNotEmpty()) { "Cannot start long RT button press since the specified buttons list is empty" }

        if (currentLongRTPressJob != null) {
            logger(LogLevel.DEBUG) { "Long RT button press job already running; ignoring redundant call" }
            return
        }

        runPumpIOCall("start long RT button press", Mode.REMOTE_TERMINAL) {
            try {
                issueLongRTButtonPressUpdate(buttons, keepGoing, pressing = true)
            } catch (t: Throwable) {
                stopLongRTButtonPress()
                throw t
            }
        }
    }

    suspend fun startLongRTButtonPress(button: ApplicationLayer.RTButton, keepGoing: (suspend () -> Boolean)? = null) =
        startLongRTButtonPress(listOf(button), keepGoing)

    suspend fun stopLongRTButtonPress() {
        if (currentLongRTPressJob == null) {
            logger(LogLevel.DEBUG) {
                "No long RT button press job running, and button press state is RELEASED; ignoring redundant call"
            }
            return
        }

        runPumpIOCall("stop long RT button press", Mode.REMOTE_TERMINAL) {
            issueLongRTButtonPressUpdate(listOf(ApplicationLayer.RTButton.NO_BUTTON), keepGoing = null, pressing = false)
        }
    }

    suspend fun waitForLongRTButtonPressToFinish() {
        currentLongRTPressJob?.await()
    }

    suspend fun switchMode(newMode: Mode, runHeartbeat: Boolean = true) = withContext(NonCancellable) {
        check(isIORunning()) { "Cannot switch mode because the pump is not connected" }

        if (_currentModeFlow.value == newMode)
            return@withContext

        try {
            logger(LogLevel.DEBUG) { "Switching mode from ${_currentModeFlow.value} to $newMode" }

            stopCMDPingHeartbeat()
            stopRTKeepAliveHeartbeat()

            onNewDisplayFrame(null)

            sendPacketMutex.withLock {
                withContext(NonCancellable) {
                    _currentModeFlow.value?.let { modeToDeactivate ->
                        logger(LogLevel.DEBUG) { "Deactivating current service" }
                        sendAppLayerPacket(
                            ApplicationLayer.createCTRLDeactivateServicePacket(
                                when (modeToDeactivate) {
                                    Mode.REMOTE_TERMINAL -> ApplicationLayer.ServiceID.RT_MODE
                                    Mode.COMMAND -> ApplicationLayer.ServiceID.COMMAND_MODE
                                }
                            )
                        )
                        logger(LogLevel.DEBUG) { "Sent CTRL_DEACTIVATE packet; waiting for CTRL_DEACTIVATE_SERVICE_RESPONSE packet" }
                        val receivedAppLayerPacket = transportLayerIO.receive(TransportLayer.Command.DATA).toAppLayerPacket()
                        if (receivedAppLayerPacket.command != ApplicationLayer.Command.CTRL_DEACTIVATE_SERVICE_RESPONSE) {
                            throw ApplicationLayer.IncorrectPacketException(
                                receivedAppLayerPacket,
                                ApplicationLayer.Command.CTRL_DEACTIVATE_SERVICE_RESPONSE
                            )
                        }
                    }

                    logger(LogLevel.DEBUG) { "Activating new service" }
                    sendAppLayerPacket(
                        ApplicationLayer.createCTRLActivateServicePacket(
                            when (newMode) {
                                Mode.REMOTE_TERMINAL -> ApplicationLayer.ServiceID.RT_MODE
                                Mode.COMMAND -> ApplicationLayer.ServiceID.COMMAND_MODE
                            }
                        )
                    )
                    logger(LogLevel.DEBUG) { "Sent CTRL_ACTIVATE packet; waiting for CTRL_ACTIVATE_SERVICE_RESPONSE packet" }
                    var receivedAppLayerPacket = transportLayerIO.receive(TransportLayer.Command.DATA).toAppLayerPacket()

                    if (receivedAppLayerPacket.command == ApplicationLayer.Command.CTRL_DEACTIVATE_SERVICE_RESPONSE) {
                        logger(LogLevel.INFO) {
                            "Got CTRL_DEACTIVATE_SERVICE_RESPONSE packet even though CTRL_ACTIVATE_SERVICE_RESPONSE was expected; " +
                            "suspected to be a Combo bug; trying to receive packet again as a workaround"
                        }
                        receivedAppLayerPacket = transportLayerIO.receive(TransportLayer.Command.DATA).toAppLayerPacket()
                    }

                    if (receivedAppLayerPacket.command != ApplicationLayer.Command.CTRL_ACTIVATE_SERVICE_RESPONSE) {
                        throw ApplicationLayer.IncorrectPacketException(
                            receivedAppLayerPacket,
                            ApplicationLayer.Command.CTRL_ACTIVATE_SERVICE_RESPONSE
                        )
                    }
                }
            }

            _currentModeFlow.value = newMode

            if (runHeartbeat) {
                logger(LogLevel.DEBUG) { "Resetting heartbeat" }
                when (newMode) {
                    Mode.COMMAND -> startCMDPingHeartbeat()
                    Mode.REMOTE_TERMINAL -> startRTKeepAliveHeartbeat()
                }
            }
        } catch (t: Throwable) {
            _connectionState.value = ConnectionState.FAILED
            throw t
        }
    }

    suspend fun runWithoutHeartbeat(block: (suspend () -> Unit)) {
        val mode = _currentModeFlow.value
        when (mode) {
            Mode.COMMAND -> stopCMDPingHeartbeat()
            Mode.REMOTE_TERMINAL -> stopRTKeepAliveHeartbeat()
            else -> Unit
        }

        block()

        when (mode) {
            Mode.COMMAND -> startCMDPingHeartbeat()
            Mode.REMOTE_TERMINAL -> startRTKeepAliveHeartbeat()
            else -> Unit
        }
    }

    /*************************************
     *** PRIVATE FUNCTIONS AND CLASSES ***
     *************************************/

    private fun isIORunning() = transportLayerIO.isIORunning()

    private fun newRtButtonConfirmationBarrier() =
        Channel<Boolean>(capacity = Channel.CONFLATED)

    private fun getCombinedButtonCodes(buttons: List<ApplicationLayer.RTButton>) =
        buttons.fold(0) { codes, button -> codes or button.id }

    private fun toString(buttons: List<ApplicationLayer.RTButton>) = buttons.joinToString(" ") { it.str }

    private suspend fun sendPacketWithResponse(
        tpLayerPacketInfo: TransportLayer.OutgoingPacketInfo,
        expectedResponseCommand: TransportLayer.Command? = null
    ): TransportLayer.Packet = sendPacketMutex.withLock {
        return withContext(NonCancellable) {
            transportLayerIO.send(tpLayerPacketInfo)
            transportLayerIO.receive(expectedResponseCommand)
        }
    }

    private suspend fun sendPacketWithResponse(
        appLayerPacketToSend: ApplicationLayer.Packet,
        expectedResponseCommand: ApplicationLayer.Command? = null,
        doRestartHeartbeat: Boolean = true
    ): ApplicationLayer.Packet = sendPacketMutex.withLock {
        return withContext(NonCancellable) {
            if (doRestartHeartbeat)
                restartHeartbeat()

            sendAppLayerPacket(appLayerPacketToSend)

            logger(LogLevel.VERBOSE) {
                if (expectedResponseCommand == null)
                    "Waiting for application layer packet (will arrive in a transport layer DATA packet)"
                else
                    "Waiting for application layer ${expectedResponseCommand.name} " +
                    "packet (will arrive in a transport layer DATA packet)"
            }

            val receivedAppLayerPacket = transportLayerIO.receive(TransportLayer.Command.DATA).toAppLayerPacket()

            if ((expectedResponseCommand != null) && (receivedAppLayerPacket.command != expectedResponseCommand))
                throw ApplicationLayer.IncorrectPacketException(receivedAppLayerPacket, expectedResponseCommand)

            receivedAppLayerPacket
        }
    }

    private suspend fun sendPacketWithoutResponse(
        tpLayerPacketInfo: TransportLayer.OutgoingPacketInfo
    ) = sendPacketMutex.withLock {
        withContext(NonCancellable) {
            transportLayerIO.send(tpLayerPacketInfo)
        }
    }

    private suspend fun sendPacketWithoutResponse(
        appLayerPacketToSend: ApplicationLayer.Packet,
        doRestartHeartbeat: Boolean = true
    ) = sendPacketMutex.withLock {
        withContext(NonCancellable) {
            if (doRestartHeartbeat)
                restartHeartbeat()
            sendAppLayerPacket(appLayerPacketToSend)
        }
    }

    private suspend fun sendAppLayerPacket(appLayerPacket: ApplicationLayer.Packet) {
        check(sendPacketMutex.isLocked)

        logger(LogLevel.VERBOSE) {
            "Sending application layer packet via transport layer:  $appLayerPacket"
        }

        val outgoingPacketInfo = appLayerPacket.toTransportLayerPacketInfo()

        if (appLayerPacket.command.serviceID == ApplicationLayer.ServiceID.RT_MODE) {
            if (outgoingPacketInfo.payload.size < (ApplicationLayer.PAYLOAD_BYTES_OFFSET + 2)) {
                throw ApplicationLayer.InvalidPayloadException(
                    appLayerPacket,
                    "Cannot send application layer RT packet since there's no room in the payload for the RT sequence number"
                )
            }

            logger(LogLevel.VERBOSE) { "Writing current RT sequence number $currentRTSequence into packet" }

            outgoingPacketInfo.payload[ApplicationLayer.PAYLOAD_BYTES_OFFSET + 0] =
                ((currentRTSequence shr 0) and 0xFF).toByte()
            outgoingPacketInfo.payload[ApplicationLayer.PAYLOAD_BYTES_OFFSET + 1] =
                ((currentRTSequence shr 8) and 0xFF).toByte()

            currentRTSequence++
            if (currentRTSequence > 65535)
                currentRTSequence = 0
        }

        transportLayerIO.send(outgoingPacketInfo)
    }

    private fun processReceivedPacket(tpLayerPacket: TransportLayer.Packet) =
        if (tpLayerPacket.command == TransportLayer.Command.DATA) {
            when (ApplicationLayer.extractAppLayerPacketCommand(tpLayerPacket)) {
                ApplicationLayer.Command.CTRL_ACTIVATE_SERVICE_RESPONSE -> {
                    logger(LogLevel.DEBUG) { "New service was activated; resetting RT sequence number" }
                    currentRTSequence = 0
                    TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET
                }

                ApplicationLayer.Command.RT_DISPLAY -> {
                    processRTDisplayPayload(
                        ApplicationLayer.parseRTDisplayPacket(tpLayerPacket.toAppLayerPacket())
                    )
                    rtButtonConfirmationBarrier.trySend(true)
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.RT_BUTTON_CONFIRMATION -> {
                    logger(LogLevel.VERBOSE) { "Got RT_BUTTON_CONFIRMATION packet from the Combo" }
                    rtButtonConfirmationBarrier.trySend(true)
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.RT_KEEP_ALIVE -> {
                    logger(LogLevel.VERBOSE) { "Got RT_KEEP_ALIVE packet from the Combo; ignoring" }
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.RT_AUDIO -> {
                    logger(LogLevel.VERBOSE) {
                        val audioType = ApplicationLayer.parseRTAudioPacket(tpLayerPacket.toAppLayerPacket())
                        "Got RT_AUDIO packet with audio type ${audioType.toHexString(8)}; ignoring"
                    }
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.RT_PAUSE,
                ApplicationLayer.Command.RT_RELEASE -> {
                    logger(LogLevel.VERBOSE) {
                        "Got ${ApplicationLayer.Command} packet with payload " +
                                "${tpLayerPacket.toAppLayerPacket().payload.toHexString()}; ignoring"
                    }
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.RT_VIBRATION -> {
                    logger(LogLevel.VERBOSE) {
                        val vibrationType = ApplicationLayer.parseRTVibrationPacket(
                            tpLayerPacket.toAppLayerPacket()
                        )
                        "Got RT_VIBRATION packet with vibration type ${vibrationType.toHexString(8)}; ignoring"
                    }
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.CTRL_SERVICE_ERROR -> {
                    val appLayerPacket = tpLayerPacket.toAppLayerPacket()
                    val ctrlServiceError = ApplicationLayer.parseCTRLServiceErrorPacket(appLayerPacket)
                    logger(LogLevel.ERROR) { "Got CTRL_SERVICE_ERROR packet from the Combo; throwing exception" }
                    throw ApplicationLayer.ServiceErrorException(appLayerPacket, ctrlServiceError)
                }

                else -> TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET
            }
        } else
            TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET

    private fun processRTDisplayPayload(rtDisplayPayload: ApplicationLayer.RTDisplayPayload) {
        try {
            val displayFrame = displayFrameAssembler.processRTDisplayPayload(
                rtDisplayPayload.index,
                rtDisplayPayload.row,
                rtDisplayPayload.rowBytes
            )
            if (displayFrame != null)
                onNewDisplayFrame(displayFrame)
        } catch (t: Throwable) {
            logger(LogLevel.ERROR) { "Could not process RT_DISPLAY payload: $t" }
            throw t
        }
    }

    private fun isCMDPingHeartbeatRunning() = (cmdPingHeartbeatJob != null)

    private fun startCMDPingHeartbeat() {
        if (isCMDPingHeartbeatRunning())
            return

        logger(LogLevel.VERBOSE) { "Starting background CMD ping heartbeat" }

        require(internalScope != null)

        cmdPingHeartbeatJob = internalScope!!.launch {
            while (true) {
                delay(1000)
                logger(LogLevel.VERBOSE) { "Transmitting CMD ping packet" }
                try {
                    sendPacketWithResponse(
                        ApplicationLayer.createCMDPingPacket(),
                        ApplicationLayer.Command.CMD_PING_RESPONSE,
                        doRestartHeartbeat = false
                    )
                } catch (e: CancellationException) {
                    cmdPingHeartbeatJob = null
                    throw e
                } catch (e: TransportLayer.PacketReceiverException) {
                    logger(LogLevel.ERROR) {
                        "Could not send CMD ping packet because packet receiver failed - stopping CMD ping heartbeat"
                    }
                    cmdPingHeartbeatJob = null
                    break
                } catch (t: Throwable) {
                    logger(LogLevel.ERROR) {
                        "Error caught when attempting to transmit CMD ping packet - stopping CMD ping heartbeat"
                    }
                    logger(LogLevel.ERROR) {
                        "Error: ${t.stackTraceToString()}"
                    }
                    cmdPingHeartbeatJob = null
                    break
                }
            }
        }
    }

    private suspend fun stopCMDPingHeartbeat() {
        if (!isCMDPingHeartbeatRunning())
            return

        logger(LogLevel.VERBOSE) { "Stopping background CMD ping heartbeat" }

        cmdPingHeartbeatJob?.cancelAndJoin()
        cmdPingHeartbeatJob = null

        logger(LogLevel.VERBOSE) { "Background CMD ping heartbeat stopped" }
    }

    private fun isRTKeepAliveHeartbeatRunning() = (rtKeepAliveHeartbeatJob != null)

    private fun startRTKeepAliveHeartbeat() {
        if (isRTKeepAliveHeartbeatRunning())
            return

        logger(LogLevel.VERBOSE) { "Starting background RT keep-alive heartbeat" }

        require(internalScope != null)

        rtKeepAliveHeartbeatJob = internalScope!!.launch {
            while (true) {
                delay(1000)
                logger(LogLevel.VERBOSE) { "Transmitting RT keep-alive packet" }
                try {
                    sendPacketWithoutResponse(
                        ApplicationLayer.createRTKeepAlivePacket(),
                        doRestartHeartbeat = false
                    )
                } catch (e: CancellationException) {
                    rtKeepAliveHeartbeatJob = null
                    throw e
                } catch (e: TransportLayer.PacketReceiverException) {
                    logger(LogLevel.ERROR) {
                        "Could not send RT keep-alive packet because packet receiver failed - stopping RT keep-alive heartbeat"
                    }
                    rtKeepAliveHeartbeatJob = null
                    break
                } catch (t: Throwable) {
                    logger(LogLevel.ERROR) {
                        "Error caught when attempting to transmit RT keep-alive packet - stopping RT keep-alive heartbeat"
                    }
                    logger(LogLevel.ERROR) {
                        "Error: ${t.stackTraceToString()}"
                    }
                    rtKeepAliveHeartbeatJob = null
                    break
                }
            }
        }
    }

    private suspend fun stopRTKeepAliveHeartbeat() {
        if (!isRTKeepAliveHeartbeatRunning())
            return

        logger(LogLevel.VERBOSE) { "Stopping background RT keep-alive heartbeat" }

        rtKeepAliveHeartbeatJob!!.cancelAndJoin()
        rtKeepAliveHeartbeatJob = null

        logger(LogLevel.VERBOSE) { "Background RT keep-alive heartbeat stopped" }
    }

    private suspend fun restartHeartbeat() {
        when (currentModeFlow.value) {
            Mode.REMOTE_TERMINAL -> {
                if (isRTKeepAliveHeartbeatRunning()) {
                    stopRTKeepAliveHeartbeat()
                    startRTKeepAliveHeartbeat()
                }
            }

            Mode.COMMAND -> {
                if (isCMDPingHeartbeatRunning()) {
                    stopCMDPingHeartbeat()
                    startCMDPingHeartbeat()
                }
            }

            null -> Unit
        }
    }

    private suspend fun issueLongRTButtonPressUpdate(
        buttons: List<ApplicationLayer.RTButton>,
        keepGoing: (suspend () -> Boolean)?,
        pressing: Boolean
    ) {
        if (!pressing) {
            logger(LogLevel.DEBUG) {
                "Releasing RTs button(s) ${toString(currentLongRTPressedButtons)}"
            }

            longRTPressLoopRunning = false
            currentLongRTPressJob?.await()

            return
        }

        currentLongRTPressedButtons = buttons
        val buttonCodes = getCombinedButtonCodes(buttons)
        longRTPressLoopRunning = true

        var delayBeforeNoButton = false
        var ignoreNoButtonError = false

        currentLongRTPressJob = internalScope!!.async {
            try {
                var buttonStatusChanged = true

                while (longRTPressLoopRunning) {
                    if (keepGoing != null) {
                        try {
                            if (!keepGoing()) {
                                logger(LogLevel.DEBUG) { "Aborting long RT button press flow" }
                                break
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger(LogLevel.DEBUG) { "keepGoing callback threw error: $t" }
                            throw t
                        }
                    }

                    rtButtonConfirmationBarrier.tryReceive()

                    logger(LogLevel.DEBUG) {
                        "Sending long RT button press; button(s) = ${toString(buttons)} status changed = $buttonStatusChanged"
                    }

                    sendPacketWithoutResponse(
                        ApplicationLayer.createRTButtonStatusPacket(buttonCodes, buttonStatusChanged)
                    )

                    logger(LogLevel.DEBUG) { "Waiting for button confirmation" }
                    val canContinue = rtButtonConfirmationBarrier.receive()
                    logger(LogLevel.DEBUG) { "Got button confirmation; canContinue = $canContinue" }

                    if (!canContinue)
                        break

                    buttonStatusChanged = false
                }
            } catch (e: CancellationException) {
                delayBeforeNoButton = true
                ignoreNoButtonError = true
                throw e
            } catch (t: Throwable) {
                delayBeforeNoButton = true
                ignoreNoButtonError = true
                logger(LogLevel.ERROR) { "Error thrown during long RT button press: ${t.stackTraceToString()}" }
                throw t
            } finally {
                logger(LogLevel.DEBUG) { "Ending long RT button press by sending NO_BUTTON" }
                try {
                    withContext(NonCancellable) {
                        if (delayBeforeNoButton)
                            delay(200L)

                        sendPacketWithoutResponse(
                            ApplicationLayer.createRTButtonStatusPacket(
                                ApplicationLayer.RTButton.NO_BUTTON.id,
                                buttonStatusChanged = true
                            )
                        )
                    }
                } catch (t: Throwable) {
                    if (ignoreNoButtonError) {
                        logger(LogLevel.DEBUG) {
                            "Ignoring error that was thrown while sending NO_BUTTON to end long button press; exception: $t"
                        }
                    } else {
                        logger(LogLevel.ERROR) {
                            "Error thrown while sending NO_BUTTON to end long button press; exception ${t.stackTraceToString()}"
                        }
                        throw t
                    }
                }

                currentLongRTPressJob = null
            }
        }
    }

    private data class KeyResponseInfo(val pumpClientCipher: Cipher, val clientPumpCipher: Cipher, val keyResponseAddress: Byte)

    private fun processKeyResponsePacket(packet: TransportLayer.Packet, weakCipher: Cipher): KeyResponseInfo {
        if (packet.payload.size != (CIPHER_KEY_SIZE * 2))
            throw TransportLayer.InvalidPayloadException(packet, "Expected ${CIPHER_KEY_SIZE * 2} bytes, got ${packet.payload.size}")

        val encryptedPCKey = ByteArray(CIPHER_KEY_SIZE)
        val encryptedCPKey = ByteArray(CIPHER_KEY_SIZE)

        for (i in 0 until CIPHER_KEY_SIZE) {
            encryptedPCKey[i] = packet.payload[i + 0]
            encryptedCPKey[i] = packet.payload[i + CIPHER_KEY_SIZE]
        }

        val pumpClientCipher = Cipher(weakCipher.decrypt(encryptedPCKey))
        val clientPumpCipher = Cipher(weakCipher.decrypt(encryptedCPKey))

        val addressInt = packet.address.toPosInt()
        val sourceAddress = addressInt and 0xF
        val destinationAddress = (addressInt shr 4) and 0xF
        val keyResponseAddress = ((sourceAddress shl 4) or destinationAddress).toByte()

        return KeyResponseInfo(
            pumpClientCipher = pumpClientCipher,
            clientPumpCipher = clientPumpCipher,
            keyResponseAddress = keyResponseAddress
        )
    }

    private fun processIDResponsePacket(packet: TransportLayer.Packet): String {
        if (packet.payload.size != 17)
            throw TransportLayer.InvalidPayloadException(packet, "Expected 17 bytes, got ${packet.payload.size}")

        val serverID = ((packet.payload[0].toPosLong() shl 0) or
                (packet.payload[1].toPosLong() shl 8) or
                (packet.payload[2].toPosLong() shl 16) or
                (packet.payload[3].toPosLong() shl 24))

        val pumpIDStrBuilder = StringBuilder()
        for (i in 0 until 13) {
            val pumpIDByte = packet.payload[4 + i]
            if (pumpIDByte == 0.toByte()) break
            else pumpIDStrBuilder.append(pumpIDByte.toInt().toChar())
        }
        val pumpID = pumpIDStrBuilder.toString()

        logger(LogLevel.DEBUG) {
            "Received IDs: server ID: $serverID pump ID: $pumpID"
        }

        return pumpID
    }

    private suspend fun <T> runPumpIOCall(
        commandDesc: String,
        expectedMode: Mode,
        block: suspend () -> T
    ): T {
        check(isIORunning()) {
            "Cannot $commandDesc because the pump is not connected"
        }
        check(_currentModeFlow.value == expectedMode) {
            "Cannot $commandDesc while being in ${_currentModeFlow.value} mode"
        }

        try {
            return block()
        } catch (t: Throwable) {
            _connectionState.value = ConnectionState.FAILED
            throw t
        }
    }

    private suspend fun disconnectBTDeviceAndCatchExceptions() {
        try {
            withContext(bluetoothDevice.ioDispatcher + NonCancellable) {
                bluetoothDevice.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger(LogLevel.ERROR) {
                "Error occurred during Bluetooth device disconnect; not propagating; error: $t"
            }
        }
    }
}
