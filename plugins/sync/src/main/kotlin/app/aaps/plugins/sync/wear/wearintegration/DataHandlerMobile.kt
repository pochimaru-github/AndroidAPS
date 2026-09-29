package app.aaps.plugins.sync.wear.wearintegration

import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import com.google.android.gms.wearable.DataMap
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

/* TODO: 現行型定義および依存パッケージ復旧時に再有効化
import app.aaps.core.interfaces.ActionData
import app.aaps.core.interfaces.Pump
import app.aaps.core.interfaces.PumpEnactResult
import app.aaps.core.interfaces.TherapyEngine
import app.aaps.core.interfaces.Wear
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.objectMapper
import app.aaps.core.interfaces.rx.bus.RxBusWearData
import app.aaps.core.interfaces.wear.WearPath
import app.aaps.core.units.GlucoseUnit
import app.aaps.core.utils.DateUtil
import app.aaps.core.utils.FabricUtils
import app.aaps.core.utils.JsonParser
import app.aaps.core.utils.NumberUtils
import app.aaps.core.utils.SafeParse
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.StepsRate
import app.aaps.plugins.sync.wear.wearintegration.WearDataService.Companion.SYNC_KEY
*/

@Singleton
class DataHandlerMobile @Inject constructor(
    private val context: Context,
    private val rxBus: RxBus,
    val rh: ResourceHelper,
    aapsLogger: AAPSLogger
    // private val persistenceLayer: PersistenceLayer, // TODO: 現行 DB/Repository に適合・再実装
    // private val treatments: Treatments, // TODO: 現行 Treatments インターフェースに適合・再実装
    // private val profileFunction: ProfileFunction, // TODO: 現行 Profile インターフェースに適合・再実装
    // private val configBuilder: ConfigBuilder, // TODO: 現行 ConfigBuilder インターフェースに適合・再実装
    // private val nsClient: NSClient, // TODO: 現行 NSClient インターフェースに適合・再実装
    // private val tdd: TDD, // TODO: 現行 TDD インターフェースに適合・再実装
    // private val automation: Automation // TODO: 現行 Automation インターフェースに適合・再実装
) : PluginBase(PluginDescription().mainType(PluginType.SYNC), aapsLogger) {

    private val disposable = CompositeDisposable()
    private var lastSendTime = 0L

    init {
        /* TODO: RxBusWearData および WearPath の型適合完了後に再有効化
        disposable += rxBus.register(RxBusWearData::class.java) { event ->
            handleWearData(event)
        }
        */
    }

    /* TODO: WearPath および L (ロガー) 適合後に再有効化
    private fun handleWearData(event: RxBusWearData) {
        val path = event.path
        val dataMap = event.dataMap

        L.d(L.WEAR, "Received wear path: $path")

        when (path) {
            WearPath.ActionBolusPreCheck.path -> handleBolusPreCheck(dataMap)
            WearPath.ActionBolusConfirmed.path -> handleBolusConfirmed(dataMap)
            WearPath.ActionWizardPreCheck.path -> handleWizardPreCheck(dataMap)
            WearPath.ActionWizardConfirmed.path -> handleWizardConfirmed(dataMap)
            WearPath.ActionQuickWizardPreCheck.path -> handleQuickWizardPreCheck(dataMap)
            WearPath.ActionFillPreCheck.path -> handleFillPreCheck(dataMap)
            WearPath.ActionFillConfirmed.path -> handleFillConfirmed(dataMap)
            WearPath.CancelBolus.path -> handleCancelBolus()
            WearPath.ActionECarbsPreCheck.path -> handleECarbsPreCheck(dataMap)
            WearPath.ActionECarbsConfirmed.path -> handleECarbsConfirmed(dataMap)
            WearPath.ActionTempTargetPreCheck.path -> handleTempTargetPreCheck(dataMap)
            WearPath.ActionTempTargetConfirmed.path -> handleTempTargetConfirmed(dataMap)
            WearPath.LoopStatesRequest.path -> sendLoopStates()
            WearPath.LoopStateSelected.path -> handleLoopStateSelected(dataMap)
            WearPath.LoopStateConfirmed.path -> handleLoopStateConfirmed(dataMap)
            WearPath.ActionProfileSwitchPreCheck.path -> handleProfileSwitchPreCheck(dataMap)
            WearPath.ActionProfileSwitchConfirmed.path -> handleProfileSwitchConfirmed(dataMap)
            WearPath.ActionHeartRate.path -> handleHeartRate(dataMap)
            WearPath.ActionStepsRate.path -> handleStepsRate(dataMap)
            WearPath.ActionGetCustomWatchface.path -> handleGetCustomWatchface()
            else -> L.w(L.WEAR, "Unknown path: $path")
        }
    }
    */

    private fun handleBolusPreCheck(dataMap: DataMap) {
        // Implementation for Bolus PreCheck
    }

    private fun handleBolusConfirmed(dataMap: DataMap) {
        // Implementation for Bolus Confirmed
    }

    private fun handleWizardPreCheck(dataMap: DataMap) {
        // Implementation for Wizard PreCheck
    }

    private fun handleWizardConfirmed(dataMap: DataMap) {
        // Implementation for Wizard Confirmed
    }

    private fun handleQuickWizardPreCheck(dataMap: DataMap) {
        // Implementation for Quick Wizard PreCheck
    }

    private fun handleFillPreCheck(dataMap: DataMap) {
        // Implementation for Fill PreCheck
    }

    private fun handleFillConfirmed(dataMap: DataMap) {
        // Implementation for Fill Confirmed
    }

    private fun handleCancelBolus() {
        // Implementation for Cancel Bolus
    }

    private fun handleECarbsPreCheck(dataMap: DataMap) {
        // Implementation for eCarbs PreCheck
    }

    private fun handleECarbsConfirmed(dataMap: DataMap) {
        // Implementation for eCarbs Confirmed
    }

    private fun handleTempTargetPreCheck(dataMap: DataMap) {
        // Implementation for TempTarget PreCheck
    }

    private fun handleTempTargetConfirmed(dataMap: DataMap) {
        // Implementation for TempTarget Confirmed
    }

    private fun sendLoopStates() {
        // Send loop states to wear
    }

    private fun handleLoopStateSelected(dataMap: DataMap) {
        // Implementation for Loop State Selected
    }

    private fun handleLoopStateConfirmed(dataMap: DataMap) {
        // Implementation for Loop State Confirmed
    }

    private fun handleProfileSwitchPreCheck(dataMap: DataMap) {
        // Implementation for Profile Switch PreCheck
    }

    private fun handleProfileSwitchConfirmed(dataMap: DataMap) {
        // Implementation for Profile Switch Confirmed
    }

    private fun handleHeartRate(dataMap: DataMap) {
        val rate = dataMap.getInt("rate", 0)
        val date = dataMap.getLong("date", System.currentTimeMillis())
        if (rate > 0) {
            // TODO: 現行 DB/Repository 層へ適合・再実装
            // val heartRate = HeartRate(date = date, value = rate)
            // persistenceLayer.insertHeartRate(heartRate)
        }
    }

    private fun handleStepsRate(dataMap: DataMap) {
        val steps = dataMap.getInt("steps", 0)
        val date = dataMap.getLong("date", System.currentTimeMillis())
        if (steps >= 0) {
            // TODO: 現行 DB/Repository 層へ適合・再実装
            // val stepsRate = StepsRate(date = date, value = steps)
            // persistenceLayer.insertStepsRate(stepsRate)
        }
    }

    private fun handleGetCustomWatchface() {
        // Send custom watchface data
    }

    fun resendData() {
        val now = System.currentTimeMillis()
        if (now - lastSendTime < 5000) return
        lastSendTime = now

        // Send status and data to Wear OS
        sendStatus()
    }

    fun sendStatus() {
        // Core status sync logic
    }

    fun sendTreatments() {
        // Core treatments sync logic
    }

    fun onCleanUp() {
        disposable.clear()
    }
}
