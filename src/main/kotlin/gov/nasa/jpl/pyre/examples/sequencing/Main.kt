package gov.nasa.jpl.pyre.examples.sequencing

import gov.nasa.jpl.pyre.general.plans.runStandardPlanSimulation
import kotlin.io.path.Path
import kotlin.io.path.absolute

fun main(args: Array<String>) {
    runStandardPlanSimulation(
        args[0],
        { SequencingDemo(Path(args[0]).absolute().parent, this) },
        SequencingDemo.JSON_FORMAT
    )
}
