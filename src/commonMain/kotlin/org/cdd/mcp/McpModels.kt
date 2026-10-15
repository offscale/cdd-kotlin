/**
 * Model Context Protocol (MCP) 1.0.0 data models, JSON-RPC message envelopes, and request/response
 * sealed hierarchies.
 */
package org.cdd.mcp

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

/** Represents the Annotated MCP model. */
@Serializable
data class Annotated(
    /** The annotations property. */
    @Required val annotations: Map<String, String>? = null,
)

/** Represents the BlobResourceContents MCP model. */
@Serializable
data class BlobResourceContents(
    /** The blob property. */
    val blob: String,
    /** The mimeType property. */
    val mimeType: String? = null,
    /** The uri property. */
    val uri: String,
)

/** Represents the CallToolRequest MCP model. */
@Serializable
data class CallToolRequest(
    /** The method property. */
    val method: String = "tools/call",
    /** The params property. */
    val params: CallToolRequestParams,
) : ClientRequest

/** Represents the CallToolRequestParams MCP model. */
@Serializable
data class CallToolRequestParams(
    /** The arguments property. */
    val arguments: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The name property. */
    val name: String,
)

/** Represents the CallToolResult MCP model. */
@Serializable
data class CallToolResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The content property. */
    val content: List<kotlinx.serialization.json.JsonElement>,
    /** The isError property. */
    val isError: Boolean? = null,
) : ServerResult

/** Represents the CancelledNotification MCP model. */
@Serializable
data class CancelledNotification(
    /** The method property. */
    val method: String = "notifications/cancelled",
    /** The params property. */
    val params: CancelledNotificationParams,
) : ClientNotification, ServerNotification

/** Represents the CancelledNotificationParams MCP model. */
@Serializable
data class CancelledNotificationParams(
    /** The reason property. */
    val reason: String? = null,
    /** The requestId property. */
    val requestId: String,
)

/** Represents the ClientCapabilities MCP model. */
@Serializable
data class ClientCapabilities(
    /** The experimental property. */
    @Required val experimental: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The roots property. */
    val roots: ClientCapabilitiesRoots? = null,
    /** The sampling property. */
    val sampling: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the ClientCapabilitiesRoots MCP model. */
@Serializable
data class ClientCapabilitiesRoots(
    /** The listChanged property. */
    @Required val listChanged: Boolean? = null,
)

/** Represents the CompleteRequest MCP model. */
@Serializable
data class CompleteRequest(
    /** The method property. */
    val method: String = "completion/complete",
    /** The params property. */
    val params: CompleteRequestParams,
) : ClientRequest

/** Represents the CompleteRequestParams MCP model. */
@Serializable
data class CompleteRequestParams(
    /** The argument property. */
    val argument: CompleteRequestArgument,
    /** The ref property. */
    val ref: CompleteRequestRef,
)

/** Represents the CompleteRequestArgument MCP model. */
@Serializable
data class CompleteRequestArgument(
    /** The name property. */
    val name: String,
    /** The value property. */
    val value: String,
)

/** Represents the CompleteRequestRef MCP model. */
@Serializable
data class CompleteRequestRef(
    /** The name property. */
    val name: String? = null,
    /** The type property. */
    val type: String,
)

/** Represents the CompleteResult MCP model. */
@Serializable
data class CompleteResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The completion property. */
    val completion: CompleteResultCompletion,
) : ServerResult

/** Represents the CompleteResultCompletion MCP model. */
@Serializable
data class CompleteResultCompletion(
    /** The hasMore property. */
    val hasMore: Boolean? = null,
    /** The total property. */
    val total: Int? = null,
    /** The values property. */
    val values: List<String>,
)

/** Represents the CreateMessageRequest MCP model. */
@Serializable
data class CreateMessageRequest(
    /** The method property. */
    val method: String = "sampling/createMessage",
    /** The params property. */
    val params: CreateMessageRequestParams,
) : ServerRequest

/** Represents the CreateMessageRequestParams MCP model. */
@Serializable
data class CreateMessageRequestParams(
    /** The includeContext property. */
    val includeContext: String? = null,
    /** The maxTokens property. */
    val maxTokens: Int,
    /** The messages property. */
    val messages: List<SamplingMessage>,
    /** The metadata property. */
    val metadata: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The modelPreferences property. */
    val modelPreferences: ModelPreferences? = null,
    /** The stopSequences property. */
    val stopSequences: List<String>? = null,
    /** The systemPrompt property. */
    val systemPrompt: String? = null,
    /** The temperature property. */
    val temperature: Double? = null,
)

