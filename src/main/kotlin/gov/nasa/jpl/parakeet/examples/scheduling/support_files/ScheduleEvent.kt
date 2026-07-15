package gov.nasa.jpl.parakeet.examples.scheduling.support_files

import kotlin.time.Instant

interface ScheduleEvent {
    val start: Instant
    val end: Instant
}