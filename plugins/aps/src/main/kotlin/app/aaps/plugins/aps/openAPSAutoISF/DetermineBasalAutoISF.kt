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

        var dynamicRatio: Double = 1.0
        val targetBg: Double = profile.targetBg.toDouble()

        val acob: Double? = aCOBpredBG
        val uam: Double? = UAMpredBG

        if (acob != null && acob > targetBg) {
            val bgDiff: Double = acob - targetBg
            val adjustmentFactor: Double = 0.005
            dynamicRatio += (bgDiff * adjustmentFactor)
        } else if (uam != null && uam > targetBg) {
            val bgDiff: Double = uam - targetBg
            val adjustmentFactor: Double = 0.004
            dynamicRatio += (bgDiff * adjustmentFactor)
        } else if (glucoseStatus.glucose.toDouble() > targetBg) {
            val bgDiff: Double = glucoseStatus.glucose.toDouble() - targetBg
            val adjustmentFactor: Double = 0.003
            dynamicRatio += (bgDiff * adjustmentFactor)
        }

        dynamicRatio = min(max(dynamicRatio, profile.minAutoSensRatio.toDouble()), profile.maxAutoSensRatio.toDouble())

        val adjustedIsf: Double = profile.isf.toDouble() / dynamicRatio
        log.debug("AutoISF adjusted ISF: original={}, adjusted={}, ratio={}", profile.isf, adjustedIsf, dynamicRatio)

        val targetDifference: Double = glucoseStatus.glucose.toDouble() - targetBg
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
