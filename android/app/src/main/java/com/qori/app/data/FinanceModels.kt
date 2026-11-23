package com.qori.app.data

import com.google.gson.annotations.SerializedName

data class AccountResponse(
    val id: String,
    val name: String,
    val currency: String,
    @SerializedName("opening_on") val openingOn: String,
    @SerializedName("available_balance") val availableBalance: String,
    @SerializedName("reserved_balance") val reservedBalance: String
)

data class CategoryResponse(val id: String, val name: String)

data class CreateIncomeRequest(
    @SerializedName("account_id") val accountId: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("occurred_on") val occurredOn: String,
    val amount: String,
    @SerializedName("request_id") val requestId: String
)

data class IncomeResponse(val id: String)
