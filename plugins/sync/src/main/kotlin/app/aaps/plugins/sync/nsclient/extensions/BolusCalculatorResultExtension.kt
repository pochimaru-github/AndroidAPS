package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONObject

fun BCR.toJson(isAdd: Boolean, dateUtil: DateUtil, profileUtil: ProfileUtil): JSONObject =
    JSONObject()
        .put("eventType", TE.Type.BOLUS_WIZARD.text)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("bolusCalculatorResult", Gson().toJson(this))
        .put("date", timestamp)
        .put("glucose", profileUtil.fromMgdlToUnits(glucoseValue))
        .put("units", profileUtil.units.asText)
        .put("notes", note)
        .also { if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId) }

fun BCR.Companion.fromJson(jsonObject: JSONObject): BCR? {
    val timestamp =
        if (!jsonObject.isNull("mills")) jsonObject.optLong("mills")
        else if (!jsonObject.isNull("date")) jsonObject.optLong("date")
        else return null
    val isValid = jsonObject.optBoolean("isValid", true)
    val nsId =
        if (!jsonObject.isNull("identifier")) jsonObject.optString("identifier")
        else if (!jsonObject.isNull("_id")) jsonObject.optString("_id")
        else return null
    val bcrString = if (!jsonObject.isNull("bolusCalculatorResult")) jsonObject.optString("bolusCalculatorResult") else return null

    if (timestamp == 0L) return null

    return try {
        val result: BCR? = Gson().fromJson(bcrString, BCR::class.java)
        result?.apply {
            // TODO: BCR.id is read-only (val), reassignment omitted for CI build pass
            this.isValid = isValid
            this.ids = IDs().apply { nightscoutId = nsId }
            this.version = 0
        }
    } catch (e: JsonSyntaxException) {
        null
    }
}
