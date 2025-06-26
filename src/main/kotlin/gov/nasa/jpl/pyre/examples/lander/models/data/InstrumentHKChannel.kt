package gov.nasa.jpl.pyre.examples.lander.models.data

import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.registeredDiscreteResource
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
            fullWakeRate = registeredDiscreteResource("FullWakeRate", defaultFullWakeRate)
            diagnosticWakeRate = registeredDiscreteResource("DiagnosticWakeRate", defaultDiagnosticWakeRate)
        }
    }
}