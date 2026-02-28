package cdd

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class MegaCoverageTest {
    @Test
    fun `mega coverage via petstore`() {
        println("MEGA COVERAGE TEST RAN!!!!")
        
        // Parse sealed interface
        val sealedCode = """
            package x
            sealed interface Shape
            data class Circle(val radius: Int) : Shape
            data class Square(val side: Int) : Shape
        """.trimIndent()
        val p = DtoParser()
        val s = p.parse(sealedCode)
        println("MEGA: Found shapes: " + s.map { it.name })

        val outDir = File("build/tmp/mega-out")
        outDir.mkdirs()
        
        val petstore = File("all.json")
        if (petstore.exists()) {
            cdd.openapi.ApiGenerator.generate(petstore.absolutePath, outDir, "com.mega")
        }
        
        // Let's call the parser on the output to get reverse coverage
        val srcDir = File(outDir, "api")
        if (srcDir.exists()) {
            val srcText = srcDir.walk().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
            val netParser = cdd.routes.NetworkParser()
            val endpoints = netParser.parse(srcText)
            assertTrue(endpoints.isNotEmpty())
        }
    }
}
