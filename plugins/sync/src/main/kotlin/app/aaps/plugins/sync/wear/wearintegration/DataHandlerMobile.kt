package app.aaps.plugins.sync.wear.wearintegration

import android.content.Context
import app.aaps.core.data.model.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.utils.AapsLogger
import com.google.android.gms.wearable.DataMap
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataHandlerMobile @Inject constructor(
    private val context: Context,
    private val rxBus: RxBus,
    override val rh: ResourceHelper,
    private val aapsLogger: AapsLogger
) : PluginBase(PluginType.SYNC, aapsLogger) {

    private val disposable = CompositeDisposable()
    private var lastSendTime = 0L

    init {
        /* TODO: RxBusWearData 型定義の参照整合性が確認でき次第解除
        disposable.add(
            rxBus.register(RxBusWearData::class.java) { event ->
                handleWearData(event)
            }
        )
        */
    }

    /* TODO: WearPath および DataMap の受け渡し定義確定後に解除
    private fun handleWearData(event: RxBusWearData) {
        val path = event.path
        val dataMap = event.dataMap

        aapsLogger.d(TAG, "Received wear path: $path")

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
            else -> aapsLogger.w(TAG, "Unknown path: $path")
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
            aapsLogger.d(TAG, "HeartRate received: rate=$rate, date=$date")
        }
    }

    private fun handleStepsRate(dataMap: DataMap) {
        val steps = dataMap.getInt("steps", 0)
        val date = dataMap.getLong("date", System.currentTimeMillis())
        if (steps >= 0) {
            aapsLogger.d(TAG, "StepsRate received: steps=$steps, date=$date")
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

    companion object {
        private const val TAG = "DataHandlerMobile"
    }
}
