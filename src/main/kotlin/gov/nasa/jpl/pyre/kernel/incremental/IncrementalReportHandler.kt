package gov.nasa.jpl.pyre.kernel.incremental

/**
 * A generalization of [gov.nasa.jpl.pyre.kernel.ReportHandler] which allows the simulator to revoke a report it issued previously,
 * in response to incremental changes to the simulation.
 */
interface IncrementalReportHandler {
    fun report(report: IncrementalReport<*>)
    fun revoke(report: IncrementalReport<*>)
}