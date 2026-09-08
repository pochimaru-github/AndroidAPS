package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.plugins.aps.openAPS.determinebasal.data.AutosensData
import app.aaps.plugins.aps.openAPS.determinebasal.data.DetermineBasalResult
import app.aaps.plugins.aps.openAPS.determinebasal.data.GlucoseStatus
import app.aaps.plugins.aps.openAPS.determinebasal.data.IobStatus
import app.aaps.plugins.aps.openAPS.determinebasal.data.MealData
import app.aaps.plugins.aps.openAPS.determinebasal.data.Profile
import app.aaps.plugins.aps.openAPS.determinebasal.data.TempBasal
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
            return DetermineBasalResult().apply {
                reason = "Invalid glucose reading"
            }
        }

        val aCOBpredBG: Double? = profile.aCOBpredBG
        val UAMpredBG: Double? = profile.UAMpredBG

        var dynamicRatio = 1.0
        val targetBg = profile.targetBg

        val acob = aCOBpredBG
        val uam = UAMpredBG

        if (acob != null && acob > targetBg) {
            val bgDiff: Double = acob - targetBg
            val adjustmentFactor: Double = 0.005
            dynamicRatio += (bgDiff * adjustmentFactor)
        } else if (uam != null && uam > targetBg) {
            val bgDiff: Double = uam - targetBg
            val adjustmentFactor: Double = 0.004
            dynamicRatio += (bgDiff * adjustmentFactor)
        } else if (glucoseStatus.glucose > targetBg) {
            val bgDiff: Double = glucoseStatus.glucose.toDouble() - targetBg.toDouble()
            val adjustmentFactor: Double = 0.003
            dynamicRatio += (bgDiff * adjustmentFactor)
        }

        dynamicRatio = min(max(dynamicRatio, profile.minAutoSensRatio), profile.maxAutoSensRatio)

        val adjustedIsf = profile.isf / dynamicRatio
        log.debug("AutoISF adjusted ISF: original={}, adjusted={}, ratio={}", profile.isf, adjustedIsf, dynamicRatio)

        val targetDifference = glucoseStatus.glucose.toDouble() - profile.targetBg.toDouble()
        val requiredBasalRate = profile.currentBasal + (targetDifference / adjustedIsf)

        val calculatedRate = max(0.0, min(requiredBasalRate, profile.maxBasal))
        val calculatedDuration = 30
        val calculatedReason = "AutoISF Active (Ratio: %.2f, Adj ISF: %.1f)".format(dynamicRatio, adjustedIsf)

        return DetermineBasalResult().apply {
            rate = calculatedRate
            duration = calculatedDuration
            reason = calculatedReason
        }
    }
}
