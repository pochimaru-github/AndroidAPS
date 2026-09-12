package app.aaps.plugins.sync.di

import android.content.Context
import androidx.work.WorkManager
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.plugins.sync.garmin.LoopHub
import app.aaps.plugins.sync.garmin.LoopHubImpl
import app.aaps.plugins.sync.nsShared.NSClientFragment
import app.aaps.plugins.sync.nsShared.StoreDataForDbImpl
import app.aaps.plugins.sync.nsclient.data.NSSettingsStatusImpl
import app.aaps.plugins.sync.nsclient.data.ProcessedDeviceStatusDataImpl
import app.aaps.plugins.sync.nsclient.services.NSClientService
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.plugins.sync.tidepool.TidepoolFragment
import app.aaps.plugins.sync.tidepool.auth.AuthFlowIn
import app.aaps.plugins.sync.wear.WearFragment
import app.aaps.plugins.sync.wear.activities.CwfInfosActivity
import app.aaps.plugins.sync.wear.receivers.WearDataReceiver
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobile
import app.aaps.plugins.sync.xdrip.DataSyncSelectorXdripImpl
import app.aaps.plugins.sync.xdrip.XdripFragment
import app.aaps.plugins.sync.xdrip.XdripPlugin
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        SyncModule.Binding::class,
        SyncModule.Provide::class
    ]
)

@Suppress("unused")
abstract class SyncModule {

    @ContributesAndroidInjector abstract fun contributesNSClientFragment(): NSClientFragment

    @ContributesAndroidInjector abstract fun contributesNSClientService(): NSClientService
    @ContributesAndroidInjector abstract fun contributesNSClientV3Service(): NSClientV3Service

    @ContributesAndroidInjector abstract fun contributesTidepoolFragment(): TidepoolFragment
    @ContributesAndroidInjector abstract fun contributesAuthFlowInActivity(): AuthFlowIn
    @ContributesAndroidInjector abstract fun contributesXdripFragment(): XdripFragment
    @ContributesAndroidInjector abstract fun contributesWearFragment(): WearFragment
    @ContributesAndroidInjector abstract fun contributesWearDataReceiver(): WearDataReceiver
    @ContributesAndroidInjector abstract fun contributesWatchUpdaterService(): DataLayerListenerServiceMobile
    @ContributesAndroidInjector abstract fun contributesCustomWatchfaceInfosActivity(): CwfInfosActivity

    @Module
    open class Provide {

        @Reusable
        @Provides
        fun providesWorkManager(context: Context) = WorkManager.getInstance(context)
    }

    @Module
    interface Binding {

        @Binds fun bindProcessedDeviceStatusData(processedDeviceStatusDataImpl: ProcessedDeviceStatusDataImpl): ProcessedDeviceStatusData
        @Binds fun bindNSSettingsStatus(nsSettingsStatusImpl: NSSettingsStatusImpl): NSSettingsStatus
        @Binds fun bindDataSyncSelectorXdripInterface(dataSyncSelectorXdripImpl: DataSyncSelectorXdripImpl): DataSyncSelectorXdrip
        @Binds fun bindStoreDataForDb(storeDataForDbImpl: StoreDataForDbImpl): StoreDataForDb
        @Binds fun bindXDripBroadcastInterface(xDripBroadcastImpl: XdripPlugin): XDripBroadcast
        @Binds fun bindLoopHub(loopHub: LoopHubImpl): LoopHub
    }

}
