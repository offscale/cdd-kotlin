import os

models = {
    'Annotated': {'annotations': 'Map<String, String>? = null'},
    'BlobResourceContents': {'blob': 'String', 'mimeType': 'String? = null', 'uri': 'String'},
    'CallToolRequest': {'method': 'String = "tools/call"', 'params': 'CallToolRequestParams'},
    'CallToolRequestParams': {'arguments': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'name': 'String'},
    'CallToolResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'content': 'List<kotlinx.serialization.json.JsonElement>', 'isError': 'Boolean? = null'},
    'CancelledNotification': {'method': 'String = "notifications/cancelled"', 'params': 'CancelledNotificationParams'},
    'CancelledNotificationParams': {'reason': 'String? = null', 'requestId': 'String'},
    'ClientCapabilities': {'experimental': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'roots': 'ClientCapabilitiesRoots? = null', 'sampling': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'ClientCapabilitiesRoots': {'listChanged': 'Boolean? = null'},
    'CompleteRequest': {'method': 'String = "completion/complete"', 'params': 'CompleteRequestParams'},
    'CompleteRequestParams': {'argument': 'CompleteRequestArgument', 'ref': 'CompleteRequestRef'},
    'CompleteRequestArgument': {'name': 'String', 'value': 'String'},
    'CompleteRequestRef': {'name': 'String? = null', 'type': 'String'},
    'CompleteResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'completion': 'CompleteResultCompletion'},
    'CompleteResultCompletion': {'hasMore': 'Boolean? = null', 'total': 'Int? = null', 'values': 'List<String>'},
    'CreateMessageRequest': {'method': 'String = "sampling/createMessage"', 'params': 'CreateMessageRequestParams'},
    'CreateMessageRequestParams': {'includeContext': 'String? = null', 'maxTokens': 'Int', 'messages': 'List<SamplingMessage>', 'metadata': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'modelPreferences': 'ModelPreferences? = null', 'stopSequences': 'List<String>? = null', 'systemPrompt': 'String? = null', 'temperature': 'Double? = null'},
    'CreateMessageResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'content': 'SamplingMessageContent', 'model': 'String', 'role': 'Role', 'stopReason': 'String? = null'},
    'SamplingMessageContent': {'text': 'String? = null', 'type': 'String', 'data': 'String? = null', 'mimeType': 'String? = null'},
    'EmbeddedResource': {'annotations': 'Map<String, String>? = null', 'resource': 'ResourceContents', 'type': 'String = "resource"'},
    'GetPromptRequest': {'method': 'String = "prompts/get"', 'params': 'GetPromptRequestParams'},
    'GetPromptRequestParams': {'arguments': 'Map<String, String>? = null', 'name': 'String'},
    'GetPromptResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'description': 'String? = null', 'messages': 'List<PromptMessage>'},
    'ImageContent': {'annotations': 'Map<String, String>? = null', 'data': 'String', 'mimeType': 'String', 'type': 'String = "image"'},
    'Implementation': {'name': 'String', 'version': 'String'},
    'InitializeRequest': {'method': 'String = "initialize"', 'params': 'InitializeRequestParams'},
    'InitializeRequestParams': {'capabilities': 'ClientCapabilities', 'clientInfo': 'Implementation', 'protocolVersion': 'String'},
    'InitializeResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'capabilities': 'ServerCapabilities', 'instructions': 'String? = null', 'protocolVersion': 'String', 'serverInfo': 'Implementation'},
    'InitializedNotification': {'method': 'String = "notifications/initialized"', 'params': 'InitializedNotificationParams? = null'},
    'InitializedNotificationParams': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'JSONRPCError': {'error': 'JSONRPCErrorError', 'id': 'String', 'jsonrpc': 'String = "2.0"'},
    'JSONRPCErrorError': {'code': 'Int', 'data': 'kotlinx.serialization.json.JsonElement? = null', 'message': 'String'},
    'JSONRPCNotification': {'jsonrpc': 'String = "2.0"', 'method': 'String', 'params': 'kotlinx.serialization.json.JsonElement? = null'},
    'JSONRPCRequest': {'id': 'String', 'jsonrpc': 'String = "2.0"', 'method': 'String', 'params': 'kotlinx.serialization.json.JsonElement? = null'},
    'JSONRPCResponse': {'id': 'String', 'jsonrpc': 'String = "2.0"', 'result': 'kotlinx.serialization.json.JsonElement'},
    'ListPromptsRequest': {'method': 'String = "prompts/list"', 'params': 'ListPromptsRequestParams? = null'},
    'ListPromptsRequestParams': {'cursor': 'String? = null'},
    'ListPromptsResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'nextCursor': 'String? = null', 'prompts': 'List<Prompt>'},
    'ListResourceTemplatesRequest': {'method': 'String = "resources/templates/list"', 'params': 'ListResourceTemplatesRequestParams? = null'},
    'ListResourceTemplatesRequestParams': {'cursor': 'String? = null'},
    'ListResourceTemplatesResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'nextCursor': 'String? = null', 'resourceTemplates': 'List<ResourceTemplate>'},
    'ListResourcesRequest': {'method': 'String = "resources/list"', 'params': 'ListResourcesRequestParams? = null'},
    'ListResourcesRequestParams': {'cursor': 'String? = null'},
    'ListResourcesResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'nextCursor': 'String? = null', 'resources': 'List<Resource>'},
    'ListRootsRequest': {'method': 'String = "roots/list"', 'params': 'ListRootsRequestParams? = null'},
    'ListRootsRequestParams': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'ListRootsResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'roots': 'List<Root>'},
    'ListToolsRequest': {'method': 'String = "tools/list"', 'params': 'ListToolsRequestParams? = null'},
    'ListToolsRequestParams': {'cursor': 'String? = null'},
    'ListToolsResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'nextCursor': 'String? = null', 'tools': 'List<Tool>'},
    'LoggingMessageNotification': {'method': 'String = "notifications/message"', 'params': 'LoggingMessageNotificationParams'},
    'LoggingMessageNotificationParams': {'data': 'kotlinx.serialization.json.JsonElement', 'level': 'LoggingLevel', 'logger': 'String? = null'},
    'ModelHint': {'name': 'String'},
    'ModelPreferences': {'costPriority': 'Double? = null', 'hints': 'List<ModelHint>? = null', 'intelligencePriority': 'Double? = null', 'speedPriority': 'Double? = null'},
    'Notification': {'method': 'String', 'params': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'PaginatedRequest': {'method': 'String', 'params': 'PaginatedRequestParams? = null'},
    'PaginatedRequestParams': {'cursor': 'String? = null'},
    'PaginatedResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'nextCursor': 'String? = null'},
    'PingRequest': {'method': 'String = "ping"', 'params': 'PingRequestParams? = null'},
    'PingRequestParams': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'ProgressNotification': {'method': 'String = "notifications/progress"', 'params': 'ProgressNotificationParams'},
    'ProgressNotificationParams': {'progressToken': 'String', 'progress': 'Double', 'total': 'Double? = null'},
    'Prompt': {'arguments': 'List<PromptArgument>? = null', 'description': 'String? = null', 'name': 'String'},
    'PromptArgument': {'description': 'String? = null', 'name': 'String', 'required': 'Boolean? = null'},
    'PromptListChangedNotification': {'method': 'String = "notifications/prompts/list_changed"', 'params': 'PromptListChangedNotificationParams? = null'},
    'PromptListChangedNotificationParams': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'PromptMessage': {'content': 'PromptMessageContent', 'role': 'Role'},
    'PromptMessageContent': {'text': 'String? = null', 'type': 'String', 'data': 'String? = null', 'mimeType': 'String? = null'},
    'PromptReference': {'name': 'String', 'type': 'String = "prompt"'},
    'ReadResourceRequest': {'method': 'String = "resources/read"', 'params': 'ReadResourceRequestParams'},
    'ReadResourceRequestParams': {'uri': 'String'},
    'ReadResourceResult': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'contents': 'List<ResourceContents>'},
    'Request': {'method': 'String', 'params': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'Resource': {'annotations': 'Map<String, String>? = null', 'description': 'String? = null', 'mimeType': 'String? = null', 'name': 'String', 'size': 'Int? = null', 'uri': 'String'},
    'ResourceContents': {'mimeType': 'String? = null', 'uri': 'String', 'text': 'String? = null', 'blob': 'String? = null'},
    'ResourceListChangedNotification': {'method': 'String = "notifications/resources/list_changed"', 'params': 'ResourceListChangedNotificationParams? = null'},
    'ResourceListChangedNotificationParams': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'ResourceReference': {'type': 'String = "resource"', 'uri': 'String'},
    'ResourceTemplate': {'annotations': 'Map<String, String>? = null', 'description': 'String? = null', 'mimeType': 'String? = null', 'name': 'String', 'uriTemplate': 'String'},
    'ResourceUpdatedNotification': {'method': 'String = "notifications/resources/updated"', 'params': 'ResourceUpdatedNotificationParams'},
    'ResourceUpdatedNotificationParams': {'uri': 'String'},
    'Result': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'Root': {'name': 'String? = null', 'uri': 'String'},
    'RootsListChangedNotification': {'method': 'String = "notifications/roots/list_changed"', 'params': 'RootsListChangedNotificationParams? = null'},
    'RootsListChangedNotificationParams': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'SamplingMessage': {'content': 'SamplingMessageContent', 'role': 'Role'},
    'ServerCapabilities': {'experimental': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'logging': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'prompts': 'ServerCapabilitiesPrompts? = null', 'resources': 'ServerCapabilitiesResources? = null', 'tools': 'ServerCapabilitiesTools? = null'},
    'ServerCapabilitiesPrompts': {'listChanged': 'Boolean? = null'},
    'ServerCapabilitiesResources': {'listChanged': 'Boolean? = null', 'subscribe': 'Boolean? = null'},
    'ServerCapabilitiesTools': {'listChanged': 'Boolean? = null'},
    'SetLevelRequest': {'method': 'String = "logging/setLevel"', 'params': 'SetLevelRequestParams'},
    'SetLevelRequestParams': {'level': 'LoggingLevel'},
    'SubscribeRequest': {'method': 'String = "resources/subscribe"', 'params': 'SubscribeRequestParams'},
    'SubscribeRequestParams': {'uri': 'String'},
    'TextContent': {'annotations': 'Map<String, String>? = null', 'text': 'String', 'type': 'String = "text"'},
    'TextResourceContents': {'mimeType': 'String? = null', 'text': 'String', 'uri': 'String'},
    'Tool': {'description': 'String? = null', 'inputSchema': 'ToolInputSchema', 'name': 'String'},
    'ToolInputSchema': {'properties': 'Map<String, kotlinx.serialization.json.JsonElement>? = null', 'required': 'List<String>? = null', 'type': 'String = "object"'},
    'ToolListChangedNotification': {'method': 'String = "notifications/tools/list_changed"', 'params': 'ToolListChangedNotificationParams? = null'},
    'ToolListChangedNotificationParams': {'_meta': 'Map<String, kotlinx.serialization.json.JsonElement>? = null'},
    'UnsubscribeRequest': {'method': 'String = "resources/unsubscribe"', 'params': 'UnsubscribeRequestParams'},
    'UnsubscribeRequestParams': {'uri': 'String'}
}

out_test = """package org.cdd.mcp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class McpModelsCoverageTest {
"""

def generate_args(model_name, alternate=False):
    args = []
    props = models[model_name]
    for p, t in props.items():
        if alternate:
            if "String" in t and "List" not in t and "Map" not in t:
                args.append(f'{p} = "alt"')
            elif "LoggingLevel" in t:
                args.append(f"{p} = LoggingLevel.info")
            elif "Role" in t:
                args.append(f"{p} = Role.assistant")
            elif "Boolean" in t:
                args.append(f"{p} = false")
            elif "Int" in t:
                args.append(f"{p} = 1")
            elif "Double" in t:
                args.append(f"{p} = 1.0")
            elif "List<" in t:
                list_type = t.split("?")[0].split("=")[0].strip()
                inner = list_type[list_type.find('<')+1:list_type.rfind('>')]
                args.append(f"{p} = emptyList<{inner}>()")
            elif "Map<" in t:
                map_type = t.split("?")[0].split("=")[0].strip()
                inner = map_type[map_type.find('<')+1:map_type.rfind('>')]
                args.append(f"{p} = emptyMap<{inner}>()")
            elif "kotlinx.serialization.json.JsonElement" in t:
                args.append(f"{p} = kotlinx.serialization.json.JsonObject(emptyMap())")
            elif "Params" in t:
                nested_args = generate_args(t.split('?')[0], alternate=True)
                args.append(f"{p} = {t.split('?')[0]}({nested_args})")
            elif t in models:
                nested_args = generate_args(t.split('?')[0], alternate=True)
                args.append(f"{p} = {t.split('?')[0]}({nested_args})")
        else:
            if "= null" in t:
                pass # can omit
            elif "String =" in t:
                pass # Has default
            elif "List<" in t and "?" not in t:
                list_type = t.split("?")[0].split("=")[0].strip()
                inner = list_type[list_type.find('<')+1:list_type.rfind('>')]
                args.append(f"{p} = emptyList<{inner}>()")
            elif "Map<" in t and "?" not in t:
                map_type = t.split("?")[0].split("=")[0].strip()
                inner = map_type[map_type.find('<')+1:map_type.rfind('>')]
                args.append(f"{p} = emptyMap<{inner}>()")
            elif "String" in t and "List" not in t and "Map" not in t and "?" not in t:
                args.append(f'{p} = "test"')
            elif "LoggingLevel" in t and "?" not in t:
                args.append(f"{p} = LoggingLevel.debug")
            elif "Role" in t and "?" not in t:
                args.append(f"{p} = Role.user")

            elif "Boolean" in t and "?" not in t:
                args.append(f"{p} = true")
            elif "Int" in t and "?" not in t:
                args.append(f"{p} = 0")
            elif "Double" in t and "?" not in t:
                args.append(f"{p} = 0.0")
            elif "kotlinx.serialization.json.JsonElement" in t and "?" not in t:
                args.append(f"{p} = kotlinx.serialization.json.JsonObject(emptyMap())")
            elif "Params" in t and "?" not in t:
                nested_args = generate_args(t.split('?')[0])
                args.append(f"{p} = {t.split('?')[0]}({nested_args})")
            elif t in models:
                nested_args = generate_args(t.split('?')[0])
                args.append(f"{p} = {t.split('?')[0]}({nested_args})")
    return ", ".join(args)





for m, props in models.items():
    if not props: continue
    
    args_str = generate_args(m)
    args_str_alt = generate_args(m, alternate=True)
    
    copy_all_args = ", ".join([f"{p} = obj1.{p}" for p in props.keys()])
    
    out_test += f"""
    @Test
    fun test{m}() {{
        val obj1 = {m}({args_str})
        val obj2 = {m}({args_str})
        val obj3 = {m}({args_str_alt})

        assertNotNull(obj1)
        
        // Serialization coverage
        val json = kotlinx.serialization.json.Json {{
            ignoreUnknownKeys = true
            encodeDefaults = true
        }}.encodeToString(obj1)
        assertNotNull(json)
        val decoded = kotlinx.serialization.json.Json {{
            ignoreUnknownKeys = true
            encodeDefaults = true
        }}.decodeFromString<{m}>(json)
        assertEquals(obj1, decoded)
        
        // Fuzz serialization branches
        try {{
            val unknownJson = "{{\\"unknown_key_123\\":\\"val\\"}}"
            kotlinx.serialization.json.Json {{
                ignoreUnknownKeys = true
            }}.decodeFromString<{m}>(unknownJson)
        }} catch(e: Exception) {{}}
        
        try {{
            val emptyJson = "{{}}"
            kotlinx.serialization.json.Json {{
                ignoreUnknownKeys = true
            }}.decodeFromString<{m}>(emptyJson)
        }} catch(e: Exception) {{}}
        
        // Branch coverage for generated equals, hashCode, toString
        assertEquals(obj1, obj2)
        assertEquals(obj1.hashCode(), obj2.hashCode())
        assertEquals(obj1.toString(), obj2.toString())
        assertTrue(obj1.equals(obj1))
        assertFalse(obj1.equals(null))
        assertFalse(obj1.equals(Any()))
        // If properties change, it should not equal (if alt exists and differs)
        if (obj1.toString() != obj3.toString()) {{
            assertFalse(obj1.equals(obj3))
        }}
        
        // Exhaustive data class copy branch coverage
        obj1.copy()
        obj1.copy({copy_all_args})
    }}
"""
with open("src/test/kotlin/org/cdd/mcp/McpModelsCoverageTest.kt", "w") as f:
    f.write(out_test + """
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
""" + "}\n")

print("Test generated.")
