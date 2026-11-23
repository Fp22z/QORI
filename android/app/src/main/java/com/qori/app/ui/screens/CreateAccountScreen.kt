package com.qori.app.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qori.app.data.CreateAccountRequest
import com.qori.app.data.CreateAccountResponse
import com.qori.app.data.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CreateAccountScreen(token: String, onBack: () -> Unit, onSaved: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var openingDate by remember { mutableStateOf<LocalDate?>(null) }
    val context = LocalContext.current
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    BackHandler { if (!saving) onBack() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cuenta financiera", style = MaterialTheme.typography.headlineSmall)
        Text("Crear cuenta virtual", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(), enabled = !saving,
            label = { Text("Nombre de la cuenta") }, singleLine = true
        )
        AccountSelector(
            "Tipo de cuenta", type,
            mapOf("CASH" to "Efectivo", "BANK" to "Banco", "DIGITAL_WALLET" to "Billetera digital"),
            !saving
        ) { type = it }
        AccountSelector("Moneda", currency, mapOf("PEN" to "Soles", "USD" to "Dólares"), !saving) {
            currency = it
        }
        OutlinedTextField(
            balance, { balance = it }, Modifier.fillMaxWidth(), enabled = !saving,
            label = { Text("Saldo inicial") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text("Fecha de inicio", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val initialDate = openingDate ?: LocalDate.now(ZoneId.of("America/Lima"))
                DatePickerDialog(
                    context,
                    { _, year, month, day -> openingDate = LocalDate.of(year, month + 1, day) },
                    initialDate.year,
                    initialDate.monthValue - 1,
                    initialDate.dayOfMonth
                ).show()
            }
        ) {
            Text(openingDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Seleccionar fecha")
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
            val accountName = name.trim()
            val amount = balance.trim().replace(',', '.')
            error = when {
                accountName.isEmpty() -> "Ingresa el nombre."
                accountName.length > 120 -> "El nombre admite hasta 120 caracteres."
                type.isEmpty() -> "Selecciona el tipo de cuenta."
                currency.isEmpty() -> "Selecciona la moneda."
                !amount.matches(Regex("[0-9]{1,17}([.][0-9]{1,2})?")) ->
                    "Ingresa un saldo desde cero con hasta dos decimales."
                else -> ""
            }
            if (error.isEmpty()) {
                saving = true
                val account = CreateAccountRequest(accountName, type, currency, amount, openingDate?.toString())
                RetrofitClient.apiService.createAccount(token, account)
                    .enqueue(object : Callback<CreateAccountResponse> {
                        override fun onResponse(
                            call: Call<CreateAccountResponse>,
                            response: Response<CreateAccountResponse>
                        ) {
                            saving = false
                            if (response.isSuccessful && response.body() != null) {
                                onSaved()
                            } else {
                                error = when (response.code()) {
                                    401 -> "Tu sesión expiró. Vuelve a iniciar sesión."
                                    403 -> "Necesitas un perfil para crear una cuenta."
                                    422 -> "Revisa los datos de la cuenta."
                                    else -> "No se pudo guardar la cuenta."
                                }
                            }
                        }

                        override fun onFailure(call: Call<CreateAccountResponse>, t: Throwable) {
                            saving = false
                            error = "Error de conexión al guardar la cuenta."
                        }
                    })
            }
        }) { Text(if (saving) "Guardando…" else "Guardar cuenta") }
        TextButton(enabled = !saving, onClick = onBack) { Text("Cancelar") }
    }
}

@Composable
internal fun AccountSelector(
    label: String,
    value: String,
    options: Map<String, String>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            val selected = options[value]
            Text(if (selected == null) label else "$label: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, description) ->
                DropdownMenuItem(text = { Text(description) }, onClick = {
                    expanded = false
                    onSelect(key)
                })
            }
        }
    }
}
