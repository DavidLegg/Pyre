package gov.nasa.jpl.pyre.incremental

import kotlin.time.Instant

// Time within the simulator is primarily the Instant at which a task runs.
// Within a single instant, there's a series of job batches.
// All the jobs in a batch run in parallel.
// The ordering of steps between two parallel jobs is meaningless, but we can impose an arbitrary order for sorting purposes.
// Finally, within a job, there are a series of steps.
// Since we may move steps within a branch to do incremental re-simulation, the step number is mutable.
// In order not to break containers that rely on the ordering of times, the simulator guarantees
// that it shall not change the relative ordering of times that are "active" -
// That is, it will either maintain their relative order, or it will revoke at least one object involved.
// Revoked objects must be "forgotten" - removed from any containers and references to them deleted.
data class SimulationTime(
    val instant: Instant,
    val batch: Int = 0,
    val branch: Int = 0,
    var step: Int = 0,
) : Comparable<SimulationTime> {
    override fun compareTo(other: SimulationTime): Int {
        if (this === other) return 0

        var n = instant.compareTo(other.instant)
        if (n == 0) n = batch.compareTo(other.batch)
        if (n == 0) n = branch.compareTo(other.branch)
        if (n == 0) n = step.compareTo(other.step)
        return n
    }

    override fun toString(): String = "$instant::$batch/$branch/$step"
}