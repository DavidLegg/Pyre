package gov.nasa.jpl.pyre.examples.sequencing.telecom

import gov.nasa.jpl.pyre.examples.sequencing.primeness.DualString
import gov.nasa.jpl.pyre.examples.sequencing.primeness.Side
import gov.nasa.jpl.pyre.flame.tasks.subContext
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class TelecomModel(
    context: SparkInitContext,
) {
    val radios: DualString<Radio>
    val twtas: DualString<TWTA>

    init {
        with (context) {
            radios = DualString(
                registeredDiscreteResource("prime_radio", Side.A),
                ::Radio,
                subContext("radio")
            )
            twtas = DualString(
                registeredDiscreteResource("prime_twta", Side.A),
                ::TWTA,
                subContext("twta")
            )
        }
    }
}