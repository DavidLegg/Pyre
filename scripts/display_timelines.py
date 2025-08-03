import json
import os
import sys
import datetime as dt
from json import JSONDecodeError

import matplotlib as mpl
mpl.rcParams['agg.path.chunksize'] = 5000
mpl.rcParams['path.simplify_threshold'] = 1.0

import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
from typing import List, Optional, Annotated
import typer

# TODO: Decimation: min/max over each decimation window (look up screen resolution?)
#   This is hard to do well, probably better handled by just not plotting things live.

class Limits:
    def __init__(self, mn = None, mx = None, mn_buffer = 0.1, mx_buffer = 0.1):
        self.mn = mn
        self.mx = mx
        self.mn_buffer = mn_buffer
        self.mx_buffer = mx_buffer

    def merge(self, other: 'Limits') -> bool:
        if self.mn is None:
            self.mn = other.mn
            self.mx = other.mx
            return True

        changed = False
        if other.mn < self.mn:
            self.mn = other.mn - self.mn_buffer * (self.mx - other.mn)
            changed = True
        if other.mx > self.mx:
            self.mx = other.mx + self.mx_buffer * (other.mx - self.mn)
            changed = True
        return changed


class ResourceData:
    def __init__(self):
        self.x = []
        self.y = []

    def merge(self, other: 'ResourceData'):
        self.x += other.x
        self.y += other.y


def main(
        resources: Annotated[List[str], typer.Argument(help='Which resources to display')],
        # live: Annotated[bool, typer.Option(help='Plot simulation results incrementally.')] = False
):
    os.set_blocking(sys.stdin.fileno(), False)
    data = {r: ResourceData() for r in resources}

    fig, axes = plt.subplots(nrows=len(resources), sharex=True)
    last_axis = axes[-1]
    last_axis.set_xlabel('Time')
    axes = dict(zip(resources, axes))
    x_limits = Limits(mn_buffer=0)
    y_limits = {r: Limits() for r in resources}
    lines = {}
    for r, ax in axes.items():
        lines[r] = ax.plot([], [])[0]
        ax.set_ylabel(r)
    all_lines = tuple(lines.values())

    buffered_text = ''
    def update(frame):
        nonlocal buffered_text, data, lines, axes, x_limits, y_limits, all_lines

        new_data = {r: ResourceData() for r in resources}

        buffered_text += sys.stdin.read()
        input_lines = buffered_text.split('\n')
        # The last line is whatever was after the last newline; an incomplete line
        buffered_text = input_lines.pop()
        for ln in input_lines:
            try:
                report = json.loads(ln)
                resource_new_data = new_data.get(report['channel'])
                if resource_new_data is not None:
                    resource_new_data.x.append(parse_dt(report['time']))
                    resource_new_data.y.append(float(report['data']))
            except (JSONDecodeError, KeyError):
                # Just ignore bad data
                pass

        overall_x_changed = False
        for r, rnd in new_data.items():
            data[r].merge(rnd)
            lines[r].set_data(data[r].x, data[r].y)
            if x_limits.merge(Limits(mn=min(rnd.x),mx=max(rnd.x))):
                overall_x_changed = True
            if y_limits[r].merge(Limits(mn=min(rnd.y),mx=max(rnd.y))):
                axes[r].set_ylim(y_limits[r].mn, y_limits[r].mx)

        if overall_x_changed:
            last_axis.set_xlim(x_limits.mn, x_limits.mx)

        return all_lines

    animation = FuncAnimation(fig, update, blit=True, interval=200)

    plt.show()


DATETIME_FORMATS = [
    '%Y-%m-%dT%H:%M:%SZ',
    '%Y-%m-%dT%H:%M:%S.%fZ',
]
def parse_dt(s: str):
    for fmt in DATETIME_FORMATS:
        try:
            return dt.datetime.strptime(s, fmt)
        except ValueError:
            pass
    raise ValueError(f"Invalid datetime string '{s}'")



if __name__ == "__main__":
    typer.run(main)
