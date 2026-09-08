package app.aaps.pump.common.defs

import app.aaps.pump.common.R
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.interfaces.R as CoreIfR

enum class PumpDriverState {

    NotInitialized,
    Connecting,
    Connected,
    Initialized,
    EncryptCommunication,
    Ready,
    Busy,
    Suspended,
    ExecutingCommand,
    Disconnecting,
    Disconnected;

    val resourceId: Int
        get() = when (this) {
            NotInitialized -> R.string.pump_status_not_initialized
            Connecting -> CoreUiR.string.connecting
            Connected -> CoreIfR.string.connected
            Initialized -> R.string.pump_status_initialized
            EncryptCommunication -> R.string.pump_status_encrypt
            Ready -> R.string.pump_status_ready
            Busy -> R.string.pump_status_busy
            Suspended -> R.string.pump_status_suspended
            ExecutingCommand -> R.string.pump_status_executing_command
            Disconnecting -> CoreIfR.string.disconnecting
            Disconnected -> CoreUiR.string.disconnected
        }

    fun isConnected(): Boolean = this == Connected || this == Initialized || this == Busy || this == Suspended
    fun isInitialized(): Boolean = this == Initialized || this == Busy || this == Suspended
}
