# CDD Kotlin Swagger 2.0, OpenAPI 3.2.0, Arazzo 1.1.0 & MCP 1.0.0 Compliance

This document tracks support for Swagger 2.0, OpenAPI 3.2.0, Arazzo 1.1.0, and Model Context Protocol (MCP) 1.0.0 features in the `cdd-kotlin` toolchain.

## Support Coverage

100% compliance achieved across all supported standards! `cdd-kotlin` provides full bidirectional specification compliance across Client SDK (`to_sdk`), Client SDK CLI (`to_sdk_cli`), and Server / Stateful Mock / E2E Test Suites (`to_server`).

| Feature / Specification Area | Supported |
|---|---|
| OpenAPI 3.2.0 Document Root (`openapi`, `$self`, `jsonSchemaDialect`, `info`, `servers`, `paths`, `webhooks`, `components`, `security`, `tags`, `externalDocs`) | ✅ |
| Swagger 2.0 Automatic Upgrade (`swagger: "2.0"`, `host`, `basePath`, `schemes`, `consumes`, `produces`, `definitions`, `parameters`, `responses`, `securityDefinitions`) | ✅ |
| Info, Contact & License Objects (`summary`, `identifier`, `termsOfService`, `x-*` extensions) | ✅ |
| Server & ServerVariable Objects (`url`, `description`, `name`, `variables`, `enum`, `default`) | ✅ |
| Path Item & Operation Objects (`get`, `put`, `post`, `delete`, `options`, `head`, `patch`, `trace`, `query`, `additionalOperations`) | ✅ |
| Parameter Serialization (`path`, `query`, `querystring`, `header`, `cookie`; `matrix`, `label`, `form`, `simple`, `spaceDelimited`, `pipeDelimited`, `deepObject`) | ✅ |
| Request Body, Media Type & Encoding Objects (`schema`, `itemSchema`, `encoding`, `prefixEncoding`, `itemEncoding`, `multipart`, `text/event-stream` SSE) | ✅ |
| Responses, Headers, Links & Callbacks (`default`, `1XX`-`5XX`, `operationRef`, `operationId`, runtime expressions) | ✅ |
| JSON Schema Draft 2020-12 & Boolean Schemas (`$ref`, `$dynamicRef`, `$dynamicAnchor`, `allOf`, `anyOf`, `oneOf`, `not`, `prefixItems`, `unevaluatedProperties`, `unevaluatedItems`, `discriminator`, `xml`) | ✅ |
| Security Schemes & OAuth2 / OIDC Flows (`apiKey`, `http`, `mutualTLS`, `oauth2`, `openIdConnect`, `deviceAuthorization`) | ✅ |
| Arazzo 1.1.0 Specification Object (`arazzo`, `$self`, `info`, `sourceDescriptions`, `workflows`, `components`) | ✅ |
| Arazzo 1.1.0 Workflow & Step Orchestration (`operationId`, `operationPath`, `channelPath`, `workflowId`, `dependsOn`) | ✅ |
| Arazzo 1.1.0 Runtime Expressions (All 17 `$` Roots & `{...}` String Templates) | ✅ |
| Arazzo 1.1.0 Criteria, Selectors & Payload Replacements (`simple`, `regex`, `jsonpath`, `jsonpointer`, `xpath`) | ✅ |
| Arazzo 1.1.0 Success & Failure Actions (`end`, `goto`, `retry`, `retryAfter`, `retryLimit`) | ✅ |
| Model Context Protocol (MCP) 1.0.0 (`mcp` stdio JSON-RPC 2.0 server, tool/resource adapters, SSE transport, auth bridge) | ✅ |

## Detailed Conformance Matrices

- **OpenAPI 3.2.0**: [`conformance/openapi-3.2.0/`](conformance/openapi-3.2.0/) ([`client-sdk.md`](conformance/openapi-3.2.0/client-sdk.md), [`client-sdk-cli.md`](conformance/openapi-3.2.0/client-sdk-cli.md), [`servers.md`](conformance/openapi-3.2.0/servers.md))
- **Swagger 2.0**: [`conformance/swagger-2.0/`](conformance/swagger-2.0/) ([`client-sdk.md`](conformance/swagger-2.0/client-sdk.md), [`client-sdk-cli.md`](conformance/swagger-2.0/client-sdk-cli.md), [`servers.md`](conformance/swagger-2.0/servers.md))
- **Arazzo 1.1.0**: [`conformance/arazzo-1.1.0/`](conformance/arazzo-1.1.0/) ([`client-sdk.md`](conformance/arazzo-1.1.0/client-sdk.md), [`client-sdk-cli.md`](conformance/arazzo-1.1.0/client-sdk-cli.md), [`servers.md`](conformance/arazzo-1.1.0/servers.md))
- **MCP 1.0.0**: [`conformance/mcp-1.0.0/`](conformance/mcp-1.0.0/) ([`client-sdk.md`](conformance/mcp-1.0.0/client-sdk.md), [`client-sdk-cli.md`](conformance/mcp-1.0.0/client-sdk-cli.md), [`servers.md`](conformance/mcp-1.0.0/servers.md))
