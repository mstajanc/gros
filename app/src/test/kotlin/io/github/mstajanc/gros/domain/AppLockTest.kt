package io.github.mstajanc.gros.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AppLockTest {

    private var now = 1_000_000L
    private val lock = AppLock { now }

    @Test
    fun `nothing is shown until the setting is read`() {
        assertEquals(LockState.LOADING, lock.state.value)
    }

    @Test
    fun `cold start is locked only when the lock is enabled`() {
        lock.onEnabledChanged(true)
        assertEquals(LockState.LOCKED, lock.state.value)

        val other = AppLock { now }
        other.onEnabledChanged(false)
        assertEquals(LockState.UNLOCKED, other.state.value)
    }

    @Test
    fun `locks after 60 seconds in background but not sooner`() {
        lock.onEnabledChanged(true)
        lock.unlock()

        lock.onBackground()
        now += 59_999
        lock.onForeground()
        assertEquals(LockState.UNLOCKED, lock.state.value)

        lock.onBackground()
        now += 60_000
        lock.onForeground()
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `file picker does not count as background`() {
        lock.onEnabledChanged(true)
        lock.unlock()

        lock.beginExternalActivity()
        lock.onBackground()
        now += 5 * 60_000
        lock.onForeground()
        assertEquals(LockState.UNLOCKED, lock.state.value)

        // Ďalší pobyt na pozadí sa už počíta normálne.
        lock.onBackground()
        now += 60_000
        lock.onForeground()
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `enabling does not lock and disabling unlocks`() {
        lock.onEnabledChanged(false)
        lock.onEnabledChanged(true)
        assertEquals(LockState.UNLOCKED, lock.state.value)

        lock.onBackground()
        now += 60_000
        lock.onForeground()
        assertEquals(LockState.LOCKED, lock.state.value)
        lock.onEnabledChanged(false)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `disabled lock never locks`() {
        lock.onEnabledChanged(false)
        lock.onBackground()
        now += 10 * 60_000
        lock.onForeground()
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `backup reminder after 30 days, counted from first launch without any backup`() {
        assertFalse(BackupReminder.shouldShow(true, lastBackupEpochDay = 100, firstLaunchEpochDay = 1, todayEpochDay = 129))
        assertTrue(BackupReminder.shouldShow(true, lastBackupEpochDay = 100, firstLaunchEpochDay = 1, todayEpochDay = 130))
        assertFalse(BackupReminder.shouldShow(true, lastBackupEpochDay = null, firstLaunchEpochDay = 100, todayEpochDay = 129))
        assertTrue(BackupReminder.shouldShow(true, lastBackupEpochDay = null, firstLaunchEpochDay = 100, todayEpochDay = 130))
        assertFalse(BackupReminder.shouldShow(false, lastBackupEpochDay = null, firstLaunchEpochDay = 1, todayEpochDay = 500))
        assertEquals(45, BackupReminder.daysSince(lastBackupEpochDay = 55, firstLaunchEpochDay = 1, todayEpochDay = 100))
    }
}
