# Scheduling Requirements

Scheduling is the process of incrementally constructing a plan with the assistance of simulation.
Scheduling often involves many iterations of simulation, plan refinement, and re-simulation.
It is assumed that simulation is a time- and compute-intensive operation.
To reduce this intensity, simulations may be bounded in time while some subset of the plan is iteratively refined,
and/or parts of the simulation may be "turned off" or run with reduced fidelity.

The SchedulingSystem is a utility designed to facilitate Scheduling, via the following requirements:

## Time-anchored

The SchedulingSystem shall maintain an absolute time.

### Advancement

The SchedulingSystem shall permit an operation to advance the time forward.
The SchedulingSystem does not need to permit an operation to "roll time backward".

## Simulation-based

The SchedulingSystem shall contain a simulation.
That simulation is at the time of the SchedulingSystem.
When the SchedulingSystem's time advances, simulation time advances with it by running the simulation.

### Configurable

When constructing a SchedulingSystem, a Configuration may be given.
The configuration is passed to the model constructor.
This is used primarily to modify the fidelity of the constructed model, by turning subsystem models on or off,
or changing the fidelity of modeling within subsystems.

## Plan-aware

The SchedulingSystem shall maintain a list of activities called the "nominal plan".
Activities starting at or after the SchedulingSystem's time may be added or removed freely.
Activities starting before the SchedulingSystem's time may not be changed in any way.

When the SchedulingSystem's time advances, activities starting in the interval between the old and new time are
injected into the simulation and executed when the simulation executes.

### Plan collection

The SchedulingSystem shall permit a "reportPlan" operation, which returns the nominal plan.

## Results

The SchedulingSystem shall maintain and expose two kinds of results:
- Timelines for all resources reported by the simulation, from the start of this SchedulingSystem's existence to its current time.
- Spans (ActivityEvents) for all activities simulated by this SchedulingSystem.

These results taken together are called a "result set".

### Restriction

A result set shall permit a "restrict" operation, accepting a time range.
The result of a restrict operation is
- a set of timelines covering only the given time range, which agree with the original resource timelines, and
- a subset of the original activity spans, including only those spans which start within the given time range

### Composition

Result sets shall permit a "compose" binary operation.
The result of a compose operation is a new result set, comprising
- resource timelines which agree with the second operand wherever possible, and with the first operand otherwise.
  - The second operand is intended to have fewer resources, and/or to cover a different or shorter time window, as the first operand.
- activity spans agreeing with the second operand for all spans starting in the time range covered by the second operand,
  and agreeing with the first operand everywhere else.

