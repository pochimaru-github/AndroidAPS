package app.aaps.plugins.sync.nsclient.acks

import androidx.work.OneTimeWorkRequest
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsclient.services.NSClientService
import app.aaps.plugins.sync.nsclient.workers.NSClientAddAckWorker
import io.socket.client.Ack
import org.json.JSONArray
import org.json.JSONObject

class NSAddAck(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val nsClientService: NSClientService,
    private val dateUtil: DateUtil,
    private val dataWorkerStorage: DataWorkerStorage,
    val originalObject: Any? = null
) : Event(), Ack {

    var id: String? = null
    var json: JSONObject? = null

    override fun call(vararg args: Any) {
        if (args.isEmpty()) return

        val firstArg = args[0]

        // Regular response
        if (firstArg is JSONArray) {
            try {
                if (firstArg.length() > 0) {
                    val response = firstArg.getJSONObject(0)
                    id = response.optString("_id", null)
                    json = response
                }
                processAddAck()
                return
            } catch (e: Exception) {
                aapsLogger.error("Unhandled exception", e)
            }
        }

        // Check for not authorized
        if (firstArg is JSONObject) {
            try {
                if (firstArg.has("result")) {
                    id = null
                    val resultStr = firstArg.optString("result", "")
                    if (resultStr.contains("Not")) {
                        rxBus.send(EventNSClientRestart())
                        return
                    }
                    aapsLogger.debug(LTag.NSCLIENT, "DBACCESS $resultStr")
                }
            } catch (e: Exception) {
                aapsLogger.error("Unhandled exception", e)
            }
        }
    }

    private fun processAddAck() {
        nsClientService.lastAckTime = dateUtil.now()
        dataWorkerStorage.enqueue(
            OneTimeWorkRequest.Builder(NSClientAddAckWorker::class.java)
                .setInputData(dataWorkerStorage.storeInputData(this))
                .build()
        )
    }
}
