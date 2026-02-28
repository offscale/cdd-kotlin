            package com.example.auto.dto

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
            
            import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable 
            
            /**
 * @xmlName Pet
 */
@Serializable
            data class Pet( 
                @SerialName("id")
    val id: Long? = null,

    @SerialName("category")
    val category: Category? = null,

    /**
     * @example doggie
     */
    @SerialName("name")
    val name: String,

    /**
     * @xmlWrapped
     */
    @SerialName("photoUrls")
    val photoUrls: List<String>,

    /**
     * @xmlWrapped
     */
    @SerialName("tags")
    val tags: List<Tag>? = null,

    /**
     * pet status in the store
     *
     * @enum "available"
     *
     * @enum "pending"
     *
     * @enum "sold"
     */
    @SerialName("status")
    val status: String? = null
            )