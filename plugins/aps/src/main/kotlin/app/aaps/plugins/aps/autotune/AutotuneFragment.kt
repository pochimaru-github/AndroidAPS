package app.aaps.plugins.aps.autotune

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.fragments.PluginBaseFragment
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.databinding.AutotuneFragmentBinding
import javax.inject.Inject

class AutotuneFragment : PluginBaseFragment() {

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

        binding.autotuneRunButton.setOnClickListener {
            context?.let { ctx ->
                // Kotlin 1.9 Overload resolution ambiguity 回避のために DialogInterface.OnClickListener を明示
                OKDialog.showConfirmation(
                    ctx,
                    rh.gs(R.string.autotune_confirm_title),
                    rh.gs(R.string.autotune_confirm_message),
                    DialogInterface.OnClickListener { _, _ -> runAutotune() }
                )
            }
        }
    }

    private fun runAutotune() {
        val daysText = binding.autotuneDays.text.toString()
        val days = daysText.toIntOrNull() ?: 7
        binding.autotuneRunButton.isEnabled = false
        binding.autotuneResults.text = rh.gs(R.string.autotune_running)

        // Autotune実行処理の呼び出し
        autotunePlugin.aapsAutotune(daysBack = days, autoSwitch = false, profileToTune = "", weekDays = null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
