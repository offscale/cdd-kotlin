                        package com.example.auto.api
            
            import com.example.auto.dto.*
            import com.example.auto.ApiException
            
            import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
            
            // Result is part of kotlin standard library since 1.3
            
            
            interface IPetApi {
                /**
     * uploads an image
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param petId ID of pet to update
     * @param additionalMetadata Additional data to pass to server
     * @param file file to upload
     * @response 200 Unit successful operation
     */
    suspend fun uploadFile(petId: String, additionalMetadata: String? = null, file: String? = null): Result<Unit>

    /**
     * Add a new pet to the store
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param body Pet object that needs to be added to the store
     * @paramSchema body {"$ref":"#/definitions/Pet"}
     * @response 405 Unit Invalid input
     */
    suspend fun addPet(body: Pet): Result<Unit>

    /**
     * Update an existing pet
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param body Pet object that needs to be added to the store
     * @paramSchema body {"$ref":"#/definitions/Pet"}
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Pet not found
     * @response 405 Unit Validation exception
     */
    suspend fun updatePet(body: Pet): Result<Unit>

    /**
     * Finds Pets by status
     *
     * Multiple status values can be provided with comma separated strings
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param status Status values that need to be considered for filter
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid status value
     */
    suspend fun findPetsByStatus(status: String): Result<Unit>

    /**
     * Finds Pets by tags
     *
     * Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @deprecated
     * @param tags Tags to filter by
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid tag value
     */
    suspend fun findPetsByTags(tags: String): Result<Unit>

    /**
     * Find pet by ID
     *
     * Returns a single pet
     * @tag pet
     * @security {"api_key":[]}
     * @param petId ID of pet to return
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Pet not found
     */
    suspend fun getPetById(petId: String): Result<Unit>

    /**
     * Updates a pet in the store with form data
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param petId ID of pet that needs to be updated
     * @param name Updated name of the pet
     * @param status Updated status of the pet
     * @response 405 Unit Invalid input
     */
    suspend fun updatePetWithForm(petId: String, name: String? = null, status: String? = null): Result<Unit>

    /**
     * Deletes a pet
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param petId Pet id to delete
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Pet not found
     */
    suspend fun deletePet(api_key: String? = null, petId: String): Result<Unit>
            }
            
            class PetApi(
                private val client: HttpClient,
                private val baseUrl: String = "/"
            ) : IPetApi {
                /**
     * uploads an image
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param petId ID of pet to update
     * @param additionalMetadata Additional data to pass to server
     * @param file file to upload
     * @response 200 Unit successful operation
     */
    override suspend fun uploadFile(petId: String, additionalMetadata: String?, file: String?): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/pet/${encodePathComponent(petId.toString(), false)}/uploadImage") {
            method = HttpMethod.Post
        if (additionalMetadata != null) {
                parameter("additionalMetadata", additionalMetadata)
        }
        if (file != null) {
                parameter("file", file)
        }
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
     * Add a new pet to the store
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param body Pet object that needs to be added to the store
     * @paramSchema body {"$ref":"#/definitions/Pet"}
     * @response 405 Unit Invalid input
     */
    override suspend fun addPet(body: Pet): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/pet") {
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
     * Update an existing pet
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param body Pet object that needs to be added to the store
     * @paramSchema body {"$ref":"#/definitions/Pet"}
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Pet not found
     * @response 405 Unit Validation exception
     */
    override suspend fun updatePet(body: Pet): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/pet") {
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
     * Finds Pets by status
     *
     * Multiple status values can be provided with comma separated strings
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param status Status values that need to be considered for filter
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid status value
     */
    override suspend fun findPetsByStatus(status: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/pet/findByStatus") {
            method = HttpMethod.Get
        parameter("status", status)
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
     * Finds Pets by tags
     *
     * Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @deprecated
     * @param tags Tags to filter by
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid tag value
     */
    override suspend fun findPetsByTags(tags: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/pet/findByTags") {
            method = HttpMethod.Get
        parameter("tags", tags)
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
     * Find pet by ID
     *
     * Returns a single pet
     * @tag pet
     * @security {"api_key":[]}
     * @param petId ID of pet to return
     * @response 200 Unit successful operation
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Pet not found
     */
    override suspend fun getPetById(petId: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/pet/${encodePathComponent(petId.toString(), false)}") {
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
     * Updates a pet in the store with form data
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param petId ID of pet that needs to be updated
     * @param name Updated name of the pet
     * @param status Updated status of the pet
     * @response 405 Unit Invalid input
     */
    override suspend fun updatePetWithForm(petId: String, name: String?, status: String?): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/pet/${encodePathComponent(petId.toString(), false)}") {
            method = HttpMethod.Post
        if (name != null) {
                parameter("name", name)
        }
        if (status != null) {
                parameter("status", status)
        }
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
     * Deletes a pet
     * @tag pet
     * @security {"petstore_auth":["write:pets","read:pets"]}
     * @param petId Pet id to delete
     * @response 400 Unit Invalid ID supplied
     * @response 404 Unit Pet not found
     */
    override suspend fun deletePet(api_key: String?, petId: String): Result<Unit> {
    return try {
        val response = client.request("$baseUrl/pet/${encodePathComponent(petId.toString(), false)}") {
            method = HttpMethod.Delete
        if (api_key != null) {
                header("api_key", api_key)
        }
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