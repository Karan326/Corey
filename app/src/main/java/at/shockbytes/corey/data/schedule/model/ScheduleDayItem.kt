package at.shockbytes.corey.data.schedule.model

import at.shockbytes.corey.common.core.workout.model.LocationType
import at.shockbytes.corey.common.core.workout.model.WorkoutIconType
import java.util.UUID

data class ScheduleDayItem(
    val name: String,
    var day: Int,
    val id: String = UUID.randomUUID().toString(),
    val locationType: LocationType = LocationType.NONE,
    val workoutIconType: WorkoutIconType = WorkoutIconType.NONE
)