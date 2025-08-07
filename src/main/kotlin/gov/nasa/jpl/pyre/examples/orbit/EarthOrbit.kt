package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.examples.orbit.OrbitalSimulation.Vector
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope

class EarthOrbit(
    context: SparkInitScope,
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

            earthPosition = orbitalSimulation.bodyPositions.getValue(earth) named { "earth_position" }
            moonPosition = orbitalSimulation.bodyPositions.getValue(moon) named { "moon_position" }

            register(earthPosition)
            register(moonPosition)
        }
    }
}