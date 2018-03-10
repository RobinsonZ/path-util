# path-util

A stupid utility for generating paths using [Jaci's Pathfinder](https://github.com/JacisNonsense/Pathfinder).

## How To Use

Create a `trajectory.properties` file to configure global settings:

```properties
# Fit method to use for trajectory generation. Accepted values: HERMITE_CUBIC or HERMITE_QUINTIC
fitMethod=HERMITE_CUBIC
# Number of samples to use. Accepted values: HIGH, LOW, FAST, or an integer value.
samples=HIGH
# Change in time for each point, in seconds.
dt=03
# Maximum velocity in units per second
# units can be anything, but must be consistent across velocity acceleration, jerk, wheelbase, and position parameters
maxVel=40
# Maximum acceleration in units per second squared
maxAccel=40
# Maximum jerk in units per second cubed
maxJerk=2300
# The width of your wheels
wheelbase=2591
```

Create a `profiles.json` file containing your trajectories:

```json
{
  "go_straight": {
    "points": [
      {
        "x": 0,
        "y": 0,
        "angle": 0
      },
      {"x": 134.5, "y": 0, "angle": 0}
    ],
    "flip": false
  },
  "fancy-auto": {
    "points": [
      {"x": 0, "y": 0, "angle": 0},
      {"x": 120.250, "y": 50.75, "angle": 0.1},
      {"x": 200, "y": -50, "angle": 0}
    ],
    "flip": true
  }
}
```

* For each entry in the `points` array:
  * `x` is the waypoint position forward and backward (same position units as in `trajectory.properties`)
  * `y` is the waypoint position left and right
  * `angle` is the waypoint heading in radians
* If `flip` is true, the profile will be negated and left and right will be swapped (this will make you go backwards)

Download the jar from the releases page and run it from the command line using `java -jar profilegeneration.jar` in the same directory as your `trajectory.properties` and `profiles.json` files. Each profile will be written to two files, name_left.csv and name_right.csv, where `name` is the key for the profile entry in `profiles.json`.

## Building

To create an executable jar file with all dependencies, run the `fatCapsule` task from Gradle.
