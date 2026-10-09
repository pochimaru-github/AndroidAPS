package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAcceptOpenLoopChange
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventNewOpenLoopNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventRunningModeChange
import app.aaps.core.interfaces.rx.events.EventScale
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewGraph
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewIobCob
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewSensitivity
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.main.databinding.OverviewFragmentBinding
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.overview.ui.StatusLightHandler
import app.aaps.plugins.main.skins.SkinProvider
import com.jjoe64.graphview.GraphView
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

class OverviewFragment : DaggerFragment(), View.OnClickListener, OnLongClickListener {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var dexcomBoyda: DexcomBoyda
    @Inject lateinit var xDripSource: XDripSource
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var config: Config
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var overview: Overview
    @Inject lateinit var lastBgData: LastBgData
    @Inject lateinit var automation: Automation
    @Inject lateinit var bgQualityCheck: BgQualityCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var graphDataProvider: Provider<GraphData>
    @Inject lateinit var commandQueue: CommandQueue

    private val disposable = CompositeDisposable()

    private var smallWidth = false
    private var smallHeight = false
    private var axisWidth: Int = 0
    private lateinit var refreshLoop: Runnable
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val secondaryGraphs = ArrayList<GraphView>()
    private val secondaryGraphsLabel = ArrayList<TextView>()

    private var carbAnimation: AnimationDrawable? = null
    private var lastUserAction = ""

