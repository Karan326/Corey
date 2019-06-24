package at.shockbytes.corey.ui.adapter

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import at.shockbytes.core.scheduler.SchedulerFacade
import at.shockbytes.corey.R
import at.shockbytes.corey.common.addTo
import at.shockbytes.corey.common.core.util.CoreySettings
import at.shockbytes.corey.common.core.workout.model.LocationType
import at.shockbytes.corey.common.setVisible
import at.shockbytes.corey.data.schedule.model.ScheduleDay
import at.shockbytes.corey.data.schedule.weather.ScheduleWeatherResolver
import at.shockbytes.corey.util.ScheduleItemDiffUtilCallback
import at.shockbytes.util.adapter.BaseAdapter
import at.shockbytes.util.adapter.ItemTouchHelperAdapter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_schedule.*
import timber.log.Timber
import java.util.Collections

/**
 * Author:  Martin Macheiner
 * Date:    02.12.2015
 */
class ScheduleAdapter(
    context: Context,
    private val onItemClickedListener: ((item: ScheduleDay, v: View, position: Int) -> Unit),
    private val onItemDismissedListener: ((item: ScheduleDay, position: Int) -> Unit),
    private val weatherResolver: ScheduleWeatherResolver,
    private val schedulers: SchedulerFacade,
    private val coreySettings: CoreySettings
) : BaseAdapter<ScheduleDay>(context), ItemTouchHelperAdapter {

    private val compositeDisposable = CompositeDisposable()

    override var data: MutableList<ScheduleDay>
        get() = super.data
        set(value) {
            for (i in data.size - 1 downTo 0) {
                deleteEntity(i)
            }
            fillUpScheduleList(value).forEach { addEntityAtLast(it) }
        }

    init {
        data = mutableListOf()
    }

    // ----------------------------------------------------------------------

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseAdapter<ScheduleDay>.ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.item_schedule, parent, false))
    }

    override fun onBindViewHolder(holder: BaseAdapter<ScheduleDay>.ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        (holder as? ViewHolder)?.bind(data[position], position)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        compositeDisposable.dispose()
    }

    override fun onItemMove(from: Int, to: Int): Boolean {
        if (from < to) {
            (from until to).forEach { Collections.swap(data, it, it + 1) }
        } else {
            (from downTo to + 1).forEach { Collections.swap(data, it, it - 1) }
        }
        notifyItemMoved(from, to)
        onItemMoveListener?.onItemMove(data[from], from, to)
        return true
    }

    override fun onItemMoveFinished() {
        onItemMoveListener?.onItemMoveFinished()
    }

    override fun onItemDismiss(position: Int) {
        val entry = data.removeAt(position)
        if (!entry.isEmpty) {
            onItemMoveListener?.onItemDismissed(entry, entry.day)
        }
        notifyItemRemoved(position)
        addEntity(position, emptyScheduleItem(position))
    }

    // -----------------------------Data Section-----------------------------

    fun updateData(items: List<ScheduleDay>) {

        val filledItems = fillUpScheduleList2(items)
        val diffResult = DiffUtil.calculateDiff(ScheduleItemDiffUtilCallback(data, filledItems))

        data.clear()
        data.addAll(filledItems)

        diffResult.dispatchUpdatesTo(this)
    }

    fun insertScheduleItem(item: ScheduleDay) {
        val location = item.day
        if (location >= 0) {
            data[location] = item
            notifyItemChanged(location)
        }
    }

    fun updateScheduleItem(item: ScheduleDay) {

        val oldLocation = getLocation(item)
        val newLocation = item.day
        if (newLocation >= 0 && oldLocation != newLocation) {
            val newLocationItem = data[newLocation]
            data[newLocation] = item
            data[oldLocation] = newLocationItem
            notifyItemChanged(newLocation)
            notifyItemChanged(oldLocation)
        }
    }

    fun resetEntity(item: ScheduleDay) {
        val location = item.day
        if (location >= 0) {
            data[location] = emptyScheduleItem(location)
            notifyItemChanged(location)
        }
    }

    fun reorderAfterMove(): List<ScheduleDay> {
        // Assign the right day indices to the objects
        data.forEachIndexed { index, _ ->
            data[index].day = index
        }
        // Only return the filled ones for syncing
        return data.filter { !it.isEmpty }
    }

    private fun fillUpScheduleList(items: List<ScheduleDay>): List<ScheduleDay> {

        val array = arrayOfNulls<ScheduleDay>(MAX_SCHEDULES)
        // Populate array with all given items
        items.forEach { array[it.day] = it }
        // Now add placeholder objects for empty spots
        (0 until MAX_SCHEDULES).forEach { idx ->
            if (array[idx] == null) {
                array[idx] = emptyScheduleItem(idx)
            }
        }
        // Safe to do so, because all nulls are already replaced
        return array.mapTo(mutableListOf()) { it!! }
    }

    private fun fillUpScheduleList2(items: List<ScheduleDay>): List<ScheduleDay> {
        val def = Array(MAX_SCHEDULES) { emptyScheduleItem(it) }.toMutableList()
        items.forEach { item ->
            def[item.day] = item
        }
        return def
    }

    private fun fillUpScheduleList3(items: List<ScheduleDay>): List<ScheduleDay> {

        val array = arrayOfNulls<ScheduleDay>(MAX_SCHEDULES)
        // Populate array with all given items
        items.forEach { array[it.day] = it }
        // Now add placeholder objects for empty spots
        (0 until MAX_SCHEDULES).forEach { idx ->
            if (array[idx] == null) {

                if (data[idx].isEmpty) {
                    array[idx] = data[idx].copy(day = idx)
                } else {
                    array[idx] = emptyScheduleItem(idx)
                }
            }
        }
        // Safe to do so, because all nulls are already replaced
        return array.mapTo(mutableListOf()) { it!! }
    }

    private fun emptyScheduleItem(idx: Int): ScheduleDay = ScheduleDay(day = idx)

    private inner class ViewHolder(
        override val containerView: View
    ) : BaseAdapter<ScheduleDay>.ViewHolder(containerView), LayoutContainer {

        private lateinit var item: ScheduleDay
        private var itemPosition: Int = 0

        init {
            item_schedule_txt_name.setOnClickListener {
                onItemClickedListener.invoke(item, itemView, itemPosition)
            }
            item_schedule_btn_clear.setOnClickListener {
                onItemDismissedListener.invoke(item, itemPosition)
            }
        }

        override fun bindToView(t: ScheduleDay) = Unit

        fun bind(item: ScheduleDay, position: Int) {
            this.item = item
            itemPosition = position
            item_schedule_txt_name.text = item.name

            if (shouldLoadWeather(item)) {
                loadWeather(position)
            }

            item_schedule_iv_icon.apply {
                /* TODO Merge multiple icons together
                setImageResource((item.workoutIconType.iconRes ?: 0))
                item.workoutIconType.iconTint?.let { tintColor ->
                    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tintColor))
                }
                */
            }
        }

        private fun shouldLoadWeather(day: ScheduleDay): Boolean {
            return day.items.any { item ->
                (item.locationType == LocationType.OUTDOOR) && coreySettings.isWeatherForecastEnabled
            }
        }

        private fun loadWeather(index: Int) {
            weatherResolver.resolveWeatherForScheduleIndex(index)
                    .subscribeOn(schedulers.io)
                    .observeOn(schedulers.ui)
                    .subscribe({ weatherInfo ->
                        item_schedule_weather.setVisible(true)
                        item_schedule_weather.setWeatherInfo(weatherInfo, unit = "°C", animate = true)
                    }, { throwable ->
                        Timber.e(throwable)
                        item_schedule_weather.setVisible(false)
                    })
                    .addTo(compositeDisposable)
        }
    }

    companion object {
        const val MAX_SCHEDULES = 7
    }
}