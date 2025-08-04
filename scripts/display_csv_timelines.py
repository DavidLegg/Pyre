import json
from dataclasses import dataclass
from typing import List, Optional

import typer
import pandas as pd
import matplotlib.pyplot as plt

@dataclass
class ResourceView:
    name: str
    kind: str
    discrete_values: List[str]

    @classmethod
    def from_json_obj(cls, json_obj: dict) -> 'ResourceView':
        return ResourceView(
            json_obj['name'],
            json_obj.get('kind', 'continuous'),
            json_obj.get('discrete_values', [])
        )


@dataclass
class View:
    resources: List[ResourceView]

    @classmethod
    def from_json_file(cls, view_file: str) -> 'View':
        with open(view_file) as f:
            json_obj = json.load(f)
        return View.from_json_obj(json_obj)

    @classmethod
    def from_json_obj(cls, json_obj: dict) -> 'View':
        return View([ResourceView.from_json_obj(r) for r in json_obj['resources']])


def main(csv_file: str, view_file: str):
    print(f'Reading {csv_file}')
    data = pd.read_csv(csv_file)
    data.time = pd.to_datetime(data.time)
    data.set_index('time', inplace=True)
    print(f'Reading {view_file}')
    view_obj = View.from_json_file(view_file)
    print(f'Plotting')
    fig, axes = plt.subplots(nrows=len(view_obj.resources), sharex=True, layout="constrained")
    for resource_view, ax in zip(view_obj.resources, axes):
        ax.set_title(resource_view.name)
        if resource_view.kind == 'continuous':
            data[resource_view.name].ffill().plot(ax=ax)
        elif resource_view.kind == 'discrete':
            resource_data = data[resource_view.name].ffill()
            # Compute the last moment each state value is active
            rd_ends = resource_data.shift(1)[resource_data.shift(1) != resource_data][1:]
            # Add these points in, ordered before each change, to build a step-plot
            resource_data = pd.concat((rd_ends, resource_data)).sort_index()
            # Build the label-to-number translation
            # Give priority to explicitly-listed values
            state_table = {label: i for i, label in enumerate(resource_view.discrete_values or [])}
            # As a fallback, add any other values in the data
            for actual_value in resource_data.unique():
                if actual_value not in state_table:
                    state_table[actual_value] = len(state_table)
            resource_data.replace(state_table, inplace=True)
            resource_data.plot(ax=ax)
            tick_to_label = {idx: label for label, idx in state_table.items()}
            ax.set_yticks(list(range(len(state_table))), [tick_to_label[i] for i in range(len(state_table))])
        # TODO: Activities type - need to think about how to "plot" this...
        else:
            raise ValueError(f"'{resource_view.kind}' is not a recognized resource kind. Use 'continuous' or 'discrete'")
    plt.show()


if __name__ == "__main__":
    typer.run(main)
