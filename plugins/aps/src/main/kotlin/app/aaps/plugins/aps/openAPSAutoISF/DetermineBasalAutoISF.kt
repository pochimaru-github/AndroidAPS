package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.profile.Profile
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

class DetermineBasalAutoISF {

    private val log = LoggerFactory.getLogger(DetermineBasalAutoISF::class.java)

    fun determineBasal(
        glucoseStatus: GlucoseStatus,
        currentTemp: CurrentTemp,
        iobData: Any?,
        profile: Profile,
        autosensData: AutosensResult,
        mealData: MealData,
        microBolusAllowed: Boolean,
        reservoirData: Double?
    ): Result {

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

        val targetDifference = currentGlucose - targetBg
        val currentBasal = profile.getBasal()
        val requiredBasalRate = currentBasal + (targetDifference / adjustedIsf)

        val maxBasal = profile.getMaxDailyBasal()
        val calculatedRate = max(0.0, min(requiredBasalRate, maxBasal))

        return Result(
            rate = calculatedRate,
            duration = 30,
            reason = "AutoISF Active (Ratio: %.2f, Adj ISF: %.1f)".format(dynamicRatio, adjustedIsf)
        )
    }

    class Result(
        val rate: Double = 0.0,
        val duration: Int = 0,
        val reason: String = ""
    )
}
