package com.qori.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qori.app.data.BalancesResponse
import com.qori.app.data.RetrofitClient
import java.io.IOException
import java.math.BigDecimal
import java.util.Locale

// HU10 - Consultar saldos
// Muestra por moneda y por cuenta: saldo registrado (IND07), reservado en metas y disponible (IND08).
// Solo cuentan los movimientos confirmados. Soles y dólares nunca se suman.
@Composable
fun BalancesScreen(token: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<BalancesResponse?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)
    LaunchedEffect(token, attempt) {
        data = null
        error = ""
        try {
            val response = RetrofitClient.apiService.listBalances(token)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                data = body
            } else {
                error = if (response.code() == 401) "Tu sesión expiró. Vuelve a iniciar sesión."
                else "No se pudieron cargar los saldos."
            }
        } catch (_: IOException) {
            error = "No hay conexión con el servidor."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("Volver") } }
        item {
            Text("Mis saldos", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Registrado = saldo inicial + ingresos − gastos ± transferencias (solo confirmados). " +
                    "Disponible = registrado − reservado en metas.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        val current = data
        when {
            error.isNotEmpty() -> item {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("Reintentar") }
            }
            current == null -> item { CircularProgressIndicator() }
            current.accounts.isEmpty() -> item {
                Text("Todavía no tienes cuentas. Crea una para ver tus saldos.")
            }
            else -> {
                items(current.totals, key = { it.currency }) { total ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                if (total.currency == "PEN") "Total en soles" else "Total en dólares",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                balanceMoney(total.currency, total.availableBalance),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Disponible", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            BalanceRow("Registrado", balanceMoney(total.currency, total.registeredBalance))
                            BalanceRow("Reservado en metas", balanceMoney(total.currency, total.reservedBalance))
                        }
                    }
                }
                item { Text("Por cuenta", style = MaterialTheme.typography.titleMedium) }
                items(current.accounts, key = { it.id }) { account ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(account.name, style = MaterialTheme.typography.titleMedium)
                            Text("${accountTypeName(account.type)} · ${account.currency}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            BalanceRow("Saldo inicial", balanceMoney(account.currency, account.openingBalance))
                            BalanceRow("Registrado", balanceMoney(account.currency, account.registeredBalance))
                            BalanceRow("Reservado", balanceMoney(account.currency, account.reservedBalance))
                            BalanceRow("Disponible", balanceMoney(account.currency, account.availableBalance), bold = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun accountTypeName(type: String): String = when (type) {
    "CASH" -> "Efectivo"
    "BANK" -> "Banco"
    "DIGITAL_WALLET" -> "Billetera digital"
    else -> type
}

private fun balanceMoney(currency: String, value: String): String {
    val symbol = if (currency == "PEN") "S/" else "US$"
    val number = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
    return "$symbol " + String.format(Locale.forLanguageTag("es-PE"), "%,.2f", number)
}
