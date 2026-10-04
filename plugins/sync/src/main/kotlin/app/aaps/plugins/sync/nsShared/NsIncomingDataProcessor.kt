package app.aaps.plugins.sync.nsShared

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import app.aaps.core.nssdk.localmodel.treatment.NSBolusWizard
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import app.aaps.core.nssdk.localmodel.treatment.NSEffectiveProfileSwitch
import app.aaps.core.nssdk.localmodel.treatment.NSExtendedBolus
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryTarget
import app.aaps.core.nssdk.localmodel.treatment.NSTherapyEvent
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.plugins.sync.nsclient.extensions.fromJson
import app.aaps.plugins.sync.nsclientV3.extensions.toBolus
import app.aaps.plugins.sync.nsclientV3.extensions.toBolusCalculatorResult
import app.aaps.plugins.sync.nsclientV3.extensions.toCarbs
import app.aaps.plugins.sync.nsclientV3.extensions.toEffectiveProfileSwitch
import app.aaps.plugins.sync.nsclientV3.extensions.toExtendedBolus
import app.aaps.plugins.sync.nsclientV3.extensions.toFood
import app.aaps.plugins.sync.nsclientV3.extensions.toGV
import app.aaps.plugins.sync.nsclientV3.extensions.toProfileSwitch
import app.aaps.plugins.sync.nsclientV3.extensions.toRunningMode
import app.aaps.plugins.sync.nsclientV3.extensions.toTemporaryBasal
import app.aaps.plugins.sync.nsclientV3.extensions.toTemporaryTarget
import app.aaps.plugins.sync.nsclientV3.extensions.toTherapyEvent
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class NsIncomingDataProcessor @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val nsClientSource: NSClientSource,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val storeDataForDb: StoreDataForDb,
    private val config: Config,
    private val profileStoreProvider: Provider<ProfileStore>,
    private val profileSource: ProfileSource,
    private val uiInteraction: UiInteraction
) {

    private fun toGv(jsonObject: JSONObject): GV? {
        val sgv = NSSgv(jsonObject)
        return GV(
            timestamp = sgv.mills ?: return null,
            value = sgv.mgdl?.toDouble() ?: return null,
            noise = null,
            raw = sgv.filtered?.toDouble(),
            trendArrow = TrendArrow.fromString(sgv.direction),
            ids = IDs(nightscoutId = sgv.id),
            sourceSensor = SourceSensor.fromString(sgv.device)
        )
    }

    /**
     * Preprocess list of SGVs
     *
     * @return true if there was an accepted SGV
     */
    fun processSgvs(sgvs: Any, doFullSync: Boolean): Boolean {
        // Objective0
        preferences.put(BooleanNonKey.ObjectivesBgIsAvailableInNs, true)

        if (!nsClientSource.isEnabled() && !preferences.get(BooleanKey.NsClientAcceptCgmData) && !doFullSync) return false

        var latestDateInReceivedData: Long = 0
        aapsLogger.debug(LTag.NSCLIENT, "Received NS Data: $sgvs")
        val glucoseValues = mutableListOf<GV>()

        if (sgvs is JSONArray) { // V1 client
            for (i in 0 until sgvs.length()) {
                val sgv = toGv(sgvs.getJSONObject(i)) ?: continue
                // allow 1 min in the future
                if (sgv.timestamp < dateUtil.now() + T.mins(1).msecs() && sgv.timestamp > latestDateInReceivedData) {
                    latestDateInReceivedData = sgv.timestamp
                    glucoseValues += sgv
                } else
                    aapsLogger.debug(LTag.NSCLIENT, "Ignoring record with wrong timestamp: $sgv")
            }
        } else if (sgvs is List<*>) { // V3 client
            sgvs.filterIsInstance<NSSgvV3>().forEach { sgv ->
                val gv = sgv.toGV()
                if (gv != null) {
                    if (gv.timestamp < dateUtil.now() + T.mins(1).msecs() && gv.timestamp > latestDateInReceivedData) {
                        latestDateInReceivedData = gv.timestamp
                        glucoseValues += gv
                    } else {
                        aapsLogger.debug(LTag.NSCLIENT, "Ignoring record with wrong timestamp: $gv")
                    }
                }
            }
        }
        if (glucoseValues.isNotEmpty()) {
            activePlugin.activeNsClient?.updateLatestBgReceivedIfNewer(latestDateInReceivedData)
            // Was that sgv more less 5 mins ago ?
            if (T.msecs(dateUtil.now() - latestDateInReceivedData).mins() < 5L) {
                rxBus.send(EventDismissNotification(Notification.NS_ALARM))
                rxBus.send(EventDismissNotification(Notification.NS_URGENT_ALARM))
            }
            storeDataForDb.addToGlucoseValues(glucoseValues)
        }
        return glucoseValues.isNotEmpty()
    }

    /**
     * Preprocess list of treatments
     *
     * @return true if there was an accepted treatment
     */
    fun processTreatments(treatments: List<*>, doFullSync: Boolean): Boolean {
        var acceptedTreatment = false
        treatments.filterIsInstance<NSTreatment>().forEach { treatment ->
            when (treatment) {
                is NSBolus -> {
                    val bolus = treatment.toBolus()
                    if (bolus != null) {
                        storeDataForDb.addToBoluses(listOf(bolus))
                        acceptedTreatment = true
                    }
                }
                is NSBolusWizard -> {
                    val bcr = treatment.toBolusCalculatorResult()
                    if (bcr != null) {
                        storeDataForDb.addToBolusCalculatorResults(listOf(bcr))
                        acceptedTreatment = true
                    }
                }
                is NSCarbs -> {
                    val carbs = treatment.toCarbs()
                    if (carbs != null) {
                        storeDataForDb.addToCarbs(listOf(carbs))
                        acceptedTreatment = true
                    }
                }
                is NSEffectiveProfileSwitch -> {
                    val eps = treatment.toEffectiveProfileSwitch()
                    if (eps != null) {
                        storeDataForDb.addToEffectiveProfileSwitches(listOf(eps))
                        acceptedTreatment = true
                    }
                }
                is NSExtendedBolus -> {
                    val eb = treatment.toExtendedBolus()
                    if (eb != null) {
                        storeDataForDb.addToExtendedBoluses(listOf(eb))
                        acceptedTreatment = true
                    }
                }
                is NSOfflineEvent -> {
                    val rm = treatment.toRunningMode()
                    if (rm != null) {
                        storeDataForDb.addToRunningModes(listOf(rm))
                        acceptedTreatment = true
                    }
                }
                is NSProfileSwitch -> {
                    val ps = treatment.toProfileSwitch()
                    if (ps != null) {
                        storeDataForDb.addToProfileSwitches(listOf(ps))
                        acceptedTreatment = true
                    }
                }
                is NSTemporaryBasal -> {
                    val tb = treatment.toTemporaryBasal()
                    if (tb != null) {
                        storeDataForDb.addToTemporaryBasals(listOf(tb))
                        acceptedTreatment = true
                    }
                }
                is NSTemporaryTarget -> {
                    val tt = treatment.toTemporaryTarget()
                    if (tt != null) {
                        storeDataForDb.addToTemporaryTargets(listOf(tt))
                        acceptedTreatment = true
                    }
                }
                is NSTherapyEvent -> {
                    val te = treatment.toTherapyEvent()
                    if (te != null) {
                        storeDataForDb.addToTherapyEvents(listOf(te))
                        acceptedTreatment = true
                    }
                }
            }
        }
        return acceptedTreatment
    }

    fun processFood(data: Any) {
        aapsLogger.debug(LTag.NSCLIENT, "Received Food Data: $data")

        try {
            val foods = mutableListOf<FD>()
            if (data is JSONArray) {
                for (index in 0 until data.length()) {
                    val jsonFood: JSONObject = data.getJSONObject(index)

                    val type = if (jsonFood.has("type") && !jsonFood.isNull("type")) jsonFood.optString("type") else null
                    if (type != "food") continue

                    val action = if (jsonFood.has("action") && !jsonFood.isNull("action")) jsonFood.optString("action") else null
                    when (action) {
                        "remove" -> {
                            val id = if (jsonFood.has("_id") && !jsonFood.isNull("_id")) jsonFood.optString("_id") else null
                            val delFood = FD(
                                name = "",
                                portion = 0.0,
                                carbs = 0,
                                isValid = false
                            ).also { it.ids.nightscoutId = id }
                            foods += delFood
                        }

                        else     -> {
                            val food = FD.fromJson(jsonFood)
                            if (food != null) foods += food
                            else aapsLogger.error(LTag.NSCLIENT, "Error parsing food", jsonFood.toString())
                        }
                    }
                }
            } else if (data is List<*>) {
                data.filterIsInstance<NSFood>().forEach { food ->
                    val fd = food.toFood()
                    if (fd != null) foods += fd
                }
            }
            storeDataForDb.addToFoods(foods)
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("◄ ERROR", error.localizedMessage))
        }
    }

    fun processProfile(profileJson: JSONObject, doFullSync: Boolean) {
        if (preferences.get(BooleanKey.NsClientAcceptProfileStore) || config.AAPSCLIENT || doFullSync) {
            val store = profileStoreProvider.get().with(profileJson)
            val createdAt = store.getStartDate()
            val lastLocalChange = preferences.get(LongNonKey.LocalProfileLastChange)
            aapsLogger.debug(LTag.PROFILE, "Received profileStore: createdAt: $createdAt Local last modification: $lastLocalChange")
            if (createdAt > lastLocalChange || createdAt % 1000 == 0L) { // whole second means edited in NS
                profileSource.loadFromStore(store)
                activePlugin.activeNsClient?.dataSyncSelector?.profileReceived(store.getStartDate())
                aapsLogger.debug(LTag.PROFILE, "Received profileStore: $profileJson")
            }
        }
    }
}
