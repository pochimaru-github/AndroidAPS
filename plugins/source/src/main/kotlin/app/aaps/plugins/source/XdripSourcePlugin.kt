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
            aapsLogger.debug(LTag.BGSOURCE, "XdripSourceWorker doWorkAndLog started")
            var ret = Result.success()

            if (!xdripSourcePlugin.isEnabled()) {
                aapsLogger.debug(LTag.BGSOURCE, "XdripSourcePlugin is not enabled")
                return Result.success(workDataOf("Result" to "Plugin not enabled"))
            }
            val bundle = dataWorkerStorage.pickupBundle(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
            if (bundle == null) {
                aapsLogger.debug(LTag.BGSOURCE, "XdripSourceWorker bundle is missing")
                return Result.failure(workDataOf("Error" to "missing input data"))
            }

            // --- 【改善】あらゆる型の混入に対応した安全なパラメータ抽出 ---
            
            // トレンド（文字列）
            val rawSlope = bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)
                ?: bundle.getString("slopeName")
                ?: bundle.getString("direction")
                ?: bundle.getString("slope")
                ?: bundle.get(Intents.EXTRA_BG_SLOPE_NAME)?.toString()
                ?: ""

            val trendArrow = when (rawSlope.lowercase().trim()) {
                "flat", "rightarrow", "→", "stable" -> TrendArrow.FLAT
                "singleup", "uparrow", "↑", "rising" -> TrendArrow.SINGLE_UP
                "doubleup", "uparrowuparrow", "↑↑", "fastrising" -> TrendArrow.DOUBLE_UP
                "fortyfiveup", "sentryup", "↗", "slightrise" -> TrendArrow.FORTY_FIVE_UP
                "singledown", "downarrow", "↓", "falling" -> TrendArrow.SINGLE_DOWN
                "doubledown", "downarrowdownarrow", "↓↓", "fastfalling" -> TrendArrow.DOUBLE_DOWN
                "fortyfivedown", "sentrydown", "↘", "slightfall" -> TrendArrow.FORTY_FIVE_DOWN
                else -> TrendArrow.fromString(rawSlope)
            }

            // タイムスタンプ（Long / String / Int のいずれにも対応）
            val rawTimestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0L)
                .takeIf { it > 0L } ?: bundle.getLong("timestamp", 0L)
                .takeIf { it > 0L } ?: bundle.getLong("time", 0L)
                .takeIf { it > 0L } ?: bundle.get(Intents.EXTRA_TIMESTAMP)?.toString()?.toLongOrNull()
                ?: bundle.get("timestamp")?.toString()?.toLongOrNull()
                ?: 0L

            val correctedTimestamp = if (rawTimestamp > 0 && rawTimestamp < 10000000000L) {
                rawTimestamp * 1000L
            } else if (rawTimestamp <= 0) {
                dateUtil.now()
            } else {
                rawTimestamp
            }

            // 血糖値 (Double / Int / String のいずれにも対応)
            val bgEstimate = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)
                .takeIf { it > 0.0 } ?: bundle.getDouble("glucose", 0.0)
                .takeIf { it > 0.0 } ?: bundle.getDouble("sgv", 0.0)
                .takeIf { it > 0.0 } ?: bundle.get(Intents.EXTRA_BG_ESTIMATE)?.toString()?.toDoubleOrNull()
                ?: bundle.get("glucose")?.toString()?.toDoubleOrNull()
                ?: bundle.get("sgv")?.toString()?.toDoubleOrNull()
                ?: 0.0

            // 生データ値
            val rawValue = bundle.getDouble(Intents.EXTRA_RAW, 0.0)
                .takeIf { it > 0.0 } ?: bundle.getDouble("raw", 0.0)
                .takeIf { it > 0.0 } ?: bundle.get(Intents.EXTRA_RAW)?.toString()?.toDoubleOrNull()
                ?: bundle.get("raw")?.toString()?.toDoubleOrNull()
                ?: 0.0

            val sourceStr = bundle.getString(Intents.XDRIP_DATA_SOURCE)
                ?: bundle.getString("source")
                ?: ""

            val glucoseValues = mutableListOf<GV>()
            if (correctedTimestamp > 0 && bgEstimate > 0.0) {
                glucoseValues += GV(
                    timestamp = correctedTimestamp,
                    value = round(bgEstimate),
                    raw = round(rawValue),
                    noise = null,
                    trendArrow = trendArrow,
                    sourceSensor = SourceSensor.fromString(sourceStr)
                )
            }
            
            val newSensorStartTime = getSensorStartTime(bundle)
            // Retrieve last stored sensorStartTime from the database
            val lastTherapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
            val lastStoredSensorStartTime = lastTherapyEvent?.timestamp
            // Decide whether to update sensorStartTime or keep the last stored one
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

            // --- 【改善】データベース保存処理の確実な実行とログ詳細化 ---
            if (glucoseValues.isNotEmpty() && glucoseValues[0].timestamp > 0 && glucoseValues[0].value > 0.0) {
                val targetValue = glucoseValues[0]
                aapsLogger.debug(LTag.BGSOURCE, "Attempting to insert CGM data: timestamp=${targetValue.timestamp}, value=${targetValue.value}, trend=${targetValue.trendArrow}")

                try {
                    persistenceLayer.insertCgmSourceData(Sources.Xdrip, glucoseValues, emptyList(), finalSensorStartTime)
                        .doOnSuccess { savedValues ->
                            val count = savedValues.all().size
                            aapsLogger.debug(LTag.BGSOURCE, "Successfully inserted CGM data to DB. Saved count: $count")
                            savedValues.all().forEach { xdripSourcePlugin.detectSource(it) }
                        }
                        .doOnError { throwable ->
                            aapsLogger.error(LTag.BGSOURCE, "Error inserting CGM data to DB: ${throwable.message}", throwable)
                            ret = Result.failure(workDataOf("Error" to throwable.toString()))
                        }
                        .blockingGet()
                } catch (e: Exception) {
                    aapsLogger.error(LTag.BGSOURCE, "Exception during CGM data insertion: ${e.message}", e)
                    ret = Result.failure(workDataOf("Error" to e.toString()))
                }
            } else {
                aapsLogger.debug(LTag.BGSOURCE, "Missing or invalid glucoseValue: ${glucoseValues.getOrNull(0)}")
                return Result.failure(workDataOf("Error" to "missing glucoseValue"))
            }

            xdripSourcePlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
            return ret
    }
}
