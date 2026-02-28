            package com.example.auto.dto

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
            
            import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable 
            
            /**
 * @xmlName Order
 */
@Serializable
            data class Order( 
                @SerialName("id")
    val id: Long? = null,

    @SerialName("petId")
    val petId: Long? = null,

    @SerialName("quantity")
    val quantity: Int? = null,

    @SerialName("shipDate")
    val shipDate: Instant? = null,

    /**
     * Order Status
     *
     * @enum "placed"
     *
     * @enum "approved"
     *
     * @enum "delivered"
     */
    @SerialName("status")
    val status: String? = null,

    @SerialName("complete")
    val complete: Boolean? = null
            )