package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class ServerVariablesCoverageTest {
    @Test
    fun `buildServerVariablesBlock test`() {
        val gen = NetworkGenerator()
        val m = NetworkGenerator::class.java.getDeclaredMethod("buildServerVariablesBlock", List::class.java)
        m.isAccessible = true
        
        val servers = listOf(
            Server(url = "http://{a}", variables = mapOf("a" to ServerVariable(default = "x"))),
            Server(url = "http://{b}", variables = mapOf("b" to ServerVariable(default = "y")))
        )
        
        val block = m.invoke(gen, servers) as String
        println(block)
        
        val emptyBlock = m.invoke(gen, listOf(Server(url="x"))) as String
        assertTrue(emptyBlock.isEmpty())
    }
}
