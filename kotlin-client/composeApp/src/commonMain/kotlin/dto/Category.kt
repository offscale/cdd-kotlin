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
 * @xmlName Category
 */
@Serializable
            data class Category( 
                @SerialName("id")
    val id: Long? = null,

    @SerialName("name")
    val name: String? = null
            )