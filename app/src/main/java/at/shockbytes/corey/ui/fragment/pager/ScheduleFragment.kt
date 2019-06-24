package at.shockbytes.corey.ui.fragment.pager

import android.content.res.Configuration
import android.os.Bundle
import androidx.recyclerview.widget.ItemTouchHelper
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.shockbytes.core.scheduler.SchedulerFacade
import at.shockbytes.core.ui.fragment.BaseFragment
import at.shockbytes.corey.R
import at.shockbytes.corey.ui.adapter.DaysScheduleAdapter
import at.shockbytes.corey.ui.adapter.ScheduleAdapter
import at.shockbytes.corey.common.addTo
import at.shockbytes.corey.common.core.util.CoreySettings
import at.shockbytes.corey.dagger.AppComponent
import at.shockbytes.corey.data.schedule.ScheduleRepository
import at.shockbytes.corey.data.schedule.model.ScheduleDay
import at.shockbytes.corey.data.schedule.model.ScheduleDayItem
import at.shockbytes.corey.data.schedule.weather.ScheduleWeatherResolver
import at.shockbytes.corey.ui.fragment.dialog.InsertScheduleDialogFragment
import at.shockbytes.util.AppUtils
import at.shockbytes.util.adapter.BaseAdapter
import at.shockbytes.util.adapter.BaseItemTouchHelper
import at.shockbytes.util.view.EqualSpaceItemDecoration
import kotterknife.bindView
import javax.inject.Inject

/**
 * Author:  Martin Macheiner
 * Date:    26.10.2015
 */
class ScheduleFragment : BaseFragment<AppComponent>(), BaseAdapter.OnItemMoveListener<ScheduleDay> {

    override val snackBarBackgroundColorRes: Int = R.color.sb_background
    override val snackBarForegroundColorRes: Int = R.color.sb_foreground

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var schedulers: SchedulerFacade

    @Inject
    lateinit var coreySettings: CoreySettings

    @Inject
    lateinit var weatherResolver: ScheduleWeatherResolver

    private lateinit var touchHelper: ItemTouchHelper
    private val adapter: ScheduleAdapter by lazy {
        ScheduleAdapter(
                requireContext(),
                { item, _, position -> onScheduleItemClicked(item, position) },
                { item -> onItemDismissed(item, item.day) },
                weatherResolver,
                schedulers,
                coreySettings
        )
    }

    private val recyclerView: RecyclerView by bindView(R.id.fragment_schedule_rv)
    private val recyclerViewDays: RecyclerView by bindView(R.id.fragment_schedule_rv_days)

    private val recyclerViewLayoutManager: RecyclerView.LayoutManager
        get() = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        } else {
            GridLayoutManager(context, ScheduleAdapter.MAX_SCHEDULES)
        }

    override val layoutId = R.layout.fragment_schedule

    override fun onItemMove(t: ScheduleDay, from: Int, to: Int) = Unit

    override fun onItemMoveFinished() {
        adapter.reorderAfterMove()
                .forEach { item ->
                    scheduleRepository.updateScheduleDay(item)
                }
    }

    override fun onItemDismissed(t: ScheduleDay, position: Int) {
        if (!t.isEmpty) {
            scheduleRepository.deleteScheduleDay(t)
        }
    }

    override fun bindViewModel() {

        scheduleRepository.schedule
                .subscribeOn(schedulers.io)
                .observeOn(schedulers.ui)
                .subscribe({ scheduleItems ->
                    adapter.updateData(scheduleItems)
                }, { throwable ->
                    throwable.printStackTrace()
                    Toast.makeText(context, throwable.toString(), Toast.LENGTH_LONG).show()
                })
                .addTo(compositeDisposable)
    }

    override fun unbindViewModel() = Unit

    override fun setupViews() {

        recyclerViewDays.apply {
            layoutManager = recyclerViewLayoutManager
            adapter = DaysScheduleAdapter(requireContext(), resources.getStringArray(R.array.days).toList())
            addItemDecoration(EqualSpaceItemDecoration(AppUtils.convertDpInPixel(4, requireContext())))
        }
        recyclerView.apply {
            layoutManager = recyclerViewLayoutManager
            isNestedScrollingEnabled = false
            addItemDecoration(EqualSpaceItemDecoration(AppUtils.convertDpInPixel(4, requireContext())))
        }
        val callback = BaseItemTouchHelper(adapter, false, BaseItemTouchHelper.DragAccess.ALL)
        adapter.onItemMoveListener = this

        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)
        recyclerView.adapter = adapter
    }

    override fun injectToGraph(appComponent: AppComponent?) {
        appComponent?.inject(this)
    }

    private fun onScheduleItemClicked(item: ScheduleDay, position: Int) {
        if (!scheduleRepository.hasDayReachedMaxItems(item)) {
            InsertScheduleDialogFragment.newInstance()
                .setOnScheduleItemSelectedListener { i ->
                    scheduleRepository.insertScheduleItemAtDay(
                        ScheduleDayItem(i.item.title,
                            locationType = i.item.locationType,
                            workoutIconType = i.item.workoutType
                        ),
                        day = position
                    )
                }
                .show(childFragmentManager, "dialog-fragment-insert-schedule")
        }
    }

    companion object {

        fun newInstance(): ScheduleFragment {
            val fragment = ScheduleFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}
