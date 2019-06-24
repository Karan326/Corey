package at.shockbytes.corey.util

import at.shockbytes.corey.data.schedule.model.ScheduleDay

fun ScheduleDay.isItemOfCurrentDay(currentDay: Int): Boolean {
    return day == currentDay && !isEmpty
}