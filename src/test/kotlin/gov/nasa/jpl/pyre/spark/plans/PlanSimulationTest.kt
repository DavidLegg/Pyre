package gov.nasa.jpl.pyre.spark.plans

import gov.nasa.jpl.pyre.array
import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.JsonArray
import gov.nasa.jpl.pyre.ember.SimulationState
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.int
import gov.nasa.jpl.pyre.spark.BasicSerializers.INT_SERIALIZER
import gov.nasa.jpl.pyre.spark.ChannelizedReports
import gov.nasa.jpl.pyre.spark.channel
import gov.nasa.jpl.pyre.spark.tasks.SparkContext
import gov.nasa.jpl.pyre.spark.plans.PlanSimulation.PlanSimulationSetup
import gov.nasa.jpl.pyre.spark.plans.PlanSimulationTest.ModelWithResources
import gov.nasa.jpl.pyre.spark.resources.discrete.Discrete
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.register
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.register
import gov.nasa.jpl.pyre.*
import gov.nasa.jpl.pyre.spark.resources.discrete.*
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class PlanSimulationTest {
    @Test
    fun empty_model_can_be_created() {
        assertDoesNotThrow {
            val simulation = PlanSimulation(
                PlanSimulationSetup(
                    reportHandler = { },
                    inconProvider = null,
                    constructModel = { },
                    constructActivity = { fail() },
                )
            )
            simulation.runUntil(HOUR)
        }
    }

    class ModelWithResources {
        val x: DiscreteResource<Int>
        val y: DiscreteResource<String>

        constructor(context: SparkInitContext) {
            with(context) {
                x = discreteResource("x", 0)
                y = discreteResource("y", "XYZ")

                registerInt("x", x)
                registerString("y", y)
            }
        }
    }

    @Test
    fun model_with_resources_can_be_created() {
        val reports = ChannelizedReports()
        val simulation = PlanSimulation(
            PlanSimulationSetup(
                reportHandler = reports::add,
                inconProvider = null,
                constructModel = ::ModelWithResources,
                constructActivity = { fail() },
            )
        )
        simulation.runUntil(HOUR)

        with (reports) {
            channel("x") {
                at(ZERO)
                element { assertEquals(0, int()) }
                assert(atEnd())
            }
            channel("y") {
                at(ZERO)
                element { assertEquals("XYZ", string()) }
                assert(atEnd())
            }
        }
    }
    // TODO
}
