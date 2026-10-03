package app.aaps.plugins.sync.nsShared

import org.json.JSONObject

/**
 *
 * {"mgdl":105,"mills":1455136282375,"device":"xDrip-BluetoothWixel","direction":"Flat","filtered":98272,"unfiltered":98272,"noise":1,"rssi":100}
 */
@Suppress("SpellCheckingInspection")
class NSSgv(val data: JSONObject) {

    val mgdl: Int?
        get() = if (data.has("mgdl") && !data.isNull("mgdl")) data.optInt("mgdl") else null
    val filtered: Int?
        get() = if (data.has("filtered") && !data.isNull("filtered")) data.optInt("filtered") else null
    val noise: Int?
        get() = if (data.has("noise") && !data.isNull("noise")) data.optInt("noise") else null
    val mills: Long?
        get() = if (data.has("mills") && !data.isNull("mills")) data.optLong("mills") else null
    val device: String?
        get() = if (data.has("device") && !data.isNull("device")) data.optString("device") else null
    val direction: String?
        get() = if (data.has("direction") && !data.isNull("direction")) data.optString("direction") else null
    val id: String?
        get() = if (data.has("_id") && !data.isNull("_id")) data.optString("_id") else null

}
