package com.qori.app.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qori.app.data.AccountResponse
import com.qori.app.data.CategoryResponse
import com.qori.app.data.CreateIncomeRequest
import com.qori.app.data.RetrofitClient
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun IncomeScreen(token: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(ZoneId.of("America/Lima"))
    var accounts by remember { mutableStateOf<List<AccountResponse>?>(null) }
    var categories by remember { mutableStateOf<List<CategoryResponse>?>(null) }
    var accountId by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var confirmationId by rememberSaveable { mutableStateOf("") }
    var pendingRetry by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }

    BackHandler { if (!saving) onBack() }
    LaunchedEffect(token, attempt) {
        loadError = ""
        val api = RetrofitClient.apiService
        accounts = loadFinanceList({ api.listAccounts(token) }) { loadError = it }
        if (accounts != null) {
            categories = loadFinanceList({ api.listCategories(token, "INCOME") }) { loadError = it }
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Registrar ingreso", style = MaterialTheme.typography.headlineSmall)
        when {
            loadError.isNotEmpty() -> {
                Text(loadError, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("Reintentar") }
            }
            accounts == null || categories == null -> CircularProgressIndicator()
            accounts.isNullOrEmpty() -> Text("Agrega una cuenta antes de registrar un ingreso.")
            categories.isNullOrEmpty() -> Text("Todavía no hay categorías de ingreso disponibles.")
            else -> {
                val editable = !saving && !pendingRetry
                AccountSelector("Cuenta", accountId, accounts.orEmpty().associate {
                    it.id to "${it.name} · ${it.currency}"
                }, editable) { accountId = it }
                AccountSelector("Categoría de ingreso", categoryId, categories.orEmpty().associate {
                    it.id to it.name
                }, editable) { categoryId = it }
                Text("Fecha del ingreso", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editable,
                    onClick = {
                        val selected = LocalDate.parse(date)
                        DatePickerDialog(
                            context, { _, year, month, day ->
                                date = LocalDate.of(year, month + 1, day).toString()
                            },
                            selected.year, selected.monthValue - 1, selected.dayOfMonth
                        ).show()
                    }
                ) { Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
                OutlinedTextField(
                    amount, { amount = it }, Modifier.fillMaxWidth(), enabled = editable,
                    singleLine = true, label = { Text("Importe") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(
                    enabled = !saving, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val account = accounts.orEmpty().find { it.id == accountId }
                        val normalizedAmount = amount.trim().replace(',', '.')
                        val selectedDate = LocalDate.parse(date)
                        error = when {
                            pendingRetry -> ""
                            account == null -> "Selecciona una cuenta."
                            categories.orEmpty().none { it.id == categoryId } -> "Selecciona una categoría de ingreso."
                            selectedDate > today -> "La fecha no puede estar en el futuro."
                            selectedDate < LocalDate.parse(account.openingOn) ->
                                "El ingreso no puede ser anterior a la apertura de la cuenta."
                            !normalizedAmount.matches(Regex("[0-9]{1,17}([.][0-9]{1,2})?")) ||
                                normalizedAmount.toBigDecimal() <= java.math.BigDecimal.ZERO ->
                                "Ingresa un importe positivo con hasta dos decimales."
                            else -> ""
                        }
                        if (error.isEmpty()) {
                            if (confirmationId.isEmpty()) confirmationId = UUID.randomUUID().toString()
                            val income = CreateIncomeRequest(accountId, categoryId, date, normalizedAmount, confirmationId)
                            saving = true
                            pendingRetry = true
                            scope.launch {
                                try {
                                    val response = RetrofitClient.apiService.createIncome(token, income)
                                    if (response.isSuccessful && response.body() != null) {
                                        pendingRetry = false
                                        onSaved()
                                    } else {
                                        val code = response.code()
                                        if (code in 400..499 && code != 401 && code != 408) {
                                            pendingRetry = false
                                            confirmationId = ""
                                        }
                                        error = when (code) {
                                            401 -> "Tu sesión expiró."
                                            404 -> "La cuenta ya no está disponible."
                                            409 -> "Esta confirmación ya se utilizó."
                                            422 -> "Revisa la cuenta, la categoría, la fecha y el importe."
                                            else -> "No se pudo confirmar el ingreso. Reintenta con los mismos datos."
                                        }
                                    }
                                } catch (_: IOException) {
                                    error = "Se perdió la conexión. Reintenta la misma confirmación."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                ) {
                    Text(when {
                        saving -> "Guardando…"
                        pendingRetry -> "Reintentar confirmación"
                        else -> "Confirmar ingreso"
                    })
                }
            }
        }
        TextButton(enabled = !saving, onClick = onBack) { Text("Volver") }
    }
}
