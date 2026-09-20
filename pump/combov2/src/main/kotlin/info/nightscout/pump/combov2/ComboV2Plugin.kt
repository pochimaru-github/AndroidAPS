package info.nightscout.pump.combov2

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import info.nightscout.comboctl.android.AndroidBluetoothInterface
import info.nightscout.comboctl.base.BasicProgressStage
import info.nightscout.comboctl.base.BluetoothException
import info.nightscout.comboctl.base.BluetoothNotAvailableException
import info.nightscout.comboctl.base.BluetoothNotEnabledException
import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.NullDisplayFrame
import info.nightscout.comboctl.base.PairingPIN
import info.nightscout.comboctl.main.BasalProfile
import info.nightscout.comboctl.main.QuantityNotChangingException
import info.nightscout.pump.combov2.activities.ComboV2PairingActivity
import info.nightscout.pump.combov2.keys.ComboBooleanKey
import info.nightscout.pump.combov2.keys.ComboIntKey
import info.nightscout.pump.combov2.keys.ComboIntNonKey
import info.nightscout.pump.combov2.keys.ComboIntentKey
import info.nightscout.pump.combov2.keys.ComboLongNonKey
import info.nightscout.pump.combov2.keys.ComboStringNonKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import info.nightscout.comboctl.base.BluetoothAddress as ComboCtlBluetoothAddress
import info.nightscout.comboctl.base.LogLevel as ComboCtlLogLevel
import info.nightscout.comboctl.base.Logger as ComboCtlLogger
import info.nightscout.comboctl.base.Tbr as ComboCtlTbr
import info.nightscout.comboctl.main.Pump as ComboCtlPump
import info.nightscout.comboctl.main.PumpManager as ComboCtlPumpManager

// 修正前
// import info.nightscout.comboctl.main.PumpStatus
// import info.nightscout.comboctl.main.AlertScreen
// 修正後
import info.nightscout.comboctl.main.AlertScreenException

//import info.nightscout.comboctl.main.CommandDescription

internal const val PUMP_ERROR_TIMEOUT_INTERVAL_MSECS = 1000L * 60 * 5

