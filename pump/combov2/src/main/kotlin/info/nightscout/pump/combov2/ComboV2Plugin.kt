package info.nightscout.pump.combov2

import android.content.Context
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.data.R
import info.nightscout.androidaps.interfaces.Plugin
import info.nightscout.androidaps.interfaces.Pump
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.comboctl.main.AlertScreenContent
import info.nightscout.comboctl.main.AlertScreenException
import info.nightscout.comboctl.main.BatteryState
import info.nightscout.comboctl.main.BolusCancelledByUserException
import info.nightscout.comboctl.main.BolusNotDeliveredException
import info.nightscout.comboctl.main.ComboCtlPump
import info.nightscout.comboctl.main.InsufficientInsulinAvailableException
import info.nightscout.comboctl.main.LastBolus
import info.nightscout.comboctl.main.RTCommandProgressStage
import info.nightscout.comboctl.main.SetTbrOutcome
import info.nightscout.comboctl.main.StandardBolusReason
import info.nightscout.comboctl.main.Status
import info.nightscout.comboctl.main.UnaccountedBolusDetectedException
import info.nightscout.comboctl.main.UnexpectedTbrStateException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComboV2Plugin @Inject constructor(
    private val context: Context
) : Plugin, Pump {

    sealed class DriverState {
        object NotInitialized : DriverState()
        object Disconnected : DriverState()
        object Connecting : DriverState()
        object CheckingPump : DriverState()
        data class ExecutingCommand(val commandName: String) : DriverState()
        object Ready : DriverState()
        object Suspended : DriverState()
        object Error : DriverState()
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _driverStateUIFlow = MutableStateFlow<DriverState>(DriverState.NotInitialized)
    val driverStateUIFlow: StateFlow<DriverState> = _driverStateUIFlow.asStateFlow()

    private val _pairedStateUIFlow = MutableStateFlow(false)
    val pairedStateUIFlow: StateFlow<Boolean> = _pairedStateUIFlow.asStateFlow()

    private val _statusFlow = MutableStateFlow<Status?>(null)
    val statusFlow: StateFlow<Status?> = _statusFlow.asStateFlow()

    private val _lastBolusFlow = MutableStateFlow<LastBolus?>(null)
    val lastBolusFlow: StateFlow<LastBolus?> = _lastBolusFlow.asStateFlow()

    private val _currentTbrFlow = MutableStateFlow<Pair<Int, Int>?>(null)
    val currentTbrFlow: StateFlow<Pair<Int, Int>?> = _currentTbrFlow.asStateFlow()

    private val _connectProgressFlow = MutableStateFlow<RTCommandProgressStage?>(null)
    val connectProgressFlow: StateFlow<RTCommandProgressStage?> = _connectProgressFlow.asStateFlow()

    private val _setDateTimeProgressFlow = MutableStateFlow<RTCommandProgressStage?>(null)
    val setDateTimeProgressFlow: StateFlow<RTCommandProgressStage?> = _setDateTimeProgressFlow.asStateFlow()

    private val _getBasalProfileFlow = MutableStateFlow<RTCommandProgressStage?>(null)
    val getBasalProfileFlow: StateFlow<RTCommandProgressStage?> = _getBasalProfileFlow.asStateFlow()

    private val _setBasalProfileFlow = MutableStateFlow<RTCommandProgressStage?>(null)
    val setBasalProfileFlow: StateFlow<RTCommandProgressStage?> = _setBasalProfileFlow.asStateFlow()

    private val _bolusDeliveryProgressFlow = MutableStateFlow<RTCommandProgressStage?>(null)
    val bolusDeliveryProgressFlow: StateFlow<RTCommandProgressStage?> = _bolusDeliveryProgressFlow.asStateFlow()

    private val _parsedDisplayFrameFlow = MutableStateFlow<String?>(null)
    val parsedDisplayFrameFlow: StateFlow<String?> = _parsedDisplayFrameFlow.asStateFlow()

    var lastComboAlert: Int? = null
        private set

    override fun getPluginName(): String = context.getString(R.string.combov2_plugin_name)

override fun getPluginDescription(): String = context.getString(R.string.combov2_plugin_description)

    fun updateStatus() {
        // Status update execution logic
        _driverStateUIFlow.value = DriverState.Ready
    }

    suspend fun unpair() {
        // Unpairing logic with pump
        _pairedStateUIFlow.value = false
        _driverStateUIFlow.value = DriverState.Disconnected
    }

    fun notifyAboutComboAlert(alertCode: Int) {
        lastComboAlert = alertCode
    }

    private fun disconnectInternal(forceDisconnect: Boolean = false) {
        _driverStateUIFlow.value = DriverState.Disconnected
    }

    suspend fun handleAlertScreenException(e: AlertScreenException) {
        lastComboAlert = e.alertCode
        notifyAboutComboAlert(e.alertCode)
        disconnectInternal(forceDisconnect = true)
        throw e
    }

    suspend fun setTbr(percentage: Int, durationMinutes: Int): Boolean {
        _driverStateUIFlow.value = DriverState.ExecutingCommand("SetTBR")
        return try {
            // TBR setting implementation mapping
            _currentTbrFlow.value = Pair(percentage, durationMinutes)
            _driverStateUIFlow.value = DriverState.Ready
            true
        } catch (e: UnexpectedTbrStateException) {
            _driverStateUIFlow.value = DriverState.Error
            false
        } catch (e: AlertScreenException) {
            handleAlertScreenException(e)
            false
        } catch (t: Throwable) {
            _driverStateUIFlow.value = DriverState.Error
            false
        }
    }

    suspend fun deliverBolus(amount: Double, reason: StandardBolusReason): Boolean {
        _driverStateUIFlow.value = DriverState.ExecutingCommand("DeliverBolus")
        return try {
            // Bolus delivery execution logic
            _driverStateUIFlow.value = DriverState.Ready
            true
        } catch (e: BolusCancelledByUserException) {
            _driverStateUIFlow.value = DriverState.Ready
            false
        } catch (e: BolusNotDeliveredException) {
            _driverStateUIFlow.value = DriverState.Error
            false
        } catch (e: UnaccountedBolusDetectedException) {
            _driverStateUIFlow.value = DriverState.Error
            false
        } catch (e: InsufficientInsulinAvailableException) {
            _driverStateUIFlow.value = DriverState.Error
            false
        } catch (e: AlertScreenException) {
            handleAlertScreenException(e)
            false
        } catch (t: Throwable) {
            _driverStateUIFlow.value = DriverState.Error
            false
        }
    }

fun processBatteryState(state: BatteryState): Int {
        return when (state) {
            BatteryState.OK -> 100
            BatteryState.LOW -> 20
            BatteryState.EMPTY -> 0
            else -> 0
        }
    }

    suspend fun fetchTotalDailyAmount(date: String): Double {
        return try {
            // Logic to fetch total daily amount
            0.0
        } catch (e: Exception) {
            0.0
        }
    }

    fun parseAlertContent(content: AlertScreenContent): Int {
        return content.code
    }

    fun isPumpSuspended(state: ComboCtlPump.State): Boolean {
        return state == ComboCtlPump.State.Suspended
    }

    fun isPumpInError(state: ComboCtlPump.State): Boolean {
        return state == ComboCtlPump.State.Error
    }

    override fun getPumpDescription(): PumpDescription {
        return object : PumpDescription {
            override val isBolusCapable: Boolean = true
            override val isExtendedBolusCapable: Boolean = false
            override val isTempBasalCapable: Boolean = true
        }
    }
}
