# Await and conditions

This tutorial builds on [04_delay](../04_delay/README.md) by introducing conditions and the "await" action.

When a task wants to wait until the world is in a certain state, it can use the `await` action.
`await` takes a condition and delays the task until that condition is satisfied.
Conceptually, a condition is a boolean function of the resources in the model.
While there are edge cases where we build conditions directly, we usually just derive a boolean resource and await that.

For example, we might add a task that awaits `counterIsLarge` becoming true, then decrements `counter` slowly back to 0:
```kotlin
spawn("Decrement Counter", task {
    // A boolean resource functionally "is" a condition, so we can wait for it to be true:
    await(counterIsLarge)
    repeat(counter.getValue()) {
        delay(5.minutes)
        counter.decrement()
    }
})
```

We can define derived resources anywhere, not just in the initialization block.
When we're using them as conditions like this, it's common to define them in place, as that's often the only place
where that particular condition is needed.

For example, let's define a task that waits for the counter to exceed 3, and then waits again for it to go back to 0:
```kotlin
spawn("Warn about the counter", task {
    await(counter greaterThan 3)
    stdout.report("Counter is ${counter.getValue()}!")
    await(counter equals 0)
    stdout.report("Counter is back to 0!")
})
```

If we run this model, we get something like this:
```
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Resources:
  stdout
    2030-01-01T01:30:00Z -> Counter is 4!
    2030-01-01T02:20:00Z -> Counter is back to 0!
  stderr
  counter
    2030-01-01T00:00:00Z -> 0
    2030-01-01T01:00:00Z -> 1
    2030-01-01T01:10:00Z -> 2
    2030-01-01T01:20:00Z -> 3
    2030-01-01T01:30:00Z -> 4
    2030-01-01T01:40:00Z -> 5
    2030-01-01T01:50:00Z -> 6
    2030-01-01T01:55:00Z -> 5
    2030-01-01T02:00:00Z -> 4
    2030-01-01T02:05:00Z -> 3
    2030-01-01T02:10:00Z -> 2
    2030-01-01T02:15:00Z -> 1
    2030-01-01T02:20:00Z -> 0
  counterIsLarge
    2030-01-01T00:00:00Z -> false
    2030-01-01T01:50:00Z -> true
    2030-01-01T01:55:00Z -> false
```

This is as we expected, but our tasks all run only once.
What if we want our tasks to react not just the first time some condition is satisfied, but _every_ time it's satisfied?
For that, we'll use repeating tasks, the most common of which are [reactions](../06_reactions/README.md).

You can find the full code for this tutorial in [Await.kt](./src/main/kotlin/pyre_tutorials/Await.kt).