@Singleton
class ComboV2Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val context: Context,
    private val rxBus: RxBus,
    private val constraintChecker: ConstraintsChecker,
    sp: SP,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    private val androidPermission: AndroidPermission,
    private val config: Config,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) :
    PumpPluginBase(
        pluginDescription = PluginDescription()
            .mainType(PluginType.PUMP)
            .fragmentClass(ComboV2Fragment::class.java.name)
            .pluginIcon(R.drawable.ic_combov2)
            .pluginName(R.string.combov2_plugin_name)
            .shortName(R.string.combov2_plugin_shortname)
            .description(R.string.combov2_plugin_description)
            .preferencesId(PluginDescription.PREFERENCE_SCREEN),
        ownPreferences = listOf(
            ComboIntentKey::class.java, ComboIntKey::class.java, ComboBooleanKey::class.java,
            ComboStringNonKey::class.java, ComboIntNonKey::class.java, ComboLongNonKey::class.java
        ),
        aapsLogger, rh, preferences, commandQueue
    ), Pump, PluginConstraints {
        
    // Coroutine scope and the associated job. All coroutines
    // that are started in this plugin are part of this scope.
    private var pumpCoroutineScopeJob = SupervisorJob()
    private var pumpCoroutineScope = CoroutineScope(Dispatchers.Default + pumpCoroutineScopeJob)

    private val _pumpDescription = PumpDescription()

    private val pumpStateStore = AAPSPumpStateStore(sp)
    private var pumpStateBackup: AAPSPumpStateStore.StatesBackup? = null

    // These are initialized in onStart() and torn down in onStop().
    private var bluetoothInterface: AndroidBluetoothInterface? = null
    private var pumpManager: ComboCtlPumpManager? = null
    private var initializationChangedEventSent = false

    // These are initialized in connect() and torn down in disconnect().
    private var pump: ComboCtlPump? = null
    private var connectionSetupJob: Job? = null
    private var stateAndStatusFlowsDeferred: Deferred<Unit>? = null
    private var pumpUIFlowsDeferred: Deferred<Unit>? = null

    // States for the Pump interface and for the UI.
    // private var pumpStatus: PumpStatus? = null
    private var lastConnectionTimestamp = 0L
// 修正前
// private var lastComboAlert: AlertScreen.Content? = null
// 修正後
private var lastComboAlert: Any? = null

    // States for when the pump reports an error. We then want isInitialized()
    // to return false until either the user presses the Refresh button or the
    // pumpErrorTimeoutJob expires. That way, the loop won't run until then,
    // giving the user a chance to handle the error.
    private var pumpErrorObserved = false
    private var pumpErrorTimeoutJob: Job? = null

    // Set to true if a disconnect request came in while the driver
    // was in the Connecting, CheckingPump, or ExecutingCommand
    // state (in other words, while isBusy() was returning true).
    private var disconnectRequestPending = false

    // Set to true in when unpair() starts and back to false in the
    // pumpManager onPumpUnpaired callback. This fixes a race condition
    // that can happen if the user unpairs the pump while AndroidAPS
    // is calling connect().
    private var unpairing = false

    // The current driver state. We use a StateFlow here to
    // allow other components to react to state changes.
        private val _driverStateFlow = MutableStateFlow<DriverState>(DriverState.Disconnected)
        val driverStateFlow: StateFlow<DriverState> = _driverStateFlow.asStateFlow()

        private val _pairedStateFlow = MutableStateFlow<Boolean>(false)
        val pairedStateFlow: StateFlow<Boolean> = _pairedStateFlow.asStateFlow()

        private val _driverStateUIFlow = MutableStateFlow<DriverState>(DriverState.Disconnected)
        val driverStateUIFlow = _driverStateUIFlow.asStateFlow()

        data class CurrentActivityInfo(val description: String, val overallProgress: Double)

        private fun noCurrentActivity() = CurrentActivityInfo("", 0.0)
        private var _currentActivityUIFlow = MutableStateFlow(noCurrentActivity())
        val currentActivityUIFlow = _currentActivityUIFlow.asStateFlow()

        private var _lastConnectionTimestampUIFlow = MutableStateFlow<Long?>(null)
        val lastConnectionTimestampUIFlow = _lastConnectionTimestampUIFlow.asStateFlow()

        private var _batteryStateUIFlow = MutableStateFlow<BatteryState?>(null)
        val batteryStateUIFlow = _batteryStateUIFlow.asStateFlow()

        data class ReservoirLevel(val state: ReservoirState, val availableUnits: Int)

        private var _reservoirLevelUIFlow = MutableStateFlow<ReservoirLevel?>(null)
        val reservoirLevelUIFlow = _reservoirLevelUIFlow.asStateFlow()

        private var _lastBolusUIFlow = MutableStateFlow<Any?>(null)
        val lastBolusUIFlow = _lastBolusUIFlow.asStateFlow()
        
    // If true, the pump was found to be suspended during the connect()
    // call. This is separate from driverStateFlow and driverStateUIFlow.
    // It is set immediately after connect() (while the other two may be
    // set in a separate coroutine), which is important for check
    // inside connect() and checks before commands like deliverTreatment()
    // are run. This is what drives the isSuspended() call, and is _not_
    // to be used for UI update purposes (use driverStateUIFlow for that).
    // Like driverStateUIFlow, this state persists even after disconnecting
    // from the pump. This is necessary, because AAPS may call isSuspended()
    // even when the pump is not connected.
    private var pumpIsSuspended = false

    // The basal profile that is set to be the pump's current profile.
    // If the pump's actual basal profile deviates from this, it is
    // overwritten. This check is performed in checkBasalProfile().
    // In setNewBasalProfile(), this value is changed.
    private var activeBasalProfile: BasalProfile? = null

    // This is used for checking that the correct basal profile is
    // active in the Combo. If not, loop invocation is disallowed.
    // This is _not_ reset by disconnect(). That's on purpose; it
    // is read by isLoopInvocationAllowed(), which is called even
    // if the pump is not connected.
    private var lastActiveBasalProfileNumber: Int? = null

    private var bolusJob: Job? = null

    /*** Public functions and base class & interface overrides ***/

// ============================================================================
// DriverState 定義および ComboCtlPump.State 相互変換ヘルパー
// ============================================================================

sealed class DriverState(val name: String) {
    object Disconnected : DriverState("disconnected")
    object Connecting : DriverState("connecting")
    object Connected : DriverState("connected")

    /* TODO: Step 2 - CommandDescriptionの現行構造適合時に元の型を復元
    class ExecutingCommand(val description: ComboCtlPump.CommandDescription) : DriverState("executingCommand")
    */
    class ExecutingCommand(val description: String? = null) : DriverState("executingCommand")

    object Error : DriverState("error")
}

/**
 * ComboCtlPump.State から DriverState への変換ロジック
 */
fun ComboCtlPump.State.toDriverState(commandDescription: String? = null): DriverState = when (this) {
    ComboCtlPump.State.DISCONNECTED -> DriverState.Disconnected
    ComboCtlPump.State.CONNECTING,
    ComboCtlPump.State.CHECKING_PUMP -> DriverState.Connecting
    ComboCtlPump.State.READY_FOR_COMMANDS,
    ComboCtlPump.State.SUSPENDED -> DriverState.Connected
    ComboCtlPump.State.EXECUTING_COMMAND -> DriverState.ExecutingCommand(commandDescription)
    ComboCtlPump.State.ERROR -> DriverState.Error
}

/**
 * 切断状態判定プロパティ・関数
 */
val DriverState.isDisconnected: Boolean
    get() = this is DriverState.Disconnected

val DriverState.isConnected: Boolean
    get() = this is DriverState.Connected || this is DriverState.ExecutingCommand

    private val driverStateFlow = _driverStateFlow.asStateFlow()

    // Used by ComboV2PairingActivity to launch its own
    // custom activities that have a result.
    var customDiscoveryActivityStartCallback: ((intent: Intent) -> Unit)?
        set(value) {
            bluetoothInterface?.customDiscoveryActivityStartCallback = value
        }
        get() = bluetoothInterface?.customDiscoveryActivityStartCallback

    init {
        ComboCtlLogger.backend = AAPSComboCtlLogger(aapsLogger)
        _pumpDescription.fillFor(PumpType.ACCU_CHEK_COMBO)
    }

    override fun onStart() {
        aapsLogger.info(LTag.PUMP, "Starting combov2 driver")

        super.onStart()

        updateComboCtlLogLevel()

        aapsLogger.debug(LTag.PUMP, "Creating bluetooth interface")
        val newBluetoothInterface = AndroidBluetoothInterface(context)
        bluetoothInterface = newBluetoothInterface

        aapsLogger.info(LTag.PUMP, "Continuing combov2 driver start in coroutine")

        // Continue initialization in a separate coroutine. This allows us to call
        // runWithPermissionCheck(), which will keep trying to run the code block
        // until either the necessary Bluetooth permissions are granted, or the
        // coroutine is cancelled (see onStop() below).
        pumpCoroutineScope.launch {
            try {
                runWithPermissionCheck(
                    context, config, aapsLogger, androidPermission,
                    permissionsToCheckFor = listOf("android.permission.BLUETOOTH_CONNECT")
                ) {
                    aapsLogger.debug(LTag.PUMP, "Setting up bluetooth interface")

                    try {
                        newBluetoothInterface.setup()

                        rxBus.send(EventDismissNotification(Notification.BLUETOOTH_NOT_ENABLED))

                        aapsLogger.debug(LTag.PUMP, "Setting up pump manager")
                        val newPumpManager = ComboCtlPumpManager(newBluetoothInterface, pumpStateStore)
                        newPumpManager.setup {
                            _pairedStateUIFlow.value = false
                            unpairing = false
                        }

                        // UI flows that must have defined values right
                        // at start are initialized here.

                        // The paired state UI flow is special in that it is also
                        // used as the backing store for the isPaired() function,
                        // so setting up that UI state flow equals updating that
                        // paired state.
                        val paired = newPumpManager.getPairedPumpAddresses().isNotEmpty()
                        _pairedStateUIFlow.value = paired

                        pumpManager = newPumpManager
                    } catch (_: BluetoothNotAvailableException) {
                        uiInteraction.addNotification(
                            Notification.BLUETOOTH_NOT_SUPPORTED,
                            text = rh.gs(R.string.combov2_bluetooth_not_supported),
                            level = Notification.URGENT
                        )

                        // Deliberately _not_ setting the driver state here before
                        // exiting this scope. We are essentially aborting the start
                        // since Bluetooth is not supported by the hardware, so the
                        // driver cannot do anything, and therefore cannot leave the
                        // DriverState.NotInitialized state.
                        aapsLogger.error(LTag.PUMP, "combov2 driver start cannot be completed since the hardware does not support Bluetooth")
                        return@runWithPermissionCheck
                    } catch (_: BluetoothNotEnabledException) {
                        uiInteraction.addNotification(
                            Notification.BLUETOOTH_NOT_ENABLED,
                            text = rh.gs(R.string.combov2_bluetooth_disabled),
                            level = Notification.INFO
                        )

                        // If the user currently has Bluetooth disabled, retry until
                        // the user turns it on. AAPS will automatically show a dialog
                        // box which requests the user to enable Bluetooth. Upon
                        // catching this exception, runWithPermissionCheck() will wait
                        // a bit before retrying, so no delay() call is needed here.
                        throw RetryPermissionCheckException()
                    }

                    setDriverState(DriverState.Disconnected)

                    aapsLogger.info(LTag.PUMP, "combov2 driver start complete")

                    // NOTE: EventInitializationChanged is sent in getPumpStatus() .
                }
            } catch (e: CancellationException) {
                aapsLogger.info(LTag.PUMP, "combov2 driver start cancelled")
                throw e
            }
        }
    }

    override fun onStop() {
        aapsLogger.info(LTag.PUMP, "Stopping combov2 driver")

        runBlocking {
            // Cancel any ongoing background coroutines. This includes an ongoing
            // unfinished initialization that still waits for the user to grant
            // Bluetooth permissions. Also join to wait for the coroutines to
            // finish. Otherwise, race conditions can occur, for example, when
            // a coroutine tries to access bluetoothInterface right after it
            // was torn down below.
            pumpCoroutineScopeJob.cancelAndJoin()

            // Normally this should not happen, but to be safe,
            // make sure any running pump instance is disconnected.
            pump?.disconnect()
            pump = null
        }

        pumpManager = null
        bluetoothInterface?.teardown()
        bluetoothInterface = null

        // Set this flag to false here in case an ongoing pairing attempt
        // is somehow aborted inside the interface without the onPumpUnpaired
        // callback being invoked.
        unpairing = false

        setDriverState(DriverState.Disconnected)

        rxBus.send(EventInitializationChanged())
        initializationChangedEventSent = false

        // The old job and scope were completed. We need new ones.
        pumpCoroutineScopeJob = SupervisorJob()
        pumpCoroutineScope = CoroutineScope(Dispatchers.Default + pumpCoroutineScopeJob)

        super.onStop()

        aapsLogger.info(LTag.PUMP, "combov2 driver stopped")
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)

        // Setup coroutine to enable/disable the pair and unpair
        // preferences depending on the pairing state.
        preferenceFragment.run {
            // We use the fragment's lifecycle instead of the fragment view's, since the latter
            // is initialized in onCreateView(), and we reach this point here _before_ that
            // method is called. In other words, the fragment view does not exist at this point.
            // repeatOnLifecycle() is a utility function that runs its block when the lifecycle
            // starts. If the fragment is destroyed, the code inside - that is, the flow - is
            // cancelled. That way, the UI flow is automatically reconstructed when Android
            // recreates the fragment.
            lifecycle.coroutineScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val pairPref: Preference? = findPreference(ComboIntentKey.PairWithPump.key)
                    val unpairPref: Preference? = findPreference(ComboIntentKey.UnpairPump.key)

                    val isInitiallyPaired = pairedStateUIFlow.value
                    pairPref?.isEnabled = !isInitiallyPaired
                    unpairPref?.isEnabled = isInitiallyPaired

                    pairedStateUIFlow
                        .onEach { isPaired ->
                            pairPref?.isEnabled = !isPaired
                            unpairPref?.isEnabled = isPaired
                        }
                        .launchIn(this)
                }
            }
        }
    }

    override fun isInitialized(): Boolean =
        isPaired() && (driverStateFlow.value != DriverState.Disconnected) && !pumpErrorObserved

    override fun isSuspended(): Boolean = pumpIsSuspended

    override fun isBusy(): Boolean =
        when (driverStateFlow.value) {
            // DriverState.Connecting is _not_ listed here. Even though the pump
            // is technically busy and unable to execute commands in that state,
            // returning true then causes problems with AAPS' KeepAlive mechanism.
            // DriverState.CheckingPump, // Step 1: 廃止APIのため無効化
            // is DriverState.ExecutingCommand -> true // Step 1: 廃止APIのため無効化

            else                            -> false
        }

    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Connecting to Combo; reason: $reason")

        if (unpairing) {
            aapsLogger.debug(LTag.PUMP, "Aborting connect attempt since we are currently unpairing")
            return
        }

        if (pumpErrorObserved) {
            aapsLogger.debug(LTag.PUMP, "Aborting connect attempt since the pumpErrorObserved flag is set")
            uiInteraction.addNotification(
                Notification.COMBO_PUMP_ALARM,
                text = rh.gs(R.string.combov2_cannot_connect_pump_error_observed),
                level = Notification.NORMAL
            )
            return
        }

        when (driverStateFlow.value) {
            DriverState.Connecting,
            DriverState.Error -> {
                aapsLogger.debug(
                    LTag.PUMP,
                    "Cannot connect while driver is in the ${driverStateFlow.value} state"
                )
                return
            }

            else              -> Unit
        }

        if (!isPaired()) {
            aapsLogger.debug(LTag.PUMP, "Cannot connect since no Combo has been paired")
            return
        }

        assert(pump == null)

        lastComboAlert = null

        val bluetoothAddress = when (val address = getBluetoothAddress()) {
            null -> {
                aapsLogger.error(LTag.PUMP, "No Bluetooth address stored - pump state store may be corrupted")
                unpairDueToPumpDataError()
                return
            }

            else -> address
        }

        try {
            val curPumpManager = pumpManager ?: throw Error("Could not get pump manager; this should not happen. Please report this as a bug.")

            val acquiredPump = 
                curPumpManager.acquirePump(bluetoothAddress, activeBasalProfile) { event -> handlePumpEvent(event) }

            pump = acquiredPump

            _bluetoothAddressUIFlow.value = bluetoothAddress.toString()
            _serialNumberUIFlow.value = curPumpManager.getPumpID(bluetoothAddress)

            rxBus.send(EventDismissNotification(Notification.BLUETOOTH_NOT_ENABLED))

            // Erase any display frame that may be left over from a previous connection.
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _displayFrameUIFlow.resetReplayCache()

            stateAndStatusFlowsDeferred = pumpCoroutineScope.async {
                coroutineScope {
                    acquiredPump.stateFlow
                        .onEach { pumpState ->
                            val driverState = when (pumpState) {
                                ComboCtlPump.State.DISCONNECTED        -> return@onEach
                                ComboCtlPump.State.CONNECTING          -> DriverState.Connecting
                                ComboCtlPump.State.CHECKING_PUMP        -> DriverState.Connecting
                                ComboCtlPump.State.READY_FOR_COMMANDS    -> DriverState.Connecting
                                ComboCtlPump.State.EXECUTING_COMMAND -> DriverState.Connecting
                                ComboCtlPump.State.SUSPENDED           -> DriverState.Connecting
                                ComboCtlPump.State.ERROR               -> DriverState.Error
                            }
                            setDriverState(driverState)
                        }
                        .launchIn(this)
                }
            }

            setupUiFlows(acquiredPump)

            disconnectRequestPending = false
            setDriverState(DriverState.Connecting)

            connectionSetupJob = pumpCoroutineScope.launch {
                var forciblyDisconnectDueToError = false

                try {
                    runWithPermissionCheck(
                        context, config, aapsLogger, androidPermission,
                        permissionsToCheckFor = listOf("android.permission.BLUETOOTH_CONNECT")
                    ) {
                        pump?.connect()
                    }

                    pump?.let {
                        pumpIsSuspended = when (it.stateFlow.value) {
                            ComboCtlPump.State.SUSPENDED,
                            ComboCtlPump.State.ERROR -> true

                            else                        -> false
                        }

                        aapsLogger.debug(LTag.PUMP, "Pump is suspended: $pumpIsSuspended")

                        if (!isSuspended()) {
                            val activeBasalProfileNumber: Int? = null
                            aapsLogger.debug(LTag.PUMP, "Active basal profile number: $activeBasalProfileNumber")
                            if ((activeBasalProfileNumber != null) && (activeBasalProfileNumber != 1)) {
                                uiInteraction.addNotification(
                                    Notification.COMBO_PUMP_ALARM,
                                    text = rh.gs(R.string.combov2_incorrect_active_basal_profile, activeBasalProfileNumber),
                                    level = Notification.URGENT
                                )
                            }
                            lastActiveBasalProfileNumber = activeBasalProfileNumber
                        }

                        if (activeBasalProfile == null) {
                            aapsLogger.debug(
                                LTag.PUMP,
                                "No basal profile specified by pump queue (yet); using the basal profile that got read from the pump"
                            )
                        }
                        updateBaseBasalRateUI()
                    }
                } catch (e: CancellationException) {
                    disconnectRequestPending = false
                    setDriverState(DriverState.Disconnected)
                    throw e
                } catch (e: AlertScreenException) {
                    notifyAboutComboAlert("")
                    forciblyDisconnectDueToError = true
                } catch (e: Exception) {
                    uiInteraction.addNotification(
                        Notification.COMBO_PUMP_ALARM,
                        text = rh.gs(R.string.combov2_connection_error, e.message),
                        level = Notification.URGENT
                    )

                    aapsLogger.error(LTag.PUMP, "Exception while connecting: ${e.stackTraceToString()}")

                    forciblyDisconnectDueToError = true
                }

                if (forciblyDisconnectDueToError) {
                    connectionSetupJob = null
                    disconnectInternal(forceDisconnect = true)

                    ToastUtils.showToastInUiThread(context, rh.gs(R.string.combov2_could_not_connect))
                } else {
                    connectionSetupJob = null
                    executePendingDisconnect()
                }
            }
        } catch (_: BluetoothNotEnabledException) {
            uiInteraction.addNotification(
                Notification.BLUETOOTH_NOT_ENABLED,
                text = rh.gs(R.string.combov2_bluetooth_disabled),
                level = Notification.INFO
            )
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Connection failure: $e")
            ToastUtils.showToastInUiThread(context, rh.gs(R.string.combov2_could_not_connect))
            disconnectInternal(forceDisconnect = true)
        }
    }

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Disconnecting from Combo; reason: $reason")
        disconnectInternal(forceDisconnect = false)
    }

    // This is called when (a) the AAPS watchdog is about to toggle
    // Bluetooth (if permission is given by the user) and (b) when
    // the command queue is being emptied. In both cases, the
    // connection attempt must be stopped immediately, which is why
    // forceDisconnect is set to true.
    override fun stopConnecting() {
        aapsLogger.debug(LTag.PUMP, "Stopping connect attempt by (forcibly) disconnecting")
        disconnectInternal(forceDisconnect = true)
    }

    override fun getPumpStatus(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Getting pump status; reason: $reason")

        lastComboAlert = null


            try {
                executeCommand {
                    // ステータス更新は ComboCtlPump.State / Event 経由で自動同調されるため空処理
                }

                // We send this event here, and not in onStart(), to include
                // the initial pump status update before emitting the event.
                if (!initializationChangedEventSent) {
                    rxBus.send(EventInitializationChanged())
                    initializationChangedEventSent = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // State and status are automatically updated via the associated flows.
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        if (!isInitialized()) {
            aapsLogger.error(LTag.PUMP, "Cannot set profile since driver is not initialized")

            uiInteraction.addNotification(
                Notification.PROFILE_NOT_SET_NOT_INITIALIZED,
                rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set),
                Notification.URGENT
            )

            return pumpEnactResultProvider.get().apply {
                success = false
                enacted = false
                comment = rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            }
        }

        val acquiredPump = getAcquiredPump()

        rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
        rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))

        val pumpEnactResult = pumpEnactResultProvider.get()

        val requestedBasalProfile = profile.toComboCtlBasalProfile()
        aapsLogger.debug(LTag.PUMP, "Basal profile to set: $requestedBasalProfile")

        
            try {
                executeCommand {
                    acquiredPump.setBasalProfile(requestedBasalProfile)
                    aapsLogger.debug(LTag.PUMP, "Basal profiles are different; new profile set")
                    activeBasalProfile = requestedBasalProfile
                    updateBaseBasalRateUI()

                    uiInteraction.addNotificationValidFor(
                        Notification.PROFILE_SET_OK,
                        rh.gs(app.aaps.core.ui.R.string.profile_set_ok),
                        Notification.INFO,
                        60
                    )

                    pumpEnactResult.apply {
                        success = true
                        enacted = true
                    }
                }
            } catch (e: CancellationException) {
                // Cancellation is not an error, but it also means
                // that the profile update was not enacted.
                pumpEnactResult.apply {
                    success = true
                    enacted = false
                }
                throw e
            } catch (e: Exception) {
                aapsLogger.error("Exception thrown during basal profile update: $e")

                uiInteraction.addNotification(
                    Notification.FAILED_UPDATE_PROFILE,
                    rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile),
                    Notification.URGENT
                )

                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile)
                }
            }
        return pumpEnactResult
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        if (!isInitialized())
            return true

        return (activeBasalProfile == profile.toComboCtlBasalProfile())
    }

    override val lastDataTime: Long get() = lastConnectionTimestamp

    @OptIn(ExperimentalTime::class)
    override val lastBolusTime: Long? get() = null // Step 1: 廃止API (lastBolusUIFlow.value?.timestamp) のためスタブ化
    override val lastBolusAmount: Double? get() = null // Step 1: 廃止API (lastBolusUIFlow.value?.bolusAmount) のためスタブ化
    override val baseBasalRate: Double
        get() {
            val currentHour = DateTime().hourOfDay().get()
            return activeBasalProfile?.get(currentHour)?.cctlBasalToIU() ?: 0.0
        }

    // Store the levels as plain properties. That way, the last reported
    // levels are shown on the UI even when the driver connects to the
    // pump again and resets the current pump state.

    private var _reservoirLevel: Double? = null
    override val reservoirLevel: Double
        get() = _reservoirLevel ?: 0.0

    private var _batteryLevel: Int? = null
    override val batteryLevel: Int?
        get() = _batteryLevel

    private fun updateLevels() {
        /* Step 1: 廃止API (pumpStatus) のためリザバーおよびバッテリー自動記録処理を一時無効化
        pumpStatus?.availableUnitsInReservoir?.let { newLevel ->
            _reservoirLevel?.let { currentLevel ->
                aapsLogger.debug(LTag.PUMP, "Current/new reservoir levels: $currentLevel / $newLevel")
                if (preferences.get(ComboBooleanKey.AutomaticReservoirEntry) && (newLevel > currentLevel)) {
                    aapsLogger.debug(LTag.PUMP, "Auto-inserting reservoir change therapy event")
                    pumpSync.insertTherapyEventIfNewWithTimestamp(
                        timestamp = System.currentTimeMillis(),
                        type = TE.Type.INSULIN_CHANGE,
                        pumpId = null,
                        pumpType = PumpType.ACCU_CHEK_COMBO,
                        pumpSerial = serialNumber()
                    )
                }
            }
            _reservoirLevel = newLevel.toDouble()
        }

        pumpStatus?.batteryState?.let { newState ->
            val newLevel = when (newState) {
                PumpStatus.BatteryState.NO_BATTERY   -> 5
                PumpStatus.BatteryState.LOW_BATTERY  -> 25
                PumpStatus.BatteryState.FULL_BATTERY -> 100
            }

            _batteryLevel?.let { currentLevel ->
                aapsLogger.debug(LTag.PUMP, "Current/new battery levels: $currentLevel / $newLevel")
                if (preferences.get(ComboBooleanKey.AutomaticBatteryEntry) && (newLevel > currentLevel)) {
                    aapsLogger.debug(LTag.PUMP, "Auto-inserting battery change therapy event")
                    pumpSync.insertTherapyEventIfNewWithTimestamp(
                        timestamp = System.currentTimeMillis(),
                        type = TE.Type.PUMP_BATTERY_CHANGE,
                        pumpId = null,
                        pumpType = PumpType.ACCU_CHEK_COMBO,
                        pumpSerial = serialNumber()
                    )
                }
            }

            _batteryLevel = newLevel
        }
        */
    }

