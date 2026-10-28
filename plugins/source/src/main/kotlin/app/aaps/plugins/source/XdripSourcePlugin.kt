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
    aapsLogger: AAPSLogger
) : AbstractBgSourceWithSensorInsertLogPlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon((app.aaps.core.objects.R.drawable.ic_blooddrop_48))
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.source_xdrip)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_xdrip),
    aapsLogger, rh
), BgSource, XDripSource {

    @VisibleForTesting
    var advancedFiltering = false
    override var sensorBatteryLevel = -1

    override fun advancedFilteringSupported(): Boolean = advancedFiltering

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

    // cannot be inner class because of needed injection
    class XdripSourceWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var xdripSourcePlugin: XdripSourcePlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer
        @Inject lateinit var preferences: Preferences
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage
        @Inject lateinit var rxBus: app.aaps.core.interfaces.rx.bus.RxBus

        fun getSensorStartTime(bundle: Bundle): Long? {
            val now = dateUtil.now()
            var sensorStartTime: Long? = if (preferences.get(BooleanKey.BgSourceCreateSensorChange)) {
                bundle.getLong(Intents.EXTRA_SENSOR_STARTED_AT, 0)
            } else {
                null
            }
            // check start time validity
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

            // xDrip+から送られてくる実際のBundleの中身を全件ログ出力して事実を確定する
            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip bundle dump start -----")
            for (key in bundle.keySet()) {
                val obj = bundle.get(key)
                aapsLogger.debug(LTag.BGSOURCE, "Key: [$key] | Value: [$obj] | Type: [${obj?.javaClass?.name}]")
            }
            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip bundle dump end -----")

            val rawSlope = bundle.getString(Intents.EXTRA_BG_SLOPE_NAME) 
                ?: bundle.getString("direction") 
                ?: bundle.getString("slope") 
                ?: ""

            val resolvedTrend = when {
                rawSlope.isEmpty() -> TrendArrow.NONE
                else -> TrendArrow.fromString(rawSlope).let { trend ->
                    if (trend == TrendArrow.NONE) {
                        when (rawSlope.lowercase().trim()) {
                            "flat", "rightarrow", "→", "stable", "3" -> TrendArrow.FLAT
                            "singleup", "uparrow", "↑", "rising", "2" -> TrendArrow.SINGLE_UP
                            "doubleup", "uparrows", "⇈", "fastrise", "1" -> TrendArrow.DOUBLE_UP
                            "singledown", "downarrow", "↓", "falling", "4" -> TrendArrow.SINGLE_DOWN
                            "doubledown", "downarrows", "⇊", "fastfall", "5" -> TrendArrow.DOUBLE_DOWN
                            "fortyfiveup", "se", "↗", "fourtyfiveup" -> TrendArrow.FORTY_FIVE_UP
                            "fortyfivedown", "ne", "↘", "fourtyfivedown" -> TrendArrow.FORTY_FIVE_DOWN
                            else -> TrendArrow.NONE
                        }
                    } else trend
                }
            }

            val glucoseValues = mutableListOf<GV>()
            glucoseValues += GV(
                timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0),
                value = round(bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)),
                raw = round(bundle.getDouble(Intents.EXTRA_RAW, 0.0)),
                noise = null,
                trendArrow = resolvedTrend,
                sourceSensor = SourceSensor.fromString(bundle.getString(Intents.XDRIP_DATA_SOURCE) ?: "")
            )

            val newSensorStartTime = getSensorStartTime(bundle)
            val lastTherapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
            val lastStoredSensorStartTime = lastTherapyEvent?.timestamp
            val finalSensorStartTime = when {
                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    abs(newSensorStartTime - lastStoredSensorStartTime) <= 300_000 -> null
                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    newSensorStartTime < lastStoredSensorStartTime -> null
                else -> newSensorStartTime
            }
            
            if (glucoseValues[0].timestamp > 0 && glucoseValues[0].value > 0.0) {
                persistenceLayer.insertCgmSourceData(Sources.Xdrip, glucoseValues, emptyList(), finalSensorStartTime)
                    .doOnError { ret = Result.failure(workDataOf("Error" to it.toString())) }
                    .blockingGet()
                    .also { savedValues -> 
                        savedValues.all().forEach { xdripSourcePlugin.detectSource(it) } 
                    }
            } else {
                return Result.failure(workDataOf("Error" to "missing glucoseValue"))
            }

            xdripSourcePlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
            return ret
        }
    }
}
