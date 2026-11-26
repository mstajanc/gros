package io.github.mstajanc.gros.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LockState {
    /** Nastavenie zámku sa ešte číta; nevykresľuje sa nič. */
    LOADING,

    /** Pred overením sa nevykreslí žiadny obsah appky. */
    LOCKED,
    UNLOCKED,
}

/**
 * Kedy je appka zamknutá: pri studenom štarte a po [TIMEOUT_MILLIS] na pozadí, ak je zámok zapnutý.
 * Systémový dialóg výberu súboru (záloha) sa za pobyt na pozadí nepočíta. Čistá logika bez Android API;
 * overenie (BiometricPrompt) robí rozhranie.
 */
class AppLock(private val nowMillis: () -> Long) {

    private val _state = MutableStateFlow(LockState.LOADING)
    val state: StateFlow<LockState> = _state

    private var enabled: Boolean? = null
    private var backgroundSince: Long? = null
    private var externalActivity = false

    /** Hodnota nastavenia z DataStore. Prvé načítanie zodpovedá studenému štartu. */
    fun onEnabledChanged(value: Boolean) {
        val first = enabled == null
        enabled = value
        when {
            first -> _state.value = if (value) LockState.LOCKED else LockState.UNLOCKED
            // Vypnutie zámku odomkne; zapnutie (po úspešnom overení, G5) appku nezamyká.
            !value -> _state.value = LockState.UNLOCKED
        }
    }

    fun onBackground() {
        if (!externalActivity) backgroundSince = nowMillis()
    }

    fun onForeground() {
        val since = backgroundSince
        backgroundSince = null
        if (externalActivity) {
            externalActivity = false
            return
        }
        if (enabled == true && since != null && nowMillis() - since >= TIMEOUT_MILLIS) {
            _state.value = LockState.LOCKED
        }
    }

    /** Volá sa tesne pred otvorením systémového výberu súboru (export a import zálohy). */
    fun beginExternalActivity() {
        externalActivity = true
    }

    fun unlock() {
        if (_state.value == LockState.LOCKED) _state.value = LockState.UNLOCKED
    }

    private val _missingCredential = MutableStateFlow(false)

    /** Zámok je zapnutý, ale zariadenie nemá zámok obrazovky: appka sa otvorila bez overenia. */
    val missingCredential: StateFlow<Boolean> = _missingCredential

    /** Ak používateľ zrušil zámok obrazovky zariadenia, appka sa otvorí s upozornením, aby sa nevymkol. */
    fun unlockWithoutCredential() {
        if (_state.value != LockState.LOCKED) return
        _missingCredential.value = true
        _state.value = LockState.UNLOCKED
    }

    fun dismissMissingCredential() {
        _missingCredential.value = false
    }

    companion object {
        const val TIMEOUT_MILLIS = 60_000L
    }
}

/** Pripomienka zálohy na Prehľade (G8): po 30 dňoch od poslednej zálohy, bez nej od prvého spustenia (B12, G7). */
object BackupReminder {
    const val DAYS = 30L

    fun daysSince(lastBackupEpochDay: Long?, firstLaunchEpochDay: Long?, todayEpochDay: Long): Long? =
        (lastBackupEpochDay ?: firstLaunchEpochDay)?.let { todayEpochDay - it }

    fun shouldShow(enabled: Boolean, lastBackupEpochDay: Long?, firstLaunchEpochDay: Long?, todayEpochDay: Long): Boolean {
        if (!enabled) return false
        val days = daysSince(lastBackupEpochDay, firstLaunchEpochDay, todayEpochDay) ?: return false
        return days >= DAYS
    }
}
