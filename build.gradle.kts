import java.io.ByteArrayOutputStream

plugins {
    application
    kotlin("jvm") version "1.3.61"
}

repositories {
    mavenCentral()
    /* 
     * The following repositories contain beta features and should be added for experimental mode only
     * 
     * maven("https://dl.bintray.com/alchemist-simulator/Alchemist/")
     * maven("https://dl.bintray.com/protelis/Protelis/")
     */
}
/*
 * Only required if you plan to use Protelis, remove otherwise
 */
sourceSets {
    main {
        resources {
            srcDir("src/main/protelis")
        }
    }
}
val alchemistVersion = "9.3.0-dev1bn+1fc9a8ebd" // "9.3.0-dev1bc+fd2c3a1c0" // "9.3.0-dev1bn+1fc9a8ebd"
dependencies {
    implementation("com.codepoetics:protonpack:1.13")
    implementation("de.ruedigermoeller:fst:2.57")
    implementation("it.unibo.alchemist:alchemist:$alchemistVersion")
    implementation("it.unibo.alchemist:alchemist-euclidean-geometry:$alchemistVersion")
    implementation("it.unibo.alchemist:alchemist-incarnation-protelis:$alchemistVersion")
    implementation("it.unibo.alchemist:alchemist-swingui:$alchemistVersion")
    implementation("org.danilopianini:thread-inheritable-resource-loader:0.3.5")
    implementation("net.agkn:hll:1.6.0")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("org.jgrapht:jgrapht-core:1.5.0")
    implementation("org.protelis:protelis-lang:14.0.0")
    implementation("org.protelis:protelis-interpreter:14.0.0")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
}

// Heap size estimation for batches
val maxHeap: Long? by project
val heap: Long = maxHeap ?:
if (System.getProperty("os.name").toLowerCase().contains("linux")) {
    ByteArrayOutputStream().use { output ->
        exec {
            executable = "bash"
            args = listOf("-c", "cat /proc/meminfo | grep MemAvailable | grep -o '[0-9]*'")
            standardOutput = output
        }
        output.toString().trim().toLong() / 1024
    }
        .also { println("Detected ${it}MB RAM available.") }  * 8 / 10
} else {
    // Guess 16GB RAM of which 2 used by the OS
    14 * 1024L
}
val cpuCount = Runtime.getRuntime().availableProcessors()
println("Detected $cpuCount processors")

val alchemistGroup = "Run Alchemist"
/*
 * This task is used to run all experiments in sequence
 */
val runAllGraphic by tasks.register<DefaultTask>("runAllGraphic") {
    group = alchemistGroup
    description = "Launches all simulations with the graphic subsystem enabled"
}
val runAllBatch by tasks.register<DefaultTask>("runAllBatch") {
    group = alchemistGroup
    description = "Launches all experiments"
}
data class Experiment(
    val name: String,
    val maxTaskSize: Int = 512,
    val samplingTime: Double = 1.0,
    val variables: Array<String> = emptyArray()
)
val customization = listOf(
    Experiment("simulation", (7.6 * 1024).toInt(), 0.5, arrayOf("speed", "meanNeighbors", "nodeCount")),
    Experiment("converge", maxTaskSize = 2048 + 512, variables = arrayOf("diameter")),
    Experiment("leaderelection", maxTaskSize = 2048, variables = arrayOf("grain", "nodeCount", "deploymentType"))
).groupBy { it.name }.mapValues { (_, list) -> list.first() }
/*
 * Scan the folder with the simulation files, and create a task for each one of them.
 */
File(rootProject.rootDir.path + "/src/main/yaml").listFiles()
    ?.filterNot { it.name.startsWith("run") }
    ?.filter { it.extension == "yml" }
    ?.sortedBy { it.nameWithoutExtension }
    ?.forEach {
        fun basetask(name: String, additionalConfiguration: JavaExec.() -> Unit = {}) = tasks.register<JavaExec>(name) {
            group = alchemistGroup
            description = "Launches graphic simulation ${it.nameWithoutExtension}"
            main = "it.unibo.alchemist.Alchemist"
            classpath = sourceSets["main"].runtimeClasspath
            args(
                "-y", it.absolutePath,
                "-g", "effects/${it.nameWithoutExtension}.aes"
//                "-e", "export-from-graphical-sim.txt"
            )
            if (System.getenv("CI") == "true") {
                args("-hl", "-t", "2")
            }
            this.additionalConfiguration()
        }
        val capitalizedName = it.nameWithoutExtension.capitalize()
        val graphic by basetask("run${capitalizedName}Graphic")
        runAllGraphic.dependsOn(graphic)
        val batch by basetask("run${capitalizedName}Batch") {
            description = "Launches batch experiments for $capitalizedName"
            jvmArgs(
//                "-XX:+UseCMSInitiatingOccupancyOnly",
//                "-XX:CMSInitiatingOccupancyFraction=90",
//                "-XX:+ScavengeBeforeFullGC",
//                "-XX:+CMSScavengeBeforeRemark",
                "-XX:+UseParallelGC",
                "-XX:+AggressiveHeap"
            )
            val experiment: Experiment = customization[it.nameWithoutExtension] ?: Experiment("")
            val threadCount = maxOf(1, minOf(cpuCount, heap.toInt() / experiment.maxTaskSize ))
            val xmx = minOf(heap.toInt(), Runtime.getRuntime().availableProcessors() * experiment.maxTaskSize)
            maxHeapSize = "${xmx}m"
            File("data").mkdirs()
            args(
                "-e", "data/${it.nameWithoutExtension}",
                "-b",
                "-var", "seed", *experiment.variables,
                "-p", threadCount,
                "-i", experiment.samplingTime
            )
            doFirst {
                println("This batch will be using $xmx MB for the heap, and execute with $threadCount parallel threads")
            }
        }
        runAllBatch.dependsOn(batch)
    }

