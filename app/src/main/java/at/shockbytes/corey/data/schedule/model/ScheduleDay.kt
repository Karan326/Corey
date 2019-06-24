package at.shockbytes.corey.data.schedule.model

data class ScheduleDay(
    val day: Int,
    val items: List<ScheduleDayItem>
)