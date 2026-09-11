package app.aaps.pump.omnipod.dash.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.dash.R
import javax.inject.Inject

class OmnipodDashOverviewFragment : Fragment() {

    @Inject
    lateinit var plugin: OmnipodDashPumpPlugin

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.omnipod_dash_overview_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUi()
    }

    fun updateUi() {
        if (isAdded) {
            // UI更新処理が必要な場合はここに記述
        }
    }
}
