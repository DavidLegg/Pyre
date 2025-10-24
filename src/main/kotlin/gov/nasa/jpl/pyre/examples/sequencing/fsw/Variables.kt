package gov.nasa.jpl.pyre.examples.sequencing.fsw

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableStringResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope

class Variables(
    numberOfVariables: Map<VariableType, Int>,
    context: InitScope,
) {
    enum class VariableType {
        INT,
        UINT,
        FLOAT,
        STRING
    }

    val ints: List<MutableIntResource>
    val uints: List<MutableDiscreteResource<UInt>>
    val floats: List<MutableDoubleResource>
    val strings: List<MutableStringResource>

    init {
        with (context) {
            with (subContext("int")) {
                ints = Array(numberOfVariables.getOrDefault(VariableType.INT, 0)) {
                    registeredDiscreteResource(it.toString(), 0)
                }.toList()
            }
            with (subContext("uint")) {
                uints = Array(numberOfVariables.getOrDefault(VariableType.UINT, 0)) {
                    registeredDiscreteResource(it.toString(), 0.toUInt())
                }.toList()
            }
            with (subContext("float")) {
                floats = Array(numberOfVariables.getOrDefault(VariableType.FLOAT, 0)) {
                    registeredDiscreteResource(it.toString(), 0.0)
                }.toList()
            }
            with (subContext("string")) {
                strings = Array(numberOfVariables.getOrDefault(VariableType.STRING, 0)) {
                    registeredDiscreteResource(it.toString(), "")
                }.toList()
            }
        }
    }

    context (scope: TaskScope)
    suspend fun reset() {
        ints.forEach { it.set(0) }
        uints.forEach { it.set(0.toUInt()) }
        floats.forEach { it.set(0.0) }
        strings.forEach { it.set("") }
    }
}
