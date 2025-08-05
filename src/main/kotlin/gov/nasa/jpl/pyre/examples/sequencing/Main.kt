package gov.nasa.jpl.pyre.examples.sequencing

import gov.nasa.jpl.pyre.flame.plans.runStandardPlanSimulation

fun main(args: Array<String>) {
    runStandardPlanSimulation(
        args[0],
        ::SequencingDemo,
        SequencingDemo.JSON_FORMAT
    )
}