/** Represents the CreateMessageResult MCP model. */
@Serializable
data class CreateMessageResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The content property. */
    val content: SamplingMessageContent,
    /** The model property. */
    val model: String,
    /** The role property. */
    val role: Role,
    /** The stopReason property. */
    val stopReason: String? = null,
) : ClientResult

/** Represents the SamplingMessageContent MCP model. */
@Serializable
data class SamplingMessageContent(
    /** The text property. */
    val text: String? = null,
    /** The type property. */
    val type: String,
    /** The data property. */
    val data: String? = null,
    /** The mimeType property. */
    val mimeType: String? = null,
)

/** Represents the EmbeddedResource MCP model. */
@Serializable
data class EmbeddedResource(
    /** The annotations property. */
    val annotations: Map<String, String>? = null,
    /** The resource property. */
    val resource: ResourceContents,
    /** The type property. */
    val type: String = "resource",
)

/** Represents the GetPromptRequest MCP model. */
@Serializable
data class GetPromptRequest(
    /** The method property. */
    val method: String = "prompts/get",
    /** The params property. */
    val params: GetPromptRequestParams,
) : ClientRequest

/** Represents the GetPromptRequestParams MCP model. */
@Serializable
data class GetPromptRequestParams(
    /** The arguments property. */
    val arguments: Map<String, String>? = null,
    /** The name property. */
    val name: String,
)

/** Represents the GetPromptResult MCP model. */
@Serializable
data class GetPromptResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The description property. */
    val description: String? = null,
    /** The messages property. */
    val messages: List<PromptMessage>,
) : ServerResult

/** Represents the ImageContent MCP model. */
@Serializable
data class ImageContent(
    /** The annotations property. */
    val annotations: Map<String, String>? = null,
    /** The data property. */
    val data: String,
    /** The mimeType property. */
    val mimeType: String,
    /** The type property. */
    val type: String = "image",
)

/** Represents the Implementation MCP model. */
@Serializable
data class Implementation(
    /** The name property. */
    val name: String,
    /** The version property. */
    val version: String,
)

/** Represents the InitializeRequest MCP model. */
@Serializable
data class InitializeRequest(
    /** The method property. */
    val method: String = "initialize",
    /** The params property. */
    val params: InitializeRequestParams,
) : ClientRequest

/** Represents the InitializeRequestParams MCP model. */
@Serializable
data class InitializeRequestParams(
    /** The capabilities property. */
    val capabilities: ClientCapabilities,
    /** The clientInfo property. */
    val clientInfo: Implementation,
    /** The protocolVersion property. */
    val protocolVersion: String,
)

/** Represents the InitializeResult MCP model. */
@Serializable
data class InitializeResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The capabilities property. */
    val capabilities: ServerCapabilities,
    /** The instructions property. */
    val instructions: String? = null,
    /** The protocolVersion property. */
    val protocolVersion: String,
    /** The serverInfo property. */
    val serverInfo: Implementation,
) : ServerResult

/** Represents the InitializedNotification MCP model. */
@Serializable
data class InitializedNotification(
    /** The method property. */
    @Required val method: String = "notifications/initialized",
    /** The params property. */
    val params: InitializedNotificationParams? = null,
) : ClientNotification

/** Represents the InitializedNotificationParams MCP model. */
@Serializable
data class InitializedNotificationParams(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the JSONRPCError MCP model. */
@Serializable
data class JSONRPCError(
    /** The error property. */
    val error: JSONRPCErrorError,
    /** The id property. */
    val id: String,
    /** The jsonrpc property. */
    val jsonrpc: String = "2.0",
) : JSONRPCMessage

/** Represents the JSONRPCErrorError MCP model. */
@Serializable
data class JSONRPCErrorError(
    /** The code property. */
    val code: Int,
    /** The data property. */
    val data: kotlinx.serialization.json.JsonElement? = null,
    /** The message property. */
    val message: String,
)

/** Represents the JSONRPCNotification MCP model. */
@Serializable
data class JSONRPCNotification(
    /** The jsonrpc property. */
    val jsonrpc: String = "2.0",
    /** The method property. */
    val method: String,
    /** The params property. */
    val params: kotlinx.serialization.json.JsonElement? = null,
) : JSONRPCMessage

/** Represents the JSONRPCRequest MCP model. */
@Serializable
data class JSONRPCRequest(
    /** The id property. */
    val id: String,
    /** The jsonrpc property. */
    val jsonrpc: String = "2.0",
    /** The method property. */
    val method: String,
    /** The params property. */
    val params: kotlinx.serialization.json.JsonElement? = null,
) : JSONRPCMessage

/** Represents the JSONRPCResponse MCP model. */
@Serializable
data class JSONRPCResponse(
    /** The id property. */
    val id: String,
    /** The jsonrpc property. */
    val jsonrpc: String = "2.0",
    /** The result property. */
    val result: kotlinx.serialization.json.JsonElement,
) : JSONRPCMessage

/** Represents the ListPromptsRequest MCP model. */
@Serializable
data class ListPromptsRequest(
    /** The method property. */
    @Required val method: String = "prompts/list",
    /** The params property. */
    val params: ListPromptsRequestParams? = null,
) : ClientRequest

/** Represents the ListPromptsRequestParams MCP model. */
@Serializable
data class ListPromptsRequestParams(
    /** The cursor property. */
    @Required val cursor: String? = null,
)

/** Represents the ListPromptsResult MCP model. */
@Serializable
data class ListPromptsResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The nextCursor property. */
    val nextCursor: String? = null,
    /** The prompts property. */
    val prompts: List<Prompt>,
) : ServerResult

