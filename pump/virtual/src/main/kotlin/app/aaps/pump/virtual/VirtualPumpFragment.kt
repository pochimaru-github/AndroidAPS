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

    private lateinit var refreshLoop: Runnable
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var rootView: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val layoutId = requireContext().resources.getIdentifier("virtual_pump_fragment", "layout", requireContext().packageName)
        val view = if (layoutId != 0) inflater.inflate(layoutId, container, false) else View(requireContext())
        rootView = view
        return view
    }

    private fun <T : View> findViewById(name: String): T? {
        val id = requireContext().resources.getIdentifier(name, "id", requireContext().packageName)
        return if (id != 0) rootView?.findViewById(id) else null
    }

    private fun getStringByName(name: String, fallback: String = ""): String {
        val id = requireContext().resources.getIdentifier(name, "string", requireContext().packageName)
        return if (id != 0) rh.gs(id) else fallback
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventVirtualPumpUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGui() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
        handler.postDelayed(refreshLoop, T.mins(1).msecs())

        val pumpSuspended = findViewById<CheckBox>("pump_suspended")
        if (pumpSuspended != null) {
            pumpSuspended.isChecked = preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended)
            pumpSuspended.setOnClickListener { preferences.put(VirtualBooleanNonPreferenceKey.IsSuspended, pumpSuspended.isChecked) }
        }

        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    @Synchronized
    private fun updateGui() {
        if (rootView == null) return
        val profile = profileFunction.getProfile() ?: return

        val baseBasalRate = findViewById<TextView>("base_basal_rate")
        val tempbasal = findViewById<TextView>("tempbasal")
        val extendedbolus = findViewById<TextView>("extendedbolus")
        val battery = findViewById<TextView>("battery")
        val reservoir = findViewById<TextView>("reservoir")
        val type = findViewById<TextView>("type")
        val typeDef = findViewById<TextView>("type_def")
        val serialNumber = findViewById<TextView>("serial_number")

        baseBasalRate?.text = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, virtualPumpPlugin.baseBasalRate)
        tempbasal?.text = persistenceLayer.getTemporaryBasalActiveAt(dateUtil.now())?.toStringFull(profile, dateUtil, rh) ?: ""
        extendedbolus?.text = persistenceLayer.getExtendedBolusActiveAt(dateUtil.now())?.toStringFull(dateUtil, rh) ?: ""
        battery?.text = rh.gs(app.aaps.core.ui.R.string.format_percent, virtualPumpPlugin.batteryPercent)
        reservoir?.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, virtualPumpPlugin.reservoirInUnits.toDouble())

        virtualPumpPlugin.refreshConfiguration()
        val pumpType = virtualPumpPlugin.pumpType

        type?.text = pumpType?.description
        typeDef?.text = pumpType?.getFullDescription(getStringByName("virtual_pump_pump_def"), pumpType.hasExtendedBasals(), rh)
        serialNumber?.text = virtualPumpPlugin.serialNumber()
    }

    private fun getStep(step: String, stepSize: DoseStepSize?): String =
        if (stepSize != null) step + " [" + stepSize.description + "] *"
        else step

    private fun PumpType.getFullDescription(i18nTemplate: String, hasExtendedBasals: Boolean, rh: ResourceHelper): String {
        val unit = if (pumpTempBasalType() == PumpTempBasalType.Percent) "%" else ""
        val eb = extendedBolusSettings() ?: return "INVALID"
        val tbr = tbrSettings() ?: return "INVALID"
        val extendedNote = if (hasExtendedBasals) getStringByName("def_extended_note") else ""
        return String.format(
            i18nTemplate,
            getStep(bolusSize().toString(), specialBolusSize()),
            eb.step, eb.durationStep, eb.maxDuration / 60,
            getStep(baseBasalRange(), baseBasalSpecialSteps()),
            tbr.minDose.toString() + unit + "-" + tbr.maxDose + unit, tbr.step.toString() + unit,
            tbr.durationStep, tbr.maxDuration / 60, extendedNote
        )
    }
}
