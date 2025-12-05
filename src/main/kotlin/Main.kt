import scaffold.ScaffoldGenerator
import java.io.File

fun main() {
    val generator = ScaffoldGenerator()

    // We generate the scaffold into a folder named "generated-project" inside this project
    // just for demonstration purposes.
    val outputDir = File("generated-project")

    println("Generating KMP Scaffold into: ${outputDir.absolutePath}...")

    try {
        generator.generate(
            outputDirectory = outputDir,
            projectName = "MyGeneratedApp",
            packageName = "com.example.auto"
        )
        println("Success! Open 'generated-project' in IntelliJ to see the result.")
    } catch (e: Exception) {
        println("Failed to generate scaffold:")
        e.printStackTrace()
    }
}
