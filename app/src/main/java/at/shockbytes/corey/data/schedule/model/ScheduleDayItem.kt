package at.shockbytes.corey.data.schedule.model

import at.shockbytes.corey.common.core.workout.model.LocationType
import at.shockbytes.corey.common.core.workout.model.WorkoutIconType

data class ScheduleDayItem(
    val name: String = "",
    val locationType: LocationType = LocationType.NONE,
    val workoutIconType: WorkoutIconType = WorkoutIconType.NONE
)