# Delay

This tutorial builds on [03_intro_to_tasks](../03_intro_to_tasks/README.md) by adding some simple delays.

There are two basic kinds, `delay` and `delayUntil`.
`delay` waits a fixed duration, while `delayUntil` waits until a certain absolute time.

We can add lines like these to some tasks which increment our counter afterwards:
```kotlin
    delay(1.hours)
    delayUntil(start + 3.hours)
```

And when we run this simulator, we see output like this:
```
--- SimulationResults ---
Start: 2030-01-01T00:00:00Z
End:   2030-01-02T00:00:00Z
Resources:
  stdout
  stderr
  counter
    2030-01-01T00:00:00Z -> 0
    2030-01-01T01:00:00Z -> 1
    2030-01-01T03:00:00Z -> 10
  counterIsLarge
    2030-01-01T00:00:00Z -> false
    2030-01-01T03:00:00Z -> true
```

Finally, some new times in the output!

We can also see in this example that when we set `counter` to 10, `counterIsLarge` is automatically updated to be true.

What if we want to wait, not for a fixed time or until a fixed time, but until some interesting event happens?
Instead of delay, we need to [await](../05_await_and_conditions/README.md).

You can find the full code for this tutorial in [Delay.kt](./src/main/kotlin/parakeet_tutorials/Delay.kt).
