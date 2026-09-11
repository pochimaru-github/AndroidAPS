package app.aaps.plugins.sync.nsclient.acks

import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import io.socket.client.Ack
import org.json.JSONObject

class NSAddAck(private val rxBus: RxBus) : Event(), Ack {

    var success = false

    override fun call(vararg args: Any) {
        if (args.isNotEmpty()) {
            val response = args[0] as? JSONObject
            if (response != null) {
                success = response.optBoolean("result", false)
            }
        }
        rxBus.send(this)
    }
}
