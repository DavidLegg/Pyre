package gov.nasa.jpl.pyre.spark

import gov.nasa.jpl.pyre.boolean
import gov.nasa.jpl.pyre.double
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.int
import gov.nasa.jpl.pyre.spark.ChannelizedReports.Report
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.test.assertEquals
import kotlin.time.Instant

class ChannelizedReports(
    private val jsonFormat: Json = Json,
) {
    data class Report(val time: Instant, val data: JsonElement)

    private val channelizedReports: MutableMap<String, MutableList<Report>> = mutableMapOf()
    private val unchannelizedReports: MutableList<JsonElement> = mutableListOf()

    fun handler(): ReportHandler = object : ReportHandler {
        override fun <T> handle(value: T, type: KType) {
            (value as? ChannelizedReport<*>)?.let {
                handleChannelized(it, requireNotNull(type.arguments[0].type))
            } ?: unchannelizedReports.add(encode(value, type))
        }
    }

    private fun <T> handleChannelized(value: ChannelizedReport<T>, type: KType) {
        channelizedReports.getOrPut(value.channel, ::mutableListOf)
            .add(Report(value.time, encode(value, type)))
    }

    private fun <T> encode(value: T, type: KType) =
        jsonFormat.encodeToJsonElement(jsonFormat.serializersModule.serializer(type), value)

    operator fun get(channel: String) =
        channelizedReports[channel]?.toList() ?: emptyList()

    fun misc() = unchannelizedReports.toList()
}

fun ChannelizedReports.channel(channel: String, block: ChannelAssertContext.() -> Unit) {
    ChannelAssertContext(requireNotNull(get(channel))).block()
}

class ChannelAssertContext(val channel: List<Report>) {
    private var epoch: Instant = Instant.DISTANT_PAST
    private var time: Instant = Instant.DISTANT_PAST
    private var n: Int = 0

    fun withEpoch(time: Instant) { this.epoch = time}
    fun at(time: Duration) = at(epoch + time.toKotlinDuration())
    fun at(time: Instant) { this.time = time }
    fun element(block: JsonElement.() -> Unit) {
        val report = channel[n++]
        assertEquals(time, report.time)
        report.data.block()
    }
    fun atEnd() = n >= channel.size
}

fun ChannelAssertContext.end() {
    assert(atEnd())
}
fun ChannelAssertContext.activityStart(name: String, typeName: String = name) {
    element {
        assertEquals(name, string("name"))
        assertEquals(typeName, string("type"))
        assertEquals("start", string("event"))
    }
}
fun ChannelAssertContext.activityEnd(name: String, typeName: String = name) {
    element {
        assertEquals(name, string("name"))
        assertEquals(typeName, string("type"))
        assertEquals("end", string("event"))
    }
}
fun ChannelAssertContext.log(message: String) {
    element { assertEquals(message, string()) }
}

fun ChannelAssertContext.value(v: String) {
    element { assertEquals(v, string()) }
}
fun ChannelAssertContext.value(v: Long) {
    element { assertEquals(v, int()) }
}
fun ChannelAssertContext.value(v: Double) {
    element { assertEquals(v, double()) }
}
fun ChannelAssertContext.value(v: Boolean) {
    element { assertEquals(v, boolean()) }
}

// TODO: write assertions to inspect channelized outputs
