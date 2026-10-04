package com.liebeblack.divtrack.core.database.di

import android.content.Context
import androidx.room.Room
import com.liebeblack.divtrack.core.database.DatabaseRebuildTracker
import com.liebeblack.divtrack.core.database.DivTrackDatabase
import com.liebeblack.divtrack.core.database.RebuildingOpenHelperFactory
import com.liebeblack.divtrack.core.database.datasource.RateLocalDataSource
import com.liebeblack.divtrack.core.database.datasource.RoomRateLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * La base entra siempre por [RebuildingOpenHelperFactory]: un archivo dañado se recrea
     * (con aviso al usuario) en vez de tumbar la app al leer. El porqué, en el KDoc de la
     * fábrica y en la ADR 31.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        tracker: DatabaseRebuildTracker,
    ): DivTrackDatabase =
        Room.databaseBuilder(
            context,
            DivTrackDatabase::class.java,
            DivTrackDatabase.NAME,
        )
            .openHelperFactory(RebuildingOpenHelperFactory(context, tracker))
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindingsModule {

    @Binds
    @Singleton
    abstract fun bindRateLocalDataSource(
        impl: RoomRateLocalDataSource,
    ): RateLocalDataSource
}
