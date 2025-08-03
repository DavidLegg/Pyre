package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

fun interface CommandBehavior {
    context (scope: SparkTaskScope)
    suspend fun effectModel(command: Command)

    companion object {
        val ignore = CommandBehavior {}
    }
}