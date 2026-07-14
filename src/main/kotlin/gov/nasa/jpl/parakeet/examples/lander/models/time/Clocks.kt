package gov.nasa.jpl.parakeet.examples.lander.models.time

import gov.nasa.jpl.pyre.general.resources.unstructured.UnstructuredResource
import gov.nasa.jpl.pyre.general.resources.unstructured.UnstructuredResourceApplicative.map
import gov.nasa.jpl.pyre.general.resources.unstructured.UnstructuredResourceOperations.asUnstructured
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
class Clocks(context: InitScope) {
    val time: UnstructuredResource<Time>
    // Convenience functions for looking up the current time in various formats
    val utcTimestamp: UnstructuredResource<String>
    val lmstTimestamp: UnstructuredResource<String>

    init {
        with (context) {
            time = map(simulationClock.asUnstructured()) { Time(it.toJavaInstant()) }
            utcTimestamp = map(time, Time::utcTimestamp)
            lmstTimestamp = map(time, Time::lmstTimestamp)
        }
    }
}