package app.aaps.pump.common.defs

import app.aaps.pump.common.R
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.interfaces.R as CoreIfR

// TODO there are 3 classes now, that do similar things, sort of, need to define exact rules: PumpDeviceState, PumpDriverState, PumpStatusState

// TODO split this enum into 2
enum class PumpDriverState(val resourceId: Int) {

    NotInitialized(R.string.pump_status_not_initialized), // this state should be set only when driver is created
    Connecting(CoreUiR.string.connecting),
    Connected(CoreIfR.string.connected),
    Initialized(R.string.pump_status_initialized), // this is weird state that probably won't be used, since its more driver centric that communication centric
    EncryptCommunication(R.string.pump_status_encrypt),
    Ready(R.string.pump_status_ready),
    Busy(R.string.pump_status_busy),
    Suspended(R.string.pump_status_suspended),
    ExecutingCommand(R.string.pump_status_executing_command),
    Disconnecting(CoreIfR.string.disconnecting),
    Disconnected(CoreUiR.string.disconnected);

    fun isConnected(): Boolean = this == Connected || this == Initialized || this == Busy || this == Suspended
    fun isInitialized(): Boolean = this == Initialized || this == Busy || this == Suspended
}
