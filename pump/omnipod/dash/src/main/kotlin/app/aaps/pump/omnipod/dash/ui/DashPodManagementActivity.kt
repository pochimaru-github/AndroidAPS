package app.aaps.pump.omnipod.dash.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeep
import app.aaps.pump.omnipod.common.ui.wizard.activation.PodActivationWizardActivity
import app.aaps.pump.omnipod.dash.R
import app.aaps.pump.omnipod.dash.ui.wizard.activation.DashPodActivationWizardActivity
import app.aaps.pump.omnipod.dash.ui.wizard.deactivation.DashPodDeactivationWizardActivity
import app.aaps.pump.omnipod.dash.util.mapProfileToBasalProgram
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class DashPodManagementActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var podStateManager: OmnipodDashPodStateManager
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus

    private var disposables: CompositeDisposable = CompositeDisposable()

    private lateinit var buttonActivatePod: Button
    private lateinit var buttonDeactivatePod: Button
    private lateinit var buttonDiscardPod: Button
    private lateinit var buttonPlayTestBeep: Button
    private lateinit var buttonPodHistory: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.omnipod_dash_pod_management)

        title = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        buttonActivatePod = findViewById(R.id.buttonActivatePod)
        buttonDeactivatePod = findViewById(R.id.buttonDeactivatePod)
        buttonDiscardPod = findViewById(R.id.buttonDiscardPod)
        buttonPlayTestBeep = findViewById(R.id.buttonPlayTestBeep)
        buttonPodHistory = findViewById(R.id.buttonPodHistory)

        buttonActivatePod.setOnClickListener {
            val profile = profileFunction.getProfile()
            if (profile == null) {
                OKDialog.show(
                    this,
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_warning),
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_set_profile_empty_profile)
                )
                return@setOnClickListener
            }

            try {
                mapProfileToBasalProgram(profile)
            } catch (e: IllegalArgumentException) {
                OKDialog.show(
                    this,
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_warning),
                    e.message ?: rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_set_basal_failed)
                )
                return@setOnClickListener
            }

            val type: PodActivationWizardActivity.Type =
                if (podStateManager.activationProgress.isAtLeast(ActivationProgress.PRIME_COMPLETED)) {
                    PodActivationWizardActivity.Type.SHORT
                } else {
                    PodActivationWizardActivity.Type.LONG
                }

            val intent = Intent(this, DashPodActivationWizardActivity::class.java)
            intent.putExtra(PodActivationWizardActivity.KEY_TYPE, type)
            startActivity(intent)
        }

        buttonDeactivatePod.setOnClickListener {
            startActivity(Intent(this, DashPodDeactivationWizardActivity::class.java))
        }

        buttonDiscardPod.setOnClickListener {
            OKDialog.showConfirmation(
                this,
                rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_discard_pod_confirmation),
                Runnable {
                    podStateManager.reset()
                }
            )
        }

        buttonPlayTestBeep.setOnClickListener {
            buttonPlayTestBeep.isEnabled = false
            buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_playing_test_beep)

            commandQueue.customCommand(
                CommandPlayTestBeep(),
                object : Callback() {
                    override fun run() {
                        if (result.success.not()) {
                            displayErrorDialog(
                                rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_warning),
                                rh.gs(
                                    app.aaps.pump.omnipod.common.R.string.omnipod_common_two_strings_concatenated_by_colon,
                                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_play_test_beep),
                                    result.comment
                                ),
                                false
                            )
                        }
                    }
                }
            )
        }

        buttonPodHistory.setOnClickListener {
            startActivity(Intent(this, DashPodHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        disposables += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshButtons() }, fabricPrivacy::logException)

        refreshButtons()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    private fun refreshButtons() {
        val discardButtonEnabled =
            podStateManager.uniqueId != null &&
                podStateManager.activationProgress.isBefore(ActivationProgress.SET_UNIQUE_ID)
        buttonDiscardPod.visibility = if (discardButtonEnabled) View.VISIBLE else View.GONE

        buttonActivatePod.isEnabled = podStateManager.activationProgress.isBefore(ActivationProgress.COMPLETED)
        buttonDeactivatePod.isEnabled = podStateManager.bluetoothAddress != null || podStateManager.ltk != null

        if (podStateManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED)) {
            if (commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class.java)) {
                buttonPlayTestBeep.isEnabled = false
                buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_playing_test_beep)
            } else {
                buttonPlayTestBeep.isEnabled = true
                buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
            }
        } else {
            buttonPlayTestBeep.isEnabled = false
            buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
        }

        if (discardButtonEnabled) {
            buttonDiscardPod.isEnabled = true
        }
    }

    private fun displayErrorDialog(title: String, message: String, @Suppress("SameParameterValue") withSound: Boolean) {
        uiInteraction.runAlarm(message, title, if (withSound) app.aaps.core.ui.R.raw.boluserror else 0)
    }
}
