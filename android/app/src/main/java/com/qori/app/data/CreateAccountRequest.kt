package com.qori.app.data

import com.google.gson.annotations.SerializedName

data class CreateAccountRequest(
    val name: String,
    val type: String,
    val currency: String,
    @SerializedName("opening_balance") val openingBalance: String,
    @SerializedName("opening_on") val openingOn: String? = null
)
