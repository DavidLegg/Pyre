import sys
from pathlib import Path
import re

ROUND_PATTERN = re.compile(r'Running round (?P<round>\d+)')
ACTIVITY_PATTERN = r"GroundedActivity\(\s*time=(?P<time>[^,)]*)\s*,\s*name=(?P<name>[^,)]*)\s*,\s*activity=(?P<activity>.*)\s*\)"
ADD_PATTERN = re.compile(f'Add {ACTIVITY_PATTERN}')
REMOVE_PATTERN = re.compile(f'Remove {ACTIVITY_PATTERN}')
MOVE_PATTERN = re.compile(fr'Move {ACTIVITY_PATTERN} to\s*(?P<new_time>.+?)\s*$')
EDIT_PATTERN = re.compile(fr'Edit {ACTIVITY_PATTERN} to\s*(?P<new_activity>.+?)\s*$')

START_SAVE_PATTERN = re.compile(r'Checkpoint time =\s*(?P<time>\S+)')
END_SAVE_PATTERN = re.compile(r'Save/restore cycle complete')

def main(in_file: str):
    text = Path(in_file).read_text()
    edit_round = 0
    incon_time = None
    this_edit_batch = []
    fns = []
    fn_calls = []
    for ln in text.splitlines():
        if m := ROUND_PATTERN.search(ln):
            fn, call = translate_batch_fn(this_edit_batch, edit_round, incon_time)
            fns.append(fn)
            fn_calls.append(call)
            this_edit_batch = []
            edit_round = int(m.group("round"))
            fn_calls.append(f'println("{ln.strip()}")')
        elif m := START_SAVE_PATTERN.search(ln):
            incon_time = m.group("time")
        elif m := END_SAVE_PATTERN.search(ln):
            fn, call = translate_batch_fn(this_edit_batch, edit_round, incon_time)
            fns.append(fn)
            fn_calls.append(call)
            incon_time = None
            this_edit_batch = []
        elif m := ADD_PATTERN.search(ln):
            if edit_round == 0 or incon_time is not None:
                this_edit_batch.append(translate_activity_match(m))
            else:
                this_edit_batch.append(f'add({translate_activity_match(m)})')
        elif m := REMOVE_PATTERN.search(ln):
            this_edit_batch.append(f'remove({translate_activity_match(m)})')
        elif m := MOVE_PATTERN.search(ln):
            this_edit_batch.append(f'move({translate_activity_match(m)} to Instant.parse("{m.group("new_time")}"))')
        elif m := EDIT_PATTERN.search(ln):
            this_edit_batch.append(f'edit({translate_activity_match(m)} to {translate_activity_instance_str(m.group("new_activity"))})')
    fn, call = translate_batch_fn(this_edit_batch, edit_round, incon_time)
    fns.append(fn)
    fn_calls.append(call)

    test_fn_content = fn_calls[0] + '\n' + indent('\n'.join(fn_calls[1:]))
    support_fns = '\n\n'.join(fns)
    print(f'@Test\nfun `repro directly`() {{\n{INDENT}// split into methods to avoid "method too large" compile error\n{indent(test_fn_content)}\n}}\n\n{support_fns}')


ID_PATTERN = re.compile(r'id=(?P<id>[a-zA-Z0-9\-_]+)')
def translate_activity_match(m):
    return f'GroundedActivity(Instant.parse("{m.group("time")}"), Name("{m.group("name")}"), {translate_activity_instance_str(m.group("activity"))})'


def translate_activity_instance_str(activity_str):
    activity_str = ID_PATTERN.sub(r'id="\g<id>"', activity_str)
    return activity_str


