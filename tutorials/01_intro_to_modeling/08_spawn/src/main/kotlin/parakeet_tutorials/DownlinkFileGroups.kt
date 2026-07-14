package parakeet_tutorials

import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.plans.ActivityActions.spawn
import gov.nasa.jpl.parakeet.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import kotlin.time.Duration

data class DownlinkFileGroups(
    val fileTypes: List<String>,
    val delayBetweenGroups: Duration,
    val numberOfFiles: Int,
    val delayBetweenFiles: Duration,
) : Activity<CommSystem> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: CommSystem) {
        for (fileType in fileTypes) {
            // An activity can spawn another activity, just like a daemon task can spawn an activity.
            // Note that once again, we have to pass the model when spawning an activity,
            // but spawning an activity won't block this task.
            spawn(DownlinkFiles(fileType, numberOfFiles, delayBetweenFiles), model)
            delay(delayBetweenGroups)
        }
    }
}