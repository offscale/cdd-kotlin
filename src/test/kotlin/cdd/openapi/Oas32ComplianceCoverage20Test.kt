package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

class Oas32ComplianceCoverage20Test {
    @Test
    fun `resolve header and link component refs via parse`() {
        val json = """
        {
            "openapi": "3.2.0",
            "info": { "title": "t", "version": "1" },
            "paths": {
                "/p": {
                    "get": {
                        "responses": {
                            "200": {
                                "description": "ok",
                                "headers": {
                                    "h1": { "${'$'}ref": "#/components/headers/h2", "description": "h1 desc", "x-ext": "v1" }
                                },
                                "links": {
                                    "l1": { "${'$'}ref": "#/components/links/l2", "description": "l1 desc", "x-ext": "v1" }
                                }
                            }
                        }
                    }
                }
            },
            "components": {
                "headers": {
                    "h2": { "${'$'}ref": "#/components/headers/h3" },
                    "h3": { "description": "h3 desc", "schema": { "type": "string" }, "x-ext": "v2" },
                    "cycle1": { "${'$'}ref": "#/components/headers/cycle2" },
                    "cycle2": { "${'$'}ref": "#/components/headers/cycle1" }
                },
                "links": {
                    "l2": { "${'$'}ref": "#/components/links/l3" },
                    "l3": { "operationId": "op", "description": "l3 desc", "x-ext": "v2" },
                    "cycle1": { "${'$'}ref": "#/components/links/cycle2" },
                    "cycle2": { "${'$'}ref": "#/components/links/cycle1" }
                }
            }
        }
        """
        val def = OpenApiParser().parseString(json)
        
        val h1 = def.paths["/p"]?.get?.responses?.get("200")?.headers?.get("h1")
        assertNotNull(h1)
        assertEquals("#/components/headers/h2", h1?.reference?.ref)
        assertEquals("h1 desc", h1?.description) // should have resolved to h3 then overridden by h1
        
        val l1 = def.paths["/p"]?.get?.responses?.get("200")?.links?.get("l1")
        assertNotNull(l1)
        assertEquals("#/components/links/l2", l1?.reference?.ref)
        assertEquals("l1 desc", l1?.description)
        
        // Also force a cycle test
        val def2 = OpenApiParser().parseString("""
        {
            "openapi": "3.2.0",
            "info": { "title": "t", "version": "1" },
            "paths": {
                "/p": {
                    "get": {
                        "responses": {
                            "200": {
                                "description": "ok",
                                "headers": { "h": { "${'$'}ref": "#/components/headers/cycle1" } },
                                "links": { "l": { "${'$'}ref": "#/components/links/cycle1" } }
                            }
                        }
                    }
                }
            },
            "components": {
                "headers": {
                    "cycle1": { "${'$'}ref": "#/components/headers/cycle2" },
                    "cycle2": { "${'$'}ref": "#/components/headers/cycle1" }
                },
                "links": {
                    "cycle1": { "${'$'}ref": "#/components/links/cycle2" },
                    "cycle2": { "${'$'}ref": "#/components/links/cycle1" }
                }
            }
        }
        """)
        assertNotNull(def2)
    }
}
