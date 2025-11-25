import subprocess
from pathlib import Path

HERE = Path(__file__).absolute().parent
REPO_ROOT = HERE.parent.parent
SIM_JAR = REPO_ROOT / 'build/libs/pyre-1.0-SNAPSHOT.jar'
OUTPUT_DIR = HERE / 'output'

JAVA_MAX_MEMORY_GB = 60


def main():
    setup_files = list(HERE.glob('*-year.setup.json'))
    for setup in setup_files:
        name_prefix = setup.name.removesuffix('.setup.json')
        for i in range(1, 4):
            name = f"{name_prefix}-{i}"
            cmd = [
                'measure_memory', OUTPUT_DIR / name,
                'java',
                f'-Xmx{JAVA_MAX_MEMORY_GB}g',
                '-jar', SIM_JAR,
                setup,
            ]
            print(f'Running {name} - {subprocess.list2cmdline(cmd)}')
            subprocess.run(cmd)


if __name__ == '__main__':
    main()
