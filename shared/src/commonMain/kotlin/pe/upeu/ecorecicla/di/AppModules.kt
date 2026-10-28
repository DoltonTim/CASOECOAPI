package pe.upeu.ecorecicla.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.remote.EcoApiService
import pe.upeu.ecorecicla.data.remote.crearHttpClient
import pe.upeu.ecorecicla.data.repository.AuthRepositoryImpl
import pe.upeu.ecorecicla.data.repository.EcoRepositoryOfflineImpl
import pe.upeu.ecorecicla.db.EcoReciclaDb
import pe.upeu.ecorecicla.domain.repository.AuthRepository
import pe.upeu.ecorecicla.domain.repository.EcoRepository
import pe.upeu.ecorecicla.domain.usecase.AnularPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.CerrarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.CerrarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.CorregirPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.IniciarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesPuntoUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.RecuperarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import pe.upeu.ecorecicla.presentation.detalle.DetallePuntoViewModel
import pe.upeu.ecorecicla.presentation.listado.ListadoRutaViewModel
import pe.upeu.ecorecicla.presentation.sesion.SesionViewModel

/**
 * ============================================================================
 * MODULOS DE INYECCION DE DEPENDENCIAS CON KOIN (SECCION 4.7)
 * ============================================================================
 * Declara explicitamente los 5 modulos esperados:
 * - [redModule]: unica instancia de `HttpClient` y `EcoApiService`.
 * - [dataModule]: `EcoReciclaDb`, `BaseLocal` e implementaciones offline-first de repositorios.
 * - [domainModule]: todos los casos de uso (`SincronizarUseCase`, `ObservarRutaUseCase`, etc.).
 * - [presentationModule]: `SesionViewModel`, `ListadoRutaViewModel` y `DetallePuntoViewModel`.
 * - [platformModule]: dependencias nativas (`AlmacenSeguroAndroid`, `BaseLocalAndroid`, `OkHttp`).
 */

val redModule = module {
    single<HttpClient> {
        crearHttpClient(
            motor = get<HttpClientEngine>(),
            almacenSeguro = get<AlmacenSeguro>(),
            esDepuracion = true
        )
    }
    single { EcoApiService(get()) }
}

val dataModule = module {
    single { EcoReciclaDb(get()) }
    single { BaseLocal(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<EcoRepository> { EcoRepositoryOfflineImpl(get(), get(), get()) }
}

val domainModule = module {
    factory { IniciarSesionUseCase(get()) }
    factory { RecuperarSesionUseCase(get()) }
    factory { CerrarSesionUseCase(get()) }
    factory { ObservarRutaUseCase(get()) }
    factory { ObservarPesajesPuntoUseCase(get()) }
    factory { RegistrarPesajeUseCase(get()) }
    factory { CorregirPesajeUseCase(get()) }
    factory { AnularPesajeUseCase(get()) }
    factory { CerrarRutaUseCase(get()) }
    factory { SincronizarUseCase(get()) }
}

val presentationModule = module {
    viewModel { SesionViewModel(get(), get(), get()) }
    viewModel { ListadoRutaViewModel(get(), get(), get()) }
    viewModel { DetallePuntoViewModel(get(), get(), get(), get()) }
}

expect val platformModule: Module

fun initKoin(configuracionAdicional: KoinApplication.() -> Unit = {}) {
    startKoin {
        configuracionAdicional()
        modules(
            platformModule,
            redModule,
            dataModule,
            domainModule,
            presentationModule
        )
    }
}
