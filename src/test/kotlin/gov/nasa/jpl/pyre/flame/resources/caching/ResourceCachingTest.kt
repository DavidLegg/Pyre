package gov.nasa.jpl.pyre.flame.resources.caching

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.Plan
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.plans.activities
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.jsonlReportHandler
import gov.nasa.jpl.pyre.flame.resources.caching.ResourceCaching.fileBackedResource
import gov.nasa.jpl.pyre.flame.resources.caching.ResourceCachingTest.OriginalResourceModel.*
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResource
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.decrement
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableStringResource
import gov.nasa.jpl.pyre.spark.resources.discrete.StringResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.Reactions.every
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.outputStream
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ResourceCachingTest {
    /**
     * The "original" model has the resource to cache as a mutable or derived resource.
     * The profile of that resource is computed by daemons and activities, "normal" simulation things.
     * That resource is registered like usual and saved to the output file.
     */
    class OriginalResourceModel(
        context: InitScope,
    ) {
        val resourceA: MutableIntResource
        val resourceB: MutableStringResource

        init {
            with (context) {
                resourceA = registeredDiscreteResource("resourceA", 0)
                resourceB = registeredDiscreteResource("resourceB", "Initial string")

                spawn("Decay $resourceA", every(30 * SECOND) {
                    val n = resourceA.getValue()
                    if (n > 0) resourceA.decrement()
                    else if (n < 0) resourceA.increment()
                })
            }
        }

        @Serializable
        @SerialName("ChangeA")
        data class ChangeA(
            val delta: Int
        ) : Activity<OriginalResourceModel> {
            context(scope: TaskScope)
            override suspend fun effectModel(model: OriginalResourceModel) {
                model.resourceA.increment(delta)
            }
        }

        @Serializable
        @SerialName("ChangeB")
        data class ChangeB(
            val newValue: String
        ) : Activity<OriginalResourceModel> {
            context(scope: TaskScope)
            override suspend fun effectModel(model: OriginalResourceModel) {
                model.resourceB.set(newValue)
            }
        }
    }

    /**
     * The "cached" model reads the output file from a prior simulation,
     * replaying that profile in an immutable resource.
     * By naming the resources identically between the "original" and "cached" models, everything naturally lines up.
     */
    class CachedResourceModel(
        resourceFile: Path,
        jsonFormat: Json,
        context: InitScope,
    ) {
        val resourceA: IntResource
        val resourceB: StringResource

        init {
            with (context) {
                resourceA = fileBackedResource("resourceA", resourceFile, jsonFormat)
                resourceB = fileBackedResource("resourceB", resourceFile, jsonFormat)

                // Since file-backed resources are, by definition, being read from disk, we don't normally register them.
                // For the sake of this test, though, registering them gives us an easy way to verify they're working.
                register(resourceA)
                register(resourceB)
            }
        }
    }

    @Test
    fun cached_resources_can_be_read_from_simulation_output() {
        val jsonFormat = Json {
            serializersModule = SerializersModule {
                contextual(Instant::class, String.serializer().alias(InvertibleFunction.of(
                    Instant::parse, Instant::toString
                )))
                activities {
                    activity(ChangeA::class)
                    activity(ChangeB::class)
                }
            }
        }
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val outputFile1 = createTempFile(suffix="output1.jsonl")
        outputFile1.outputStream().use { out ->
            val simulation = PlanSimulation.withoutIncon(
                jsonlReportHandler(out, jsonFormat),
                epoch,
                epoch,
                ::OriginalResourceModel
            )

            simulation.runPlan(Plan(
                epoch,
                epoch + HOUR,
                listOf(
                    GroundedActivity(epoch + 5 * MINUTE, ChangeA(delta = 10)),
                    GroundedActivity(epoch + 15 * MINUTE, ChangeB("Second string")),
                    GroundedActivity(epoch + 20 * MINUTE, ChangeA(delta = -10)),
                    GroundedActivity(epoch + 30 * MINUTE, ChangeB("Third string")),
                )
            ))
        }

        // At this point, we should have written those resources out to disk:
        assert(outputFile1.exists())
        assert(outputFile1.fileSize() > 0)

        val outputFile2 = createTempFile(suffix="output2.jsonl")
        outputFile2.outputStream().use { out ->
            val simulation = PlanSimulation.withoutIncon(
                jsonlReportHandler(out, jsonFormat),
                epoch,
                epoch,
                { CachedResourceModel(outputFile1, jsonFormat, this) },
            )

            // The full profile is read out of outputFile1 - there's no activities nor daemons in this version.
            simulation.runUntil(epoch + HOUR)
        }

        // At this point, we should have written the file-backed resources back to outputFile2, just for the sake of testing.
        assert(outputFile2.exists())
        assert(outputFile2.fileSize() > 0)

        // By the design of this test, every resource was cached and registered again.
        // Except for the "activities" channel, the outputs should be identical.
        assertEquals(
            outputFile1.readLines()
                .map { jsonFormat.decodeFromString<ChannelizedReport<JsonElement>>(it) }
                .filter { it.channel != "activities" },
            outputFile2.readLines()
                .map { jsonFormat.decodeFromString<ChannelizedReport<JsonElement>>(it) }
        )
    }

    @Test
    fun cached_resources_can_be_played_from_times_after_cache_file_start() {
        val jsonFormat = Json {
            serializersModule = SerializersModule {
                contextual(Instant::class, String.serializer().alias(InvertibleFunction.of(
                    Instant::parse, Instant::toString
                )))
                activities {
                    activity(ChangeA::class)
                    activity(ChangeB::class)
                }
            }
        }
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val outputFile1 = createTempFile(suffix="output1.jsonl")
        outputFile1.outputStream().use { out ->
            val simulation = PlanSimulation.withoutIncon(
                jsonlReportHandler(out, jsonFormat),
                epoch,
                epoch,
                ::OriginalResourceModel
            )

            simulation.runPlan(Plan(
                epoch,
                epoch + HOUR,
                listOf(
                    GroundedActivity(epoch + 5 * MINUTE, ChangeA(delta = 10)),
                    GroundedActivity(epoch + 15 * MINUTE, ChangeB("Second string")),
                    GroundedActivity(epoch + 20 * MINUTE, ChangeA(delta = -10)),
                    GroundedActivity(epoch + 30 * MINUTE, ChangeB("Third string")),
                )
            ))
        }

        val outputFile2 = createTempFile(suffix="output2.jsonl")
        val sim2start = epoch + 20 * MINUTE + 10 * SECOND
        outputFile2.outputStream().use { out ->
            val simulation = PlanSimulation.withoutIncon(
                jsonlReportHandler(out, jsonFormat),
                epoch,
                sim2start,
                { CachedResourceModel(outputFile1, jsonFormat, this) },
            )

            // The full profile is read out of outputFile1 - there's no activities nor daemons in this version.
            simulation.runUntil(epoch + HOUR)
        }

        // Now, since we're starting the replay at ~epoch + 20 min, the output should be identical
        // iff we strip out the activities channel, drop the reports before that time,
        // and add new initial value reports.
        assertEquals(
            listOf(
                ChannelizedReport("/resourceB", sim2start, JsonPrimitive("Second string")),
                ChannelizedReport("/resourceA", sim2start, JsonPrimitive(-10)),
            ) +
            outputFile1.readLines()
                .map { jsonFormat.decodeFromString<ChannelizedReport<JsonElement>>(it) }
                .filter { it.time > sim2start && it.channel != "activities" },
            outputFile2.readLines()
                .map { jsonFormat.decodeFromString<ChannelizedReport<JsonElement>>(it) }
        )
    }
}