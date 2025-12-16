package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.kernel.Name
import kotlin.reflect.KType

// TODO: Do any of these need to be private or internal?
class Channel<T> internal constructor(
    val name: Name,
    val reportType: KType,
) {
    override fun toString(): String = name.toString()
}
