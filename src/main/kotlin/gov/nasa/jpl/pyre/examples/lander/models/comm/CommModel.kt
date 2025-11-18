package gov.nasa.jpl.pyre.examples.lander.models.comm

import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext

class CommModel(
    context: InitScope,
) {
    enum class Orbiter {
        ODY,
        MRO,
        TGO,
        MVN,
        MEX
    }

    enum class XBandAntenna {
        EAST_MGA,
        WEST_MGA
    }

    val downlinkRate: MutableDoubleResource
    val dataSent: PolynomialResource
    val activeXBandAntenna: MutableDiscreteResource<XBandAntenna>
    val alternateUhfBlockInUseMap: Map<Orbiter, MutableBooleanResource>

    init {
        with (context) {
            downlinkRate = registeredDiscreteResource("downlinkRate", 0.0)
            dataSent = downlinkRate.asPolynomial().registeredIntegral("dataSent", 0.0)
            activeXBandAntenna = registeredDiscreteResource("activeXBandAntenna", XBandAntenna.EAST_MGA)

            subContext("alternate_block_in_use") {
                alternateUhfBlockInUseMap = Orbiter.entries.associateWith {
                    registeredDiscreteResource(it.toString(), false)
                }
            }
        }
    }
}