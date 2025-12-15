package gov.nasa.jpl.pyre.examples.lander.models.comm

import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.integral

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
            downlinkRate = discreteResource("downlinkRate", 0.0).registered()
            dataSent =
                downlinkRate.asPolynomial().integral("dataSent", 0.0).registered<Double, Polynomial, IntegralResource>()
            activeXBandAntenna = discreteResource("activeXBandAntenna", XBandAntenna.EAST_MGA).registered()

            subContext("alternate_block_in_use") {
                alternateUhfBlockInUseMap = Orbiter.entries.associateWith {
                    discreteResource(it.toString(), false).registered()
                }
            }
        }
    }
}