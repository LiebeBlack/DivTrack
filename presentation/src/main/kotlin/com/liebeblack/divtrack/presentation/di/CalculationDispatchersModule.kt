package com.liebeblack.divtrack.presentation.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatcher de la aritmética de la calculadora.
 *
 * Se inyecta en lugar de usar `Dispatchers.Default` directamente para que los tests puedan
 * sustituirlo por el scheduler virtual de `kotlinx-coroutines-test`: un dispatcher real
 * haría que `advanceUntilIdle()` no controlara el cálculo y los tests dependerían del reloj
 * de la máquina.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CalculatorDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CalculationDispatchersModule {

    @Provides
    @Singleton
    @CalculatorDispatcher
    fun provideCalculatorDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
