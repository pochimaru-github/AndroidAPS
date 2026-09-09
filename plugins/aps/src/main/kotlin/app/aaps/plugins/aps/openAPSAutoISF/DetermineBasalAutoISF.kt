package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobStatus
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.profile.Profile
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

class DetermineBasalAutoISF {

    private val log = LoggerFactory.getLogger(DetermineBasalAutoISF::class.java)

    fun <RT : APSResult> determineBasal(
        glucoseStatus: GlucoseStatus,
        currentTemp: CurrentTemp,
        iobData: IobStatus,
        profile: Profile,
        autosensData: AutosensResult,
        mealData: MealData,
        microBolusAllowed: Boolean,
        reservoirData: Double?,
        resultClass: Class<RT>
    ): RT {

        val result = resultClass.getDeclaredConstructor().newInstance()

        if (glucoseStatus.glucose <= 0) {
            result.reason = "Invalid glucose reading"
            return result
        }

        var dynamicRatio = 1.0
        val targetBg = profile.getTargetMgdl()
        val currentGlucose = glucoseStatus.glucose

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

        result.rate = calculatedRate
        result.duration = 30
        result.reason = "AutoISF Active (Ratio: %.2f, Adj ISF: %.1f)".format(dynamicRatio, adjustedIsf)

        return result
    }
}
