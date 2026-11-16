package io.github.mstajanc.gros.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Nastavenia appky v Preferences DataStore; nie sú súčasťou databázy ani zálohy. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    /** Účet, ktorý sa predvyplní pri novej transakcii. */
    val lastUsedAccountId: Flow<Long?> = dataStore.data.map { it[LAST_USED_ACCOUNT] }

    suspend fun getLastUsedAccountId(): Long? = lastUsedAccountId.first()

    suspend fun setLastUsedAccountId(accountId: Long) {
        dataStore.edit { it[LAST_USED_ACCOUNT] = accountId }
    }

    private companion object {
        val LAST_USED_ACCOUNT = longPreferencesKey("last_used_account_id")
    }
}
