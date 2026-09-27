package app.aaps.plugins.sync.nsShared

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
// TODO: 未解決参照につきコメントアウト (要再実装)
// import app.aaps.core.ui.dialogs.OKDialog
// import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.sync.R
// TODO: databinding 未解決参照につきコメントアウト (要再実装)
// import app.aaps.plugins.sync.databinding.NsClientFragmentBinding
// import app.aaps.plugins.sync.databinding.NsClientLogItemBinding
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiData
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiQueue
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NSClientFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var persistenceLayer: PersistenceLayer

    companion object {

        const val ID_MENU_CLEAR_LOG = 507
        const val ID_MENU_RESTART = 508
        const val ID_MENU_SEND_NOW = 509
        const val ID_MENU_FULL_SYNC = 510
    }

    override var plugin: PluginBase? = null
    private val nsClientPlugin
        get() = activePlugin.activeNsClient

    private val disposable = CompositeDisposable()
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    class FixedLinearLayoutManager(context: Context?, @RecyclerView.Orientation orientation: Int = RecyclerView.VERTICAL, reverseLayout: Boolean = false) :
        LinearLayoutManager(context, orientation, reverseLayout) {

        override fun supportsPredictiveItemAnimations(): Boolean = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return inflater.inflate(R.layout.ns_client_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: ViewBinding 復旧時にUIバインディング処理を適用 (要再実装)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clear_log)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_RESTART, 0, rh.gs(R.string.restart)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_SEND_NOW, 0, rh.gs(R.string.deliver_now)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_CLEAR_LOG -> {
                nsClientPlugin?.listLog?.let { list ->
                    synchronized(list) {
                        list.clear()
                        updateLog()
                    }
                }
                true
            }

            ID_MENU_RESTART   -> {
                rxBus.send(EventNSClientRestart())
                true
            }

            ID_MENU_SEND_NOW  -> {
                handler.post { nsClientPlugin?.resend("GUI") }
                true
            }

            ID_MENU_FULL_SYNC -> {
                // TODO: OKDialog オーバーロードおよびフル同期のダイアログ表示を再実装
                handler.post {
                    nsClientPlugin?.resetToFullSync()
                    nsClientPlugin?.resend("FULL_SYNC")
                }
                true
            }

            else              -> false
        }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventNSClientUpdateGuiData::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    updateLog()
                }, fabricPrivacy::logException
            )
        disposable += rxBus
            .toObservable(EventNSClientUpdateGuiQueue::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateQueue() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientUpdateGuiStatus::class.java)
            .debounce(3L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateStatus() }, fabricPrivacy::logException)
        updateStatus()
        updateQueue()
        updateLog()
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    private fun updateQueue() {
        // TODO: ViewBinding 復旧時にキュー数の表示処理を再実装
    }

    private fun updateStatus() {
        // TODO: ViewBinding 復旧時にステータス表示処理を再実装
    }

    private fun updateLog() {
        // TODO: ViewBinding 復旧時にログ表示処理を再実装
    }

}

class RecyclerViewAdapter(
    private var logList: List<EventNSClientNewLog>
) : RecyclerView.Adapter<RecyclerViewAdapter.NsClientLogViewHolder>() {

    class NsClientLogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // TODO: NsClientLogItemBinding 復旧時にバインディングプロパティを再実装
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): NsClientLogViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.ns_client_log_item, viewGroup, false)
        return NsClientLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: NsClientLogViewHolder, position: Int) {
        // TODO: logText の HTML 描画処理を再実装
    }

    override fun getItemCount(): Int = logList.size
}
