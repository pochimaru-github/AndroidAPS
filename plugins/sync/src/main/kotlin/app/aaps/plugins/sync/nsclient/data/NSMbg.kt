package app.aaps.plugins.sync.nsclient.data

// TODO: JsonHelper 参照不可のため一時的にコメントアウト (要再実装)
// import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

class NSMbg(val json: JSONObject) {

    var date: Long = 0
    var mbg: Double = 0.0

    init {
        date = json.optLong("mills", 0L)
        mbg = json.optDouble("mgdl", 0.0)
    }

    fun id(): String? = if (json.has("_id") && !json.isNull("_id")) json.optString("_id") else null
    fun isValid(): Boolean = date != 0L && mbg != 0.0
}
