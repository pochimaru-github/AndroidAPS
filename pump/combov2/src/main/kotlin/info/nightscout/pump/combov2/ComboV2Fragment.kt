package info.nightscout.pump.combov2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import info.nightscout.pump.combov2.R
import info.nightscout.pump.combov2.databinding.Combov2FragmentBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ComboV2Fragment : Fragment() {

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
            ComboV2Plugin.DriverState.NotInitialized -> context.getString(R.string.combov2_not_initialized)
            ComboV2Plugin.DriverState.Disconnected   -> context.getString(R.string.combov2_disconnected)
            ComboV2Plugin.DriverState.Connecting     -> context.getString(R.string.combov2_connecting)
            ComboV2Plugin.DriverState.CheckingPump   -> context.getString(R.string.combov2_checking_pump)
            is ComboV2Plugin.DriverState.ExecutingCommand -> context.getString(R.string.combov2_executing_command)
            ComboV2Plugin.DriverState.Ready          -> context.getString(R.string.combov2_ready)
            ComboV2Plugin.DriverState.Suspended      -> context.getString(R.string.combov2_suspended)
            ComboV2Plugin.DriverState.Error          -> context.getString(R.string.combov2_error)
        }
        binding.combov2DriverState.text = statusText
    }

    private fun updatePairedUI(isPaired: Boolean) {
        if (isPaired) {
            binding.combov2FragmentMainUi.visibility = View.VISIBLE
            binding.combov2FragmentUnpairedUi.visibility = View.GONE
        } else {
            binding.combov2FragmentMainUi.visibility = View.GONE
            binding.combov2FragmentUnpairedUi.visibility = View.VISIBLE
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
