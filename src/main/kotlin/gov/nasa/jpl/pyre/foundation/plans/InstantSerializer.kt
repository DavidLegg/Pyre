package gov.nasa.jpl.pyre.foundation.plans

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.kernel.Serialization.alias
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Instant

class InstantSerializer : KSerializer<Instant> by String.serializer().alias(
    InvertibleFunction.of(
    Instant.Companion::parse,
    Instant::toString
))