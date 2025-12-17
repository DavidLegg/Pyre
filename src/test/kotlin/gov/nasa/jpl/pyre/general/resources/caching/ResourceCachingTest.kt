package gov.nasa.jpl.pyre.general.resources.caching

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.kernel.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.Serialization.alias
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulation
import gov.nasa.jpl.pyre.foundation.plans.activities
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.general.reporting.ReportHandling.jsonlReportHandler
import gov.nasa.jpl.pyre.general.resources.caching.ResourceCaching.fileBackedResource
import gov.nasa.jpl.pyre.general.resources.caching.ResourceCachingTest.OriginalResourceModel.*
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.decrement
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableStringResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.StringResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.every
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.utilities.invoke
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
                resourceA = discreteResource("resourceA", 0).registered()
                resourceB = discreteResource("resourceB", "Initial string").registered()

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

        companion object {
            val JSON_FORMAT = Json {
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
                resourceA.registered()
                resourceB.registered()
            }
        }
    }

    @Test
    fun cached_resources_can_be_read_from_simulation_output() {
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val outputFile1 = createTempFile(suffix="output1.jsonl")
        outputFile1.outputStream().use { out ->
            val simulation = PlanSimulation(
                jsonlReportHandler(out, OriginalResourceModel.JSON_FORMAT),
                epoch,
                constructModel = ::OriginalResourceModel
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
            val simulation = PlanSimulation(
                jsonlReportHandler(out, OriginalResourceModel.JSON_FORMAT),
                epoch,
                constructModel = (::CachedResourceModel)(outputFile1, OriginalResourceModel.JSON_FORMAT)
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
            outputFile1.readReports(OriginalResourceModel.JSON_FORMAT)
                .filter { it.channel != Name("activities") },
            outputFile2.readReports(OriginalResourceModel.JSON_FORMAT)
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
            val simulation = PlanSimulation(
                jsonlReportHandler(out, jsonFormat),
                epoch,
                constructModel = ::OriginalResourceModel
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
            val simulation = PlanSimulation(
                jsonlReportHandler(out, jsonFormat),
                sim2start,
                constructModel = (::CachedResourceModel)(outputFile1, jsonFormat)
            )

            // The full profile is read out of outputFile1 - there's no activities nor daemons in this version.
            simulation.runUntil(epoch + HOUR)
        }

        // Now, since we're starting the replay at ~epoch + 20 min, the output should be identical
        // iff we strip out the activities channel, drop the reports before that time,
        // and add new initial value reports.
        val output2 = outputFile2.readReports(jsonFormat)
        // Since the initial values can be reported in any order, compare those separately, as sets
        assertEquals<Set<ChannelData<JsonElement>>>(
            setOf(
                ChannelData(Name("resourceB"), sim2start, JsonPrimitive("Second string")),
                ChannelData(Name("resourceA"), sim2start, JsonPrimitive(-10)),
                ),
            output2.take(2).toSet()
        )
        // All other reports should be deterministically ordered
        assertEquals(
            outputFile1.readReports(jsonFormat)
                .filter { it.time > sim2start && it.channel != Name("activities") },
            output2.drop(2)
        )
    }

    private fun Path.readReports(jsonFormat: Json): List<ChannelData<JsonElement>> = readLines()
        .mapNotNull {
            try {
                jsonFormat.decodeFromString<ChannelData<JsonElement>>(it)
            } catch (_: SerializationException) {
                // Ignore reports that aren't values, like metadata
                null
            }
        }
}