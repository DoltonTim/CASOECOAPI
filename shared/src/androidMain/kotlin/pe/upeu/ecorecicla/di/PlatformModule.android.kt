package pe.upeu.ecorecicla.di

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.AlmacenSeguroAndroid
import pe.upeu.ecorecicla.data.local.BaseLocalAndroid

/**
 * Modulo nativo de Android (Seccion 4.7):
 * - Motor HTTP OkHttp para Ktor.
 * - `AlmacenSeguroAndroid` con EncryptedSharedPreferences + AndroidKeyStore.
 * - `BaseLocalAndroid` con `AndroidSqliteDriver` para SQLDelight.
 */
actual val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    single<SqlDriver> { BaseLocalAndroid(androidContext()).crearDriver() }
}
