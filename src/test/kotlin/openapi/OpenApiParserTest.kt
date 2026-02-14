package openapi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import domain.Callback
import domain.HttpMethod
import domain.ParameterLocation
import domain.ParameterStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class OpenApiParserTest {

    private val parser = OpenApiParser()
    private val writer = OpenApiWriter()

    @Test
    fun `parse JSON OpenAPI into IR`() {
        val json = """
            {
              "openapi": "3.2.0",
              "x-root": "root-ext",
              "${"$"}self": "https://example.com/api/openapi",
              "info": {
                "title": "Pet API",
                "version": "1.0",
                "summary": "Summary",
                "description": "Description",
                "termsOfService": "https://example.com/tos",
                "contact": { "name": "Support", "url": "https://example.com", "email": "support@example.com" },
                "license": { "name": "Apache-2.0", "identifier": "Apache-2.0" },
                "x-info": "info-ext"
              },
              "jsonSchemaDialect": "https://spec.openapis.org/oas/3.1/dialect/base",
              "servers": [
                {
                  "url": "https://api.example.com/{version}",
                  "description": "Primary",
                  "name": "prod",
                  "variables": {
                    "version": { "default": "v1", "enum": ["v1", "v2"], "description": "API version" }
                  }
                }
              ],
              "paths": {
                "x-paths": "paths-ext",
                "/users/{id}": {
                  "summary": "User path",
                  "description": "User ops",
                  "x-path": true,
                  "parameters": [
                    {
                      "name": "id",
                      "in": "path",
                      "required": true,
                      "schema": { "type": "string", "minLength": 1 },
                      "example": null
                    },
                    { "${"$"}ref": "#/components/parameters/limit", "summary": "Limit summary", "description": "Limit override" },
                    { "${"$"}ref": "#/components/parameters/MissingParam" },
                    { "name": "headerParam", "in": "header", "schema": { "type": "string" }, "style": "SIMPLE" }
                  ],
                  "get": {
                    "operationId": "getUser",
                    "summary": "Get user",
                    "description": "Fetch user",
                    "tags": ["users"],
                    "deprecated": true,
                    "x-op": { "flag": true },
                    "parameters": [
                      { "${"$"}ref": "#/components/parameters/limit" }
                    ],
                    "requestBody": { "${"$"}ref": "#/components/requestBodies/CreateUser", "description": "Body override" },
                    "responses": {
                      "200": {
                        "description": "ok",
                        "headers": {
                          "X-Rate-Limit": { "schema": { "type": "integer" }, "required": true },
                          "X-Trace": { "${"$"}ref": "#/components/headers/X-Trace", "description": "Trace override" },
                          "X-Missing": { "${"$"}ref": "#/components/headers/Unknown" }
                        },
                        "content": {
                          "application/json": { "schema": { "${"$"}ref": "#/components/schemas/User" } }
                        },
                        "links": {
                          "UserLink": {
                            "operationId": "getUser",
                            "parameters": {
                              "userId": "${"$"}response.body#/id",
                              "priority": 3,
                              "flags": [true, false],
                              "meta": { "source": "test" }
                            },
                            "requestBody": { "id": 1 }
                          },
                          "AltLink": { "${"$"}ref": "#/components/links/UserLink", "summary": "Alt summary", "description": "Alt desc" }
                        }
                      },
                      "404": { "${"$"}ref": "#/components/responses/Unknown", "summary": "Missing", "description": "Missing response" },
                      "default": { "${"$"}ref": "#/components/responses/NotFound" }
                    },
                    "callbacks": {
                      "OnEvent": {
                        "${"$"}request.body#/url": {
                          "post": { "operationId": "onEvent", "responses": { "200": { "description": "ok" } } }
                        }
                      }
                    },
                    "security": [ { "api_key": [] } ],
                    "servers": [ { "url": "https://override.example.com" } ]
                  },
                  "post": {
                    "responses": { "201": { "description": "created" } },
                    "requestBody": { "${"$"}ref": "#/components/requestBodies/MissingBody" }
                  },
                  "put": {
                    "requestBody": {
                      "content": {
                        "text/plain": { "schema": { "type": "string" } }
                      }
                    },
                    "responses": { "200": { "description": "updated" } }
                  },
                  "additionalOperations": {
                    "COPY": {
                      "operationId": "copyUser",
                      "responses": { "204": { "description": "copied" } }
                    }
                  },
                  "LOCK": {
                    "operationId": "lockUser",
                    "responses": { "200": { "description": "locked" } }
                  },
                  "x-note": { "operationId": "ignored", "responses": { "200": { "description": "ignored" } } },
                  "customString": "skip"
                },
                "/ref": {
                  "${"$"}ref": "#/components/pathItems/UsersPath",
                  "summary": "Ref summary",
                  "parameters": [
                    { "name": "refParam", "in": "query", "schema": { "type": "string" } }
                  ]
                }
              },
              "webhooks": {
                "x-webhooks": "hook-ext",
                "newPet": {
                  "post": {
                    "operationId": "onNewPet",
                    "responses": { "200": { "description": "ok" } }
                  }
                }
              },
              "components": {
                "schemas": {
                "User": {
                  "type": "object",
                  "${"$"}id": "https://example.com/schemas/User",
                  "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
                  "${"$"}anchor": "user",
                  "${"$"}dynamicAnchor": "userDyn",
                  "description": "User schema",
                  "x-schema": 7,
                  "required": ["id"],
                  "minProperties": 1,
                  "maxProperties": 4,
                  "properties": {
                      "id": { "type": "integer", "format": "int64", "minimum": 1, "maximum": 9, "x-prop": "prop-ext" },
                      "name": { "type": ["string", "null"], "maxLength": 20, "pattern": "^[a-z]+${"$"}" },
                      "xmlAttr": { "type": "string", "xml": { "name": "xmlAttr", "attribute": true } },
                      "address": { "${"$"}ref": "#/components/schemas/User" },
                      "status": { "type": "string", "enum": ["active", "disabled"] },
                      "pet": {
                        "oneOf": [
                          { "type": "string" },
                          { "type": "integer" }
                        ]
                      },
                      "settings": {
                        "type": "object",
                        "required": ["timezone"],
                        "properties": {
                          "timezone": { "type": "string" }
                        },
                        "not": { "type": "null" }
                      },
                      "profile": {
                        "type": "object",
                        "externalDocs": { "url": "https://docs.example.com/profile", "description": "Profile docs" },
                        "discriminator": {
                          "propertyName": "kind",
                          "mapping": { "user": "#/components/schemas/User" },
                          "defaultMapping": "#/components/schemas/User"
                        }
                      },
                      "tags": {
                        "type": "array",
                        "items": { "type": "string" },
                        "examples": ["alpha", "beta"]
                      }
                    }
                  },
                  "Tuple": {
                    "type": "array",
                    "prefixItems": [
                      { "type": "string" },
                      { "type": "integer" }
                    ],
                    "contains": { "type": "string" },
                    "minContains": 1,
                    "maxContains": 2
                  },
                  "Flags": {
                    "type": "array",
                    "items": { "type": "string" },
                    "minItems": 1,
                    "maxItems": 5,
                    "uniqueItems": true,
                    "examples": ["a", "b"]
                  },
                  "AnyMap": {
                    "type": "object",
                    "additionalProperties": true
                  },
                  "Bag": {
                    "type": "object",
                    "additionalProperties": { "type": "integer" }
                  },
                  "NoTypeObject": {
                    "properties": { "x": { "type": "string" } }
                  },
                  "NoTypeArray": {
                    "items": { "type": "string" }
                  },
                  "NoTypeEnum": {
                    "enum": ["A", "B"],
                    "default": 7,
                    "const": true
                  },
                  "NoTypeDefault": {
                    "example": { "x": 1 }
                  },
                  "Pet": {
                    "oneOf": [
                      { "${"$"}ref": "#/components/schemas/User" },
                      { "title": "InlineTitle" },
                      { "type": "string" },
                      { }
                    ],
                    "anyOf": [
                      { "title": "AnyTitle" }
                    ],
                    "allOf": [
                      { "type": "string" }
                    ],
                    "discriminator": {
                      "propertyName": "petType",
                      "mapping": { "dog": "#/components/schemas/User" }
                    },
                    "xml": { "name": "Pet", "nodeType": "element" }
                  },
                  "WeirdProps": {
                    "type": "object",
                    "additionalProperties": []
                  },
                  "ExampleMap": {
                    "type": "object",
                    "examples": { "first": 1, "second": "two" }
                  }
                },
                "parameters": {
                  "limit": {
                    "name": "limit",
                    "in": "query",
                    "schema": { "type": "integer" },
                    "required": false,
                    "style": "form",
                    "explode": false,
                    "allowReserved": true
                  },
                  "matrixParam": {
                    "name": "matrix",
                    "in": "path",
                    "schema": { "type": "string" },
                    "style": "matrix"
                  },
                  "labelParam": {
                    "name": "label",
                    "in": "path",
                    "schema": { "type": "string" },
                    "style": "label"
                  },
                  "simpleHeader": {
                    "name": "X-Simple",
                    "in": "header",
                    "schema": { "type": "string" },
                    "style": "simple"
                  },
                  "spaceParam": {
                    "name": "space",
                    "in": "query",
                    "schema": { "type": "array", "items": { "type": "string" } },
                    "style": "spaceDelimited"
                  },
                  "pipeParam": {
                    "name": "pipe",
                    "in": "query",
                    "schema": { "type": "array", "items": { "type": "string" } },
                    "style": "pipeDelimited"
                  },
                  "deepParam": {
                    "name": "deep",
                    "in": "query",
                    "schema": { "type": "object" },
                    "style": "deepObject"
                  },
                  "cookieParam": {
                    "name": "cookie",
                    "in": "cookie",
                    "schema": { "type": "string" },
                    "style": "FORM"
                  },
                  "cookieStyleParam": {
                    "name": "cookieStyle",
                    "in": "cookie",
                    "schema": { "type": "string" },
                    "style": "cookie"
                  },
                  "defaultParam": {
                    "name": "default",
                    "schema": { "type": "string" },
                    "style": "unknown"
                  }
                },
                "responses": {
                  "NotFound": { "description": "missing" }
                },
                "requestBodies": {
                  "CreateUser": {
                    "description": "create",
                    "content": {
                      "application/json": {
                        "schema": { "${"$"}ref": "#/components/schemas/User" },
                        "example": [1, true],
                        "examples": {
                          "payload": { "dataValue": { "id": 1 }, "serializedValue": "{\"id\":1}" },
                          "nullPayload": { "dataValue": null }
                        }
                      }
                    }
                  },
                  "RefBody": { "${"$"}ref": "#/components/requestBodies/CreateUser" }
                },
                "headers": {
                  "X-Trace": { "schema": { "type": "string" } }
                },
                "examples": {
                  "UserExample": { "summary": "ex", "dataValue": { "id": 1 } },
                  "RefExample": { "${"$"}ref": "https://example.com/example.json" },
                  "SimpleExample": "raw-string",
                  "BoolExample": { "value": true },
                  "NullExample": { "dataValue": null }
                },
                "links": {
                  "UserLink": { "operationId": "getUser", "server": { "url": "https://link.example.com" } }
                },
                "callbacks": {
                  "OnEvent": {
                    "x-cb": "cb-ext",
                    "${"$"}request.body#/url": {
                      "post": { "operationId": "onEvent", "responses": { "200": { "description": "ok" } } }
                    }
                  },
                  "RefCallback": {
                    "${"$"}ref": "#/components/callbacks/OnEvent",
                    "summary": "Ref cb",
                    "description": "Ref cb desc"
                  }
                },
                "pathItems": {
                  "UsersPath": {
                    "get": { "operationId": "getUsers", "responses": { "200": { "description": "ok" } } }
                  }
                },
                "mediaTypes": {
                  "multipart/form-data": {
                    "schema": { "type": "object" },
                    "itemSchema": { "type": "string" },
                    "examples": {
                      "raw": { "serializedValue": "file=abc" }
                    },
                    "encoding": {
                      "file": {
                        "contentType": "image/png",
                        "headers": {
                          "X-Rate-Limit": { "schema": { "type": "integer" } },
                          "X-Ref": { "${"$"}ref": "#/components/headers/Unknown" }
                        },
                        "style": "form",
                        "explode": true,
                        "allowReserved": true,
                        "encoding": {
                          "nested": { "contentType": "text/plain" }
                        },
                        "prefixEncoding": [
                          { "contentType": "application/json" }
                        ],
                        "itemEncoding": { "contentType": "text/plain" }
                      }
                    },
                    "prefixEncoding": [
                      { "contentType": "application/json" }
                    ],
                    "itemEncoding": { "contentType": "text/plain" }
                  },
                  "text/plain": {
                    "${"$"}ref": "#/components/mediaTypes/multipart/form-data",
                    "summary": "MediaType ref",
                    "description": "MediaType ref desc"
                  }
                },
                "securitySchemes": {
                  "api_key": {
                    "type": "apiKey",
                    "name": "X-API-KEY",
                    "in": "header",
                    "deprecated": true
                  },
                  "RefScheme": {
                    "${"$"}ref": "#/components/securitySchemes/api_key",
                    "description": "ref scheme"
                  }
                }
              },
              "security": [ { "api_key": [] } ],
              "tags": [
                { "name": "users", "summary": "Users", "description": "User ops", "kind": "nav", "externalDocs": { "description": "missing url" } }
              ],
              "externalDocs": { "description": "more", "url": "https://docs.example.com" }
            }
        """.trimIndent()

        val root = parser.parseString(json, OpenApiParser.Format.JSON)

        assertEquals("3.2.0", root.openapi)
        assertEquals("https://example.com/api/openapi", root.self)
        assertEquals("Pet API", root.info.title)
        assertEquals("root-ext", root.extensions["x-root"])
        assertEquals("info-ext", root.info.extensions["x-info"])
        assertEquals("https://spec.openapis.org/oas/3.1/dialect/base", root.jsonSchemaDialect)
        assertEquals("https://api.example.com/{version}", root.servers.first().url)
        assertEquals("v1", root.servers.first().variables?.get("version")?.default)

        val pathItem = root.paths["/users/{id}"]
        assertNotNull(pathItem)
        assertEquals("User path", pathItem?.summary)
        assertEquals(true, pathItem?.extensions?.get("x-path"))

        val getOp = pathItem?.get
        assertEquals("getUser", getOp?.operationId)
        assertEquals(HttpMethod.GET, getOp?.method)
        assertTrue(getOp?.deprecated == true)
        val opExt = getOp?.extensions?.get("x-op") as? Map<*, *>
        assertEquals(true, opExt?.get("flag"))
        assertTrue(getOp?.parameters?.any { it.name == "limit" && it.location == ParameterLocation.QUERY } == true)
        assertTrue(pathItem?.parameters?.any { it.name == "MissingParam" } == true)
        val pathLimitParam = pathItem?.parameters?.firstOrNull { it.reference?.ref == "#/components/parameters/limit" }
        assertEquals("Limit summary", pathLimitParam?.reference?.summary)
        assertEquals("Limit override", pathLimitParam?.description)
        assertEquals("#/components/requestBodies/CreateUser", getOp?.requestBody?.reference?.ref)
        assertEquals("Body override", getOp?.requestBody?.description)
        val traceHeader = getOp?.responses?.get("200")?.headers?.get("X-Trace")
        assertEquals("#/components/headers/X-Trace", traceHeader?.reference?.ref)
        assertEquals("Trace override", traceHeader?.description)
        val missingResponse = getOp?.responses?.get("404")
        assertEquals("#/components/responses/Unknown", missingResponse?.reference?.ref)
        assertEquals("Missing", missingResponse?.reference?.summary)
        assertEquals("Missing response", missingResponse?.description)

        val copyOp = pathItem?.additionalOperations?.get("COPY")
        assertNotNull(copyOp)
        assertEquals(HttpMethod.CUSTOM, copyOp?.method)
        assertEquals("copyUser", copyOp?.operationId)

        val lockOp = pathItem?.additionalOperations?.get("LOCK")
        assertNotNull(lockOp)
        assertEquals("lockUser", lockOp?.operationId)
        assertTrue(pathItem?.additionalOperations?.containsKey("x-note") == false)
        assertTrue(pathItem?.additionalOperations?.containsKey("customString") == false)

        val refPath = root.paths["/ref"]
        assertEquals("#/components/pathItems/UsersPath", refPath?.ref)
        assertEquals("Ref summary", refPath?.summary)
        assertTrue(refPath?.parameters?.any { it.name == "refParam" && it.location == ParameterLocation.QUERY } == true)
        assertEquals("paths-ext", root.pathsExtensions["x-paths"])
        assertTrue(root.paths.containsKey("x-paths") == false)
        assertEquals("hook-ext", root.webhooksExtensions["x-webhooks"])
        assertTrue(root.webhooks.containsKey("x-webhooks") == false)

        val userSchema = root.components?.schemas?.get("User")
        assertEquals("User schema", userSchema?.description)
        assertEquals("https://example.com/schemas/User", userSchema?.schemaId)
        assertEquals("https://json-schema.org/draft/2020-12/schema", userSchema?.schemaDialect)
        assertEquals("user", userSchema?.anchor)
        assertEquals("userDyn", userSchema?.dynamicAnchor)
        assertEquals(7, userSchema?.extensions?.get("x-schema"))
        assertEquals(1, userSchema?.minProperties)
        assertEquals(4, userSchema?.maxProperties)
        assertTrue(userSchema?.properties?.get("name")?.types?.contains("null") == true)
        assertEquals(20, userSchema?.properties?.get("name")?.maxLength)
        assertEquals("#/components/schemas/User", userSchema?.properties?.get("address")?.ref)
        assertEquals("prop-ext", userSchema?.properties?.get("id")?.extensions?.get("x-prop"))
        assertTrue(userSchema?.properties?.get("xmlAttr")?.xml?.attribute == true)
        assertEquals(listOf("active", "disabled"), userSchema?.properties?.get("status")?.enumValues)
        assertEquals(2, userSchema?.properties?.get("pet")?.oneOf?.size)
        val profile = userSchema?.properties?.get("profile")
        assertEquals("https://docs.example.com/profile", profile?.externalDocs?.url)
        assertEquals("Profile docs", profile?.externalDocs?.description)
        assertEquals("kind", profile?.discriminator?.propertyName)
        assertEquals("#/components/schemas/User", profile?.discriminator?.mapping?.get("user"))
        assertEquals("#/components/schemas/User", profile?.discriminator?.defaultMapping)
        val settingsSchema = userSchema?.properties?.get("settings")
        assertEquals(listOf("timezone"), settingsSchema?.required)
        assertEquals("string", settingsSchema?.properties?.get("timezone")?.types?.firstOrNull())
        assertTrue(settingsSchema?.not?.types?.contains("null") == true)
        assertEquals(listOf("alpha", "beta"), userSchema?.properties?.get("tags")?.examples)

        val petSchema = root.components?.schemas?.get("Pet")
        assertEquals(listOf("#/components/schemas/User"), petSchema?.oneOf)
        assertEquals(3, petSchema?.oneOfSchemas?.size)
        assertEquals("InlineTitle", petSchema?.oneOfSchemas?.get(0)?.title)
        assertEquals("string", petSchema?.oneOfSchemas?.get(1)?.type)
        assertEquals("string", petSchema?.oneOfSchemas?.get(2)?.type)
        assertTrue(petSchema?.anyOf?.isEmpty() == true)
        assertEquals(1, petSchema?.anyOfSchemas?.size)
        assertEquals("AnyTitle", petSchema?.anyOfSchemas?.get(0)?.title)
        assertTrue(petSchema?.allOf?.isEmpty() == true)
        assertEquals(1, petSchema?.allOfSchemas?.size)
        assertEquals("string", petSchema?.allOfSchemas?.get(0)?.type)

        val tupleSchema = root.components?.schemas?.get("Tuple")
        assertEquals(2, tupleSchema?.prefixItems?.size)
        assertTrue(tupleSchema?.contains?.types?.contains("string") == true)
        assertEquals(1, tupleSchema?.minContains)
        assertEquals(2, tupleSchema?.maxContains)

        val flags = root.components?.schemas?.get("Flags")
        assertEquals("a", flags?.examples?.get("example1"))
        assertEquals(listOf("a", "b"), flags?.examplesList)
        assertTrue(flags?.uniqueItems == true)

        val anyMap = root.components?.schemas?.get("AnyMap")
        assertNotNull(anyMap?.additionalProperties)

        val bag = root.components?.schemas?.get("Bag")
        assertTrue(bag?.additionalProperties?.types?.contains("integer") == true)

        val weird = root.components?.schemas?.get("WeirdProps")
        assertNull(weird?.additionalProperties)

        val exampleMap = root.components?.schemas?.get("ExampleMap")
        assertEquals(1, exampleMap?.examples?.get("first"))

        val noTypeEnum = root.components?.schemas?.get("NoTypeEnum")
        assertEquals(7, noTypeEnum?.defaultValue)
        assertEquals(true, noTypeEnum?.constValue)

        val noTypeDefault = root.components?.schemas?.get("NoTypeDefault")
        val noTypeExample = noTypeDefault?.example as? Map<*, *>
        assertEquals(1, noTypeExample?.get("x"))

        val apiKey = root.components?.securitySchemes?.get("api_key")
        assertTrue(apiKey?.deprecated == true)
        val refScheme = root.components?.securitySchemes?.get("RefScheme")
        assertEquals("#/components/securitySchemes/api_key", refScheme?.reference?.ref)
        assertEquals("ref scheme", refScheme?.reference?.description)

        val mediaType = root.components?.mediaTypes?.get("multipart/form-data")
        assertNotNull(mediaType?.encoding?.get("file")?.itemEncoding)
        assertEquals("file=abc", mediaType?.examples?.get("raw")?.serializedValue)
        assertEquals("String", mediaType?.encoding?.get("file")?.headers?.get("X-Ref")?.type)

        val mediaRef = root.components?.mediaTypes?.get("text/plain")
        assertEquals("#/components/mediaTypes/multipart/form-data", mediaRef?.reference?.ref)
        assertEquals("MediaType ref", mediaRef?.reference?.summary)
        assertEquals("MediaType ref desc", mediaRef?.reference?.description)

        val refExample = root.components?.examples?.get("RefExample")
        assertEquals("https://example.com/example.json", refExample?.ref)

        val simpleExample = root.components?.examples?.get("SimpleExample")
        assertEquals("raw-string", simpleExample?.value)

        val nullExample = root.components?.examples?.get("NullExample")
        assertTrue(nullExample?.dataValue == null)

        val onEventCallback = root.components?.callbacks?.get("OnEvent") as? Callback.Inline
        assertEquals("cb-ext", onEventCallback?.extensions?.get("x-cb"))
        assertTrue(onEventCallback?.expressions?.containsKey("\$request.body#/url") == true)
        val refCallback = root.components?.callbacks?.get("RefCallback") as? Callback.Inline
        assertEquals("#/components/callbacks/OnEvent", refCallback?.reference?.ref)
        assertEquals("Ref cb", refCallback?.reference?.summary)
        assertEquals("Ref cb desc", refCallback?.reference?.description)
        assertTrue(refCallback?.expressions?.containsKey("\$request.body#/url") == true)

        val matrixParam = root.components?.parameters?.get("matrixParam")
        assertEquals(ParameterStyle.MATRIX, matrixParam?.style)

        val cookieParam = root.components?.parameters?.get("cookieParam")
        assertEquals(ParameterLocation.COOKIE, cookieParam?.location)
        assertEquals(ParameterStyle.FORM, cookieParam?.style)

        val cookieStyleParam = root.components?.parameters?.get("cookieStyleParam")
        assertEquals(ParameterLocation.COOKIE, cookieStyleParam?.location)
        assertEquals(ParameterStyle.COOKIE, cookieStyleParam?.style)

        val defaultParam = root.components?.parameters?.get("defaultParam")
        assertEquals(ParameterLocation.QUERY, defaultParam?.location)
        assertNull(defaultParam?.style)

        val response404 = getOp?.responses?.get("404")
        assertEquals("Missing response", response404?.description)

        val response200 = getOp?.responses?.get("200")
        assertEquals("String", response200?.headers?.get("X-Missing")?.type)
        val userLink = response200?.links?.get("UserLink")
        assertEquals("getUser", userLink?.operationId)
        assertEquals("\$response.body#/id", userLink?.parameters?.get("userId"))
        assertEquals(3, userLink?.parameters?.get("priority"))
        assertEquals(listOf(true, false), userLink?.parameters?.get("flags"))
        assertEquals(mapOf("source" to "test"), userLink?.parameters?.get("meta"))
        assertEquals(mapOf("id" to 1), userLink?.requestBody)

        val altLink = response200?.links?.get("AltLink")
        assertEquals("#/components/links/UserLink", altLink?.ref)
        assertEquals("Alt summary", altLink?.reference?.summary)
        assertEquals("Alt desc", altLink?.reference?.description)

        val postOp = pathItem?.post
        assertEquals("#/components/requestBodies/MissingBody", postOp?.requestBody?.reference?.ref)

        val putOp = pathItem?.put
        assertEquals("String", putOp?.requestBodyType)

        val webhook = root.webhooks["newPet"]
        assertEquals("onNewPet", webhook?.post?.operationId)

        val tag = root.tags.first()
        assertNull(tag.externalDocs)
    }

    @Test
    fun `parse preserves explicit empty response content`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Ping", "version": "1.0" },
              "paths": {
                "/ping": {
                  "get": {
                    "responses": {
                      "200": { "description": "ok", "content": {} }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val response = definition.paths["/ping"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertTrue(response!!.content.isEmpty())
        assertTrue(response.contentPresent)
    }

    @Test
    fun `parse resolves callback refs while preserving ref metadata`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Callbacks", "version": "1.0" },
              "components": {
                "callbacks": {
                  "OnEvent": {
                    "{${'$'}request.body#/url}": {
                      "post": { "responses": { "200": { "description": "ok" } } }
                    }
                  }
                }
              },
              "paths": {
                "/subscribe": {
                  "post": {
                    "responses": { "200": { "description": "ok" } },
                    "callbacks": {
                      "onEvent": { "${'$'}ref": "#/components/callbacks/OnEvent" }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val callback = definition.paths["/subscribe"]?.post?.callbacks?.get("onEvent")
        assertTrue(callback is Callback.Inline)
        val inline = callback as Callback.Inline
        assertEquals("#/components/callbacks/OnEvent", inline.reference?.ref)
        assertTrue(inline.expressions.containsKey("{${'$'}request.body#/url}"))

        val serialized = writer.writeJson(definition)
        val root = ObjectMapper(JsonFactory()).readTree(serialized)
        val callbackNode = root["paths"]["/subscribe"]["post"]["callbacks"]["onEvent"]
        assertEquals("#/components/callbacks/OnEvent", callbackNode["${'$'}ref"].asText())
    }

    @Test
    fun `parse resolves component response refs with absolute uri`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Pets", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "responses": {
                      "200": { "${"$"}ref": "https://example.com/openapi.json#/components/responses/Ok" }
                    }
                  }
                }
              },
              "components": {
                "responses": {
                  "Ok": { "description": "ok" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json)
        val response = definition.paths["/pets"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertEquals("ok", response?.description)
    }

    @Test
    fun `parse resolves component response refs with self base match`() {
        val json = """
            {
              "openapi": "3.2.0",
              "${"$"}self": "https://example.com/openapi.json",
              "info": { "title": "Pets", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "responses": {
                      "200": { "${"$"}ref": "https://example.com/openapi.json#/components/responses/Ok" }
                    }
                  }
                }
              },
              "components": {
                "responses": {
                  "Ok": { "description": "ok" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json)
        val response = definition.paths["/pets"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertEquals("ok", response?.description)
    }

    @Test
    fun `parse resolves component response refs using base uri when self missing`() {
        val ref = "https://example.com/openapi.json#/components/responses/Ok"
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Pets", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "responses": {
                      "200": { "${"$"}ref": "$ref" }
                    }
                  }
                }
              },
              "components": {
                "responses": {
                  "Ok": { "description": "ok" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, baseUri = "https://example.com/openapi.json")
        val response = definition.paths["/pets"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertEquals("ok", response?.description)
    }

    @Test
    fun `parse resolves component response refs when self is relative and base uri provided`() {
        val ref = "https://example.com/api/openapi.json#/components/responses/Ok"
        val json = """
            {
              "openapi": "3.2.0",
              "${"$"}self": "/api/openapi.json",
              "info": { "title": "Pets", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "responses": {
                      "200": { "${"$"}ref": "$ref" }
                    }
                  }
                }
              },
              "components": {
                "responses": {
                  "Ok": { "description": "ok" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, baseUri = "https://example.com/root/openapi.json")
        val response = definition.paths["/pets"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertEquals("ok", response?.description)
    }

    @Test
    fun `parse does not resolve component response refs when base uri mismatches`() {
        val ref = "https://example.com/openapi.json#/components/responses/Ok"
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Pets", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "responses": {
                      "200": { "${"$"}ref": "$ref" }
                    }
                  }
                }
              },
              "components": {
                "responses": {
                  "Ok": { "description": "ok" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, baseUri = "https://other.example.com/openapi.json")
        val response = definition.paths["/pets"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertEquals("ref:$ref", response?.description)
    }

    @Test
    fun `parse does not resolve component response refs when self base mismatches`() {
        val ref = "https://other.example.com/openapi.json#/components/responses/Ok"
        val json = """
            {
              "openapi": "3.2.0",
              "${"$"}self": "https://example.com/openapi.json",
              "info": { "title": "Pets", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "responses": {
                      "200": { "${"$"}ref": "$ref" }
                    }
                  }
                }
              },
              "components": {
                "responses": {
                  "Ok": { "description": "ok" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json)
        val response = definition.paths["/pets"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertEquals("ref:$ref", response?.description)
    }

    @Test
    fun `parse resolves component response refs via registry`() {
        val sharedJson = """
            {
              "openapi": "3.2.0",
              "${"$"}self": "https://example.com/shared.json",
              "info": { "title": "Shared", "version": "1.0" },
              "components": {
                "responses": {
                  "Ok": { "description": "shared ok" }
                }
              }
            }
        """.trimIndent()

        val sharedDefinition = parser.parseString(sharedJson)
        val registry = OpenApiDocumentRegistry()
        registry.registerOpenApi(sharedDefinition)

        val mainJson = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Main", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "responses": {
                      "200": { "${"$"}ref": "https://example.com/shared.json#/components/responses/Ok" }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(mainJson, registry = registry)
        val response = definition.paths["/pets"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertEquals("shared ok", response?.description)
    }

    @Test
    fun `parse resolves relative component refs via registry`() {
        val sharedJson = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Shared", "version": "1.0" },
              "components": {
                "parameters": {
                  "limit": {
                    "name": "limit",
                    "in": "query",
                    "schema": { "type": "integer" }
                  }
                }
              }
            }
        """.trimIndent()

        val sharedDefinition = parser.parseString(sharedJson)
        val registry = OpenApiDocumentRegistry()
        val sharedBase = "https://example.com/root/shared/common.json"
        registry.registerOpenApi(sharedDefinition, baseUri = sharedBase)

        val mainJson = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Main", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "parameters": [
                      { "${"$"}ref": "shared/common.json#/components/parameters/limit" }
                    ],
                    "responses": {
                      "200": { "description": "ok" }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(
            mainJson,
            baseUri = "https://example.com/root/openapi.json",
            registry = registry
        )
        val param = definition.paths["/pets"]?.get?.parameters?.firstOrNull()
        assertNotNull(param)
        assertEquals("limit", param?.name)
        assertEquals(ParameterLocation.QUERY, param?.location)
    }

    @Test
    fun `parse resolves component response refs with percent encoded key`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Pets", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "responses": {
                      "200": { "${"$"}ref": "#/components/responses/Ok%20Response" }
                    }
                  }
                }
              },
              "components": {
                "responses": {
                  "Ok Response": { "description": "ok" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json)
        val response = definition.paths["/pets"]?.get?.responses?.get("200")
        assertNotNull(response)
        assertEquals("ok", response?.description)
    }

    @Test
    fun `parseDocumentString returns Schema document when root is schema`() {
        val schemaJson = """{ "type": "string", "minLength": 1 }"""
        val doc = parser.parseDocumentString(schemaJson, OpenApiParser.Format.JSON)

        val schemaDoc = doc as? OpenApiDocument.Schema
        assertNotNull(schemaDoc)
        assertEquals(setOf("string"), schemaDoc?.schema?.types)
        assertEquals(1, schemaDoc?.schema?.minLength)
    }

    @Test
    fun `parseDocumentString returns OpenAPI document when openapi field present`() {
        val openapiJson = """{ "openapi": "3.2.0", "info": { "title": "X", "version": "1" } }"""
        val doc = parser.parseDocumentString(openapiJson, OpenApiParser.Format.JSON)
        val openapiDoc = doc as? OpenApiDocument.OpenApi
        assertNotNull(openapiDoc)
        assertEquals("3.2.0", openapiDoc?.definition?.openapi)
    }

    @Test
    fun `parseSchemaString throws when document is OpenAPI`() {
        val openapiJson = """{ "openapi": "3.2.0", "info": { "title": "X", "version": "1" } }"""
        try {
            parser.parseSchemaString(openapiJson, OpenApiParser.Format.JSON)
        } catch (ex: IllegalArgumentException) {
            assertTrue(ex.message?.contains("OpenAPI Object") == true)
            return
        }
        throw AssertionError("Expected IllegalArgumentException for OpenAPI document")
    }

    @Test
    fun `parse YAML OpenAPI into IR and parse from file`() {
        val yaml = """
            openapi: 3.2.0
            info:
              title: YAML API
              version: "1.0"
            paths:
              /search:
                query:
                  parameters:
                    - name: q
                      in: querystring
                      content:
                        application/x-www-form-urlencoded:
                          schema:
                            type: object
                            properties:
                              term:
                                type: string
                  callbacks:
                    onDone:
                      ${"$"}request.query.cb:
                        post:
                          operationId: cbPost
                          responses:
                            "200":
                              description: ok
                  responses:
                    "200":
                      description: ok
            components:
              schemas:
                Prefs:
                  type: ["string", "null"]
                  examples:
                    - "a"
                    - "b"
                Opts:
                  type: object
                  additionalProperties: false
              securitySchemes:
                OAuth2:
                  type: oauth2
                  flows:
                    authorizationCode:
                      authorizationUrl: https://example.com/auth
                      tokenUrl: https://example.com/token
                      scopes:
                        read: read access
              examples:
                BinaryExample:
                  value: !!binary "SGVsbG8="
            tags:
              - name: search
                parent: root
                kind: nav
            externalDocs:
              url: https://docs.example.com
        """.trimIndent()

        val root = parser.parseString(yaml, OpenApiParser.Format.AUTO)

        assertEquals("YAML API", root.info.title)
        assertEquals(1, root.paths.size)

        val queryOp = root.paths["/search"]?.query
        assertNotNull(queryOp)
        assertEquals(HttpMethod.QUERY, queryOp?.method)
        assertTrue(queryOp?.operationId?.startsWith("query_search") == true)
        assertTrue(queryOp?.parameters?.any { it.location == ParameterLocation.QUERYSTRING } == true)
        val queryCallback = queryOp?.callbacks?.get("onDone") as? Callback.Inline
        assertTrue(queryCallback?.expressions?.containsKey("\$request.query.cb") == true)

        val prefs = root.components?.schemas?.get("Prefs")
        assertTrue(prefs?.types?.contains("null") == true)
        assertEquals("a", prefs?.examples?.get("example1"))

        val opts = root.components?.schemas?.get("Opts")
        assertEquals(false, opts?.additionalProperties?.booleanSchema)

        val oauth = root.components?.securitySchemes?.get("OAuth2")
        assertEquals("https://example.com/auth", oauth?.flows?.authorizationCode?.authorizationUrl)
        assertEquals("read access", oauth?.flows?.authorizationCode?.scopes?.get("read"))

        val binaryExample = root.components?.examples?.get("BinaryExample")
        assertNotNull(binaryExample?.value)

        val file = File.createTempFile("oas", ".yaml")
        file.writeText(yaml)
        val fileRoot = parser.parseFile(file)
        assertEquals("YAML API", fileRoot.info.title)
        file.delete()

        val jsonFile = File.createTempFile("oas", ".json")
        jsonFile.writeText(
            """
            { "openapi": "3.2.0", "info": { "title": "JSON File", "version": "1.0" } }
            """.trimIndent()
        )
        val jsonRoot = parser.parseFile(jsonFile)
        assertEquals("JSON File", jsonRoot.info.title)
        jsonFile.delete()

        val autoJsonRoot = parser.parseString(
            """{ "openapi": "3.2.0", "info": { "title": "Auto JSON", "version": "1.0" } }"""
        )
        assertEquals("Auto JSON", autoJsonRoot.info.title)
    }

    @Test
    fun `parse preserves explicit empty paths and webhooks`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Empty API", "version": "1.0.0" },
              "paths": {},
              "webhooks": {}
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)

        assertTrue(definition.paths.isEmpty())
        assertTrue(definition.webhooks.isEmpty())
        assertTrue(definition.pathsExplicitEmpty)
        assertTrue(definition.webhooksExplicitEmpty)
    }

    @Test
    fun `parse tracks missing requestBody content`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Body", "version": "1.0" },
              "paths": {
                "/submit": {
                  "post": {
                    "operationId": "submit",
                    "requestBody": {
                      "description": "raw payload"
                    },
                    "responses": {
                      "200": { "description": "ok" }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val root = parser.parseString(json, OpenApiParser.Format.JSON)
        val body = root.paths["/submit"]?.post?.requestBody
        assertNotNull(body)
        assertFalse(body?.contentPresent ?: true)
        assertTrue(body?.content?.isEmpty() == true)
    }

    @Test
    fun `parse resolves media type ref for content schemas`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Media Types", "version": "1.0.0" },
              "components": {
                "mediaTypes": {
                  "PetJson": {
                    "schema": {
                      "type": "object",
                      "properties": { "id": { "type": "string" } }
                    }
                  }
                }
              },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "getPets",
                    "responses": {
                      "200": {
                        "description": "ok",
                        "content": {
                          "application/json": { "${"$"}ref": "#/components/mediaTypes/PetJson" }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val media = definition.paths["/pets"]?.get?.responses?.get("200")?.content?.get("application/json")

        assertNotNull(media)
        assertEquals("#/components/mediaTypes/PetJson", media?.ref)
        assertNotNull(media?.schema, "Expected resolved schema for media type ref")
    }

    @Test
    fun `parse explicit empty security arrays`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Security", "version": "1.0" },
              "security": [],
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "security": [],
                    "responses": { "200": { "description": "ok" } }
                  }
                }
              }
            }
        """.trimIndent()

        val root = parser.parseString(json, OpenApiParser.Format.JSON)

        assertTrue(root.security.isEmpty())
        assertTrue(root.securityExplicitEmpty)
        val op = root.paths["/pets"]?.get
        assertTrue(op?.security?.isEmpty() == true)
        assertTrue(op?.securityExplicitEmpty == true)
    }

    @Test
    fun `parse schema property preserves ref siblings`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Test", "version": "1.0" },
              "components": {
                "schemas": {
                  "Wrapper": {
                    "type": "object",
                    "properties": {
                      "id": {
                        "${"$"}ref": "#/components/schemas/Id",
                        "description": "Referenced id",
                        "deprecated": true
                      }
                    }
                  },
                  "Id": { "type": "string" }
                }
              }
            }
        """.trimIndent()

        val parsed = parser.parseString(json, OpenApiParser.Format.JSON)
        val prop = parsed.components?.schemas?.get("Wrapper")?.properties?.get("id")
        assertNotNull(prop)
        assertEquals("#/components/schemas/Id", prop?.ref)
        assertEquals("Referenced id", prop?.description)
        assertTrue(prop?.deprecated == true)
    }

    @Test
    fun `parse supports non-string enum values`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Enums", "version": "1.0" },
              "components": {
                "schemas": {
                  "IntEnum": { "type": "integer", "enum": [1, 2, 3] },
                  "BoolEnum": { "type": "boolean", "enum": [true, false] },
                  "ObjEnum": {
                    "type": "object",
                    "enum": [ { "k": "v1" }, { "k": "v2" } ]
                  },
                  "Wrapper": {
                    "type": "object",
                    "properties": {
                      "mode": { "type": "string", "enum": ["fast", "slow"] },
                      "level": { "type": "number", "enum": [0.1, 0.5] }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = parser.parseString(json, OpenApiParser.Format.JSON)
        val components = parsed.components?.schemas

        assertEquals(listOf(1, 2, 3), components?.get("IntEnum")?.enumValues)
        assertEquals(listOf(true, false), components?.get("BoolEnum")?.enumValues)
        assertEquals(
            listOf(mapOf("k" to "v1"), mapOf("k" to "v2")),
            components?.get("ObjEnum")?.enumValues
        )

        val wrapper = components?.get("Wrapper")
        assertEquals(listOf("fast", "slow"), wrapper?.properties?.get("mode")?.enumValues)
        val levelEnums = wrapper?.properties?.get("level")?.enumValues?.map { (it as Number).toDouble() }
        assertEquals(listOf(0.1, 0.5), levelEnums)
    }

    @Test
    fun `parse resolves media type refs for response typing and encoding header refs`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Refs", "version": "1.0" },
              "components": {
                "schemas": {
                  "Pet": { "type": "object" }
                },
                "mediaTypes": {
                  "PetJson": {
                    "schema": { "${"$"}ref": "#/components/schemas/Pet" }
                  }
                },
                "headers": {
                  "TraceHeader": {
                    "schema": { "type": "integer", "format": "int32" }
                  }
                }
              },
              "paths": {
                "/pets": {
                  "get": {
                    "responses": {
                      "200": {
                        "description": "ok",
                        "content": {
                          "application/json": { "${"$"}ref": "#/components/mediaTypes/PetJson" }
                        }
                      }
                    }
                  }
                },
                "/upload": {
                  "post": {
                    "requestBody": {
                      "content": {
                        "multipart/form-data": {
                          "schema": {
                            "type": "object",
                            "properties": { "file": { "type": "string" } }
                          },
                          "encoding": {
                            "file": {
                              "headers": {
                                "X-Trace": { "${"$"}ref": "#/components/headers/TraceHeader" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "responses": { "200": { "description": "ok" } }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)

        val petResponse = definition.paths["/pets"]?.get?.responses?.get("200")
        assertEquals("Pet", petResponse?.type)

        val encodingHeader = definition.paths["/upload"]
            ?.post
            ?.requestBody
            ?.content
            ?.get("multipart/form-data")
            ?.encoding
            ?.get("file")
            ?.headers
            ?.get("X-Trace")

        assertEquals("Int", encodingHeader?.type)
        assertEquals("#/components/headers/TraceHeader", encodingHeader?.reference?.ref)
    }

    @Test
    fun `selects most specific media type when deriving response type`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Media", "version": "1.0" },
              "paths": {
                "/items": {
                  "get": {
                    "operationId": "listItems",
                    "responses": {
                      "200": {
                        "description": "ok",
                        "content": {
                          "application/*": { "schema": { "type": "string" } },
                          "application/*+json": { "schema": { "type": "integer" } }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val responseType = definition.paths["/items"]
            ?.get
            ?.responses
            ?.get("200")
            ?.type

        assertEquals("Int", responseType)
    }

    @Test
    fun `infers schema-less binary response type from media type`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Binary", "version": "1.0" },
              "paths": {
                "/bin": {
                  "get": {
                    "operationId": "getBin",
                    "responses": {
                      "200": {
                        "description": "ok",
                        "content": {
                          "application/octet-stream": {}
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val responseType = definition.paths["/bin"]
            ?.get
            ?.responses
            ?.get("200")
            ?.type

        assertEquals("ByteArray", responseType)
    }

    @Test
    fun `infers schema-less form request body type from media type`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Form", "version": "1.0" },
              "paths": {
                "/form": {
                  "post": {
                    "operationId": "submitForm",
                    "requestBody": {
                      "required": true,
                      "content": {
                        "application/x-www-form-urlencoded": {}
                      }
                    },
                    "responses": { "200": { "description": "ok" } }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val requestBodyType = definition.paths["/form"]
            ?.post
            ?.requestBodyType

        assertEquals("String", requestBodyType)
    }

    @Test
    fun `infers list type from itemSchema content`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Stream", "version": "1.0" },
              "paths": {
                "/events": {
                  "get": {
                    "operationId": "streamEvents",
                    "responses": {
                      "200": {
                        "description": "ok",
                        "content": {
                          "application/jsonl": { "itemSchema": { "type": "string" } }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val responseType = definition.paths["/events"]
            ?.get
            ?.responses
            ?.get("200")
            ?.type

        assertEquals("List<String>", responseType)
    }

    @Test
    fun `parse resolves component link refs with overrides`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Links", "version": "1.0" },
              "paths": {
                "/items": {
                  "get": {
                    "responses": {
                      "200": {
                        "description": "ok",
                        "links": {
                          "UserLink": {
                            "${"$"}ref": "#/components/links/BaseLink",
                            "description": "override desc"
                          }
                        }
                      }
                    }
                  }
                }
              },
              "components": {
                "links": {
                  "BaseLink": {
                    "operationId": "getUser",
                    "parameters": { "id": "${"$"}response.body#/id" }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val link = definition.paths["/items"]?.get?.responses?.get("200")?.links?.get("UserLink")

        assertNotNull(link)
        assertEquals("getUser", link?.operationId)
        assertEquals("override desc", link?.description)
        assertEquals("#/components/links/BaseLink", link?.ref)
    }

    @Test
    fun `parse resolves component example refs with overrides`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Examples", "version": "1.0" },
              "paths": {
                "/items": {
                  "get": {
                    "parameters": [
                      {
                        "name": "id",
                        "in": "query",
                        "schema": { "type": "string" },
                        "examples": {
                          "sample": {
                            "${"$"}ref": "#/components/examples/IdExample",
                            "summary": "override summary"
                          }
                        }
                      }
                    ],
                    "responses": {
                      "200": { "description": "ok" }
                    }
                  }
                }
              },
              "components": {
                "examples": {
                  "IdExample": { "summary": "base", "dataValue": "abc-123" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val example = definition.paths["/items"]?.get?.parameters?.firstOrNull()
            ?.examples?.get("sample")

        assertNotNull(example)
        assertEquals("override summary", example?.summary)
        assertEquals("abc-123", example?.dataValue)
        assertEquals("#/components/examples/IdExample", example?.ref)
    }

    @Test
    fun `parse resolves component refs within components`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Ref Components", "version": "1.0" },
              "components": {
                "parameters": {
                  "Limit": {
                    "name": "limit",
                    "in": "query",
                    "schema": { "type": "integer" }
                  },
                  "LimitRef": {
                    "${"$"}ref": "#/components/parameters/Limit",
                    "description": "override"
                  }
                },
                "responses": {
                  "Ok": { "description": "ok" },
                  "OkRef": { "${"$"}ref": "#/components/responses/Ok" }
                },
                "requestBodies": {
                  "BaseBody": {
                    "content": { "application/json": { "schema": { "type": "string" } } }
                  },
                  "RefBody": {
                    "${"$"}ref": "#/components/requestBodies/BaseBody",
                    "description": "Body override"
                  }
                },
                "securitySchemes": {
                  "ApiKey": { "type": "apiKey", "name": "X-API-KEY", "in": "header" },
                  "ApiKeyRef": { "${"$"}ref": "#/components/securitySchemes/ApiKey", "description": "Ref desc" }
                },
                "examples": {
                  "BaseExample": { "dataValue": "base" },
                  "RefExample": { "${"$"}ref": "#/components/examples/BaseExample", "summary": "override" }
                },
                "mediaTypes": {
                  "JsonBase": { "schema": { "type": "string" } },
                  "JsonRef": { "${"$"}ref": "#/components/mediaTypes/JsonBase" }
                }
              }
            }
        """.trimIndent()

        val root = parser.parseString(json, OpenApiParser.Format.JSON)
        val components = root.components
        assertNotNull(components)

        val limitRef = components?.parameters?.get("LimitRef")
        assertEquals("#/components/parameters/Limit", limitRef?.reference?.ref)
        assertEquals("limit", limitRef?.name)
        assertEquals("override", limitRef?.description)

        val okRef = components?.responses?.get("OkRef")
        assertEquals("#/components/responses/Ok", okRef?.reference?.ref)
        assertEquals("ok", okRef?.description)

        val bodyRef = components?.requestBodies?.get("RefBody")
        assertEquals("#/components/requestBodies/BaseBody", bodyRef?.reference?.ref)
        assertTrue(bodyRef?.content?.isNotEmpty() == true)
        assertEquals("Body override", bodyRef?.description)

        val schemeRef = components?.securitySchemes?.get("ApiKeyRef")
        assertEquals("#/components/securitySchemes/ApiKey", schemeRef?.reference?.ref)
        assertEquals("apiKey", schemeRef?.type)
        assertEquals("header", schemeRef?.`in`)
        assertEquals("Ref desc", schemeRef?.description)

        val exampleRef = components?.examples?.get("RefExample")
        assertEquals("#/components/examples/BaseExample", exampleRef?.ref)
        assertEquals("base", exampleRef?.dataValue)
        assertEquals("override", exampleRef?.summary)

        val mediaRef = components?.mediaTypes?.get("JsonRef")
        assertEquals("#/components/mediaTypes/JsonBase", mediaRef?.reference?.ref ?: mediaRef?.ref)
        assertNotNull(mediaRef?.schema)
    }

    @Test
    fun `parse missing operationId preserves omission`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "NoOpId", "version": "1.0" },
              "paths": {
                "/pets": {
                  "get": {
                    "responses": {
                      "200": { "description": "ok" }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val operation = definition.paths["/pets"]?.get
        assertNotNull(operation)
        assertFalse(operation!!.operationIdExplicit)
        assertEquals("get_pets", operation.operationId)
    }
}
