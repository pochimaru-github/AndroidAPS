package info.nightscout.androidaps.plugins.openaps.autotune

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginFragment
import info.nightscout.androidaps.plugins.openaps.autotune.data.Autotune
import info.nightscout.androidaps.plugins.openaps.autotune.data.AutotuneResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf me.LoggerFactory

class AutotuneFragment : Fragment(), PluginFragment {

    private val log = LoggerFactory.getLogger(AutotuneFragment::class.java)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var daysEditText: EditText
    private lateinit var runButton: Button
    private lateinit var resultTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.autotune_fragment, container, false)

        daysEditText = view.findViewById(R.id.autotune_days)
        runButton = view.findViewById(R.id.autotune_run_button)
        resultTextView = view.findViewById(R.id.autotune_results)

        runButton.setOnClickListener {
            runAutotune()
        }

        return view
    }

    private fun runAutotune() {
        val days = daysEditText.text.toString().toIntOrNull() ?: 7
        runButton.isEnabled = false
        resultTextView.text = getString(R.string.autotune_running)

        scope.launch(Dispatchers.IO) {
            try {
                val autotune = Autotune()
                val result: AutotuneResult = autotune.calculate(days)

                withContext(Dispatchers.Main) {
                    resultTextView.text = result.toFormattedString()
                    runButton.isEnabled = true
                }
            } catch (e: Exception) {
                log.error("Error executing Autotune: ", e)
                withContext(Dispatchers.Main) {
                    resultTextView.text = getString(R.string.autotune_error, e.localizedMessage)
                    runButton.isEnabled = true
                }
            }
        }
    }

    override fun getPluginName(): String = "Autotune"
}
