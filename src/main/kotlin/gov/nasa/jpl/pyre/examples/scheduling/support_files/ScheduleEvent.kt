package gov.nasa.jpl.pyre.examples.scheduling.support_files

import kotlin.time.Instant

interface ScheduleEvent {
    val start: Instant
    val end: Instant
}