package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serialization.alias
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Instant

class InstantSerializer : KSerializer<Instant> by String.Companion.serializer().alias(
    InvertibleFunction.Companion.of(
    Instant.Companion::parse,
    Instant::toString
))