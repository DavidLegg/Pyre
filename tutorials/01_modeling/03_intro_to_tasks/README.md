# Intro to Tasks

This tutorial builds on [02_intro_to_resources](../02_intro_to_resources/README.md) by adding our first task.

Tasks define the behavior of the simulation.
They can read and write to resources, report output, and more.

We'll start with our simulator from before:
```kotlin
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
    ) {
        val counter: MutableIntResource = discreteResource("counter", 0).registered()
    }
```

Now, let's say we want to increment this counter when the simulation starts.
We could try calling `counter.increment()` directly from within the initialization block, but that won't work.
We'll get a compile error, because the initialization block has `InitScope`, but we need `TaskScope`.
Only tasks are allowed to write to resources.

To fix this, we'll spawn a task, and that task can then increment the counter:
```kotlin
    spawn("Increment Counter", task {
        counter.increment()
    })
```

We're using the `task` function because we have a simple task that doesn't need to repeat.
Later, we'll see the other kinds of tasks, and when it's appropriate to use them.

If we run this simulator and dump its output, we should see something like this:
```
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Resources:
  stdout
  stderr
  counter
    2030-01-01T00:00:00Z -> 0
    2030-01-01T00:00:00Z -> 1
```

Notice that both the counter's original value, and its value after the increment, are reported.
This is an important feature - even though these values happen at the same time, they are ordered, and both are reported.

But what if we want things to happen later? For that, we'll need to [delay](../04_delay/README.md).

You can find the full code for this tutorial in [IntroTasks.kt](./src/main/kotlin/pyre_tutorials/IntroTasks.kt).
