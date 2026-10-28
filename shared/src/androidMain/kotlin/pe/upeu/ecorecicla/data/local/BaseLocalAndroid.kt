package pe.upeu.ecorecicla.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.upeu.ecorecicla.db.EcoReciclaDb

/**
 * Fabrica del driver nativo SQLDelight para Android (`AndroidSqliteDriver`) (Seccion 4.7).
 */
class BaseLocalAndroid(private val contexto: Context) {
    fun crearDriver(): SqlDriver =
        AndroidSqliteDriver(EcoReciclaDb.Schema, contexto, "ecorecicla.db")
}
