package gov.nasa.jpl.pyre.examples.lander.models.data

import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
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
            fullWakeRate = discreteResource("FullWakeRate", defaultFullWakeRate).registered()
            diagnosticWakeRate = discreteResource("DiagnosticWakeRate", defaultDiagnosticWakeRate).registered()
        }
    }
}