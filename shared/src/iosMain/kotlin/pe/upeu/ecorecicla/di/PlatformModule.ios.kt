package pe.upeu.ecorecicla.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.db.EcoReciclaDb
import pe.upeu.ecorecicla.domain.model.Sesion
import platform.Foundation.NSUserDefaults

class AlmacenSeguroIos : AlmacenSeguro {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun guardarToken(token: String) { defaults.setObject(token, "jwt_token") }
    override fun leerToken(): String? = defaults.stringForKey("jwt_token")
    override fun borrarToken() { defaults.removeObjectForKey("jwt_token") }
    override fun guardarSesionCache(sesion: Sesion) { guardarToken(sesion.token) }
    override fun leerSesionCache(): Sesion? = null
}

actual val platformModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single<AlmacenSeguro> { AlmacenSeguroIos() }
    single<SqlDriver> { NativeSqliteDriver(EcoReciclaDb.Schema, "ecorecicla.db") }
}
