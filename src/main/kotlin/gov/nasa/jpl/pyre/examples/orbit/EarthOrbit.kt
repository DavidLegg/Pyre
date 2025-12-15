package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.kernel.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.examples.orbit.OrbitalSimulation.Vector
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext

class EarthOrbit(
    context: InitScope,
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
                subContext("Earth Orbit"),
                listOf(earth, moon),
                HOUR,
            )

            earthPosition = orbitalSimulation.bodyPositions.getValue(earth)
                .named { "earth_position" }
                .registered()
            moonPosition = orbitalSimulation.bodyPositions.getValue(moon)
                .named { "moon_position" }
                .registered()
        }
    }
}