package mega.privacy.android.data.gateway

import kotlinx.coroutines.flow.Flow
import mega.privacy.android.domain.entity.BatteryInfo

internal interface BroadcastReceiverGateway {

    /**
     * monitor battery info
     */
    val monitorBatteryInfo: Flow<BatteryInfo>

    /**
     * monitor charging state
     */
    val monitorChargingStoppedState: Flow<Boolean>

    /**
     * Monitor muted chats
     */
    val monitorMutedChats: Flow<Boolean>
}
