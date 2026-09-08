package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.implementation.aps.AutosensData
import app.aaps.implementation.aps.DetermineBasalResult
import app.aaps.implementation.aps.GlucoseStatus
import app.aaps.implementation.aps.IobStatus
import app.aaps.implementation.aps.MealData
import app.aaps.implementation.aps.Profile
import app.aaps.implementation.aps.TempBasal
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

        val result = DetermineBasalResult()

        if (glucoseStatus.glucose <= 0) {
            result.reason = "Invalid glucose reading"
            return result
        }

        val aCOBpredBG: Double? = profile.aCOBpredBG
        val UAMpredBG: Double? = profile.UAMpredBG

        var dynamicRatio = 1.0
        val targetBg = profile.targetBg.toDouble()

        val acob = aCOBpredBG
        val uam = UAMpredBG

        if (acob != null && acob > targetBg) {
            val bgDiff = acob - targetBg
            val adjustmentFactor = 0.005
            dynamicRatio += (bgDiff * adjustmentFactor)
        } else if (uam != null && uam > targetBg) {
            val bgDiff = uam - targetBg
            val adjustmentFactor = 0.004
            dynamicRatio += (bgDiff * adjustmentFactor)
        } else if (glucoseStatus.glucose.toDouble() > targetBg) {
            val bgDiff = glucoseStatus.glucose.toDouble() - targetBg
            val adjustmentFactor = 0.003
            dynamicRatio += (bgDiff * adjustmentFactor)
        }

        dynamicRatio = min(max(dynamicRatio, profile.minAutoSensRatio.toDouble()), profile.maxAutoSensRatio.toDouble())

        val adjustedIsf = profile.isf.toDouble() / dynamicRatio
        log.debug("AutoISF adjusted ISF: original={}, adjusted={}, ratio={}", profile.isf, adjustedIsf, dynamicRatio)

        val targetDifference = glucoseStatus.glucose.toDouble() - targetBg
        val requiredBasalRate = profile.currentBasal.toDouble() + (targetDifference / adjustedIsf)

        result.rate = max(0.0, min(requiredBasalRate, profile.maxBasal.toDouble()))
        result.duration = 30
        result.reason = "AutoISF Active (Ratio: %.2f, Adj ISF: %.1f)".format(dynamicRatio, adjustedIsf)

        return result
    }
}
