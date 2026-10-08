package app.aaps.plugins.main.profile

import androidx.fragment.app.FragmentActivity
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.blockFromJsonArray
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.main.R
import app.aaps.plugins.main.profile.keys.ProfileComposedBooleanKey
import app.aaps.plugins.main.profile.keys.ProfileComposedDoubleKey
import app.aaps.plugins.main.profile.keys.ProfileComposedStringKey
import app.aaps.plugins.main.profile.keys.ProfileIntKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ProfilePlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    rh: ResourceHelper,
    preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val profileStoreProvider: Provider<ProfileStore>,
    private val decimalFormatter: DecimalFormatter,
    private val uiInteraction: UiInteraction
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PROFILE)
        .fragmentClass(ProfileFragment::class.java.name)
        .enableByDefault(true)
        .simpleModePosition(PluginDescription.Position.TAB)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_local_profile)
        .pluginName(R.string.localprofile)
        .shortName(R.string.localprofile_shortname)
        .description(R.string.description_profile_local)
        .setDefault(),
    ownPreferences = listOf(
        ProfileComposedStringKey::class.java, ProfileComposedDoubleKey::class.java, ProfileComposedBooleanKey::class.java, ProfileIntKey::class.java
    ),
    aapsLogger, rh, preferences
), ProfileSource {

    private var rawProfile: ProfileStore? = null

    companion object {

        const val DEFAULT_ARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0}]"
        const val DEFAULT_IC_ARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":10}]"
        const val DEFAULT_ISF_ARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":50}]"
        const val DEFAULT_BASAL_ARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.5}]"
        const val DEFAULT_TARGET_ARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":100}]"
    }

    override fun onStart() {
        super.onStart()
        loadSettings()
    }

    var isEdited: Boolean = false
    private var profiles: ArrayList<ProfileSource.SingleProfile> = ArrayList()

    val numOfProfiles get() = profiles.size
    override var currentProfileIndex = 0

    override fun currentProfile(): ProfileSource.SingleProfile? = if (numOfProfiles > 0 && currentProfileIndex < numOfProfiles) profiles[currentProfileIndex] else null

    private fun parseOrFallbackJsonArray(rawStr: String?, defaultJsonStr: String): JSONArray {
        if (rawStr.isNullOrBlank()) return JSONArray(defaultJsonStr)
        return try {
            val array = JSONArray(rawStr)
            if (array.length() == 0) {
                JSONArray(defaultJsonStr)
            } else {
                var allZero = true
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i)
                    if (obj != null && obj.optDouble("value", 0.0) != 0.0) {
                        allZero = false
                        break
                    }
                }
                if (allZero) JSONArray(defaultJsonStr) else array
            }
        } catch (_: Exception) {
            JSONArray(defaultJsonStr)
        }
    }

    @Synchronized
    fun isValidEditState(activity: FragmentActivity?): Boolean {
        val pumpDescription = activePlugin.activePump.pumpDescription
        with(profiles[currentProfileIndex]) {
            if (dia < hardLimits.minDia() || dia > hardLimits.maxDia()) {
                ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.value_out_of_hard_limits, rh.gs(app.aaps.core.ui.R.string.profile_dia), dia))
                return false
            }
            if (name.isEmpty()) {
                ToastUtils.errorToast(activity, rh.gs(R.string.missing_profile_name))
                return false
            }
            val icBlocks = blockFromJsonArray(ic, dateUtil)
            if (icBlocks.isNullOrEmpty() || icBlocks.all { it.amount < hardLimits.minIC() || it.amount > hardLimits.maxIC() }) {
                ToastUtils.errorToast(activity, rh.gs(R.string.error_in_ic_values))
                return false
            }
            val low = blockFromJsonArray(targetLow, dateUtil)
            val high = blockFromJsonArray(targetHigh, dateUtil)
            if (low.isNullOrEmpty() || high.isNullOrEmpty()) {
                ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                return false
            }
            if (mgdl) {
                val isfBlocks = blockFromJsonArray(isf, dateUtil)
                if (isfBlocks.isNullOrEmpty() || isfBlocks.all { hardLimits.isInRange(it.amount, HardLimits.MIN_ISF, HardLimits.MAX_ISF) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_isf_values))
                    return false
                }
                val basalBlocks = blockFromJsonArray(basal, dateUtil)
                if (basalBlocks.isNullOrEmpty() || basalBlocks.all { it.amount < pumpDescription.basalMinimumRate || it.amount > 10.0 }) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_basal_values))
                    return false
                }
                if (low.all { hardLimits.isInRange(it.amount, HardLimits.LIMIT_MIN_BG[0], HardLimits.LIMIT_MIN_BG[1]) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
                if (high.all { hardLimits.isInRange(it.amount, HardLimits.LIMIT_MAX_BG[0], HardLimits.LIMIT_MAX_BG[1]) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
            } else {
                val isfBlocks = blockFromJsonArray(isf, dateUtil)
                if (isfBlocks.isNullOrEmpty() || isfBlocks.all { hardLimits.isInRange(profileUtil.convertToMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.MIN_ISF, HardLimits.MAX_ISF) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_isf_values))
                    return false
                }
                val basalBlocks = blockFromJsonArray(basal, dateUtil)
                if (basalBlocks.isNullOrEmpty() || basalBlocks.all { it.amount < pumpDescription.basalMinimumRate || it.amount > 10.0 }) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_basal_values))
                    return false
                }
                if (low.all { hardLimits.isInRange(profileUtil.convertToMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.LIMIT_MIN_BG[0], HardLimits.LIMIT_MIN_BG[1]) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
                if (high.all { hardLimits.isInRange(profileUtil.convertToMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.LIMIT_MAX_BG[0], HardLimits.LIMIT_MAX_BG[1]) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
            }
            for (i in low.indices) {
                if (i < high.size && low[i].amount > high[i].amount) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
            }
        }
        return true
    }

    @Synchronized
    fun getEditedProfile(): PureProfile? {
        val profile = JSONObject()
        with(profiles[currentProfileIndex]) {
            profile.put("dia", dia)
            profile.put("carbratio", ic)
            profile.put("sens", isf)
            profile.put("basal", basal)
            profile.put("target_low", targetLow)
            profile.put("target_high", targetHigh)
            profile.put("units", if (mgdl) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
            profile.put("timezone", TimeZone.getDefault().id)
        }
        val defaultUnits = JsonHelper.safeGetStringAllowNull(profile, "units", null)
        return pureProfileFromJson(profile, dateUtil, defaultUnits)
    }

    @Synchronized
    override fun storeSettings(activity: FragmentActivity?, timestamp: Long) {
        for (i in 0 until numOfProfiles) {
            profiles[i].run {
                if (ic.length() == 0) ic = JSONArray(DEFAULT_IC_ARRAY)
                if (isf.length() == 0) isf = JSONArray(DEFAULT_ISF_ARRAY)
                if (basal.length() == 0) basal = JSONArray(DEFAULT_BASAL_ARRAY)
                if (targetLow.length() == 0) targetLow = JSONArray(DEFAULT_TARGET_ARRAY)
                if (targetHigh.length() == 0) targetHigh = JSONArray(DEFAULT_TARGET_ARRAY)

                preferences.put(ProfileComposedStringKey.LocalProfileNumberedName, i, value = name)
                preferences.put(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, i, value = mgdl)
                preferences.put(ProfileComposedDoubleKey.LocalProfileNumberedDia, i, value = dia)
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIc, i, value = ic.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIsf, i, value = isf.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedBasal, i, value = basal.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetLow, i, value = targetLow.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, i, value = targetHigh.toString())
            }
        }
        preferences.put(ProfileIntKey.AmountOfProfiles, numOfProfiles)

        preferences.put(LongNonKey.LocalProfileLastChange, timestamp)
        createAndStoreConvertedProfile()
        isEdited = false
        aapsLogger.debug(LTag.PROFILE, "Storing settings: " + rawProfile?.getData().toString())
        rxBus.send(EventProfileStoreChanged())
        var namesOK = true
        profiles.forEach { if (it.name.contains(".")) namesOK = false }
        if (!namesOK) activity?.let {
            OKDialog.show(it, "", rh.gs(R.string.profile_name_contains_dot))
        }
    }

    @Synchronized
    fun loadSettings() {
        val numOfProfiles = preferences.get(ProfileIntKey.AmountOfProfiles)
        profiles.clear()

        if (numOfProfiles == 0) {
            addNewProfile()
            return
        }

        for (i in 0 until numOfProfiles) {
            val name = preferences.get(ProfileComposedStringKey.LocalProfileNumberedName, i)
            if (isExistingName(name)) continue

            val icJson = parseOrFallbackJsonArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedIc, i), DEFAULT_IC_ARRAY)
            val isfJson = parseOrFallbackJsonArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedIsf, i), DEFAULT_ISF_ARRAY)
            val basalJson = parseOrFallbackJsonArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedBasal, i), DEFAULT_BASAL_ARRAY)
            val targetLowJson = parseOrFallbackJsonArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedTargetLow, i), DEFAULT_TARGET_ARRAY)
            val targetHighJson = parseOrFallbackJsonArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, i), DEFAULT_TARGET_ARRAY)

            profiles.add(
                ProfileSource.SingleProfile(
                    name = name,
                    mgdl = preferences.get(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, i),
                    dia = preferences.get(ProfileComposedDoubleKey.LocalProfileNumberedDia, i),
                    ic = icJson,
                    isf = isfJson,
                    basal = basalJson,
                    targetLow = targetLowJson,
                    targetHigh = targetHighJson
                )
            )
        }
        isEdited = false
        createAndStoreConvertedProfile()
    }

    @Synchronized
    override fun loadFromStore(store: ProfileStore) {
        try {
            val newProfiles: ArrayList<ProfileSource.SingleProfile> = ArrayList()
            for (p in store.getProfileList()) {
                val profile = store.getSpecificProfile(p.toString())
                val validityCheck = profile?.let { ProfileSealed.Pure(profile, activePlugin).isValid("NS", activePlugin.activePump, config, rh, rxBus, hardLimits, false) } ?: Profile.ValidityCheck()
                if (profile != null && validityCheck.isValid) {
                    val sp = copyFrom(profile, p.toString())
                    sp.name = p.toString()
                    newProfiles.add(sp)
                } else {
                    uiInteraction.addNotificationWithDialogResponse(
                        id = Notification.INVALID_PROFILE_NOT_ACCEPTED,
                        text = rh.gs(R.string.invalid_profile_not_accepted, p.toString()),
                        level = Notification.NORMAL,
                        buttonText = R.string.view,
                        title = rh.gs(R.string.errors),
                        message = validityCheck.reasons.joinToString(separator = "\n"),
                        validityCheck = null
                    )
                }
            }
            if (newProfiles.isNotEmpty()) {
                profiles = newProfiles
                currentProfileIndex = 0
                isEdited = false
                aapsLogger.debug(LTag.PROFILE, "Accepted ${profiles.size} profiles")
                storeSettings(timestamp = store.getStartDate())
                rxBus.send(EventLocalProfileChanged())
            } else
                aapsLogger.debug(LTag.PROFILE, "ProfileStore not accepted")
        } catch (e: Exception) {
            aapsLogger.error("Error loading ProfileStore", e)
        }
    }

    override fun copyFrom(pureProfile: PureProfile, newName: String): ProfileSource.SingleProfile {
        var verifiedName = newName
        if (rawProfile?.getSpecificProfile(newName) != null) {
            verifiedName += " " + dateUtil.now().toString()
        }
        val profile = ProfileSealed.Pure(pureProfile, activePlugin)
        val pureJson = pureProfile.jsonObject
        return ProfileSource.SingleProfile(
            name = verifiedName,
            mgdl = profile.units == GlucoseUnit.MGDL,
            dia = pureJson.getDouble("dia"),
            ic = pureJson.getJSONArray("carbratio"),
            isf = pureJson.getJSONArray("sens"),
            basal = pureJson.getJSONArray("basal"),
            targetLow = pureJson.getJSONArray("target_low"),
            targetHigh = pureJson.getJSONArray("target_high")
        )
    }

    private fun isExistingName(name: String?): Boolean {
        for (p in profiles) {
            if (p.name == name) return true
        }
        return false
    }

    private fun createAndStoreConvertedProfile() {
        rawProfile = createProfileStore()
    }

    fun addNewProfile() {
        var free = 0
        for (i in 1..10000) {
            if (rawProfile?.getSpecificProfile(Constants.LOCAL_PROFILE + i) == null) {
                free = i
                break
            }
        }
        profiles.add(
            ProfileSource.SingleProfile(
                name = Constants.LOCAL_PROFILE + free,
                mgdl = profileFunction.getUnits() == GlucoseUnit.MGDL,
                dia = Constants.defaultDIA,
                ic = JSONArray(DEFAULT_IC_ARRAY),
                isf = JSONArray(DEFAULT_ISF_ARRAY),
                basal = JSONArray(DEFAULT_BASAL_ARRAY),
                targetLow = JSONArray(DEFAULT_TARGET_ARRAY),
                targetHigh = JSONArray(DEFAULT_TARGET_ARRAY)
            )
        )
        currentProfileIndex = profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings(timestamp = 0)
    }

    fun cloneProfile() {
        val p = profiles[currentProfileIndex].deepClone()
        p.name = p.name + " copy"
        profiles.add(p)
        currentProfileIndex = profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings(timestamp = dateUtil.now())
        isEdited = false
    }

    override fun addProfile(p: ProfileSource.SingleProfile) {
        profiles.add(p)
        currentProfileIndex = profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings(timestamp = dateUtil.now())
        isEdited = false
    }

    fun removeCurrentProfile() {
        profiles.removeAt(currentProfileIndex)
        if (profiles.isEmpty()) addNewProfile()
        currentProfileIndex = 0
        createAndStoreConvertedProfile()
        storeSettings(timestamp = dateUtil.now())
        isEdited = false
    }

    private fun createProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()

        try {
            for (i in 0 until numOfProfiles) {
                profiles[i].run {
                    val profile = JSONObject()
                    profile.put("dia", dia)
                    profile.put("carbratio", ic)
                    profile.put("sens", isf)
                    profile.put("basal", basal)
                    profile.put("target_low", targetLow)
                    profile.put("target_high", targetHigh)
                    profile.put("units", if (mgdl) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
                    profile.put("timezone", TimeZone.getDefault().id)
                    store.put(name, profile)
                }
            }
            if (numOfProfiles > 0) json.put("defaultProfile", currentProfile()?.name)
            val startDate = preferences.getIfExists(LongNonKey.LocalProfileLastChange) ?: dateUtil.now()
            json.put("date", startDate)
            json.put("created_at", dateUtil.toISOAsUTC(startDate))
            json.put("startDate", dateUtil.toISOAsUTC(startDate))
            json.put("store", store)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }

        return profileStoreProvider.get().with(json)
    }

    override val profile: ProfileStore?
        get() = rawProfile

    override val profileName: String
        get() = rawProfile?.getDefaultProfile()?.let {
            decimalFormatter.to2Decimal(ProfileSealed.Pure(it, activePlugin).percentageBasalSum()) + "U "
        } ?: "INVALID"
}
