package app.aaps.plugins.aps.autotune

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.plugins.aps.databinding.AutotuneFragmentBinding
import javax.inject.Inject

class AutotuneFragment : Fragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var autotunePlugin: AutotunePlugin

    private var _binding: AutotuneFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AutotuneFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.autotuneRun.setOnClickListener {
            context?.let { ctx ->
                OKDialog.showConfirmation(
                    ctx,
                    rh.gs(app.aaps.core.ui.R.string.autotune),
                    rh.gs(app.aaps.core.ui.R.string.ok),
                    DialogInterface.OnClickListener { _, _ -> runAutotune() }
                )
            }
        }
    }

    private fun runAutotune() {
        val context = context
        val daysId = context?.resources?.getIdentifier("autotune_days", "id", context.packageName) ?: 0
        val daysEditText = if (daysId != 0) binding.root.findViewById<EditText>(daysId) else null
        val daysText = daysEditText?.text?.toString() ?: "7"
        val days = daysText.toIntOrNull() ?: 7

        binding.autotuneRun.isEnabled = false

        val resultsId = context?.resources?.getIdentifier("autotune_results", "id", context.packageName) ?: 0
        val resultsTextView = if (resultsId != 0) binding.root.findViewById<TextView>(resultsId) else null
        
        // リソースIDの欠落を防ぐため直接テキストをセット
        resultsTextView?.text = "Autotune running..."

        autotunePlugin.aapsAutotune(daysBack = days, autoSwitch = false, profileToTune = "", weekDays = null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
