package app.aaps.plugins.sync.garmin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.garmin.android.apps.connectmobile.connectiq.IConnectIQService
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQMessage
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
            var notifyReceiver: Boolean
            synchronized(bindLock) {
                aapsLogger.info(LTag.GARMIN, "ConnectIQ App connected")
                ciqService = IConnectIQService.Stub.asInterface(service)
                notifyReceiver = state != State.RECONNECTING
                state = State.CONNECTED
                bindLock.notifyAll()
            }
            if (notifyReceiver) receiver.onConnect(this@GarminDeviceClient)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(bindLock) {
                aapsLogger.info(LTag.GARMIN, "ConnectIQ App disconnected")
                if (state != State.DISPOSED) state = State.DISCONNECTED
                ciqService = null
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
        get() {
            val service = ciqService ?: return emptyList()
            return try {
                val devices = service.knownDevices
                devices?.map { GarminDevice(this@GarminDeviceClient, it) } ?: emptyList()
            } catch (e: Exception) {
                aapsLogger.error(LTag.GARMIN, "failed to get connected devices", e)
                emptyList()
            }
        }

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
        ContextCompat.registerReceiver(context, recv, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
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
        val statusName = intent.getStringExtra(EXTRA_STATUS)
        val status = statusName?.let { runCatching { IQMessageStatus.valueOf(it) }.getOrNull() } ?: IQMessageStatus.FAILURE_UNKNOWN
        val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD) ?: (intent.getSerializableExtra(EXTRA_PAYLOAD) as? ByteArray)
        aapsLogger.info(LTag.GARMIN, "onReceiveMessage ${app.device.id}${app.id} status=$status payload=$payload")
        if (status == IQMessageStatus.SUCCESS && payload != null) {
            receiver.onReceiveMessage(this, app.device.id, app.id, payload)
        }
    }

    /** Receives callback from ConnectIQ about message transfers. */
    private fun onSendMessage(intent: Intent) {
        val deviceId = getDevice(intent) ?: return
        val appId = intent.getStringExtra(EXTRA_APPLICATION_ID) ?: return
        val statusName = intent.getStringExtra(EXTRA_STATUS)
        val status = statusName?.let { runCatching { IQMessageStatus.valueOf(it) }.getOrNull() } ?: IQMessageStatus.FAILURE_UNKNOWN

        aapsLogger.info(LTag.GARMIN, "onSendMessage status=$status $deviceId$appId")
        val queue = messageQueues[Pair(deviceId, appId)] ?: return
        synchronized(queue) {
            val msg = queue.poll()
            if (status != IQMessageStatus.SUCCESS) {
                if (msg != null && msg.attempt < MAX_RETRIES) {
                    aapsLogger.warn(LTag.GARMIN, "send message failed, retrying (${msg.attempt}) $deviceId$appId")
                    queue.add(msg)
                    retryMessage(deviceId, appId)
                } else {
                    aapsLogger.error(LTag.GARMIN, "send message failed definitively $deviceId$appId")
                    receiver.onSendMessage(this, deviceId, appId, status.name)
                }
            } else {
                aapsLogger.info(LTag.GARMIN, "send message successful $deviceId$appId")
                receiver.onSendMessage(this, deviceId, appId, null)
                if (queue.isNotEmpty()) {
                    sendMessage(queue.peek()!!)
                }
            }
        }
    }

    private fun getDevice(intent: Intent): Long? {
        return if (intent.hasExtra(EXTRA_REMOTE_DEVICE)) {
            intent.getLongExtra(EXTRA_REMOTE_DEVICE, 0L)
        } else {
            null
        }
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
        val queue = messageQueues.getOrPut(Pair(app.device.id, app.id)) { LinkedList() }
        val msg = Message(app, data)
        synchronized(queue) {
            queue.add(msg)
            if (queue.size == 1) {
                sendMessage(msg)
            }
        }
    }

    private fun retryMessage(deviceId: Long, appId: String) {
        val queue = messageQueues[Pair(deviceId, appId)] ?: return
        val msg = synchronized(queue) { queue.peek() } ?: return
        val delay = retryWaitFactor * (1L shl msg.attempt.coerceAtMost(6))

        Schedulers.io().scheduleDirect({
            synchronized(queue) {
                if (queue.peek() == msg) {
                    sendMessage(msg)
                }
            }
        }, delay, TimeUnit.SECONDS)
    }

    private fun sendMessage(msg: Message) {
        val service = ciqService
        if (service == null) {
            aapsLogger.warn(LTag.GARMIN, "sendMessage failed: no service $msg")
            return
        }
        val device = IQDevice(msg.app.device.id, msg.app.device.name)
        msg.attempt++
        msg.lastAttempt = Instant.now()
        val app = IQApp(msg.app.id)

        val parcel = Parcel.obtain()
        val iqMessage = try {
            parcel.writeByteArray(msg.data)
            parcel.setDataPosition(0)
            IQMessage(parcel)
        } finally {
            parcel.recycle()
        }

        try {
            aapsLogger.info(LTag.GARMIN, "sending message to ${device.friendlyName} ${msg.app.id} attempt=${msg.attempt}")
            service.sendMessage(iqMessage, device, app)
        } catch (e: Exception) {
            aapsLogger.error(LTag.GARMIN, "sendMessage exception", e)
        }
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
