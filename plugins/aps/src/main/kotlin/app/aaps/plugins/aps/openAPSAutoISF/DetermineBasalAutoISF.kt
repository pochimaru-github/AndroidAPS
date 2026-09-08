package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.aps.AutosensData
import app.aaps.core.aps.DetermineBasalResult
import app.aaps.core.aps.GlucoseStatus
import app.aaps.core.aps.IobStatus
import app.aaps.core.aps.MealData
import app.aaps.core.aps.Profile
import app.aaps.core.aps.TempBasal
import org.slf.LoggerFactory
import kotlin.math.max
import kotlin.math.min

class DetermineBasalAutoISF {

    private val log = LoggerFactory.getLogger(DetermineBasalAutoISF::class.java)

    fun determineBasal(
        glucoseStatus: GlucoseStatus,
        currentTemp: TempBasal,
        iobData: IobStatus,
        profile: Profile,
        autosensData: AutosensData,
        mealData: MealData,
        microBolusAllowed: Boolean,
        reservoirData: Double?
    ): DetermineBasalResult {

        if (glucoseStatus.glucose <= 0) {
            return DetermineBasalResult(
                reason = "Invalid glucose reading"
            )
        }

        val aCOBpredBG: Double? = profile.aCOBpredBG?.toDouble()
        val UAMpredBG: Double? = profile.UAMpredBG?.toDouble()

        var dynamicRatio: Double = 1.0
        val targetBg: Double = profile.targetBg.toDouble()

        val acob: Double? = aCOBpredBG
        val uam: Double? = UAMpredBG
        val currentGlucose: Double = glucoseStatus.glucose.toDouble()

        if (acob != null && acob > targetBg) {
            val bgDiff: Double = acob - targetBg
            val adjustmentFactor: Double = 0.005
            dynamicRatio += (bgDiff * adjustmentFactor)
        } else if (uam != null && uam > targetBg) {
            val bgDiff: Double = uam - targetBg
            val adjustmentFactor: Double = 0.004
            dynamicRatio += (bgDiff * adjustmentFactor)
        } else if (currentGlucose > targetBg) {
            val bgDiff: Double = currentGlucose - targetBg
            val adjustmentFactor: Double = 0.003
            dynamicRatio += (bgDiff * adjustmentFactor)
        }

        val minRatio: Double = profile.minAutoSensRatio.toDouble()
        val maxRatio: Double = profile.maxAutoSensRatio.toDouble()
        dynamicRatio = min(max(dynamicRatio, minRatio), maxRatio)

        val adjustedIsf: Double = profile.isf.toDouble() / dynamicRatio
        log.debug("AutoISF adjusted ISF: original={}, adjusted={}, ratio={}", profile.isf, adjustedIsf, dynamicRatio)

        val targetDifference: Double = currentGlucose - targetBg
        val requiredBasalRate: Double = profile.currentBasal.toDouble() + (targetDifference / adjustedIsf)

        val calculatedRate: Double = max(0.0, min(requiredBasalRate, profile.maxBasal.toDouble()))
        val calculatedDuration: Int = 30
        val calculatedReason: String = "AutoISF Active (Ratio: %.2f, Adj ISF: %.1f)".format(dynamicRatio, adjustedIsf)

        return DetermineBasalResult(
            rate = calculatedRate,
            duration = calculatedDuration,
            reason = calculatedReason
        )
    }
}
