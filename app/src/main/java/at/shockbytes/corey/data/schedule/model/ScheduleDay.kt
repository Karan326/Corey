package at.shockbytes.corey.data.schedule.model

import at.shockbytes.corey.common.core.workout.model.WorkoutIconType
import com.google.firebase.database.Exclude

data class ScheduleDay(
    var day: Int = -1,
    val id: String = "",
    val items: List<ScheduleDayItem> = listOf()
) {

    @get:Exclude
    val isEmpty: Boolean
        get() = items.isEmpty()

    @get:Exclude
    val name: String
        get() = items.joinToString(separator = " & ") { it.name }

    @get:Exclude
    val icons: List<WorkoutIconType>
        get() = items.map { it.workoutIconType }

    @get:Exclude
    val notificationWorkoutIcon: WorkoutIconType
        get() = items.first().workoutIconType

    @get:Exclude
    val itemSize: Int
        get() = items.size

}