MAX_BATCH_SIZE_PER_FN = 32
def translate_batch_fn(batch, edit_round, incon_time):
    if any('BlockActivity' in ln for ln in batch):
        model = 'BlockTestModel'
    else:
        model = 'TestModel'
    if edit_round == 0:
        if len(batch) < MAX_BATCH_SIZE_PER_FN:
            contents = '\n'.join([
                'println("Building initial plan")',
                f'return test(::{model},\n    ' + ',\n    '.join(batch) + '\n)',
            ])
            return f'private fun buildInitialTester(): IncrementalSimulationTester<{model}> {{\n{indent(contents)}\n}}', 'var tester = buildInitialTester()'
        else:
            part_functions = []
            while batch:
                batch_part, batch = batch[:MAX_BATCH_SIZE_PER_FN], batch[MAX_BATCH_SIZE_PER_FN:]
                part_functions.append(f'private fun buildInitialTester_part{len(part_functions) + 1}() = listOf(\n{indent(',\n'.join(batch_part))}\n)')
            contents = '\n'.join([
                'println("Building initial plan")',
                f'return test(::{model}, activities = \n' + '\n+ '.join(f'buildInitialTester_part{i}()' for i in range(1, len(part_functions) + 1)) + '\n)',
            ])
            return f'private fun buildInitialTester(): IncrementalSimulationTester<{model}> {{\n{indent(contents)}\n}}\n\n' + '\n\n'.join(part_functions), 'var tester = buildInitialTester()'
    elif incon_time is not None:
        if len(batch) < MAX_BATCH_SIZE_PER_FN:
            contents = '\n'.join([
                'println("Doing a save/restore cycle")',
                f'val inconTime = Instant.parse("{incon_time}")',
                'val incon = tester.save(inconTime)',
                f'return test(::{model}, startTime = inconTime, endTime = inconTime + 1.days, incon = incon, activities = listOf(\n    ' + ',\n    '.join(batch) + '\n)).also { println("Save/restore cycle complete") }',
            ])
            return f'private fun saveRestore{edit_round}(tester: IncrementalSimulationTester<{model}>): IncrementalSimulationTester<{model}> {{\n{indent(contents)}\n}}', f'tester = saveRestore{edit_round}(tester)'
        else:
            part_functions = []
            while batch:
                batch_part, batch = batch[:MAX_BATCH_SIZE_PER_FN], batch[MAX_BATCH_SIZE_PER_FN:]
                part_functions.append(f'private fun saveRestore{edit_round}_part{len(part_functions) + 1}() = listOf(\n{indent(',\n'.join(batch_part))}\n)')
            contents = '\n'.join([
                'println("Doing a save/restore cycle")',
                f'val inconTime = Instant.parse("{incon_time}")',
                'val incon = tester.save(inconTime)',
                f'return test(::{model}, startTime = inconTime, endTime = inconTime + 1.days, incon = incon, activities = \n' + indent('\n+ '.join(f'saveRestore{edit_round}_part{i}()' for i in range(1, len(part_functions) + 1))) + '\n).also { println("Save/restore cycle complete") }',
            ])
            return f'private fun saveRestore{edit_round}(tester: IncrementalSimulationTester<{model}>): IncrementalSimulationTester<{model}> {{\n{indent(contents)}\n}}\n\n' + '\n\n'.join(part_functions), f'tester = saveRestore{edit_round}(tester)'
    else:
        if len(batch) < MAX_BATCH_SIZE_PER_FN:
            contents = '\n'.join([
                'println("Running edits")',
                'tester.run(\n    ' + '\n    + '.join(batch) + '\n)',
            ])
            return f'private fun runEdits{edit_round}(tester: IncrementalSimulationTester<{model}>) {{\n{indent(contents)}\n}}', f'runEdits{edit_round}(tester)'
        else:
            part_functions = []
            while batch:
                batch_part, batch = batch[:MAX_BATCH_SIZE_PER_FN], batch[MAX_BATCH_SIZE_PER_FN:]
                part_functions.append(f'private fun runEdits{edit_round}_part{len(part_functions) + 1}() = (\n{indent('\n+ '.join(batch_part))}\n)')
            contents = '\n'.join([
                'println("Running edits")',
                'tester.run(\n' + indent('\n+ '.join(f'runEdits{edit_round}_part{i}()' for i in range(1, len(part_functions) + 1))) + '\n)',
            ])
            return f'private fun runEdits{edit_round}(tester: IncrementalSimulationTester<{model}>) {{\n{indent(contents)}\n}}\n\n' + '\n\n'.join(part_functions), f'runEdits{edit_round}(tester)'


INDENT = '    '
def indent(s):
    return INDENT + s.replace('\n', '\n' + INDENT)


if __name__ == '__main__':
    # Usage: pass the file containing the fuzz test stdout. Pipe to pbcopy, then paste into the test.
    main(sys.argv[1])
