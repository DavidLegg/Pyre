# Hello World

In this tutorial, we'll set up the absolute simplest simulation that we can.

First, we'll import the `Simulator` class, and start building an instance of the simulator.

```kotlin
val simulator = Simulator(...)
```

The simulator is what runs a simulation. In order to build it, we need to provide a few key parameters:

- `reportHandler`: Defines how to handle output from the simulation. All simulation output goes to this function.
- `startTime`: The start time of the simulation. This can be omitted if using `incon` instead.
- `incon`: An optional checkpoint used to restart a simulation we ran and saved before. We'll cover this in later tutorials.
- `constructModel`: Defines the "initialization" phase of the simulation, and returns the model. We'll talk more about what the model is in later tutorials.

Parakeet allows us to configure a report handler for each simulator we construct.
We might configure some simulations to write to disk, while others write to stdout, or keep their results in memory.
We can change the output format, filter, split, or augment the output, and more by changing this function.

For now, we'll stick to a simple strategy of collecting the output in memory.
To do this, we'll construct a `MutableSimulationResults` like so:
```kotlin
val start = Instant.parse("2030-01-01T00:00:00Z")
val end = start + 1.days
val results = MutableSimulationResults(start, end)
```

Then we'll ask `results` to generate a report handler for us, and give it to the simulator.
Since we defined the start time for the results already, we'll pass that too:
```kotlin
val simulator = Simulator(reportHandler = results.reportHandler(), startTime = start)
```

We'll see in later tutorials that we commonly use a class constructor for the `constructModel` parameter.
For this tutorial though, we'll use a simple lambda that returns `Unit`, the Kotlin type for an "empty" return type.
```kotlin
val simulator = Simulator(reportHandler = results.reportHandler(), startTime = start) {
    // Implicitly returns Unit
}
```

In order to see _something_ show up in the results, though, we'll write a message to the simulation stdout:
```kotlin
val simulator = Simulator(reportHandler = results.reportHandler(), startTime = start) {
    stdout.report("Hello, world!")
}
```

Note that the simulation stdout is different from the program's stdout.
The simulation stdout is part of the simulation results, so it'll be part of the `results` object we defined earlier.
In later tutorials, when we start using `incon` and eventually incremental simulation, we'll see that those features
might re-run modeling code and need to suppress the output of some modeling code.
If we wrote directly to the program's stdout, the simulator wouldn't be able to suppress that output when needed.
For this and other reasons, it's important that the simulation write to the simulation stdout and stderr, not the program's stdout and stderr.

Finally, we need to run the simulation by providing an end time to run until:
```kotlin
simulator.runUntil(end)
```

And then dump the results to screen:
```kotlin
results.dump()
```

We can run this tutorial using the following command, from the repository root:
```bash
./gradlew :tutorials:01_intro_to_modeling:01_hello_world:run
```

And it should print something like this:
```
Building simulator...
Running simulator...
Reading results...
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Resources:
  stdout
    2030-01-01T00:00:00Z -> Hello, world!
  stderr
```

Now that we have a simulator, we need to put something in it.
That something is a [resource](../02_intro_to_resources/README.md).

You can find the full code in [HelloWorld.kt](src/main/kotlin/parakeet_tutorials/HelloWorld.kt)
