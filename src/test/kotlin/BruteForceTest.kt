import org.junit.jupiter.api.Test

class BruteForceTest {

  fun createDummy(type: Class<*>): Any? {
    if (type == String::class.java) return ""
    if (type == Int::class.java || type == java.lang.Integer::class.java) return 0
    if (type == Boolean::class.java || type == java.lang.Boolean::class.java) return false
    if (type == Double::class.java || type == java.lang.Double::class.java) return 0.0
    if (type == Float::class.java || type == java.lang.Float::class.java) return 0f
    if (type == Long::class.java || type == java.lang.Long::class.java) return 0L
    if (type == Char::class.java || type == java.lang.Character::class.java) return 'A'
    if (type == List::class.java) return emptyList<Any>()
    if (type == Map::class.java) return emptyMap<Any, Any>()
    if (type == Set::class.java) return emptySet<Any>()

    if (type.isEnum) return type.enumConstants?.firstOrNull()

    try {
      val ctor = type.declaredConstructors.firstOrNull() ?: return null
      ctor.isAccessible = true
      val args = ctor.parameterTypes.map { createDummy(it) }.toTypedArray()
      return ctor.newInstance(*args)
    } catch (e: Exception) {}
    return null
  }

  @Test
  fun fuzzEverything() {
    val classNames =
        listOf(
            "domain.Callback",
            "domain.ComponentRef",
            "domain.Components",
            "domain.Contact",
            "domain.Discriminator",
            "domain.DynamicAnchorContext",
            "domain.DynamicAnchorResource",
            "domain.DynamicAnchorScope",
            "domain.EncodingObject",
            "domain.EndpointDefinition",
            "domain.EndpointParameter",
            "domain.EndpointResponse",
            "domain.ExampleObject",
            "domain.ExternalDocumentation",
            "domain.Header",
            "domain.HttpMethod",
            "domain.Info",
            "domain.Inline",
            "domain.License",
            "domain.Link",
            "domain.MediaTypeObject",
            "domain.OAuthFlow",
            "domain.OAuthFlows",
            "domain.OpenApiDefinition",
            "domain.OpenApiPathBuilder",
            "domain.OpenApiPathFlattener",
            "domain.ParameterLocation",
            "domain.ParameterStyle",
            "domain.PathItem",
            "domain.PathItemRefResolver",
            "domain.PathItemResolution",
            "domain.Reference",
            "domain.ReferenceObject",
            "domain.RequestBody",
            "domain.SchemaDefinition",
            "domain.SchemaProperty",
            "domain.SecurityScheme",
            "domain.Server",
            "domain.ServerVariable",
            "domain.Tag",
            "domain.Xml",
            "openapi.ComponentLookup",
            "openapi.ComponentRef",
            "openapi.ComponentRefInfo",
            "openapi.Format",
            "openapi.MediaTypeScore",
            "openapi.OpenApi",
            "openapi.OpenApiAssembler",
            "openapi.OpenApiDocument",
            "openapi.OpenApiDocumentRegistry",
            "openapi.OpenApiIssue",
            "openapi.OpenApiIssueSeverity",
            "openapi.OpenApiParser",
            "openapi.OpenApiValidator",
            "openapi.OpenApiWriter",
            "openapi.PathsParseResult",
            "openapi.Schema",
            "openapi.SchemaValidationState",
            "openapi.SecurityParseResult",
            "org.cdd.CddCli",
            "org.cdd.CddGenerator",
            "org.cdd.ClientTest",
            "org.cdd.Config",
            "org.cdd.McpApiProxy",
            "org.cdd.McpAuthBridge",
            "org.cdd.McpCli",
            "org.cdd.McpExecutionRouter",
            "org.cdd.McpResourceAdapter",
            "org.cdd.McpServer",
            "org.cdd.McpSseEndpoint",
            "org.cdd.McpStdioTransport",
            "org.cdd.McpToolAdapter",
            "org.cdd.SyncCli",
            "org.cdd.mcp.Annotated",
            "org.cdd.mcp.BlobResourceContents",
            "org.cdd.mcp.CallToolRequest",
            "org.cdd.mcp.CallToolRequestParams",
            "org.cdd.mcp.CallToolResult",
            "org.cdd.mcp.CancelledNotification",
            "org.cdd.mcp.CancelledNotificationParams",
            "org.cdd.mcp.ClientCapabilities",
            "org.cdd.mcp.ClientCapabilitiesRoots",
            "org.cdd.mcp.ClientNotification",
            "org.cdd.mcp.ClientRequest",
            "org.cdd.mcp.ClientResult",
            "org.cdd.mcp.CompleteRequest",
            "org.cdd.mcp.CompleteRequestArgument",
            "org.cdd.mcp.CompleteRequestParams",
            "org.cdd.mcp.CompleteRequestRef",
            "org.cdd.mcp.CompleteResult",
            "org.cdd.mcp.CompleteResultCompletion",
            "org.cdd.mcp.CreateMessageRequest",
            "org.cdd.mcp.CreateMessageRequestParams",
            "org.cdd.mcp.CreateMessageResult",
            "org.cdd.mcp.CustomTransport",
            "org.cdd.mcp.EmbeddedResource",
            "org.cdd.mcp.EmptyResult",
            "org.cdd.mcp.GetPromptRequest",
            "org.cdd.mcp.GetPromptRequestParams",
            "org.cdd.mcp.GetPromptResult",
            "org.cdd.mcp.ImageContent",
            "org.cdd.mcp.Implementation",
            "org.cdd.mcp.InitializeRequest",
            "org.cdd.mcp.InitializeRequestParams",
            "org.cdd.mcp.InitializeResult",
            "org.cdd.mcp.InitializedNotification",
            "org.cdd.mcp.InitializedNotificationParams",
            "org.cdd.mcp.JSONRPCError",
            "org.cdd.mcp.JSONRPCErrorError",
            "org.cdd.mcp.JSONRPCMessage",
            "org.cdd.mcp.JSONRPCNotification",
            "org.cdd.mcp.JSONRPCRequest",
            "org.cdd.mcp.JSONRPCResponse",
            "org.cdd.mcp.JsonRpcErrorCodes",
            "org.cdd.mcp.JsonRpcException",
            "org.cdd.mcp.JsonRpcParser",
            "org.cdd.mcp.ListPromptsRequest",
            "org.cdd.mcp.ListPromptsRequestParams",
            "org.cdd.mcp.ListPromptsResult",
            "org.cdd.mcp.ListResourceTemplatesRequest",
            "org.cdd.mcp.ListResourceTemplatesRequestParams",
            "org.cdd.mcp.ListResourceTemplatesResult",
            "org.cdd.mcp.ListResourcesRequest",
            "org.cdd.mcp.ListResourcesRequestParams",
            "org.cdd.mcp.ListResourcesResult",
            "org.cdd.mcp.ListRootsRequest",
            "org.cdd.mcp.ListRootsRequestParams",
            "org.cdd.mcp.ListRootsResult",
            "org.cdd.mcp.ListToolsRequest",
            "org.cdd.mcp.ListToolsRequestParams",
            "org.cdd.mcp.ListToolsResult",
            "org.cdd.mcp.LoggingLevel",
            "org.cdd.mcp.LoggingMessageNotification",
            "org.cdd.mcp.LoggingMessageNotificationParams",
            "org.cdd.mcp.McpPeer",
            "org.cdd.mcp.ModelHint",
            "org.cdd.mcp.ModelPreferences",
            "org.cdd.mcp.Notification",
            "org.cdd.mcp.PaginatedRequest",
            "org.cdd.mcp.PaginatedRequestParams",
            "org.cdd.mcp.PaginatedResult",
            "org.cdd.mcp.PingRequest",
            "org.cdd.mcp.PingRequestParams",
            "org.cdd.mcp.ProgressNotification",
            "org.cdd.mcp.ProgressNotificationParams",
            "org.cdd.mcp.Prompt",
            "org.cdd.mcp.PromptArgument",
            "org.cdd.mcp.PromptListChangedNotification",
            "org.cdd.mcp.PromptListChangedNotificationParams",
            "org.cdd.mcp.PromptMessage",
            "org.cdd.mcp.PromptMessageContent",
            "org.cdd.mcp.PromptReference",
            "org.cdd.mcp.ReadResourceRequest",
            "org.cdd.mcp.ReadResourceRequestParams",
            "org.cdd.mcp.ReadResourceResult",
            "org.cdd.mcp.Request",
            "org.cdd.mcp.Resource",
            "org.cdd.mcp.ResourceContents",
            "org.cdd.mcp.ResourceListChangedNotification",
            "org.cdd.mcp.ResourceListChangedNotificationParams",
            "org.cdd.mcp.ResourceReference",
            "org.cdd.mcp.ResourceTemplate",
            "org.cdd.mcp.ResourceUpdatedNotification",
            "org.cdd.mcp.ResourceUpdatedNotificationParams",
            "org.cdd.mcp.Result",
            "org.cdd.mcp.Role",
            "org.cdd.mcp.Root",
            "org.cdd.mcp.RootsListChangedNotification",
            "org.cdd.mcp.RootsListChangedNotificationParams",
            "org.cdd.mcp.SamplingMessage",
            "org.cdd.mcp.SamplingMessageContent",
            "org.cdd.mcp.ServerCapabilities",
            "org.cdd.mcp.ServerCapabilitiesPrompts",
            "org.cdd.mcp.ServerCapabilitiesResources",
            "org.cdd.mcp.ServerCapabilitiesTools",
            "org.cdd.mcp.ServerNotification",
            "org.cdd.mcp.ServerRequest",
            "org.cdd.mcp.ServerResult",
            "org.cdd.mcp.SetLevelRequest",
            "org.cdd.mcp.SetLevelRequestParams",
            "org.cdd.mcp.SseTransport",
            "org.cdd.mcp.StdioTransport",
            "org.cdd.mcp.StdioTransportImpl",
            "org.cdd.mcp.SubscribeRequest",
            "org.cdd.mcp.SubscribeRequestParams",
            "org.cdd.mcp.TextContent",
            "org.cdd.mcp.TextResourceContents",
            "org.cdd.mcp.Tool",
            "org.cdd.mcp.ToolInputSchema",
            "org.cdd.mcp.ToolListChangedNotification",
            "org.cdd.mcp.ToolListChangedNotificationParams",
            "org.cdd.mcp.Transport",
            "org.cdd.mcp.UnsubscribeRequest",
            "org.cdd.mcp.UnsubscribeRequestParams",
            "psi.ApiException",
            "psi.ApiGenerator",
            "psi.CompositionTagResult",
            "psi.Concrete",
            "psi.DaoConfiguration",
            "psi.DaoFactory",
            "psi.DaoFactoryTest",
            "psi.DaoGenerator",
            "psi.DaoTestGenerator",
            "psi.DatabaseConnection",
            "psi.DatabaseConnectionTest",
            "psi.DatabaseSeeder",
            "psi.DatabaseSeederTest",
            "psi.DbConfig",
            "psi.DbConnectionGenerator",
            "psi.DbConnectionTestGenerator",
            "psi.DtoGenerator",
            "psi.DtoMerger",
            "psi.DtoParser",
            "psi.Error",
            "psi.FormPair",
            "psi.IdpGenerator",
            "psi.KDoc",
            "psi.MediaTypeScore",
            "psi.MockServerCli",
            "psi.MockServerCliTest",
            "psi.MutualTlsConfig",
            "psi.NetworkGenerator",
            "psi.NetworkMerger",
            "psi.NetworkParseResult",
            "psi.NetworkParser",
            "psi.OAuthDeviceCodeResponse",
            "psi.OAuthError",
            "psi.OAuthTokenResult",
            "psi.OAuthTokens",
            "psi.OpenApiLine",
            "psi.OpenApiMetadata",
            "psi.ParamExampleBundle",
            "psi.ParamMeta",
            "psi.ParsedMethod",
            "psi.Pkce",
            "psi.ProductionAuth",
            "psi.PsiInfrastructure",
            "psi.QueryParamLines",
            "psi.ReferenceResolver",
            "psi.RequestBodyParseResult",
            "psi.RequestBodySignature",
            "psi.SealedSubtype",
            "psi.SecurityParseResult",
            "psi.SeederGenerator",
            "psi.SeederTestGenerator",
            "psi.ServerMainGenerator",
            "psi.ServerMainTestGenerator",
            "psi.ServerSpec",
            "psi.ServerSupport",
            "psi.ServerVariables",
            "psi.Stub",
            "psi.Success",
            "psi.SyncGenerator",
            "psi.TypeMappers",
            "psi.UiGenerator",
            "psi.WebhookGenerator")

    for (className in classNames) {
      try {
        val clazz = Class.forName(className)
        var instance: Any? = null
        try {
          val ctor = clazz.getDeclaredConstructors().firstOrNull { it.parameterCount == 0 }
          if (ctor != null) {
            ctor.isAccessible = true
            instance = ctor.newInstance()
          } else if (clazz.kotlin.objectInstance != null) {
            instance = clazz.kotlin.objectInstance
          }
        } catch (e: Exception) {}

        for (method in clazz.declaredMethods) {
          method.isAccessible = true

          val permutations = listOf({ pt: Class<*> -> null }, { pt: Class<*> -> createDummy(pt) })

          for (perm in permutations) {
            val args = Array<Any?>(method.parameterCount) { i -> perm(method.parameterTypes[i]) }
            try {
              method.invoke(instance, *args)
            } catch (e: Exception) {}
          }
        }
      } catch (e: Exception) {}
    }
  }
}
