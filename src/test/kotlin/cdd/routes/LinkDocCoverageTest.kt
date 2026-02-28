package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull

class LinkDocCoverageTest {
    @Test
    fun `linkToDocValue tests`() {
        val gen = NetworkGenerator()
        
        val l1 = Link(
            operationId = "o",
            operationRef = "or",
            parameters = mapOf("p" to "v"),
            requestBody = "b",
            description = "d",
            server = Server(url = "s"),
            extensions = mapOf("x-e" to "v")
        )
        assertNotNull(gen.linkToDocValue(l1))
        
        val l2 = Link(ref = "#/ref", description = "d")
        assertNotNull(gen.linkToDocValue(l2))
        
        val l3 = Link(reference = ReferenceObject(ref = "#/ref2"))
        assertNotNull(gen.linkToDocValue(l3))
    }
}
