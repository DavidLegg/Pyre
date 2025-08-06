package gov.nasa.jpl.pyre.examples.lander.models.time

import gov.nasa.jpl.pyre.flame.resources.unstructured.UnstructuredResource
import gov.nasa.jpl.pyre.flame.resources.unstructured.UnstructuredResourceApplicative.map
import gov.nasa.jpl.pyre.flame.resources.unstructured.UnstructuredResourceOperations.asUnstructured
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
class Clocks(context: SparkInitScope) {
    val epoch: Time
    val time: UnstructuredResource<Time>
    // Convenience functions for looking up the current time in various formats
    val utcTimestamp: UnstructuredResource<String>
    val lmstTimestamp: UnstructuredResource<String>

    init {
        with (context) {
            epoch = Time(simulationEpoch.toJavaInstant())
            time = map(simulationClock.asUnstructured()) { epoch + it }
            utcTimestamp = map(time, Time::utcTimestamp)
            lmstTimestamp = map(time, Time::lmstTimestamp)
        }
    }
}