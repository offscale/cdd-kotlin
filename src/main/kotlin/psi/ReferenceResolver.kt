package psi

/**
 * Logic for resolving OpenAPI Reference Objects ($ref) into distinct Kotlin type names.
 * Handles internal constraints, file paths, and fragment identifiers.
 */
object ReferenceResolver {

    /**
     * Extracts a usable Kotlin Type Name from a URI Reference.
     *
     * Examples:
     * - `#/components/schemas/User` -> `User`
     * - `#/definitions/Pet` -> `Pet`
     * - `User` -> `User`
     * - `./models/Address.json` -> `Address`
     * - `https://example.com/api/contracts/Order.yaml#/definitions/Detail` -> `Detail`
     */
    fun resolveRefToType(ref: String): String {
        // 1. Handle Fragment Identifier (#)
        if (ref.contains("#")) {
            val fragment = ref.substringAfterLast("#")
            // If the fragment is a path like /components/schemas/Name, take the last segment
            if (fragment.contains("/")) {
                return fragment.substringAfterLast("/")
            }
            // If fragment is just #Name
            if (fragment.isNotEmpty() && !fragment.startsWith("/")) {
                return fragment
            }
        }

        // 2. Handle File Paths / URLs (No fragment, or fragment processing failed to yield name)
        // Take the file name without extension
        val path = ref.substringBefore("#")
        if (path.isNotEmpty()) {
            val filename = path.substringAfterLast("/")
            if (filename.contains(".")) {
                return filename.substringBeforeLast(".")
            }
            if (filename.isNotEmpty()) {
                return filename
            }
        }

        // Fallback: Return raw ref if simple string
        return ref.substringAfterLast("/")
    }
}
