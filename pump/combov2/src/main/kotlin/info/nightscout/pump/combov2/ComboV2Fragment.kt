package info.nightscout.pump.combov2

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import info.nightscout.androidaps.data.R
import info.nightscout.androidaps.databinding.Combov2FragmentBinding
import info.nightscout.androidaps.interfaces.PumpPlugin
import info.nightscout.androidaps.plugins.pump.common.hw.comboctl.ComboCtlLogger
import info.nightscout.androidaps.plugins.pump.common.hw.comboctl.ComboCtlLogLevel
import info.nightscout.androidaps.plugins.pump.common.hw.comboctl.ComboCtlPump
import info.nightscout.androidaps.ui.AAPSBaseFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ComboV2Fragment : AAPSBaseFragment() {

    @Inject
    lateinit var comboV2Plugin: ComboV2Plugin

    private var _binding: Combov2FragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Combov2FragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    comboV2Plugin.driverStateUIFlow.collectLatest { state ->
                        updateDriverStateUI(state)
                    }
                }

                launch {
                    comboV2Plugin.pairedStateUIFlow.collectLatest { isPaired ->
                        updatePairedUI(isPaired)
                    }
                }
            }
        }
    }

    private fun updateDriverStateUI(state: ComboV2Plugin.DriverState) {
        val context = context ?: return
        val statusText = when (state) {
            ComboV2Plugin.DriverState.NotInitialized -> context.getString(R.string.combov2_state_not_initialized)
            ComboV2Plugin.DriverState.Disconnected   -> context.getString(R.string.combov2_state_disconnected)
            ComboV2Plugin.DriverState.Connecting     -> context.getString(R.string.combov2_state_connecting)
            ComboV2Plugin.DriverState.CheckingPump   -> context.getString(R.string.combov2_state_checking_pump)
            is ComboV2Plugin.DriverState.ExecutingCommand -> context.getString(R.string.combov2_state_executing_command)
            ComboV2Plugin.DriverState.Ready          -> context.getString(R.string.combov2_state_ready)
            ComboV2Plugin.DriverState.Suspended      -> context.getString(R.string.combov2_state_suspended)
            ComboV2Plugin.DriverState.Error          -> context.getString(R.string.combov2_state_error)
        }
        binding.combov2DriverState.text = statusText
    }

    private fun updatePairedUI(isPaired: Boolean) {
        binding.combov2PairedStatus.text = if (isPaired) {
            getString(R.string.combov2_paired)
        } else {
            getString(R.string.combov2_not_paired)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): ComboV2Fragment = ComboV2Fragment()
    }
}
