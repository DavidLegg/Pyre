package gov.nasa.jpl.pyre.examples.sequencing.telecom

import gov.nasa.jpl.pyre.examples.sequencing.primeness.DualString
import gov.nasa.jpl.pyre.examples.sequencing.primeness.Side
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext

class TelecomModel(
    context: InitScope,
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