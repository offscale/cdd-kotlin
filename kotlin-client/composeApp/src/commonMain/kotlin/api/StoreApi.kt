                        package com.example.auto.api
            
            import com.example.auto.dto.*
            import com.example.auto.ApiException
            
            import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
            
            // Result is part of kotlin standard library since 1.3
            
            
            interface IStoreApi {
                /**
     * Returns pet inventories by status
     *
     * Returns a map of status codes to quantities
     * @tag store
     * @security {"api_key":[]}
     * @response 200 Unit successful operation
     */
    suspend fun getInventory(): Result<Unit>

    /**
     * Place an order for a pet
     * @tag store
     * @param body order placed for purchasing the pet
     * @paramSchema body {"$ref":"#/definitions/Order"}
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid Order
     */
    suspend fun placeOrder(body: Order): Result<Unit>

    /**
     * Find purchase order by ID
     *
     * For valid response try integer IDs with value >= 1 and <= 10. Other values will generated exceptions
     * @tag store
     * @param orderId ID of pet that needs to be fetched
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Order not found
     */
    suspend fun getOrderById(orderId: String): Result<Unit>

    /**
     * Delete purchase order by ID
     *
     * For valid response try integer IDs with positive integer value. Negative or non-integer values will generate API errors
     * @tag store
     * @param orderId ID of the order that needs to be deleted
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Order not found
     */
    suspend fun deleteOrder(orderId: String): Result<Unit>
            }
            
            class StoreApi(
                private val client: HttpClient,
                private val baseUrl: String = "/"
            ) : IStoreApi {
                /**
     * Returns pet inventories by status
     *
     * Returns a map of status codes to quantities
     * @tag store
     * @security {"api_key":[]}
     * @response 200 Unit successful operation
     */
    override suspend fun getInventory(): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/store/inventory") {
            method = HttpMethod.Get
        }
        if (response.status.isSuccess()) {
            Result.success(response.body<Unit>())
        } else {
            Result.failure(ApiException("Error: " + response.status))
        }
    } catch (e: Exception) {
        Result.failure(ApiException(e.message ?: "Unknown Error"))
    }
}

    /**
     * Place an order for a pet
     * @tag store
     * @param body order placed for purchasing the pet
     * @paramSchema body {"$ref":"#/definitions/Order"}
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid Order
     */
    override suspend fun placeOrder(body: Order): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/store/order") {
            method = HttpMethod.Post
        contentType(io.ktor.http.ContentType.Application.Json)
        setBody(body)
        }
        if (response.status.isSuccess()) {
            Result.success(response.body<Unit>())
        } else {
            Result.failure(ApiException("Error: " + response.status))
        }
    } catch (e: Exception) {
        Result.failure(ApiException(e.message ?: "Unknown Error"))
    }
}

    /**
     * Find purchase order by ID
     *
     * For valid response try integer IDs with value >= 1 and <= 10. Other values will generated exceptions
     * @tag store
     * @param orderId ID of pet that needs to be fetched
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Order not found
     */
    override suspend fun getOrderById(orderId: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/store/order/${encodePathComponent(orderId.toString(), false)}") {
            method = HttpMethod.Get
        }
        if (response.status.isSuccess()) {
            Result.success(response.body<Unit>())
        } else {
            Result.failure(ApiException("Error: " + response.status))
        }
    } catch (e: Exception) {
        Result.failure(ApiException(e.message ?: "Unknown Error"))
    }
}

    /**
     * Delete purchase order by ID
     *
     * For valid response try integer IDs with positive integer value. Negative or non-integer values will generate API errors
     * @tag store
     * @param orderId ID of the order that needs to be deleted
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Order not found
     */
    override suspend fun deleteOrder(orderId: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/store/order/${encodePathComponent(orderId.toString(), false)}") {
            method = HttpMethod.Delete
        }
        if (response.status.isSuccess()) {
            Result.success(response.body<Unit>())
        } else {
            Result.failure(ApiException("Error: " + response.status))
        }
    } catch (e: Exception) {
        Result.failure(ApiException(e.message ?: "Unknown Error"))
    }
}
                companion object {
        private /** Auto generated docs */ fun isUnreserved(ch: Char): Boolean {
            return ch.isLetterOrDigit() || ch == '-' || ch == '.' || ch == '_' || ch == '~'
        }
        
        private /** Auto generated docs */ fun isHexDigit(ch: Char): Boolean {
            return ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
        }
        
        private /** Auto generated docs */ fun byteToHex(b: Byte): String {
            val value = b.toInt() and 0xFF
            val digits = "0123456789ABCDEF"
            return "${digits[value ushr 4]}${digits[value and 0x0F]}"
        }
        
        private /** Auto generated docs */ fun encodePathComponent(value: String, allowReserved: Boolean): String {
            if (value.isEmpty()) return value
            val sb = StringBuilder()
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch == '%' && i + 2 < value.length && isHexDigit(value[i + 1]) && isHexDigit(value[i + 2])) {
                    sb.append(ch).append(value[i + 1]).append(value[i + 2])
                    i += 3
                    continue
                }
                val allowed = isUnreserved(ch) || (allowReserved && isPathReservedAllowed(ch))
                if (allowed) {
                    sb.append(ch)
                    i += 1
                    continue
                }
                val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                for (b in bytes) {
                    sb.append('%')
                    sb.append(byteToHex(b))
                }
                i += 1
            }
            return sb.toString()
        }
        
        private /** Auto generated docs */ fun isPathReservedAllowed(ch: Char): Boolean {
            return ":@!$&'()*+,;=".indexOf(ch) >= 0
        }
    }
            
            }