/** Represents the ListResourceTemplatesRequest MCP model. */
@Serializable
data class ListResourceTemplatesRequest(
    /** The method property. */
    @Required val method: String = "resources/templates/list",
    /** The params property. */
    val params: ListResourceTemplatesRequestParams? = null,
) : ClientRequest

/** Represents the ListResourceTemplatesRequestParams MCP model. */
@Serializable
data class ListResourceTemplatesRequestParams(
    /** The cursor property. */
    @Required val cursor: String? = null,
)

/** Represents the ListResourceTemplatesResult MCP model. */
@Serializable
data class ListResourceTemplatesResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The nextCursor property. */
    val nextCursor: String? = null,
    /** The resourceTemplates property. */
    val resourceTemplates: List<ResourceTemplate>,
) : ServerResult

/** Represents the ListResourcesRequest MCP model. */
@Serializable
data class ListResourcesRequest(
    /** The method property. */
    @Required val method: String = "resources/list",
    /** The params property. */
    val params: ListResourcesRequestParams? = null,
) : ClientRequest

/** Represents the ListResourcesRequestParams MCP model. */
@Serializable
data class ListResourcesRequestParams(
    /** The cursor property. */
    @Required val cursor: String? = null,
)

/** Represents the ListResourcesResult MCP model. */
@Serializable
data class ListResourcesResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The nextCursor property. */
    val nextCursor: String? = null,
    /** The resources property. */
    val resources: List<Resource>,
) : ServerResult

/** Represents the ListRootsRequest MCP model. */
@Serializable
data class ListRootsRequest(
    /** The method property. */
    @Required val method: String = "roots/list",
    /** The params property. */
    val params: ListRootsRequestParams? = null,
) : ServerRequest

/** Represents the ListRootsRequestParams MCP model. */
@Serializable
data class ListRootsRequestParams(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the ListRootsResult MCP model. */
@Serializable
data class ListRootsResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The roots property. */
    val roots: List<Root>,
) : ClientResult

/** Represents the ListToolsRequest MCP model. */
@Serializable
data class ListToolsRequest(
    /** The method property. */
    @Required val method: String = "tools/list",
    /** The params property. */
    val params: ListToolsRequestParams? = null,
) : ClientRequest

/** Represents the ListToolsRequestParams MCP model. */
@Serializable
data class ListToolsRequestParams(
    /** The cursor property. */
    @Required val cursor: String? = null,
)

/** Represents the ListToolsResult MCP model. */
@Serializable
data class ListToolsResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The nextCursor property. */
    val nextCursor: String? = null,
    /** The tools property. */
    val tools: List<Tool>,
) : ServerResult

/** Represents the LoggingLevel MCP model. */
@Serializable
enum class LoggingLevel {
  /** Debug-level log message. */
  @kotlinx.serialization.SerialName("debug") debug,
  /** Informational log message. */
  @kotlinx.serialization.SerialName("info") info,
  /** Normal but significant condition log message. */
  @kotlinx.serialization.SerialName("notice") notice,
  /** Warning condition log message. */
  @kotlinx.serialization.SerialName("warning") warning,
  /** Error condition log message. */
  @kotlinx.serialization.SerialName("error") error,
  /** Critical condition log message. */
  @kotlinx.serialization.SerialName("critical") critical,
  /** Action must be taken immediately log message. */
  @kotlinx.serialization.SerialName("alert") alert,
  /** System is unusable log message. */
  @kotlinx.serialization.SerialName("emergency") emergency
}

