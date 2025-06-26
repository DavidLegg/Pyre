package gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data

import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data.DataConfig.APID
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.register
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class InstrumentHKChannel(
    context: SparkInitContext,
    val apid: APID,
    val defaultFullWakeRate: Double,
    val defaultDiagnosticWakeRate: Double,
    basePath: String,
) {
    val fullWakeRate: DoubleResource
    val diagnosticWakeRate: DoubleResource

    init {
        with (context) {
            fullWakeRate = discreteResource("$basePath/FullWakeRate", defaultFullWakeRate)
            diagnosticWakeRate = discreteResource("$basePath/DiagnosticWakeRate", defaultDiagnosticWakeRate)

            register("$basePath/FullWakeRate", fullWakeRate)
            register("$basePath/DiagnosticWakeRate", diagnosticWakeRate)
        }
    }
}