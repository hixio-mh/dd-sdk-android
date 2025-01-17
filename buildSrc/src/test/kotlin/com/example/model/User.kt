package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

internal data class User(
    @SerializedName("username")
    val username: String,
    @SerializedName("host")
    val host: String,
    @SerializedName("firstname")
    val firstname: String? = null,
    @SerializedName("lastname")
    val lastname: String,
    @SerializedName("contact_type")
    val contactType: ContactType
) {
    enum class ContactType {
        @SerializedName("personal")
        PERSONAL,

        @SerializedName("professional")
        PROFESSIONAL
    }
}
