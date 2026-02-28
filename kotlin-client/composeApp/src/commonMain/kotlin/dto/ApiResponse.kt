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
            
            @Serializable
            data class ApiResponse( 
                @SerialName("code")
    val code: Int? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("message")
    val message: String? = null
            )