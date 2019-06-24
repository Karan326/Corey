package at.shockbytes.corey.data.schedule

import at.shockbytes.corey.common.core.util.Pokeable
import at.shockbytes.corey.data.schedule.model.ScheduleDay
import at.shockbytes.corey.data.schedule.model.ScheduleDayItem
import io.reactivex.Completable
import io.reactivex.Observable

/**
 * Author:  Martin Macheiner
 * Date:    21.02.2017
 */
interface ScheduleRepository : Pokeable {

    val schedule: Observable<List<ScheduleDay>>

    val schedulableItems: Observable<List<SchedulableItem>>

    fun insertScheduleItemAtDay(dayItem: ScheduleDayItem, day: Int): ScheduleDay

    fun updateScheduleDay(scheduleDay: ScheduleDay)

    fun deleteScheduleDay(scheduleDay: ScheduleDay)

    fun deleteAll(): Completable

    fun hasDayReachedMaxItems(item: ScheduleDay): Boolean {
        return item.itemSize >= MAX_DAY_ITEMS
    }

    companion object {

        private const val MAX_DAY_ITEMS = 2
    }
}
