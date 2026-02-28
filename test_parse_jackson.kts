import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper

val mapper = ObjectMapper(JsonFactory())
val map = mapOf("hello" to "world")
println(mapper.writeValueAsString(map))
