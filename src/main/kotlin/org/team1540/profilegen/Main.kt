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
import java.io.FileNotFoundException
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap
import java.util.Arrays.hashCode

fun main(args: Array<String>) {
    val rootPath = Paths.get(".").toAbsolutePath().normalize().toString()

    // Make the generated directory if it isn't there
    File("$rootPath/generated/").mkdir()

    val inputStream: List<String>
    val propertiesHash: Int
    val profileHash: Int

    try {
        propertiesHash = getFileHash("$rootPath/profiles.json")
        profileHash = getFileHash("$rootPath/trajectory.properties")

        try {
            inputStream = File("profileSettingsHashes.txt").readLines()

            if (inputStream[0] != propertiesHash.toString() ||
                    inputStream[1] != profileHash.toString() ||
                    File("$rootPath/generated").list().isEmpty()) {
                println("Profile config hashes do not match or generated folder is empty!")
                generatePaths(rootPath)
                writeHashesToFile(propertiesHash, profileHash)
            } else {
                println("Profile settings have not changed, no need to generate them again!")
            }
        } catch (_: FileNotFoundException) {
            println("Could not get profile config hash file!")
            generatePaths(rootPath)
            writeHashesToFile(propertiesHash, profileHash)
        }
    } catch (_: FileNotFoundException) {
        println("Profiles/trajectory properties files not found!")
    }
}

private fun generatePaths(rootPath: String) {
    // load settings from properties file
    var config: Trajectory.Config? = null
    var width: Double? = null
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
        println("Generating profile $key")
        val flip = path.boolean("flip") ?: paramError("flip", key)

        val fitMethod = when (path.string("fitMethod")) {
            "HERMITE_CUBIC" -> Trajectory.FitMethod.HERMITE_CUBIC
            "HERMITE_QUINTIC" -> Trajectory.FitMethod.HERMITE_QUINTIC
            null -> null
            else -> null.also { println("Warning: ${path.string("fitMethod")} is not a valid fit method, reverting to default") }
        }?.also { println("Overriding fitMethod for profile $key with value $it") }

        val samplesInput = try {
            path.string("samples")
        } catch (_: ClassCastException) {
            try {
                path.int("samples")
            } catch (_: ClassCastException) {
                null.also { println("Warning: samples parameter in path $key is not a valid sample amount, reverting to default") }
            }
        }

        val samples = when (samplesInput) {
            "HIGH" -> Trajectory.Config.SAMPLES_HIGH
            "LOW" -> Trajectory.Config.SAMPLES_LOW
            "FAST" -> Trajectory.Config.SAMPLES_FAST
            is Int -> samplesInput
            else -> null
        }?.also { println("Overriding samples for profile $key with value $it") }

        val dt = try {
            path.goodDouble("dt")
                    ?.also { println("Overriding dt for profile $key with value $it") }
        } catch (_: ClassCastException) {
            null
        }

        val maxVel = try {
            path.goodDouble("maxVel")?.also { println("Overriding maxVel for profile $key with value $it") }
        } catch (_: ClassCastException) {
            null
        }

        val maxAccel = try {
            path.goodDouble("maxAccel")?.also { println("Overriding maxAccel for profile $key with value $it") }
        } catch (_: ClassCastException) {
            null
        }

        val maxJerk = try {
            path.goodDouble("maxJerk")?.also { println("Overriding maxJerk for profile $key with value $it") }
        } catch (_: ClassCastException) {
            null
        }

        val wheelbase = try {
            path.goodDouble("wheelbase")?.also { println("Overriding maxJerk for profile $key with value $it") }
        } catch (_: ClassCastException) {
            null
        }

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
        paths[key] = Path(generate(points.toTypedArray(),
                Trajectory.Config(
                        fitMethod ?: config!!.fit,
                        samples ?: config!!.sample_count,
                        dt ?: config!!.dt,
                        maxVel ?: config!!.max_velocity,
                        maxAccel ?: config!!.max_acceleration,
                        maxJerk ?: config!!.max_jerk
                )), flip, wheelbase ?: width!!)
    }

    // perform post-processing
    for ((name, path) in paths.entries) {
        val mod = TankModifier(path.trajectory).modify(path.wheelbase)

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
                        it.heading)
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
                        it.heading)
            }.toTypedArray())
        } else {
            leftTrajectory = mod.leftTrajectory
            rightTrajectory = mod.rightTrajectory
        }

        // write files
        writeToCSV(File("$rootPath/generated/${name}_left.csv"), leftTrajectory)
        writeToCSV(File("$rootPath/generated/${name}_right.csv"), rightTrajectory)
    }
}

private fun paramError(paramName: String, key: String): Nothing = throw RuntimeException("Parameter \"$paramName\" in path $key is missing or invalid")

private fun Properties.getPropertyOrExit(name: String): String = getProperty(name)
        ?: throw RuntimeException("Property $name not found in trajectory.properties file")

private data class Path(val trajectory: Trajectory, val flip: Boolean, val wheelbase: Double)

fun JsonObject.goodDouble(key: String): Double? {
    return try {
        double(key)
    } catch (e: ClassCastException) {
        int(key)?.toDouble()
    }
}

private fun getFileHash(path: String) = hashCode(File(path).readBytes())

private fun writeHashesToFile(propertiesHash: Int, profileHash: Int) {
    println("Writing updated file hashes...")
    File("profileSettingsHashes.txt").printWriter().use { out ->
        out.println(propertiesHash)
        out.println(profileHash)
    }
}