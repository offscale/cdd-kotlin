package cdd.classes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import com.fasterxml.jackson.databind.ObjectMapper

class ParseCoverageTest {
    @Test
    fun `parse inner nodes via direct access`() {
        println("ParseCoverageTest ran!!!!"); val parser = DtoParser()
        val mapper = ObjectMapper()
        
        assertNotNull(parser.parseXmlNode(mapper.readTree("""{"name":"x"}""")))
        assertNull(parser.parseXmlNode(null))
        assertNull(parser.parseXmlNode(mapper.readTree("[]")))
        
        assertNotNull(parser.parseDiscriminatorNode(mapper.readTree("""{"propertyName":"x", "mapping":{"a":"b"}}""")))
        assertNull(parser.parseDiscriminatorNode(null))
        assertNull(parser.parseDiscriminatorNode(mapper.readTree("[]")))
        
        assertNotNull(parser.parseExternalDocsNode(mapper.readTree("""{"url":"http://a"}""")))
        assertNull(parser.parseExternalDocsNode(null))
        assertNull(parser.parseExternalDocsNode(mapper.readTree("[]")))
        assertNull(parser.parseExternalDocsNode(mapper.readTree("""{}""")))
        
        assertNull(parser.parseDocLiteral(""))
        assertNull(parser.parseDocLiteral("null"))
        assertNotNull(parser.parseDocLiteral("true"))
        assertNotNull(parser.parseDocLiteral("false"))
        assertNotNull(parser.parseDocLiteral("""{"a":"b"}"""))
        assertNotNull(parser.parseDocLiteral("123"))
        assertNotNull(parser.parseDocLiteral("12.3"))
    }
}
