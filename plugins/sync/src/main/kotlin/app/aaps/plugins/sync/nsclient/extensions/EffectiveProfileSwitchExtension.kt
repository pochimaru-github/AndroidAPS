package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
// TODO: utils (JsonHelper) 未解決参照につきコメントアウト (要再実装)
// import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun EPS.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("isValid", isValid)
        .put("eventType", TE.Type.NOTE.text) // move to separate collection when available in NS
        .put("profileJson", ProfileSealed.EPS(this, null).toPureNsJson(dateUtil).toString())
        .put("originalProfileName", originalProfileName)
        .put("originalCustomizedName", originalCustomizedName)
        .put("originalTimeshift", originalTimeshift)
        .put("originalPercentage", originalPercentage)
        .put("originalDuration", originalDuration)
        .put("originalEnd", originalEnd)
        .put("notes", originalCustomizedName)
        .also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }

fun EPS.Companion.fromJson(jsonObject: JSONObject, dateUtil: DateUtil): EPS? {
    val timestamp =
        (if (jsonObject.has("mills") && !jsonObject.isNull("mills")) jsonObject.optLong("mills") else null)
            ?: (if (jsonObject.has("date") && !jsonObject.isNull("date")) jsonObject.optLong("date") else null)
            ?: return null
    val originalTimeshift = jsonObject.optLong("originalTimeshift", 0L)
    val originalDuration = jsonObject.optLong("originalDuration", 0L)
    val originalEnd = jsonObject.optLong("originalEnd", 0L)
    val originalPercentage = jsonObject.optInt("originalPercentage", 100)
    val isValid = if (jsonObject.has("isValid")) jsonObject.optBoolean("isValid", true) else true
    val id = (if (jsonObject.has("identifier") && !jsonObject.isNull("identifier")) jsonObject.optString("identifier") else null)
        ?: (if (jsonObject.has("_id") && !jsonObject.isNull("_id")) jsonObject.optString("_id") else null)
        ?: return null
    val originalProfileName = if (jsonObject.has("originalProfileName") && !jsonObject.isNull("originalProfileName")) jsonObject.optString("originalProfileName") else null ?: return null
    val originalCustomizedName = if (jsonObject.has("originalCustomizedName") && !jsonObject.isNull("originalCustomizedName")) jsonObject.optString("originalCustomizedName") else null ?: return null
    val profileJson = if (jsonObject.has("profileJson") && !jsonObject.isNull("profileJson")) jsonObject.optString("profileJson") else null ?: return null
    val pumpId = if (jsonObject.has("pumpId") && !jsonObject.isNull("pumpId")) jsonObject.optLong("pumpId") else null
    val pumpTypeStr = if (jsonObject.has("pumpType") && !jsonObject.isNull("pumpType")) jsonObject.optString("pumpType") else null
    val pumpType = PumpType.fromString(pumpTypeStr)
    val pumpSerial = if (jsonObject.has("pumpSerial") && !jsonObject.isNull("pumpSerial")) jsonObject.optString("pumpSerial") else null

    if (timestamp == 0L) return null
    val pureProfile = pureProfileFromJson(JSONObject(profileJson), dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(pureProfile, null)

    return EPS(
        timestamp = timestamp,
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = profileSealed.units,
        originalProfileName = originalProfileName,
        originalCustomizedName = originalCustomizedName,
        originalTimeshift = originalTimeshift,
        originalPercentage = originalPercentage,
        originalDuration = originalDuration,
        originalEnd = originalEnd,
        iCfg = profileSealed.iCfg,
        isValid = isValid
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}

fun JSONObject.isEffectiveProfileSwitch() = has("originalProfileName")
