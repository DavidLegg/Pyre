# Spawn

This tutorial will (re-)introduce the spawn action, but we'll write the code for it from scratch.

So far, we've seen two ways of creating tasks: spawning a daemon task from the model, and adding activities to a plan.
In this lesson, we'll introduce the third and final way of creating a task, spawning it from another task.

To motivate this lesson, let's suppose we want to model sending messages from the spacecraft to the ground.
Since our spacecraft is a considerable distance from Earth, the delay between sending and receiving the message is dominated by one-way light time.

For the sake of keeping this demo simple, we'll model downlink by just writing a message to `stdout` when each message is sent and received.
We could start out by writing a model like this:
```kotlin
class CommSystem(initScope: InitScope) {
    val oneWayLightTime: MutableDiscreteResource<Duration>

    init {
        context(initScope) {
            oneWayLightTime = discreteResource("oneWayLightTime (s)", 30.minutes)
        }
    }
    
    // Since downlink needs to "do task things" like delay and write to stdout,
    // it requires a TaskScope in its context and the "suspend" keyword.
    context (_: TaskScope)
    suspend fun downlink(message: String) {
        // Note that the S/C is sending a message, wait OWLT, then note that the ground receives the message.
        stdout.report("S/C sends '$message'")
        delay(oneWayLightTime.getValue())
        stdout.report("Ground receives '$message'")
    }
}
```

There's a flaw in this model, though: the `downlink` function blocks the caller while the message traveling to Earth.
To fix this, we need to spawn a sub-task to do the work while the caller continues immediately:
```kotlin
context (_: TaskScope)
suspend fun downlink(message: String) {
    spawn("Downlink", task {
        stdout.report("S/C sends '$message'")
        delay(oneWayLightTime.getValue())
        stdout.report("Ground receives '$message'")
    })
}
```

This is the same syntax as spawning a task from `InitScope`, and it works in basically the same way.

To see this in action, let's create an activity to send a few files in a row:
```kotlin
data class DownlinkFiles(
    val fileType: String,
    val numberOfFiles: Int,
    val delayBetweenFiles: Duration,
) : Activity<CommSystem> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: CommSystem) {
        for (i in 1..numberOfFiles) {
            // Here we're asking the model to handle the downlink.
            // Since this will happen in a spawned sub-task, it won't block this task.
            model.downlink("$fileType file $i")
            delay(delayBetweenFiles)
        }
    }
}
```

And finally wire up the simulator and a plan with this activity:
```kotlin
fun main() {
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
        constructModel = ::CommSystem
    )

    val plan = Plan(
        start,
        end,
        listOf(
            GroundedActivity(start + 1.hours, DownlinkFiles("text", 5, 2.seconds)),
        )
    )
    simulator.runPlan(plan)
    results.dump()
}
```

Running this, we see some output like
```
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Activities:
  Start 1/DownlinkFiles at 2030-01-01T01:00:00Z
  End 1/DownlinkFiles at 2030-01-01T01:00:10Z
Resources:
  stdout
    2030-01-01T01:00:00Z -> S/C sends 'text file 1'
    2030-01-01T01:00:02Z -> S/C sends 'text file 2'
    2030-01-01T01:00:04Z -> S/C sends 'text file 3'
    2030-01-01T01:00:06Z -> S/C sends 'text file 4'
    2030-01-01T01:00:08Z -> S/C sends 'text file 5'
    2030-01-01T01:30:00Z -> Ground receives 'text file 1'
    2030-01-01T01:30:02Z -> Ground receives 'text file 2'
    2030-01-01T01:30:04Z -> Ground receives 'text file 3'
    2030-01-01T01:30:06Z -> Ground receives 'text file 4'
    2030-01-01T01:30:08Z -> Ground receives 'text file 5'
  stderr
```

Notice here that all the "send" messages come out before all the "receive" messages.
In fact, the `DownlinkFiles` activity ends completely before any of the messages it sent reach Earth.
This is evidence that our sub-tasks for sending the messages may outlive the activity that spawned them.

It's not just activities that can spawn sub-tasks, though.
Let's add a daemon to periodically send a keep-alive message.
In the `context(initScope)` block in `CommSystem`, we add:
```kotlin
spawn("Send keep-alive", every(6.hours) {
    downlink("keep-alive")
})
```

Re-running, we get:
```
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Activities:
  Start 1/DownlinkFiles at 2030-01-01T01:00:00Z
  End 1/DownlinkFiles at 2030-01-01T01:00:10Z
Resources:
  stdout
    2030-01-01T01:00:00Z -> S/C sends 'text file 1'
    2030-01-01T01:00:02Z -> S/C sends 'text file 2'
    2030-01-01T01:00:04Z -> S/C sends 'text file 3'
    2030-01-01T01:00:06Z -> S/C sends 'text file 4'
    2030-01-01T01:00:08Z -> S/C sends 'text file 5'
    2030-01-01T01:30:00Z -> Ground receives 'text file 1'
    2030-01-01T01:30:02Z -> Ground receives 'text file 2'
    2030-01-01T01:30:04Z -> Ground receives 'text file 3'
    2030-01-01T01:30:06Z -> Ground receives 'text file 4'
    2030-01-01T01:30:08Z -> Ground receives 'text file 5'
    2030-01-01T06:00:00Z -> S/C sends 'keep-alive'
    2030-01-01T06:30:00Z -> Ground receives 'keep-alive'
    2030-01-01T12:00:00Z -> S/C sends 'keep-alive'
    2030-01-01T12:30:00Z -> Ground receives 'keep-alive'
    2030-01-01T18:00:00Z -> S/C sends 'keep-alive'
    2030-01-01T18:30:00Z -> Ground receives 'keep-alive'
  stderr
```

