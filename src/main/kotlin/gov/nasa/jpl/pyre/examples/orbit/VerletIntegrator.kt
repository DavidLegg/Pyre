package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.ratioOver
import gov.nasa.jpl.pyre.spark.resources.FullDynamics
import gov.nasa.jpl.pyre.spark.resources.discrete.Discrete
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope
import gov.nasa.jpl.pyre.spark.tasks.every

class VerletIntegrator(
    context: SparkInitScope,
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