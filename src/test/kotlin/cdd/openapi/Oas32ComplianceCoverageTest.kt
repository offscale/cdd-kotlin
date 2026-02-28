package cdd.openapi

import org.junit.jupiter.api.Test

class Oas32ComplianceCoverageTest {
    @Test
    fun `mega json parse and validate nested`() {
        val json = """
        {
            "openapi": "3.2.0",
            "info": {
                "title": "All",
                "version": "1.0"
            },
            "jsonSchemaDialect": "https://json-schema.org/draft/2020-12/schema",
            "paths": {},
            "components": {
                "schemas": {
                    "A": {"type": "string"},
                    "Mega": {
                        "type": "object",
                        "properties": {
                            "megaProp": {
                                "${'$'}id": "http://a.com/megaProp",
                                "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                                "${'$'}dynamicAnchor": "metaProp",
                                "${'$'}dynamicRef": "#metaProp",
                                "type": "object",
                                "oneOf": [{"type": "string"}],
                                "anyOf": [{"type": "string"}],
                                "allOf": [{"type": "string"}],
                                "not": {"type": "string"},
                                "if": {"type": "string"},
                                "then": {"type": "string"},
                                "else": {"type": "string"},
                                "properties": {"a": {"type": "string"}},
                                "patternProperties": {"^a": {"type": "string"}},
                                "dependentSchemas": {"a": {"type": "string"}},
                                "items": {"type": "string"},
                                "prefixItems": [{"type": "string"}],
                                "contains": {"type": "string"},
                                "minContains": 1,
                                "maxContains": 2,
                                "unevaluatedItems": {"type": "string"},
                                "unevaluatedProperties": {"type": "string"},
                                "contentSchema": {"type": "string"},
                                "${'$'}defs": {"d": {"type": "string"}},
                                "xml": {"name": "test", "wrapped": true, "attribute": true, "prefix": "ex", "namespace": "http://a"},
                                "discriminator": {"propertyName": "a", "mapping": {"A": "#/components/schemas/A"}},
                                "format": "email",
                                "contentMediaType": "application/json",
                                "contentEncoding": "base64",
                                "readOnly": true,
                                "writeOnly": false,
                                "deprecated": false,
                                "multipleOf": 2.0,
                                "maximum": 10.0,
                                "exclusiveMaximum": 10.0,
                                "minimum": 0.0,
                                "exclusiveMinimum": 0.0,
                                "maxLength": 10,
                                "minLength": 1,
                                "pattern": "^a",
                                "maxItems": 10,
                                "minItems": 1,
                                "uniqueItems": true,
                                "maxProperties": 10,
                                "minProperties": 1,
                                "required": ["a"],
                                "dependentRequired": {"a": ["b"]}
                            }
                        }
                    }
                }
            }
        }
        """
        val def = OpenApiParser().parseString(json)
        val issues = OpenApiValidator().validate(def)
        issues.forEach { println("${it.severity}: ${it.path} -> ${it.message}") }

    }
}
