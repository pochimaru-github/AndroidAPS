package app.aaps.plugins.aps.openAPSAutoISF

import android.text.Spanned
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.profile.Profile
import org.json.JSONObject
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

class DetermineBasalAutoISF {

    private val log = LoggerFactory.getLogger(DetermineBasalAutoISF::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <RT : APSResult> determineBasal(
        glucoseStatus: GlucoseStatus,
        currentTemp: CurrentTemp,
        iobData: Any?,
        profile: Profile,
        autosensData: AutosensResult,
        mealData: MealData,
        microBolusAllowed: Boolean,
        reservoirData: Double?
    ): RT {

        val targetBg = profile.getTargetMgdl()
        val currentGlucose = glucoseStatus.glucose
        var dynamicRatio = 1.0

        if (currentGlucose > targetBg) {
            val bgDiff = currentGlucose - targetBg
            val adjustmentFactor = 0.003
            dynamicRatio += (bgDiff * adjustmentFactor)
        }

        val minRatio = 0.5
        val maxRatio = 2.0
        dynamicRatio = min(max(dynamicRatio, minRatio), maxRatio)

        val originalIsf = profile.getIsfMgdl("DetermineBasalAutoISF") ?: 100.0
        val adjustedIsf = originalIsf / dynamicRatio
        log.debug("AutoISF adjusted ISF: original={}, adjusted={}, ratio={}", originalIsf, adjustedIsf, dynamicRatio)

        val targetDifference = currentGlucose - targetBg
        val currentBasal = profile.getBasal()
        val requiredBasalRate = currentBasal + (targetDifference / adjustedIsf)

        val maxBasal = profile.getMaxDailyBasal()
        val calculatedRate = max(0.0, min(requiredBasalRate, maxBasal))

        val resultInstance = AutoISFAPSResult().apply {
            algorithm = APSResult.Algorithm.AUTO_ISF
            rate = calculatedRate
            duration = 30
            reason = "AutoISF Active (Ratio: %.2f, Adj ISF: %.1f)".format(dynamicRatio, adjustedIsf)
            autosensResult = autosensData
            this.currentTemp = currentTemp
            this.glucoseStatus = glucoseStatus
            this.mealData = mealData
            carbsReq = 0
            carbsReqWithin = 0
        }

        return resultInstance as RT
    }

    private class AutoISFAPSResult : APSResult {
        override fun with(result: APSResult): APSResult = this
        override var date: Long = System.currentTimeMillis()
        override var reason: String = ""
        override var rate: Double = 0.0
        override var percent: Int = 0
        override var duration: Int = 0
        override var smb: Double = 0.0
        override var usePercent: Boolean = false
        override var carbsReq: Int = 0
        override var carbsReqWithin: Int = 0
        override var deliverAt: Long = 0
        override var targetBG: Double = 0.0
        override var hasPredictions: Boolean = false
        override var variableSens: Double? = null
        override var isfMgdlForCarbs: Double? = null
        override var scriptDebug: List<String>? = null

        override val predictionsAsGv: MutableList<GV> = mutableListOf()
        override val latestPredictionsTime: Long = 0
        override val isChangeRequested: Boolean = false
        override var isTempBasalRequested: Boolean = false

        override val carbsRequiredText: String get() = if (carbsReq > 0) "${carbsReq}g" else ""

        override var inputConstraints: Constraint<Double>? = null
        override var rateConstraint: Constraint<Double>? = null
        override var percentConstraint: Constraint<Int>? = null
        override var smbConstraint: Constraint<Double>? = null

        override var algorithm: APSResult.Algorithm = APSResult.Algorithm.AUTO_ISF
        override var autosensResult: AutosensResult? = null
        override var iobData: Array<IobTotal>? = null
        override var glucoseStatus: GlucoseStatus? = null
        override var currentTemp: CurrentTemp? = null
        override var oapsProfile: OapsProfile? = null
        override var oapsProfileAutoIsf: OapsProfileAutoIsf? = null
        override var mealData: MealData? = null

        override fun resultAsString(): String = reason
        override fun resultAsSpanned(): Spanned = android.text.SpannedString(reason)
        override fun newAndClone(): APSResult = this
        override fun json(): JSONObject? = null
        override fun predictions(): Predictions? = null
        override fun rawData(): Any = reason
    }
}