Notice that we could re-use `downlink`, because both activities and daemons are tasks, with identical capability.
The called code cannot, in general, distinguish between being called from a daemon task or an activity.

So far, we've been spawning a general task, but we can also spawn activities.
To see this, let's add a second activity which spawns several `DownlinkFiles` activities:
```kotlin
data class DownlinkFileGroups(
    val fileTypes: List<String>,
    val delayBetweenGroups: Duration,
    val numberOfFiles: Int,
    val delayBetweenFiles: Duration,
) : Activity<CommSystem> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: CommSystem) {
        for (fileType in fileTypes) {
            // To spawn an activity, we have to pass the model as well as the activity to spawn.
            spawn(DownlinkFiles(fileType, numberOfFiles, delayBetweenFiles), model)
            delay(delayBetweenGroups)
        }
    }
}
```

Since this activity is using `spawn` for each child activity, the parent `DownlinkFileGroups` activity continues without waiting for each child.
As a consequence, if we change our plan to read:
```kotlin
    val plan = Plan(
        start,
        end,
        listOf(
            GroundedActivity(start + 2.hours, DownlinkFileGroups(listOf("image", "science data", "engineering data"), 1.seconds, 5, 3.seconds)),
        )
    )
```
then our results look something like:
```
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Activities:
  Start 1/DownlinkFileGroups at 2030-01-01T02:00:00Z
  Start DownlinkFiles at 2030-01-01T02:00:00Z
  Start DownlinkFiles at 2030-01-01T02:00:01Z
  Start DownlinkFiles at 2030-01-01T02:00:02Z
  End 1/DownlinkFileGroups at 2030-01-01T02:00:03Z
  End DownlinkFiles at 2030-01-01T02:00:15Z
  End DownlinkFiles at 2030-01-01T02:00:16Z
  End DownlinkFiles at 2030-01-01T02:00:17Z
Resources:
  stdout
    2030-01-01T02:00:00Z -> S/C sends 'image file 1'
    2030-01-01T02:00:01Z -> S/C sends 'science data file 1'
    2030-01-01T02:00:02Z -> S/C sends 'engineering data file 1'
    2030-01-01T02:00:03Z -> S/C sends 'image file 2'
    2030-01-01T02:00:04Z -> S/C sends 'science data file 2'
    2030-01-01T02:00:05Z -> S/C sends 'engineering data file 2'
    2030-01-01T02:00:06Z -> S/C sends 'image file 3'
    2030-01-01T02:00:07Z -> S/C sends 'science data file 3'
    2030-01-01T02:00:08Z -> S/C sends 'engineering data file 3'
    2030-01-01T02:00:09Z -> S/C sends 'image file 4'
    2030-01-01T02:00:10Z -> S/C sends 'science data file 4'
    2030-01-01T02:00:11Z -> S/C sends 'engineering data file 4'
    2030-01-01T02:00:12Z -> S/C sends 'image file 5'
    2030-01-01T02:00:13Z -> S/C sends 'science data file 5'
    2030-01-01T02:00:14Z -> S/C sends 'engineering data file 5'
    2030-01-01T02:30:00Z -> Ground receives 'image file 1'
    2030-01-01T02:30:01Z -> Ground receives 'science data file 1'
    2030-01-01T02:30:02Z -> Ground receives 'engineering data file 1'
    2030-01-01T02:30:03Z -> Ground receives 'image file 2'
    2030-01-01T02:30:04Z -> Ground receives 'science data file 2'
    2030-01-01T02:30:05Z -> Ground receives 'engineering data file 2'
    2030-01-01T02:30:06Z -> Ground receives 'image file 3'
    2030-01-01T02:30:07Z -> Ground receives 'science data file 3'
    2030-01-01T02:30:08Z -> Ground receives 'engineering data file 3'
    2030-01-01T02:30:09Z -> Ground receives 'image file 4'
    2030-01-01T02:30:10Z -> Ground receives 'science data file 4'
    2030-01-01T02:30:11Z -> Ground receives 'engineering data file 4'
    2030-01-01T02:30:12Z -> Ground receives 'image file 5'
    2030-01-01T02:30:13Z -> Ground receives 'science data file 5'
    2030-01-01T02:30:14Z -> Ground receives 'engineering data file 5'
    2030-01-01T06:00:00Z -> S/C sends 'keep-alive'
    2030-01-01T06:30:00Z -> Ground receives 'keep-alive'
    2030-01-01T12:00:00Z -> S/C sends 'keep-alive'
    2030-01-01T12:30:00Z -> Ground receives 'keep-alive'
    2030-01-01T18:00:00Z -> S/C sends 'keep-alive'
    2030-01-01T18:30:00Z -> Ground receives 'keep-alive'
  stderr
```

We can make a few important observations about this output:
1. Each spawned `DownlinkFiles` activity has start and end events in the activities section.
2. The execution of all the child activities are interlaced because the parent spawns them without waiting for each to complete.

Note that there are other ways to create sub-activities, listed in [ActivityActions](../../../src/main/kotlin/gov/nasa/jpl/pyre/foundation/plans/ActivityActions.kt),
including `call` to run an activity synchronously and `defer`/`deferUntil` to spawn the activity in the future.

Finally, note that a daemon can spawn activities, though this is unusual.
See the full code for this tutorial at [Spawn.kt](./src/main/kotlin/pyre_tutorials/Spawn.kt) for an example of this.
