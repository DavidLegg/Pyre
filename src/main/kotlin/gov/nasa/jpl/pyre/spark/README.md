# Pyre - Spark

Spark is the principal "ergonomics" layer of Pyre, building on top of ember.
It's meant to contain functionality which is essential to the real-world use of Pyre.

When deciding whether some new feature belongs in this layer, we ask:
1. Can an equivalent simulation be written without this feature, even if doing so would be painful?
    - If not, this feature changes the fundamental capability of Pyre, and belongs in ember instead.
2. Are most modelers using Pyre expected to use this feature?
    - If not, this feature is not central / essential enough to go in spark. It belongs in a higher level.

## Coroutines

By far the most important concept added in Spark is the use of coroutines to build tasks.
Coroutines (aka "suspend functions") look like regular Kotlin code, but the compiler automatically splits them into continuations.
Spark can reorganize these coroutines as simulation tasks to integrate them with the save/restore system built in Ember.

This capability is offered through three primary task constructors:
- `task` - a simple task, which runs once from start to finish.
- `repeatingTask` - an infinitely looping task, which automatically restarts from the beginning when it finishes.
- `coroutineTask` - the most flexible task, which indicates each time it finishes whether to complete or restart.

## Resources

Another important concept added in Spark is the resource framework.
A resource is an abstraction over cells, representing a state being tracked in the simulation.

Some resources are backed by a cell; these are "mutable" because tasks may emit an effect on these resources to change them.
Unlike cell effects, which may be any type, resource effects have exactly one type: a unary function of the dynamics.
By default, all mutable resources use the "auto" effect trait, which tests concurrent effects for commutativity.
This incurs a small performance penalty in return for completely and safely automating the construction of effect traits.
This trade has proven well worth it in practice.

Other resources are not backed directly by a single cell.
These are generally called "derived" resources, as they are usually a function of one or more other resources.
These resources are computed lazily, and store no state information of their own.
Derived resources are used extensively to reason about the state of a simulation.
In practice, we've found it's usually more performant to compute derived resources this way than to update caches
storing the value of the derived resources.

Additionally, resources use "dynamics", which package a value with its "stepping" behavior,
which describe how that value evolves continuously over time.
Resource derivations should act on the dynamics, not merely the value.
For example, if two resources use linear dynamics "v_1 + r_1 * t" and "v_2 + r_2 * t",
the sum is linear dynamics "(v_1 + v_2) + (r_1 + r_2) * t".

Several kinds of dynamics are provided, including
- Discrete - does not evolve over time; as a result, any value type may be put in discrete resources.
- Timer - uses the same time type as the simulation engine itself, to precisely represent time.
  These can be paused, reset, and run forwards or backwards to function as clocks, timers, and countdown timers. 

[//]: # (TODO: Polynomial resources? Unit awareness?)

## Conditions and Reactions

The last important concept introduced by Spark is "reactions".
Reactions are a class of patterns for building tasks which "react" to some condition to perform some (usually small) task.
Working in close conjunction with Reactions is the identification of conditions with boolean resources using `whenTrue`.
This means that all derivations built for boolean resources can be used as conditions for a task to await.
This is especially important for reactions, which often derive the condition to await on the fly.

For example, a model may react to power draw exceeding a maximum limit to trip a flight system reset, using code like this:
```kotlin
spawn("Monitor power limit", whenever(powerDraw greaterThan 100.0) {
    flightSystem.reset()
})
```
To explain
- `powerDraw` would be a discrete double resource,
- `greaterThan` derives a boolean resource,
- `whenever` uses that as a condition, to build a task which calls `flightSystem.reset()`,
- that task is `spawn`ed with then name "Monitor power limit"
