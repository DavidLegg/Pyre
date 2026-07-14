package pyre_tutorials.util

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.general.results.SimulationResults

object Output {
    /**
     * Dump [this] to stdout in a simple format.
     *
     * Designed to cut out details for empty parts of the results, to facilitate simple tutorials where some features are not in use.
     */
    fun SimulationResults.dump(title: String? = null) {
        println("--- ${title ?: "SimulationResults"} ---")
        println("Start: $startTime")
        println("End:   $endTime")

        if (activities.isNotEmpty()) {
            println("Activities:")
            for (activity in activities) {
                println("  ${activity.friendlyFormat()}")
            }
        }

        if (resources.isNotEmpty()) {
            println("Resources:")
            for ((name, profile) in resources) {
                println("  $name")
                val metadata = profile.metadata.metadata
                if (metadata.isNotEmpty()) {
                    for ((key, value) in metadata) {
                        println("    $key -> ${value.text}")
                    }
                    println("    ---")
                }
                for (report in profile.data) {
                    println("    ${report.time} -> ${report.data}")
                }
            }
        }
    }

    private fun ActivityEvent.friendlyFormat(): String {
        val fullName = if (name.simpleName == type) name else "$name ($type)"
        return if (end == null) {
            "Start $fullName at $start"
        } else {
            "End $fullName at $end"
        }
    }
}