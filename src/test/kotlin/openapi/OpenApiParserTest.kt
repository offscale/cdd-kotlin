/** Unit and integration tests in [OpenApiParserTest]. */
package openapi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import domain.Callback
import domain.HttpMethod
import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiNull
import domain.OpenApiObject
import domain.OpenApiString
import domain.ParameterLocation
import domain.ParameterStyle
import domain.doubleOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiParserTest {

  private val parser = OpenApiParser()
  private val writer = OpenApiWriter()

  @Test
  fun `parse JSON OpenAPI into IR`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val root = parser.parseString(json, OpenApiParser.Format.JSON)

    assertEquals("3.2.0", root.openapi)
    assertEquals("https://example.com/api/openapi", root.self)
    assertEquals("Pet API", root.info.title)
    assertEquals(OpenApiString("root-ext"), root.extensions["x-root"])
    assertEquals(OpenApiString("info-ext"), root.info.extensions["x-info"])
    assertEquals("https://spec.openapis.org/oas/3.1/dialect/base", root.jsonSchemaDialect)
    assertEquals("https://api.example.com/{version}", root.servers.first().url)
    assertEquals("v1", root.servers.first().variables?.get("version")?.default)

    val pathItem = root.paths["/users/{id}"]
    assertNotNull(pathItem)
    assertEquals("User path", pathItem?.summary)
    assertEquals(OpenApiBoolean(true), pathItem?.extensions?.get("x-path"))

    val getOp = pathItem?.get
    assertEquals("getUser", getOp?.operationId)
    assertEquals(HttpMethod.GET, getOp?.method)
    assertTrue(getOp?.deprecated == true)
    val opExt = getOp?.extensions?.get("x-op") as? OpenApiObject
    assertEquals(OpenApiBoolean(true), opExt?.properties?.get("flag"))
    assertTrue(
        getOp?.parameters?.any { it.name == "limit" && it.location == ParameterLocation.QUERY } ==
            true)
    assertTrue(pathItem?.parameters?.any { it.name == "MissingParam" } == true)
    val pathLimitParam =
        pathItem?.parameters?.firstOrNull { it.reference?.ref == "#/components/parameters/limit" }
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
    assertTrue(
        refPath?.parameters?.any {
          it.name == "refParam" && it.location == ParameterLocation.QUERY
        } == true)
    assertEquals(OpenApiString("paths-ext"), root.pathsExtensions["x-paths"])
    assertTrue(root.paths.containsKey("x-paths") == false)
    assertEquals(OpenApiString("hook-ext"), root.webhooksExtensions["x-webhooks"])
    assertTrue(root.webhooks.containsKey("x-webhooks") == false)

    val userSchema = root.components?.schemas?.get("User")
    assertEquals("User schema", userSchema?.description)
    assertEquals("https://example.com/schemas/User", userSchema?.schemaId)
    assertEquals("https://json-schema.org/draft/2020-12/schema", userSchema?.schemaDialect)
    assertEquals("user", userSchema?.anchor)
    assertEquals("userDyn", userSchema?.dynamicAnchor)
    assertEquals(OpenApiInt(7L), userSchema?.extensions?.get("x-schema"))
    assertEquals(1, userSchema?.minProperties)
    assertEquals(4, userSchema?.maxProperties)
    assertTrue(userSchema?.properties?.get("name")?.types?.contains("null") == true)
    assertEquals(20, userSchema?.properties?.get("name")?.maxLength)
    assertEquals("#/components/schemas/User", userSchema?.properties?.get("address")?.ref)
    assertEquals(
        OpenApiString("prop-ext"), userSchema?.properties?.get("id")?.extensions?.get("x-prop"))
    assertTrue(userSchema?.properties?.get("xmlAttr")?.xml?.attribute == true)
    assertEquals(
        listOf(OpenApiString("active"), OpenApiString("disabled")),
        userSchema?.properties?.get("status")?.enumValues)
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
    assertEquals(
        listOf(OpenApiString("alpha"), OpenApiString("beta")),
        userSchema?.properties?.get("tags")?.examples)

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
    assertEquals(OpenApiString("a"), flags?.examples?.get("example1"))
    assertEquals(listOf(OpenApiString("a"), OpenApiString("b")), flags?.examplesList)
    assertTrue(flags?.uniqueItems == true)

    val anyMap = root.components?.schemas?.get("AnyMap")
    assertNotNull(anyMap?.additionalProperties)

    val bag = root.components?.schemas?.get("Bag")
    assertTrue(bag?.additionalProperties?.types?.contains("integer") == true)

    val weird = root.components?.schemas?.get("WeirdProps")
    assertNull(weird?.additionalProperties)

    val exampleMap = root.components?.schemas?.get("ExampleMap")
    assertEquals(OpenApiInt(1L), exampleMap?.examples?.get("first"))

    val noTypeEnum = root.components?.schemas?.get("NoTypeEnum")
    assertEquals(OpenApiInt(7L), noTypeEnum?.defaultValue)
    assertEquals(OpenApiBoolean(true), noTypeEnum?.constValue)

    val noTypeDefault = root.components?.schemas?.get("NoTypeDefault")
    val noTypeExample = noTypeDefault?.example as? OpenApiObject
    assertEquals(OpenApiInt(1L), noTypeExample?.properties?.get("x"))

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
    assertEquals(OpenApiString("raw-string"), simpleExample?.value)

    val nullExample = root.components?.examples?.get("NullExample")
    assertEquals(OpenApiNull, nullExample?.dataValue)

    val onEventCallback = root.components?.callbacks?.get("OnEvent") as? Callback.Inline
    assertEquals(OpenApiString("cb-ext"), onEventCallback?.extensions?.get("x-cb"))
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
    assertEquals(OpenApiString("\$response.body#/id"), userLink?.parameters?.get("userId"))
    assertEquals(OpenApiInt(3L), userLink?.parameters?.get("priority"))
    assertEquals(
        OpenApiArray(listOf(OpenApiBoolean(true), OpenApiBoolean(false))),
        userLink?.parameters?.get("flags"))
    assertEquals(
        OpenApiObject(mapOf("source" to OpenApiString("test"))), userLink?.parameters?.get("meta"))
    assertEquals(OpenApiObject(mapOf("id" to OpenApiInt(1L))), userLink?.requestBody)

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
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val response = definition.paths["/ping"]?.get?.responses?.get("200")
    assertNotNull(response)
    assertTrue(response!!.content.isEmpty())
    assertTrue(response.contentPresent)
  }

  @Test
  fun `parse handles missing info object gracefully`() {
    val json = """{ "openapi": "3.2.0" }"""
    val root = parser.parseString(json)
    assertEquals("Unknown", root.info.title)
    assertEquals("0.0.0", root.info.version)
  }

  @Test
  fun `parse arbitrary JSON node types in custom keywords`() {
    val json =
        """ 
            { 
              "openapi": "3.2.0", 
              "info": { "title": "Any JSON", "version": "1.0" }, 
              "x-ext": { 
                 "nullVal": null, 
                 "intVal": 42, 
                 "floatVal": 3.14, 
                 "boolVal": true, 
                 "stringVal": "hello", 
                 "arrVal": [1, 2, 3], 
                 "objVal": { "k": "v" } 
              } 
            } 
        """
            .trimIndent()
    val root = parser.parseString(json)
    val ext = (root.extensions["x-ext"] as OpenApiObject).properties
    assertEquals(OpenApiNull, ext["nullVal"])
    assertEquals(OpenApiInt(42L), ext["intVal"])
    assertEquals(3.14, ext["floatVal"].doubleOrNull())
    assertEquals(OpenApiBoolean(true), ext["boolVal"])
    assertEquals(OpenApiString("hello"), ext["stringVal"])
    assertEquals(
        OpenApiArray(listOf(OpenApiInt(1L), OpenApiInt(2L), OpenApiInt(3L))), ext["arrVal"])
    assertEquals(OpenApiObject(mapOf("k" to OpenApiString("v"))), ext["objVal"])
  }

  @Test
  fun `parse resolves callback refs while preserving ref metadata`() {
    val json =
        """ 
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
        """
            .trimIndent()

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
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json)
    val response = definition.paths["/pets"]?.get?.responses?.get("200")
    assertNotNull(response)
    assertEquals("ok", response?.description)
  }

  @Test
  fun `parse resolves component response refs with self base match`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json)
    val response = definition.paths["/pets"]?.get?.responses?.get("200")
    assertNotNull(response)
    assertEquals("ok", response?.description)
  }

  @Test
  fun `parse resolves component response refs using base uri when self missing`() {
    val ref = "https://example.com/openapi.json#/components/responses/Ok"
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, baseUri = "https://example.com/openapi.json")
    val response = definition.paths["/pets"]?.get?.responses?.get("200")
    assertNotNull(response)
    assertEquals("ok", response?.description)
  }

  @Test
  fun `parse resolves component response refs when self is relative and base uri provided`() {
    val ref = "https://example.com/api/openapi.json#/components/responses/Ok"
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, baseUri = "https://example.com/root/openapi.json")
    val response = definition.paths["/pets"]?.get?.responses?.get("200")
    assertNotNull(response)
    assertEquals("ok", response?.description)
  }

  @Test
  fun `parse does not resolve component response refs when base uri mismatches`() {
    val ref = "https://example.com/openapi.json#/components/responses/Ok"
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, baseUri = "https://other.example.com/openapi.json")
    val response = definition.paths["/pets"]?.get?.responses?.get("200")
    assertNotNull(response)
    assertEquals("ref:$ref", response?.description)
  }

  @Test
  fun `parse does not resolve component response refs when self base mismatches`() {
    val ref = "https://other.example.com/openapi.json#/components/responses/Ok"
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json)
    val response = definition.paths["/pets"]?.get?.responses?.get("200")
    assertNotNull(response)
    assertEquals("ref:$ref", response?.description)
  }

  @Test
  fun `parse resolves component response refs with percent encoded key`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json)
    val response = definition.paths["/pets"]?.get?.responses?.get("200")
    assertNotNull(response)
    assertEquals("ok", response?.description)
  }

  @Test
  fun `percentDecode ignores invalid hex values gracefully`() {
    val definition =
        parser.parseString(
            """ 
            { 
               "openapi": "3.2.0", 
               "info": { "title": "Test", "version": "1.0" }, 
               "components": { 
                 "pathItems": { 
                   "Bad%ZZ": { 
                     "get": { "operationId": "badHex" } 
                   } 
                 } 
               } 
            } 
        """
                .trimIndent())
    val op = definition.components?.pathItems?.get("Bad%ZZ")?.get
    assertEquals("badHex", op?.operationId)
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
  fun `parseSchemaStringResult fails when document is OpenAPI`() {
    val openapiJson = """{ "openapi": "3.2.0", "info": { "title": "X", "version": "1" } }"""
    val res = parser.parseSchemaStringResult(openapiJson, OpenApiParser.Format.JSON)
    assertTrue(res.isFailure)
    assertTrue(res.exceptionOrNull()?.message?.contains("OpenAPI Object") == true)
  }

  @Test
  fun `parse preserves explicit empty paths and webhooks`() {
    val json =
        """ 
            { 
              "openapi": "3.2.0", 
              "info": { "title": "Empty API", "version": "1.0.0" }, 
              "paths": {}, 
              "webhooks": {} 
            } 
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)

    assertTrue(definition.paths.isEmpty())
    assertTrue(definition.webhooks.isEmpty())
    assertTrue(definition.pathsExplicitEmpty)
    assertTrue(definition.webhooksExplicitEmpty)
  }

  @Test
  fun `parse tracks missing requestBody content`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val root = parser.parseString(json, OpenApiParser.Format.JSON)
    val body = root.paths["/submit"]?.post?.requestBody
    assertNotNull(body)
    assertFalse(body?.contentPresent ?: true)
    assertTrue(body?.content?.isEmpty() == true)
  }

  @Test
  fun `parse resolves media type ref for content schemas`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val media =
        definition.paths["/pets"]?.get?.responses?.get("200")?.content?.get("application/json")

    assertNotNull(media)
    assertEquals("#/components/mediaTypes/PetJson", media?.ref)
    assertNotNull(media?.schema, "Expected resolved schema for media type ref")
  }

  @Test
  fun `parse explicit empty security arrays`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val root = parser.parseString(json, OpenApiParser.Format.JSON)

    assertTrue(root.security.isEmpty())
    assertTrue(root.securityExplicitEmpty)
    val op = root.paths["/pets"]?.get
    assertTrue(op?.security?.isEmpty() == true)
    assertTrue(op?.securityExplicitEmpty == true)
  }

  @Test
  fun `parse schema property preserves ref siblings`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val parsed = parser.parseString(json, OpenApiParser.Format.JSON)
    val prop = parsed.components?.schemas?.get("Wrapper")?.properties?.get("id")
    assertNotNull(prop)
    assertEquals("#/components/schemas/Id", prop?.ref)
    assertEquals("Referenced id", prop?.description)
    assertTrue(prop?.deprecated == true)
  }

  @Test
  fun `parse supports non-string enum values`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val parsed = parser.parseString(json, OpenApiParser.Format.JSON)
    val components = parsed.components?.schemas

    assertEquals(
        listOf(OpenApiInt(1L), OpenApiInt(2L), OpenApiInt(3L)),
        components?.get("IntEnum")?.enumValues)
    assertEquals(
        listOf(OpenApiBoolean(true), OpenApiBoolean(false)),
        components?.get("BoolEnum")?.enumValues)
    assertEquals(
        listOf(
            OpenApiObject(mapOf("k" to OpenApiString("v1"))),
            OpenApiObject(mapOf("k" to OpenApiString("v2")))),
        components?.get("ObjEnum")?.enumValues)

    val wrapper = components?.get("Wrapper")
    assertEquals(
        listOf(OpenApiString("fast"), OpenApiString("slow")),
        wrapper?.properties?.get("mode")?.enumValues)
    val levelEnums = wrapper?.properties?.get("level")?.enumValues?.mapNotNull { it.doubleOrNull() }
    assertEquals(listOf(0.1, 0.5), levelEnums)
  }

  @Test
  fun `parse resolves media type refs for response typing and encoding header refs`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)

    val petResponse = definition.paths["/pets"]?.get?.responses?.get("200")
    assertEquals("Pet", petResponse?.type)

    val encodingHeader =
        definition.paths["/upload"]
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
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val responseType = definition.paths["/items"]?.get?.responses?.get("200")?.type

    assertEquals("Int", responseType)
  }

  @Test
  fun `infers schema-less binary response type from media type`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val responseType = definition.paths["/bin"]?.get?.responses?.get("200")?.type

    assertEquals("ByteArray", responseType)
  }

  @Test
  fun `infers schema-less form request body type from media type`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val requestBodyType = definition.paths["/form"]?.post?.requestBodyType

    assertEquals("String", requestBodyType)
  }

  @Test
  fun `infers list type from itemSchema content`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val responseType = definition.paths["/events"]?.get?.responses?.get("200")?.type

    assertEquals("List<String>", responseType)
  }

  @Test
  fun `parse resolves component link refs with overrides`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val link = definition.paths["/items"]?.get?.responses?.get("200")?.links?.get("UserLink")

    assertNotNull(link)
    assertEquals("getUser", link?.operationId)
    assertEquals("override desc", link?.description)
    assertEquals("#/components/links/BaseLink", link?.ref)
  }

  @Test
  fun `parse resolves component example refs with overrides`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val example =
        definition.paths["/items"]?.get?.parameters?.firstOrNull()?.examples?.get("sample")

    assertNotNull(example)
    assertEquals("override summary", example?.summary)
    assertEquals(OpenApiString("abc-123"), example?.dataValue)
    assertEquals("#/components/examples/IdExample", example?.ref)
  }

  @Test
  fun `parse resolves component refs within components`() {
    val json =
        """ 
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
        """
            .trimIndent()

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
    assertEquals(OpenApiString("base"), exampleRef?.dataValue)
    assertEquals("override", exampleRef?.summary)

    val mediaRef = components?.mediaTypes?.get("JsonRef")
    assertEquals("#/components/mediaTypes/JsonBase", mediaRef?.reference?.ref ?: mediaRef?.ref)
    assertNotNull(mediaRef?.schema)
  }

  @Test
  fun `parse missing operationId preserves omission`() {
    val json =
        """ 
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
        """
            .trimIndent()

    val definition = parser.parseString(json, OpenApiParser.Format.JSON)
    val operation = definition.paths["/pets"]?.get
    assertNotNull(operation)
    assertFalse(operation!!.operationIdExplicit)
    assertEquals("get_pets", operation.operationId)
  }

  @Test
  fun `parse covers component ref resolution, media type inference, and Swagger 2_0 edge cases`() {
    // 1. parseSchemaString default args and invalid root
    val boolSchema = parser.parseSchemaString("true")
    assertEquals(true, boolSchema.booleanSchema)
    assertEquals(true, parser.parseSchemaStringResult("true").getOrThrow().booleanSchema)
    val yamlDoc = parser.parseString("openapi: \"3.2.0\"\ninfo:\n  title: \"Y\"\n  version: \"1\"")
    assertEquals("Y", yamlDoc.info.title)
    assertEquals(
        "Y",
        parser
            .parseStringResult("openapi: \"3.2.0\"\ninfo:\n  title: \"Y\"\n  version: \"1\"")
            .getOrThrow()
            .info
            .title)
    assertTrue(parser.parseSchemaStringResult("123", OpenApiParser.Format.JSON).isFailure)
    assertTrue(
        parser
            .parseSchemaStringResult("""{"openapi":"3.2.0"}""", OpenApiParser.Format.JSON)
            .isFailure)
    assertTrue(parser.parseStringResult("[1, 2]", OpenApiParser.Format.JSON).isFailure)
    assertTrue(parser.parseStringResult("{unclosed", OpenApiParser.Format.JSON).isFailure)
    assertTrue(parser.parseDocumentStringResult("{unclosed", OpenApiParser.Format.JSON).isFailure)
    assertTrue(parser.parseDocumentStringResult("123", OpenApiParser.Format.JSON).isFailure)
    assertTrue(
        parser
            .parseDocumentStringResult(
                "openapi: \"3.2.0\"\ninfo:\n  title: \"Y\"\n  version: \"1\"",
                OpenApiParser.Format.YAML)
            .isSuccess)
    assertNotNull(parser.parseDocumentString("{unquotedKey: 1}"))
    assertTrue(parser.parseSchemaStringResult("{unclosed", OpenApiParser.Format.JSON).isFailure)

    // 2. Swagger 2.0 with empty components and with only securityDefinitions & response examples
    val swagEmpty =
        parser.parseString("""{"swagger":"2.0","info":{"title":"S","version":"1"},"paths":{}}""")
    assertNull(swagEmpty.components)

    val swagSec =
        parser.parseString(
            """
            {
              "swagger": "2.0",
              "info": {"title": "S", "version": "1"},
              "securityDefinitions": {
                "Basic": {"type": "basic", "scopes": {"r": "read", "arr": []}}
              },
              "paths": {
                "/x": {
                  "get": {
                    "responses": {
                      "200": {
                        "description": "ok",
                        "examples": {"application/json": {"k": "v"}}
                      }
                    }
                  }
                }
              }
            }
            """
                .trimIndent())
    assertNotNull(swagSec.components)
    assertEquals(
        OpenApiObject(mapOf("k" to OpenApiString("v"))),
        swagSec.paths["/x"]?.get?.responses?.get("200")?.examples?.get("application/json")?.value)

    // 3. Component Header, Link, Callback refs, extension merging, mediaType scoring/inference,
    // styles, enums
    val richJson =
        """
        {
          "openapi": "3.2.0",
          "info": {"title": "Rich", "version": "1.0"},
          "tags": [{"name": "t1"}, "invalidTag"],
          "paths": {
            "/test": {
              "get": {
                "parameters": [
                  {"name": "p1", "in": "query", "style": "spaceDelimited"},
                  {"name": "p2", "in": "query", "style": "pipeDelimited"},
                  {"name": "p3", "in": "query", "style": "deepObject"},
                  {"name": "p4", "in": "querystring", "style": "unknownStyle"},
                  {"name": "p5", "in": "body"},
                  {"name": "p6", "in": "formdata"}
                ],
                "responses": {
                  "200": {
                    "description": "ok",
                    "links": {
                      "LInline": {"operationId": "op", "parameters": {"id": "${'$'}request.path.id"}}
                    },
                    "content": {
                      "text/html": {},
                      "text/yaml": {},
                      "invalid_media": {},
                      "*/*": {},
                      "text/*+json": {}
                    }
                  },
                  "201": {
                    "description": "bin",
                    "content": {
                      "application/octet-stream": {}
                    }
                  },
                  "202": {
                    "description": "refMedia",
                    "content": {
                      "application/json": {"${'$'}ref": "#/components/mediaTypes/ItemMedia"}
                    }
                  },
                  "203": {
                    "description": "unresolvedMedia",
                    "content": {
                      "application/json": {"${'$'}ref": "#/components/mediaTypes/MissingMedia"}
                    }
                  }
                }
              }
            }
          },
          "components": {
            "headers": {
              "BaseH": {"schema": {"type": "string"}, "description": "base", "x-base": 1},
              "RefH": {"${'$'}ref": "#/components/headers/BaseH", "x-over": 2}
            },
            "links": {
              "BaseL": {"operationId": "op1", "description": "baseL", "x-base": 1},
              "RefL": {"${'$'}ref": "#/components/links/BaseL", "x-over": 2}
            },
            "callbacks": {
              "BaseCb": {"http://example.com": {"post": {"responses": {"200": {"description": "ok"}}}}},
              "RefCb": {"${'$'}ref": "#/components/callbacks/BaseCb"},
              "DanglingCb1": {"${'$'}ref": "#/components/callbacks/DanglingCb2"},
              "DanglingCb2": {"${'$'}ref": "#/components/callbacks/MissingCb"}
            },
            "mediaTypes": {
              "ItemMedia": {"itemSchema": {"type": "string"}}
            },
            "securitySchemes": {
              "BadScheme": 123,
              "OAuth": {
                "type": "oauth2",
                "flows": {
                  "implicit": {
                    "authorizationUrl": "https://example.com",
                    "scopes": {"read": "desc", "arr": []}
                  },
                  "password": {
                    "tokenUrl": "https://example.com/token"
                  }
                }
              }
            },
            "schemas": {
              "EnumObj": {"enum": [{"a": 1}, [1, 2]], "examples": 123},
              "DiscSchema": {
                "type": 123,
                "discriminator": {"propertyName": "t", "mapping": {"a": []}}
              },
              "DiscNoMap": {
                "minimum": 1,
                "discriminator": {"propertyName": "t"}
              },
              "InferredStr": {
                "minLength": 1
              }
            }
          }
        }
        """
            .trimIndent()
    val richDef = parser.parseString(richJson)
    assertEquals("base", richDef.components?.headers?.get("RefH")?.description)
    assertEquals("baseL", richDef.components?.links?.get("RefL")?.description)
    assertTrue(richDef.components?.callbacks?.get("RefCb") is domain.Callback.Inline)

    // 4. Direct calls to decomposed sub-parsers with default parameters and edge cases
    val emptyInfoDef = parser.parseString("""{"openapi":"3.2.0","info":{"license":{}}}""")
    assertEquals("", emptyInfoDef.info.title)
    assertEquals("", emptyInfoDef.info.version)
    assertEquals("", emptyInfoDef.info.license?.name)
    val emptyInfoIssues = OpenApiValidator().validate(emptyInfoDef)
    assertTrue(emptyInfoIssues.any { it.path == "$.info.title" })
    assertTrue(emptyInfoIssues.any { it.path == "$.info.version" })
    assertTrue(emptyInfoIssues.any { it.path == "$.info.license.name" })
    val swagWithArrays =
        parser.parseString(
            """{"swagger":"2.0","info":{"title":"T","version":"1"},"schemes":["https",{}],"consumes":["application/json"],"produces":["application/json"]}""")
    assertEquals(listOf("https"), swagWithArrays.schemes)
    val swagRoot =
        kotlinx.serialization.json.Json.parseToJsonElement(
            """{"definitions":{"Pet":{"type":"object"}}}""")
    assertNotNull(openapi.parser.Swagger2Upgrader.parseSwagger2Components(swagRoot))
    assertNull(openapi.parser.ComponentParser.parseComponents(null))
    val compNode =
        kotlinx.serialization.json.Json.parseToJsonElement(
            """{"schemas":{"Pet":{"type":"object"}}}""")
    val parsedComps = openapi.parser.ComponentParser.parseComponents(compNode)
    assertNotNull(parsedComps)
    assertNotNull(openapi.parser.ComponentParser.resolveComponentRefs(parsedComps!!))
    assertEquals(
        domain.Callback.Inline(),
        openapi.parser.ComponentParser.resolveCallbackComponentRef(
            domain.Callback.Inline(), parsedComps))
    assertNull(
        openapi.parser.ComponentParser.resolveParameterRef("#/components/parameters/P", null))
    assertNull(
        openapi.parser.ComponentParser.resolveMediaTypeRef("#/components/mediaTypes/M", null))
    assertNull(openapi.parser.ComponentParser.resolveResponseRef("#/components/responses/R", null))
    assertNull(
        openapi.parser.ComponentParser.resolveRequestBodyRef("#/components/requestBodies/B", null))
    assertNull(openapi.parser.ComponentParser.resolveHeaderRef("#/components/headers/H", null))
    assertNull(openapi.parser.ComponentParser.resolveLinkRef("#/components/links/L", null))
    assertNull(openapi.parser.ComponentParser.resolveExampleRef("#/components/examples/E", null))
    assertTrue(openapi.parser.ComponentParser.parseComponentResponses(null).isEmpty())
    assertTrue(openapi.parser.ComponentParser.parseComponentParameters(null).isEmpty())
    assertTrue(openapi.parser.ComponentParser.parseComponentRequestBodies(null).isEmpty())
    assertTrue(openapi.parser.ComponentParser.parseComponentHeaders(null).isEmpty())

    // Exercise resolveComponentRefs when reference is null vs present
    val compsWithNullRefs =
        domain.Components(
            parameters =
                mapOf(
                    "P1" to domain.EndpointParameter("p", "String", domain.ParameterLocation.QUERY),
                    "P2" to
                        domain.EndpointParameter(
                            "p2",
                            "String",
                            domain.ParameterLocation.QUERY,
                            reference = domain.ReferenceObject("#/components/parameters/P1"))),
            responses =
                mapOf(
                    "R1" to domain.EndpointResponse("200", description = "ok"),
                    "R2" to
                        domain.EndpointResponse(
                            "200",
                            reference = domain.ReferenceObject("#/components/responses/R1"))),
            requestBodies =
                mapOf(
                    "B1" to domain.RequestBody(description = "b"),
                    "B2" to
                        domain.RequestBody(
                            reference = domain.ReferenceObject("#/components/requestBodies/B1"))),
            headers =
                mapOf(
                    "H1" to domain.Header("String"),
                    "H2" to
                        domain.Header(
                            "String",
                            reference = domain.ReferenceObject("#/components/headers/H1"))),
            links =
                mapOf(
                    "L1" to domain.Link(operationId = "op"),
                    "L2" to domain.Link(ref = "#/components/links/L1")),
            examples =
                mapOf(
                    "E1" to domain.ExampleObject(summary = "s"),
                    "E2" to domain.ExampleObject(ref = "#/components/examples/E1")),
            mediaTypes =
                mapOf(
                    "M1" to domain.MediaTypeObject(),
                    "M2" to domain.MediaTypeObject(ref = "#/components/mediaTypes/M1")),
            securitySchemes =
                mapOf(
                    "S1" to domain.SecurityScheme(type = "apiKey"),
                    "S2" to
                        domain.SecurityScheme(
                            reference = domain.ReferenceObject("#/components/securitySchemes/S1"))))
    val resolvedRefs =
        openapi.parser.ComponentParser.resolveComponentRefs(
            compsWithNullRefs, "https://example.com/openapi.json")
    assertEquals("b", resolvedRefs.requestBodies["B2"]?.description)

    // PathAndOperationParser default parameter calls
    assertTrue(openapi.parser.PathAndOperationParser.parsePaths(null, null).items.isEmpty())
    val emptyObj = kotlinx.serialization.json.Json.parseToJsonElement("{}")
    assertNotNull(openapi.parser.PathAndOperationParser.parsePathItem("/a", emptyObj, null))
    assertNotNull(
        openapi.parser.PathAndOperationParser.parseOperation(
            "/a", "get", domain.HttpMethod.GET, emptyObj, null))
    assertTrue(openapi.parser.PathAndOperationParser.parseCallbacks(null, null).isEmpty())
    assertTrue(openapi.parser.PathAndOperationParser.parseParameters(null, null).isEmpty())
    assertNotNull(openapi.parser.PathAndOperationParser.parseParameter(emptyObj, null))
    assertNull(openapi.parser.PathAndOperationParser.parseRequestBody(null, null))
    assertTrue(openapi.parser.PathAndOperationParser.parseResponses(null, null).isEmpty())
    assertNotNull(openapi.parser.PathAndOperationParser.parseResponse("200", emptyObj, null))
    assertTrue(openapi.parser.PathAndOperationParser.parseHeaders(null, null).isEmpty())
    assertNotNull(openapi.parser.PathAndOperationParser.parseHeader(emptyObj, null))
    assertTrue(openapi.parser.PathAndOperationParser.parseLinks(null, null).isEmpty())
    assertNotNull(openapi.parser.PathAndOperationParser.parseLink(emptyObj, null))
    assertTrue(openapi.parser.PathAndOperationParser.parseContentMap(null, null).isEmpty())
    assertNotNull(openapi.parser.PathAndOperationParser.parseMediaTypeObject(emptyObj, null))
    assertTrue(openapi.parser.PathAndOperationParser.parseExampleMap(null, null).isEmpty())
    assertNotNull(openapi.parser.PathAndOperationParser.parseExampleObject(emptyObj, null))
    assertTrue(openapi.parser.PathAndOperationParser.parsePathItems(null).isEmpty())

    // 5. Remaining branches in ComponentParser, PathAndOperationParser, and SchemaNodeParser
    val edgeCaseSpec =
        """
        {
          "openapi": "3.2.0",
          "info": {"title": "T", "version": "1"},
          "servers": [123, {"variables": {"v": {}}}],
          "tags": [{}],
          "security": [123],
          "components": {
            "schemas": {
              "NullOnly": {"type": "null", "readOnly": true, "writeOnly": true, "discriminator": {}},
              "NumEnum": {"enum": [1, 2]},
              "BoolEnum": {"enum": [true, false]},
              "NullEnum": {"enum": [null]}
            },
            "parameters": {
              "NoName": {},
              "CycleP": {"${'$'}ref": "#/components/parameters/CycleP"}
            },
            "requestBodies": {
              "BadBody": 123
            },
            "headers": {
              "DeprH": {"deprecated": true, "example": "ex"}
            },
            "securitySchemes": {
              "NoType": {}
            },
            "examples": {
              "ExRef": {"${'$'}ref": "#/components/examples/ExRef", "description": "d"}
            }
          }
        }
        """
            .trimIndent()
    assertNotNull(parser.parseString(edgeCaseSpec))
    assertNotNull(
        openapi.parser.SchemaNodeParser.parseSchemaProperty(
            kotlinx.serialization.json.JsonPrimitive(1)))
    assertEquals(
        "https://abs.com/a.json",
        openapi.parser.ComponentParser.resolveSelfBase(
            "https://abs.com/a.json", "https://base.com/b.json"))
    assertNull(
        openapi.parser.SchemaNodeParser.selectSchema(
            mapOf("*/*" to domain.MediaTypeObject()), null))
    assertNull(
        openapi.parser.SchemaNodeParser.selectSchema(
            mapOf("invalid" to domain.MediaTypeObject()), null))
    assertNotNull(
        openapi.parser.SchemaNodeParser.selectSchema(
            mapOf(
                "application/json" to
                    domain.MediaTypeObject(
                        reference = domain.ReferenceObject("#/components/mediaTypes/M"))),
            null))
    val resolvedComp =
        openapi.parser.ComponentParser.resolveComponentRefs(
            domain.Components(
                responses =
                    mapOf(
                        "R1" to
                            domain.EndpointResponse(
                                statusCode = "200",
                                description = null,
                                reference = domain.ReferenceObject("#/components/responses/R2")),
                        "R2" to domain.EndpointResponse(statusCode = "200", description = "ok")),
                links =
                    mapOf(
                        "L1" to
                            domain.Link(
                                reference = domain.ReferenceObject("#/components/links/L2")),
                        "L2" to domain.Link(operationId = "op")),
                mediaTypes =
                    mapOf(
                        "M1" to
                            domain.MediaTypeObject(
                                reference = domain.ReferenceObject("#/components/mediaTypes/M2")),
                        "M2" to domain.MediaTypeObject())))
    assertNotNull(resolvedComp)
  }
}
