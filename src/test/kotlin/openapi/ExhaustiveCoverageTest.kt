package openapi

import java.io.File
import org.cdd.CddCli
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ExhaustiveCoverageTest {

  companion object {
    val outDir = File("build/test-out-exhaustive")
    val inFile = File("build/test-in-exhaustive.json")
    val swaggerFile = File("build/test-swagger.json")

    @JvmStatic
    @BeforeAll
    fun setup() {
      outDir.mkdirs()
    }

    @JvmStatic
    @AfterAll
    fun teardown() {
      inFile.delete()
      swaggerFile.delete()
      outDir.deleteRecursively()
    }
  }

  @Test
  fun testEverything() {
    val json =
        """
        {
          "openapi": "3.1.0",
          "jsonSchemaDialect": "https://json-schema.org/draft/2020-12/schema",
          "info": {
            "title": "Exhaustive API",
            "version": "1.0.0",
            "license": { "name": "MIT", "url": "https://opensource.org/licenses/MIT", "identifier": "MIT" },
            "contact": { "name": "API Support", "url": "http://www.example.com/support", "email": "support@example.com" }
          },
          "servers": [
            {
              "url": "https://{env}.example.com/v1",
              "variables": {
                "env": { "default": "prod", "enum": ["prod", "dev", "test"], "description": "Environment" }
              }
            }
          ],
          "paths": {
            "/something/{id}": {
              "summary": "Path Item Summary",
              "description": "Path Item Description",
              "get": {
                "summary": "Do something",
                "operationId": "doSomething",
                "parameters": [
                  { "name": "id", "in": "path", "required": true, "style": "matrix", "explode": true, "schema": { "type": "string" }, "examples": { "ex1": { "value": "123" } } },
                  { "name": "id2", "in": "path", "required": true, "style": "label", "explode": false, "schema": { "type": "array", "items": { "type": "string" } } },
                  { "name": "headerParam", "in": "header", "style": "simple", "schema": { "type": "string" } },
                  { "name": "queryParam1", "in": "query", "style": "form", "explode": false, "schema": { "type": "array", "items": { "type": "string"} } },
                  { "name": "queryParam2", "in": "query", "style": "spaceDelimited", "explode": false, "schema": { "type": "array", "items": { "type": "string"} } },
                  { "name": "queryParam3", "in": "query", "style": "pipeDelimited", "explode": false, "schema": { "type": "array", "items": { "type": "string"} } },
                  { "name": "queryParam4", "in": "query", "style": "deepObject", "explode": true, "schema": { "type": "object", "properties": { "foo": { "type": "string" } } } },
                  { "name": "cookieParam", "in": "cookie", "schema": { "type": "string" } }
                ],
                "responses": {
                  "200": {
                    "description": "OK",
                    "headers": {
                      "X-Rate-Limit": { "description": "calls per hour allowed by the user", "schema": { "type": "integer" } },
                      "X-Complex-Header": { "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ComplexModel" } } } }
                    },
                    "content": {
                      "application/json": {
                        "schema": { "${'$'}ref": "#/components/schemas/ComplexModel" },
                        "examples": { "ex": { "value": { "type": "foo", "id": "1", "foo": { "fooProp": "bar" } } } }
                      },
                      "application/xml": { "schema": { "${'$'}ref": "#/components/schemas/ComplexModel" } },
                      "multipart/form-data": {
                        "schema": { "type": "object", "properties": { "file": { "type": "string", "format": "binary" } } },
                        "encoding": {
                          "file": { "contentType": "image/png", "headers": { "X-Custom": { "schema": { "type": "string" } } }, "style": "form", "explode": true, "allowReserved": true }
                        }
                      }
                    },
                    "links": {
                      "SelfLink": { "operationId": "doSomething", "parameters": { "id": "${'$'}response.body#/id" }, "server": { "url": "https://link.example.com" } },
                      "RefLink": { "${'$'}ref": "#/components/links/MyLink" }
                    }
                  }
                },
                "callbacks": {
                  "myEvent": {
                    "${'$'}request.body#/callbackUrl": {
                      "post": {
                        "requestBody": { "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ComplexModel" } } } },
                        "responses": { "200": { "description": "OK" } }
                      }
                    }
                  },
                  "RefCallback": { "${'$'}ref": "#/components/callbacks/MyCallback" }
                },
                "security": [ { "oauth2": ["read", "write"] }, { "apiKey": [] } ]
              }
            }
          },
          "components": {
            "schemas": {
              "ComplexModel": {
                "type": "object",
                "discriminator": { "propertyName": "type", "mapping": { "foo": "#/components/schemas/Foo" } },
                "properties": {
                  "type": { "type": "string" },
                  "id": { "type": "string", "xml": { "attribute": true, "name": "identifier", "prefix": "pf", "namespace": "http://n" } },
                  "foo": { "${'$'}ref": "#/components/schemas/Foo" },
                  "arr": { "type": "array", "items": { "type": "string" }, "xml": { "wrapped": true } }
                },
                "required": ["type", "id"],
                "dependentRequired": { "foo": ["id"] },
                "additionalProperties": { "type": "integer" },
                "xml": { "name": "Complex", "namespace": "http://example.com/xml" },
                "externalDocs": { "url": "https://example.com/docs" }
              },
              "Foo": { "type": "object", "properties": { "fooProp": { "type": "string" } } },
              "EnumTest": { "type": "string", "enum": ["A", "B"] }
            },
            "securitySchemes": {
              "oauth2": {
                "type": "oauth2",
                "flows": {
                  "authorizationCode": { "authorizationUrl": "https://example.com/oauth/authorize", "tokenUrl": "https://example.com/oauth/token", "scopes": { "read": "Grants read access", "write": "Grants write access", "admin": "Grants access to admin operations" } },
                  "implicit": { "authorizationUrl": "https://example.com/oauth/authorize", "scopes": { "read": "read scope" } },
                  "password": { "tokenUrl": "https://example.com/oauth/token", "scopes": { "read": "read scope" } },
                  "clientCredentials": { "tokenUrl": "https://example.com/oauth/token", "scopes": { "read": "read scope" } }
                }
              },
              "apiKey": { "type": "apiKey", "name": "X-API-KEY", "in": "header" },
              "httpBasic": { "type": "http", "scheme": "basic" },
              "openIdConnect": { "type": "openIdConnect", "openIdConnectUrl": "https://example.com/.well-known/openid-configuration" }
            },
            "links": { "MyLink": { "operationId": "doSomething", "parameters": { "id": "${'$'}response.body#/id" } } },
            "callbacks": { "MyCallback": { "${'$'}request.body#/callbackUrl": { "post": { "responses": { "200": { "description": "Callback OK" } } } } } },
            "headers": { "MyHeader": { "description": "My Header", "schema": { "type": "string" } } }
          }
        }
        """
            .trimIndent()
    inFile.writeText(json)

    val swaggerJson =
        """
        {
          "swagger": "2.0",
          "info": { "title": "Swagger API", "version": "1.0.0" },
          "paths": { },
          "definitions": {
            "Model": {
              "type": "object",
              "properties": { "id": { "type": "integer" } }
            }
          },
          "responses": {
            "SomeResponse": {
              "description": "A response",
              "schema": { "${'$'}ref": "#/definitions/Model" }
            }
          },
          "parameters": {
            "SomeParam": { "name": "id", "in": "query", "type": "integer" }
          },
          "securityDefinitions": {
            "api_key": { "type": "apiKey", "name": "api_key", "in": "header" }
          }
        }
        """
            .trimIndent()
    swaggerFile.writeText(swaggerJson)

    val parser = OpenApiParser()
    val doc = parser.parseString(json, OpenApiParser.Format.JSON)
    val validator = OpenApiValidator()
    validator.validate(doc)

    val swDoc = parser.parseString(swaggerJson, OpenApiParser.Format.JSON)
    validator.validate(swDoc)

    val writer = OpenApiWriter()
    writer.writeDocumentToFile(
        openapi.OpenApiDocument.OpenApi(doc), File(outDir, "openapi-out.json"))
    writer.writeDocumentToFile(
        openapi.OpenApiDocument.OpenApi(doc), File(outDir, "openapi-out.yaml"))

    val exitCode =
        CddCli.generateFromOpenApi(
            arrayOf("generate", "-i", inFile.absolutePath, "-o", outDir.absolutePath))
    assertTrue(exitCode == 0, "CLI generation failed")
  }
}
