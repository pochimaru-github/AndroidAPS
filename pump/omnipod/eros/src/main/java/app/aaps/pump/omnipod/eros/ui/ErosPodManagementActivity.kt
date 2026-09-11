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
import app.aaps.pump.omnipod.eros.databinding.OmnipodErosPodManagementBinding
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
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

        binding.buttonDeactivatePod.setOnClickListener {
            disableActionButtons()
            commandQueue.customCommand(
                CommandDeactivatePod(),
                DisplayResultDialogCallback("Failed to deactivate pod", true)
                    .messageOnSuccess("Pod deactivated")
                    .actionOnSuccess { rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_ALERTS)) }
            )
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
    }

    private fun updateActionButtons() {
        val isReady = podStateManager.hasPodState()
        binding.buttonDeactivatePod.isEnabled = isReady
    }

    private fun disableActionButtons() {
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
                messageOnSuccess?.let { displayOkDialog("Confirmation", it) }
                actionOnSuccess?.run()
            } else {
                displayErrorDialog(
                    "Warning",
                    "$errorMessagePrefix: ${result.comment}",
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
