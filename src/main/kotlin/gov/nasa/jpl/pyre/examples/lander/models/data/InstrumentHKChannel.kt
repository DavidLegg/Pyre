package gov.nasa.jpl.pyre.examples.lander.models.data

import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.register
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class InstrumentHKChannel(
    context: SparkInitContext,
    val apid: DataConfig.APID,
    val defaultFullWakeRate: Double,
    val defaultDiagnosticWakeRate: Double,
) {
    val fullWakeRate: DoubleResource
    val diagnosticWakeRate: DoubleResource

    init {
        with (context) {
            fullWakeRate = discreteResource("FullWakeRate", defaultFullWakeRate)
            diagnosticWakeRate = discreteResource("DiagnosticWakeRate", defaultDiagnosticWakeRate)

            register("FullWakeRate", fullWakeRate)
            register("DiagnosticWakeRate", diagnosticWakeRate)
        }
    }
}