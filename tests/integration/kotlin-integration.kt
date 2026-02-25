package com.example.auto

import com.example.auto.api.PetApi
import com.example.auto.dto.Pet
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationTest {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }
    
    // Fallback to swagger.io but use localhost if possible
    private val petApi = PetApi(client, "http://localhost:8080/v2")

    @Test
    fun testPetOperations() = runTest {
        val petId = (1000000..9000000).random().toLong()
        val pet = Pet(
            id = petId,
            name = "KotlinTestPet",
            photoUrls = listOf("http://example.com/photo"),
            status = "available"
        )

        // 1. Create Pet
        var result = petApi.addPet(pet)
        assertTrue(result.isSuccess, "Failed to create pet")

        // 2. Read Pet
        result = petApi.getPetById(petId.toString())
        assertTrue(result.isSuccess, "Failed to read pet")

        // 3. Update Pet
        val updatedPet = pet.copy(name = "UpdatedKotlinPet", status = "sold")
        result = petApi.updatePet(updatedPet)
        assertTrue(result.isSuccess, "Failed to update pet")

        // 4. Delete Pet
        result = petApi.deletePet(api_key = "special-key", petId = petId.toString())
        assertTrue(result.isSuccess, "Failed to delete pet")

        // 5. Verify 404
        result = petApi.getPetById(petId.toString())
        assertTrue(result.isFailure, "Pet should be deleted")
    }
}
