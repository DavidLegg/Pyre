package gov.nasa.jpl.parakeet.examples.sequencing.telecom

import gov.nasa.jpl.parakeet.examples.sequencing.primeness.DualString
import gov.nasa.jpl.parakeet.examples.sequencing.primeness.Side
import gov.nasa.jpl.parakeet.foundation.reporting.Reporting.registered
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope.Companion.subContext

class TelecomModel(
    context: InitScope,
) {
    val radios: DualString<Radio>
    val twtas: DualString<TWTA>

    init {
        with (context) {
            radios = DualString(
                discreteResource("prime_radio", Side.A).registered(),
                ::Radio,
                subContext("radio")
            )
            twtas = DualString(
                discreteResource("prime_twta", Side.A).registered(),
                ::TWTA,
                subContext("twta")
            )
        }
    }
}