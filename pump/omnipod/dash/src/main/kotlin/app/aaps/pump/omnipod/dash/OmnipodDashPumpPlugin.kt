package info.nightscout.androidaps.plugins.pump.omnipod.dash

import android.content.Context
import info.nightscout.androidaps.plugins.pump.common.hw.PumpDevice
import info.nightscout.androidaps.plugins.pump.omnipod.common.OmnipodPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.dash.database.DashHistoryDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashPumpPlugin @Inject constructor(
    private val context: Context
) : OmnipodPlugin() {

    private val database: DashHistoryDatabase by lazy {
        DashHistoryDatabase.getInstance(context)
    }

    override fun getName(): String = "Omnipod DASH"

    override fun isConnected(): Boolean {
        return true
    }

    // StatusChecker 等のプロパティ初期化ロジック
    fun initializeStatusChecker() {
        // 必要に応じたステータスチェックの初期化処理
    }

    companion object {
        const val PLUGIN_NAME = "Omnipod DASH"
    }
}
