package cdd.routes

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import cdd.openapi.*

class CliParserTest {

    @Test
    fun `test parse extracts endpoints from Clikt commands`() {
        val sourceCode = """
            package com.example.cli

            import com.github.ajalt.clikt.core.CliktCommand
            import com.github.ajalt.clikt.parameters.options.option
            import com.github.ajalt.clikt.parameters.options.required
            import com.github.ajalt.clikt.parameters.types.int
            import com.github.ajalt.clikt.parameters.types.double
            import com.github.ajalt.clikt.parameters.types.boolean

            // Should be ignored
            class IgnoredClass {}

            // Should be ignored because name is MainCommand
            class MainCommand : CliktCommand(name = "main") {
                override fun run() {}
            }

            // Should be ignored because name doesn't end with Command
            class NotACommand : CliktCommand(name = "not") {
                override fun run() {}
            }

            // Should be ignored because it's a Tag command (no props, empty run)
            class UserTagCommand : CliktCommand(name = "user", help = "User tag") {
                override fun run() {}
            }

            // Should be parsed
            class GetUserCommand : CliktCommand(name = "getUser", help = "Gets a user") {
                val id by option(help = "The user id").int().required()
                val name by option()
                val active by option(help = "Is active").boolean()
                val rating by option(help = "Rating").double()
                
                override fun run() {
                    val api = UserApi()
                    api.getUser(id, name, active, rating)
                }
            }

            // Should be parsed, without explicit Api call, should default to Default tag
            class CreateItemCommand : CliktCommand(name = "createItem") {
                val itemName by option().required()
                
                override fun run() {
                    println("Creating item")
                }
            }
        """.trimIndent()

        val parser = CliParser()
        val endpoints = parser.parse(sourceCode)

        assertEquals(2, endpoints.size)

        val getUser = endpoints.find { it.operationId == "getUser" }
        assertNotNull(getUser)
        assertEquals("/User/getUser", getUser?.path)
        assertEquals(HttpMethod.POST, getUser?.method)
        assertEquals("Gets a user", getUser?.summary)
        assertEquals(listOf("User"), getUser?.tags)
        
        val params = getUser!!.parameters
        assertEquals(4, params.size)
        
        assertEquals("id", params[0].name)
        assertEquals("integer", params[0].type)
        assertEquals("The user id", params[0].description)
        assertTrue(params[0].isRequired)

        assertEquals("name", params[1].name)
        assertEquals("String", params[1].type)
        assertNull(params[1].description)
        assertFalse(params[1].isRequired)

        assertEquals("active", params[2].name)
        assertEquals("boolean", params[2].type)
        assertEquals("Is active", params[2].description)
        assertFalse(params[2].isRequired)

        assertEquals("rating", params[3].name)
        assertEquals("number", params[3].type)
        assertEquals("Rating", params[3].description)
        assertFalse(params[3].isRequired)

        val createItem = endpoints.find { it.operationId == "createItem" }
        assertNotNull(createItem)
        assertEquals("/Default/createItem", createItem?.path)
        assertEquals("Default", createItem?.tags?.get(0))
        assertNull(createItem?.summary)
        assertEquals(1, createItem?.parameters?.size)
        assertEquals("String", createItem?.parameters?.get(0)?.type)
        assertTrue(createItem?.parameters?.get(0)?.isRequired == true)
    }

    @Test
    fun `test parse ignores class without name in CliktCommand`() {
        val sourceCode = """
            class BadCommand : CliktCommand() {
                override fun run() {}
            }
        """.trimIndent()

        val parser = CliParser()
        val endpoints = parser.parse(sourceCode)

        assertTrue(endpoints.isEmpty())
    }
}
