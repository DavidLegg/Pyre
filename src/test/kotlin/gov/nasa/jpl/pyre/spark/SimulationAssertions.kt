package gov.nasa.jpl.pyre.spark

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.spark.ChannelizedReports.Report
import gov.nasa.jpl.pyre.string
import kotlin.collections.plusAssign
import kotlin.test.assertEquals

class ChannelizedReports {
    data class Report(val time: Duration, val data: JsonValue)

    private val channelizedReports: MutableMap<String, MutableList<Report>> = mutableMapOf()
    private val unchannelizedReports: MutableList<JsonValue> = mutableListOf()

    fun add(report: JsonValue) {
        try {
            val values = (report as JsonMap).values
            val channel = (values["channel"] as JsonString).value
            val time = Duration.serializer().deserialize(requireNotNull(values["time"]))
            val data = requireNotNull(values["data"])
            channelizedReports.getOrPut(channel, ::mutableListOf) += Report(time, data)
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException, is IllegalStateException ->
                    unchannelizedReports.add(report)
                else -> throw e
            }
        }
    }

    operator fun get(channel: String) =
        channelizedReports[channel]?.toList() ?: emptyList()

    fun misc() = unchannelizedReports.toList()
}

fun ChannelizedReports.channel(channel: String, block: ChannelAssertContext.() -> Unit) {
    ChannelAssertContext(requireNotNull(get(channel))).block()
}

class ChannelAssertContext(val channel: List<Report>) {
    private var time = Duration.ZERO
    private var n: Int = 0

    fun at(time: Duration) { this.time = time }
    fun element(block: JsonValue.() -> Unit) {
        val report = channel[n++]
        assertEquals(time, report.time)
        report.data.block()
    }
    fun atEnd() = n >= channel.size
}

// TODO: write assertions to inspect channelized outputs
