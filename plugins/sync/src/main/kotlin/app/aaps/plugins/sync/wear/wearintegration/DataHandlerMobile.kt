package app.aaps.plugins.sync.wear.wearintegration

import android.content.Context
import app.aaps.core.interfaces.AapsLogger
import app.aaps.core.interfaces.AapsServices
import app.aaps.core.interfaces.AapsServices.Companion.APS
import app.aaps.core.interfaces.ActionData
import app.aaps.core.interfaces.Automation
import app.aaps.core.interfaces.ConfigBuilder
import app.aaps.core.interfaces.NSClient
import app.aaps.core.interfaces.PersistenceLayer
import app.aaps.core.interfaces.ProfileFunction
import app.aaps.core.interfaces.Pump
import app.aaps.core.interfaces.PumpEnactResult
import app.aaps.core.interfaces.RxBus
import app.aaps.core.interfaces.TDD
import app.aaps.core.interfaces.TherapyEngine
import app.aaps.core.interfaces.Treatments
import app.aaps.core.interfaces.Wear
import app.aaps.core.interfaces.WearUtils
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.objectMapper
import app.aaps.core.interfaces.rx.bus.RxBusWearData
import app.aaps.core.interfaces.wear.WearPath
import app.aaps.core.plugins.PluginBase
import app.aaps.core.plugins.PluginType
import app.aaps.core.units.GlucoseUnit
import app.aaps.core.utils.DateUtil
import app.aaps.core.utils.FabricUtils
import app.aaps.core.utils.JsonParser
import app.aaps.core.utils.NumberUtils
import app.aaps.core.utils.SafeParse
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.StepsRate
import app.aaps.plugins.sync.wear.wearintegration.WearDataService.Companion.SYNC_KEY
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataHandlerMobile @Inject constructor(
    private val context: Context,
    private val rxBus: RxBus,
    private val wearUtils: WearUtils,
    private val aapsServices: AapsServices,
    private val persistenceLayer: PersistenceLayer,
    private val treatments: Treatments,
    private val profileFunction: ProfileFunction,
    private val configBuilder: ConfigBuilder,
    private val nsClient: NSClient,
    private val tdd: TDD,
    private val automation: Automation
) : PluginBase(PluginType.SYNC) {

    private val disposable = CompositeDisposable()
    private var lastSendTime = 0L

    init {
        disposable += rxBus.register(RxBusWearData::class.java) { event ->
            handleWearData(event)
        }
    }

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
            val heartRate = HeartRate(date = date, value = rate)
            persistenceLayer.insertHeartRate(heartRate)
        }
    }

    private fun handleStepsRate(dataMap: DataMap) {
        val steps = dataMap.getInt("steps", 0)
        val date = dataMap.getLong("date", System.currentTimeMillis())
        if (steps >= 0) {
            val stepsRate = StepsRate(date = date, value = steps)
            persistenceLayer.insertStepsRate(stepsRate)
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

    override fun onCleanUp() {
        disposable.clear()
    }
}
