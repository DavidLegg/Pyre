# Scheduling

Scheduling is the process of building up an activity plan, usually by simulating partial or hypothetical plans
and refining activity parameters and placements in response to the simulated behavior.

Scheduling in Pyre is accomplished primarily through the [SchedulingSystem](./SchedulingSystem.kt) class,
with more advanced functionality provided by [SchedulingAlgorithms](./SchedulingAlgorithms.kt).

See [the scheduling example](../../examples/scheduling/Main.kt) for a working example of how to do scheduling.

## "Scheduling" vs. "Planning"

Scheduling as defined here is very similar to [planning](https://en.wikipedia.org/wiki/Automated_planning_and_scheduling),
though "planning" usually suggests more autonomy.
A planner is generally given a "goal state" for the model, and allowed to choose any activities to reach that goal.
Planners are also generally given very little guidance on which activities to place.

By contrast, scheduling generally focuses on rules about placing activities, rather than a final state for the model.
A scheduler is generally given strict and detailed guidance on which activities to place.

For example, suppose we want to place "TelecomPass" activities.
A planner may be given broad state-based goals like
"never let the time between passes exceed 72 hours" and "downlink all collected science data".
By contrast, a scheduler might be given narrow rules, like
"schedule these mandatory passes: ..." and "whenever data volume exceeds 70%, schedule an additional optional pass".

In this way, planners can be thought of as more "declarative", and schedulers more "procedural",
though the distinction between planning and scheduling is blurry.
In practice, most missions choose some mix of the two to balance autonomy, performance, and predictability.
