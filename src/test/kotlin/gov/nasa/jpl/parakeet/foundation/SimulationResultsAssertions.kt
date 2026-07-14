package gov.nasa.jpl.parakeet.foundation

import gov.nasa.jpl.parakeet.foundation.resources.discrete.Discrete
import gov.nasa.jpl.parakeet.general.results.SimulationResults
import gov.nasa.jpl.parakeet.kernel.Name
import kotlin.test.assertEquals
import kotlin.time.Instant

object SimulationResultsAssertions {
    interface ProfileChecker<D> {
        fun reports(time: Instant, data: D)
    }

    fun <D> SimulationResults.checkChannel(name: Name, block: ProfileChecker<D>.() -> Unit) {
        val reports = resources.getValue(name).data
        var n = 0
        block(object : ProfileChecker<D> {
            override fun reports(time: Instant, data: D) {
                assert(n < reports.size)
                val report = reports[n++]
                assertEquals(name, report.channel)
                assertEquals(time, report.time)
                assertEquals(data, report.data)
            }
        })
        assertEquals(reports.size, n)
    }

    fun <D> SimulationResults.checkChannel(name: String, block: ProfileChecker<D>.() -> Unit) =
        checkChannel(Name(name), block)

    fun <D> ProfileChecker<D>.reports(timeString: String, data: D) =
        reports(Instant.parse(timeString), data)

    fun <V> ProfileChecker<Discrete<V>>.reportsDiscrete(time: Instant, value: V) =
        reports(time, Discrete(value))

    fun <V> ProfileChecker<Discrete<V>>.reportsDiscrete(timeString: String, value: V) =
        reports(timeString, Discrete(value))

    interface ActivityChecker {
        fun finished(name: String, start: Instant, end: Instant = start, includeStart: Boolean = true)
        fun unfinished(name: String, start: Instant)
    }

    fun SimulationResults.checkActivities(block: ActivityChecker.() -> Unit) {
        val unmatchedActivities = activities.toMutableList()
        block(object : ActivityChecker {
            override fun finished(name: String, start: Instant, end: Instant, includeStart: Boolean) {
                if (includeStart) contains(name, start, null)
                contains(name, start, end)
            }
            override fun unfinished(name: String, start: Instant) {
                contains(name, start, null)
            }
            private fun contains(name: String, start: Instant, end: Instant?) {
                val matchingEventIndex = unmatchedActivities.indexOfFirst {
                    it.name == Name(name) && it.start == start && it.end == end
                }
                assert(matchingEventIndex in unmatchedActivities.indices)
                unmatchedActivities.removeAt(matchingEventIndex)
            }
        })
        assert(unmatchedActivities.isEmpty())
    }

    fun ActivityChecker.finished(name: String, start: String, end: String = start, includeStart: Boolean = true) =
        finished(name, Instant.parse(start), Instant.parse(end), includeStart)

    fun ActivityChecker.unfinished(name: String, start: String) =
        unfinished(name, Instant.parse(start))
}