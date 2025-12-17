package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.kernel.Name
import kotlin.reflect.KType

class Channel<T> internal constructor(
    val name: Name,
    val reportType: KType,
) {
    override fun toString(): String = name.toString()
}
