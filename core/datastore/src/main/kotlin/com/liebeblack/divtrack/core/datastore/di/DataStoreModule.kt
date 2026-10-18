package com.liebeblack.divtrack.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.liebeblack.divtrack.core.datastore.DataStoreUserPreferencesDataSource
import com.liebeblack.divtrack.core.datastore.UserPreferencesDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val PREFERENCES_FILE_NAME = "divtrack_preferences"

    /**
     * Una sola instancia de DataStore por proceso: si se creara más de una sobre el mismo
     * archivo, DataStore lanza `IllegalStateException` (y con razón). Se provee por DI en
     * vez de usar el delegado `by preferencesDataStore` para que el grafo lo controle.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreBindingsModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferencesDataSource(
        impl: DataStoreUserPreferencesDataSource,
    ): UserPreferencesDataSource
}
