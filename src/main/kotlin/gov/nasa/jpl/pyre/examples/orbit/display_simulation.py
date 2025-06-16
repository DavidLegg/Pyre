import json
import subprocess
import matplotlib.pyplot as plt
import numpy as np

JAR_FILE = '/Users/dlegg/Code/pyre/build/libs/pyre-1.0-SNAPSHOT.jar'

HOURS = 24 * 100
PLOT = False


def main():

    min_x = -5
    max_x = 5
    min_y = -5
    max_y = 5

    process = subprocess.Popen(['java', '-jar', JAR_FILE, f'{HOURS}:00:00.000000'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    if PLOT:
        plt.ion()
        fig, ax = plt.subplots()
        ax.set_xlim(min_x, max_x)
        ax.set_ylim(min_y, max_y)
        plt.show()
        earth_line, = ax.plot([], [], 'b-', label='Earth')
        moon_line, = ax.plot([], [], 'g-', label='Moon')
        plot_updates = {
            'earth_position': (lambda x, y: (earth_line.set_xdata(np.append(earth_line.get_xdata(), x)), earth_line.set_ydata(np.append(earth_line.get_ydata(), y)))),
            'moon_position': (lambda x, y: (moon_line.set_xdata(np.append(moon_line.get_xdata(), x)), moon_line.set_ydata(np.append(moon_line.get_ydata(), y)))),
        }
    else:
        plot_updates = {}

    while (ln := process.stdout.readline()):
        report = json.loads(ln)
        channel = report['channel']
        if PLOT:
            if channel in plot_updates:
                x = report['data'][0]
                y = report['data'][1]
                plot_updates[channel](x, y)
                ax.set_title('Time: ' + report['time'])
                if x < min_x:
                    min_x = x - (max_x - x) * 0.1
                    ax.set_xlim(min_x, max_x)
                elif x > max_x:
                    max_x = x + (x - min_x) * 0.1
                    ax.set_xlim(min_x, max_x)
                if y < min_y:
                    min_y = y - (max_y - y) * 0.1
                    ax.set_ylim(min_y, max_y)
                elif y > max_y:
                    max_y = y + (y - min_y) * 0.1
                    ax.set_ylim(min_y, max_y)

                fig.canvas.draw()
                plt.pause(1e-6)
        else:
            print(report['time'], '-', channel, '-', report['data'])

    if PLOT:
        plt.ioff()
        plt.show()



if __name__ == '__main__':
    main()
