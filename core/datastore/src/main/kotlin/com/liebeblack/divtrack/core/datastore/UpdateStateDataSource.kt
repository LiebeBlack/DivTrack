package com.liebeblack.divtrack.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.liebeblack.divtrack.core.datastore.model.UpdateState
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Contrato del estado de actualizaciones. Interfaz, como el de preferencias, para que el
 * repositorio de `:data` se testee en JVM pura con un fake en memoria.
 */
interface UpdateStateDataSource {

    val state: Flow<UpdateState>

    /**
     * Guarda el resultado de una comprobación **exitosa**. `checkedAtMillis` lo pone quien
     * llama, que es quien tiene el `TimeProvider` inyectado.
     *
     * Si la versión publicada cambió respecto a la guardada, se borra la información de la
     * descarga anterior: el archivo que había en el dispositivo ya no es el vigente.
     */
    suspend fun saveCheck(
        latestVersion: String,
        latestTitle: String,
        latestPageUrl: String,
        latestDownloadUrl: String,
        latestSizeBytes: Long,
        latestPublishedAtMillis: Long,
        checkedAtMillis: Long,
    )

    /** Anota la descarga recién encolada en el gestor del sistema. */
    suspend fun saveDownload(downloadId: Long, fileName: String)

    /** Marca la descarga como terminada (o la desmarca si el archivo dejó de estar). */
    suspend fun markDownloadReady(ready: Boolean)

    /** Olvida la descarga (y con ella, el archivo que hubiera en el dispositivo). */
    suspend fun clearDownload()
}

@Singleton
class DataStoreUpdateStateDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UpdateStateDataSource {

    override val state: Flow<UpdateState> = dataStore.data
        // Un archivo ilegible no debe tumbar la pantalla de ajustes: se parte de vacío y se
        // reescribe en la siguiente comprobación (mismo criterio que las preferencias).
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> preferences.toUpdateState() }

    override suspend fun saveCheck(
        latestVersion: String,
        latestTitle: String,
        latestPageUrl: String,
        latestDownloadUrl: String,
        latestSizeBytes: Long,
        latestPublishedAtMillis: Long,
        checkedAtMillis: Long,
    ) {
        dataStore.edit { preferences ->
            val publishedVersionChanged = preferences[Keys.LATEST_VERSION] != latestVersion

            preferences[Keys.LATEST_VERSION] = latestVersion
            preferences[Keys.LATEST_TITLE] = latestTitle
            preferences[Keys.LATEST_PAGE_URL] = latestPageUrl
            preferences[Keys.LATEST_DOWNLOAD_URL] = latestDownloadUrl
            preferences[Keys.LATEST_SIZE_BYTES] = latestSizeBytes
            preferences[Keys.LATEST_PUBLISHED_AT] = latestPublishedAtMillis
            preferences[Keys.CHECKED_AT] = checkedAtMillis

            if (publishedVersionChanged) {
                preferences.remove(Keys.DOWNLOAD_ID)
                preferences.remove(Keys.DOWNLOAD_FILE_NAME)
                preferences.remove(Keys.DOWNLOAD_READY)
            }
        }
    }

    override suspend fun saveDownload(downloadId: Long, fileName: String) {
        dataStore.edit { preferences ->
            preferences[Keys.DOWNLOAD_ID] = downloadId
            preferences[Keys.DOWNLOAD_FILE_NAME] = fileName
            preferences[Keys.DOWNLOAD_READY] = false
        }
    }

    override suspend fun markDownloadReady(ready: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.DOWNLOAD_READY] = ready }
    }

    override suspend fun clearDownload() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.DOWNLOAD_ID)
            preferences.remove(Keys.DOWNLOAD_FILE_NAME)
            preferences.remove(Keys.DOWNLOAD_READY)
        }
    }

    private fun Preferences.toUpdateState(): UpdateState = UpdateState(
        latestVersion = this[Keys.LATEST_VERSION].orEmpty(),
        latestTitle = this[Keys.LATEST_TITLE].orEmpty(),
        latestPageUrl = this[Keys.LATEST_PAGE_URL].orEmpty(),
        latestDownloadUrl = this[Keys.LATEST_DOWNLOAD_URL].orEmpty(),
        latestSizeBytes = this[Keys.LATEST_SIZE_BYTES] ?: 0L,
        latestPublishedAtMillis = this[Keys.LATEST_PUBLISHED_AT] ?: 0L,
        checkedAtMillis = this[Keys.CHECKED_AT] ?: 0L,
        downloadId = this[Keys.DOWNLOAD_ID] ?: 0L,
        downloadFileName = this[Keys.DOWNLOAD_FILE_NAME].orEmpty(),
        downloadReady = this[Keys.DOWNLOAD_READY] ?: false,
    )

    private object Keys {
        val LATEST_VERSION = stringPreferencesKey("update_latest_version")
        val LATEST_TITLE = stringPreferencesKey("update_latest_title")
        val LATEST_PAGE_URL = stringPreferencesKey("update_latest_page_url")
        val LATEST_DOWNLOAD_URL = stringPreferencesKey("update_latest_download_url")
        val LATEST_SIZE_BYTES = longPreferencesKey("update_latest_size_bytes")
        val LATEST_PUBLISHED_AT = longPreferencesKey("update_latest_published_at")
        val CHECKED_AT = longPreferencesKey("update_checked_at")
        val DOWNLOAD_ID = longPreferencesKey("update_download_id")
        val DOWNLOAD_FILE_NAME = stringPreferencesKey("update_download_file_name")
        val DOWNLOAD_READY = booleanPreferencesKey("update_download_ready")
    }
}
