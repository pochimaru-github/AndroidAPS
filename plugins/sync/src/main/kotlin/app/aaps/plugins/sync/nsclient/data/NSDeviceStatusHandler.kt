package app.aaps.plugins.sync.nsclient.data

import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
// TODO: utils (HtmlHelper, JsonHelper) 未解決参照につきコメントアウト (要再実装)
// import app.aaps.core.utils.HtmlHelper
// import app.aaps.core.utils.JsonHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@Singleton
class NSDeviceStatusHandler @Inject constructor(
    private val preferences: Preferences,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val overviewData: OverviewData,
    private val calculationWorkflow: CalculationWorkflow
) {

    private val disposable = CompositeDisposable()

    fun handleNewData(deviceStatuses: List<NSDeviceStatus>) {
        var configurationDetected = false
        for (i in deviceStatuses.size - 1 downTo 0) {
            val nsDeviceStatus = deviceStatuses[i]
            if (config.AAPSCLIENT) {
                updatePumpData(nsDeviceStatus)
                updateDeviceData(nsDeviceStatus)
                updateOpenApsData(nsDeviceStatus)
                updateUploaderData(nsDeviceStatus)
                calculationWorkflow.runOnReceivedPredictions(overviewData)
            }
            if (config.AAPSCLIENT && !configurationDetected) {
                nsDeviceStatus.configuration?.let {
                    // copy configuration of Insulin and Sensitivity from main AAPS
                    // runningConfiguration.apply(it)
                    configurationDetected = true // pick only newest
                }
            }
            if (config.APS) {
                nsDeviceStatus.pump?.let { preferences.put(BooleanNonKey.ObjectivesPumpStatusIsAvailableInNS, true) }  // Objective 0
            }
        }
    }

    private fun updateDeviceData(deviceStatus: NSDeviceStatus) {
        val createdAt = deviceStatus.createdAt?.let { dateUtil.fromISODateString(it) } ?: return
        processedDeviceStatusData.device?.let { if (createdAt.compareTo(it.createdAt) < 0) return } // take only newer record
        deviceStatus.device?.let {
            if (it.startsWith("openaps://")) processedDeviceStatusData.device = ProcessedDeviceStatusData.Device(createdAt, it.substring(10))
        }
    }

    private fun updatePumpData(nsDeviceStatus: NSDeviceStatus) {
        val pump = nsDeviceStatus.pump ?: return
        val clock = pump.clock?.let { dateUtil.fromISODateString(it) } ?: return
        processedDeviceStatusData.pumpData?.let { if (clock.compareTo(it.clock) < 0) return } // take only newer record

        // create new status and process data
        processedDeviceStatusData.pumpData = ProcessedDeviceStatusData.PumpData().also { deviceStatusPumpData ->
            deviceStatusPumpData.clock = clock
            pump.status?.status?.let { deviceStatusPumpData.status = it }
            pump.reservoir?.let { deviceStatusPumpData.reservoir = it }
            pump.reservoirDisplayOverride?.let { deviceStatusPumpData.reservoirDisplayOverride = it }
            pump.battery?.percent?.let {
                deviceStatusPumpData.isPercent = true
                deviceStatusPumpData.percent = it
            }
            pump.battery?.voltage?.let {
                deviceStatusPumpData.isPercent = false
                deviceStatusPumpData.voltage = it
            }
            pump.extended?.let {
                val extended = StringBuilder()
                val keys: Iterator<*> = it.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    val value = it.optString(key)
                    extended.append("<b>$key:</b> $value<br>")
                }
                @Suppress("DEPRECATION")
                deviceStatusPumpData.extended = android.text.Html.fromHtml(extended.toString())
                deviceStatusPumpData.activeProfileName = if (it.has("ActiveProfile") && !it.isNull("ActiveProfile")) it.optString("ActiveProfile") else null
            }
        }
    }

    private fun updateOpenApsData(nsDeviceStatus: NSDeviceStatus) {
        nsDeviceStatus.openaps?.suggested?.let {
            val timestamp = if (it.has("timestamp") && !it.isNull("timestamp")) it.optString("timestamp") else null
            timestamp?.let { ts ->
                val clock = dateUtil.fromISODateString(ts)
                // check if this is new data
                if (clock.compareTo(processedDeviceStatusData.openAPSData.clockSuggested) > 0) {
                    try {
                        processedDeviceStatusData.openAPSData.suggested = RT.deserialize(it.toString()).apply { this.timestamp = clock }
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.NSCLIENT, e.stackTraceToString())
                    }
                    processedDeviceStatusData.openAPSData.clockSuggested = clock
                    processedDeviceStatusData.getAPSResult()?.let { apsResult ->
                        disposable += persistenceLayer.insertOrUpdateApsResult(apsResult).subscribe()
                    }
                }
            }
        }
        nsDeviceStatus.openaps?.enacted?.let {
            val timestamp = if (it.has("timestamp") && !it.isNull("timestamp")) it.optString("timestamp") else null
            timestamp?.let { ts ->
                val clock = dateUtil.fromISODateString(ts)
                // check if this is new data
                if (clock.compareTo(processedDeviceStatusData.openAPSData.clockEnacted) > 0) {
                    try {
                        processedDeviceStatusData.openAPSData.enacted = RT.deserialize(it.toString()).apply { this.timestamp = clock }
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.NSCLIENT, e.stackTraceToString())
                    }
                    processedDeviceStatusData.openAPSData.clockEnacted = clock
                }
            }
        }
    }

    private fun updateUploaderData(nsDeviceStatus: NSDeviceStatus) {
        val clock = nsDeviceStatus.createdAt?.let { dateUtil.fromISODateString(it) } ?: return
        val device = nsDeviceStatus.device ?: return
        val battery = nsDeviceStatus.uploaderBattery ?: nsDeviceStatus.uploader?.battery ?: return
        val isCharging = nsDeviceStatus.isCharging

        var uploader = processedDeviceStatusData.uploaderMap[device]
        // check if this is new data
        if (uploader == null || clock.compareTo(uploader.clock) > 0) {
            if (uploader == null) uploader = ProcessedDeviceStatusData.Uploader()
            uploader.battery = battery
            uploader.clock = clock
            uploader.isCharging = isCharging
            processedDeviceStatusData.uploaderMap[device] = uploader
        }
    }
}
