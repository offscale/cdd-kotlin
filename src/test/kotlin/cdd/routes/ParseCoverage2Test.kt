package cdd.routes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import com.fasterxml.jackson.databind.ObjectMapper

class ParseCoverage2Test {
    @Test
    fun `parse minor object nodes in NetworkParser`() {
        val json = """
        {
            "discriminator": { "propertyName": "p", "mapping": {"a": "b"}, "defaultMapping": "a", "x-ext": "v" },
            "header": { "description": "d", "required": true, "deprecated": false, "style": "simple", "explode": false },
            "headerRef": { "${'$'}ref": "ref", "summary": "s", "description": "d" },
            "xml": { "name": "n", "namespace": "ns", "prefix": "p", "nodeType": "t", "attribute": true, "wrapped": true, "x-ext": "v" },
            "license": { "name": "n", "identifier": "i", "url": "u", "x-ext": "v" },
            "licenseNoName": { "identifier": "i", "url": "u", "x-ext": "v" },
            "contact": { "name": "n", "url": "u", "email": "e", "x-ext": "v" },
            "parameter": { "name": "n", "in": "query", "style": "form", "explode": true },
            "parameterRef": { "${'$'}ref": "ref", "summary": "s", "description": "d" }
        }
        """
        val node = ObjectMapper().readTree(json)
        val parser = NetworkParser()
        
        val methods = NetworkParser::class.java.declaredMethods
        
        val parseDiscriminatorNode = methods.first { it.name == "parseDiscriminatorNode" }.apply { isAccessible = true }
        assertNotNull(parseDiscriminatorNode.invoke(parser, node.get("discriminator")))
        assertNull(parseDiscriminatorNode.invoke(parser, null))
        assertNull(parseDiscriminatorNode.invoke(parser, ObjectMapper().createArrayNode()))

        val parseHeaderNode = methods.first { it.name == "parseHeaderNode" }.apply { isAccessible = true }
        assertNotNull(parseHeaderNode.invoke(parser, node.get("header")))
        assertNotNull(parseHeaderNode.invoke(parser, node.get("headerRef")))
        
        val parseXmlNode = methods.first { it.name == "parseXmlNode" }.apply { isAccessible = true }
        assertNotNull(parseXmlNode.invoke(parser, node.get("xml")))
        assertNull(parseXmlNode.invoke(parser, null))

        val parseLicenseNode = methods.first { it.name == "parseLicenseNode" }.apply { isAccessible = true }
        assertNotNull(parseLicenseNode.invoke(parser, node.get("license")))
        assertNull(parseLicenseNode.invoke(parser, node.get("licenseNoName")))
        assertNull(parseLicenseNode.invoke(parser, null))

        val parseContactNode = methods.first { it.name == "parseContactNode" }.apply { isAccessible = true }
        assertNotNull(parseContactNode.invoke(parser, node.get("contact")))
        assertNull(parseContactNode.invoke(parser, null))
        
        val parseParameterStyle = methods.first { it.name == "parseParameterStyle" }.apply { isAccessible = true }
        assertNotNull(parseParameterStyle.invoke(parser, "matrix"))
        assertNotNull(parseParameterStyle.invoke(parser, "label"))
        assertNotNull(parseParameterStyle.invoke(parser, "simple"))
        assertNotNull(parseParameterStyle.invoke(parser, "form"))
        assertNotNull(parseParameterStyle.invoke(parser, "cookie"))
        assertNotNull(parseParameterStyle.invoke(parser, "spaceDelimited"))
        assertNotNull(parseParameterStyle.invoke(parser, "pipeDelimited"))
        assertNotNull(parseParameterStyle.invoke(parser, "deepObject"))
        assertNull(parseParameterStyle.invoke(parser, "bad"))
    }
}
