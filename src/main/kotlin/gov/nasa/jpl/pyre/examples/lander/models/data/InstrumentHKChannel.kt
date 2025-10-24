package gov.nasa.jpl.pyre.examples.lander.models.data

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope

class InstrumentHKChannel(
    context: InitScope,
    val apid: DataConfig.APID,
    val defaultFullWakeRate: Double,
    val defaultDiagnosticWakeRate: Double,
) {
    val fullWakeRate: MutableDoubleResource
    val diagnosticWakeRate: MutableDoubleResource

    init {
        with (context) {
            fullWakeRate = registeredDiscreteResource("FullWakeRate", defaultFullWakeRate)
            diagnosticWakeRate = registeredDiscreteResource("DiagnosticWakeRate", defaultDiagnosticWakeRate)
        }
    }
}