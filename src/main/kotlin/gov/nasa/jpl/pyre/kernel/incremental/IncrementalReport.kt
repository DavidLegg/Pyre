package gov.nasa.jpl.pyre.kernel.incremental

/**
 * A single report from an incremental simulation.
 * The [time] field provides fine-grained timing information.
 * All active reports will have a unique [time].
 */
sealed interface IncrementalReport<T> {
    val time: SimulationTime
    val content: T
}