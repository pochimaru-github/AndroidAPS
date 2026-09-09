package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.interfaces.aps.APSResult as CoreAPSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.ScriptAPSResult
import app.aaps.core.interfaces.profile.Profile
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

class DetermineBasalAutoISF {

    private val log = LoggerFactory.getLogger(DetermineBasalAutoISF::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <RT : CoreAPSResult> determineBasal(
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

        val resultInstance = ScriptAPSResult().apply {
            algorithm = CoreAPSResult.Algorithm.AUTO_ISF
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
}
