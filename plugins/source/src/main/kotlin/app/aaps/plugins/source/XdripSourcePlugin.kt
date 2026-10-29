package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.round

@Singleton
class XdripSourcePlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus
) : AbstractBgSourceWithSensorInsertLogPlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon((app.aaps.core.objects.R.drawable.ic_blooddrop_48))
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.source_xdrip)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_xdrip),
    aapsLogger,
    rh
), BgSource, XDripSource {

    @VisibleForTesting
    var advancedFiltering = false
    override var sensorBatteryLevel = -1

    override fun advancedFilteringSupported(): Boolean = advancedFiltering

    fun notifyNewBg() {
        try {
            rxBus.send(EventNewBG())
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Failed to send EventNewBG", e)
        }
    }

    @VisibleForTesting
    fun detectSource(glucoseValue: GV) {
        advancedFiltering = arrayOf(
            SourceSensor.DEXCOM_NATIVE_UNKNOWN,
            SourceSensor.DEXCOM_G6_NATIVE,
            SourceSensor.DEXCOM_G7_NATIVE,
            SourceSensor.DEXCOM_G6_NATIVE_XDRIP,
            SourceSensor.DEXCOM_G7_NATIVE_XDRIP,
            SourceSensor.DEXCOM_G7_XDRIP,
            SourceSensor.LIBRE_2,
            SourceSensor.LIBRE_2_NATIVE,
            SourceSensor.LIBRE_3,
        ).any { it == glucoseValue.sourceSensor }
    }

    class XdripSourceWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var xdripSourcePlugin: XdripSourcePlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer
        @Inject lateinit var preferences: Preferences
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage

        fun getSensorStartTime(bundle: Bundle): Long? {
            val now = dateUtil.now()
            var sensorStartTime: Long? = if (preferences.get(BooleanKey.BgSourceCreateSensorChange)) {
                bundle.getLong(Intents.EXTRA_SENSOR_STARTED_AT, 0)
            } else {
                null
            }
            sensorStartTime?.let {
                if (abs(it - now) > T.months(1).msecs() || it > now) sensorStartTime = null
            }
            return sensorStartTime
        }

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!xdripSourcePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorkerStorage.pickupBundle(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))

            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: $bundle")

            val rawSlopeStr = bundle.getString(Intents.EXTRA_BG_SLOPE_NAME) 
                ?: bundle.getString("direction") 
                ?: bundle.getString("trend")
                ?: bundle.getString("trend_arrow")
                ?: bundle.getString("slopeStr")
                ?: bundle.get("slope")?.toString() 
                ?: bundle.get("slopeOrdinal")?.toString()
                ?: ""

            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip slope/trend raw: $rawSlopeStr")

            val resolvedTrend = when {
                rawSlopeStr.isEmpty() -> {
                    val slopeVal = bundle.getDouble("slope", 0.0)
                    when {
                        slopeVal > 3.5 -> TrendArrow.DOUBLE_UP
                        slopeVal > 1.5 -> TrendArrow.SINGLE_UP
                        slopeVal > 0.75 -> TrendArrow.FORTY_FIVE_UP
                        slopeVal >= -0.75 -> TrendArrow.FLAT
                        slopeVal >= -1.5 -> TrendArrow.FORTY_FIVE_DOWN
                        slopeVal >= -3.5 -> TrendArrow.SINGLE_DOWN
                        slopeVal < -3.5 -> TrendArrow.DOUBLE_DOWN
                        else -> TrendArrow.NONE
                    }
                }
                else -> {
                    val trend = TrendArrow.fromString(rawSlopeStr)
                    if (trend == TrendArrow.NONE) {
                        when (rawSlopeStr.lowercase().trim()) {
                            "flat", "rightarrow", "\u2192", "stable", "0" -> TrendArrow.FLAT
                            "singleup", "uparrow", "\u2191", "rising", "1" -> TrendArrow.SINGLE_UP
                            "doubleup", "uparrows", "\u21c8", "fastrise", "2" -> TrendArrow.DOUBLE_UP
                            "singledown", "downarrow", "\u2193", "falling", "3" -> TrendArrow.SINGLE_DOWN
                            "doubledown", "downarrows", "\u21ca", "fastfall", "4" -> TrendArrow.DOUBLE_DOWN
                            "fortyfiveup", "se", "\u2197", "fourtyfiveup", "5" -> TrendArrow.FORTY_FIVE_UP
                            "fortyfivedown", "ne", "\u2198", "fourtyfivedown", "6" -> TrendArrow.FORTY_FIVE_DOWN
                            else -> {
                                val dVal = rawSlopeStr.toDoubleOrNull()
                                if (dVal != null) {
                                    when {
                                        dVal > 3.5 -> TrendArrow.DOUBLE_UP
                                        dVal > 1.5 -> TrendArrow.SINGLE_UP
                                        dVal > 0.75 -> TrendArrow.FORTY_FIVE_UP
                                        dVal >= -0.75 -> TrendArrow.FLAT
                                        dVal >= -1.5 -> TrendArrow.FORTY_FIVE_DOWN
                                        dVal >= -3.5 -> TrendArrow.SINGLE_DOWN
                                        dVal < -3.5 -> TrendArrow.DOUBLE_DOWN
                                        else -> TrendArrow.NONE
                                    }
                                } else TrendArrow.NONE
                            }
                        }
                    } else trend
                }
            }

            val timestamp = when {
                bundle.containsKey(Intents.EXTRA_TIMESTAMP) && bundle.getLong(Intents.EXTRA_TIMESTAMP, 0) > 0 -> bundle.getLong(Intents.EXTRA_TIMESTAMP, 0)
                bundle.containsKey("timestamp") -> bundle.getLong("timestamp", 0)
                bundle.containsKey("date") -> bundle.getLong("date", 0)
                bundle.containsKey("time") -> bundle.getLong("time", 0)
                else -> bundle.get("timestamp")?.toString()?.toLongOrNull() ?: bundle.get("date")?.toString()?.toLongOrNull() ?: 0L
            }

            val bgValue = when {
                bundle.containsKey(Intents.EXTRA_BG_ESTIMATE) && bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0) > 0.0 -> bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)
                bundle.containsKey("sgv") -> bundle.getDouble("sgv", 0.0)
                bundle.containsKey("glucose") -> bundle.getDouble("glucose", 0.0)
                bundle.containsKey("mbg") -> bundle.getDouble("mbg", 0.0)
                bundle.containsKey("value") -> bundle.getDouble("value", 0.0)
                else -> bundle.get(Intents.EXTRA_BG_ESTIMATE)?.toString()?.toDoubleOrNull()
                    ?: bundle.get("sgv")?.toString()?.toDoubleOrNull()
                    ?: bundle.get("glucose")?.toString()?.toDoubleOrNull()
                    ?: bundle.get("value")?.toString()?.toDoubleOrNull()
                    ?: 0.0
            }

            val rawValue = when {
                bundle.containsKey(Intents.EXTRA_RAW) -> bundle.getDouble(Intents.EXTRA_RAW, 0.0)
                bundle.containsKey("raw") -> bundle.getDouble("raw", 0.0)
                bundle.containsKey("filtered") -> bundle.getDouble("filtered", 0.0)
                else -> bundle.get(Intents.EXTRA_RAW)?.toString()?.toDoubleOrNull()
                    ?: bundle.get("raw")?.toString()?.toDoubleOrNull()
                    ?: 0.0
            }

            val glucoseValues = mutableListOf<GV>()
            glucoseValues += GV(
                timestamp = timestamp,
                value = round(bgValue),
                raw = round(rawValue),
                noise = null,
                trendArrow = resolvedTrend,
                sourceSensor = SourceSensor.fromString(bundle.getString(Intents.XDRIP_DATA_SOURCE) ?: bundle.getString("source") ?: "")
            )
            val newSensorStartTime = getSensorStartTime(bundle)
            val lastTherapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
            val lastStoredSensorStartTime = lastTherapyEvent?.timestamp
            val finalSensorStartTime = when {
                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    abs(newSensorStartTime - lastStoredSensorStartTime) <= 300_000 -> {
                    aapsLogger.debug(LTag.BGSOURCE, "Sensor start time is within 5 minutes range, skipping update.")
                    null
                }
                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    newSensorStartTime < lastStoredSensorStartTime -> {
                    aapsLogger.debug(LTag.BGSOURCE, "Sensor start time is older than last stored time, skipping update.")
                    null
                }
                else -> newSensorStartTime
            }
            
            if (glucoseValues[0].timestamp > 0 && glucoseValues[0].value > 0.0) {
                persistenceLayer.insertCgmSourceData(Sources.Xdrip, glucoseValues, emptyList(), finalSensorStartTime)
                    .doOnError { ret = Result.failure(workDataOf("Error" to it.toString())) }
                    .blockingGet()
                    .also { savedValues -> 
                        savedValues.all().forEach { xdripSourcePlugin.detectSource(it) } 
                    }
                
                try {
                    xdripSourcePlugin.notifyNewBg()
                    aapsLogger.debug(LTag.BGSOURCE, "New BG data successfully saved, EventNewBG sent immediately.")
                } catch (e: Exception) {
                    aapsLogger.error(LTag.BGSOURCE, "Failed to trigger immediate refresh after saving BG", e)
                }

            } else {
                return Result.failure(workDataOf("Error" to "missing glucoseValue"))
            }

            xdripSourcePlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
            return ret
        }
    }
}
