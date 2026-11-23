package com.qori.app.data

import com.google.gson.annotations.SerializedName

// HU10 - Consultar saldos: respuesta de GET /saldos
data class AccountBalance(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    @SerializedName("opening_balance") val openingBalance: String,
    @SerializedName("registered_balance") val registeredBalance: String,
    @SerializedName("reserved_balance") val reservedBalance: String,
    @SerializedName("available_balance") val availableBalance: String
)

data class CurrencyTotal(
    val currency: String,
    @SerializedName("registered_balance") val registeredBalance: String,
    @SerializedName("reserved_balance") val reservedBalance: String,
    @SerializedName("available_balance") val availableBalance: String
)

data class BalancesResponse(
    val accounts: List<AccountBalance>,
    val totals: List<CurrencyTotal>
)
