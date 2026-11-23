package com.qori.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qori.app.data.AccountResponse
import com.qori.app.data.RetrofitClient
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountsScreen(
    token: String,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onIncome: () -> Unit,
    onCategories: () -> Unit
) {
    var accounts by remember { mutableStateOf<List<AccountResponse>?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(token, attempt) {
        accounts = null
        error = ""
        accounts = loadFinanceList({ RetrofitClient.apiService.listAccounts(token) }) { error = it }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TextButton(onClick = onBack) { Text("Volver") }
                Text("Mis cuentas", style = MaterialTheme.typography.headlineSmall)
                Text("Saldo por cuenta virtual", style = MaterialTheme.typography.bodySmall)
            }
        }
        when {
            error.isNotEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { attempt++ }) { Text("Reintentar") }
                }
            }
            accounts == null -> item { CircularProgressIndicator() }
            accounts.isNullOrEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Todavía no tienes cuentas. Agrega una para registrar tus ingresos.")
            }
            else -> {
                val current = accounts.orEmpty()
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Saldo disponible", style = MaterialTheme.typography.titleMedium)
                            current.groupBy { it.currency }.forEach { (currency, sameCurrency) ->
                                Text(formatMoney(currency, sameCurrency.sumOf { it.availableBalance.toBigDecimal() }),
                                    style = MaterialTheme.typography.headlineSmall)
                                Text("Reservado: " + formatMoney(currency,
                                    sameCurrency.sumOf { it.reservedBalance.toBigDecimal() }))
                            }
                        }
                    }
                }
                items(current, key = { it.id }) { account ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(account.name, style = MaterialTheme.typography.titleMedium)
                            Text("${account.currency} · Disponible " +
                                formatMoney(account.currency, account.availableBalance.toBigDecimal()))
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar cuenta") }
                if (!accounts.isNullOrEmpty()) {
                    OutlinedButton(onClick = onIncome, modifier = Modifier.fillMaxWidth()) {
                        Text("Registrar ingreso")
                    }
                }
                TextButton(onClick = onCategories) { Text("Consultar categorías") }
            }
        }
    }
}

private fun formatMoney(currency: String, amount: java.math.BigDecimal): String {
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PE"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    val symbol = if (currency == "PEN") "S/" else "US$"
    return "$symbol ${formatter.format(amount)}"
}
