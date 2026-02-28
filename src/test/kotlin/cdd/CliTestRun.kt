package cdd
import com.fasterxml.jackson.databind.ObjectMapper
fun main() {
    val mapper = ObjectMapper()
    println(mapper.writeValueAsString(listOf(mapOf("hello" to "world"))))
}