override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        val oldInsulinAmount = detailedBolusInfo.insulin
        detailedBolusInfo.insulin = constraintChecker
            .applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger))
            .value()
        aapsLogger.debug(
            LTag.PUMP,
            "Applied bolus constraints:  old insulin amount: $oldInsulinAmount  new:${detailedBolusInfo.insulin}"
        )

        val pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.success = false

        if (isSuspended()) {
            aapsLogger.info(LTag.PUMP, "Cannot deliver bolus since the pump is suspended")
            pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.combov2_cannot_deliver_pump_suspended)
            }
            return pumpEnactResult
        }

        val acquiredPump = getAcquiredPump()

        val newBolusJob = pumpCoroutineScope.async {
            try {
                executeCommand {
                    acquiredPump.deliverBolus(detailedBolusInfo.insulin)
                }

                reportFinishedBolus(rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_successfully, detailedBolusInfo.insulin), detailedBolusInfo.id, pumpEnactResult, succeeded = true)
            } catch (e: CancellationException) {
                reportFinishedBolus(R.string.combov2_bolus_cancelled, detailedBolusInfo.id, pumpEnactResult, succeeded = true)
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Exception thrown during bolus delivery: $e")
                reportFinishedBolus(R.string.combov2_bolus_delivery_failed, detailedBolusInfo.id, pumpEnactResult, succeeded = false)
            } finally {
                pumpEnactResult.enacted = pumpEnactResult.success
                pumpEnactResult.bolusDelivered = if (pumpEnactResult.success) detailedBolusInfo.insulin else 0.0

                aapsLogger.debug(
                    LTag.PUMP,
                    "Pump enact result: success ${pumpEnactResult.success} enacted ${pumpEnactResult.enacted} bolusDelivered${pumpEnactResult.bolusDelivered}"
                )
                bolusJob = null
            }
        }

        bolusJob = newBolusJob

        runBlocking {
            try {
                aapsLogger.debug(LTag.PUMP, "Waiting for bolus coroutine to finish")
                newBolusJob.join()
                aapsLogger.debug(LTag.PUMP, "Bolus coroutine finished")
            } catch (_: CancellationException) {
                aapsLogger.debug(LTag.PUMP, "Bolus coroutine was cancelled")
            }
        }

        return pumpEnactResult.apply {
            success = false
            enacted = false
            comment = "Not implemented (Step 1 Stub)"
        }
    }

    override fun stopBolusDelivering() {
        aapsLogger.debug(LTag.PUMP, "Stopping bolus delivery")
        runBlocking {
            bolusJob?.cancelAndJoin()
            bolusJob = null
        }
        aapsLogger.debug(LTag.PUMP, "Bolus delivery stopped")
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.isPercent = false

        // Corner case: Current base basal rate is 0 IU. We cannot do
        // anything then, otherwise we get into a division by zero below
        // when converting absoluteRate to a percentage.
        if (baseBasalRate == 0.0) {
            pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.combov2_cannot_set_absolute_tbr_if_basal_zero)
            }
            return pumpEnactResult
        }

        // The Combo cannot handle absolute rates directly.
        // We have to convert it to a percentage instead,
        // and the percentage must be an integer multiple
        // of 10, otherwise the Combo won't accept it.

        val percentage = absoluteRate / baseBasalRate * 100
        val roundedPercentage = ((absoluteRate / baseBasalRate * 10).roundToInt() * 10)
        val limitedPercentage = min(roundedPercentage, _pumpDescription.maxTempPercent)

        aapsLogger.debug(LTag.PUMP, "Calculated percentage of $percentage% out of absolute rate $absoluteRate; rounded to: $roundedPercentage%; limited to: $limitedPercentage%")

        val cctlTbrType = when (tbrType) {
            PumpSync.TemporaryBasalType.NORMAL                -> ComboCtlTbr.Type.NORMAL
            PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND -> ComboCtlTbr.Type.EMULATED_COMBO_STOP
            PumpSync.TemporaryBasalType.SUPERBOLUS            -> ComboCtlTbr.Type.SUPERBOLUS

            PumpSync.TemporaryBasalType.PUMP_SUSPEND          -> {
                aapsLogger.error(
                    LTag.PUMP,
                    "PUMP_SUSPEND TBR type produced by AAPS for the TBR initiation even though this is supposed to only be produced by pump drivers"
                )
                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(app.aaps.core.ui.R.string.error)
                }
                return pumpEnactResult
            }
        }

        setTbrInternal(limitedPercentage, durationInMinutes, cctlTbrType, force100Percent = false, pumpEnactResult)

        return pumpEnactResult
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.isPercent = true

        val roundedPercentage = ((percent + 5) / 10) * 10
        val limitedPercentage = min(roundedPercentage, _pumpDescription.maxTempPercent)
        aapsLogger.debug(LTag.PUMP, "Got percentage of $percent%; rounded to: $roundedPercentage%; limited to: $limitedPercentage%")

        val cctlTbrType = when (tbrType) {
            PumpSync.TemporaryBasalType.NORMAL                -> ComboCtlTbr.Type.NORMAL
            PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND -> ComboCtlTbr.Type.EMULATED_COMBO_STOP
            PumpSync.TemporaryBasalType.SUPERBOLUS            -> ComboCtlTbr.Type.SUPERBOLUS

            PumpSync.TemporaryBasalType.PUMP_SUSPEND          -> {
                aapsLogger.error(
                    LTag.PUMP,
                    "PUMP_SUSPEND TBR type produced by AAPS for the TBR initiation even though this is supposed to only be produced by pump drivers"
                )
                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(app.aaps.core.ui.R.string.error)
                }
                return pumpEnactResult
            }
        }

        setTbrInternal(limitedPercentage, durationInMinutes, cctlTbrType, force100Percent = false, pumpEnactResult)

        return pumpEnactResult
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.isPercent = true
        pumpEnactResult.isTempCancel = enforceNew
        setTbrInternal(100, 0, tbrType = ComboCtlTbr.Type.NORMAL, force100Percent = enforceNew, pumpEnactResult)
        return pumpEnactResult
    }

    private fun setTbrInternal(
        percentage: Int,
        durationInMinutes: Int,
        tbrType: ComboCtlTbr.Type,
        force100Percent: Boolean,
        pumpEnactResult: PumpEnactResult
    ) {
        if (isSuspended()) {
            aapsLogger.info(LTag.PUMP, "Cannot set TBR since the pump is suspended")
            pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.combov2_pump_is_suspended)
            }
            return
        }

        val acquiredPump = getAcquiredPump()

        runBlocking {
            try {
                executeCommand {
                    acquiredPump.setTbr(percentage, durationInMinutes)

                    pumpEnactResult.apply {
                        success = true
                        enacted = true
                        comment = rh.gs(R.string.combov2_setting_tbr_succeeded)
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Setting TBR failed with exception: $e")
                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(R.string.combov2_setting_tbr_failed)
                }
            }
        }

        pumpEnactResult.apply {
            success = false
            enacted = false
            comment = "Not implemented (Step 1 Stub)"
        }
    }
    // It is currently not known how to program an extended bolus into the Combo.
    // Until that is reverse engineered, inform callers that we can't handle this.

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult =
        createFailurePumpEnactResult(R.string.combov2_extended_bolus_not_supported)

    override fun cancelExtendedBolus(): PumpEnactResult =
        createFailurePumpEnactResult(R.string.combov2_extended_bolus_not_supported)

    override fun updateExtendedJsonStatus(extendedStatus: JSONObject) {
        // NOTE: 旧 AlertScreen API (lastComboAlert) は comboctl の構造変更により廃止されました。
        // 将来的にライブラリ側でアラート・エラーコード取得機能が拡張された際、こちらに再移植を行います。
    }

    override fun manufacturer() = ManufacturerType.Roche

    override fun model() = PumpType.ACCU_CHEK_COMBO

    override fun serialNumber(): String {
        val bluetoothAddress = getBluetoothAddress()
        val curPumpManager = pumpManager
        return if ((bluetoothAddress != null) && (curPumpManager != null))
            curPumpManager.getPumpID(bluetoothAddress)
        else
            rh.gs(R.string.combov2_not_paired)
    }

    override val pumpDescription: PumpDescription
        get() = _pumpDescription

    override fun pumpSpecificShortStatus(veryShort: Boolean): String {
        val lines = mutableListOf<String>()

        // =========================================================================
        // Step 1: comboctl API変更に伴う一時無効化（AlertScreen 変更・廃止のため）
        // Step 2で新APIの警告・エラー取得処理に置き換えます。
        // =========================================================================
        // val alertCodeString = when (val alert = lastComboAlert) {
        //     is AlertScreen.Content.Warning -> "W${alert.code}"
        //     is AlertScreen.Content.Error   -> "E${alert.code}"
        //     else                           -> null
        // }
        val alertCodeString: String? = null

        if (alertCodeString != null)
            lines += rh.gs(R.string.combov2_short_status_alert, alertCodeString)

        return lines.joinToString("\n")
    }

    override val isFakingTempsByExtendedBoluses = false

    @OptIn(ExperimentalTime::class)
    override fun loadTDDs(): PumpEnactResult {
        val pumpEnactResult = pumpEnactResultProvider.get()

        val acquiredPump = getAcquiredPump()

        runBlocking {
            try {
                // Map key = timestamp; value = TDD
                val tddMap = mutableMapOf<Long, Int>()

                executeCommand {
                    val tddHistory = acquiredPump.fetchTDDHistory()

                    tddHistory
                        .filter { it.totalDailyAmount >= 1 }
                        .forEach { tddHistoryEntry ->
                            val timestamp = tddHistoryEntry.date.toEpochMilliseconds()
                            tddMap[timestamp] = (tddMap[timestamp] ?: 0) + tddHistoryEntry.totalDailyAmount
                        }
                }

                for (tddEntry in tddMap) {
                    val timestamp = tddEntry.key
                    val totalDailyAmount = tddEntry.value

                    pumpSync.createOrUpdateTotalDailyDose(
                        timestamp,
                        bolusAmount = 0.0,
                        basalAmount = 0.0,
                        totalAmount = totalDailyAmount.cctlBasalToIU(),
                        pumpId = null,
                        pumpType = PumpType.ACCU_CHEK_COMBO,
                        pumpSerial = serialNumber()
                    )
                }

                pumpEnactResult.apply {
                    success = true
                    enacted = true
                }
            } catch (e: CancellationException) {
                pumpEnactResult.apply {
                    success = true
                    enacted = false
                    comment = rh.gs(R.string.combov2_load_tdds_cancelled)
                }
                throw e
            } catch (e: Exception) {
                aapsLogger.error("Exception thrown during TDD retrieval: $e")

                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(R.string.combov2_retrieving_tdds_failed)
                }
            }
        }

        return pumpEnactResult

        return pumpEnactResult.apply {
            success = false
            enacted = false
            comment = "Not implemented (Step 1 Stub)"
        }
    }
    override fun canHandleDST() = true

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.info(LTag.PUMP, "Time, Date and/or TimeZone changed. Time change type = $timeChangeType")

        val reason = when (timeChangeType) {
            TimeChangeType.TimezoneChanged -> rh.gs(R.string.combov2_timezone_changed)
            TimeChangeType.TimeChanged     -> rh.gs(R.string.combov2_datetime_changed)
            TimeChangeType.DSTStarted      -> rh.gs(R.string.combov2_dst_started)
            TimeChangeType.DSTEnded        -> rh.gs(R.string.combov2_dst_ended)
        }
        // Updating pump status implicitly also updates the pump's local datetime,
        // which is what we want after the system datetime/timezone/DST changed.
        commandQueue.readStatus(reason, null)
    }

    fun clearPumpErrorObservedFlag() {
        stopPumpErrorTimeout()
        if (pumpErrorObserved) {
            aapsLogger.info(LTag.PUMP, "Clearing pumpErrorObserved flag")
            pumpErrorObserved = false
        }
    }

    /*** Loop constraints ***/
    // These restrict the function of the loop in case of an event
    // that makes running a loop too risky, for example because something
    // went wrong while bolusing, or because the incorrect basal profile
    // was found to be active.

    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!isSuspended() && (lastActiveBasalProfileNumber != null)) {
            val isAllowed = (lastActiveBasalProfileNumber == 1)
            aapsLogger.info(
                LTag.PUMP,
                "Currently active basal profile: $lastActiveBasalProfileNumber -> loop invocation allowed: $isAllowed"
            )

            if (!isAllowed) {
                value.set(false, rh.gs(R.string.combov2_incorrect_active_basal_profile, lastActiveBasalProfileNumber), this)
            }
        } else {
            aapsLogger.info(
                LTag.PUMP,
                "Cannot currently determine which basal profile is active in the pump"
            )
            // We don't disallow the invocation in this case since the only reasons for lastActiveBasalProfileNumber
            // being null are (1) we are in the initial, uninitialized state (in which case looping won't
            // work anyway) and (2) the pump is currently suspended (which already will not allow for looping).
        }

        return value
    }

    /*** Pairing API ***/

    fun getPairingProgressFlow() =
        pumpManager?.pairingProgressFlow ?: throw IllegalStateException("Attempting access uninitialized pump manager")

    fun resetPairingProgress() = pumpManager?.resetPairingProgress()

    private val _previousPairingAttemptFailedFlow = MutableStateFlow(false)
    val previousPairingAttemptFailedFlow = _previousPairingAttemptFailedFlow.asStateFlow()

    private var pairingJob: Job? = null
    private var pairingPINChannel: Channel<PairingPIN>? = null

    fun startPairing() {
        val discoveryDuration = preferences.get(ComboIntKey.DiscoveryDuration)

        val newPINChannel = Channel<PairingPIN>(capacity = Channel.RENDEZVOUS)
        pairingPINChannel = newPINChannel

        _previousPairingAttemptFailedFlow.value = false

        // Update the log level here in case the user changed it.
        updateComboCtlLogLevel()

        pairingJob = pumpCoroutineScope.async {
            try {
                // Do the pairing attempt within runWithPermissionCheck()
                // since pairing requires Bluetooth permissions.
                val pairingResult = runWithPermissionCheck(
                    context, config, aapsLogger, androidPermission,
                    permissionsToCheckFor = listOf("android.permission.BLUETOOTH_CONNECT")
                ) {
                    try {
                        pumpManager?.pairWithNewPump(discoveryDuration) { newPumpAddress, previousAttemptFailed ->
                            aapsLogger.info(
                                LTag.PUMP,
                                "New pairing PIN request from Combo pump with Bluetooth " +
                                    "address $newPumpAddress (previous attempt failed: $previousAttemptFailed)"
                            )
                            _previousPairingAttemptFailedFlow.value = previousAttemptFailed
                            newPINChannel.receive()
                        } ?: throw IllegalStateException("Attempting to access uninitialized pump manager")
                    } catch (e: BluetoothNotEnabledException) {
                        // If Bluetooth is turned off during pairing, show a toaster message.
                        // Notifications on the AAPS overview fragment are not useful here
                        // because the pairing activity obscures that fragment. So, instead,
                        // alert the user by showing the notification via the toaster.
                        ToastUtils.errorToast(context, app.aaps.core.ui.R.string.ble_not_enabled)
                        ComboCtlPumpManager.PairingResult.ExceptionDuringPairing(e)
                    }
                }

                if (pairingResult !is ComboCtlPumpManager.PairingResult.Success)
                    return@async

                _pairedStateUIFlow.value = true

                // Notify AndroidAPS that this is a new pump and that
                // the history that is associated with any previously
                // paired pump is to be discarded.
                pumpSync.connectNewPump()

                // Schedule a status update, since pairing can take
                // a while. By the time  we reach this point, the queue
                // connection attempt may have reached the timeout,
                // and reading the status is part of what AndroidAPS
                // was trying to do, so do that now.
                // If we reach this point before the timeout, then the
                // queue will contain a pump_driver_changed readstatus
                // command already. The queue will see that and ignore
                // this readStatus() call automatically.
                commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_paired), null)
            } finally {
                pairingJob = null
                pairingPINChannel?.close()
                pairingPINChannel = null
            }
        }
    }

    fun cancelPairing() {
        runBlocking {
            aapsLogger.debug(LTag.PUMP, "Cancelling pairing")
            pairingJob?.cancelAndJoin()
            aapsLogger.debug(LTag.PUMP, "Pairing cancelled")
        }
    }

    suspend fun providePairingPIN(pairingPIN: PairingPIN) {
        try {
            pairingPINChannel?.send(pairingPIN)
        } catch (_: ClosedSendChannelException) {
        }
    }

    private fun unpair() {
        if (unpairing)
            return

        val bluetoothAddress = getBluetoothAddress() ?: return

        unpairing = true

        disconnectInternal(forceDisconnect = true)

        runBlocking {
            try {
                val pump = pumpManager?.acquirePump(bluetoothAddress) ?: return@runBlocking
                // =========================================================================
                // Step 1: comboctl API変更に伴う一時無効化（unpair 廃止のため）
                // =========================================================================
                // pump.unpair()
                pumpManager?.releasePump(bluetoothAddress)
            } catch (_: ComboException) {
            } catch (_: BluetoothException) {
            }
        }

        // Reset these states since they are associated
        // with the now unpaired pump.
        lastConnectionTimestamp = 0L
        activeBasalProfile = null
        lastActiveBasalProfileNumber = null

        // Reset the UI flows that are associated with the pump
        // that just got unpaired to prevent the UI from showing
        // information about that now-unpaired pump anymore.
        _currentActivityUIFlow.value = noCurrentActivity()
        _lastConnectionTimestampUIFlow.value = null
        _batteryStateUIFlow.value = null
        _reservoirLevelUIFlow.value = null
        _lastBolusUIFlow.value = null
        _baseBasalRateUIFlow.value = null
        _serialNumberUIFlow.value = ""
        _bluetoothAddressUIFlow.value = ""

        clearPumpErrorObservedFlag()

        // The unpairing variable is set to false in
        // the PumpManager onPumpUnpaired callback.
    }

    /*** User interface flows ***/

    // "UI flows" are hot flows that are meant to be used for showing
    // information about the pump and its current state on the UI.
    // These are kept in the actual plugin class to make sure they
    // are always available, even if no pump is paired (which means
    // that the "pump" variable is set to null and thus its flows
    // are inaccessible).
    //
    // A few UI flows are internally also used for other checks, such
    // as pairedStateUIFlow (which is used internally to verify whether
    // or not the pump is paired).
    //
    // Some UI flows are nullable and have a null initial state to
    // indicate to UIs that they haven't been filled with actual
    // state yet.

    // This is a variant of driverStateFlow that retains the Error
    // and Suspended state even after disconnecting to make sure these
    // states kept being showed to the user post-disconnect.
    // NOTE: Do not rely on this to check prior to a command if the
    // pump is suspended or not, since the driver state UI flow is
    // updated in a separate coroutine, and is _only_ meant for UI
    // updates. Using this for other purposes can cause race conditions
    // to appear, such as when immediately after the Pump.connect() call
    // finishes, the state is checked. Use isSuspended() instead.
        private val _driverStateUIFlow = MutableStateFlow<DriverState>(DriverState.Disconnected)
        val driverStateUIFlow = _driverStateUIFlow.asStateFlow()

        // "Activity" is not to be confused with the Android Activity class.
        // An "activity" is something that a command does, for example
        // establishing a BT connection, or delivering a bolus, setting
        // a basal rate factor, reading the current pump datetime etc.
        data class CurrentActivityInfo(val description: String, val overallProgress: Double)

        private fun noCurrentActivity() = CurrentActivityInfo("", 0.0)
        private var _currentActivityUIFlow = MutableStateFlow(noCurrentActivity())
        val currentActivityUIFlow = _currentActivityUIFlow.asStateFlow()

        private var _lastConnectionTimestampUIFlow = MutableStateFlow<Long?>(null)
        val lastConnectionTimestampUIFlow = _lastConnectionTimestampUIFlow.asStateFlow()

        private var _batteryStateUIFlow = MutableStateFlow<BatteryState?>(null)
        val batteryStateUIFlow = _batteryStateUIFlow.asStateFlow()

        data class ReservoirLevel(val state: ReservoirState, val availableUnits: Int)

        private var _reservoirLevelUIFlow = MutableStateFlow<ReservoirLevel?>(null)
        val reservoirLevelUIFlow = _reservoirLevelUIFlow.asStateFlow()

        private var _lastBolusUIFlow = MutableStateFlow<Any?>(null)
        val lastBolusUIFlow = _lastBolusUIFlow.asStateFlow()

    private var _currentTbrUIFlow = MutableStateFlow<ComboCtlTbr?>(null)
    val currentTbrUIFlow = _currentTbrUIFlow.asStateFlow()

    private var _baseBasalRateUIFlow = MutableStateFlow<Double?>(null)
    val baseBasalRateUIFlow = _baseBasalRateUIFlow.asStateFlow()

    private var _serialNumberUIFlow = MutableStateFlow("")
    val serialNumberUIFlow = _serialNumberUIFlow.asStateFlow()

    private var _bluetoothAddressUIFlow = MutableStateFlow("")
    val bluetoothAddressUIFlow = _bluetoothAddressUIFlow.asStateFlow()

    private var _pairedStateUIFlow = MutableStateFlow(false)
    val pairedStateUIFlow = _pairedStateUIFlow.asStateFlow()

    // UI flow to show the current RT display frame on the UI. Unlike
    // the other UI flows, this is a SharedFlow, not a StateFlow,
    // since frames aren't "states", and StateFlow filters out duplicates
    // (which isn't useful in this case). The flow is configured such
    // that it never suspends; if its replay cache contains a frame already,
    // that older frame is overwritten. This makes sure the flow always
    // contains the current frame.
    private var _displayFrameUIFlow = MutableSharedFlow<DisplayFrame?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val displayFrameUIFlow = _displayFrameUIFlow.asSharedFlow()

    /*** Misc private functions ***/

    private fun setupUiFlows(acquiredPump: ComboCtlPump) {
        pumpUIFlowsDeferred = pumpCoroutineScope.async {
            try {
                coroutineScope {
                    acquiredPump.connectProgressFlow
                        .onEach { progressReport ->
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                rh.gs(R.string.combov2_establishing_bt_connection, 1),
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.setDateTimeProgressFlow
                        .onEach { progressReport ->
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                rh.gs(R.string.combov2_setting_current_pump_time),
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.getBasalProfileFlow
                        .onEach { progressReport ->
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                rh.gs(R.string.combov2_getting_basal_profile, 1),
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.setBasalProfileFlow
                        .onEach { progressReport ->
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                rh.gs(R.string.combov2_setting_basal_profile, 1),
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.parsedDisplayFrameFlow
                        .onEach { parsedDisplayFrame ->
                            _displayFrameUIFlow.emit(
                                parsedDisplayFrame?.displayFrame ?: NullDisplayFrame
                            )
                        }
                        .launchIn(this)

                    launch {
                        while (true) {
                            updateBaseBasalRateUI()
                            val currentMinute = DateTime().minuteOfHour().get()

                            val minutesUntilNextFactor = max((58 - currentMinute), 0)
                            delay(minutesUntilNextFactor * 60 * 1000L)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Exception thrown in UI flows coroutine scope: $e")
                throw e
            }
        }
    }
    private fun startPumpErrorTimeout() {
        if (pumpErrorTimeoutJob != null)
            return

        pumpErrorTimeoutJob = pumpCoroutineScope.launch {
            delay(PUMP_ERROR_TIMEOUT_INTERVAL_MSECS)
            aapsLogger.info(LTag.PUMP, "Clearing pumpErrorObserved flag after timeout was reached")
            pumpErrorObserved = false
            commandQueue.readStatus(rh.gs(R.string.combov2_refresh_pump_status_after_error), null)
        }
    }

    private fun stopPumpErrorTimeout() {
        pumpErrorTimeoutJob?.cancel()
        pumpErrorTimeoutJob = null
    }

    private fun updateBaseBasalRateUI() {
        val currentHour = DateTime().hourOfDay().get()
        // This sets value to null if no profile is set,
        // which keeps the base basal rate on the UI blank.
        _baseBasalRateUIFlow.value = activeBasalProfile?.get(currentHour)?.cctlBasalToIU()
    }

@OptIn(ExperimentalTime::class)
    private fun handlePumpEvent(event: ComboCtlPump.Event) {
        aapsLogger.debug(LTag.PUMP, "Handling pump event $event")

        when (event) {
            is ComboCtlPump.Event.LowBattery -> {
                uiInteraction.addNotification(
                    Notification.COMBO_PUMP_ALARM,
                    text = rh.gs(R.string.combov2_battery_low_warning),
                    level = Notification.NORMAL
                )
            }

            is ComboCtlPump.Event.TbrStarted -> {
                aapsLogger.debug(
                    LTag.PUMP,
                    "Pump reports TBR started: ${event.percentage}% for ${event.durationMinutes}m; expected state according to AAPS: ${pumpSync.expectedPumpState()}"
                )
                val tbrStartTimestampInMs = System.currentTimeMillis()
                pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = tbrStartTimestampInMs,
                    rate = event.percentage.toDouble(),
                    duration = event.durationMinutes.toLong() * 60 * 1000,
                    isAbsolute = false,
                    type = PumpSync.TemporaryBasalType.NORMAL,
                    pumpId = tbrStartTimestampInMs,
                    pumpType = PumpType.ACCU_CHEK_COMBO,
                    pumpSerial = serialNumber()
                )
            }

            is ComboCtlPump.Event.AlarmRaised -> {
                aapsLogger.warn(LTag.PUMP, "Pump alarm raised: ${event.alarmCode}")
                uiInteraction.addNotification(
                    Notification.COMBO_PUMP_ALARM,
                    text = "Pump Alarm: ${event.alarmCode}",
                    level = Notification.URGENT
                )
            }
        }
    }
    // Marked as synchronized since this may get called by a finishing
    // connect operation and by the command queue at the same time.
    @Synchronized private fun disconnectInternal(forceDisconnect: Boolean) {
        // Sometimes, the CommandQueue may decide to call disconnect while the
        // driver is still busy with something, for example because some checks
        // are being performed. Ignore disconnect requests in that case, unless
        // the forceDisconnect flag is set.
        if (!forceDisconnect && isBusy()) {
            disconnectRequestPending = true
            aapsLogger.debug(LTag.PUMP, "Ignoring disconnect request since driver is currently busy")
            return
        }

        if (isDisconnected()) {
            aapsLogger.debug(LTag.PUMP, "Already disconnected")
            return
        }

        // It makes no sense to reach this location with pump
        // being null due to the checks above.
        val pumpToDisconnect = pump
        if (pumpToDisconnect == null) {
            aapsLogger.error(LTag.PUMP, "Current pump is already null")
            return
        }

        // Run these operations in a coroutine to be able to wait
        // until the disconnect really completes and the UI flows
        // are all cancelled & their coroutines finished. Otherwise
        // we can end up with race conditions because the coroutines
        // are still ongoing in the background.
        runBlocking {
            // Disconnecting the pump needs to be done in one of two
            // ways, depending on whether we try to disconnect while
            // the pump is in the Connecting state or not:
            //
            // 1. Pump is in the Connecting state. A disconnectInternal()
            // call then means that we are aborting the ongoing connect
            // attempt. Internally, the pump may be waiting for a blocking
            // Bluetooth device connect procedure to complete.
            // 2. Pump is past the Connecting state. The blocking connect
            // procedure is already over.
            //
            // In case #1, the internal IO loops inside the pump are not
            // yet running. Also, connectionSetupJob.join() won't finish
            // because of the blocking connect procedure. In this case,
            // cancel that coroutine/Job, but don't join yet. Cancel,
            // then disconnect the pump, then join. That way, the blocking
            // Bluetooth connect procedure is aborted (closing a Bluetooth
            // socket usually does that), the connectionSetupJob is unblocked,
            // it can be canceled, and join() can finish. Since there is no
            // IO coroutine running, there won't be any IO errors when
            // disconnecting before joining connectionSetupJob.
            //
            // In case #2, the internal IO loops inside the pump *are*
            // running, so disconnecting before joining is risky. Therefore,
            // in this case, do cancel *and* join connectionSetupJob before
            // actually disconnecting the pump. Otherwise, errors occur, since
            // the connection setup code will try to communicate even though
            // the Pump.disconnect() call shuts down the RFCOMM socket,
            // making all send/receive calls fail.

            if (pumpToDisconnect.stateFlow.value == ComboCtlPump.State.Connecting) {
                aapsLogger.debug(LTag.PUMP, "Cancelling ongoing connect attempt")
                connectionSetupJob?.cancel()
                pumpToDisconnect.disconnect()
                connectionSetupJob?.join()
            } else {
                aapsLogger.debug(LTag.PUMP, "Disconnecting Combo (if not disconnected already by a cancelling request)")
                connectionSetupJob?.cancelAndJoin()
                pumpToDisconnect.disconnect()
            }

            aapsLogger.debug(LTag.PUMP, "Combo disconnected; cancelling UI flows coroutine")
            pumpUIFlowsDeferred?.cancelAndJoin()
            aapsLogger.debug(LTag.PUMP, "Cancelling state and status flows coroutine")
            stateAndStatusFlowsDeferred?.cancelAndJoin()
            aapsLogger.debug(LTag.PUMP, "Releasing pump instance back to pump manager")

            getBluetoothAddress()?.let { pumpManager?.releasePump(it) }
        }

        connectionSetupJob = null
        pumpUIFlowsDeferred = null
        stateAndStatusFlowsDeferred = null
        pump = null

        disconnectRequestPending = false

        aapsLogger.debug(LTag.PUMP, "Combo disconnect complete")
        setDriverState(DriverState.Disconnected)
    }

    private fun isPaired() = pairedStateUIFlow.value

    private fun updateComboCtlLogLevel() =
        updateComboCtlLogLevel(preferences.get(ComboBooleanKey.VerboseLogging))

    private fun updateComboCtlLogLevel(enableVerbose: Boolean) {
        aapsLogger.debug(LTag.PUMP, "${if (enableVerbose) "Enabling" else "Disabling"} verbose logging")
        ComboCtlLogger.threshold = if (enableVerbose) ComboCtlLogLevel.VERBOSE else ComboCtlLogLevel.DEBUG
    }

    private fun setDriverState(newState: DriverState) {
        val oldState = _driverStateFlow.value

        if (oldState == newState)
            return

        _driverStateUIFlow.value = newState
        _driverStateFlow.value = newState

        if (newState == DriverState.Disconnected)
            _currentActivityUIFlow.value = noCurrentActivity()

        aapsLogger.info(LTag.PUMP, "Setting Combo driver state:  old: $oldState  new: $newState")

        when (newState) {
            DriverState.Disconnected -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            DriverState.Connecting   -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
            DriverState.Error        -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
        }
    }

    private fun executePendingDisconnect() {
        if (!disconnectRequestPending)
            return

        aapsLogger.debug(LTag.PUMP, "Executing pending disconnect request")
        disconnectInternal(forceDisconnect = true)
    }

    private fun unpairDueToPumpDataError() {
        disconnectInternal(forceDisconnect = true)
        uiInteraction.addNotificationValidTo(
            id = Notification.PUMP_ERROR,
            date = dateUtil.now(),
            text = rh.gs(R.string.combov2_cannot_access_pump_data),
            level = Notification.URGENT,
            validTo = 0
        )
        unpair()
    }

    // Utility function to run a ComboCtlPump command (deliverBolus for example)
    // and do common checks afterwards (like handling AlertScreenException).
    // IMPORTANT: This disconnects in case of an error, so if any other
    // nontrivial procedure needs to be done for the command in case of an
    // error, do this inside a try-finally block in the block.
    private suspend fun executeCommand(
        block: suspend CoroutineScope.() -> Unit
    ) {
        try {
            coroutineScope {
                block.invoke(this)
            }

            // The AAPS pump command queue may have asked for a disconnect
            // while the command was being executed. Do this postponed
            // disconnect now that we are done with the command.
            executePendingDisconnect()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Disconnect since we are now in the Error state.
            disconnectInternal(forceDisconnect = true)
            throw t
        }
    }
    
    private fun updateLastConnectionTimestamp() {
        lastConnectionTimestamp = System.currentTimeMillis()
        _lastConnectionTimestampUIFlow.value = lastConnectionTimestamp
    }

    private fun getAlertDescription(alert: Any): String {
        /* Step 1: CIビルド導通のため一時無効化（Step 2でAlertScreenException等へ再実装予定）
    private fun getAlertDescription(alert: AlertScreen.Content) =
        when (alert) {
            is AlertScreen.Content.Warning -> {
                val desc = when (alert.code) {
                    4    -> rh.gs(R.string.combov2_warning_4)
                    10   -> rh.gs(R.string.combov2_warning_10)
                    else -> ""
                }

                "${rh.gs(R.string.combov2_warning)} W${alert.code}" +
                    if (desc.isEmpty()) "" else ": $desc"
            }

            is AlertScreen.Content.Error   -> {
                val desc = when (alert.code) {
                    1    -> rh.gs(R.string.combov2_error_1)
                    2    -> rh.gs(R.string.combov2_error_2)
                    4    -> rh.gs(R.string.combov2_error_4)
                    5    -> rh.gs(R.string.combov2_error_5)
                    6    -> rh.gs(R.string.combov2_error_6)
                    7    -> rh.gs(R.string.combov2_error_7)
                    8    -> rh.gs(R.string.combov2_error_8)
                    9    -> rh.gs(R.string.combov2_error_9)
                    10   -> rh.gs(R.string.combov2_error_10)
                    11   -> rh.gs(R.string.combov2_error_11)
                    else -> ""
                }

                "${rh.gs(R.string.combov2_error)} E${alert.code}" +
                    if (desc.isEmpty()) "" else ": $desc"
            }

            else                           -> rh.gs(R.string.combov2_unrecognized_alert)
        }
        */
        return ""
    }

    private fun notifyAboutComboAlert(alert: Any) {
        /* Step 1: CIビルド導通のため一時無効化（Step 2でAlertScreenException等へ再実装予定）
    private fun notifyAboutComboAlert(alert: AlertScreen.Content) {
        if (alert is AlertScreen.Content.Error) {
            aapsLogger.info(LTag.PUMP, "Error screen observed - setting pumpErrorObserved flag")
            pumpErrorObserved = true
            startPumpErrorTimeout()
        }

        uiInteraction.addNotification(
            Notification.COMBO_PUMP_ALARM,
            text = "${rh.gs(R.string.combov2_combo_alert)}: ${getAlertDescription(alert)}",
            level = if (alert is AlertScreen.Content.Warning) Notification.NORMAL else Notification.URGENT
        )
        */
    }

    private fun reportFinishedBolus(status: String, id: Long, pumpEnactResult: PumpEnactResult, succeeded: Boolean) {
        rxBus.send(EventOverviewBolusProgress(rh, percent = 100, id = id))

        pumpEnactResult.apply {
            success = succeeded
            comment = status
        }
    }

    private fun reportFinishedBolus(stringId: Int, id: Long, pumpEnactResult: PumpEnactResult, succeeded: Boolean) =
        reportFinishedBolus(rh.gs(stringId), id, pumpEnactResult, succeeded)

    private fun createFailurePumpEnactResult(comment: Int) =
        pumpEnactResultProvider.get()
            .success(false)
            .enacted(false)
            .comment(comment)

    private fun getBluetoothAddress(): ComboCtlBluetoothAddress? =
        pumpManager?.getPairedPumpAddresses()?.firstOrNull()

    private fun getAcquiredPump() = pump ?: throw Error("There is no currently acquired pump; this should not happen. Please report this as a bug.")

    private fun isDisconnected() =
        when (driverStateFlow.value) {
            DriverState.Disconnected -> true
            else                     -> false
        }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "combov2_settings"
            title = rh.gs(R.string.combov2_title)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context, intentKey = ComboIntentKey.PairWithPump, title = R.string.combov2_pair_with_pump_title, summary = R.string.combov2_pair_with_pump_summary,
                    intent = Intent(context, ComboV2PairingActivity::class.java)
                )
            )
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context, intentKey = ComboIntentKey.UnpairPump, title = R.string.combov2_unpair_pump_title, summary = R.string.combov2_unpair_pump_summary
                ).apply {
                    onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
                        OKDialog.showConfirmation(preference.context, "Confirm pump unpairing", "Do you really want to unpair the pump?", ok = Runnable { unpair() })
                        false
                    }
                }
            )
            addPreference(AdaptiveIntPreference(ctx = context, intKey = ComboIntKey.DiscoveryDuration, title = R.string.combov2_discovery_duration))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = ComboBooleanKey.AutomaticReservoirEntry, title = R.string.combov2_automatic_reservoir_entry))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = ComboBooleanKey.AutomaticBatteryEntry, title = R.string.combov2_automatic_battery_entry))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = ComboBooleanKey.VerboseLogging, title = R.string.combov2_verbose_logging))
        }
    }
}
