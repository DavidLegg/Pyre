import json
from dataclasses import dataclass
from typing import List, Optional, Set, Annotated

import typer
import pandas as pd
import matplotlib.pyplot as plt

@dataclass
class ResourceView:
    name: str
    kind: str
    discrete_values: List[str]
    resolution: pd.Timedelta

    @classmethod
    def from_json_obj(cls, json_obj: dict) -> 'ResourceView':
        return ResourceView(
            json_obj['name'],
            json_obj.get('kind', 'discrete'),
            json_obj.get('discrete_values', []),
            pd.to_timedelta(json_obj.get('resolution', '1 hr')),
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


def main(data: str, view: str, plan: Optional[str] = None):
    print(f'Reading view file {view}')
    view_obj = View.from_json_file(view)
    print(f'Reading data file {data}')
    data = read_data(data, {r.name for r in view_obj.resources})
    if plan is not None:
        print(f'Reading plan file {plan}')
        with open(plan) as f:
            plan_obj = json.load(f)
        start = pd.to_datetime(plan_obj['start'])
        end = pd.to_datetime(plan_obj['end'])
        data = data.reindex(data.index.union([start, end]))
    else:
        print(f'No plan file given.')
        start = data.index[0]
        end = data.index[-1]
    print(f'Plotting')
    fig, axes = plt.subplots(nrows=len(view_obj.resources), sharex=True, layout="constrained")
    axes[0].set_xlim(start, end)
    for resource_view, ax in zip(view_obj.resources, axes):
        ax.set_title(resource_view.name)
        try:
            resource_data = data[resource_view.name]
        except KeyError:
            print(f'Warning! {resource_view.name} not found in data. Plot will be empty')
            continue
        if resource_view.kind == 'interpolate':
            # Interpolate: numeric values, connected by drawing lines between the data points.
            # This doesn't exactly reflect what the simulator did, but may be "good enough" and is friendlier to the viewer.
            # It also happens to be the default way of plotting, so it's easy to add in here.
            resource_data.ffill().plot(ax=ax)
        elif resource_view.kind == 'discrete':
            # Discrete: numeric values, active until the next data point. Requires a step-plot.
            resource_data = pd.to_numeric(resource_data)
            resource_data.ffill(inplace=True)
            # Compute the last moment each state value is active
            rd_ends = resource_data.shift(1)[resource_data.shift(1) != resource_data][1:]
            # Add these points in, ordered before each change, to build a step-plot
            resource_data = pd.concat((rd_ends, resource_data)).sort_index(kind='stable')
            # Plot the resulting step-plot
            resource_data.plot(ax=ax)
        elif resource_view.kind == 'enum':
            # Enum: categorical values, active until the next data point. Requires remapping to ints and a step-plot.
            resource_data.ffill(inplace=True)
            # Compute the last moment each state value is active
            rd_ends = resource_data.shift(1)[resource_data.shift(1) != resource_data][1:]
            # Add these points in, ordered before each change, to build a step-plot
            resource_data = pd.concat((rd_ends, resource_data)).sort_index(kind='stable')
            # TODO: Series.factorize might be able to simplify the code below
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
        elif resource_view.kind == 'polynomial':
            end_time = resource_data.index[-1]
            resource_data.dropna(inplace=True)
            times = resource_data.index.to_series()
            plot_data = []
            plot_index = []
            for coef_str, start, end in zip(resource_data, times, times.shift(-1).fillna(end_time)):
                # Each point ends when a new polynomial point is available
                coefs: List[float] = json.loads(coef_str)
                if len(coefs) <= 2:
                    # Linear segment - for performance, just add a start and end point
                    b = 0.0 if len(coefs) < 1 else coefs[0]
                    m = 0.0 if len(coefs) < 2 else coefs[1]
                    plot_index += [start, end]
                    plot_data += [b, m * (end - start).total_seconds() + b]
                else:
                    # TODO: Use resource_view.resolution to sample within the interval
                    raise NotImplementedError("polynomials with degree >= 2")
            plot_series = pd.Series(plot_data, index=plot_index)
            plot_series.plot(ax=ax)
        elif resource_view.kind == 'span': # Alternatively, 'event' to show points, or 'activity' to specialize further?
            resource_data = resource_data.dropna().apply(json.loads)
            end_events = resource_data[resource_data.apply(lambda x: 'end' in x)]
            spans = pd.DataFrame(list(end_events), columns=['name', 'type', 'start', 'end'])
            spans.start = pd.to_datetime(spans.start)
            spans.end = pd.to_datetime(spans.end)
            spans.sort_values('start', inplace=True)
            active_spans = []
            for _, span in spans.iterrows():
                active_spans = [(t, i) for t, i in active_spans if t >= span.end]
                active_indices = {i for _, i in active_spans}
                y = -min(i for i in range(len(active_spans) + 1) if i not in active_indices)
                pd.Series([y, y], index=[span.start, span.end]).plot(ax=ax)
                ax.annotate(
                    span['name'], # Not the series name, the field called "name"
                    xy=(span.start, y), xycoords='data',
                )
                active_spans.append((span.end, -y))
            ax.get_yaxis().set_ticks([])
        else:
            raise ValueError(f"'{resource_view.kind}' is not a recognized resource kind. Use 'continuous' or 'discrete'")
    plt.show()


def read_data(csv_file: str, resources: Set[str]) -> pd.DataFrame:
    if csv_file.endswith('timelines.csv'):
        print(f'Detected data file is in timelines format')
        # Down-select columns as we read. If the timelines file is massive, this can save memory.
        data = pd.read_csv(csv_file, usecols=resources | {'time'})
        # Parse time and use that as the index
        data.time = pd.to_datetime(data.time)
        data.set_index('time', inplace=True)
    else:
        if csv_file.endswith('events.csv'):
            print(f'Detected data file is in events format')
        else:
            print(f"Warning! Assuming data file is in events format."
                  f" Use suffix '.events.csv' to confirm this format, or '.timelines.csv' to indicate timelines format.")
        # We can't down-select as we read events
        data = pd.read_csv(csv_file)
        print(f'Reformatting events as timelines')
        # Down-select to the resources we care about
        data = data[data.channel.isin(resources)]
        # Parse times
        data.time = pd.to_datetime(data.time)
        # De-duplicate, taking only the last value for each resource at each time (the "settled" value)
        data.drop_duplicates(['time', 'channel'], keep='last', inplace=True)
        # Pivot from event format to timeline format
        data = data.pivot(index='time', columns='channel', values='data')
    return data


if __name__ == "__main__":
    typer.run(main)
