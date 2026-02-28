                        package com.example.auto.api
            
            import com.example.auto.dto.*
            import com.example.auto.ApiException
            
            import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
            
            // Result is part of kotlin standard library since 1.3
            
            
            interface IUserApi {
                /**
     * Creates list of users with given input array
     * @tag user
     * @param body List of user object
     * @paramSchema body {"type":"array","items":{"$ref":"#/definitions/User"}}
     * @response default Unit successful operation
     */
    suspend fun createUsersWithListInput(body: List<User>): Result<Unit>

    /**
     * Get user by user name
     * @tag user
     * @param username The name that needs to be fetched. Use user1 for testing. 
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid username supplied
     * @response 404 Unit User not found
     */
    suspend fun getUserByName(username: String): Result<Unit>

    /**
     * Updated user
     *
     * This can only be done by the logged in user.
     * @tag user
     * @param username name that need to be updated
     * @param body Updated user object
     * @paramSchema body {"$ref":"#/definitions/User"}
     * @response 400 Unit Invalid user supplied
     * @response 404 Unit User not found
     */
    suspend fun updateUser(username: String, body: User): Result<Unit>

    /**
     * Delete user
     *
     * This can only be done by the logged in user.
     * @tag user
     * @param username The name that needs to be deleted
     * @response 400 Unit Invalid username supplied
     * @response 404 Unit User not found
     */
    suspend fun deleteUser(username: String): Result<Unit>

    /**
     * Logs user into the system
     * @tag user
     * @param username The user name for login
     * @param password The password for login in clear text
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid username/password supplied
     * @responseHeaders 200 {"X-Expires-After":{"description":"date in UTC when token expires","style":"simple","explode":false},"X-Rate-Limit":{"description":"calls per hour allowed by the user","style":"simple","explode":false}}
     */
    suspend fun loginUser(username: String, password: String): Result<Unit>

    /**
     * Logs out current logged in user session
     * @tag user
     * @response default Unit successful operation
     */
    suspend fun logoutUser(): Result<Unit>

    /**
     * Creates list of users with given input array
     * @tag user
     * @param body List of user object
     * @paramSchema body {"type":"array","items":{"$ref":"#/definitions/User"}}
     * @response default Unit successful operation
     */
    suspend fun createUsersWithArrayInput(body: List<User>): Result<Unit>

    /**
     * Create user
     *
     * This can only be done by the logged in user.
     * @tag user
     * @param body Created user object
     * @paramSchema body {"$ref":"#/definitions/User"}
     * @response default Unit successful operation
     */
    suspend fun createUser(body: User): Result<Unit>
            }
            
            class UserApi(
                private val client: HttpClient,
                private val baseUrl: String = "/"
            ) : IUserApi {
                /**
     * Creates list of users with given input array
     * @tag user
     * @param body List of user object
     * @paramSchema body {"type":"array","items":{"$ref":"#/definitions/User"}}
     * @response default Unit successful operation
     */
    override suspend fun createUsersWithListInput(body: List<User>): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/user/createWithList") {
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
     * Get user by user name
     * @tag user
     * @param username The name that needs to be fetched. Use user1 for testing. 
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid username supplied
     * @response 404 Unit User not found
     */
    override suspend fun getUserByName(username: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/user/${encodePathComponent(username.toString(), false)}") {
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
     * Updated user
     *
     * This can only be done by the logged in user.
     * @tag user
     * @param username name that need to be updated
     * @param body Updated user object
     * @paramSchema body {"$ref":"#/definitions/User"}
     * @response 400 Unit Invalid user supplied
     * @response 404 Unit User not found
     */
    override suspend fun updateUser(username: String, body: User): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/user/${encodePathComponent(username.toString(), false)}") {
            method = HttpMethod.Put
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
     * Delete user
     *
     * This can only be done by the logged in user.
     * @tag user
     * @param username The name that needs to be deleted
     * @response 400 Unit Invalid username supplied
     * @response 404 Unit User not found
     */
    override suspend fun deleteUser(username: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/user/${encodePathComponent(username.toString(), false)}") {
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

    /**
     * Logs user into the system
     * @tag user
     * @param username The user name for login
     * @param password The password for login in clear text
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid username/password supplied
     * @responseHeaders 200 {"X-Expires-After":{"description":"date in UTC when token expires","style":"simple","explode":false},"X-Rate-Limit":{"description":"calls per hour allowed by the user","style":"simple","explode":false}}
     */
    override suspend fun loginUser(username: String, password: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/user/login") {
            method = HttpMethod.Get
        parameter("username", username)
        parameter("password", password)
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
     * Logs out current logged in user session
     * @tag user
     * @response default Unit successful operation
     */
    override suspend fun logoutUser(): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/user/logout") {
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
     * Creates list of users with given input array
     * @tag user
     * @param body List of user object
     * @paramSchema body {"type":"array","items":{"$ref":"#/definitions/User"}}
     * @response default Unit successful operation
     */
    override suspend fun createUsersWithArrayInput(body: List<User>): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/user/createWithArray") {
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
     * Create user
     *
     * This can only be done by the logged in user.
     * @tag user
     * @param body Created user object
     * @paramSchema body {"$ref":"#/definitions/User"}
     * @response default Unit successful operation
     */
    override suspend fun createUser(body: User): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/user") {
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