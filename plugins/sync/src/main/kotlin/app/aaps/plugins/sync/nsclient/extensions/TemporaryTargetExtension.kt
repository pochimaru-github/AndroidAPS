package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import org.json.JSONObject

fun TT.Companion.fromJson(jsonObject: JSONObject, profileUtil: ProfileUtil): TT? {
    val units = GlucoseUnit.fromText(
        if (!jsonObject.isNull("units")) jsonObject.optString("units", GlucoseUnit.MGDL.asText) else GlucoseUnit.MGDL.asText
    )
    val timestamp =
        if (!jsonObject.isNull("mills")) jsonObject.optLong("mills")
        else if (!jsonObject.isNull("date")) jsonObject.optLong("date")
        else return null
    val duration = if (!jsonObject.isNull("duration")) jsonObject.optLong("duration") else return null
    val durationInMilliseconds = if (!jsonObject.isNull("durationInMilliseconds")) jsonObject.optLong("durationInMilliseconds") else null
    var low = jsonObject.optDouble("targetBottom", 0.0)
    low = profileUtil.convertToMgdl(low, units)
    var high = jsonObject.optDouble("targetTop", 0.0)
    high = profileUtil.convertToMgdl(high, units)
    val reasonString = if (duration != 0L) {
        if (!jsonObject.isNull("reason")) jsonObject.optString("reason") else return null
    } else ""
    // this string can be localized from NS, it will not work in this case CUSTOM will be used
    val reason = TT.Reason.fromString(reasonString)
    val nsId =
        if (!jsonObject.isNull("identifier")) jsonObject.optString("identifier")
        else if (!jsonObject.isNull("_id")) jsonObject.optString("_id")
        else return null
    val isValid = jsonObject.optBoolean("isValid", true)

    if (timestamp == 0L) return null

    if (duration > 0L) {
        // not ending event
        // TODO: Replaced operator comparison with explicit compareTo calls for CI build pass
        if (low.compareTo(Constants.MIN_TT_MGDL) < 0) return null
        if (low.compareTo(Constants.MAX_TT_MGDL) > 0) return null
        if (high.compareTo(Constants.MIN_TT_MGDL) < 0) return null
        if (high.compareTo(Constants.MAX_TT_MGDL) > 0) return null
        if (low.compareTo(high) > 0) return null
    }
    val tt = TT(
        timestamp = timestamp,
        duration = durationInMilliseconds ?: T.mins(duration).msecs(),
        reason = reason,
        lowTarget = low,
        highTarget = high,
        isValid = isValid
    )
    tt.ids.nightscoutId = nsId
    return tt
}

fun TT.toJson(isAdd: Boolean, dateUtil: DateUtil, profileUtil: ProfileUtil): JSONObject =
    JSONObject()
        .put("eventType", TE.Type.TEMPORARY_TARGET.text)
        .put("duration", T.msecs(duration).mins())
        .put("durationInMilliseconds", duration)
        .put("isValid", isValid)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("timestamp", timestamp)
        .put("enteredBy", "AndroidAPS").also {
            if (lowTarget > 0) it
                .put("reason", reason.text)
                .put("targetBottom", profileUtil.fromMgdlToUnits(lowTarget))
                .put("targetTop", profileUtil.fromMgdlToUnits(highTarget))
                .put("units", profileUtil.units.asText)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }
