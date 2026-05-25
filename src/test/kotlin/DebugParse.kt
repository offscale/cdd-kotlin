import domain.*
import openapi.*

fun main() {
  val jsonStr =
      """{
      "openapi": "3.0.0",
      "info": { "title": "API", "version": "1.0" },
      "paths": {
        "/path1": {
          "get": { "responses": {"200": { "description": "OK" }} },
          "post": { "responses": {"200": { "description": "OK" }} }
        },
        "/path2": {
          "put": { "responses": {"200": { "description": "OK" }} }
        }
      }
    }"""
  val doc = OpenApiParser().parseDocumentString(jsonStr) as OpenApiDocument.OpenApi
  println("Paths: " + doc.definition.paths.size)
}
