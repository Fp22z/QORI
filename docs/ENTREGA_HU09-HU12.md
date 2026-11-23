# Entrega HU09–HU12 — José Barrientos

Rama: `feature/hu09-12-jose` · Formato según la *Guía de desarrollo colaborativo* (sección 13).

| Campo | Detalle |
|---|---|
| HU / responsable | HU09 Registrar gasto · HU10 Consultar saldos · HU11 Recuperar contraseña · HU12 Perfil e idioma / José Barrientos |
| Pantallas Kotlin (nuevas) | `ExpenseScreen.kt`, `BalancesScreen.kt`, `RecoverPasswordScreen.kt`, `ProfileScreen.kt` |
| Modelos Request/Response (nuevos) | `ExpenseModels.kt`, `BalanceModels.kt`, `RecoveryModels.kt`, `ProfileModels.kt` |
| Router y endpoints | `movimientos.py`: `POST /movimientos/gastos` · `saldos.py` (nuevo): `GET /saldos`, `GET /saldos/{id}` · `recuperacion.py` (nuevo): `POST /recuperacion/solicitar`, `POST /recuperacion/restablecer` · `perfil.py` (nuevo): `GET /perfil`, `PATCH /perfil` |
| Tablas usadas / autorización | `transactions` (inserta EXPENSE), `accounts`, `categories`, `internal_transfers`, `goal_reservations` (solo lectura), `profiles` (lee/actualiza). El usuario siempre sale del token (`require_user_id`); cuentas, categorías y perfil se filtran por ese usuario. Recuperación usa Supabase Auth (`/recover`, `/verify`, `/user`). |
| Archivos compartidos modificados | `main.py` (+3 `include_router`), `ApiService.kt` (+7 funciones, ninguna borrada), `MainActivity.kt` (+4 pantallas en la misma selección por estado), `HomeScreen.kt` (botones "Registrar gasto", "Ver saldos" y "Mi perfil" con parámetros opcionales), `LoginScreen.kt` (solo el enlace "¿Olvidaste tu contraseña?" con parámetro opcional; el login no cambia). `auth.py` **no** se modificó. |
| Pruebas FastAPI | Probado contra PostgreSQL local con el esquema `qori` reconstruido: gasto válido, reintento con el mismo `request_id` (no duplica), conflicto 409, importe 0/negativo, fecha futura, fecha anterior a la apertura, categoría de ingreso, categoría y cuenta de otro usuario (rechazadas); saldos registrado/reservado/disponible y totales por moneda coinciden con `/cuentas`; perfil propio vs ajeno; recuperación con Supabase simulado. |
| Pruebas Android | Pendiente en emulador: compilar, recorrer las 4 pantallas y confirmar Registro/Login/Home/Logout siguen funcionando. |
| Pendientes / riesgos | 1) HU11 necesita que la plantilla **Reset Password** de Supabase incluya `{{ .Token }}`. 2) Supabase limita los correos por hora: probar HU11 con un solo intento. 3) HU12 guarda el idioma; traducir toda la app requiere acordar `strings.xml` (es/en) entre todos. 4) Un gasto futuro se rechaza igual que el ingreso de HU08; el informe propone dejarlo "Pendiente": decidir en equipo. |

## Reglas aplicadas

- **HU09:** importe > 0 con 2 decimales; fecha no futura ni anterior a la apertura de la cuenta; solo categorías de gasto activas (del sistema o propias); `request_id` evita duplicados. Si el gasto supera el disponible, Android pide confirmación.
- **HU10:** registrado (IND07) = inicial + ingresos − gastos ± transferencias, solo `CONFIRMED`; disponible (IND08) = registrado − reservado. Totales por moneda, sin mezclar PEN y USD.
- **HU11:** mismo mensaje exista o no el correo; código de Supabase + nueva contraseña (8+, un número y un símbolo); al terminar se cierra la sesión temporal.
- **HU12:** nombre (2–80), idioma `es`/`en`, moneda `PEN`/`USD`; el nombre también se actualiza en Supabase Auth para que el login lo muestre igual.

## Configuración de Supabase para HU11 (quien administre el proyecto)

Authentication → Emails → **Reset Password**, agregar al mensaje:

```
Tu código de QORI para restablecer la contraseña es: {{ .Token }}
```
