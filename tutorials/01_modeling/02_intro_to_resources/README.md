# Intro to Resources

This tutorial builds on [01_hello_world](../01_hello_world/README.md) by adding our first "real" simulation element, resources.

Resources hold the "state" of the simulation, defining the state of the world and the system you are modeling.

In this example, let's start with an empty simulator like so:
```kotlin
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
    ) {
        // ...
    }
```

Now, let's construct one of the simplest resources available, an integer counter, in the initialization block:
```kotlin
val counter = discreteResource("counter", 0)
```

And finally, let's run this tutorial using the following command from the repository root:
```bash
./gradlew :tutorials:01_modeling:02_intro_to_resources:run
```

When we do, we'll get output like this:
```
Building simulator...
Running simulator...
Reading results...
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Resources:
  stdout
  stderr
```

Not particularly interesting. Even though we've created `counter`, we didn't ask the simulator to report on it.
This is intentional, as we sometimes want to create a resource to keep track of something within the simulation,
without reporting every value it has to the output.
To get this resource into the output, we need to register it.
We'll modify the line building `counter` to read:
```kotlin
val counter = discreteResource("counter", 0).registered()
```

Now when we re-run, we'll get something like this:
```
Building simulator...
Running simulator...
Reading results...
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Resources:
  stdout
  stderr
  counter
    2030-01-01T00:00:00Z -> 0
```

Now we can see that `counter` gets its own section, and we can see the default value we gave it being reported at simulation start.
`stdout`, `stderr`, and `counter` in this view are all output channels.
The output from a simulation is divided into multiple channels, where each channel is a time-ordered stream of output reports.
Most channels are created by registering a resource, but we can also directly create a channel.
We'll cover why you might create a channel directly in another tutorial.
For now, we want to make things happen, and for that we'll need [tasks](../03_intro_to_tasks/README.md).

You can find the full code for this tutorial in [IntroResources.kt](./src/main/kotlin/pyre_tutorials/IntroResources.kt).
