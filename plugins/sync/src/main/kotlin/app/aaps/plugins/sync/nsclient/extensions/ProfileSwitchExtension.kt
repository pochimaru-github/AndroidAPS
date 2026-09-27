package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
// TODO: utils (JsonHelper) 未解決参照につきコメントアウト (要再実装)
// import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun PS.toJson(isAdd: Boolean, dateUtil: DateUtil, decimalFormatter: DecimalFormatter): JSONObject =
    JSONObject()
        .put("timeshift", timeshift)
        .put("percentage", percentage)
        .put("duration", T.msecs(duration).mins())
        .put("profile", getCustomizedName(decimalFormatter))
        .put("originalProfileName", profileName)
        .put("originalDuration", duration)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("isValid", isValid)
        .put("eventType", TE.Type.PROFILE_SWITCH.text)
        .also { // remove customization to store original profileJson in toPureNsJson call
            timeshift = 0
            percentage = 100
        }
        .put("profileJson", ProfileSealed.PS(value = this, activePlugin = null).toPureNsJson(dateUtil).toString())
        .also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }

fun PS.Companion.fromJson(jsonObject: JSONObject, dateUtil: DateUtil, activePlugin: ActivePlugin): PS? {
    val timestamp =
        (if (jsonObject.has("mills") && !jsonObject.isNull("mills")) jsonObject.optLong("mills") else null)
            ?: (if (jsonObject.has("date") && !jsonObject.isNull("date")) jsonObject.optLong("date") else null)
            ?: return null
    val duration = jsonObject.optLong("duration", 0L)
    val originalDuration = if (jsonObject.has("originalDuration") && !jsonObject.isNull("originalDuration")) jsonObject.optLong("originalDuration") else null
    val timeshift = jsonObject.optLong("timeshift", 0L)
    val percentage = jsonObject.optInt("percentage", 100)
    val isValid = if (jsonObject.has("isValid")) jsonObject.optBoolean("isValid", true) else true
    val id = (if (jsonObject.has("identifier") && !jsonObject.isNull("identifier")) jsonObject.optString("identifier") else null)
        ?: (if (jsonObject.has("_id") && !jsonObject.isNull("_id")) jsonObject.optString("_id") else null)
        ?: return null
    val profileName = if (jsonObject.has("profile") && !jsonObject.isNull("profile")) jsonObject.optString("profile") else null ?: return null
    val originalProfileName = if (jsonObject.has("originalProfileName") && !jsonObject.isNull("originalProfileName")) jsonObject.optString("originalProfileName") else null
    val profileJson = if (jsonObject.has("profileJson") && !jsonObject.isNull("profileJson")) jsonObject.optString("profileJson") else null
    val pumpId = if (jsonObject.has("pumpId") && !jsonObject.isNull("pumpId")) jsonObject.optLong("pumpId") else null
    val pumpTypeStr = if (jsonObject.has("pumpType") && !jsonObject.isNull("pumpType")) jsonObject.optString("pumpType") else null
    val pumpType = PumpType.fromString(pumpTypeStr)
    val pumpSerial = if (jsonObject.has("pumpSerial") && !jsonObject.isNull("pumpSerial")) jsonObject.optString("pumpSerial") else null

    if (timestamp == 0L) return null
    val pureProfile =
        if (profileJson == null) { // entered through NS, no JSON attached
            val profilePlugin = activePlugin.activeProfileSource
            val store = profilePlugin.profile ?: return null
            store.getSpecificProfile(profileName) ?: return null
        } else pureProfileFromJson(JSONObject(profileJson), dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(value = pureProfile, activePlugin = null)

    return PS(
        timestamp = timestamp,
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = profileSealed.units,
        profileName = originalProfileName ?: profileName,
        timeshift = timeshift,
        percentage = percentage,
        duration = originalDuration ?: T.mins(duration).msecs(),
        iCfg = profileSealed.iCfg,
        isValid = isValid
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}
