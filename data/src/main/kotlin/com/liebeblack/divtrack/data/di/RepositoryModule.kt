package com.liebeblack.divtrack.data.di

import com.liebeblack.divtrack.data.repository.RateRepositoryImpl
import com.liebeblack.divtrack.data.repository.SettingsRepositoryImpl
import com.liebeblack.divtrack.data.scheduler.WorkManagerSyncScheduler
import com.liebeblack.divtrack.domain.repository.RateRepository
import com.liebeblack.divtrack.domain.repository.SettingsRepository
import com.liebeblack.divtrack.domain.scheduler.SyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRateRepository(impl: RateRepositoryImpl): RateRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
