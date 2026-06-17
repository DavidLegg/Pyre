# Reactions

This tutorial builds on [05_await_and_conditions](../05_await_and_conditions/README.md) by introducing reactions.

Let's start with a mostly-empty model that just has our counter resources:
```kotlin
fun main() {
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
    ) {
        val counter: MutableIntResource = discreteResource("counter", 0).registered()
        val counterIsLarge: BooleanResource = (counter greaterThan 5).named { "counterIsLarge" }.registered()
        // ...
    }
}
```

A "reaction" or "reaction loop" is a task that runs every time some condition is satisfied.
They implement behaviors in the model itself which are too complex to be expressed as just a derived resource.

## Whenever

The most common of these is `whenever`, which simply awaits a condition and runs a task block in a loop.
We'll use this to write a task that drives the counter back to zero:
```kotlin
spawn("Decrement Counter", whenever(counter greaterThan 0) {
    stdout.report("Counter is ${counter.getValue()}, decrementing...")
    delay(5.minutes)
    counter.decrement()
    stdout.report("Decrement complete. Counter is ${counter.getValue()}")
})
```

Now lets add a task to increment the counter a few times:
```kotlin
spawn("Increment Counter Slowly", task {
    delay(1.hours)
    repeat(3) {
        stdout.report("Incrementing counter slowly")
        counter.increment()
        delay(10.minutes)
    }
})
```

If we run this, the `stdout` channel in our results looks like this:
```
  stdout
    2030-01-01T01:00:00Z -> Incrementing counter slowly
    2030-01-01T01:00:00Z -> Counter is 1, decrementing...
    2030-01-01T01:05:00Z -> Decrement complete. Counter is 0
    2030-01-01T01:10:00Z -> Incrementing counter slowly
    2030-01-01T01:10:00Z -> Counter is 1, decrementing...
    2030-01-01T01:15:00Z -> Decrement complete. Counter is 0
    2030-01-01T01:20:00Z -> Incrementing counter slowly
    2030-01-01T01:20:00Z -> Counter is 1, decrementing...
    2030-01-01T01:25:00Z -> Decrement complete. Counter is 0
```

As expected, every time the increment happens, the decrement reaction is triggered, resetting the counter to 0.

What happens if we increment the counter more quickly?
Let's replace our "Increment Counter Slowly" task with this:
```kotlin
spawn("Increment Counter Quickly", task {
    delay(2.hours)
    repeat(6) {
        stdout.report("Incrementing counter quickly")
        counter.increment()
        delay(10.seconds)
    }
})
```

Now, our `stdout` looks like this:
```
  stdout
    2030-01-01T02:00:00Z -> Incrementing counter quickly
    2030-01-01T02:00:00Z -> Counter is 1, decrementing...
    2030-01-01T02:00:10Z -> Incrementing counter quickly
    2030-01-01T02:00:20Z -> Incrementing counter quickly
    2030-01-01T02:00:30Z -> Incrementing counter quickly
    2030-01-01T02:00:40Z -> Incrementing counter quickly
    2030-01-01T02:00:50Z -> Incrementing counter quickly
    2030-01-01T02:05:00Z -> Decrement complete. Counter is 5
    2030-01-01T02:05:00Z -> Counter is 5, decrementing...
    2030-01-01T02:10:00Z -> Decrement complete. Counter is 4
    2030-01-01T02:10:00Z -> Counter is 4, decrementing...
    2030-01-01T02:15:00Z -> Decrement complete. Counter is 3
    2030-01-01T02:15:00Z -> Counter is 3, decrementing...
    2030-01-01T02:20:00Z -> Decrement complete. Counter is 2
    2030-01-01T02:20:00Z -> Counter is 2, decrementing...
    2030-01-01T02:25:00Z -> Decrement complete. Counter is 1
    2030-01-01T02:25:00Z -> Counter is 1, decrementing...
    2030-01-01T02:30:00Z -> Decrement complete. Counter is 0
```

Here, we see how the decrement task reacts to the first increment, and then the other increment operations happen while the decrement task is still running.
Importantly, `whenever` waits for its block to finish before repeating.
This is important for real models.
Sometimes, we react to some condition, and while we're reacting, it happens again.
There's no one-size-fits-all strategy for handling this, but we'll see a few approaches in this tutorial.
One is the approach above: the additional increment operations are naturally "queued", in some sense, for the decrement loop to eventually deal with.

---
_Aside_

Why do we have `whenever`? Why not just write a while loop?

