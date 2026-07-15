package gov.nasa.jpl.parakeet.examples.sequencing.sequence_engine

import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope

fun interface CommandBehavior {
    context (scope: TaskScope)
    suspend fun effectModel(command: Command)

    companion object {
        val ignore = CommandBehavior {}
    }
}