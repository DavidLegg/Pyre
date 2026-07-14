# Testing and Debugging Incremental Simulation

## Testing Strategy

The file [IncrementalSimulatorTest](./IncrementalSimulatorTest.kt) is the primary way we test incremental simulation.
Although there are separate
[kernel-](/main/kotlin/gov/nasa/jpl/pyre/kernel/incremental/KernelIncrementalSimulator.kt) and
[foundation-level](/main/kotlin/gov/nasa/jpl/pyre/foundation/incremental/IncrementalSimulatorImpl.kt)
incremental simulators, they are not tested independently.
It is hard to test the kernel-level incremental simulator directly,
and that is not particularly valuable outside of the way it powers the foundation-level incremental simulator.

All tests use an "oracle" testing strategy.
The [NonIncrementalSimulator](./NonIncrementalSimulator.kt) implements the `IncrementalSimulator` interface,
but does so by brute-forcing the problem with the single-shot simulator.
This violates the performance guarantees expected of incremental simulation, but generates the correct answer.
As such, [NonIncrementalSimulator](./NonIncrementalSimulator.kt) is the "oracle" which produces correct answers for any input,
and all tests simply compare the real incremental simulator against it.
As a result, no test directly specifies what the correct answer is, they only specify the operations to perform.

There are several hand-written unit tests.
These capture the initial easy cases and a few cases I knew would stress the incremental simulator.
There are also some "frozen" fuzz-tests - edge cases initially discovered by fuzzing, which exposed some bug.
After simplifying the resulting test, it was saved to reproduce that edge case reliably even if the fuzzing strategy changes.
Going forward, there is functionally no difference between these kinds of tests.

The bulk of the testing is done by fuzz testing.
Tests with names like `random plan edits...` are fuzz tests, which randomly generate plans and edit operations.
Since we have an oracle, any plan and any set of edits on that plan form a valid test case.

We have two models for fuzz testing, referred to simply as "model 1" aka. `TestModel` and "model 2" aka. `BlockTestModel`.
- Model 1 was written first, and is generally simpler. It features a small number of fixed activities to choose between.
- Model 2 uses "block" activities, with statements and expressions that can themselves be randomized.
  This allows for far more complicated activity behavior, exploring rarer edge cases.

These tests wind up running exceptionally complicated test procedures, requiring specialized automation to debug them.
The `simplifyTranscriptOnFailure` flag enables this automation.
When enabled, a fuzz test records all the operations it performs in a "transcript".
If the test fails, it then works to simplify that transcript, running it repeatedly with small modifications,
keeping modifications which preserve the test failure.
Once simplification is complete, the transcript is printed as Kotlin code to a file.
That code can be copied back into the unit test file to reproduce the failure, and manual debugging begins from there.

NOTE: Simplifying a transcript requires, in general, hundreds of executions of the test.
Only enable `simplifyTranscriptOnFailure` for a single test, and only run one test with it enabled.
It is a static variable, so enabling it in any test will enable it in all future tests of that run.
Never commit a test which enables `simplifyTranscriptOnFailure`.

## Debugging a Failing Test

### Reproducing a Fuzz Test Failure

We'll start by assuming that a fuzz test is failing.
If a hand-written test is failing instead, skip to the next section.

Every fuzz test has a "seed" - the integer listed along with the test name. Note this seed, and write a single test
```kotlin
@Test
fun `repro by seed`() {
    simplifyTranscriptOnFailure = true
    <name of fuzz test>(<seed>)
}
```
For example, if `random plan edits conform to fundamental incremental sim guarantee -- model 2` is the failing test,
and 14 is the failing seed, then we'd write
```kotlin
@Test
fun `repro by seed`() {
    simplifyTranscriptOnFailure = true
    `random plan edits conform to fundamental incremental sim guarantee -- model 2`(14)
}
```

Run `repro by seed`, wait for simplification to complete, and read the last few lines of the log to find where the replication code was written.
Copy that code back into `IncrementalSimulatorTest`. Usually, that test is called `repro directly`; we'll use that name to refer to it going forward.
Run `repro directly` and verify that it fails. Note that it may produce a different error from `repro by seed`. This is fine.

### Writing the Kernel Graph

Edit the failing test to enable debug output from `KernelIncrementalSimulator`:
```kotlin
@Test
fun `repro directly`() {
    KernelIncrementalSimulator.DEBUG = DebugLevel.MAJOR
    // ... intermediate steps setting up the test ...
    println("debugMajorStep = ${KernelIncrementalSimulator.debugMajorStep}")
    // last step, which triggers the test failure
}
```
You'll likely need to change the visibility and mutability of these members of `KernelIncrementalSimulator`.
Revert these changes before merging your final code, so the compiler can optimize away all debug code paths in production.

Now when you run the test, it should write some `.dot` files to [inc-sim-debug](/inc-sim-debug).
Run [convert](/inc-sim-debug/convert) to convert all `.dot` files to `.svg` files.
Run [clean](/inc-sim-debug/clean) to remove all `.dot` and `.svg` files before you re-run the test.

The log from `repro directly` will indicate the "major debug step" just before the last operation.
Major debug step numbers are encoded in the names of the `.svg` files. Open the one with the matching number.
Most web browsers handle SVG files well.

Every SVG file has a link to the next one. Clicking anywhere on the file will take you to the next one.
Use this and your browsers back button to page through the SVG files.

These files encode the "kernel graph" - this is a representation of nearly all the information in the `KernelIncrementalSimulator`.
The exact meaning of every node and edge is too hard to explain here - refer to the source code for that.

### Finding the Bug

In general, I like to start by stepping through the graphs somewhat quickly, looking for any edges that seem "out of place".
When I find one, I step back through the graphs until I find the first one that has any wrong information in it.
This is how I roughly locate when the bug occured.

From there, I can use the major debug step to pause a debugger in the `KernelIncrementalSimulator` just before the failure.
I'll step the debugger forward carefully and try to catch the moment the graph is "broken".

After that, general debugging practices apply.
Keep asking "Why" until you reach the root cause of the failure, and implement a fix.
Sometimes, you won't find a root cause, but you might get some indication of what aspects of the test are relevant.
You might be able to use those indications to further simplify the test.
I've generally found that tests with ~200 or fewer debug major steps, total, are feasible to debug directly.
Longer / more complex tests can often be simplified.

### Preventing Regressions

Once the bug has been fixed, re-run `repro directly` or whatever hand-written test failed to verify the fix.
Also re-run `repro by seed`, if a fuzz test was failing, to verify that all the bugs in that test have been fixed.
(Where there's one bug, there may be more!)

From there, remove `repro by seed`, and rename `repro directly` to something more meaningful.
Convoluted tests produced by fuzzing like this also benefit from a comment, detailing the observed failure, its cause, and the fix.

Make sure any edits to `KernelIncrementalSimulator`'s debugging fields are reverted, so we aren't writing debug graphs in production.

Re-run the entire test suite (making sure `simplifyTranscriptOnFailure` isn't enabled anywhere!), and verify all bugs have been fixed.
