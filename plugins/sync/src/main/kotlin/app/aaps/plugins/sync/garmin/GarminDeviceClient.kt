package app.aaps.plugins.sync.garmin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
// import app.aaps.core.utils.waitMillis // TODO: Garmin SDK 依存の適合・再実装
// import com.garmin.android.apps.connectmobile.connectiq.IConnectIQService // TODO: Garmin SDK 依存の適合・再実装
// import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus // TODO: Garmin SDK 依存の適合・再実装
// import com.garmin.android.connectiq.IQApp // TODO: Garmin SDK 依存の適合・再実装
// import com.garmin.android.connectiq.IQDevice // TODO: Garmin SDK 依存の適合・再実装
// import com.garmin.android.connectiq.IQMessage // TODO: Garmin SDK 依存の適合・再実装
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.jetbrains.annotations.VisibleForTesting
import java.lang.Thread.UncaughtExceptionHandler
import java.time.Instant
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** GarminClient that talks via the ConnectIQ app to a physical device. */
class GarminDeviceClient(
    private val aapsLogger: AAPSLogger,
    private val context: Context,
    private val receiver: GarminReceiver,
    private val retryWaitFactor: Long = 5L
) : Disposable, GarminClient {

    override val name = "Device"
    private var executor = Executors.newSingleThreadExecutor { r ->
        Thread(r).apply {
            name = "Garmin callback"
            isDaemon = true
            uncaughtExceptionHandler = UncaughtExceptionHandler { _, e ->
                aapsLogger.error(LTag.GARMIN, "ConnectIQ callback failed", e)
            }
        }
    }
    private var bindLock = Object()

    /* TODO: Garmin Connect IQ SDK 依存の適合・再実装
    private var ciqService: IConnectIQService? = null
        get() {
            synchronized(bindLock) {
                if (field?.asBinder()?.isBinderAlive != true) {
                    field = null
                    if (state !in arrayOf(State.BINDING, State.RECONNECTING)) {
                        aapsLogger.info(LTag.GARMIN, "reconnecting to ConnectIQ service")
                        state = State.RECONNECTING
                        bindService()
                    }
                    if (field?.asBinder()?.isBinderAlive != true) {
                        field = null
                        aapsLogger.warn(LTag.GARMIN, "no ciqservice $this")
                    }
                }
                return field
            }
        }
    */

    private val registeredActions = mutableSetOf<String>()
    private val broadcastReceiver = mutableListOf<BroadcastReceiver>()
    private var state = State.DISCONNECTED
    private val serviceIntent
        get() = Intent(CONNECTIQ_SERVICE_ACTION).apply {
            component = CONNECTIQ_SERVICE_COMPONENT
        }

    @VisibleForTesting
    val sendMessageAction = createAction("SEND_MESSAGE")

    private enum class State {
        BINDING,
        CONNECTED,
        DISCONNECTED,
        DISPOSED,
        RECONNECTING,
    }

    private val ciqServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            /* TODO: Garmin Connect IQ SDK 依存の適合・再実装
            var notifyReceiver: Boolean
            synchronized(bindLock) {
                aapsLogger.info(LTag.GARMIN, "ConnectIQ App connected")
                notifyReceiver = state != State.RECONNECTING
                state = State.CONNECTED
                bindLock.notifyAll()
            }
            if (notifyReceiver) receiver.onConnect(this@GarminDeviceClient)
            */
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(bindLock) {
                aapsLogger.info(LTag.GARMIN, "ConnectIQ App disconnected")
                if (state != State.DISPOSED) state = State.DISCONNECTED
            }
            broadcastReceiver.forEach { br -> context.unregisterReceiver(br) }
            broadcastReceiver.clear()
            registeredActions.clear()
            receiver.onDisconnect(this@GarminDeviceClient)
        }
    }

    init {
        aapsLogger.info(LTag.GARMIN, "binding to ConnectIQ service")
        registerReceiver(sendMessageAction, ::onSendMessage)
        state = State.BINDING
        bindService()
    }

    private fun bindService() {
        context.bindService(serviceIntent, Context.BIND_AUTO_CREATE, executor, ciqServiceConnection)
    }

    override val connectedDevices: List<GarminDevice>
        get() = emptyList() // TODO: Garmin Connect IQ SDK 依存の適合・再実装

    override fun isDisposed() = state == State.DISPOSED
    override fun dispose() {
        executor.shutdown()
        broadcastReceiver.forEach { context.unregisterReceiver(it) }
        broadcastReceiver.clear()
        registeredActions.clear()
        try {
            context.unbindService(ciqServiceConnection)
        } catch (e: Exception) {
            aapsLogger.warn(LTag.GARMIN, "unbind CIQ failed ${e.message}")
        }
        state = State.DISPOSED
    }

    /** Creates a unique action name for ConnectIQ callbacks. */
    private fun createAction(action: String) = "${javaClass.`package`!!.name}.$action"

    /** Registers a callback [BroadcastReceiver] under the given action that will
     * used by the ConnectIQ app for callbacks.*/
    private fun registerReceiver(action: String, receive: (intent: Intent) -> Unit) {
        val recv = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                receive(intent)
            }
        }
        broadcastReceiver.add(recv)
        context.registerReceiver(recv, IntentFilter(action))
    }

    override fun registerForMessages(app: GarminApplication) {
        aapsLogger.info(LTag.GARMIN, "registerForMessage $name$app")
        val action = createAction("ON_MESSAGE_${app.device.id}_${app.id}")
        synchronized(registeredActions) {
            if (!registeredActions.contains(action)) {
                registerReceiver(action) { intent: Intent -> onReceiveMessage(app, intent) }
                registeredActions.add(action)
            } else {
                aapsLogger.info(LTag.GARMIN, "registerForMessage $action already registered")
            }
        }
    }

    @Suppress("Deprecation")
    private fun onReceiveMessage(app: GarminApplication, intent: Intent) {
        /* TODO: Garmin Connect IQ SDK 依存の適合・再実装 */
    }

    /** Receives callback from ConnectIQ about message transfers. */
    private fun onSendMessage(intent: Intent) {
        /* TODO: Garmin Connect IQ SDK 依存の適合・再実装 */
    }

    private fun getDevice(intent: Intent): Long? {
        return null // TODO: Garmin Connect IQ SDK 依存の適合・再実装
    }

    private class Message(
        val app: GarminApplication,
        val data: ByteArray
    ) {
        var attempt: Int = 0
        val creation: Instant = Instant.now()
        var lastAttempt: Instant? = null
    }

    private val messageQueues = mutableMapOf<Pair<Long, String>, Queue<Message>>()

    override fun sendMessage(app: GarminApplication, data: ByteArray) {
        /* TODO: Garmin Connect IQ SDK 依存の適合・再実装 */
    }

    private fun retryMessage(deviceId: Long, appId: String) {
        /* TODO: Garmin Connect IQ SDK 依存の適合・再実装 */
    }

    private fun sendMessage(msg: Message) {
        /* TODO: Garmin Connect IQ SDK 依存の適合・再実装 */
    }

    override fun toString() = "$name[$state]"

    companion object {

        const val CONNECTIQ_SERVICE_ACTION = "com.garmin.android.apps.connectmobile.CONNECTIQ_SERVICE_ACTION"
        const val EXTRA_APPLICATION_ID = "com.garmin.android.connectiq.EXTRA_APPLICATION_ID"
        const val EXTRA_REMOTE_DEVICE = "com.garmin.android.connectiq.EXTRA_REMOTE_DEVICE"
        const val EXTRA_PAYLOAD = "com.garmin.android.connectiq.EXTRA_PAYLOAD"
        const val EXTRA_STATUS = "com.garmin.android.connectiq.EXTRA_STATUS"
        val CONNECTIQ_SERVICE_COMPONENT = ComponentName(
            "com.garmin.android.apps.connectmobile",
            "com.garmin.android.apps.connectmobile.connectiq.ConnectIQService"
        )

        const val MAX_RETRIES = 10
    }
}
