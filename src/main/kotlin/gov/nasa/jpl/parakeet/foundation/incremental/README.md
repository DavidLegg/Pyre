# Incremental Simulation

The basic problem of incremental simulation is:
> Simulate a plan P to get results R.
> Given a plan P' equal to P with some edits E, compute the results R' equal to simulating P',
> in work proportional to the size of E and independent of the size of P.

This feature is highly desirable for automated planning.
Automated planners repeatedly make small edits, at times throughout the plan.
It's common, even expected, that in practice there's a kind of background or baseline state for most of the model,
which is returned to shortly after an edit.
This motivates incremental simulation over "checkpoint" simulation,
where the entire model is restarted at a checkpoint shortly before the edits and run through the end of the plan.

As for architecture, this feature is placed beside the standard kernel-foundation-general hierarchy instead of in it
because implementing this feature requires replacing the kernel simulation with one that keeps track of more information.
This is irreconcilable - the standard simulation is designed to require constant memory w.r.t. plan length,
enabling efficient simulation of very long plans.
A future implementation could in theory write this information to disk, but I'm deferring additional complexity like that.
