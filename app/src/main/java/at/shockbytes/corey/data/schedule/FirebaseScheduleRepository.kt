package at.shockbytes.corey.data.schedule

import android.content.Context
import at.shockbytes.core.scheduler.SchedulerFacade
import at.shockbytes.corey.R
import at.shockbytes.corey.common.core.workout.model.WorkoutIconType
import at.shockbytes.corey.data.schedule.model.ScheduleDay
import at.shockbytes.corey.data.schedule.model.ScheduleDayItem
import at.shockbytes.corey.data.workout.WorkoutRepository
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber

/**
 * Author:  Martin Macheiner
 * Date:    22.02.2017
 */
class FirebaseScheduleRepository(
    private val context: Context,
    private val gson: Gson,
    private val workoutManager: WorkoutRepository,
    private val remoteConfig: FirebaseRemoteConfig,
    private val firebase: FirebaseDatabase,
    private val schedulers: SchedulerFacade
) : ScheduleRepository {

    init {
        setupFirebase()
    }

    private val scheduleItemSubject = BehaviorSubject.create<List<ScheduleDay>>()
    private val scheduleItems: MutableList<ScheduleDay> = mutableListOf()

    override val schedule: Observable<List<ScheduleDay>> = scheduleItemSubject

    override val schedulableItems: Observable<List<SchedulableItem>>
        get() = workoutManager.workouts
                .map { workouts ->
                    val workoutItems = workouts
                            .map { w ->
                                SchedulableItem(
                                    w.displayableName,
                                    w.locationType,
                                    WorkoutIconType.fromBodyRegion(w.bodyRegion)
                                )
                            }
                            .toMutableList()

                    val schedulingItemsAsJson = remoteConfig
                            .getString(context.getString(R.string.remote_config_scheduling_items))
                    val remoteConfigItems = gson.fromJson(schedulingItemsAsJson, Array<SchedulableItem>::class.java)
                    workoutItems
                            .apply {
                                addAll(remoteConfigItems)
                            }
                            .toList()
                }

    override fun poke() = Unit

    override fun insertScheduleItemAtDay(dayItem: ScheduleDayItem, day: Int): ScheduleDay {
        return if (areItemsScheduledForDay(day)) {
            updateScheduleDayWithItem(dayItem, day)
        } else {
            createNewScheduleDay(dayItem, day)
        }
    }

    private fun updateScheduleDayWithItem(dayItem: ScheduleDayItem, day: Int): ScheduleDay {
        val scheduleDay = getItemForDay(day)
            ?: createNewScheduleDay(dayItem, day) // Fallback to new instantiation, should never happen

        val updatedItems = scheduleDay.items.toMutableList()
            .apply {
                add(dayItem)
            }
            .toList()

        val updated = scheduleDay.copy(items = updatedItems)
        updateScheduleDay(updated)
        return updated
    }

    private fun createNewScheduleDay(dayItem: ScheduleDayItem, day: Int): ScheduleDay {
        val ref = firebase.getReference(PATH_SCHEDULE).push()

        val scheduleDay = ScheduleDay(
            id = ref.key ?: "",
            day = day,
            items = listOf(dayItem)
        )

        ref.setValue(scheduleDay)
        return scheduleDay
    }

    private fun areItemsScheduledForDay(day: Int): Boolean {
        return scheduleItems.any { it.day == day }
    }

    private fun getItemForDay(day: Int): ScheduleDay? {
        return scheduleItems.find { it.day == day }
    }

    override fun updateScheduleDay(scheduleDay: ScheduleDay){
        firebase.getReference(PATH_SCHEDULE).child(scheduleDay.id).setValue(scheduleDay)
    }

    override fun deleteScheduleDay(scheduleDay: ScheduleDay) {
        firebase.getReference(PATH_SCHEDULE).child(scheduleDay.id).removeValue()
    }

    override fun deleteAll(): Completable {
        return Completable
                .create { emitter ->
                    firebase.getReference(PATH_SCHEDULE).removeValue()
                            .addOnCompleteListener { emitter.onComplete() }
                            .addOnFailureListener { throwable -> emitter.onError(throwable) }
                }
                .subscribeOn(schedulers.io)
    }

    private fun setupFirebase() {

        firebase.getReference(PATH_SCHEDULE).addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
                dataSnapshot.getValue(ScheduleDay::class.java)?.let { item ->
                    scheduleItems.add(item)
                    scheduleItemSubject.onNext(scheduleItems)
                }
            }

            override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {
                dataSnapshot.getValue(ScheduleDay::class.java)?.let { changed ->

                    val indexOfChanged = scheduleItems.indexOf(changed)
                    scheduleItems[indexOfChanged] = changed
                    scheduleItemSubject.onNext(scheduleItems)
                }
            }

            override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                dataSnapshot.getValue(ScheduleDay::class.java)?.let { removed ->
                    scheduleItems.remove(removed)
                    scheduleItemSubject.onNext(scheduleItems)
                }
            }

            override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {
                Timber.d("ScheduleItem moved: $dataSnapshot / $s")
            }

            override fun onCancelled(databaseError: DatabaseError) = Unit
        })
    }

    companion object {

        private const val PATH_SCHEDULE = "/schedule2"
    }
}
