package openapi

fun isAbsoluteUri(value: String): Boolean {
    return value.startsWith("http://") || value.startsWith("https://") || value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))
}

fun resolveUri(base: String, ref: String): String {
    if (isAbsoluteUri(ref)) return ref
    if (ref.startsWith("/")) {
        val schemeIdx = base.indexOf("://")
        if (schemeIdx != -1) {
            val pathIdx = base.indexOf("/", schemeIdx + 3)
            if (pathIdx != -1) {
                return base.substring(0, pathIdx) + ref
            }
        }
        return base + ref
    }
    if (base.endsWith("/")) return base + ref
    return base.substringBeforeLast("/") + "/" + ref
}
