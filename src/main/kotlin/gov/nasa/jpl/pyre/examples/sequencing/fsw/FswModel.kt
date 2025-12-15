package gov.nasa.jpl.pyre.examples.sequencing.fsw

import gov.nasa.jpl.pyre.examples.sequencing.primeness.DualString
import gov.nasa.jpl.pyre.examples.sequencing.primeness.Side
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext

class FswModel(
    context: InitScope,
) {
    val primeComputer: MutableDiscreteResource<Side>
    val globals: DualString<Variables>

    init {
        with (context) {
            primeComputer = discreteResource("prime_computer", Side.A).registered()
            globals = DualString(primeComputer, { Variables(NUMBER_OF_GLOBALS, it) }, subContext("globals"))
        }
    }

    companion object {
        val NUMBER_OF_GLOBALS = mapOf(
            Variables.VariableType.INT to GlobalIntVarName.entries.size,
            Variables.VariableType.UINT to GlobalUIntVarName.entries.size,
            Variables.VariableType.FLOAT to GlobalFloatVarName.entries.size,
            Variables.VariableType.STRING to GlobalStringVarName.entries.size,
        )
    }

    sealed interface GlobalVarName
    enum class GlobalIntVarName: GlobalVarName {
        G00INT,
        G01INT,
        G02INT,
        G03INT,
        G04INT,
        G05INT,
        G06INT,
        G07INT,
        G08INT,
        G09INT,
    }
    enum class GlobalUIntVarName: GlobalVarName {
        G00UINT,
        G01UINT,
        G02UINT,
        G03UINT,
        G04UINT,
        G05UINT,
        G06UINT,
        G07UINT,
        G08UINT,
        G09UINT,
    }
    enum class GlobalFloatVarName: GlobalVarName {
        G00FLT,
        G01FLT,
        G02FLT,
        G03FLT,
        G04FLT,
        G05FLT,
        G06FLT,
        G07FLT,
        G08FLT,
        G09FLT,
    }
    enum class GlobalStringVarName: GlobalVarName {
        G00STR,
        G01STR,
        G02STR,
        G03STR,
        G04STR,
    }
}