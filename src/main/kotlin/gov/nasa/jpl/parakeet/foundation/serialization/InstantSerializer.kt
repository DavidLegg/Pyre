package gov.nasa.jpl.parakeet.foundation.serialization

import gov.nasa.jpl.parakeet.utilities.InvertibleFunction
import gov.nasa.jpl.parakeet.utilities.Serialization.alias
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Instant

class InstantSerializer : KSerializer<Instant> by String.serializer().alias(
    InvertibleFunction.of(
    Instant.Companion::parse,
    Instant::toString
))