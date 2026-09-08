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
import app.aaps.plugins.aps.R
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
        // レイアウトに autotuneDays / autotuneResults がバインディング経由で取れるか確認し、
        // 取得できない場合は findViewById 等で補填・デフォルト値（7日）を使用
        val daysEditText = binding.root.findViewById<EditText?>(R.id.autotune_days)
        val daysText = daysEditText?.text?.toString() ?: "7"
        val days = daysText.toIntOrNull() ?: 7

        binding.autotuneRun.isEnabled = false

        val resultsTextView = binding.root.findViewById<TextView?>(R.id.autotune_results)
        resultsTextView?.text = rh.gs(R.string.autotune_running)

        autotunePlugin.aapsAutotune(daysBack = days, autoSwitch = false, profileToTune = "", weekDays = null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
