package io.github.mstajanc.gros.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /** Veľkosť písma v appke; neznáma uložená hodnota padne na štandardnú. */
    val textSize: Flow<TextSize> = dataStore.data.map { prefs ->
        TextSize.entries.firstOrNull { it.name == prefs[TEXT_SIZE] } ?: TextSize.STANDARD
    }

    suspend fun setTextSize(size: TextSize) {
        dataStore.edit { it[TEXT_SIZE] = size.name }
    }

    /** Téma appky (H1); neznáma uložená hodnota padne na predvolenú. */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE] } ?: ThemeMode.DEFAULT
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    /** Po importe zálohy sa posledný použitý účet vynuluje (import, krok 4). */
    suspend fun clearLastUsedAccountId() {
        dataStore.edit { it.remove(LAST_USED_ACCOUNT) }
    }

    /** Zámok appky (biometria alebo PIN zariadenia). */
    val lockEnabled: Flow<Boolean> = dataStore.data.map { it[LOCK_ENABLED] ?: false }

    suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit { it[LOCK_ENABLED] = enabled }
    }

    /** Deň poslednej úspešnej zálohy (epoch day) alebo `null`. */
    val lastBackupEpochDay: Flow<Long?> = dataStore.data.map { it[LAST_BACKUP] }

    suspend fun setLastBackupEpochDay(epochDay: Long) {
        dataStore.edit { it[LAST_BACKUP] = epochDay }
    }

    /** Deň prvého spustenia verzie so zálohou; od neho sa ráta pripomienka bez zálohy (B12, G7). */
    val firstLaunchEpochDay: Flow<Long?> = dataStore.data.map { it[FIRST_LAUNCH] }

    suspend fun recordFirstLaunch(epochDay: Long) {
        dataStore.edit { if (it[FIRST_LAUNCH] == null) it[FIRST_LAUNCH] = epochDay }
    }

    /** Pruh „Zálohovať“ na Prehľade sa dá vypnúť. */
    val backupReminderEnabled: Flow<Boolean> = dataStore.data.map { it[BACKUP_REMINDER] ?: true }

    suspend fun setBackupReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[BACKUP_REMINDER] = enabled }
    }

    private companion object {
        val LAST_USED_ACCOUNT = longPreferencesKey("last_used_account_id")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val LAST_BACKUP = longPreferencesKey("last_backup_epoch_day")
        val FIRST_LAUNCH = longPreferencesKey("first_launch_epoch_day")
        val BACKUP_REMINDER = booleanPreferencesKey("backup_reminder_enabled")
    }
}
