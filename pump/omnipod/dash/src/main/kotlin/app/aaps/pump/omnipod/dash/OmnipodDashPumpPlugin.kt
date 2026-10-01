package app.aaps.pump.omnipod.dash

import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class OmnipodDashPumpPlugin @Inject constructor(
    private val context: Context,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .pluginName(R.string.omnipod_dash)
        .shortName(R.string.omnipod_dash_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, commandQueue
), Pump {

    private val database: DashHistoryDatabase by lazy {
        DashHistoryDatabase.getInstance(context)
    }

    fun getName(): String = "Omnipod DASH"

    override fun isInitialized(): Boolean = true
    override fun isSuspended(): Boolean = false
    override fun isBusy(): Boolean = false
    override fun isConnected(): Boolean = true
    override fun isConnecting(): Boolean = false
    override fun isHandshakeInProgress(): Boolean = false
    override fun finishHandshaking() {}
    override fun connect(reason: String) {}
    override fun disconnect(reason: String) {}
    override fun stopConnecting() {}
    override fun getPumpStatus(reason: String) {}
    override fun setNewBasalProfile(profile: Profile): PumpEnactResult = pumpEnactResultProvider.get()
    override fun isThisProfileSet(profile: Profile): Boolean = true
    override val lastDataTime: Long get() = 0L
    override val lastBolusTime: Long? get() = null
    override val lastBolusAmount: Double? get() = null
    override val baseBasalRate: Double get() = 0.0
    override val reservoirLevel: Double get() = 0.0
    override val batteryLevel: Int? get() = null
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult = pumpEnactResultProvider.get()
    override fun stopBolusDelivering() {}
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult = pumpEnactResultProvider.get()
    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult = pumpEnactResultProvider.get()
    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult = pumpEnactResultProvider.get()
    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult = pumpEnactResultProvider.get()
    override fun cancelExtendedBolus(): PumpEnactResult = pumpEnactResultProvider.get()
    override fun manufacturer(): ManufacturerType = ManufacturerType.Insulet
    override fun model(): PumpType = PumpType.OMNIPOD_DASH
    override fun serialNumber(): String = ""
    override val pumpDescription: PumpDescription get() = PumpDescription()
    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun loadTDDs(): PumpEnactResult = pumpEnactResultProvider.get()
    override fun canHandleDST(): Boolean = false
    override fun getCustomActions(): List<CustomAction>? = null
    override fun executeCustomAction(customActionType: CustomActionType) {}
    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? = null
    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {}

    fun initializeStatusChecker() {
        // 初期化処理
    }

    companion object {
        const val PLUGIN_NAME = "Omnipod DASH"
    }
}
