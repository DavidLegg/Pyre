package parakeet_tutorials

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlin.time.Duration

data class DownlinkFiles(
    val fileType: String,
    val numberOfFiles: Int,
    val delayBetweenFiles: Duration,
) : Activity<CommSystem> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: CommSystem) {
        for (i in 1..numberOfFiles) {
            // Here we're asking the model to handle the downlink.
            // Since this will happen in a spawned sub-task, it won't block this task.
            model.downlink("$fileType file $i")
            delay(delayBetweenFiles)
        }
    }
}