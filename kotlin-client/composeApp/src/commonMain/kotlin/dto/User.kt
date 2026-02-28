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
 * @xmlName User
 */
@Serializable
            data class User( 
                @SerialName("id")
    val id: Long? = null,

    @SerialName("username")
    val username: String? = null,

    @SerialName("firstName")
    val firstName: String? = null,

    @SerialName("lastName")
    val lastName: String? = null,

    @SerialName("email")
    val email: String? = null,

    @SerialName("password")
    val password: String? = null,

    @SerialName("phone")
    val phone: String? = null,

    /**
     * User Status
     */
    @SerialName("userStatus")
    val userStatus: Int? = null
            )