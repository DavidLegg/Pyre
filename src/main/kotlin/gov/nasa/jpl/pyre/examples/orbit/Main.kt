package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.general.plans.runStandardPlanSimulation

// This is a simple setup, using default choices for how to run a simulation.
// Most importantly, it chooses to read and write files for incon, plan, outputs, and fincon,
// and to use the default CSV event output format.
fun simpleMain(args: Array<String>) {
    runStandardPlanSimulation(args[0], ::EarthOrbit, EarthOrbit.JSON_FORMAT)
}
