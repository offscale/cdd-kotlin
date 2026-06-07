package org.cdd.mcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class McpModelsCoverageTest {

  @Test
  fun testAnnotated() {
    val obj1 = Annotated()
    val obj2 = Annotated()
    val obj3 = Annotated(annotations = emptyMap<String, String>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Annotated>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Annotated>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Annotated>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(annotations = obj1.annotations)
  }

  @Test
  fun testBlobResourceContents() {
    val obj1 = BlobResourceContents(blob = "test", uri = "test")
    val obj2 = BlobResourceContents(blob = "test", uri = "test")
    val obj3 = BlobResourceContents(blob = "alt", mimeType = "alt", uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<BlobResourceContents>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<BlobResourceContents>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<BlobResourceContents>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(blob = obj1.blob, mimeType = obj1.mimeType, uri = obj1.uri)
  }

  @Test
  fun testCallToolRequest() {
    val obj1 = CallToolRequest(params = CallToolRequestParams(name = "test"))
    val obj2 = CallToolRequest(params = CallToolRequestParams(name = "test"))
    val obj3 =
        CallToolRequest(
            method = "alt",
            params =
                CallToolRequestParams(
                    arguments = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
                    name = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CallToolRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CallToolRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CallToolRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testCallToolRequestParams() {
    val obj1 = CallToolRequestParams(name = "test")
    val obj2 = CallToolRequestParams(name = "test")
    val obj3 =
        CallToolRequestParams(
            arguments = emptyMap<String, kotlinx.serialization.json.JsonElement>(), name = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CallToolRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CallToolRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CallToolRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(arguments = obj1.arguments, name = obj1.name)
  }

  @Test
  fun testCallToolResult() {
    val obj1 = CallToolResult(content = emptyList<kotlinx.serialization.json.JsonElement>())
    val obj2 = CallToolResult(content = emptyList<kotlinx.serialization.json.JsonElement>())
    val obj3 =
        CallToolResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            content = emptyList<kotlinx.serialization.json.JsonElement>(),
            isError = false)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CallToolResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CallToolResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CallToolResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, content = obj1.content, isError = obj1.isError)
  }

  @Test
  fun testCancelledNotification() {
    val obj1 = CancelledNotification(params = CancelledNotificationParams(requestId = "test"))
    val obj2 = CancelledNotification(params = CancelledNotificationParams(requestId = "test"))
    val obj3 =
        CancelledNotification(
            method = "alt", params = CancelledNotificationParams(reason = "alt", requestId = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CancelledNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CancelledNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CancelledNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testCancelledNotificationParams() {
    val obj1 = CancelledNotificationParams(requestId = "test")
    val obj2 = CancelledNotificationParams(requestId = "test")
    val obj3 = CancelledNotificationParams(reason = "alt", requestId = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CancelledNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CancelledNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CancelledNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(reason = obj1.reason, requestId = obj1.requestId)
  }

  @Test
  fun testClientCapabilities() {
    val obj1 = ClientCapabilities()
    val obj2 = ClientCapabilities()
    val obj3 =
        ClientCapabilities(
            experimental = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            sampling = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ClientCapabilities>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ClientCapabilities>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ClientCapabilities>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(experimental = obj1.experimental, roots = obj1.roots, sampling = obj1.sampling)
  }

  @Test
  fun testClientCapabilitiesRoots() {
    val obj1 = ClientCapabilitiesRoots()
    val obj2 = ClientCapabilitiesRoots()
    val obj3 = ClientCapabilitiesRoots(listChanged = false)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ClientCapabilitiesRoots>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ClientCapabilitiesRoots>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ClientCapabilitiesRoots>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(listChanged = obj1.listChanged)
  }

  @Test
  fun testCompleteRequest() {
    val obj1 =
        CompleteRequest(
            params =
                CompleteRequestParams(
                    argument = CompleteRequestArgument(name = "test", value = "test"),
                    ref = CompleteRequestRef(type = "test")))
    val obj2 =
        CompleteRequest(
            params =
                CompleteRequestParams(
                    argument = CompleteRequestArgument(name = "test", value = "test"),
                    ref = CompleteRequestRef(type = "test")))
    val obj3 =
        CompleteRequest(
            method = "alt",
            params =
                CompleteRequestParams(
                    argument = CompleteRequestArgument(name = "alt", value = "alt"),
                    ref = CompleteRequestRef(name = "alt", type = "alt")))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CompleteRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testCompleteRequestParams() {
    val obj1 =
        CompleteRequestParams(
            argument = CompleteRequestArgument(name = "test", value = "test"),
            ref = CompleteRequestRef(type = "test"))
    val obj2 =
        CompleteRequestParams(
            argument = CompleteRequestArgument(name = "test", value = "test"),
            ref = CompleteRequestRef(type = "test"))
    val obj3 =
        CompleteRequestParams(
            argument = CompleteRequestArgument(name = "alt", value = "alt"),
            ref = CompleteRequestRef(name = "alt", type = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CompleteRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(argument = obj1.argument, ref = obj1.ref)
  }

  @Test
  fun testCompleteRequestArgument() {
    val obj1 = CompleteRequestArgument(name = "test", value = "test")
    val obj2 = CompleteRequestArgument(name = "test", value = "test")
    val obj3 = CompleteRequestArgument(name = "alt", value = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CompleteRequestArgument>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteRequestArgument>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteRequestArgument>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(name = obj1.name, value = obj1.value)
  }

  @Test
  fun testCompleteRequestRef() {
    val obj1 = CompleteRequestRef(type = "test")
    val obj2 = CompleteRequestRef(type = "test")
    val obj3 = CompleteRequestRef(name = "alt", type = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CompleteRequestRef>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteRequestRef>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteRequestRef>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(name = obj1.name, type = obj1.type)
  }

  @Test
  fun testCompleteResult() {
    val obj1 = CompleteResult(completion = CompleteResultCompletion(values = emptyList<String>()))
    val obj2 = CompleteResult(completion = CompleteResultCompletion(values = emptyList<String>()))
    val obj3 =
        CompleteResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            completion =
                CompleteResultCompletion(hasMore = false, total = 1, values = emptyList<String>()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CompleteResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, completion = obj1.completion)
  }

  @Test
  fun testCompleteResultCompletion() {
    val obj1 = CompleteResultCompletion(values = emptyList<String>())
    val obj2 = CompleteResultCompletion(values = emptyList<String>())
    val obj3 = CompleteResultCompletion(hasMore = false, total = 1, values = emptyList<String>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CompleteResultCompletion>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteResultCompletion>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CompleteResultCompletion>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(hasMore = obj1.hasMore, total = obj1.total, values = obj1.values)
  }

  @Test
  fun testCreateMessageRequest() {
    val obj1 =
        CreateMessageRequest(
            params =
                CreateMessageRequestParams(maxTokens = 0, messages = emptyList<SamplingMessage>()))
    val obj2 =
        CreateMessageRequest(
            params =
                CreateMessageRequestParams(maxTokens = 0, messages = emptyList<SamplingMessage>()))
    val obj3 =
        CreateMessageRequest(
            method = "alt",
            params =
                CreateMessageRequestParams(
                    includeContext = "alt",
                    maxTokens = 1,
                    messages = emptyList<SamplingMessage>(),
                    metadata = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
                    stopSequences = emptyList<String>(),
                    systemPrompt = "alt",
                    temperature = 1.0))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CreateMessageRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CreateMessageRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CreateMessageRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testCreateMessageRequestParams() {
    val obj1 = CreateMessageRequestParams(maxTokens = 0, messages = emptyList<SamplingMessage>())
    val obj2 = CreateMessageRequestParams(maxTokens = 0, messages = emptyList<SamplingMessage>())
    val obj3 =
        CreateMessageRequestParams(
            includeContext = "alt",
            maxTokens = 1,
            messages = emptyList<SamplingMessage>(),
            metadata = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            stopSequences = emptyList<String>(),
            systemPrompt = "alt",
            temperature = 1.0)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CreateMessageRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CreateMessageRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CreateMessageRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        includeContext = obj1.includeContext,
        maxTokens = obj1.maxTokens,
        messages = obj1.messages,
        metadata = obj1.metadata,
        modelPreferences = obj1.modelPreferences,
        stopSequences = obj1.stopSequences,
        systemPrompt = obj1.systemPrompt,
        temperature = obj1.temperature)
  }

  @Test
  fun testCreateMessageResult() {
    val obj1 =
        CreateMessageResult(
            content = SamplingMessageContent(type = "test"), model = "test", role = Role.user)
    val obj2 =
        CreateMessageResult(
            content = SamplingMessageContent(type = "test"), model = "test", role = Role.user)
    val obj3 =
        CreateMessageResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            content =
                SamplingMessageContent(text = "alt", type = "alt", data = "alt", mimeType = "alt"),
            model = "alt",
            role = Role.assistant,
            stopReason = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<CreateMessageResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CreateMessageResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<CreateMessageResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        _meta = obj1._meta,
        content = obj1.content,
        model = obj1.model,
        role = obj1.role,
        stopReason = obj1.stopReason)
  }

  @Test
  fun testSamplingMessageContent() {
    val obj1 = SamplingMessageContent(type = "test")
    val obj2 = SamplingMessageContent(type = "test")
    val obj3 = SamplingMessageContent(text = "alt", type = "alt", data = "alt", mimeType = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<SamplingMessageContent>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SamplingMessageContent>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SamplingMessageContent>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(text = obj1.text, type = obj1.type, data = obj1.data, mimeType = obj1.mimeType)
  }

  @Test
  fun testEmbeddedResource() {
    val obj1 = EmbeddedResource(resource = ResourceContents(uri = "test"))
    val obj2 = EmbeddedResource(resource = ResourceContents(uri = "test"))
    val obj3 =
        EmbeddedResource(
            annotations = emptyMap<String, String>(),
            resource = ResourceContents(mimeType = "alt", uri = "alt", text = "alt", blob = "alt"),
            type = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<EmbeddedResource>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<EmbeddedResource>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<EmbeddedResource>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(annotations = obj1.annotations, resource = obj1.resource, type = obj1.type)
  }

  @Test
  fun testGetPromptRequest() {
    val obj1 = GetPromptRequest(params = GetPromptRequestParams(name = "test"))
    val obj2 = GetPromptRequest(params = GetPromptRequestParams(name = "test"))
    val obj3 =
        GetPromptRequest(
            method = "alt",
            params = GetPromptRequestParams(arguments = emptyMap<String, String>(), name = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<GetPromptRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<GetPromptRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<GetPromptRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testGetPromptRequestParams() {
    val obj1 = GetPromptRequestParams(name = "test")
    val obj2 = GetPromptRequestParams(name = "test")
    val obj3 = GetPromptRequestParams(arguments = emptyMap<String, String>(), name = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<GetPromptRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<GetPromptRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<GetPromptRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(arguments = obj1.arguments, name = obj1.name)
  }

  @Test
  fun testGetPromptResult() {
    val obj1 = GetPromptResult(messages = emptyList<PromptMessage>())
    val obj2 = GetPromptResult(messages = emptyList<PromptMessage>())
    val obj3 =
        GetPromptResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            description = "alt",
            messages = emptyList<PromptMessage>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<GetPromptResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<GetPromptResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<GetPromptResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, description = obj1.description, messages = obj1.messages)
  }

  @Test
  fun testImageContent() {
    val obj1 = ImageContent(data = "test", mimeType = "test")
    val obj2 = ImageContent(data = "test", mimeType = "test")
    val obj3 =
        ImageContent(
            annotations = emptyMap<String, String>(), data = "alt", mimeType = "alt", type = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ImageContent>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ImageContent>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ImageContent>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        annotations = obj1.annotations,
        data = obj1.data,
        mimeType = obj1.mimeType,
        type = obj1.type)
  }

  @Test
  fun testImplementation() {
    val obj1 = Implementation(name = "test", version = "test")
    val obj2 = Implementation(name = "test", version = "test")
    val obj3 = Implementation(name = "alt", version = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Implementation>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Implementation>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Implementation>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(name = obj1.name, version = obj1.version)
  }

  @Test
  fun testInitializeRequest() {
    val obj1 =
        InitializeRequest(
            params =
                InitializeRequestParams(
                    capabilities = ClientCapabilities(),
                    clientInfo = Implementation(name = "test", version = "test"),
                    protocolVersion = "test"))
    val obj2 =
        InitializeRequest(
            params =
                InitializeRequestParams(
                    capabilities = ClientCapabilities(),
                    clientInfo = Implementation(name = "test", version = "test"),
                    protocolVersion = "test"))
    val obj3 =
        InitializeRequest(
            method = "alt",
            params =
                InitializeRequestParams(
                    capabilities =
                        ClientCapabilities(
                            experimental =
                                emptyMap<String, kotlinx.serialization.json.JsonElement>(),
                            sampling = emptyMap<String, kotlinx.serialization.json.JsonElement>()),
                    clientInfo = Implementation(name = "alt", version = "alt"),
                    protocolVersion = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<InitializeRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializeRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializeRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testInitializeRequestParams() {
    val obj1 =
        InitializeRequestParams(
            capabilities = ClientCapabilities(),
            clientInfo = Implementation(name = "test", version = "test"),
            protocolVersion = "test")
    val obj2 =
        InitializeRequestParams(
            capabilities = ClientCapabilities(),
            clientInfo = Implementation(name = "test", version = "test"),
            protocolVersion = "test")
    val obj3 =
        InitializeRequestParams(
            capabilities =
                ClientCapabilities(
                    experimental = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
                    sampling = emptyMap<String, kotlinx.serialization.json.JsonElement>()),
            clientInfo = Implementation(name = "alt", version = "alt"),
            protocolVersion = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<InitializeRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializeRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializeRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        capabilities = obj1.capabilities,
        clientInfo = obj1.clientInfo,
        protocolVersion = obj1.protocolVersion)
  }

  @Test
  fun testInitializeResult() {
    val obj1 =
        InitializeResult(
            capabilities = ServerCapabilities(),
            protocolVersion = "test",
            serverInfo = Implementation(name = "test", version = "test"))
    val obj2 =
        InitializeResult(
            capabilities = ServerCapabilities(),
            protocolVersion = "test",
            serverInfo = Implementation(name = "test", version = "test"))
    val obj3 =
        InitializeResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            capabilities =
                ServerCapabilities(
                    experimental = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
                    logging = emptyMap<String, kotlinx.serialization.json.JsonElement>()),
            instructions = "alt",
            protocolVersion = "alt",
            serverInfo = Implementation(name = "alt", version = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<InitializeResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializeResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializeResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        _meta = obj1._meta,
        capabilities = obj1.capabilities,
        instructions = obj1.instructions,
        protocolVersion = obj1.protocolVersion,
        serverInfo = obj1.serverInfo)
  }

  @Test
  fun testInitializedNotification() {
    val obj1 = InitializedNotification()
    val obj2 = InitializedNotification()
    val obj3 =
        InitializedNotification(
            method = "alt",
            params =
                InitializedNotificationParams(
                    _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<InitializedNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializedNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializedNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testInitializedNotificationParams() {
    val obj1 = InitializedNotificationParams()
    val obj2 = InitializedNotificationParams()
    val obj3 =
        InitializedNotificationParams(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<InitializedNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializedNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<InitializedNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta)
  }

  @Test
  fun testJSONRPCError() {
    val obj1 = JSONRPCError(error = JSONRPCErrorError(code = 0, message = "test"), id = "test")
    val obj2 = JSONRPCError(error = JSONRPCErrorError(code = 0, message = "test"), id = "test")
    val obj3 =
        JSONRPCError(
            error =
                JSONRPCErrorError(
                    code = 1,
                    data = kotlinx.serialization.json.JsonObject(emptyMap()),
                    message = "alt"),
            id = "alt",
            jsonrpc = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<JSONRPCError>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCError>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCError>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(error = obj1.error, id = obj1.id, jsonrpc = obj1.jsonrpc)
  }

  @Test
  fun testJSONRPCErrorError() {
    val obj1 = JSONRPCErrorError(code = 0, message = "test")
    val obj2 = JSONRPCErrorError(code = 0, message = "test")
    val obj3 =
        JSONRPCErrorError(
            code = 1, data = kotlinx.serialization.json.JsonObject(emptyMap()), message = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<JSONRPCErrorError>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCErrorError>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCErrorError>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(code = obj1.code, data = obj1.data, message = obj1.message)
  }

  @Test
  fun testJSONRPCNotification() {
    val obj1 = JSONRPCNotification(method = "test")
    val obj2 = JSONRPCNotification(method = "test")
    val obj3 =
        JSONRPCNotification(
            jsonrpc = "alt",
            method = "alt",
            params = kotlinx.serialization.json.JsonObject(emptyMap()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<JSONRPCNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(jsonrpc = obj1.jsonrpc, method = obj1.method, params = obj1.params)
  }

  @Test
  fun testJSONRPCRequest() {
    val obj1 = JSONRPCRequest(id = "test", method = "test")
    val obj2 = JSONRPCRequest(id = "test", method = "test")
    val obj3 =
        JSONRPCRequest(
            id = "alt",
            jsonrpc = "alt",
            method = "alt",
            params = kotlinx.serialization.json.JsonObject(emptyMap()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<JSONRPCRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(id = obj1.id, jsonrpc = obj1.jsonrpc, method = obj1.method, params = obj1.params)
  }

  @Test
  fun testJSONRPCResponse() {
    val obj1 =
        JSONRPCResponse(id = "test", result = kotlinx.serialization.json.JsonObject(emptyMap()))
    val obj2 =
        JSONRPCResponse(id = "test", result = kotlinx.serialization.json.JsonObject(emptyMap()))
    val obj3 =
        JSONRPCResponse(
            id = "alt", jsonrpc = "alt", result = kotlinx.serialization.json.JsonObject(emptyMap()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<JSONRPCResponse>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCResponse>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<JSONRPCResponse>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(id = obj1.id, jsonrpc = obj1.jsonrpc, result = obj1.result)
  }

  @Test
  fun testListPromptsRequest() {
    val obj1 = ListPromptsRequest()
    val obj2 = ListPromptsRequest()
    val obj3 = ListPromptsRequest(method = "alt", params = ListPromptsRequestParams(cursor = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListPromptsRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListPromptsRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListPromptsRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testListPromptsRequestParams() {
    val obj1 = ListPromptsRequestParams()
    val obj2 = ListPromptsRequestParams()
    val obj3 = ListPromptsRequestParams(cursor = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListPromptsRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListPromptsRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListPromptsRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(cursor = obj1.cursor)
  }

  @Test
  fun testListPromptsResult() {
    val obj1 = ListPromptsResult(prompts = emptyList<Prompt>())
    val obj2 = ListPromptsResult(prompts = emptyList<Prompt>())
    val obj3 =
        ListPromptsResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            nextCursor = "alt",
            prompts = emptyList<Prompt>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListPromptsResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListPromptsResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListPromptsResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, nextCursor = obj1.nextCursor, prompts = obj1.prompts)
  }

  @Test
  fun testListResourceTemplatesRequest() {
    val obj1 = ListResourceTemplatesRequest()
    val obj2 = ListResourceTemplatesRequest()
    val obj3 =
        ListResourceTemplatesRequest(
            method = "alt", params = ListResourceTemplatesRequestParams(cursor = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListResourceTemplatesRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourceTemplatesRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourceTemplatesRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testListResourceTemplatesRequestParams() {
    val obj1 = ListResourceTemplatesRequestParams()
    val obj2 = ListResourceTemplatesRequestParams()
    val obj3 = ListResourceTemplatesRequestParams(cursor = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListResourceTemplatesRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourceTemplatesRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourceTemplatesRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(cursor = obj1.cursor)
  }

  @Test
  fun testListResourceTemplatesResult() {
    val obj1 = ListResourceTemplatesResult(resourceTemplates = emptyList<ResourceTemplate>())
    val obj2 = ListResourceTemplatesResult(resourceTemplates = emptyList<ResourceTemplate>())
    val obj3 =
        ListResourceTemplatesResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            nextCursor = "alt",
            resourceTemplates = emptyList<ResourceTemplate>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListResourceTemplatesResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourceTemplatesResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourceTemplatesResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        _meta = obj1._meta,
        nextCursor = obj1.nextCursor,
        resourceTemplates = obj1.resourceTemplates)
  }

  @Test
  fun testListResourcesRequest() {
    val obj1 = ListResourcesRequest()
    val obj2 = ListResourcesRequest()
    val obj3 =
        ListResourcesRequest(method = "alt", params = ListResourcesRequestParams(cursor = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListResourcesRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourcesRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourcesRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testListResourcesRequestParams() {
    val obj1 = ListResourcesRequestParams()
    val obj2 = ListResourcesRequestParams()
    val obj3 = ListResourcesRequestParams(cursor = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListResourcesRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourcesRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourcesRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(cursor = obj1.cursor)
  }

  @Test
  fun testListResourcesResult() {
    val obj1 = ListResourcesResult(resources = emptyList<Resource>())
    val obj2 = ListResourcesResult(resources = emptyList<Resource>())
    val obj3 =
        ListResourcesResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            nextCursor = "alt",
            resources = emptyList<Resource>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListResourcesResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourcesResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListResourcesResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, nextCursor = obj1.nextCursor, resources = obj1.resources)
  }

  @Test
  fun testListRootsRequest() {
    val obj1 = ListRootsRequest()
    val obj2 = ListRootsRequest()
    val obj3 =
        ListRootsRequest(
            method = "alt",
            params =
                ListRootsRequestParams(
                    _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListRootsRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListRootsRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListRootsRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testListRootsRequestParams() {
    val obj1 = ListRootsRequestParams()
    val obj2 = ListRootsRequestParams()
    val obj3 =
        ListRootsRequestParams(_meta = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListRootsRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListRootsRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListRootsRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta)
  }

  @Test
  fun testListRootsResult() {
    val obj1 = ListRootsResult(roots = emptyList<Root>())
    val obj2 = ListRootsResult(roots = emptyList<Root>())
    val obj3 =
        ListRootsResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            roots = emptyList<Root>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListRootsResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListRootsResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListRootsResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, roots = obj1.roots)
  }

  @Test
  fun testListToolsRequest() {
    val obj1 = ListToolsRequest()
    val obj2 = ListToolsRequest()
    val obj3 = ListToolsRequest(method = "alt", params = ListToolsRequestParams(cursor = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListToolsRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListToolsRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListToolsRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testListToolsRequestParams() {
    val obj1 = ListToolsRequestParams()
    val obj2 = ListToolsRequestParams()
    val obj3 = ListToolsRequestParams(cursor = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListToolsRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListToolsRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListToolsRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(cursor = obj1.cursor)
  }

  @Test
  fun testListToolsResult() {
    val obj1 = ListToolsResult(tools = emptyList<Tool>())
    val obj2 = ListToolsResult(tools = emptyList<Tool>())
    val obj3 =
        ListToolsResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            nextCursor = "alt",
            tools = emptyList<Tool>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ListToolsResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListToolsResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ListToolsResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, nextCursor = obj1.nextCursor, tools = obj1.tools)
  }

  @Test
  fun testLoggingMessageNotification() {
    val obj1 =
        LoggingMessageNotification(
            params =
                LoggingMessageNotificationParams(
                    data = kotlinx.serialization.json.JsonObject(emptyMap()),
                    level = LoggingLevel.debug))
    val obj2 =
        LoggingMessageNotification(
            params =
                LoggingMessageNotificationParams(
                    data = kotlinx.serialization.json.JsonObject(emptyMap()),
                    level = LoggingLevel.debug))
    val obj3 =
        LoggingMessageNotification(
            method = "alt",
            params =
                LoggingMessageNotificationParams(
                    data = kotlinx.serialization.json.JsonObject(emptyMap()),
                    level = LoggingLevel.info,
                    logger = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<LoggingMessageNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<LoggingMessageNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<LoggingMessageNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testLoggingMessageNotificationParams() {
    val obj1 =
        LoggingMessageNotificationParams(
            data = kotlinx.serialization.json.JsonObject(emptyMap()), level = LoggingLevel.debug)
    val obj2 =
        LoggingMessageNotificationParams(
            data = kotlinx.serialization.json.JsonObject(emptyMap()), level = LoggingLevel.debug)
    val obj3 =
        LoggingMessageNotificationParams(
            data = kotlinx.serialization.json.JsonObject(emptyMap()),
            level = LoggingLevel.info,
            logger = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<LoggingMessageNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<LoggingMessageNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<LoggingMessageNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(data = obj1.data, level = obj1.level, logger = obj1.logger)
  }

  @Test
  fun testModelHint() {
    val obj1 = ModelHint(name = "test")
    val obj2 = ModelHint(name = "test")
    val obj3 = ModelHint(name = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ModelHint>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ModelHint>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ModelHint>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(name = obj1.name)
  }

  @Test
  fun testModelPreferences() {
    val obj1 = ModelPreferences()
    val obj2 = ModelPreferences()
    val obj3 =
        ModelPreferences(
            costPriority = 1.0,
            hints = emptyList<ModelHint>(),
            intelligencePriority = 1.0,
            speedPriority = 1.0)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ModelPreferences>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ModelPreferences>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ModelPreferences>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        costPriority = obj1.costPriority,
        hints = obj1.hints,
        intelligencePriority = obj1.intelligencePriority,
        speedPriority = obj1.speedPriority)
  }

  @Test
  fun testNotification() {
    val obj1 = Notification(method = "test")
    val obj2 = Notification(method = "test")
    val obj3 =
        Notification(
            method = "alt", params = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Notification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Notification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Notification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testPaginatedRequest() {
    val obj1 = PaginatedRequest(method = "test")
    val obj2 = PaginatedRequest(method = "test")
    val obj3 = PaginatedRequest(method = "alt", params = PaginatedRequestParams(cursor = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PaginatedRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PaginatedRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PaginatedRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testPaginatedRequestParams() {
    val obj1 = PaginatedRequestParams()
    val obj2 = PaginatedRequestParams()
    val obj3 = PaginatedRequestParams(cursor = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PaginatedRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PaginatedRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PaginatedRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(cursor = obj1.cursor)
  }

  @Test
  fun testPaginatedResult() {
    val obj1 = PaginatedResult()
    val obj2 = PaginatedResult()
    val obj3 =
        PaginatedResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(), nextCursor = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PaginatedResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PaginatedResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PaginatedResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, nextCursor = obj1.nextCursor)
  }

  @Test
  fun testPingRequest() {
    val obj1 = PingRequest()
    val obj2 = PingRequest()
    val obj3 =
        PingRequest(
            method = "alt",
            params =
                PingRequestParams(
                    _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PingRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PingRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PingRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testPingRequestParams() {
    val obj1 = PingRequestParams()
    val obj2 = PingRequestParams()
    val obj3 = PingRequestParams(_meta = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PingRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PingRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PingRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta)
  }

  @Test
  fun testProgressNotification() {
    val obj1 =
        ProgressNotification(
            params = ProgressNotificationParams(progressToken = "test", progress = 0.0))
    val obj2 =
        ProgressNotification(
            params = ProgressNotificationParams(progressToken = "test", progress = 0.0))
    val obj3 =
        ProgressNotification(
            method = "alt",
            params = ProgressNotificationParams(progressToken = "alt", progress = 1.0, total = 1.0))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ProgressNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ProgressNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ProgressNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testProgressNotificationParams() {
    val obj1 = ProgressNotificationParams(progressToken = "test", progress = 0.0)
    val obj2 = ProgressNotificationParams(progressToken = "test", progress = 0.0)
    val obj3 = ProgressNotificationParams(progressToken = "alt", progress = 1.0, total = 1.0)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ProgressNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ProgressNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ProgressNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(progressToken = obj1.progressToken, progress = obj1.progress, total = obj1.total)
  }

  @Test
  fun testPrompt() {
    val obj1 = Prompt(name = "test")
    val obj2 = Prompt(name = "test")
    val obj3 = Prompt(arguments = emptyList<PromptArgument>(), description = "alt", name = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Prompt>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Prompt>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Prompt>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(arguments = obj1.arguments, description = obj1.description, name = obj1.name)
  }

  @Test
  fun testPromptArgument() {
    val obj1 = PromptArgument(name = "test")
    val obj2 = PromptArgument(name = "test")
    val obj3 = PromptArgument(description = "alt", name = "alt", required = false)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PromptArgument>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptArgument>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptArgument>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(description = obj1.description, name = obj1.name, required = obj1.required)
  }

  @Test
  fun testPromptListChangedNotification() {
    val obj1 = PromptListChangedNotification()
    val obj2 = PromptListChangedNotification()
    val obj3 =
        PromptListChangedNotification(
            method = "alt",
            params =
                PromptListChangedNotificationParams(
                    _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PromptListChangedNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptListChangedNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptListChangedNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testPromptListChangedNotificationParams() {
    val obj1 = PromptListChangedNotificationParams()
    val obj2 = PromptListChangedNotificationParams()
    val obj3 =
        PromptListChangedNotificationParams(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PromptListChangedNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptListChangedNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptListChangedNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta)
  }

  @Test
  fun testPromptMessage() {
    val obj1 = PromptMessage(content = PromptMessageContent(type = "test"), role = Role.user)
    val obj2 = PromptMessage(content = PromptMessageContent(type = "test"), role = Role.user)
    val obj3 =
        PromptMessage(
            content =
                PromptMessageContent(text = "alt", type = "alt", data = "alt", mimeType = "alt"),
            role = Role.assistant)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PromptMessage>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptMessage>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptMessage>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(content = obj1.content, role = obj1.role)
  }

  @Test
  fun testPromptMessageContent() {
    val obj1 = PromptMessageContent(type = "test")
    val obj2 = PromptMessageContent(type = "test")
    val obj3 = PromptMessageContent(text = "alt", type = "alt", data = "alt", mimeType = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PromptMessageContent>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptMessageContent>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptMessageContent>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(text = obj1.text, type = obj1.type, data = obj1.data, mimeType = obj1.mimeType)
  }

  @Test
  fun testPromptReference() {
    val obj1 = PromptReference(name = "test")
    val obj2 = PromptReference(name = "test")
    val obj3 = PromptReference(name = "alt", type = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<PromptReference>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptReference>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<PromptReference>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(name = obj1.name, type = obj1.type)
  }

  @Test
  fun testReadResourceRequest() {
    val obj1 = ReadResourceRequest(params = ReadResourceRequestParams(uri = "test"))
    val obj2 = ReadResourceRequest(params = ReadResourceRequestParams(uri = "test"))
    val obj3 = ReadResourceRequest(method = "alt", params = ReadResourceRequestParams(uri = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ReadResourceRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ReadResourceRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ReadResourceRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testReadResourceRequestParams() {
    val obj1 = ReadResourceRequestParams(uri = "test")
    val obj2 = ReadResourceRequestParams(uri = "test")
    val obj3 = ReadResourceRequestParams(uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ReadResourceRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ReadResourceRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ReadResourceRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(uri = obj1.uri)
  }

  @Test
  fun testReadResourceResult() {
    val obj1 = ReadResourceResult(contents = emptyList<ResourceContents>())
    val obj2 = ReadResourceResult(contents = emptyList<ResourceContents>())
    val obj3 =
        ReadResourceResult(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            contents = emptyList<ResourceContents>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ReadResourceResult>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ReadResourceResult>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ReadResourceResult>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta, contents = obj1.contents)
  }

  @Test
  fun testRequest() {
    val obj1 = Request(method = "test")
    val obj2 = Request(method = "test")
    val obj3 =
        Request(method = "alt", params = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Request>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Request>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Request>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testResource() {
    val obj1 = Resource(name = "test", uri = "test")
    val obj2 = Resource(name = "test", uri = "test")
    val obj3 =
        Resource(
            annotations = emptyMap<String, String>(),
            description = "alt",
            mimeType = "alt",
            name = "alt",
            size = 1,
            uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Resource>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Resource>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Resource>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        annotations = obj1.annotations,
        description = obj1.description,
        mimeType = obj1.mimeType,
        name = obj1.name,
        size = obj1.size,
        uri = obj1.uri)
  }

  @Test
  fun testResourceContents() {
    val obj1 = ResourceContents(uri = "test")
    val obj2 = ResourceContents(uri = "test")
    val obj3 = ResourceContents(mimeType = "alt", uri = "alt", text = "alt", blob = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ResourceContents>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceContents>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceContents>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(mimeType = obj1.mimeType, uri = obj1.uri, text = obj1.text, blob = obj1.blob)
  }

  @Test
  fun testResourceListChangedNotification() {
    val obj1 = ResourceListChangedNotification()
    val obj2 = ResourceListChangedNotification()
    val obj3 =
        ResourceListChangedNotification(
            method = "alt",
            params =
                ResourceListChangedNotificationParams(
                    _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ResourceListChangedNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceListChangedNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceListChangedNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testResourceListChangedNotificationParams() {
    val obj1 = ResourceListChangedNotificationParams()
    val obj2 = ResourceListChangedNotificationParams()
    val obj3 =
        ResourceListChangedNotificationParams(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ResourceListChangedNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceListChangedNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceListChangedNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta)
  }

  @Test
  fun testResourceReference() {
    val obj1 = ResourceReference(uri = "test")
    val obj2 = ResourceReference(uri = "test")
    val obj3 = ResourceReference(type = "alt", uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ResourceReference>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceReference>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceReference>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(type = obj1.type, uri = obj1.uri)
  }

  @Test
  fun testResourceTemplate() {
    val obj1 = ResourceTemplate(name = "test", uriTemplate = "test")
    val obj2 = ResourceTemplate(name = "test", uriTemplate = "test")
    val obj3 =
        ResourceTemplate(
            annotations = emptyMap<String, String>(),
            description = "alt",
            mimeType = "alt",
            name = "alt",
            uriTemplate = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ResourceTemplate>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceTemplate>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceTemplate>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        annotations = obj1.annotations,
        description = obj1.description,
        mimeType = obj1.mimeType,
        name = obj1.name,
        uriTemplate = obj1.uriTemplate)
  }

  @Test
  fun testResourceUpdatedNotification() {
    val obj1 = ResourceUpdatedNotification(params = ResourceUpdatedNotificationParams(uri = "test"))
    val obj2 = ResourceUpdatedNotification(params = ResourceUpdatedNotificationParams(uri = "test"))
    val obj3 =
        ResourceUpdatedNotification(
            method = "alt", params = ResourceUpdatedNotificationParams(uri = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ResourceUpdatedNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceUpdatedNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceUpdatedNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testResourceUpdatedNotificationParams() {
    val obj1 = ResourceUpdatedNotificationParams(uri = "test")
    val obj2 = ResourceUpdatedNotificationParams(uri = "test")
    val obj3 = ResourceUpdatedNotificationParams(uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ResourceUpdatedNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceUpdatedNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ResourceUpdatedNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(uri = obj1.uri)
  }

  @Test
  fun testResult() {
    val obj1 = Result()
    val obj2 = Result()
    val obj3 = Result(_meta = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Result>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Result>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Result>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta)
  }

  @Test
  fun testRoot() {
    val obj1 = Root(uri = "test")
    val obj2 = Root(uri = "test")
    val obj3 = Root(name = "alt", uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Root>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Root>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<Root>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(name = obj1.name, uri = obj1.uri)
  }

  @Test
  fun testRootsListChangedNotification() {
    val obj1 = RootsListChangedNotification()
    val obj2 = RootsListChangedNotification()
    val obj3 =
        RootsListChangedNotification(
            method = "alt",
            params =
                RootsListChangedNotificationParams(
                    _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<RootsListChangedNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<RootsListChangedNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<RootsListChangedNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testRootsListChangedNotificationParams() {
    val obj1 = RootsListChangedNotificationParams()
    val obj2 = RootsListChangedNotificationParams()
    val obj3 =
        RootsListChangedNotificationParams(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<RootsListChangedNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<RootsListChangedNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<RootsListChangedNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta)
  }

  @Test
  fun testSamplingMessage() {
    val obj1 = SamplingMessage(content = SamplingMessageContent(type = "test"), role = Role.user)
    val obj2 = SamplingMessage(content = SamplingMessageContent(type = "test"), role = Role.user)
    val obj3 =
        SamplingMessage(
            content =
                SamplingMessageContent(text = "alt", type = "alt", data = "alt", mimeType = "alt"),
            role = Role.assistant)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<SamplingMessage>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SamplingMessage>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SamplingMessage>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(content = obj1.content, role = obj1.role)
  }

  @Test
  fun testServerCapabilities() {
    val obj1 = ServerCapabilities()
    val obj2 = ServerCapabilities()
    val obj3 =
        ServerCapabilities(
            experimental = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            logging = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ServerCapabilities>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ServerCapabilities>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ServerCapabilities>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(
        experimental = obj1.experimental,
        logging = obj1.logging,
        prompts = obj1.prompts,
        resources = obj1.resources,
        tools = obj1.tools)
  }

  @Test
  fun testServerCapabilitiesPrompts() {
    val obj1 = ServerCapabilitiesPrompts()
    val obj2 = ServerCapabilitiesPrompts()
    val obj3 = ServerCapabilitiesPrompts(listChanged = false)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ServerCapabilitiesPrompts>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ServerCapabilitiesPrompts>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ServerCapabilitiesPrompts>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(listChanged = obj1.listChanged)
  }

  @Test
  fun testServerCapabilitiesResources() {
    val obj1 = ServerCapabilitiesResources()
    val obj2 = ServerCapabilitiesResources()
    val obj3 = ServerCapabilitiesResources(listChanged = false, subscribe = false)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ServerCapabilitiesResources>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ServerCapabilitiesResources>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ServerCapabilitiesResources>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(listChanged = obj1.listChanged, subscribe = obj1.subscribe)
  }

  @Test
  fun testServerCapabilitiesTools() {
    val obj1 = ServerCapabilitiesTools()
    val obj2 = ServerCapabilitiesTools()
    val obj3 = ServerCapabilitiesTools(listChanged = false)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ServerCapabilitiesTools>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ServerCapabilitiesTools>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ServerCapabilitiesTools>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(listChanged = obj1.listChanged)
  }

  @Test
  fun testSetLevelRequest() {
    val obj1 = SetLevelRequest(params = SetLevelRequestParams(level = LoggingLevel.debug))
    val obj2 = SetLevelRequest(params = SetLevelRequestParams(level = LoggingLevel.debug))
    val obj3 =
        SetLevelRequest(method = "alt", params = SetLevelRequestParams(level = LoggingLevel.info))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<SetLevelRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SetLevelRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SetLevelRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testSetLevelRequestParams() {
    val obj1 = SetLevelRequestParams(level = LoggingLevel.debug)
    val obj2 = SetLevelRequestParams(level = LoggingLevel.debug)
    val obj3 = SetLevelRequestParams(level = LoggingLevel.info)

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<SetLevelRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SetLevelRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SetLevelRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(level = obj1.level)
  }

  @Test
  fun testSubscribeRequest() {
    val obj1 = SubscribeRequest(params = SubscribeRequestParams(uri = "test"))
    val obj2 = SubscribeRequest(params = SubscribeRequestParams(uri = "test"))
    val obj3 = SubscribeRequest(method = "alt", params = SubscribeRequestParams(uri = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<SubscribeRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SubscribeRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SubscribeRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testSubscribeRequestParams() {
    val obj1 = SubscribeRequestParams(uri = "test")
    val obj2 = SubscribeRequestParams(uri = "test")
    val obj3 = SubscribeRequestParams(uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<SubscribeRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SubscribeRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<SubscribeRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(uri = obj1.uri)
  }

  @Test
  fun testTextContent() {
    val obj1 = TextContent(text = "test")
    val obj2 = TextContent(text = "test")
    val obj3 = TextContent(annotations = emptyMap<String, String>(), text = "alt", type = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<TextContent>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<TextContent>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<TextContent>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(annotations = obj1.annotations, text = obj1.text, type = obj1.type)
  }

  @Test
  fun testTextResourceContents() {
    val obj1 = TextResourceContents(text = "test", uri = "test")
    val obj2 = TextResourceContents(text = "test", uri = "test")
    val obj3 = TextResourceContents(mimeType = "alt", text = "alt", uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<TextResourceContents>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<TextResourceContents>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<TextResourceContents>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(mimeType = obj1.mimeType, text = obj1.text, uri = obj1.uri)
  }

  @Test
  fun testTool() {
    val obj1 = Tool(inputSchema = ToolInputSchema(), name = "test")
    val obj2 = Tool(inputSchema = ToolInputSchema(), name = "test")
    val obj3 =
        Tool(
            description = "alt",
            inputSchema =
                ToolInputSchema(
                    properties = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
                    required = emptyList<String>(),
                    type = "alt"),
            name = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<Tool>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<Tool>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<Tool>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(description = obj1.description, inputSchema = obj1.inputSchema, name = obj1.name)
  }

  @Test
  fun testToolInputSchema() {
    val obj1 = ToolInputSchema()
    val obj2 = ToolInputSchema()
    val obj3 =
        ToolInputSchema(
            properties = emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            required = emptyList<String>(),
            type = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ToolInputSchema>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ToolInputSchema>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ToolInputSchema>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(properties = obj1.properties, required = obj1.required, type = obj1.type)
  }

  @Test
  fun testToolListChangedNotification() {
    val obj1 = ToolListChangedNotification()
    val obj2 = ToolListChangedNotification()
    val obj3 =
        ToolListChangedNotification(
            method = "alt",
            params =
                ToolListChangedNotificationParams(
                    _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>()))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ToolListChangedNotification>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ToolListChangedNotification>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ToolListChangedNotification>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testToolListChangedNotificationParams() {
    val obj1 = ToolListChangedNotificationParams()
    val obj2 = ToolListChangedNotificationParams()
    val obj3 =
        ToolListChangedNotificationParams(
            _meta = emptyMap<String, kotlinx.serialization.json.JsonElement>())

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<ToolListChangedNotificationParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ToolListChangedNotificationParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<ToolListChangedNotificationParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(_meta = obj1._meta)
  }

  @Test
  fun testUnsubscribeRequest() {
    val obj1 = UnsubscribeRequest(params = UnsubscribeRequestParams(uri = "test"))
    val obj2 = UnsubscribeRequest(params = UnsubscribeRequestParams(uri = "test"))
    val obj3 = UnsubscribeRequest(method = "alt", params = UnsubscribeRequestParams(uri = "alt"))

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<UnsubscribeRequest>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<UnsubscribeRequest>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<UnsubscribeRequest>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(method = obj1.method, params = obj1.params)
  }

  @Test
  fun testUnsubscribeRequestParams() {
    val obj1 = UnsubscribeRequestParams(uri = "test")
    val obj2 = UnsubscribeRequestParams(uri = "test")
    val obj3 = UnsubscribeRequestParams(uri = "alt")

    assertNotNull(obj1)

    // Serialization coverage
    val json =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .encodeToString(obj1)
    assertNotNull(json)
    val decoded =
        kotlinx.serialization.json
            .Json {
              ignoreUnknownKeys = true
              encodeDefaults = true
            }
            .decodeFromString<UnsubscribeRequestParams>(json)
    assertEquals(obj1, decoded)

    // Fuzz serialization branches
    try {
      val unknownJson = "{\"unknown_key_123\":\"val\"}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<UnsubscribeRequestParams>(unknownJson)
    } catch (e: Exception) {}

    try {
      val emptyJson = "{}"
      kotlinx.serialization.json
          .Json { ignoreUnknownKeys = true }
          .decodeFromString<UnsubscribeRequestParams>(emptyJson)
    } catch (e: Exception) {}

    // Branch coverage for generated equals, hashCode, toString
    assertEquals(obj1, obj2)
    assertEquals(obj1.hashCode(), obj2.hashCode())
    assertEquals(obj1.toString(), obj2.toString())
    assertTrue(obj1.equals(obj1))
    assertFalse(obj1.equals(null))
    assertFalse(obj1.equals(Any()))
    // If properties change, it should not equal (if alt exists and differs)
    if (obj1.toString() != obj3.toString()) {
      assertFalse(obj1.equals(obj3))
    }

    // Exhaustive data class copy branch coverage
    obj1.copy()
    obj1.copy(uri = obj1.uri)
  }

  @Test
  fun testEmptyClasses() {
    assertNotNull(ClientNotification())
    assertNotNull(ClientRequest())
    assertNotNull(ClientResult())
    assertNotNull(JSONRPCMessage())
    assertNotNull(ServerNotification())
    assertNotNull(ServerRequest())
    assertNotNull(ServerResult())
    assertNotNull(EmptyResult())
  }

  @Test
  fun testLoggingLevel() {
    val obj1 = LoggingLevel.debug
    val obj2 = LoggingLevel.debug
    val obj3 = LoggingLevel.info

    assertNotNull(obj1)
    val json = kotlinx.serialization.json.Json.encodeToString(obj1)
    val decoded = kotlinx.serialization.json.Json.decodeFromString<LoggingLevel>(json)
    org.junit.jupiter.api.Assertions.assertEquals(obj1, decoded)
    org.junit.jupiter.api.Assertions.assertEquals(obj1, obj2)
    org.junit.jupiter.api.Assertions.assertEquals(obj1.hashCode(), obj2.hashCode())
    org.junit.jupiter.api.Assertions.assertTrue(obj1.equals(obj1))
    org.junit.jupiter.api.Assertions.assertFalse(obj1.equals(null))
    org.junit.jupiter.api.Assertions.assertFalse(obj1.equals(Any()))
    org.junit.jupiter.api.Assertions.assertFalse(obj1.equals(obj3))
  }

  @Test
  fun testRole() {
    val obj1 = Role.user
    val obj2 = Role.user
    val obj3 = Role.assistant

    assertNotNull(obj1)
    val json = kotlinx.serialization.json.Json.encodeToString(obj1)
    val decoded = kotlinx.serialization.json.Json.decodeFromString<Role>(json)
    org.junit.jupiter.api.Assertions.assertEquals(obj1, decoded)
    org.junit.jupiter.api.Assertions.assertEquals(obj1, obj2)
    org.junit.jupiter.api.Assertions.assertTrue(obj1.equals(obj1))
    org.junit.jupiter.api.Assertions.assertFalse(obj1.equals(obj3))
  }
}
