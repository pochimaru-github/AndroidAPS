package app.aaps.plugins.sync.wear.wearintegration

import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataHandlerMobile @Inject constructor(
    private val context: Context,
    private val rxBus: RxBus,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC),
    aapsLogger,
    rh
) {

    private val disposable = CompositeDisposable()
    private var lastSendTime = 0L

    fun resendData() {
        val now = System.currentTimeMillis()
        if (now - lastSendTime < 5000) return
        lastSendTime = now
        sendStatus()
    }

    fun sendStatus() {
        aapsLogger.debug(LTag.WEAR, "DataHandlerMobile: sendStatus called")
    }

    fun sendTreatments() {
        aapsLogger.debug(LTag.WEAR, "DataHandlerMobile: sendTreatments called")
    }

    fun onCleanUp() {
        disposable.clear()
    }
}
