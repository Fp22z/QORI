package com.qori.app.data

import com.google.gson.annotations.SerializedName

// HU09 - Registrar gasto: JSON que se envía a POST /movimientos/gastos
data class CreateExpenseRequest(
    @SerializedName("account_id") val accountId: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("occurred_on") val occurredOn: String,
    val amount: String,
    @SerializedName("request_id") val requestId: String
)

data class ExpenseResponse(val id: String)
