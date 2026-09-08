package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.plugins.aps.openaps.determinebasal.data.AutosensData
import app.aaps.plugins.aps.openaps.determinebasal.data.DetermineBasalResult
import app.aaps.plugins.aps.openaps.determinebasal.data.GlucoseStatus
import app.aaps.plugins.aps.openaps.determinebasal.data.IobStatus
import app.aaps.plugins.aps.openaps.determinebasal.data.MealData
import app.aaps.plugins.aps.openaps.determinebasal.data.Profile
import app.aaps.plugins.aps.openaps.determinebasal.data.TempBasal
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
        val targetBg = profile.targetBg

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
        } else if (glucoseStatus.glucose > targetBg) {
            val bgDiff = glucoseStatus.glucose - targetBg
            val adjustmentFactor = 0.003
            dynamicRatio += (bgDiff * adjustmentFactor)
        }

        dynamicRatio = min(max(dynamicRatio, profile.minAutoSensRatio), profile.maxAutoSensRatio)

        val adjustedIsf = profile.isf / dynamicRatio
        log.debug("AutoISF adjusted ISF: original={}, adjusted={}, ratio={}", profile.isf, adjustedIsf, dynamicRatio)

        val targetDifference = glucoseStatus.glucose - profile.targetBg
        val requiredBasalRate = profile.currentBasal + (targetDifference / adjustedIsf)

        result.rate = max(0.0, min(requiredBasalRate, profile.maxBasal))
        result.duration = 30
        result.reason = "AutoISF Active (Ratio: %.2f, Adj ISF: %.1f)".format(dynamicRatio, adjustedIsf)

        return result
    }
}
