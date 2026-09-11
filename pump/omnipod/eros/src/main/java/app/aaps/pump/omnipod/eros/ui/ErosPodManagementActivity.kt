package app.aaps.pump.omnipod.eros.ui

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.PumpActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeeps
import app.aaps.pump.omnipod.common.queue.command.CommandReadPodStatus
import app.aaps.pump.omnipod.common.queue.command.CommandReadPulseLog
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.databinding.OmnipodErosPodManagementBinding
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.queue.command.CommandReadPodInfo
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ErosPodManagementActivity : PumpActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var podStateManager: ErosPodStateManager
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var omnipodErosPumpPlugin: OmnipodErosPumpPlugin
    @Inject lateinit var omnipodManager: AapsOmnipodErosManager
    @Inject lateinit var omnipodUtil: AapsOmnipodUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposables: CompositeDisposable = CompositeDisposable()
    private lateinit var binding: OmnipodErosPodManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OmnipodErosPodManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSetupPod.setOnClickListener {
            startActivity(Intent(this, ErosPodSetupActivity::class.java))
        }

        binding.buttonDeactivatePod.setOnClickListener {
            startActivity(Intent(this, ErosPodDeactivationActivity::class.java))
        }

        binding.buttonReadStatus.setOnClickListener {
            disableButtons()
            commandQueue.customCommand(
                CommandReadPodStatus(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_read_status), false)
            )
        }

        binding.buttonReadPulseLog.setOnClickListener {
            disableButtons()
            commandQueue.customCommand(
                CommandReadPulseLog(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_read_pulse_log), false)
            )
        }

        binding.buttonReadPodInfo.setOnClickListener {
            disableButtons()
            commandQueue.customCommand(
                CommandReadPodInfo(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_read_pod_info), false)
            )
        }

        binding.buttonTestBeeps.setOnClickListener {
            disableButtons()
            commandQueue.customCommand(
                CommandPlayTestBeeps(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_play_test_beeps), false)
            )
        }

        binding.buttonDiscardPodState.setOnClickListener {
            OKDialog.show(
                this,
                rh.gs(app.aaps.core.ui.R.string.confirmation),
                rh.gs(R.string.omnipod_common_discard_pod_state_confirmation),
                {
                    disableButtons()
                    commandQueue.customCommand(
                        CommandDeactivatePod(),
                        DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_deactivate_pod), false)
                            .actionOnSuccess {
                                podStateManager.discardPodState()
                                finish()
                            }
                    )
                },
                null
            )
        }
    }

    override fun onResume() {
        super.onResume()
        disposables += rxBus
            .toObservable(EventOmnipodErosPumpValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                updateUi()
            }, fabricPrivacy::logException)
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    private fun updateUi() {
        updatePodStatus()
        updateButtons()
    }

    private fun disableButtons() {
        binding.buttonSetupPod.isEnabled = false
        binding.buttonDeactivatePod.isEnabled = false
        binding.buttonReadStatus.isEnabled = false
        binding.buttonReadPulseLog.isEnabled = false
        binding.buttonReadPodInfo.isEnabled = false
        binding.buttonTestBeeps.isEnabled = false
        binding.buttonDiscardPodState.isEnabled = false
    }

    private fun updateButtons() {
        val hasPodState = podStateManager.hasPodState()
        val isPodInitialized = podStateManager.isPodInitialized
        val isPodDeactivated = podStateManager.isPodDeactivated
        val isPodRunning = podStateManager.isPodRunning
        val activationProgress = podStateManager.activationProgress

        binding.buttonSetupPod.isEnabled = hasPodState.not() || isPodDeactivated || (isPodInitialized.not() && activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED))
        binding.buttonDeactivatePod.isEnabled = hasPodState && isPodDeactivated.not()
        binding.buttonReadStatus.isEnabled = hasPodState && isPodInitialized && isPodDeactivated.not()
        binding.buttonReadPulseLog.isEnabled = hasPodState && isPodInitialized && isPodDeactivated.not()
        binding.buttonReadPodInfo.isEnabled = hasPodState && isPodInitialized && isPodDeactivated.not()
        binding.buttonTestBeeps.isEnabled = hasPodState && isPodInitialized && isPodRunning
        binding.buttonDiscardPodState.isEnabled = hasPodState
    }

    private fun updatePodStatus() {
        if (podStateManager.hasPodState().not()) {
            binding.podStatus.text = rh.gs(R.string.omnipod_common_pod_status_no_active_pod)
            binding.podInfo.text = ""
            return
        }

        val statusText = StringBuilder()
        val infoText = StringBuilder()

        statusText.append(rh.gs(R.string.omnipod_common_pod_status_title)).append(": ")
        if (podStateManager.isPodDeactivated) {
            statusText.append(rh.gs(R.string.omnipod_common_pod_status_deactivated))
        } else if (podStateManager.isPodRunning) {
            statusText.append(rh.gs(R.string.omnipod_common_pod_status_running))
        } else {
            statusText.append(podStateManager.podProgressStatus.toString())
        }

        if (podStateManager.isPodInitialized) {
            infoText.append("Lot: ").append(podStateManager.lot).append("\n")
            infoText.append("TID: ").append(podStateManager.tid).append("\n")
            infoText.append("Address: ").append(Integer.toHexString(podStateManager.address)).append("\n")

            val activeAlerts = omnipodUtil.getTranslatedActiveAlerts(podStateManager)
            if (activeAlerts.isNotEmpty()) {
                infoText.append("\nAlerts:\n").append(TextUtils.join("\n", activeAlerts))
            }
        }

        binding.podStatus.text = statusText.toString()
        binding.podInfo.text = infoText.toString()
    }

    private fun displayErrorDialog(title: String, message: String, withSound: Boolean) {
        uiInteraction.runAlarm(message, title, if (withSound) app.aaps.core.ui.R.raw.boluserror else 0)
    }

    private fun displayOkDialog(title: String, message: String) {
        OKDialog.show(this, title, message)
    }

    inner class DisplayResultDialogCallback(private val errorMessagePrefix: String, private val withSoundOnError: Boolean) : app.aaps.core.interfaces.queue.Callback() {

        private var messageOnSuccess: String? = null
        private var actionOnSuccess: Runnable? = null

        override fun run() {
            if (result.success.not()) {
                displayErrorDialog(
                    rh.gs(R.string.omnipod_common_warning),
                    rh.gs(R.string.omnipod_common_two_strings_concatenated_by_colon, errorMessagePrefix, result.comment),
                    withSoundOnError
                )
            } else {
                val messageOnSuccess = this.messageOnSuccess
                if (messageOnSuccess != null) {
                    displayOkDialog(rh.gs(R.string.omnipod_common_confirmation), messageOnSuccess)
                }
                actionOnSuccess?.run()
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
