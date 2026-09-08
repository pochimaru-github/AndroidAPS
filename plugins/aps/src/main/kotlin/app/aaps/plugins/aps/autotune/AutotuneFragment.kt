package info.nightscout.androidaps.plugins.openaps.autotune

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.AutotuneFragmentBinding
import info.nightscout.androidaps.interfaces.FragmentWithMenu
import info.nightscout.androidaps.plugins.openaps.autotune.data.Autotune
import info.nightscout.androidaps.plugins.openaps.autotune.data.AutotuneResult
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.SafeParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf me.LoggerFactory

class AutotuneFragment : FragmentWithMenu() {

    private val log = LoggerFactory.getLogger(AutotuneFragment::class.java)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var _binding: AutotuneFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AutotuneFragmentBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.autotuneRunButton.setOnClickListener {
            context?.let { ctx ->
                OKDialog.showConfirmation(
                    ctx,
                    getString(R.string.autotune_confirm_title),
                    getString(R.string.autotune_confirm_message),
                    DialogInterface.OnClickListener { _, _ -> runAutotune() }
                )
            }
        }

        return view
    }

    private fun runAutotune() {
        val days = SafeParse.parseInt(binding.autotuneDays.text.toString(), 7)
        binding.autotuneRunButton.isEnabled = false
        binding.autotuneResults.text = getString(R.string.autotune_running)

        scope.launch(Dispatchers.IO) {
            try {
                val autotune = Autotune()
                val result: AutotuneResult = autotune.calculate(days)

                withContext(Dispatchers.Main) {
                    binding.autotuneResults.text = result.toFormattedString()
                    binding.autotuneRunButton.isEnabled = true
                }
            } catch (e: Exception) {
                log.error("Error executing Autotune: ", e)
                withContext(Dispatchers.Main) {
                    binding.autotuneResults.text = getString(R.string.autotune_error, e.localizedMessage)
                    binding.autotuneRunButton.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getPluginName(): String = "Autotune"
}
