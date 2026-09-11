package app.aaps.plugins.sync.nsclient.acks

import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import io.socket.client.Ack
import org.json.JSONObject

class NSAuthAck(private val rxBus: RxBus) : Event(), Ack {

    var read = false
    var write = false
    var writeTreatment = false

    override fun call(args: Array<out Any>) {
        if (args.isNotEmpty()) {
            val response = args[0] as? JSONObject
            if (response != null) {
                read = response.optBoolean("read")
                write = response.optBoolean("write")
                writeTreatment = response.optBoolean("write_treatment")
            }
        }
        rxBus.send(this)
    }
}
