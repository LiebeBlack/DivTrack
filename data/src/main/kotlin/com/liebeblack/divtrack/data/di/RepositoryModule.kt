package com.liebeblack.divtrack.data.di

import com.liebeblack.divtrack.data.downloader.DownloadManagerAppDownloader
import com.liebeblack.divtrack.data.platform.PackageManagerInstalledAppVersion
import com.liebeblack.divtrack.data.repository.RateRepositoryImpl
import com.liebeblack.divtrack.data.repository.SettingsRepositoryImpl
import com.liebeblack.divtrack.data.repository.UpdateRepositoryImpl
import com.liebeblack.divtrack.data.scheduler.WorkManagerSyncScheduler
import com.liebeblack.divtrack.data.scheduler.WorkManagerUpdateCheckScheduler
import com.liebeblack.divtrack.domain.repository.AppDownloader
import com.liebeblack.divtrack.domain.repository.InstalledAppVersionProvider
import com.liebeblack.divtrack.domain.repository.RateRepository
import com.liebeblack.divtrack.domain.repository.SettingsRepository
import com.liebeblack.divtrack.domain.repository.UpdateRepository
import com.liebeblack.divtrack.domain.scheduler.SyncScheduler
import com.liebeblack.divtrack.domain.scheduler.UpdateCheckScheduler
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

    // --- Módulo de actualizaciones (Sistema de Actualización Inteligente) ---

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: UpdateRepositoryImpl): UpdateRepository

    @Binds
    @Singleton
    abstract fun bindUpdateCheckScheduler(
        impl: WorkManagerUpdateCheckScheduler,
    ): UpdateCheckScheduler

    @Binds
    @Singleton
    abstract fun bindAppDownloader(impl: DownloadManagerAppDownloader): AppDownloader

    @Binds
    @Singleton
    abstract fun bindInstalledAppVersion(
        impl: PackageManagerInstalledAppVersion,
    ): InstalledAppVersionProvider
}
