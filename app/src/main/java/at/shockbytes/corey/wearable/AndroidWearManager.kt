package at.shockbytes.corey.wearable

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import at.shockbytes.corey.R
import at.shockbytes.corey.common.core.util.WatchInfo
import at.shockbytes.corey.common.core.workout.model.Workout
import at.shockbytes.corey.storage.StorageManager
import at.shockbytes.corey.workout.WorkoutManager
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.CapabilityClient
import com.google.gson.Gson


/**
 * @author Martin Macheiner
 * Date: 18.03.2017.
 */

class AndroidWearManager(private val context: Context,
                         private val workoutManager: WorkoutManager,
                         private val storageManager: StorageManager,
                         private val gson: Gson)
    : WearableManager, MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {

    private var nodeListener: ((WatchInfo) -> Unit)? = null

    override fun onStart(nodeListener: ((WatchInfo) -> Unit)?) {
        this.nodeListener = nodeListener

        Wearable.getMessageClient(context).addListener(this)
        Wearable.getCapabilityClient(context).addLocalCapability(context.getString(R.string.capability_device))
        Wearable.getCapabilityClient(context)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        synchronize()
    }

    override fun onPause() {
        Wearable.getMessageClient(context).removeListener(this)
        Wearable.getCapabilityClient(context).removeLocalCapability(context.getString(R.string.capability_device))
        Wearable.getCapabilityClient(context).removeListener(this)
    }

    override fun synchronizeWorkouts(workouts: List<Workout>) {

        val request = PutDataRequest.create("/workouts")
        val serializedWorkouts = gson.toJson(workouts)
        request.data = serializedWorkouts.toByteArray()

        Wearable.getDataClient(context).putDataItem(request).addOnCompleteListener { }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {

        Log.wtf("Corey", "information received! + " + messageEvent.toString())
        if (messageEvent.path == "/wear_information") {
            Toast.makeText(context, String(messageEvent.data), Toast.LENGTH_SHORT).show()
            grabDataFromMessage(messageEvent)
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.nodes.size > 0) {
            val connectedNode = info.nodes.iterator().next() // Assume first node is watch
            nodeListener?.invoke(WatchInfo(connectedNode?.displayName, true))
        } else {
            nodeListener?.invoke(WatchInfo(null, false))
        }
    }

    private fun grabDataFromMessage(messageEvent: MessageEvent) {

        val s = String(messageEvent.data).split(",".toRegex()).dropLastWhile { it.isEmpty() }

        val pulse = s[0].toIntOrNull()
        val workouts = s[1].toIntOrNull()
        val time = s[2].toIntOrNull()

        if (pulse != null && workouts != null && time != null) {
            storageManager.updateWearWorkoutInformation(pulse, workouts, time)
        }
    }

    private fun synchronize() {
        workoutManager.workouts.subscribe { workouts -> synchronizeWorkouts(workouts) }
    }

}