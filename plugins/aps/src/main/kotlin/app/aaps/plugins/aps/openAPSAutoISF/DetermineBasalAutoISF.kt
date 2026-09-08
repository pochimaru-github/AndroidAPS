package info.nightscout.androidaps.plugins.openaps.determinebasal

import info.nightscout.androidaps.plugins.openaps.determinebasal.data.DetermineBasalResult
import info.nightscout.androidaps.plugins.openaps.determinebasal.data.Profile
import info.nightscout.androidaps.plugins.openaps.determinebasal.data.GlucoseStatus
import info.nightscout.androidaps.plugins.openaps.determinebasal.data.IobStatus
import info.nightscout.androidaps.plugins.openaps.determinebasal.data.MealData
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

        // AutoISF Dynamic Ratio Adjustment Logic for v1.9
        var dynamicRatio = 1.0
        val targetBg = profile.targetBg

        if (glucoseStatus.glucose > targetBg) {
            val bgDiff = glucoseStatus.glucose - targetBg
            val adjustmentFactor = 0.005
            dynamicRatio += (bgDiff * adjustmentFactor)
        }

        // Cap dynamic ratio to safe limits
        dynamicRatio = min(max(dynamicRatio, profile.minAutoSensRatio), profile.maxAutoSensRatio)

        val adjustedIsf = profile.isf / dynamicRatio
        log.debug("AutoISF adjusted ISF: original={}, adjusted={}, ratio={}", profile.isf, adjustedIsf, dynamicRatio)

        // Basic temp basal logic calculation
        val bgRate = glucoseStatus.delta * 5.0
        val targetDifference = glucoseStatus.glucose - profile.targetBg
        val requiredBasalRate = profile.currentBasal + (targetDifference / adjustedIsf)

        result.rate = max(0.0, min(requiredBasalRate, profile.maxBasal))
        result.duration = 30
        result.reason = "AutoISF Active (Ratio: %.2f, Adj ISF: %.1f)".format(dynamicRatio, adjustedIsf)

        return result
    }
}
