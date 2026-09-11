package app.aaps.pump.omnipod.dash

import android.content.Context
import app.aaps.core.interfaces.plugin.Plugin
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashPumpPlugin @Inject constructor(
    private val context: Context
) : Plugin {

    private val database: DashHistoryDatabase by lazy {
        DashHistoryDatabase.getInstance(context)
    }

    override fun getName(): String = "Omnipod DASH"

    fun isConnected(): Boolean {
        return true
    }

    fun initializeStatusChecker() {
        // 初期化処理
    }

    companion object {
        const val PLUGIN_NAME = "Omnipod DASH"
    }
}
