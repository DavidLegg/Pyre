package gov.nasa.jpl.pyre.examples.lander.models.comm

import gov.nasa.jpl.pyre.flame.composition.subContext
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.integral
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.register
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.register
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class CommModel(
    context: SparkInitContext,
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

            with (subContext("alternate_block_in_use")) {
                alternateUhfBlockInUseMap = Orbiter.entries.associateWith {
                    registeredDiscreteResource(it.toString(), false)
                }
            }
        }
    }
}