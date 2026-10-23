package app.aaps.pump.virtual

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import app.aaps.core.data.pump.defs.DoseStepSize
import app.aaps.core.data.pump.defs.PumpTempBasalType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.defs.baseBasalRange
import app.aaps.core.interfaces.pump.defs.hasExtendedBasals
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.toStringFull
import app.aaps.pump.virtual.events.EventVirtualPumpUpdateGui
import app.aaps.pump.virtual.keys.VirtualBooleanNonPreferenceKey
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class VirtualPumpFragment : DaggerFragment() {
    
    private var baseBasalRateView: TextView? = null
    private var tempbasalView: TextView? = null
    private var extendedbolusView: TextView? = null
    private var batteryView: TextView? = null
    private var reservoirView: TextView? = null
    private var typeView: TextView? = null
    private var typeDefView: TextView? = null
    private var serialNumberView: TextView? = null
    private var pumpSuspendedView: CheckBox? = null

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var preferences: Preferences

    private val disposable = CompositeDisposable()

    private var rootView: View? = null
    private var retryCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val ctx = context ?: return null
            val view = inflater.inflate(app.aaps.pump.virtual.R.layout.virtual_pump_fragment, container, false)
            rootView = view
            view
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            baseBasalRateView = view.findViewById(app.aaps.pump.virtual.R.id.base_basal_rate) as? TextView
            tempbasalView = view.findViewById(app.aaps.pump.virtual.R.id.tempbasal) as? TextView
            extendedbolusView = view.findViewById(app.aaps.pump.virtual.R.id.extendedbolus) as? TextView
            batteryView = view.findViewById(app.aaps.pump.virtual.R.id.battery) as? TextView
            reservoirView = view.findViewById(app.aaps.pump.virtual.R.id.reservoir) as? TextView
            typeView = view.findViewById(app.aaps.pump.virtual.R.id.type) as? TextView
            typeDefView = view.findViewById(app.aaps.pump.virtual.R.id.type_def) as? TextView
            serialNumberView = view.findViewById(app.aaps.pump.virtual.R.id.serial_number) as? TextView
            pumpSuspendedView = view.findViewById(app.aaps.pump.virtual.R.id.pump_suspended) as? CheckBox

            val pumpSuspended = pumpSuspendedView
            if (pumpSuspended != null) {
                pumpSuspended.isChecked = try {
                    if (::preferences.isInitialized) {
                        preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended)
                    } else false
                } catch (_: Exception) { false }

                pumpSuspended.setOnClickListener {
                    try {
                        if (::preferences.isInitialized) {
                            preferences.put(VirtualBooleanNonPreferenceKey.IsSuspended, pumpSuspended.isChecked)
                        }
                    } catch (_: Exception) {}
                }
            }

            view.post {
                if (isAdded && !isDetached && rootView != null) {
                    safeUpdateGui()
                }
            }
        } catch (e: Exception) {
            if (::fabricPrivacy.isInitialized) {
                fabricPrivacy.logException(e)
            }
        }
    }

    private fun findViewByName(view: View, name: String): View? {
        try {
            if (view.id != View.NO_ID) {
                val entryName = view.resources.getResourceEntryName(view.id)
                if (entryName == name) return view
            }
        } catch (_: Throwable) {}
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findViewByName(view.getChildAt(i), name)
                if (found != null) return found
            }
        }
        return null
    }
    
    private fun getStringByName(name: String, fallback: String = ""): String {
        val ctx = context ?: return fallback
        return try {
            val id = ctx.resources.getIdentifier(name, "string", "app.aaps.pump.virtual")
            if (id != 0) rh.gs(id) else fallback
        } catch (_: Exception) { fallback }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        try {
            if (::rxBus.isInitialized && ::aapsSchedulers.isInitialized && ::fabricPrivacy.isInitialized) {
                disposable += rxBus
                    .toObservable(EventVirtualPumpUpdateGui::class.java)
                    .observeOn(aapsSchedulers.main)
                    .subscribe({ safeUpdateGui() }, fabricPrivacy::logException)
                disposable += rxBus
                    .toObservable(EventTempBasalChange::class.java)
                    .observeOn(aapsSchedulers.main)
                    .subscribe({ safeUpdateGui() }, fabricPrivacy::logException)
                disposable += rxBus
                    .toObservable(EventExtendedBolusChange::class.java)
                    .observeOn(aapsSchedulers.main)
                    .subscribe({ safeUpdateGui() }, fabricPrivacy::logException)
            }

            val pumpSuspended = pumpSuspendedView
            if (pumpSuspended != null) {
                pumpSuspended.isChecked = try {
                    if (::preferences.isInitialized) {
                        preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended)
                    } else false
                } catch (_: Exception) { false }

                pumpSuspended.setOnClickListener {
                    try {
                        if (::preferences.isInitialized) {
                            preferences.put(VirtualBooleanNonPreferenceKey.IsSuspended, pumpSuspended.isChecked)
                        }
                    } catch (_: Exception) {}
                }
            }

            view?.post {
                if (isAdded && !isDetached && rootView != null) {
                    safeUpdateGui()
                }
            }
        } catch (e: Exception) {
            if (::fabricPrivacy.isInitialized) {
                fabricPrivacy.logException(e)
            }
        }
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        try {
            disposable.clear()
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        try {
            disposable.clear()
            rootView = null
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
        } catch (_: Exception) { }
    }

    @Synchronized
    private fun safeUpdateGui() {
        if (isDetached || !isAdded || rootView == null || context == null) return
        try {
            if (!::virtualPumpPlugin.isInitialized) return
            if (!::profileFunction.isInitialized) return
            if (!::persistenceLayer.isInitialized) return
            if (!::dateUtil.isInitialized) return
            if (!::decimalFormatter.isInitialized) return
            if (!::rh.isInitialized) return
            if (!::fabricPrivacy.isInitialized) return

            val plugin = virtualPumpPlugin
            val profile = try { profileFunction.getProfile() } catch (_: Exception) { null }
            val pumpType = try { plugin.pumpType } catch (_: Exception) { null }

            val baseBasalRate = baseBasalRateView
            val tempbasal = tempbasalView
            val extendedbolus = extendedbolusView
            val battery = batteryView
            val reservoir = reservoirView
            val type = typeView
            val typeDef = typeDefView
            val serialNumber = serialNumberView

            if (baseBasalRate != null) {
                baseBasalRate.text = try {
                    val rate = plugin.baseBasalRate
                    try {
                        rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, rate)
                    } catch (_: Exception) {
                        decimalFormatter.to2Decimal(rate) + " U/h"
                    }
                } catch (_: Exception) { "0.00 U/h" }
            }

            if (tempbasal != null) {
                tempbasal.text = try {
                    if (profile != null) {
                        val activeTemp = persistenceLayer.getTemporaryBasalActiveAt(dateUtil.now())
                        if (activeTemp != null) {
                            activeTemp.toStringFull(profile, dateUtil, rh)
                        } else {
                            "None"
                        }
                    } else "None"
                } catch (_: Exception) { "None" }
            }

            if (extendedbolus != null) {
                extendedbolus.text = try {
                    val activeBolus = persistenceLayer.getExtendedBolusActiveAt(dateUtil.now())
                    if (activeBolus != null) {
                        activeBolus.toStringFull(dateUtil, rh)
                    } else {
                        "None"
                    }
                } catch (_: Exception) { "None" }
            }

            if (battery != null) {
                battery.text = try {
                    val bat = plugin.batteryPercent
                    try {
                        rh.gs(app.aaps.core.ui.R.string.format_percent, bat)
                    } catch (_: Exception) { "$bat%" }
                } catch (_: Exception) { "100%" }
            }

            if (reservoir != null) {
                reservoir.text = try {
                    val res = plugin.reservoirInUnits.toDouble()
                    try {
                        rh.gs(app.aaps.core.ui.R.string.format_insulin_units, res)
                    } catch (_: Exception) { "${decimalFormatter.to1Decimal(res)} U" }
                } catch (_: Exception) { "100.0 U" }
            }

            if (type != null) {
                type.text = pumpType?.description ?: "Virtual Pump"
            }

            if (typeDef != null) {
                typeDef.text = try {
                    pumpType?.getFullDescription(getStringByName("virtual_pump_pump_def"), pumpType.hasExtendedBasals(), rh) ?: "Virtual Pump System"
                } catch (_: Exception) { "Virtual Pump System" }
            }

            if (serialNumber != null) {
                serialNumber.text = try {
                    plugin.serialNumber().ifEmpty { "VPUMP-001" }
                } catch (_: Exception) { "VPUMP-001" }
            }
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }

    private fun getStep(step: String, stepSize: DoseStepSize?): String =
        if (stepSize != null) step + " [" + stepSize.description + "] *"
        else step

    private fun PumpType.getFullDescription(i18nTemplate: String, hasExtendedBasals: Boolean, rh: ResourceHelper): String {
        return try {
            val unit = if (pumpTempBasalType() == PumpTempBasalType.Percent) "%" else ""
            val eb = extendedBolusSettings() ?: return "INVALID"
            val tbr = tbrSettings() ?: return "INVALID"
            val extendedNote = if (hasExtendedBasals) getStringByName("def_extended_note") else ""
            String.format(
                i18nTemplate,
                getStep(bolusSize().toString(), specialBolusSize()),
                eb.step, eb.durationStep, eb.maxDuration / 60,
                getStep(baseBasalRange(), baseBasalSpecialSteps()),
                tbr.minDose.toString() + unit + "-" + tbr.maxDose + unit, tbr.step.toString() + unit,
                tbr.durationStep, tbr.maxDuration / 60, extendedNote
            )
        } catch (e: Exception) {
            "INVALID"
        }
    }
}