/** Represents the LoggingMessageNotification MCP model. */
@Serializable
data class LoggingMessageNotification(
    /** The method property. */
    val method: String = "notifications/message",
    /** The params property. */
    val params: LoggingMessageNotificationParams,
) : ServerNotification

/** Represents the LoggingMessageNotificationParams MCP model. */
@Serializable
data class LoggingMessageNotificationParams(
    /** The data property. */
    val data: kotlinx.serialization.json.JsonElement,
    /** The level property. */
    val level: LoggingLevel,
    /** The logger property. */
    val logger: String? = null,
)

/** Represents the ModelHint MCP model. */
@Serializable
data class ModelHint(
    /** The name property. */
    val name: String,
)

/** Represents the ModelPreferences MCP model. */
@Serializable
data class ModelPreferences(
    /** The costPriority property. */
    @Required val costPriority: Double? = null,
    /** The hints property. */
    val hints: List<ModelHint>? = null,
    /** The intelligencePriority property. */
    val intelligencePriority: Double? = null,
    /** The speedPriority property. */
    val speedPriority: Double? = null,
)

/** Represents the Notification MCP model. */
@Serializable
data class Notification(
    /** The method property. */
    val method: String,
    /** The params property. */
    val params: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the PaginatedRequest MCP model. */
@Serializable
data class PaginatedRequest(
    /** The method property. */
    val method: String,
    /** The params property. */
    val params: PaginatedRequestParams? = null,
)

/** Represents the PaginatedRequestParams MCP model. */
@Serializable
data class PaginatedRequestParams(
    /** The cursor property. */
    @Required val cursor: String? = null,
)

/** Represents the PaginatedResult MCP model. */
@Serializable
data class PaginatedResult(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The nextCursor property. */
    val nextCursor: String? = null,
)

/** Represents the PingRequest MCP model. */
@Serializable
data class PingRequest(
    /** The method property. */
    @Required val method: String = "ping",
    /** The params property. */
    val params: PingRequestParams? = null,
) : ClientRequest, ServerRequest

/** Represents the PingRequestParams MCP model. */
@Serializable
data class PingRequestParams(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the ProgressNotification MCP model. */
@Serializable
data class ProgressNotification(
    /** The method property. */
    val method: String = "notifications/progress",
    /** The params property. */
    val params: ProgressNotificationParams,
) : ClientNotification, ServerNotification

/** Represents the ProgressNotificationParams MCP model. */
@Serializable
data class ProgressNotificationParams(
    /** The progressToken property. */
    val progressToken: String,
    /** The progress property. */
    val progress: Double,
    /** The total property. */
    val total: Double? = null,
)

/** Represents the Prompt MCP model. */
@Serializable
data class Prompt(
    /** The arguments property. */
    val arguments: List<PromptArgument>? = null,
    /** The description property. */
    val description: String? = null,
    /** The name property. */
    val name: String,
)

/** Represents the PromptArgument MCP model. */
@Serializable
data class PromptArgument(
    /** The description property. */
    val description: String? = null,
    /** The name property. */
    val name: String,
    /** The required property. */
    val required: Boolean? = null,
)

/** Represents the PromptListChangedNotification MCP model. */
@Serializable
data class PromptListChangedNotification(
    /** The method property. */
    @Required val method: String = "notifications/prompts/list_changed",
    /** The params property. */
    val params: PromptListChangedNotificationParams? = null,
) : ServerNotification

/** Represents the PromptListChangedNotificationParams MCP model. */
@Serializable
data class PromptListChangedNotificationParams(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the PromptMessage MCP model. */
@Serializable
data class PromptMessage(
    /** The content property. */
    val content: PromptMessageContent,
    /** The role property. */
    val role: Role,
)

/** Represents the PromptMessageContent MCP model. */
@Serializable
data class PromptMessageContent(
    /** The text property. */
    val text: String? = null,
    /** The type property. */
    val type: String,
    /** The data property. */
    val data: String? = null,
    /** The mimeType property. */
    val mimeType: String? = null,
)

/** Represents the PromptReference MCP model. */
@Serializable
data class PromptReference(
    /** The name property. */
    val name: String,
    /** The type property. */
    val type: String = "prompt",
)

/** Represents the ReadResourceRequest MCP model. */
@Serializable
data class ReadResourceRequest(
    /** The method property. */
    val method: String = "resources/read",
    /** The params property. */
    val params: ReadResourceRequestParams,
) : ClientRequest

/** Represents the ReadResourceRequestParams MCP model. */
@Serializable
data class ReadResourceRequestParams(
    /** The uri property. */
    val uri: String,
)

/** Represents the ReadResourceResult MCP model. */
@Serializable
data class ReadResourceResult(
    /** The _meta property. */
    val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The contents property. */
    val contents: List<ResourceContents>,
) : ServerResult

/** Represents the Request MCP model. */
@Serializable
data class Request(
    /** The method property. */
    val method: String,
    /** The params property. */
    val params: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the Resource MCP model. */
@Serializable
data class Resource(
    /** The annotations property. */
    val annotations: Map<String, String>? = null,
    /** The description property. */
    val description: String? = null,
    /** The mimeType property. */
    val mimeType: String? = null,
    /** The name property. */
    val name: String,
    /** The size property. */
    val size: Int? = null,
    /** The uri property. */
    val uri: String,
)

/** Represents the ResourceContents MCP model. */
@Serializable
data class ResourceContents(
    /** The mimeType property. */
    val mimeType: String? = null,
    /** The uri property. */
    val uri: String,
    /** The text property. */
    val text: String? = null,
    /** The blob property. */
    val blob: String? = null,
)

/** Represents the ResourceListChangedNotification MCP model. */
@Serializable
data class ResourceListChangedNotification(
    /** The method property. */
    @Required val method: String = "notifications/resources/list_changed",
    /** The params property. */
    val params: ResourceListChangedNotificationParams? = null,
) : ServerNotification

/** Represents the ResourceListChangedNotificationParams MCP model. */
@Serializable
data class ResourceListChangedNotificationParams(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the ResourceReference MCP model. */
@Serializable
data class ResourceReference(
    /** The type property. */
    val type: String = "resource",
    /** The uri property. */
    val uri: String,
)

/** Represents the ResourceTemplate MCP model. */
@Serializable
data class ResourceTemplate(
    /** The annotations property. */
    val annotations: Map<String, String>? = null,
    /** The description property. */
    val description: String? = null,
    /** The mimeType property. */
    val mimeType: String? = null,
    /** The name property. */
    val name: String,
    /** The uriTemplate property. */
    val uriTemplate: String,
)

/** Represents the ResourceUpdatedNotification MCP model. */
@Serializable
data class ResourceUpdatedNotification(
    /** The method property. */
    val method: String = "notifications/resources/updated",
    /** The params property. */
    val params: ResourceUpdatedNotificationParams,
) : ServerNotification

/** Represents the ResourceUpdatedNotificationParams MCP model. */
@Serializable
data class ResourceUpdatedNotificationParams(
    /** The uri property. */
    val uri: String,
)

/** Represents the Result MCP model. */
@Serializable
data class Result(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
) : ClientResult, ServerResult

/** Represents the Root MCP model. */
@Serializable
data class Root(
    /** The name property. */
    val name: String? = null,
    /** The uri property. */
    val uri: String,
)

/** Represents the RootsListChangedNotification MCP model. */
@Serializable
data class RootsListChangedNotification(
    /** The method property. */
    @Required val method: String = "notifications/roots/list_changed",
    /** The params property. */
    val params: RootsListChangedNotificationParams? = null,
) : ClientNotification

/** Represents the RootsListChangedNotificationParams MCP model. */
@Serializable
data class RootsListChangedNotificationParams(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the SamplingMessage MCP model. */
@Serializable
data class SamplingMessage(
    /** The content property. */
    val content: SamplingMessageContent,
    /** The role property. */
    val role: Role,
)

/** Represents the ServerCapabilities MCP model. */
@Serializable
data class ServerCapabilities(
    /** The experimental property. */
    @Required val experimental: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The logging property. */
    val logging: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The prompts property. */
    val prompts: ServerCapabilitiesPrompts? = null,
    /** The resources property. */
    val resources: ServerCapabilitiesResources? = null,
    /** The tools property. */
    val tools: ServerCapabilitiesTools? = null,
)

/** Represents the ServerCapabilitiesPrompts MCP model. */
@Serializable
data class ServerCapabilitiesPrompts(
    /** The listChanged property. */
    @Required val listChanged: Boolean? = null,
)

/** Represents the ServerCapabilitiesResources MCP model. */
@Serializable
data class ServerCapabilitiesResources(
    /** The listChanged property. */
    @Required val listChanged: Boolean? = null,
    /** The subscribe property. */
    val subscribe: Boolean? = null,
)

/** Represents the ServerCapabilitiesTools MCP model. */
@Serializable
data class ServerCapabilitiesTools(
    /** The listChanged property. */
    @Required val listChanged: Boolean? = null,
)

/** Represents the SetLevelRequest MCP model. */
@Serializable
data class SetLevelRequest(
    /** The method property. */
    val method: String = "logging/setLevel",
    /** The params property. */
    val params: SetLevelRequestParams,
) : ClientRequest

/** Represents the SetLevelRequestParams MCP model. */
@Serializable
data class SetLevelRequestParams(
    /** The level property. */
    val level: LoggingLevel,
)

/** Represents the SubscribeRequest MCP model. */
@Serializable
data class SubscribeRequest(
    /** The method property. */
    val method: String = "resources/subscribe",
    /** The params property. */
    val params: SubscribeRequestParams,
) : ClientRequest

/** Represents the SubscribeRequestParams MCP model. */
@Serializable
data class SubscribeRequestParams(
    /** The uri property. */
    val uri: String,
)

/** Represents the TextContent MCP model. */
@Serializable
data class TextContent(
    /** The annotations property. */
    val annotations: Map<String, String>? = null,
    /** The text property. */
    val text: String,
    /** The type property. */
    val type: String = "text",
)

/** Represents the TextResourceContents MCP model. */
@Serializable
data class TextResourceContents(
    /** The mimeType property. */
    val mimeType: String? = null,
    /** The text property. */
    val text: String,
    /** The uri property. */
    val uri: String,
)

/** Represents the Tool MCP model. */
@Serializable
data class Tool(
    /** The description property. */
    val description: String? = null,
    /** The inputSchema property. */
    val inputSchema: ToolInputSchema,
    /** The name property. */
    val name: String,
)

/** Represents the ToolInputSchema MCP model. */
@Serializable
data class ToolInputSchema(
    /** The properties property. */
    val properties: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    /** The required property. */
    val required: List<String>? = null,
    /** The type property. */
    @Required val type: String = "object",
)

/** Represents the ToolListChangedNotification MCP model. */
@Serializable
data class ToolListChangedNotification(
    /** The method property. */
    @Required val method: String = "notifications/tools/list_changed",
    /** The params property. */
    val params: ToolListChangedNotificationParams? = null,
) : ServerNotification

/** Represents the ToolListChangedNotificationParams MCP model. */
@Serializable
data class ToolListChangedNotificationParams(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** Represents the UnsubscribeRequest MCP model. */
@Serializable
data class UnsubscribeRequest(
    /** The method property. */
    val method: String = "resources/unsubscribe",
    /** The params property. */
    val params: UnsubscribeRequestParams,
) : ClientRequest

/** Represents the UnsubscribeRequestParams MCP model. */
@Serializable
data class UnsubscribeRequestParams(
    /** The uri property. */
    val uri: String,
)

/** Sealed hierarchy representing all notifications sent from an MCP client to a server. */
@Serializable sealed interface ClientNotification

/** Sealed hierarchy representing all requests sent from an MCP client to a server. */
@Serializable sealed interface ClientRequest

/** Sealed hierarchy representing all result payloads returned from an MCP client to a server. */
@Serializable sealed interface ClientResult

/** Represents the Cursor MCP model. */
typealias Cursor = String

/** Represents the EmptyResult MCP model. */
@Serializable
data class EmptyResult(
    /** The _meta property. */
    @Required val _meta: Map<String, kotlinx.serialization.json.JsonElement>? = null
) : ClientResult, ServerResult

/** Sealed hierarchy representing all JSON-RPC 2.0 protocol messages in MCP. */
@Serializable sealed interface JSONRPCMessage

/** Represents the ProgressToken MCP model. */
typealias ProgressToken = String

/** Represents the RequestId MCP model. */
typealias RequestId = String

/** Represents the Role MCP model. */
@Serializable
enum class Role {
  /** The user role. */
  @kotlinx.serialization.SerialName("user") user,
  /** The assistant role. */
  @kotlinx.serialization.SerialName("assistant") assistant
}

/** Sealed hierarchy representing all notifications sent from an MCP server to a client. */
@Serializable sealed interface ServerNotification

/** Sealed hierarchy representing all requests sent from an MCP server to a client. */
@Serializable sealed interface ServerRequest

/** Sealed hierarchy representing all result payloads returned from an MCP server to a client. */
@Serializable sealed interface ServerResult
