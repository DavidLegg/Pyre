package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.foundation.resources.FullDynamics
import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.every
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope

class VerletIntegrator(
    context: InitScope,
    private val name: String,
    initialPosition: DoubleArray,
    initialVelocity: DoubleArray,
    acceleration: (DoubleArray) -> DoubleArray,
    private val stepSize: Duration,
) : DiscreteResource<DoubleArray> {
    private val priorPosition: DiscreteResource<DoubleArray>
    private val position: DiscreteResource<DoubleArray>

    context (scope: ResourceScope)
    override suspend fun getDynamics(): FullDynamics<Discrete<DoubleArray>> = position.getDynamics()

    init {
        with (context) {
            position = discreteResource("$name.position", initialPosition)
            // This calculation of prior position ignores acceleration, introducing a small error.
            // That's good enough for now.
            val delta_t = stepSize.ratioOver(SECOND)
            val initialPriorPosition = DoubleArray(initialPosition.size)
            for (i in initialPosition.indices) {
                initialPriorPosition[i] = initialPosition[i] - initialVelocity[i] * delta_t
            }
            priorPosition = discreteResource("$name.priorPosition", initialPriorPosition)

            spawn("Run $name", every(stepSize) {
                val x_1 = position.getValue()
                val x_0 = priorPosition.getValue()
                val a = acceleration(x_1)
                val result = DoubleArray(x_0.size)
                for (i in result.indices) {
                    result[i] = (2 * x_1[i]) - x_0[i] + (a[i] * delta_t * delta_t)
                }
                priorPosition.set(x_1)
                position.set(result)
            })
        }
    }
}