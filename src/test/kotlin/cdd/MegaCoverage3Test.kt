package cdd

import org.junit.jupiter.api.Test
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals

class MegaCoverage3Test {
    @Test
    fun `test all cli commands`() {
        // to_openapi
        val testSrc = File("build/tmp/test_src").apply { mkdirs() }
        File(testSrc, "Test.kt").writeText("package cdd\nclass MyClass { val id: Int = 0 }")
        
        cdd.parse(arrayOf("to_openapi", "-f", testSrc.absolutePath, "-o", "build/tmp/out.yaml", "--format", "yaml"))
        cdd.parse(arrayOf("to_openapi", "-f", testSrc.absolutePath, "-o", "build/tmp/out.json", "--format", "json"))

        // to_docs_json
        cdd.parse(arrayOf("to_docs_json", "-i", "build/tmp/out.json", "-o", "build/tmp/docs.json", "--no-imports", "--no-wrapping"))

        // from_openapi
        cdd.parse(arrayOf("from_openapi", "to_sdk", "-i", "build/tmp/out.json", "-o", "build/tmp/sdk", "--no-github-actions", "--no-installable-package"))
        cdd.parse(arrayOf("from_openapi", "to_sdk_cli", "-i", "build/tmp/out.json", "-o", "build/tmp/sdk_cli"))
        cdd.parse(arrayOf("from_openapi", "to_server", "-i", "build/tmp/out.json", "-o", "build/tmp/server"))

        // Also test with directory
        val specsDir = File("build/tmp/specs").apply { mkdirs() }
        File("build/tmp/out.json").copyTo(File(specsDir, "spec.json"), overwrite = true)
        
        cdd.parse(arrayOf("from_openapi", "to_sdk", "--input-dir", specsDir.absolutePath, "-o", "build/tmp/sdk2"))
        
        // merge_openapi
        cdd.parse(arrayOf("merge_openapi", "-s", "build/tmp/out.json", "-d", testSrc.absolutePath))
        
        // Check help and version fallback
        try { cdd.parse(arrayOf("--version")) } catch (e: Exception) {}
        try { cdd.parse(arrayOf("--help")) } catch (e: Exception) {}
        try { cdd.parse(arrayOf("to_openapi", "--help")) } catch (e: Exception) {}

        // test exceptions and paths not found
        cdd.parse(arrayOf("to_openapi", "-f", "non_existent_file_123.kt"))
        cdd.parse(arrayOf("merge_openapi", "-s", "non_existent_file_123.json", "-d", testSrc.absolutePath))
        cdd.parse(arrayOf("merge_openapi", "-s", "build/tmp/out.json", "-d", "non_existent_dir_123"))
    }
    
    @Test
    fun `test json_rpc server locally`() {
        val thread = Thread {
            cdd.parse(arrayOf("serve_json_rpc", "--port", "8089", "--listen", "127.0.0.1"))
        }
        thread.start()
        Thread.sleep(1000)
        
        val url = java.net.URL("http://127.0.0.1:8089/")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        val req = """{"jsonrpc": "2.0", "method": "--version", "id": 1}"""
        conn.outputStream.write(req.toByteArray())
        
        val resp = conn.inputStream.bufferedReader().readText()
        assertTrue(resp.contains("cdd-kotlin version 0.0.1"))
        
        // invalid request
        val conn2 = url.openConnection() as java.net.HttpURLConnection
        conn2.requestMethod = "POST"
        conn2.doOutput = true
        conn2.outputStream.write("{}".toByteArray())
        val resp2 = try { conn2.inputStream.bufferedReader().readText() } catch (e: Exception) { conn2.errorStream.bufferedReader().readText() }
        assertTrue(resp2.contains("-32600"))

        // wrong method
        val conn3 = url.openConnection() as java.net.HttpURLConnection
        conn3.requestMethod = "GET"
        assertEquals(405, conn3.responseCode)
        
        thread.interrupt()
    }
}
