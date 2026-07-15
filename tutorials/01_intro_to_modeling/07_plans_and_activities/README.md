# Plans and Activities

For this tutorial, we're going to start from scratch, not from the end of another tutorial.

When we're building a simulation system, we often want to build a single "model" of how our system works,
and supply that model with multiple plans, describing different scenarios for that model to analyze.
As a rule of thumb, activities describe things we command the system to do, while the model describes everything else.

There's a lot of room for judgement about where the line between model and activity should be drawn.
One modeler may say that sending a command is activity code, and the on-board behavior that results is model code.
Another may argue that both are a direct consequence of the commanding, so both should be activity code.
Neither is wrong, and the resulting differences are mostly style choices.
Both modeling styles, if written well, should produce functionally similar simulation systems.

## Model

For this tutorial, let's build a very simple model of a spacecraft heater.

To start, we'll define a class for our model.
We'll add `InitScope` as the only constructor parameter, which will make it directly compatible with the simulator.
In real systems, we almost always build a top-level class like this, though later we'll show how to add additional configuration parameters to our model.

```kotlin
class Heater(initScope: InitScope) {
    // ...
}
```

Then we can define our main function to build a simulator for this model:
```kotlin
fun main() {
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
        constructModel = ::Heater
    )

    // TODO: Run simulator
    results.dump()
}
```

Going back to the `Heater` class, let's add two resources.
The first will be an enum stating what state we've commanded the heater to be.
```kotlin
class Heater(initScope: InitScope) {
    val state: MutableDiscreteResource<HeaterState>

    enum class HeaterState { OFF, ON }
}
```

We might try to initialize it similar to how we did before:
```kotlin
class Heater(initScope: InitScope) {
    // Compile error!
    val state: MutableDiscreteResource<HeaterState> = discreteResource("state", HeaterState.OFF).registered()
    enum class HeaterState { OFF, ON }
}
```
but this leads to a compile error, complaining that we don't have an `InitScope`.

Instead, we need to put the `InitScope` in context, then build our resources.
Since we'll need the `InitScope` in context for everything we initialize, we'll use an init block like this:
```kotlin
class Heater(initScope: InitScope) {
    val state: MutableDiscreteResource<HeaterState>

    init {
        context(initScope) {
            state = discreteResource("state", HeaterState.OFF).registered()
        }
    }

    enum class HeaterState { OFF, ON }
}
```

Next, let's add another resource to track the temperature of the heater.

```kotlin
class Heater(initScope: InitScope) {
    val state: MutableDiscreteResource<HeaterState>
    val temperature: MutableDoubleResource

    init {
        context(initScope) {
            state = discreteResource("state", HeaterState.OFF).registered()
            temperature = discreteResource("temperature", 70.0).registered()
        }
    }

    enum class HeaterState { OFF, ON }
}
```

To link them together, let's add tasks that react to the heater turning on or off, and update the temperature after a suitable delay:
```kotlin
class Heater(initScope: InitScope) {
    // ...
    init {
        // ...
        spawn("Warm up", onceWhenever((state.trace() equals HeaterState.ON).trace()) {
            delay(5.minutes)
            temperature.set(100.0)
        })
        spawn("Cool off", onceWhenever(state equals HeaterState.OFF) {
            delay(1.minutes)
            temperature.set(70.0)
        })
    }
}
```

---
_Aside_

There's a flaw in this model - if we turn the heater off while it's warming up, we'll still set the temperature to 100
at the end of the warmup period.
For this tutorial, I'm going to leave that, but it's a good exercise to think about how you might address this.

---


## Activities

Now that we have a model, it's time to build some activities that use it.

An activity is just a class implementing `Activity<M>`, where `M` is the model type.
Let's start with an activity to turn the heater on:
```kotlin
class TurnHeaterOn : Activity<Heater> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: Heater) {
        model.state.set(HeaterState.ON)
    }
}
```

