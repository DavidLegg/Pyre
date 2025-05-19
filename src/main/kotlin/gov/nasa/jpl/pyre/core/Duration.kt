package org.example.gov.nasa.jpl.pyre.core

data class Duration(val ticks: Long) : Comparable<Duration> {
    override fun compareTo(other: Duration): Int {
        return ticks.compareTo(other.ticks)
    }

    operator fun plus(other: Duration): Duration {
        return Duration(ticks + other.ticks)
    }

    operator fun minus(other: Duration): Duration {
        return Duration(ticks - other.ticks)
    }

    companion object {
        val ZERO: Duration = Duration(0)
    }
}
