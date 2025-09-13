import datetime as dt
import json
import random
from dataclasses import dataclass
from typing import Generator, List

RANDOM_SEED = 1

PLAN_START = dt.datetime.fromisoformat("2020-01-01T00:00:00Z")
PLAN_END = dt.datetime.fromisoformat("2021-01-01T00:00:00Z")

COMM_PASS_PERIOD = dt.timedelta(hours=14)
COMM_PASS_DEVIATION = dt.timedelta(hours=2)
COMM_PASS_DURATIONS = [
    *([dt.timedelta(hours=1)] * 1),
    *([dt.timedelta(hours=2)] * 3),
    *([dt.timedelta(hours=4)] * 1),
]
COMM_PASS_CRITICAL_CHANCE = 0.1
COMM_PASS_DOWNLINK_RATES = [
    *([1e1, 2e1, 5e1] * 4),
    *([1e2, 2e2, 5e2] * 2),
    *([1e3, 2e3, 5e3] * 1),
]


SCIENCE_OP_PERIOD = dt.timedelta(hours=5)
SCIENCE_OP_DEVIATION = dt.timedelta(hours=3)
SCIENCE_OP_DURATIONS = [
    dt.timedelta(minutes=10),
    dt.timedelta(minutes=20),
    dt.timedelta(minutes=40),
    dt.timedelta(hours=1),
    dt.timedelta(hours=2),
    dt.timedelta(hours=4),
]
SCIENCE_OP_CRITICAL_CHANCE = 0.1
SCIENCE_OP_TARGETS = [
    *[f"J2000_{pm}_{a}" for pm in ["POS", "NEG"] for a in "XYZ"],
    *(["EARTH"] * 2),
    *(["MARS"] * 4),
]


@dataclass
class Span:
    start: dt.datetime
    end: dt.datetime
    critical: bool


def main():
    random.seed(RANDOM_SEED)
    comm_passes = [
        {
            'start': span.start.isoformat(timespec='seconds'),
            'end': span.end.isoformat(timespec='seconds'),
            'critical': span.critical,
            'downlink_rate': random.choice(COMM_PASS_DOWNLINK_RATES),
        }
        for span in gen_spans(
            COMM_PASS_PERIOD,
            COMM_PASS_DEVIATION,
            COMM_PASS_DURATIONS,
            COMM_PASS_CRITICAL_CHANCE,
        )
    ]
    with open('comm_passes.json', 'w') as f:
        json.dump(comm_passes, f, indent=2)

    science_ops = [
        {
            'start': span.start.isoformat(timespec='seconds'),
            'end': span.end.isoformat(timespec='seconds'),
            'critical': span.critical,
            'target': random.choice(SCIENCE_OP_TARGETS),
        }
        for span in gen_spans(
            SCIENCE_OP_PERIOD,
            SCIENCE_OP_DEVIATION,
            SCIENCE_OP_DURATIONS,
            SCIENCE_OP_CRITICAL_CHANCE,
        )
    ]
    with open('science_ops.json', 'w') as f:
        json.dump(science_ops, f, indent=2)


def gen_spans(
        period: dt.timedelta,
        deviation: dt.timedelta,
        durations: List[dt.timedelta],
        critical_chance: float
) -> Generator[Span, None, None]:
    d_sec = deviation.total_seconds()
    t = PLAN_START + period
    while t + deviation < PLAN_END:
        start = t + dt.timedelta(seconds=random.uniform(-d_sec, d_sec))
        end = start + random.choice(durations)
        critical = random.random() < critical_chance
        yield Span(start, end, critical)
        t += period


if __name__ == '__main__':
    main()
