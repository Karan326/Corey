package at.shockbytes.corey.ui.adapter

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import at.shockbytes.core.scheduler.SchedulerFacade
import at.shockbytes.corey.R
import at.shockbytes.corey.common.addTo
import at.shockbytes.corey.common.core.util.CoreySettings
import at.shockbytes.corey.common.core.workout.model.LocationType
import at.shockbytes.corey.common.core.workout.model.WorkoutIconType
import at.shockbytes.corey.common.setVisible
import at.shockbytes.corey.data.schedule.model.ScheduleDay
import at.shockbytes.corey.data.schedule.weather.ScheduleWeatherResolver
import at.shockbytes.corey.util.ScheduleItemDiffUtilCallback
import at.shockbytes.util.AppUtils
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
    private val onItemDismissedListener: ((item: ScheduleDay) -> Unit),
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
            fillUpScheduleList(value)
                .forEach { scheduleDay ->
                    addEntityAtLast(scheduleDay)
                }
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

        val filledItems = fillUpScheduleList(items)
        val diffResult = DiffUtil.calculateDiff(ScheduleItemDiffUtilCallback(data, filledItems))

        data.clear()
        data.addAll(filledItems)

        diffResult.dispatchUpdatesTo(this)
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
        val def = Array(MAX_SCHEDULES) { emptyScheduleItem(it) }.toMutableList()
        items.forEach { item ->
            def[item.day] = item
        }
        return def
    }

    private fun emptyScheduleItem(idx: Int): ScheduleDay = ScheduleDay(day = idx)

    private inner class ViewHolder(
        override val containerView: View
    ) : BaseAdapter<ScheduleDay>.ViewHolder(containerView), LayoutContainer {

        override fun bindToView(t: ScheduleDay) = Unit

        fun bind(item: ScheduleDay, position: Int) {
            item_schedule_txt_name.setOnClickListener {
                onItemClickedListener.invoke(item, itemView, position)
            }
            item_schedule_btn_clear.setOnClickListener {
                onItemDismissedListener.invoke(item)
            }

            item_schedule_txt_name.text = item.name

            if (shouldLoadWeather(item)) {
                loadWeather(position)
            }

            setIcons(item.icons)
        }

        private fun setIcons(icons: List<WorkoutIconType>) {
            item_schedule_icon_container.removeAllViews()

            icons
                .mapIndexed { index, workoutIconType ->
                    mapWorkoutIconToImageView(index, workoutIconType)
                }
                .forEach { imgView ->
                    item_schedule_icon_container.addView(imgView)
                }
        }

        private fun mapWorkoutIconToImageView(index: Int, workoutIconType: WorkoutIconType): ImageView {
            return ImageView(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    AppUtils.convertDpInPixel(ICON_WIDTH, context),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = index * AppUtils.convertDpInPixel(ICON_START_MARGIN, context)
                }

                setImageResource((workoutIconType.iconRes ?: 0))
                workoutIconType.iconTint?.let { tintColor ->
                    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tintColor))
                }
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

        private const val ICON_WIDTH = 18
        private const val ICON_START_MARGIN = 12
    }
}