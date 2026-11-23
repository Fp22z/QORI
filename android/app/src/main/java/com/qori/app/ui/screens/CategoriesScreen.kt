package com.qori.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qori.app.data.CategoryResponse
import com.qori.app.data.RetrofitClient
import retrofit2.Response
import java.io.IOException

@Composable
fun CategoriesScreen(token: String, onBack: () -> Unit) {
    var type by remember { mutableStateOf("INCOME") }
    var categories by remember { mutableStateOf<List<CategoryResponse>?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(token, type, attempt) {
        categories = null
        error = ""
        categories = loadFinanceList({ RetrofitClient.apiService.listCategories(token, type) }) { error = it }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("Volver") } }
        item { Text("Categorías", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == "INCOME", { type = "INCOME" }, { Text("Ingresos") })
                FilterChip(type == "EXPENSE", { type = "EXPENSE" }, { Text("Gastos") })
            }
        }
        when {
            error.isNotEmpty() -> item {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("Reintentar") }
            }
            categories == null -> item { CircularProgressIndicator() }
            categories.isNullOrEmpty() -> item { Text("No hay categorías para este tipo.") }
            else -> items(categories.orEmpty(), key = { it.id }) { category ->
                OutlinedCard(Modifier.fillMaxWidth()) { Text(category.name, Modifier.padding(14.dp)) }
            }
        }
    }
}

internal suspend fun <T> loadFinanceList(
    request: suspend () -> Response<List<T>>,
    onError: (String) -> Unit
): List<T>? {
    return try {
        val response = request()
        val result = response.body()
        if (response.isSuccessful && result != null) result
        else {
            onError(if (response.code() == 401) "Tu sesión expiró. Vuelve a iniciar sesión."
                else "No se pudieron cargar los datos.")
            null
        }
    } catch (_: IOException) {
        onError("No hay conexión con el servidor.")
        null
    }
}
