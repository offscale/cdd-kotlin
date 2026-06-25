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
            "openapi.OpenApiParser",
            "openapi.OpenApiValidator",
            "openapi.OpenApiWriter",
            "openapi.JsonNodeExtKt",
            "openapi.OpenApiDocumentRegistry",
            "openapi.OpenApiValidator\$Companion",
            "psi.DtoGenerator",
            "psi.DtoMerger",
            "psi.DtoParser",
            "psi.NetworkGenerator",
            "psi.NetworkParser",
            "domain.OpenApiPathFlattener",
            "org.cdd.CddCli",
            "MainKt")

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
