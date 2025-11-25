import sys
import json
from pathlib import Path
import datetime as dt


HERE = Path(__file__).absolute().parent


def main(n_years: int):
    start = dt.datetime(2020, 1, 1)
    end = start + (dt.timedelta(days=365) * n_years)

    plan = {
        "start": start.isoformat() + 'Z',
        "end": end.isoformat() + 'Z',
        "activities": []
    }
    with open(HERE / f"{n_years}-year.plan.json", 'w') as f:
        json.dump(plan, f, indent=4)

    setup = {
        "plan": f"{n_years}-year.plan.json",
        "output": f"output/{n_years}-year.events.csv"
    }
    with open(HERE / f"{n_years}-year.setup.json", 'w') as f:
        json.dump(setup, f, indent=4)


if __name__ == '__main__':
    main(int(sys.argv[1]))
