package app.aaps.pump.omnipod.eros.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.databinding.OmnipodErosPodManagementBinding
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
import app.aaps.pump.omnipod.eros.queue.command.CommandPlayTestBeeps
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
import dagger.android.AndroidInjection
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ErosPodManagementActivity : AppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var podStateManager: ErosPodStateManager
    @Inject lateinit var omnipodAlertUtil: OmnipodAlertUtil
    @Inject lateinit var omnipodErosPumpPlugin: OmnipodErosPumpPlugin
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposables: CompositeDisposable = CompositeDisposable()
    private var _binding: OmnipodErosPodManagementBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        _binding = OmnipodErosPodManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonPlayTestBeeps.setOnClickListener {
            disableActionButtons()
            commandQueue.customCommand(
                CommandPlayTestBeeps(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_error_failed_to_play_test_beeps), false)
                    .messageOnSuccess(rh.gs(R.string.omnipod_confirmation_played_test_beeps))
            )
        }

        binding.buttonDeactivatePod.setOnClickListener {
            disableActionButtons()
            commandQueue.customCommand(
                CommandDeactivatePod(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_error_failed_to_deactivate_pod), true)
                    .messageOnSuccess(rh.gs(R.string.omnipod_confirmation_pod_deactivated))
                    .actionOnSuccess { rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_ALERTS)) }
            )
        }

        binding.buttonSaveLowReservoirAlert.setOnClickListener {
            val input = binding.lowReservoirAlertUnits.text?.toString()
            val units = input?.toDoubleOrNull()
            if (units != null) {
                omnipodAlertUtil.lowReservoirAlertUnits = units.toInt()
                displayOkDialog(
                    rh.gs(R.string.omnipod_confirmation),
                    rh.gs(R.string.omnipod_confirmation_saved_low_reservoir_alert)
                )
            }
        }

        binding.buttonSavePodExpirationAlert.setOnClickListener {
            val input = binding.podExpirationAlertHours.text?.toString()
            val hours = input?.toDoubleOrNull()
            if (hours != null) {
                omnipodAlertUtil.podExpirationAlertHours = hours.toInt()
                displayOkDialog(
                    rh.gs(R.string.omnipod_confirmation),
                    rh.gs(R.string.omnipod_confirmation_saved_pod_expiration_alert)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        disposables += rxBus
            .toObservable(EventOmnipodErosPumpValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateUi() }, { })
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun updateUi() {
        updateActionButtons()
        updateAlertSettings()
    }

    private fun updateActionButtons() {
        val isReady = podStateManager.hasPodState()
        binding.buttonPlayTestBeeps.isEnabled = isReady
        binding.buttonDeactivatePod.isEnabled = isReady
    }

    private fun updateAlertSettings() {
        val lowAlertUnits = omnipodAlertUtil.lowReservoirAlertUnits
        val currentLowAlert = if (lowAlertUnits != null) {
            lowAlertUnits.toString()
        } else {
            OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD.toString()
        }
        binding.lowReservoirAlertUnits.setText(currentLowAlert)

        val expAlertHours = omnipodAlertUtil.podExpirationAlertHours
        val currentExpAlert = if (expAlertHours != null) {
            expAlertHours.toString()
        } else {
            OmnipodConstants.DEFAULT_EXPIRATION_ALERT_HOURS.toString()
        }
        binding.podExpirationAlertHours.setText(currentExpAlert)
    }

    private fun disableActionButtons() {
        binding.buttonPlayTestBeeps.isEnabled = false
        binding.buttonDeactivatePod.isEnabled = false
    }

    private fun displayErrorDialog(title: String, message: String, withSound: Boolean) {
        uiInteraction.runAlarm(message, title, if (withSound) app.aaps.core.ui.R.raw.boluserror else 0)
    }

    private fun displayOkDialog(title: String, message: String) {
        OKDialog.show(this, title, message)
    }

    inner class DisplayResultDialogCallback(
        private val errorMessagePrefix: String,
        private val withSoundOnError: Boolean
    ) : Callback() {

        private var messageOnSuccess: String? = null
        private var actionOnSuccess: Runnable? = null

        override fun run() {
            if (result.success) {
                messageOnSuccess?.let { displayOkDialog(rh.gs(R.string.omnipod_confirmation), it) }
                actionOnSuccess?.run()
            } else {
                displayErrorDialog(
                    rh.gs(R.string.omnipod_warning),
                    rh.gs(app.aaps.core.R.string.two_strings_concatenated_by_colon, errorMessagePrefix, result.comment),
                    withSoundOnError
                )
            }
        }

        fun messageOnSuccess(message: String): DisplayResultDialogCallback {
            messageOnSuccess = message
            return this
        }

        fun actionOnSuccess(action: Runnable): DisplayResultDialogCallback {
            actionOnSuccess = action
            return this
        }
    }
}
