# Pyre - Kernel

Kernel is the lowest runnable layer of Pyre.
This layer intends to define the bare minimum of types and logic to run a simulation.
This layer intends to be ergonomic to run, but not necessarily to write.

Additions intending to make these types more ergonomic to write should go in higher layers.

Modifications to this layer are highly discouraged, because of the broad ramifications of such a change.
Whenever possible, new functionality should build on top of, rather than supplant, functionality in kernel.

## Defining a Simulation

Fundamentally, a simulation comprises:
- State information, stored in cells,
- Tasks, which act on those cells,
- Conditions, which monitor the cells to trigger tasks,
- and Results, arbitrary JSON objects reported out of the simulation.

### Simulation Process

A simulation maintains a queue of active tasks, each tagged with the time at which they'll next be resumed,
and a set of active conditions.
To progress a simulation, we check when the next task needs to be run.
If that time is later than now, we step the simulation to that time.
Stepping the simulation requires stepping the cells, which may evolve continuously over time.
(For example, a total may evolve linearly to integrate a constant rate.)

When the next task needs to be run "now", we take all the tasks that need to run "now" as a batch.
Each task in the batch is run logically in parallel; none can observe the effects the others produce.
This makes the simulation more deterministic, by removing non-determinism caused by the ordering of logically simultaneous tasks.
Each task is run until it "yields" by returning a Delay, Await, or Complete result.
The effects from all tasks are merged together, with each cell defining how to merge its own effects.

After running a task batch, the engine determines which conditions need to be re-evaluated,
and satisfied conditions schedule their awaiting tasks to resume appropriately.

During this process, tasks may report arbitrary results.
Kernel is agnostic to the structure or meaning of these results, or how those results are further handled.
A hook in the simulation setup allows the caller to define how to handle results.
This is the only legal way to report results from the simulation.
Results reported any other way, for instance by direct file I/O, may be corrupted by save/restore cycles or repeated execution.

### Cells

Cells store the data used by a simulation.
This data can evolve continuously over time, or be changed discretely by effects.
Those effects are emitted by tasks, and effects are the only way tasks may legally affect the state of a simulation.
Every cell defines for itself how its data evolves over time, what type its effects are, and how to apply and merge those effects.
Additionally, every cell must define a serializer and a name, to facilitate saving and restoring that cell.

### Tasks

Tasks comprise arbitrary code, split into a sequence of continuations called steps.
Each step returns one of several results:
- Read - indicating a cell should be read, and the resultant value given to the next step.
- Emit - indicating an effect should be emitted against a cell.
- Report - indicating a JSON value should be reported as a result of this simulation.
- Delay - indicating the next step should be run only after a fixed Duration has elapsed.
- Await - indicating the next step should be run when a Condition is satisfied.
- Spawn - indicating a child task should be started in parallel.
- Complete - indicating this task is finished, with no steps left to run.
- Restart* - indicating this task should be restarted from the beginning.

*Restart is a special kind of task step, which gets removed during task construction.
It exists to facilitate long-running looping tasks, which can be restored with a small fraction of their overall history.

### Conditions

Conditions detect that the simulation state meets some criteria of interest to a task.
Conditions follow a similar structure to tasks, a sequence of continuations called steps.
Condition steps can return one of two results:
- Read - indicating a cell should be read, and the resultant value given to the next step.
- Complete - indicating the condition has been evaluated, and will be satisfied at the indicated time (or will never be satisfied).

### Save/Restore (Fincon/Incon)

The simulation may, at a time chosen by the caller, collect a final condition (aka fincon).
This is a hierarchical structure of JSON values captured by a FinconCollector.
These values are meant to be persisted, then given to an InconProvider to restore a later simulation to the fincon state.
Both cell values and tasks can be saved and restored this way.

Given a simulation initializer S, running S until some time T should produce identical results as:
* running S until some earlier time T',
* saving a fincon at T',
* restoring a new simulation using S and that fincon,
* running that restored simulation until T,
* and concatenating the results of these simulations.

To make this guarantee, all mutable simulation state should be in a cell, and all tasks must be deterministic.
Compile-time constants do not need to be in a cell.
Mutable state outside of a cell cannot be managed by Pyre, and may cause the save/restore contract above to fail
in hard-to-predict and hard-to-debug ways.
Similarly, task nondeterminism, for example by accessing the system RNG or reading a file, can cause the save/restore
system to fail in similarly mysterious ways.

Tasks should only report results using the Report task step result.
This is the only way for Pyre to manage task outputs, including ignoring redundant outputs during a task restore.

Cells are saved and restored in the obvious way, by serializing the value directly.
Tasks are saved indirectly, by saving a "history" for the task.
A task's history is a record of the steps that task has taken, with enough information to "replay" the task along that history.
Most importantly, this includes saving the serialized values read from cells.
To restore a task, the root task step is constructed by the simulation initializer, and then the history is used to run
the root task up to the point it was saved.
Cell reads are "faked" using the history instead of the real cell, to ensure the task takes exactly the same execution path.

Child tasks are handled identically, saving a full history including a history for their parent until the spawn step.
Additionally, child tasks save information to the parent task indicating which children need to be restored.
To restore a child, the parent task replays using the child's history, and at some point switches to the "child" branch
of a spawn step, then continues replaying the child task.
This extends to grandchildren, great-grandchildren, etc.; each simply saves a history from the start of the root task
to its current state, through all the ancestors which led up to this state.
