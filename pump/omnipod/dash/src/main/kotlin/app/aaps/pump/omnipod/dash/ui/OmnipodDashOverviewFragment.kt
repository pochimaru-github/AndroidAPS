package app.aaps.pump.omnipod.dash.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.dash.databinding.OmnipodDashOverviewFragmentBinding
import javax.inject.Inject

class OmnipodDashOverviewFragment : Fragment() {

    @Inject
    lateinit var plugin: OmnipodDashPumpPlugin

    private var _binding: OmnipodDashOverviewFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = OmnipodDashOverviewFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUi()
    }

    fun updateUi() {
        if (_binding != null && isAdded) {
            binding.plugin = plugin
            binding.lifecycleOwner = viewLifecycleOwner
            binding.executePendingBindings()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
