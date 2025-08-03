package gov.nasa.jpl.pyre.examples.sequencing

import gov.nasa.jpl.pyre.coals.Closeable.Companion.asCloseable
import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo.Companion.JSON_FORMAT
import gov.nasa.jpl.pyre.flame.plans.runStandardPlanSimulation
import gov.nasa.jpl.pyre.flame.reporting.CSVReportHandler

fun main(args: Array<String>) {
    runStandardPlanSimulation(
        args[0],
        ::SequencingDemo,
        JSON_FORMAT
    ) { CSVReportHandler(it, JSON_FORMAT).asCloseable() }
}
