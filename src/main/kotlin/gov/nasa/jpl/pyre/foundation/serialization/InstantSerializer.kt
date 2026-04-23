package gov.nasa.jpl.pyre.foundation.serialization

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.utilities.Serialization.alias
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Instant

class InstantSerializer : KSerializer<Instant> by String.serializer().alias(
    InvertibleFunction.of(
    Instant.Companion::parse,
    Instant::toString
))