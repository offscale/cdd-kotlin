# Compliance Validation (OAS 3.2)

Use the validator to catch common OpenAPI 3.2 structural violations:

```kotlin
val issues = OpenApiValidator().validate(definition)
if (issues.isNotEmpty()) {
    issues.forEach { println("${it.severity}: ${it.path} -> ${it.message}") }
}
```

Validator coverage includes:

- Path template + path parameter consistency (including duplicate template names)
- Path template collision detection across paths with equivalent templated structure
- Path keys must not include query strings or fragments
- OperationId uniqueness across paths, webhooks, callbacks, and component Path Items
- Response code format validation (`200`, `2XX`, `default`)
- Server variable enum/default consistency and url-variable usage rules
- Server variables defined but not used in the url (warning)
- URI/email format validation for Info/contact/license/externalDocs and OAuth/OpenID URL fields
- Security scheme type validation and `apiKey.in` location checks
- Response presence, required response descriptions, and header `Content-Type` restrictions
- Parameter/header schema/content rules and style/explode constraints
- OpenAPI version must be `3.2.x` (warning if not)
- `allowEmptyValue` is only valid for query parameters
- Header parameters named `Accept`, `Content-Type`, or `Authorization` are ignored (warning)
- Single-response operations should use a success (`2XX`) response (warning)
- Header `content` must not be combined with `style`/`explode`
- Header names (parameters/response/encoding) must be valid HTTP tokens
- Path Item parameter uniqueness and parameter validation at the Path Item level
- Path Item `$ref` with sibling fields (warning)
- Sequential media type rules for `itemSchema` and positional encoding
- Parameters using `content` must not define `style`/`explode`/`allowReserved`
- Parameter and Header `content` must contain exactly one media type
- Media type keys must be valid media types or media type ranges
- Schema bound validation for min/max length/items/properties/contains (non-negative + ordering)
- Schema content metadata validation for `contentMediaType` and `contentEncoding`
- Schema dialect-aware warnings for OpenAPI-only keywords and custom keywords when `jsonSchemaDialect`/`$schema` target non-OAS vocabularies
- Server name uniqueness within a `servers` list (root/path/operation)
- `additionalOperations` method tokens must be valid HTTP tokens
- Webhook Path Items are validated without path-key constraints
- Encoding entries must match schema properties (encoding-by-name warnings)
- Encoding headers must not include `Content-Type` (warning)
- `$self` must be a valid URI reference
- Callback runtime expressions are validated for basic syntax (including embedded expressions in URLs)
- Security scheme component names that look like URIs are flagged (warning)
- Link runtime expressions in `parameters`/`requestBody` are validated for basic syntax
- Media Type and Example `$ref` with sibling fields or extensions are ignored (warning)
- Reference Object `$ref`, Schema `$ref`/`$dynamicRef`/`$id`/`$schema`, and Example `externalValue` are validated as URIs
- Local component `$ref` targets are validated for Parameters, Headers, RequestBodies, Responses, Links, Examples, MediaTypes, SecuritySchemes, and PathItems
- Schema `$ref` targets are validated for local component schemas and in-scope `$defs`
- Response `links` keys must match the component name regex
- Link `operationId` must reference an existing operationId
- Link `operationRef` must be a valid URI reference
- Link `operationRef` local JSON Pointers (including `$self`-based absolute refs) must resolve to an existing operation
- Link `operationRef` normalization accepts percent-encoded path template braces (`%7B` / `%7D`)
- Discriminator rules (composition required; defaultMapping required when discriminator property is optional)
- XML Object constraints (`nodeType` vs `attribute`/`wrapped`, and `wrapped` requires array schemas)

## Schema Annotations (OAS 3.2)

The DTO layer round-trips JSON Schema annotation and selected structural keywords via KDoc tags and Kotlin annotations:

- `title`, `default`, `const`
- `enum` (including non-string values via `@enum` KDoc tags)
- `schemaId`, `schemaDialect`, `anchor`, `dynamicAnchor`, `dynamicRef`, `defs`
- `deprecated` (also emitted as `@Deprecated`)
- `readOnly`, `writeOnly`
- `contentMediaType`, `contentEncoding`
- `minContains`, `maxContains`, `contains`, `prefixItems`
- `discriminator`, `discriminatorMapping`, `discriminatorDefault`
- `xmlName`, `xmlNamespace`, `xmlPrefix`, `xmlNodeType`, `xmlAttribute`, `xmlWrapped`
- `comment`
- `patternProperties`, `propertyNames`
- `dependentRequired`, `dependentSchemas`
- `unevaluatedProperties`, `unevaluatedItems`
- `contentSchema`
- `oneOf`, `anyOf`, `allOf`, `not`, `if`, `then`, `else`
- `additionalProperties` (including `false`)
- `customKeywords` (arbitrary JSON Schema keywords via `@keywords {...}`)
- legacy `nullable` / `x-nullable` (OAS 3.0 / Swagger 2.0) normalized to `type: ["T","null"]`

OpenAPI parsing/writing also supports additional JSON Schema structural keywords in the IR:

- `$comment`
- `$dynamicRef`
- `$defs`
- `if` / `then` / `else`
- `patternProperties`, `propertyNames`
- `dependentRequired`, `dependentSchemas`
- `unevaluatedProperties`, `unevaluatedItems`
- `contentSchema`
- `additionalProperties: false`
- custom JSON Schema keywords (non-`x-`) preserved via `customKeywords`

OpenAPI 3.2 object handling also preserves:

- `style: cookie` for cookie parameters
- Non-string `enum` values in Schema Objects
- Response `headers`, `links`, and `content` maps via `@responseHeaders`, `@responseLinks`, and `@responseContent`
- Component `$ref` emissions derived from Kotlin types (e.g., `requestBodyType`, response `type`, schema compositions) use `$self` as the base when present
- Component `$ref` resolution is `$self`-aware: when `$self` is present, only refs whose base matches `$self` (or fragment-only refs) are resolved
- Reference Objects (`$ref`) for Parameter and Response objects via `@paramRef` and `@responseRef`
- Reference Objects (`$ref`) for Link and Example objects
- Reference Objects (`$ref`) for Callback objects
- Reference Objects (`$ref`) for Security Schemes
- Reference Objects (`$ref`) for Media Type objects (including summary/description overrides)
- Explicit empty `security: []` at root/operation to clear inherited security
- Explicit empty `paths: {}` and `webhooks: {}` to preserve ACL semantics (`@pathsEmpty`, `@webhooksEmpty`)
- Link `parameters` and `requestBody` with non-string JSON values
- Schema Object `externalDocs` and `discriminator` on nested properties
- Schema Object `$ref` siblings (JSON Schema 2020-12 behavior) on nested properties
- Path Item `$ref` siblings (summary/description/parameters/operations) for round-trip safety
- Component schemas with omitted `type` (re-emitted without forcing `type: object`)
- Specification Extensions (`x-...`) across OpenAPI objects
- Component Media Type `$ref` resolution for codegen (while preserving `$ref` for round-trip)
- JSON Pointer percent-decoding for `$ref` component keys (e.g. `#/components/responses/Ok%20Response`)
- Webhook flattening helpers via `OpenApiPathFlattener.flattenWebhooks` and `flattenAll`