The `Activity` interface defines a single method, `effectModel`.
This method has `TaskScope`, which should sound familiar.

[//]: # (TODO: Perhaps we should rename effectModel to just "run")

Activities, as it turns out, are also just tasks.
Up to this point, all the tasks we've defined have been "daemon" tasks.
A daemon task is just a task defined by the model itself.
The reaction loops that update temperature, for example, are daemon tasks.
All tasks are either daemons or activities.
Since both daemons and activities just use `TaskScope`, you can do exactly the same things in either one.

Now, we could define an activity to just turn the heater off, but let's make things a little more interesting.
Let's define an activity which waits a while, then turns the heater off.
For that, we need to see how an activity can accept a parameter.

```kotlin
data class TurnHeaterOff(val time: Duration) : Activity<Heater> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: Heater) {
        delay(time)
        model.state.set(HeaterState.OFF)
    }
}
```

Once again, we implement `Activity<Heater>`, but this time, we use a data class.
As a rule, activities should not modify their parameters once constructed.
Doing this jeopardizes the simulator's ability to re-run activities if needed, and makes your code more brittle and harder to debug.
For this and other reasons, I suggest using `data class` for activities.
Since `TurnHeaterOn` doesn't take any parameters, it can be a `data object` instead.

## Plans

So now we have a model, we have activities that can use that model, how do we put it all together?
This is where we build and run a plan.

Let's go back to our main function:
```kotlin
fun main() {
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
        constructModel = ::Heater
    )

    // TODO: Run plan
    results.dump()
}
```

We'll fill in that "TODO" comment like so:
```kotlin
    val plan = Plan(
        start,
        end,
        listOf(
            GroundedActivity(start + 1.hours, TurnHeaterOn),
            GroundedActivity(start + 2.hours, TurnHeaterOff(5.minutes)),
        )
    )
    simulator.runPlan(plan)
```

If we run that, we see something like this:
```
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Activities:
  Start 1/TurnHeaterOn at 2030-01-01T01:00:00Z
  End 1/TurnHeaterOn at 2030-01-01T01:00:00Z
  Start 2/TurnHeaterOff at 2030-01-01T02:00:00Z
  End 2/TurnHeaterOff at 2030-01-01T02:05:00Z
Resources:
  stdout
  stderr
  state
    2030-01-01T00:00:00Z -> OFF
    2030-01-01T01:00:00Z -> ON
    2030-01-01T02:05:00Z -> OFF
  temperature
    2030-01-01T00:00:00Z -> 70.0
    2030-01-01T01:05:00Z -> 100.0
    2030-01-01T02:06:00Z -> 70.0
```

Notice that we have a new section in the results, "Activities".
This section indicates when each activity starts and ends.
The numbers like "1/TurnHeaterOn" are automatically-assigned IDs, meant to disambiguate activities with the same name.
We didn't explicitly give our activities names, so they were named according to their type.

To give them explicit names, we just add that argument to `GroundedActivity` like so:
```kotlin
    val plan = Plan(
    start,
    end,
    listOf(
        GroundedActivity(start + 1.hours, TurnHeaterOn, "A"),
        GroundedActivity(start + 2.hours, TurnHeaterOff(5.minutes), "B"),
    )
)
```

With that change, the activities section of our output changes to:
```
Activities:
  Start 1/A (TurnHeaterOn) at 2030-01-01T01:00:00Z
  End 1/A (TurnHeaterOn) at 2030-01-01T01:00:00Z
  Start 2/B (TurnHeaterOff) at 2030-01-01T02:00:00Z
  End 2/B (TurnHeaterOff) at 2030-01-01T02:05:00Z
```

## Next Steps

We've seen two ways to introduce a task to the simulation.
The third and final way lets us branch one task off of another, called [spawning](../08_spawn/README.md).

You can find the full code for this tutorial in [Activities.kt](./src/main/kotlin/parakeet_tutorials/Activities.kt) and the files it references.
