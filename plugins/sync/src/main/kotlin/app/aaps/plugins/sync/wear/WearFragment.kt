package app.aaps.plugins.sync.wear

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.fragments.TranslatedDaggerFragment
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.databinding.WearFragmentBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearFragment @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val wearPlugin: WearPlugin
) : TranslatedDaggerFragment(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(WearFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_watch)
        .pluginName(R.string.wear)
        .shortName(R.string.wear_short)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.wear_description),
    aapsLogger, rh
) {

    private var _binding: WearFragmentBinding? = null
    private val binding get() = _binding!!
    private val disposable = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = WearFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventWearUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                it.customWatchfaceData?.let { cwf ->
                    if (!it.exportFile) {
                        wearPlugin.savedCustomWatchface = cwf
                        updateGui()
                    }
                }
            }, fabricPrivacy::logException)

        updateGui()
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun updateGui() {
        wearPlugin.savedCustomWatchface?.let {
            val metadata = it.metadata
            val drawable = (it.resData["custom_watchface.png"] as? ByteArray)?.toDrawable(resources)
            binding.customWatchface.setImageDrawable(drawable)
            var titleText = rh.gs(CwfMetadataKey.CWF_NAME.label, metadata[CwfMetadataKey.CWF_NAME])
            metadata[CwfMetadataKey.CWF_AUTHOR_VERSION]?.let { authorVersion ->
                titleText = "${metadata[CwfMetadataKey.CWF_NAME]} ($authorVersion)"
            }
            binding.cwfTitle.text = titleText
            binding.author.text = rh.gs(CwfMetadataKey.CWF_AUTHOR.label, metadata[CwfMetadataKey.CWF_AUTHOR] ?: "")
        }
    }
}

private fun ByteArray?.toDrawable(resources: Resources): Drawable? {
    if (this == null || isEmpty()) return null
    return try {
        val bitmap = BitmapFactory.decodeByteArray(this, 0, size)
        BitmapDrawable(resources, bitmap)
    } catch (_: Exception) {
        null
    }
}
