package gov.nasa.jpl.pyre.foundation

import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.kernel.Name
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
        fun finished(name: String, type: String, start: Instant, end: Instant = start)
        fun unfinished(name: String, type: String, start: Instant)
    }

    fun SimulationResults.checkActivities(block: ActivityChecker.() -> Unit) {
        val unmatchedActivities = activities.values.toMutableList()
        block(object : ActivityChecker {
            override fun finished(name: String, type: String, start: Instant, end: Instant) =
                contains(name, type, start, end)
            override fun unfinished(name: String, type: String, start: Instant) =
                contains(name, type, start, null)
            private fun contains(name: String, type: String, start: Instant, end: Instant?) {
                val matchingEventIndex = unmatchedActivities.indexOfFirst {
                    it.name == name && it.type == type && it.start == start && it.end == end
                }
                assert(matchingEventIndex in unmatchedActivities.indices)
                unmatchedActivities.removeAt(matchingEventIndex)
            }
        })
        assert(unmatchedActivities.isEmpty())
    }

    fun ActivityChecker.finished(name: String, type: String, start: String, end: String) =
        finished(name, type, Instant.parse(start), Instant.parse(end))

    fun ActivityChecker.finished(name: String, start: String, end: String = start) =
        finished(name, name, start, end)

    fun ActivityChecker.unfinished(name: String, type: String, start: String) =
        unfinished(name, type, Instant.parse(start))

    fun ActivityChecker.unfinished(name: String, start: String) =
        unfinished(name, name, start)
}