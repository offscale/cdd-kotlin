package domain

/**
 * This is the root object of the OpenAPI Description.
 *
 * The OpenAPI Specification (OAS) defines a standard, language-agnostic interface to HTTP APIs which
 * allows both humans and computers to discover and understand the capabilities of the service without
 * access to source code, documentation, or through network traffic inspection.
 *
 * See [OpenAPI Object](https://spec.openapis.org/oas/v3.2.0#openapi-object)
 */
data class OpenApiDefinition(
    /**
     * **REQUIRED**. This string MUST be the version number of the OpenAPI Specification that the
     * OpenAPI document uses. The `openapi` field SHOULD be used by tooling to interpret the OpenAPI document.
     * This is *not* related to the `info.version` string, which describes the OpenAPI document's version.
     */
    val openapi: String = "3.2.0",

    /**
     * **REQUIRED**. Provides metadata about the API. The metadata MAY be used by tooling as required.
     */
    val info: Info,

    /**
     * The default value for the `$schema` keyword within Schema Objects contained within this OAS document.
     * This MUST be in the form of a URI.
     */
    val jsonSchemaDialect: String? = null,

    /**
     * An array of Server Objects, which provide connectivity information to a target server.
     * If the `servers` field is not provided, or is an empty array, the default value would be a Server Object with a url value of `/`.
     */
    val servers: List<Server> = emptyList(),

    /**
     * The available paths and operations for the API.
     *
     * This maps the path string (e.g. "/users/{id}") to a [PathItem], which can contain
     * multiple operations (GET, POST, etc.) and path-level parameters.
     */
    val paths: Map<String, PathItem> = emptyMap(),

    /**
     * Specification extensions applied to the Paths Object (keys starting with `x-`).
     * These are siblings of path entries under the `paths` object.
     */
    val pathsExtensions: Map<String, Any?> = emptyMap(),

    /**
     * The incoming webhooks that MAY be received as part of this API and that the API consumer MAY choose to implement.
     * Closely related to the `callbacks` feature, this section describes requests initiated other than by an API call,
     * for example by an out of band registration.
     *
     * Webhooks are defined as a map of Name -> [PathItem].
     */
    val webhooks: Map<String, PathItem> = emptyMap(),

    /**
     * Specification extensions applied to the Webhooks Object (keys starting with `x-`).
     * These are siblings of webhook entries under the `webhooks` object.
     */
    val webhooksExtensions: Map<String, Any?> = emptyMap(),

    /**
     * An element to hold various schemas for the OpenAPI Description.
     */
    val components: Components? = null,

    /**
     * A declaration of which security mechanisms can be used across the API.
     * The list of values includes alternative Security Requirement Objects that can be used.
     * Only one of the Security Requirement Objects need to be satisfied to authorize a request.
     * Individual operations can override this definition.
     */
    val security: List<SecurityRequirement> = emptyList(),

    /**
     * When true, serialize an explicit empty security array (`security: []`).
     * This distinguishes between "not specified" and "explicitly no security requirements".
     */
    val securityExplicitEmpty: Boolean = false,

    /**
     * A list of tags used by the OpenAPI Description with additional metadata.
     * The order of the tags can be used to reflect on their order by the parsing tools.
     */
    val tags: List<Tag> = emptyList(),

    /**
     * Additional external documentation.
     */
    val externalDocs: ExternalDocumentation? = null,

    /**
     * The self-assigned URI of this document, which also serves as its base URI.
     * This maps to the `$self` field in the OpenAPI Object.
     * This MUST be in the form of a URI reference.
     */
    val self: String? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Holds a set of reusable objects for different aspects of the OAS.
 * All objects defined within the Components Object will have no effect on the API unless they are
 * explicitly referenced from outside the Components Object.
 *
 * See [Components Object](https://spec.openapis.org/oas/v3.2.0#components-object)
 */
data class Components(
    /** An object to hold reusable [SchemaDefinition] Objects. */
    val schemas: Map<String, SchemaDefinition> = emptyMap(),

    /** An object to hold reusable [EndpointResponse] Objects. */
    val responses: Map<String, EndpointResponse> = emptyMap(),

    /** An object to hold reusable [EndpointParameter] Objects. */
    val parameters: Map<String, EndpointParameter> = emptyMap(),

    /** An object to hold reusable [RequestBody] Objects. */
    val requestBodies: Map<String, RequestBody> = emptyMap(),

    /** An object to hold reusable [Header] Objects. */
    val headers: Map<String, Header> = emptyMap(),

    /** An object to hold reusable [SecurityScheme] Objects. */
    val securitySchemes: Map<String, SecurityScheme> = emptyMap(),

    /** An object to hold reusable [ExampleObject] Objects. */
    val examples: Map<String, ExampleObject> = emptyMap(),

    /** An object to hold reusable [Link] Objects. */
    val links: Map<String, Link> = emptyMap(),

    /** An object to hold reusable [Callback] Objects. */
    val callbacks: Map<String, Callback> = emptyMap(),

    /** An object to hold reusable [PathItem] Objects. */
    val pathItems: Map<String, PathItem> = emptyMap(),

    /** An object to hold reusable [MediaTypeObject] Objects. */
    val mediaTypes: Map<String, MediaTypeObject> = emptyMap(),

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * The object provides metadata about the API.
 * The metadata MAY be used by the clients if needed, and MAY be presented in editing or
 * documentation generation tools for convenience.
 *
 * See [Info Object](https://spec.openapis.org/oas/v3.2.0#info-object)
 */
data class Info(
    /**
     * **REQUIRED**. The title of the API.
     */
    val title: String,

    /**
     * **REQUIRED**. The version of the OpenAPI document (which is distinct from the OpenAPI Specification version
     * or the version of the API being described or the version of the OpenAPI Description).
     */
    val version: String,

    /**
     * A short summary of the API.
     */
    val summary: String? = null,

    /**
     * A description of the API. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * A URI for the Terms of Service for the API. This MUST be in the form of a URI.
     */
    val termsOfService: String? = null,

    /**
     * The contact information for the exposed API.
     */
    val contact: Contact? = null,

    /**
     * The license information for the exposed API.
     */
    val license: License? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Contact information for the exposed API.
 *
 * See [Contact Object](https://spec.openapis.org/oas/v3.2.0#contact-object)
 */
data class Contact(
    /**
     * The identifying name of the contact person/organization.
     */
    val name: String? = null,

    /**
     * The URI for the contact information. This MUST be in the form of a URI.
     */
    val url: String? = null,

    /**
     * The email address of the contact person/organization. This MUST be in the form of an email address.
     */
    val email: String? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * License information for the exposed API.
 *
 * See [License Object](https://spec.openapis.org/oas/v3.2.0#license-object)
 */
data class License(
    /**
     * **REQUIRED**. The license name used for the API.
     */
    val name: String,

    /**
     * An SPDX license expression for the API.
     * The `identifier` field is mutually exclusive of the `url` field.
     */
    val identifier: String? = null,

    /**
     * A URI for the license used for the API. This MUST be in the form of a URI.
     * The `url` field is mutually exclusive of the `identifier` field.
     */
    val url: String? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * An object representing a Server.
 *
 * See [Server Object](https://spec.openapis.org/oas/v3.2.0#server-object)
 */
data class Server(
    /**
     * **REQUIRED**. A URL to the target host. This URL supports Server Variables and MAY be relative,
     * to indicate that the host location is relative to the location where the document containing
     * the Server Object is being served.
     */
    val url: String,

    /**
     * An optional string describing the host designated by the URL.
     * CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * A map between a variable name and its value. The value is used for substitution in the server's URL template.
     */
    val variables: Map<String, ServerVariable>? = null,

    /**
     * An optional unique string to refer to the host designated by the URL.
     */
    val name: String? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * An object representing a Server Variable for server URL template substitution.
 *
 * See [Server Variable Object](https://spec.openapis.org/oas/v3.2.0#server-variable-object)
 */
data class ServerVariable(
    /**
     * **REQUIRED**. The default value to use for substitution, which SHALL be sent if an alternate value is *not* supplied.
     */
    val default: String,

    /**
     * An enumeration of string values to be used if the substitution options are from a limited set.
     * The array MUST NOT be empty.
     */
    val enum: List<String>? = null,

    /**
     * An optional description for the server variable.
     * CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Defines a security scheme that can be used by the operations.
 *
 * See [Security Scheme Object](https://spec.openapis.org/oas/v3.2.0#security-scheme-object)
 */
data class SecurityScheme(
    /**
     * **REQUIRED**. The type of the security scheme.
     * Valid values are `"apiKey"`, `"http"`, `"mutualTLS"`, `"oauth2"`, `"openIdConnect"`.
     */
    val type: String = "apiKey",

    /**
     * A description for security scheme.
     * CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * **REQUIRED** (`apiKey`). The name of the header, query or cookie parameter to be used.
     */
    val name: String? = null,

    /**
     * **REQUIRED** (`apiKey`). The location of the API key.
     * Valid values are `"query"`, `"header"`, or `"cookie"`.
     */
    val `in`: String? = null,

    /**
     * **REQUIRED** (`http`). The name of the HTTP Authentication scheme to be used in the Authorization header.
     * e.g. "bearer", "basic", "digest".
     */
    val scheme: String? = null,

    /**
     * (`http`, "bearer"). A hint to the client to identify how the bearer token is formatted.
     * e.g. "JWT".
     */
    val bearerFormat: String? = null,

    /**
     * **REQUIRED** (`oauth2`). An object containing configuration information for the flow types supported.
     */
    val flows: OAuthFlows? = null,

    /**
     * **REQUIRED** (`openIdConnect`). Well-known URL to discover the OpenID Connect provider metadata.
     */
    val openIdConnectUrl: String? = null,

    /**
     * (`oauth2`). URL to the OAuth2 authorization server metadata (RFC8414). TLS is required.
     */
    val oauth2MetadataUrl: String? = null,

    /**
     * Declares this security scheme to be deprecated. Consumers SHOULD refrain from usage of the declared scheme.
     * Default value is `false`.
     */
    val deprecated: Boolean = false,

    /**
     * Reference Object allowing `$ref` with optional summary/description overrides.
     * When present, this is treated as a Reference Object and other fields are ignored for serialization.
     */
    val reference: ReferenceObject? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Allows configuration of the supported OAuth Flows.
 *
 * See [OAuth Flows Object](https://spec.openapis.org/oas/v3.2.0#oauth-flows-object)
 */
data class OAuthFlows(
    /** Configuration for the OAuth Implicit flow */
    val implicit: OAuthFlow? = null,
    /** Configuration for the OAuth Resource Owner Password flow */
    val password: OAuthFlow? = null,
    /** Configuration for the OAuth Client Credentials flow. */
    val clientCredentials: OAuthFlow? = null,
    /** Configuration for the OAuth Authorization Code flow. */
    val authorizationCode: OAuthFlow? = null,
    /** Configuration for the OAuth Device Authorization flow. */
    val deviceAuthorization: OAuthFlow? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Configuration details for a supported OAuth Flow.
 *
 * See [OAuth Flow Object](https://spec.openapis.org/oas/v3.2.0#oauth-flow-object)
 */
data class OAuthFlow(
    /**
     * **REQUIRED** (`implicit`, `authorizationCode`). The authorization URL to be used for this flow.
     */
    val authorizationUrl: String? = null,

    /**
     * **REQUIRED** (`password`, `clientCredentials`, `authorizationCode`, `deviceAuthorization`).
     * The token URL to be used for this flow.
     */
    val tokenUrl: String? = null,

    /**
     * The URL to be used for obtaining refresh tokens.
     */
    val refreshUrl: String? = null,

    /**
     * **REQUIRED**. The available scopes for the OAuth2 security scheme.
     * A map between the scope name and a short description for it.
     */
    val scopes: Map<String, String> = emptyMap(),

    /**
     * **REQUIRED** (`deviceAuthorization`). The device authorization URL to be used for this flow.
     */
    val deviceAuthorizationUrl: String? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Lists the required security schemes to execute this operation.
 * The name used for each property MUST correspond to a security scheme declared in the Security Schemes under the Components Object.
 *
 * Properties are mapped as: Scheme Name -> List<Scope Name>
 * An empty list of scopes means the scheme alone is sufficient.
 *
 * See [Security Requirement Object](https://spec.openapis.org/oas/v3.2.0#security-requirement-object)
 */
typealias SecurityRequirement = Map<String, List<String>>

/**
 * Adds metadata to a single tag that is used by the Operation Object.
 *
 * See [Tag Object](https://spec.openapis.org/oas/v3.2.0#tag-object)
 */
data class Tag(
    /**
     * **REQUIRED**. The name of the tag. Use this value in the `tags` array of an Operation.
     */
    val name: String,

    /**
     * A short summary of the tag, used for display purposes.
     */
    val summary: String? = null,

    /**
     * A description for the tag. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * Additional external documentation for this tag.
     */
    val externalDocs: ExternalDocumentation? = null,

    /**
     * The `name` of a tag that this tag is nested under.
     * The named tag MUST exist in the API description, and circular references between parent and child tags MUST NOT be used.
     */
    val parent: String? = null,

    /**
     * A machine-readable string to categorize what sort of tag it is.
     * Any string value can be used; common uses are `nav` for Navigation, `badge` for visible badges, `audience` for APIs used by different groups.
     */
    val kind: String? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Allows referencing an external resource for extended documentation.
 *
 * See [External Documentation Object](https://spec.openapis.org/oas/v3.2.0#external-documentation-object)
 */
data class ExternalDocumentation(
    /**
     * A description of the target documentation. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * **REQUIRED**. The URI for the target documentation. This MUST be in the form of a URI.
     */
    val url: String,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)