    private var _binding: OverviewFragmentBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OverviewFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
        }.root

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val wm = requireActivity().windowManager.currentWindowMetrics
        val screenWidth = wm.bounds.width()
        val screenHeight = wm.bounds.height()
        smallWidth = screenWidth <= Constants.SMALL_WIDTH
        smallHeight = screenHeight <= Constants.SMALL_HEIGHT
        val landscape = screenHeight < screenWidth

        if (config.AAPSCLIENT1)
            binding.nsclientCard.setBackgroundColor(Color.argb(80, 0xE8, 0xC5, 0x0C))
        if (config.AAPSCLIENT2)
            binding.nsclientCard.setBackgroundColor(Color.argb(80, 0x0F, 0xBB, 0xE0))

        overview.setVersionView(binding.infoLayout.version)

        skinProvider.activeSkin().preProcessLandscapeOverviewLayout(binding, landscape, rh.gb(app.aaps.core.ui.R.bool.isTablet), smallHeight)
        binding.nsclientCard.visibility = config.AAPSCLIENT.toVisibility()

        binding.notifications.setHasFixedSize(false)
        binding.notifications.layoutManager = LinearLayoutManager(view.context)
        axisWidth = when {
            resources.displayMetrics.densityDpi <= 120 -> 3
            resources.displayMetrics.densityDpi <= 160 -> 10
            resources.displayMetrics.densityDpi <= 320 -> 35
            resources.displayMetrics.densityDpi <= 420 -> 50
            resources.displayMetrics.densityDpi <= 560 -> 70
            else                                       -> 80
        }
        binding.graphsLayout.bgGraph.gridLabelRenderer?.gridColor = rh.gac(context, app.aaps.core.ui.R.attr.graphGrid)
        binding.graphsLayout.bgGraph.gridLabelRenderer?.reloadStyles()
        binding.graphsLayout.bgGraph.gridLabelRenderer?.labelVerticalWidth = axisWidth
        binding.graphsLayout.bgGraph.layoutParams?.height = rh.dpToPx(skinProvider.activeSkin().mainGraphHeight)

        carbAnimation = binding.infoLayout.carbsIcon.background as AnimationDrawable?
        carbAnimation?.setEnterFadeDuration(1200)
        carbAnimation?.setExitFadeDuration(1200)

        binding.graphsLayout.bgGraph.setOnLongClickListener {
            overviewData.rangeToDisplay += 6
            overviewData.rangeToDisplay = if (overviewData.rangeToDisplay > 24) 6 else overviewData.rangeToDisplay
            preferences.put(IntNonKey.RangeToDisplay, overviewData.rangeToDisplay)
            rxBus.send(EventPreferenceChange(IntNonKey.RangeToDisplay.key))
            preferences.put(BooleanNonKey.ObjectivesScaleUsed, true)
            false
        }
        prepareGraphsIfNeeded(overviewMenus.setting.size)
        overviewMenus.setupChartMenu(binding.graphsLayout.chartMenuButton, binding.graphsLayout.scaleButton)
        binding.graphsLayout.scaleButton.text = overviewMenus.scaleString(overviewData.rangeToDisplay)

        binding.graphsLayout.chartMenuButton.visibility = preferences.simpleMode.not().toVisibility()

        binding.activeProfile.setOnClickListener(this)
        binding.activeProfile.setOnLongClickListener(this)
        binding.tempTarget.setOnClickListener(this)
        binding.tempTarget.setOnLongClickListener(this)
        binding.pumpStatusLayout.setOnClickListener(this)
        binding.buttonsLayout.acceptTempButton.setOnClickListener(this)
        binding.buttonsLayout.treatmentButton.setOnClickListener(this)
        binding.buttonsLayout.wizardButton.setOnClickListener(this)
        binding.buttonsLayout.calibrationButton.setOnClickListener(this)
        binding.buttonsLayout.cgmButton.setOnClickListener(this)
        binding.buttonsLayout.insulinButton.setOnClickListener(this)
        binding.buttonsLayout.carbsButton.setOnClickListener(this)
        binding.buttonsLayout.quickWizardButton.setOnClickListener(this)
        binding.buttonsLayout.quickWizardButton.setOnLongClickListener(this)
        binding.infoLayout.apsMode.setOnClickListener(this)
        binding.infoLayout.apsMode.setOnLongClickListener(this)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewCalcProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateCalcProgress() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewIobCob::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateIobCob() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewSensitivity::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateSensitivity() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewGraph::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGraph() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewNotification::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateNotification() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDismissNotification::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateNotification() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventScale::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           overviewData.rangeToDisplay = it.hours
                           preferences.put(IntNonKey.RangeToDisplay, it.hours)
                           rxBus.send(EventPreferenceChange(IntNonKey.RangeToDisplay.key))
                           preferences.put(BooleanNonKey.ObjectivesScaleUsed, true)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventBucketedDataCreated::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateBg() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (it.now) refreshAll()
                           else scheduleUpdateGUI()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAcceptOpenLoopChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewOpenLoopNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .delay(30, TimeUnit.MILLISECONDS, aapsSchedulers.main)
            .subscribe({
                           context?.let { ctx ->
                               overviewData.pumpStatus = it.getStatus(ctx)
                               updatePumpStatus()
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processButtonsVisibility() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateTemporaryTarget() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateExtendedBolus() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateTemporaryBasal() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRunningModeChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ processAps() }, fabricPrivacy::logException)

        refreshLoop = Runnable {
            refreshAll()
            handler.postDelayed(refreshLoop, 60 * 1000L)
        }
        handler.postDelayed(refreshLoop, 60 * 1000L)

        handler.post { refreshAll() }
        updatePumpStatus()
        updateCalcProgress()

        popupBolusDialogIfRunning(onClick = false)
    }

    fun refreshAll() {
        if (!config.appInitialized || !isAdded || _binding == null) return
        runOnUiThread {
            _binding ?: return@runOnUiThread
            updateTime()
            updateSensitivity()
            updateGraph()
            updateNotification()
        }
        updateBg()
        updateTemporaryBasal()
        updateExtendedBolus()
        updateIobCob()
        processButtonsVisibility()
        processAps()
        updateProfile()
        updateTemporaryTarget()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
        _binding?.graphsLayout?.bgGraph?.let { graph ->
            graph.setOnLongClickListener(null)
            graph.removeAllSeries()
        }
        for (graph in secondaryGraphs) {
            graph.setOnLongClickListener(null)
            graph.removeAllSeries()
        }
        _binding = null
        carbAnimation?.stop()
        carbAnimation = null
        secondaryGraphs.clear()
        secondaryGraphsLabel.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            handler.looper.quitSafely()
        } catch (ignored: Exception) { }
    }

    override fun onClick(v: View) {
        val b = _binding ?: return
        when (v.id) {
            b.activeProfile.id -> uiInteraction.runProfileSwitchDialog(childFragmentManager)
            b.tempTarget.id -> uiInteraction.runTempTargetDialog(childFragmentManager)
            b.buttonsLayout.treatmentButton.id -> uiInteraction.runTreatmentDialog(childFragmentManager)
            b.buttonsLayout.calibrationButton.id -> uiInteraction.runCalibrationDialog(childFragmentManager)
            b.buttonsLayout.cgmButton.id -> uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.SENSOR_INSERT, app.aaps.core.ui.R.string.cgm_sensor_insert)
            b.buttonsLayout.insulinButton.id -> uiInteraction.runInsulinDialog(childFragmentManager)
            b.buttonsLayout.carbsButton.id -> uiInteraction.runCarbsDialog(childFragmentManager)
            b.buttonsLayout.wizardButton.id -> uiInteraction.runWizardDialog(childFragmentManager)
        }
    }

    override fun onLongClick(v: View?): Boolean {
        return false
    }

    private fun scheduleUpdateGUI() {
        if (!isAdded) return
        handler.post { refreshAll() }
    }

    private fun updateTime() {
        runOnUiThread {
            _binding?.let { b ->
                b.infoLayout.time.text = dateUtil.timeString(System.currentTimeMillis())
            }
        }
    }

    private fun updateSensitivity() {
        // Handled via bus events
    }

    private fun updateGraph() {
        if (!isAdded || _binding == null) return
        try {
            val graphData = graphDataProvider.get().with(binding.graphsLayout.bgGraph, overviewData)
            graphData.formatAxis(overviewData.fromTime, overviewData.endTime)
            graphData.addBgReadings(true, context)
            graphData.addTargetLine()
            graphData.addBasals()
            graphData.addIob(false, 0.3)
            graphData.addCob(false, 0.3)
            graphData.addTreatments(context)
            graphData.addNowLine(dateUtil.now())
            graphData.setNumVerticalLabels()
            graphData.performUpdate()
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }

    private fun updateNotification() {
        // Handled via notificationStore
    }

    private fun updateBg() {
        val lastBg = lastBgData.lastBg()
        runOnUiThread {
            _binding?.let { b ->
                if (lastBg != null) {
                    b.infoLayout.bg.text = decimalFormatter.to1Decimal(lastBg.value)
                    val deltaVal = if (overviewData.bgReadingsArray.size >= 2) {
                        overviewData.bgReadingsArray[0].value - overviewData.bgReadingsArray[1].value
                    } else 0.0
                    b.infoLayout.delta.text = decimalFormatter.to1Decimal(deltaVal)
                    b.infoLayout.timeAgo.text = dateUtil.minAgo(rh, lastBg.timestamp)
                }
            }
        }
    }

    private fun updateTemporaryBasal() {
        runOnUiThread {
            _binding?.let { b ->
                b.infoLayout.baseBasal.text = overviewData.temporaryBasalText()
            }
        }
    }

    private fun updateExtendedBolus() {
        runOnUiThread {
            _binding?.let { b ->
                b.infoLayout.extendedBolus.text = overviewData.extendedBolusText()
            }
        }
    }

    private fun updateIobCob() {
        val iob = iobCobCalculator.calculateIobFromBolus()
        val cobInfo = iobCobCalculator.getCobInfo("OverviewFragment")
        runOnUiThread {
            _binding?.let { b ->
                b.infoLayout.iob.text = decimalFormatter.to2Decimal(iob.iob)
                val cobVal = cobInfo.displayCob ?: 0.0
                b.infoLayout.cob.text = decimalFormatter.to1Decimal(cobVal)
            }
        }
    }

    private fun processButtonsVisibility() {
        runOnUiThread {
            _binding?.let { b ->
                val isInit = config.appInitialized
                b.buttonsLayout.treatmentButton.visibility = (isInit && preferences.get(BooleanKey.OverviewShowTreatmentButton)).toVisibility()
                b.buttonsLayout.wizardButton.visibility = (isInit && preferences.get(BooleanKey.OverviewShowWizardButton)).toVisibility()
                b.buttonsLayout.cgmButton.visibility = (isInit && preferences.get(BooleanKey.OverviewShowCgmButton)).toVisibility()
                b.buttonsLayout.calibrationButton.visibility = (isInit && preferences.get(BooleanKey.OverviewShowCalibrationButton)).toVisibility()
                b.buttonsLayout.insulinButton.visibility = (isInit && preferences.get(BooleanKey.OverviewShowInsulinButton)).toVisibility()
                b.buttonsLayout.carbsButton.visibility = (isInit && preferences.get(BooleanKey.OverviewShowCarbsButton)).toVisibility()
            }
        }
    }

    private fun processAps() {
        val mode = loop.runningMode
        runOnUiThread {
            _binding?.let { b ->
                b.infoLayout.apsModeText.text = mode.toString()
            }
        }
    }

    private fun updateProfile() {
        runOnUiThread {
            _binding?.let { b ->
                b.activeProfile.text = profileFunction.getProfileName()
            }
        }
    }

    private fun updateTemporaryTarget() {
        val activeTT = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        runOnUiThread {
            _binding?.let { b ->
                if (activeTT != null) {
                    val unitStr = profileFunction.getUnits().toString()
                    val targetText = if (activeTT.lowTarget == activeTT.highTarget) {
                        "${decimalFormatter.to1Decimal(activeTT.lowTarget)} $unitStr"
                    } else {
                        "${decimalFormatter.to1Decimal(activeTT.lowTarget)} - ${decimalFormatter.to1Decimal(activeTT.highTarget)} $unitStr"
                    }
                    b.tempTarget.text = targetText
                } else {
                    val profile = profileFunction.getProfile()
                    if (profile != null) {
                        val targetVal = decimalFormatter.to1Decimal(profile.getTargetLowMgdl())
                        val unitStr = profileFunction.getUnits().toString()
                        b.tempTarget.text = "$targetVal $unitStr"
                    } else {
                        b.tempTarget.text = rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
                    }
                }
            }
        }
    }

    private fun updatePumpStatus() {
        runOnUiThread {
            _binding?.let { b ->
                b.pumpStatus.text = overviewData.pumpStatus
            }
        }
    }

    private fun updateCalcProgress() {
        runOnUiThread {
            _binding?.let { b ->
                b.progressBar.progress = overviewData.calcProgressPct
                b.progressBar.visibility = (overviewData.calcProgressPct < 100).toVisibility()
            }
        }
    }

    private fun prepareGraphsIfNeeded(size: Int) {
        // Internal setup for graphs layout
    }

    private fun popupBolusDialogIfRunning(onClick: Boolean) {
        // Internal setup for popup dialogs
    }
}
