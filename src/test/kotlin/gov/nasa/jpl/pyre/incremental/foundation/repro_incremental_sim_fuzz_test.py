import sys
from pathlib import Path
import re

ROUND_PATTERN = re.compile(r'Running round (?P<round>\d+)')
ACTIVITY_PATTERN = r"""GroundedActivity\(\s*time=(?P<time>[^,)]*)\s*,\s*name=(?P<name>[^,)]*)\s*,\s*activity=(?P<activity>.*)\s*\)"""
ADD_PATTERN = re.compile(f'Add {ACTIVITY_PATTERN}')
REMOVE_PATTERN = re.compile(f'Remove {ACTIVITY_PATTERN}')
MOVE_PATTERN = re.compile(fr'Move {ACTIVITY_PATTERN} to\s*(?P<new_time>\S+)')
EDIT_PATTERN = re.compile(fr'Edit {ACTIVITY_PATTERN} to\s*(?P<new_activity>\S+)\s*')

START_SAVE_PATTERN = re.compile(r'Checkpoint time =\s*(?P<time>\S+)')
END_SAVE_PATTERN = re.compile(r'Save/restore cycle complete')

def main(in_file: str):
    text = Path(in_file).read_text()
    first_batch = True
    incon_time = None
    this_edit_batch = []
    for ln in text.splitlines():
        if m := ROUND_PATTERN.search(ln):
            print_batch(this_edit_batch, first_batch, incon_time)
            this_edit_batch = []
            first_batch = False
            print(f'println("{ln.strip()}")')
        elif m := START_SAVE_PATTERN.search(ln):
            incon_time = m.group("time")
        elif m := END_SAVE_PATTERN.search(ln):
            print_batch(this_edit_batch, first_batch, incon_time)
            incon_time = None
            this_edit_batch = []
        elif m := ADD_PATTERN.search(ln):
            if first_batch or incon_time is not None:
                this_edit_batch.append(translate_activity_match(m))
            else:
                this_edit_batch.append(f'add({translate_activity_match(m)})')
        elif m := REMOVE_PATTERN.search(ln):
            this_edit_batch.append(f'remove({translate_activity_match(m)})')
        elif m := MOVE_PATTERN.search(ln):
            this_edit_batch.append(f'move({translate_activity_match(m)} to Instant.parse("{m.group("new_time")}"))')
        elif m := EDIT_PATTERN.search(ln):
            this_edit_batch.append(f'edit({translate_activity_match(m)} to {translate_activity_instance_str(m.group("new_activity"))})')
    print_batch(this_edit_batch, first_batch, incon_time)


ID_PATTERN = re.compile(r'id=(?P<id>[a-zA-Z0-9\-_]+)')
def translate_activity_match(m):
    return f'GroundedActivity(Instant.parse("{m.group("time")}"), Name("{m.group("name")}"), {translate_activity_instance_str(m.group("activity"))})'


def translate_activity_instance_str(activity_str):
    activity_str = ID_PATTERN.sub(r'id="\g<id>"', activity_str)
    return activity_str


def print_batch(batch, first_batch, incon_time):
    if first_batch:
        print('var incon: Checkpoint<TestModel>')
        print('var inconTime: Instant')
        print('println("Building initial plan")')
        print('var tester = test(\n    ' + ',\n    '.join(batch) + '\n)')
    elif incon_time is not None:
        print('println("Doing a save/restore cycle")')
        print(f'inconTime = Instant.parse("{incon_time}")')
        print('incon = tester.save(inconTime)')
        print('tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon, activities = listOf(\n    ' + ',\n    '.join(batch) + '\n))')
        print('println("Save/restore cycle complete")')
    else:
        print('println("Running edits")')
        print('tester.run(\n    ' + '\n    + '.join(batch) + '\n)')


if __name__ == '__main__':
    # Usage: pass the file containing the fuzz test stdout. Pipe to pbcopy, then paste into the test.
    main(sys.argv[1])