[//]: # (TODO: Reconsider how to compose activity spans. Would a straight union be better? Or maybe a union collapsing activities by reference equality?)

#### Motivation

When doing scheduling, it may be helpful to have a "background" simulation, reflecting a nominal or "baseline" plan.
Incremental updates may be performed on this plan, refining earlier results.
Scheduling may want to view these two sets of results together, as a low-compute-intensity estimate of what the full results
of a simulation with the incremental plan changes could look like.

## Copying

The SchedulingSystem shall permit a copy operation, which constructs a logically identical but separate SchedulingSystem.
The copy maintains all resource timelines, all activity spans, the nominal plan, and an identical but separate simulation.
To do this,
- A shallow copy is made of the nominal plan. Activity objects are not copied, preserving reference equality.
- A fincon is collected from the source SchedulingSystem's simulation.
  This is used as an incon in the copy SchedulingSystem's simulation, to set up the copy's simulation identically to the source's.
- Resource timelines are either shallow-copied or referenced by something like an immutable slab-list data structure.
  This way, the copy's resource timelines share a prefix with the source's.

### Configurability

The copy operation shall permit a new Configuration to be given.
This new Configuration shall be used when constructing the copy's simulation.
There is no guarantee of compatibility between a changed Configuration and the fincon provided by a differently-configured simulation.
Re-configuration like this is performed on a best-effort basis.


# Scheduling ConOps

Notionally, the SchedulingSystem is used to form a tree of partially-updated partially-executed sytems.
Each time a decision is made which may need to be "rolled back", the system is copied.

For example, we often want add activities in "layers".
High-priority or tightly-constrained activities are added first, throughout the plan.
Lower-priority and more-flexible activities can be added later, irrespective of time order, on the assumption
(which may or may not be explicitly verified by the scheduler) that they don't perturb earlier layers.

- Construct an initial system $S_0$, with an empty plan, at the start of the full planning window.
  $S_0$ is configured to run with full fidelity.
- Copy $S_0$ to $S_0'$, and advance $S_0'$ to the end of the planning window.
  The result set of $S_0'$ now represents the baseline dynamics of the environment, without activities.
- For each layer $k \geq 1$ of activities,
  - Copy $S_0$ to $S_k$. Re-configure during this copy to turn off models that aren't needed in this layer.
  - Add all activities from $S_{k-1}$ to $S_k$, copying the final plan from the last layer into this layer.
  - Consider resources by composing the results of $S_k, \dots, S_1, S_0'$ in that order.
    This forms a best-effort estimate of the resources, assuming each layer changed only the subsystems it modeled.
  - For each activity to schedule in this layer,
    - Advance $S_k$ to the earliest possible start time for the activity.
    - If backtracking may be necessary, then
      - Copy $S_k$,
      - Place the activity in that copy,
      - Run the copy until the activity completes,
      - And repeat these steps, refining the placement as needed.
    - Once a final placement for activity $i$ is determined, add it to $S_k$
  - Once all activities in layer $k$ are added to $S_k$, advance $S_k$ to the end of the planning window.
- Once all layers of activity scheduling are complete, make a final copy $S_\omega$ of $S_0$,
  configured for a full-fidelity simulation.
- Add all scheduled activities to $S_\omega$.
- Advance $S_\omega$ to the end of the planning window to get a final result set.
- Do any final constraint checking or analysis on the results of $S_\omega$.
  Note that because $S_\omega$ is configured for full fidelity, we should _not_ compose it with other results.

Alternatively, one might consider the case of using automated general-purpose planners to schedule activities.
In this case, the timing of the next activity to add is not known in advance.
To save computation, we could instead maintain a set of "checkpoints" - SchedulingSystems saved at regular times throughout the planning window
by copying the "active" system and keeping that copy in reserve, while advancing the active system.
When the planner requests resimulation of an activity, we can choose the most recent checkpoint to resume simulation from,
invalidating all checkpoints after it and rebuilding them as we can.

Finally, the layered approach and the automated planner could be combined.
Start with the layered approach.
Then, instead of a hand-written procedure to add activities in that layer, use the automated planner approach,
maintaining a set of checkpoints to speed up planner-requested re-simulation.
The simulation for that layer can still be configured to run only a relevant subset of modeling,
and the resources given to the planner can be the composition of each layer's resources.
This semi-automated approach combines high performance partial simulation with automated planning flexibility,
using a limited amount of domain expertise just to define the layers.


# Disabling a Subsystem Model

One of the key performance optimizations made during scheduling is the ability to "disable" a subsystem model.
This means having that model's resources replayed from a prior sim, irrespective of what's happening in this sim.
The idea is that a prior simulation's resource timelines were "good enough", and reading from them instead of recomputing them saves time.

It would be nice to not have to build this idea over and over, and to not have to write a separate "disabled" version of each model.
Instead, I'd like a system that "wraps" an existing resource (resource constructor?) with a function that checks whether
it's enabled, and if not, replaces it with a replay of that resource in a SimulationResults object.

Alternatively, we can explore just hand-writing "stubs" which fill in the inputs of a model using replay resources.
These could be used to spin up just one subsystem at a time, instead of "disabling" models in a full simulation.
Once again, this is a "good enough" approach, given a modeler who understands the structure of the model and of the problem enough to decide which models need to run when.
