package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.coals.InvertibleFunction.Companion.withInverse
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.DOUBLE_ARRAY_SERIALIZER
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.alias
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlin.math.sqrt

class OrbitalSimulation(
    context: SparkInitContext,
    name: String,
    bodies: List<Body>,
    stepSize: Duration,
) {
    /**
     * @param initialPosition Units of m
     * @param initialVelocity Units of m/s
     * @param mass Units of kg
     */
    class Body(
        val name: String,
        val initialPosition: Vector,
        val initialVelocity: Vector,
        val mass: Double,
    )
    data class Vector(val x: Double, val y: Double, val z: Double) {
        fun asSequence() = sequenceOf(x, y, z)

        companion object {
            fun serializer(): Serializer<Vector> = DOUBLE_ARRAY_SERIALIZER.alias(
                { v: Vector -> doubleArrayOf(v.x, v.y, v.z) }
                withInverse
                { a: DoubleArray -> Vector(a[0], a[1], a[2]) }
            )
        }
    }

    private val integrator: VerletIntegrator
    val bodyPositions: Map<Body, DiscreteResource<Vector>>

    init {
        with (context) {
            require(bodies.isNotEmpty()) { "At least one body must be defined" }

            val G = 6.6743e-11
            val mass = bodies.map { it.mass }.toDoubleArray()

            integrator = VerletIntegrator(
                context,
                name,
                initialPosition = bodies.flatMap { it.initialPosition.asSequence() }.toDoubleArray(),
                initialVelocity = bodies.flatMap { it.initialVelocity.asSequence() }.toDoubleArray(),
                acceleration = { position ->
                    val acceleration = DoubleArray(position.size) { 0.0 }
                    for (i in bodies.indices) {
                        for (j in 0..(i - 1)) {
                            val r_x = position[3 * j + 0] - position[3 * i + 0]
                            val r_y = position[3 * j + 1] - position[3 * i + 1]
                            val r_z = position[3 * j + 2] - position[3 * i + 2]
                            val r_squared = (r_x * r_x) + (r_y * r_y) + (r_z * r_z)
                            val r = sqrt(r_squared)
                            acceleration[3 * i + 0] = G * mass[j] * r_x / (r_squared * r)
                            acceleration[3 * i + 1] = G * mass[j] * r_y / (r_squared * r)
                            acceleration[3 * i + 2] = G * mass[j] * r_z / (r_squared * r)
                            acceleration[3 * j + 0] = -G * mass[i] * r_x / (r_squared * r)
                            acceleration[3 * j + 1] = -G * mass[i] * r_y / (r_squared * r)
                            acceleration[3 * j + 2] = -G * mass[i] * r_z / (r_squared * r)
                        }
                    }
                    acceleration
                },
                stepSize = stepSize,
            )

            bodyPositions = bodies.withIndex().associate { (i, body) -> Pair(body, map(integrator) {
                Vector(it[3 * i + 0], it[3 * i + 1], it[3 * i + 2])
            }) }
        }
    }
}