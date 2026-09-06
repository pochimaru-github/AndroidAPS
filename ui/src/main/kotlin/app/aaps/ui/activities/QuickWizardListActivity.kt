package app.aaps.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.util.forEach
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.dragHelpers.ItemTouchHelperAdapter
import app.aaps.core.ui.dragHelpers.OnStartDragListener
import app.aaps.core.ui.dragHelpers.SimpleItemTouchHelperCallback
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.ui.R
import app.aaps.ui.databinding.ActivityQuickwizardListBinding
import app.aaps.ui.databinding.QuickwizardListItemBinding
import app.aaps.ui.dialogs.EditQuickWizardDialog
import app.aaps.ui.events.EventQuickWizardChange
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.abs

class QuickWizardListActivity : TranslatedDaggerAppCompatActivity(), OnStartDragListener {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var automation: Automation
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposable: CompositeDisposable = CompositeDisposable()
    private lateinit var actionHelper: ActionModeHelper<QuickWizardEntry>
    private val itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback())
    private lateinit var binding: ActivityQuickwizardListBinding
    private var menuProvider: MenuProvider? = null

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    class QuickWizardEntryViewHolder(val itemView: View, val itemBinding: QuickwizardListItemBinding) : RecyclerView.ViewHolder(itemView)

    private inner class RecyclerViewAdapter(var fragmentManager: FragmentManager) : RecyclerView.Adapter<QuickWizardEntryViewHolder>(), ItemTouchHelperAdapter {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickWizardEntryViewHolder {
            val itemBinding = QuickwizardListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val view: View = itemBinding.root
            return QuickWizardEntryViewHolder(view, itemBinding)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: QuickWizardEntryViewHolder, position: Int) {
            val entry = quickWizard[position]
            val binding = holder.itemBinding
            binding.from.text = dateUtil.timeString(entry.validFromDate())
            binding.to.text = dateUtil.timeString(entry.validToDate())
            binding.buttonText.text = entry.buttonText()
            var bindingCarbsTextFull = rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs())
            if (entry.useEcarbs() == QuickWizardEntry.YES) {
                bindingCarbsTextFull += " +" + rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs2())
                bindingCarbsTextFull += "/" + entry.duration() + "h->" + entry.time() + "min"
            }
            binding.carbs.text = bindingCarbsTextFull
            if (entry.device() == QuickWizardEntry.DEVICE_ALL) {
                binding.device.visibility = View.GONE
            } else {
                binding.device.visibility = View.VISIBLE
                val resId = if (quickWizard[position].device() == QuickWizardEntry.DEVICE_WATCH) {
                    app.aaps.core.objects.R.drawable.ic_watch
                } else {
                    app.aaps.core.objects.R.drawable.ic_smartphone
                }
                binding.device.setImageResource(resId)
                
                val desc = if (quickWizard[position].device() == QuickWizardEntry.DEVICE_WATCH) {
                    rh.gs(R.string.a11y_only_on_watch)
                } else {
                    rh.gs(R.string.a11y_only_on_phone)
                }
                binding.device.contentDescription = desc
            }
            binding.root.setOnClickListener {
                if (actionHelper.isNoAction) {
                    val manager = fragmentManager
                    val editQuickWizardDialog = EditQuickWizardDialog()
                    val bundle = Bundle()
                    bundle.putInt("position", position)
                    editQuickWizardDialog.arguments = bundle
                    editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
                } else if (actionHelper.isRemoving) {
                    binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, entry, binding.cbRemove.isChecked)
                }
            }
            binding.root.setOnLongClickListener { view ->
                if (actionHelper.isNoAction) {
                    val actualBg = iobCobCalculator.ads.actualBg()
                    val profile = profileFunction.getProfile()
                    val profileName = profileFunction.getProfileName()
                    val pump = activePlugin.activePump
                    val quickWizardEntry = quickWizard[position]

                    if (actualBg != null && profile != null) {
                        val wizard = quickWizardEntry.doCalc(profile, profileName, actualBg)

                        if (wizard.calculatedTotalInsulin > 0.0 && quickWizardEntry.carbs() > 0.0) {
                            val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(quickWizardEntry.carbs(), aapsLogger)).value()
                            val stepSize = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints)
                            val violatesInsulin = abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= stepSize
                            val violatesCarbs = carbsAfterConstraints != quickWizardEntry.carbs()

                            if (violatesInsulin || violatesCarbs) {
                                val errorMsg = "${rh.gs(R.string.constraints_violation)}\n${rh.gs(R.string.change_your_input)}"
                                OKDialog.show(
                                    view.context,
                                    rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror),
                                    errorMsg
                                )
                            }
                            wizard.confirmAndExecute(view.context, quickWizardEntry)
                        }
                    }
                    return@setOnLongClickListener true
                }
                false
            }
            binding.sortHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                    return@setOnTouchListener true
                }
                return@setOnTouchListener false
            }
            binding.cbRemove.isChecked = actionHelper.isSelected(position)
            binding.cbRemove.setOnCheckedChangeListener { _, value ->
                actionHelper.updateSelection(position, entry, value)
            }
            binding.sortHandle.visibility = actionHelper.isSorting.toVisibility()
            binding.cbRemove.visibility = actionHelper.isRemoving.toVisibility()
        }

        override fun getItemCount() = quickWizard.size()

        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            binding.recyclerview.adapter?.notifyItemMoved(fromPosition, toPosition)
            quickWizard.move(fromPosition, toPosition)
            return true
        }

        override fun onDrop() = rxBus.send(EventQuickWizardChange())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickwizardListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        actionHelper = ActionModeHelper(rh, this, null)
        actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        actionHelper.enableSort = true

        title = rh.gs(app.aaps.core.ui.R.string.quickwizard)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(supportFragmentManager)
        itemTouchHelper.attachToRecyclerView(binding.recyclerview)

        binding.addButton.setOnClickListener {
            actionHelper.finish()
            val manager = supportFragmentManager
            val editQuickWizardDialog = EditQuickWizardDialog()
            editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
        }
        menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(app.aaps.core.objects.R.menu.menu_actions, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                actionHelper.onOptionsItemSelected(menuItem)
        }
        addMenuProvider(menuProvider!!)
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventQuickWizardChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           val adapter = RecyclerViewAdapter(supportFragmentManager)
                           binding.recyclerview.swapAdapter(adapter, false)
                       }, fabricPrivacy::logException)
    }

    override fun onPause() {
        disposable.clear()
        actionHelper.finish()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.recyclerview.adapter = null
        binding.addButton.setOnClickListener(null)
        menuProvider?.let { removeMenuProvider(it) }
    }

    private fun removeSelected(selectedItems: SparseArray<QuickWizardEntry>) {
        OKDialog.showConfirmation(this, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
            var shiftPositionToLeftFor = 0
            selectedItems.forEach { _, item ->
                quickWizard.remove(item.position - shiftPositionToLeftFor)
                shiftPositionToLeftFor++
                rxBus.send(EventQuickWizardChange())
            }
            actionHelper.finish()
        })
    }

    private fun getConfirmationText(selectedItems: SparseArray<QuickWizardEntry>): String {
        if (selectedItems.size() == 1) {
            val entry = selectedItems.valueAt(0)
            return "${rh.gs(app.aaps.core.ui.R.string.remove_button)} ${entry.buttonText()} ${rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs())}\n" +
                "${dateUtil.timeString(entry.validFromDate())} - ${dateUtil.timeString(entry.validToDate())}"
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }
}
