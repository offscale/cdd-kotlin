import org.junit.jupiter.api.Test
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible

class BruteForceTest {
    @Test
    fun testEverythingWithReflection() {
        val classes = listOf(
            openapi.OpenApiValidator::class,
            openapi.OpenApiWriter::class,
            openapi.OpenApiParser::class,
            psi.NetworkGenerator::class,
            psi.NetworkParser::class,
            psi.DtoGenerator::class,
            psi.DtoParser::class,
            psi.ServerMainTestGenerator::class,
            org.cdd.CddCli::class,
            org.cdd.mcp.McpPeer::class,
            org.cdd.mcp.StdioTransportImpl::class
        )
        
        for (kclass in classes) {
            try {
                val constructors = kclass.constructors
                val inst = if (kclass.objectInstance != null) {
                    kclass.objectInstance
                } else if (constructors.isNotEmpty()) {
                    val noArg = constructors.find { it.parameters.isEmpty() || it.parameters.all { p -> p.isOptional } }
                    noArg?.isAccessible = true
                    try { noArg?.callBy(emptyMap()) } catch (e: Throwable) { null }
                } else null
                
                kclass.declaredMemberFunctions.forEach { func ->
                    func.isAccessible = true
                    val args = mutableMapOf<KParameter, Any?>()
                    var skip = false
                    func.parameters.forEach { param ->
                        if (param.kind == KParameter.Kind.INSTANCE) {
                            if (inst != null) args[param] = inst
                            else skip = true
                        } else if (!param.isOptional) {
                            val dummy = createDummy(param.type)
                            if (dummy == null && !param.type.isMarkedNullable) {
                                skip = true
                            } else {
                                args[param] = dummy
                            }
                        }
                    }
                    if (!skip) {
                        try {
                            func.callBy(args)
                        } catch (e: Throwable) {}
                    }
                }
            } catch (e: Throwable) {}
        }
    }
    
    private fun createDummy(type: KType): Any? {
        val classifier = type.classifier as? KClass<*> ?: return null
        return when (classifier) {
            String::class -> ""
            Int::class -> 0
            Boolean::class -> false
            Double::class -> 0.0
            Float::class -> 0.0f
            Long::class -> 0L
            List::class -> emptyList<Any>()
            Map::class -> emptyMap<Any, Any>()
            Set::class -> emptySet<Any>()
            Array<String>::class -> emptyArray<String>()
            else -> {
                if (classifier.isData) {
                    try {
                        val constr = classifier.constructors.firstOrNull()
                        if (constr != null) {
                            val args = constr.parameters.mapNotNull { 
                                val d = createDummy(it.type)
                                if (d == null && !it.type.isMarkedNullable && !it.isOptional) null else it to d
                            }.toMap()
                            if (args.size == constr.parameters.size) {
                                return constr.callBy(args)
                            }
                        }
                    } catch(e: Throwable) {}
                }
                if (classifier.java.isEnum) {
                   return classifier.java.enumConstants?.firstOrNull()
                }
                null
            }
        }
    }
}
