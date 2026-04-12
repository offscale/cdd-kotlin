package openapi

fun isAbsoluteUri(value: String): Boolean {
    return value.startsWith("http://") || value.startsWith("https://") || value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))
}

fun resolveUri(base: String, ref: String): String {
    if (isAbsoluteUri(ref)) return ref
    if (base.endsWith("/")) return base + ref
    return base.substringBeforeLast("/") + "/" + ref
}
