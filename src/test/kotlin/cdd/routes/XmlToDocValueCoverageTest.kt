package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull

class XmlToDocValueCoverageTest {
    @Test
    fun `xmlToDocValue test`() {
        val gen = NetworkGenerator()
        val m = NetworkGenerator::class.java.getDeclaredMethod("xmlToDocValue", Xml::class.java)
        m.isAccessible = true
        val result = m.invoke(gen, Xml(name = "n", namespace = "ns", prefix = "p", nodeType = "t", attribute = true, wrapped = true, extensions = mapOf("x-ext" to "v")))
        assertNotNull(result)
        
        val m2 = NetworkGenerator::class.java.getDeclaredMethod("licenseToDocValue", License::class.java)
        m2.isAccessible = true
        m2.invoke(gen, License(name = "n", identifier = "i", url = "u", extensions = mapOf("x-ext" to "v")))
        
        val m3 = NetworkGenerator::class.java.getDeclaredMethod("contactToDocValue", Contact::class.java)
        m3.isAccessible = true
        m3.invoke(gen, Contact(name = "n", url = "u", email = "e", extensions = mapOf("x-ext" to "v")))

        val m4 = NetworkGenerator::class.java.getDeclaredMethod("discriminatorToDocValue", Discriminator::class.java)
        m4.isAccessible = true
        m4.invoke(gen, Discriminator(propertyName = "p", mapping = mapOf("a" to "b"), defaultMapping = "d", extensions = mapOf("x-ext" to "v")))
    }
}