If you open the definition of `whenever`, you'll see it reads something like this:
```kotlin
context (scope: SimulationScope)
fun whenever(condition: BooleanResource, block: suspend context (TaskScope) () -> Unit):
        suspend context (TaskScope) () -> TaskScopeResult = {
            await(condition)
            block()
            TaskScopeResult.Restart
        }
```

That is, it builds a function which awaits the condition, runs the block, and returns a special `Restart` token.
Why do this, instead of just writing a loop like this?
```kotlin
context (scope: SimulationScope)
fun whenever(condition: BooleanResource, block: suspend context (TaskScope) () -> Unit) = task {
        while (true) {
            await(condition)
            block()
        }
    }
```

For the simple simulations we've done so far, they'd be mostly equivalent.
However, when it comes time to save checkpoints (a topic we'll cover in a future tutorial), tasks need to save a "history" of what they've done.
A task with a while loop like this would grow history indefinitely, making our checkpoints bigger and bigger and
making the restore operations slower and slower as we need to process all of that history.

When a task returns that special `Restart` token, it's asking the simulator to restart the task from the beginning.
This lets us forget the history before the restart, which in turn makes checkpoints smaller and restoration more efficient.

As a modeler, you'll almost never need to handle the `Restart` token directly.
Picking the right kind of reaction not only makes your code clearer to read, it'll also make it more efficient.

---

## Once Whenever

A slight variation on `whenever`, the `onceWhenever` reaction waits for the condition to be false again before repeating.
For example, we might implement a warning that fires once whenever `counter` exceeds 3:
```kotlin
spawn("Warn about counter", onceWhenever(counter greaterThan 3) {
    stdout.report("Warning! Counter is ${counter.getValue()}!")
})
```

Running this, our output looks like:
```
  stdout
    2030-01-01T02:00:00Z -> Incrementing counter quickly
    2030-01-01T02:00:00Z -> Counter is 1, decrementing...
    2030-01-01T02:00:10Z -> Incrementing counter quickly
    2030-01-01T02:00:20Z -> Incrementing counter quickly
    2030-01-01T02:00:30Z -> Incrementing counter quickly
    2030-01-01T02:00:30Z -> Warning! Counter is 4!
    2030-01-01T02:00:40Z -> Incrementing counter quickly
    2030-01-01T02:00:50Z -> Incrementing counter quickly
    2030-01-01T02:05:00Z -> Decrement complete. Counter is 5
    2030-01-01T02:05:00Z -> Counter is 5, decrementing...
    2030-01-01T02:10:00Z -> Decrement complete. Counter is 4
    2030-01-01T02:10:00Z -> Counter is 4, decrementing...
    2030-01-01T02:15:00Z -> Decrement complete. Counter is 3
    2030-01-01T02:15:00Z -> Counter is 3, decrementing...
    2030-01-01T02:20:00Z -> Decrement complete. Counter is 2
    2030-01-01T02:20:00Z -> Counter is 2, decrementing...
    2030-01-01T02:25:00Z -> Decrement complete. Counter is 1
    2030-01-01T02:25:00Z -> Counter is 1, decrementing...
    2030-01-01T02:30:00Z -> Decrement complete. Counter is 0
```

If we were to use `whenever` instead of `onceWhenever` here, that warning goes off continuously.
In fact, because there's no delay in this loop, this stops the simulator from advancing time, resulting in a "stall protection" error:
```
Exception in thread "main" java.lang.IllegalStateException: Simulation has stalled at 2030-01-01T02:00:30Z after 101 iterations.
```

If the simulator is completely unable to advance time, it eventually gives up rather than spinning forever.
This is called "stalling", and it indicates a serious problem with your model.
When your simulator stalls, it will also dump its state to help you diagnose why it stalled.
In this case, that dump looks something like this:
```
KernelSimulator dump:
  Simulation time: 2030-01-01T02:00:30Z
  Active tasks:
    2030-01-01T02:00:30Z - Warn about counter
    2030-01-01T02:00:40Z - Increment Counter Quickly
    2030-01-01T02:05:00Z - Decrement Counter
  Waiting tasks:
    Decrement Counter
    Increment Counter Quickly
    Report resource counterIsLarge
    Report resource counter
```

We can see that "Warn about counter" is the first active task, and the only task active at the current time.
This squarely points the blame for the stall on this task.
We can also see the other tasks in the system, including two we didn't directly create, called "Report resource ...".
This is because, under the hood, when we `.registered()` our resources, that function created reaction tasks for us.
Those tasks monitor the resource we registered, and create the reports for us whenever they change, using the `wheneverChanges` reaction we'll talk about next.

For now, let's remove the task "Warn about counter" altogether.

## Whenever Changes

In some situations, rather than caring about a resource having a specific value, we care that it changes value.
In these situations, we might use the `wheneverChanges` reactions.
For example, the `.registered()` method we've used to include resources in the output uses this reaction internally.

Let's use `wheneverChanges` to implement a simple kind of alarm based on `counterIsLarge`:
```kotlin
spawn("Alarm", wheneverChanges(counterIsLarge) {
    if (counterIsLarge.getValue()) {
        stdout.report("Alarm on! Counter is large!")
    } else {
        stdout.report("Alarm off! Counter is small!")
    }
})
```

If we run this, we'll get:
```
  stdout
    2030-01-01T02:00:00Z -> Incrementing counter quickly
    2030-01-01T02:00:00Z -> Counter is 1, decrementing...
    2030-01-01T02:00:10Z -> Incrementing counter quickly
    2030-01-01T02:00:20Z -> Incrementing counter quickly
    2030-01-01T02:00:30Z -> Incrementing counter quickly
    2030-01-01T02:00:40Z -> Incrementing counter quickly
    2030-01-01T02:00:50Z -> Incrementing counter quickly
    2030-01-01T02:00:50Z -> Alarm on! Counter is large!
    2030-01-01T02:05:00Z -> Decrement complete. Counter is 5
    2030-01-01T02:05:00Z -> Counter is 5, decrementing...
    2030-01-01T02:05:00Z -> Alarm off! Counter is small!
    2030-01-01T02:10:00Z -> Decrement complete. Counter is 4
    2030-01-01T02:10:00Z -> Counter is 4, decrementing...
    2030-01-01T02:15:00Z -> Decrement complete. Counter is 3
    2030-01-01T02:15:00Z -> Counter is 3, decrementing...
    2030-01-01T02:20:00Z -> Decrement complete. Counter is 2
    2030-01-01T02:20:00Z -> Counter is 2, decrementing...
    2030-01-01T02:25:00Z -> Decrement complete. Counter is 1
    2030-01-01T02:25:00Z -> Counter is 1, decrementing...
    2030-01-01T02:30:00Z -> Decrement complete. Counter is 0
```

In this case, since our resource is boolean, we also could have written a reaction that waits for the value to be true, then false:
```kotlin
spawn("Alarm", whenever(counterIsLarge) {
    stdout.report("Alarm on! Counter is large!")
    await(!counterIsLarge)
    stdout.report("Alarm off! Counter is small!")
})
```
Indeed, this second style of reaction loop is preferred, since `wheneverChanges` uses a more complex condition,
which takes a little more time to run, and may be more brittle if the data types in your resources have poorly-behaved `equals` methods.
If you find yourself writing a `wheneverChanges` reaction that immediately checks the value of the resource that changed,
consider if you can re-write it as a `whenever` reaction instead.

## Every

Finally, the simplest reaction loop is `every`, which simply runs the block at fixed intervals.
To try it out, let's replace "Increment Counter Quickly" with a task that increments the counter every six hours:
```kotlin
spawn("Increment Counter Periodically", every(6.hours) {
    stdout.report("Incrementing counter periodically")
    counter.increment()
})
```

Running this produces:
```
  stdout
    2030-01-01T06:00:00Z -> Incrementing counter periodically
    2030-01-01T06:00:00Z -> Counter is 1, decrementing...
    2030-01-01T06:05:00Z -> Decrement complete. Counter is 0
    2030-01-01T12:00:00Z -> Incrementing counter periodically
    2030-01-01T12:00:00Z -> Counter is 1, decrementing...
    2030-01-01T12:05:00Z -> Decrement complete. Counter is 0
    2030-01-01T18:00:00Z -> Incrementing counter periodically
    2030-01-01T18:00:00Z -> Counter is 1, decrementing...
    2030-01-01T18:05:00Z -> Decrement complete. Counter is 0
```

Notice that, while the task is scheduled to happen at exactly the end time of the simulation, it didn't run.
The simulator quits as soon as it reaches the end time.
It reaches the end time exactly before it would run the tasks at that time, so it quits without running them.
We can think of the simulator as running a "half-open" interval of time, which includes the start time
and everything after the start but strictly before the end time, and excludes the end time itself.

# Next Steps

Reactions are a powerful tool for implementing behavior in your model.
So far though, we've needed to define all the behavior of our system within the model.
Every run of the simulator would be the same, and we'd have to build a new model to change that.

In the real world, we want to build one model that encodes how our system behaves in general, and then try out multiple scenarios.
We call those scenarios "plans", and the building blocks of plans are [activities](../07_plans_and_activities/README.md).