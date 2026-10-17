package app.aaps.pump.virtual

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
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
import app.aaps.pump.virtual.R
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class VirtualPumpFragment : DaggerFragment() {

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

    private var refreshLoop: Runnable? = null
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var rootView: View? = null

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

    @Synchronized
    override fun onResume() {
        super.onResume()
        try {
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

            if (handlerThread == null || handlerThread?.isAlive == false) {
                handlerThread = HandlerThread(this::class.java.simpleName + "Handler").also { it.start() }
                handler = Handler(handlerThread!!.looper)
            }

            refreshLoop = Runnable {
                if (isAdded && !isDetached && rootView != null) {
                    activity?.runOnUiThread { safeUpdateGui() }
                }
                handler?.postDelayed(refreshLoop!!, T.mins(1).msecs())
            }
            handler?.postDelayed(refreshLoop!!, T.mins(1).msecs())

            val pumpSuspended = rootView?.findViewById<CheckBox>(R.id.pump_suspended)
            if (pumpSuspended != null) {
                pumpSuspended.isChecked = try { preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended) } catch (_: Exception) { false }
                pumpSuspended.setOnClickListener {
                    try { preferences.put(VirtualBooleanNonPreferenceKey.IsSuspended, pumpSuspended.isChecked) } catch (_: Exception) {}
                }
            }

            activity?.runOnUiThread { safeUpdateGui() }
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        try {
            disposable.clear()
            refreshLoop?.let { handler?.removeCallbacks(it) }
            handler?.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        try {
            disposable.clear()
            refreshLoop?.let { handler?.removeCallbacks(it) }
            handler?.removeCallbacksAndMessages(null)
            rootView = null
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            handler?.removeCallbacksAndMessages(null)
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
        } catch (_: Exception) { }
    }

    private fun safeUpdateGui() {
        try {
            updateGui()
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }

    @Synchronized
    private fun updateGui() {
        if (!isAdded || isDetached || rootView == null || context == null) return

        val profile = try { profileFunction.getProfile() } catch (_: Exception) { null }

        val baseBasalRate = rootView?.findViewById<TextView>(R.id.base_basal_rate)
        val tempbasal = rootView?.findViewById<TextView>(R.id.tempbasal)
        val extendedbolus = rootView?.findViewById<TextView>(R.id.extendedbolus)
        val battery = rootView?.findViewById<TextView>(R.id.battery)
        val reservoir = rootView?.findViewById<TextView>(R.id.reservoir)
        val type = rootView?.findViewById<TextView>(R.id.type)
        val typeDef = rootView?.findViewById<TextView>(R.id.type_def)
        val serialNumber = rootView?.findViewById<TextView>(R.id.serial_number)

        if (baseBasalRate != null) {
            baseBasalRate.text = try {
                val rate = virtualPumpPlugin.baseBasalRate
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
                    persistenceLayer.getTemporaryBasalActiveAt(dateUtil.now())?.toStringFull(profile, dateUtil, rh) ?: "None"
                } else "None"
            } catch (_: Exception) { "None" }
        }

        if (extendedbolus != null) {
            extendedbolus.text = try {
                persistenceLayer.getExtendedBolusActiveAt(dateUtil.now())?.toStringFull(dateUtil, rh) ?: "None"
            } catch (_: Exception) { "None" }
        }

        if (battery != null) {
            battery.text = try {
                val bat = virtualPumpPlugin.batteryPercent
                try {
                    rh.gs(app.aaps.core.ui.R.string.format_percent, bat)
                } catch (_: Exception) { "$bat%" }
            } catch (_: Exception) { "100%" }
        }

        if (reservoir != null) {
            reservoir.text = try {
                val res = virtualPumpPlugin.reservoirInUnits.toDouble()
                try {
                    rh.gs(app.aaps.core.ui.R.string.format_insulin_units, res)
                } catch (_: Exception) { "${decimalFormatter.to1Decimal(res)} U" }
            } catch (_: Exception) { "100.0 U" }
        }

        try {
            virtualPumpPlugin.refreshConfiguration()
        } catch (_: Exception) { }

        val pumpType = try { virtualPumpPlugin.pumpType } catch (_: Exception) { null }

        if (type != null) {
            type.text = pumpType?.description ?: "Virtual Pump"
        }

        if (typeDef != null) {
            typeDef.text = try {
                val template = try { rh.gs(R.string.virtual_pump_pump_def) } catch (_: Exception) { "%s" }
                pumpType?.getFullDescription(template, pumpType.hasExtendedBasals(), rh) ?: "Virtual Pump System"
            } catch (_: Exception) { "Virtual Pump System" }
        }

        if (serialNumber != null) {
            serialNumber.text = try {
                virtualPumpPlugin.serialNumber().ifEmpty { "VPUMP-001" }
            } catch (_: Exception) { "VPUMP-001" }
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
            val extendedNote = if (hasExtendedBasals) {
                try { rh.gs(R.string.def_extended_note) } catch (_: Exception) { "" }
            } else ""
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
