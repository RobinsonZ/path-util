@file:JvmName("Main")

package org.team1540.profilegen

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import jaci.pathfinder.Pathfinder.generate
import jaci.pathfinder.Pathfinder.writeToCSV
import jaci.pathfinder.Trajectory
import jaci.pathfinder.Waypoint
import jaci.pathfinder.modifiers.TankModifier
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap

fun main(args: Array<String>) {
    // load settings from properties file
    var config: Trajectory.Config? = null
    var width: Double? = null
    // load a config file
    val rootPath = Paths.get(".").toAbsolutePath().normalize().toString();
    Properties().apply {
        load(FileInputStream("$rootPath/trajectory.properties"))
        config = Trajectory.Config(
                when (getPropertyOrExit("fitMethod")) {
                    "HERMITE_CUBIC" -> Trajectory.FitMethod.HERMITE_CUBIC
                    "HERMITE_QUINTIC" -> Trajectory.FitMethod.HERMITE_QUINTIC
                    else -> throw RuntimeException("${getProperty("fitMethod")} is not a valid fit method!")
                },
                when {
                    getPropertyOrExit("samples") == "HIGH" -> Trajectory.Config.SAMPLES_HIGH
                    getPropertyOrExit("samples") == "LOW" -> Trajectory.Config.SAMPLES_LOW
                    getPropertyOrExit("samples") == "FAST" -> Trajectory.Config.SAMPLES_FAST
                    getPropertyOrExit("samples").toIntOrNull() != null -> getProperty("samples").toInt()
                    else -> throw RuntimeException("${getProperty("samples")} is not a valid sample amount!")
                },
                getPropertyOrExit("dt").toDouble(),
                getPropertyOrExit("maxVel").toDouble(),
                getPropertyOrExit("maxAccel").toDouble(),
                getPropertyOrExit("maxJerk").toDouble()
        )

        width = getPropertyOrExit("wheelbase").toDouble()
    }

    config!!

    val paths: MutableMap<String, Path> = HashMap()
    val profilesJson = Parser().parse(FileInputStream(File("$rootPath/profiles.json"))) as JsonObject
    for (key in profilesJson.keys) {
        val path = profilesJson.obj(key)
                ?: throw RuntimeException("Item at key $key is not a valid JSON object")
        val flip = path.boolean("flip") ?: paramError("flip", key)
        val points: MutableList<Waypoint> = LinkedList()
        path.array<JsonObject>("points")?.forEachIndexed { i, point ->
            val x = point.goodDouble("x")
            val y = point.goodDouble("y")
            val angle = point.goodDouble("angle")
            if (x != null && y != null && angle != null) {
                points.add(Waypoint(x, y, angle))
            } else {
                throw RuntimeException("Parameters in path $key point $i are missing or invalid")
            }
        } ?: paramError("points", key)
        paths[key] = Path(generate(points.toTypedArray(), config), flip)
    }

    // perform post-processing
    for ((name, path) in paths.entries) {
        val mod = TankModifier(path.trajectory).modify(width!!)

        val leftTrajectory: Trajectory
        val rightTrajectory: Trajectory
        if (path.flip) {
            leftTrajectory = Trajectory(mod.rightTrajectory.segments.map {
                Trajectory.Segment(
                        it.dt,
                        -it.x,
                        -it.y,
                        -it.position,
                        -it.velocity,
                        -it.acceleration,
                        -it.jerk,
                        -it.heading)
            }.toTypedArray())
            rightTrajectory = Trajectory(mod.leftTrajectory.segments.map {
                Trajectory.Segment(
                        it.dt,
                        -it.x,
                        -it.y,
                        -it.position,
                        -it.velocity,
                        -it.acceleration,
                        -it.jerk,
                        -it.heading)
            }.toTypedArray())
        } else {
            leftTrajectory = mod.leftTrajectory
            rightTrajectory = mod.rightTrajectory
        }

        // write files
        writeToCSV(File("$rootPath/${name}_left.csv"), leftTrajectory)
        writeToCSV(File("$rootPath/${name}_right.csv"), rightTrajectory)
    }
}

private fun paramError(paramName: String, key: String): Nothing = throw RuntimeException("Parameter \"$paramName\" in path $key is missing or invalid")

private fun Properties.getPropertyOrExit(name: String): String = getProperty(name)
        ?: throw RuntimeException("Property $name not found in trajectory.properties file")

private data class Path(val trajectory: Trajectory, val flip: Boolean)

fun JsonObject.goodDouble(key: String): Double? {
    return try {
        double(key)
    } catch (e: ClassCastException) {
        int(key)?.toDouble()
    }
}
