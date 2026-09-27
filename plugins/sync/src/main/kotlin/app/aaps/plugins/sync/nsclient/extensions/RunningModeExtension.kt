package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
// TODO: utils (JsonHelper) 未解決参照につきコメントアウト (要再実装)
// import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun RM.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject {
    val reportedDuration = when (mode) {
        RM.Mode.OPEN_LOOP,
        RM.Mode.CLOSED_LOOP,
        RM.Mode.CLOSED_LOOP_LGS   -> 0

        RM.Mode.DISABLED_LOOP,
        RM.Mode.SUPER_BOLUS,
        RM.Mode.DISCONNECTED_PUMP,
        RM.Mode.SUSPENDED_BY_PUMP,
        RM.Mode.SUSPENDED_BY_DST,
        RM.Mode.SUSPENDED_BY_USER -> duration

        RM.Mode.RESUME            -> error("Invalid mode")
    }
    return JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AAPS")
        .put("eventType", TE.Type.APS_OFFLINE.text)
        .put("isValid", isValid)
        .put("duration", T.msecs(reportedDuration).mins())
        .put("durationInMilliseconds", reportedDuration)
        .put("originalDuration", duration)
        .put("mode", mode.name)
        .also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }
}

fun RM.Companion.fromJson(jsonObject: JSONObject): RM? {
    val timestamp =
        (if (jsonObject.has("mills") && !jsonObject.isNull("mills")) jsonObject.optLong("mills") else null)
            ?: (if (jsonObject.has("date") && !jsonObject.isNull("date")) jsonObject.optLong("date") else null)
            ?: return null
    val duration = jsonObject.optLong("duration", 0L)
    val durationInMilliseconds = if (jsonObject.has("durationInMilliseconds") && !jsonObject.isNull("durationInMilliseconds")) jsonObject.optLong("durationInMilliseconds") else null
    val originalDuration = if (jsonObject.has("originalDuration") && !jsonObject.isNull("originalDuration")) jsonObject.optLong("originalDuration") else null
    val isValid = if (jsonObject.has("isValid")) jsonObject.optBoolean("isValid", true) else true
    val id = (if (jsonObject.has("identifier") && !jsonObject.isNull("identifier")) jsonObject.optString("identifier") else null)
        ?: (if (jsonObject.has("_id") && !jsonObject.isNull("_id")) jsonObject.optString("_id") else null)
        ?: return null
    val pumpId = if (jsonObject.has("pumpId") && !jsonObject.isNull("pumpId")) jsonObject.optLong("pumpId") else null
    val pumpTypeStr = if (jsonObject.has("pumpType") && !jsonObject.isNull("pumpType")) jsonObject.optString("pumpType") else null
    val pumpType = PumpType.fromString(pumpTypeStr)
    val pumpSerial = if (jsonObject.has("pumpSerial") && !jsonObject.isNull("pumpSerial")) jsonObject.optString("pumpSerial") else null
    val modeStr = if (jsonObject.has("mode") && !jsonObject.isNull("mode")) jsonObject.optString("mode") else DEFAULT_MODE.name
    val mode = RM.Mode.fromString(modeStr)

    return RM(
        timestamp = timestamp,
        duration = originalDuration ?: durationInMilliseconds ?: T.mins(duration).msecs(),
        isValid = isValid,
        mode = mode
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}
