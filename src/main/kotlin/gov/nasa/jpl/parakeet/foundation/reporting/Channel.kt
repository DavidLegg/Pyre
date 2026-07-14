package gov.nasa.jpl.parakeet.foundation.reporting

import gov.nasa.jpl.parakeet.kernel.Name
import kotlin.reflect.KType

class Channel<T> internal constructor(
    val name: Name,
    val reportType: KType,
) {
    override fun toString(): String = name.toString()
}
