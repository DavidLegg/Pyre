package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.examples.orbit.OrbitalSimulation.Vector
import gov.nasa.jpl.pyre.spark.reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class EarthOrbit(
    context: SparkInitContext,
) {
    private val orbitalSimulation: OrbitalSimulation
    val earthPosition: DiscreteResource<Vector>
    val moonPosition: DiscreteResource<Vector>

    init {
        with (context) {
            val earth = OrbitalSimulation.Body(
                "Earth",
                Vector(0.0, 0.0, 0.0),
                Vector(0.0, 0.0, 0.0),
                5.972e24
            )
            val moon = OrbitalSimulation.Body(
                "Moon",
                Vector(3.844e8, 0.0, 0.0),
                Vector(0.0, 1.022e3, 0.0),
                7.346e22
            )
            orbitalSimulation = OrbitalSimulation(
                context,
                "Earth Orbit",
                listOf(earth, moon),
                HOUR,
            )

            earthPosition = orbitalSimulation.bodyPositions.getValue(earth)
            moonPosition = orbitalSimulation.bodyPositions.getValue(moon)

            register("earth_position", earthPosition)
            register("moon_position", moonPosition)
        }
    }
}