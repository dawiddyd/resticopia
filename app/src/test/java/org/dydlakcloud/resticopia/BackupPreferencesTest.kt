package org.dydlakcloud.resticopia

import android.content.Context
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Focused tests for BackupPreferences.
 * 
 * Tests the preference storage layer that controls backup behavior:
 * - Cellular network allowance
 * - Charging requirements
 * - Default values (security-conscious defaults)
 * - Persistence across app restarts
 * 
 * These tests ensure the user's backup rules are stored and retrieved correctly,
 * which is critical for the cellular backup fix to work properly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU], manifest = Config.NONE)
class BackupPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Clean SharedPreferences for test isolation
        context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        // Clean up after test
        context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // ========== Cellular Network Preference Tests ==========

    @Test
    fun `allowsCellular defaults to false for security`() {
        // Given: Fresh preferences (no value set)
        // When: Checking cellular allowance
        val result = BackupPreferences.allowsCellular(context)
        
        // Then: Defaults to false (WiFi only - prevents unexpected data charges)
        assertThat(result).isFalse()
    }

    @Test
    fun `setAllowsCellular stores true correctly`() {
        // Given: Setting cellular to true
        BackupPreferences.setAllowsCellular(context, true)
        
        // When: Reading back
        val result = BackupPreferences.allowsCellular(context)
        
        // Then: Returns true
        assertThat(result).isTrue()
    }

    @Test
    fun `setAllowsCellular stores false correctly`() {
        // Given: Setting cellular to false
        BackupPreferences.setAllowsCellular(context, false)
        
        // When: Reading back
        val result = BackupPreferences.allowsCellular(context)
        
        // Then: Returns false
        assertThat(result).isFalse()
    }

    @Test
    fun `allowsCellular persists across reads`() {
        // Given: Setting cellular to true
        BackupPreferences.setAllowsCellular(context, true)
        
        // When: Reading multiple times
        val read1 = BackupPreferences.allowsCellular(context)
        val read2 = BackupPreferences.allowsCellular(context)
        val read3 = BackupPreferences.allowsCellular(context)
        
        // Then: All reads return the same value
        assertThat(read1).isTrue()
        assertThat(read2).isTrue()
        assertThat(read3).isTrue()
    }

    @Test
    fun `allowsCellular can toggle between true and false`() {
        // Given: Initial state is false
        assertThat(BackupPreferences.allowsCellular(context)).isFalse()
        
        // When: Setting to true
        BackupPreferences.setAllowsCellular(context, true)
        assertThat(BackupPreferences.allowsCellular(context)).isTrue()
        
        // And: Setting back to false
        BackupPreferences.setAllowsCellular(context, false)
        assertThat(BackupPreferences.allowsCellular(context)).isFalse()
        
        // And: Setting to true again
        BackupPreferences.setAllowsCellular(context, true)
        
        // Then: Final state is true
        assertThat(BackupPreferences.allowsCellular(context)).isTrue()
    }

    // ========== Charging Requirement Preference Tests ==========

    @Test
    fun `requiresCharging defaults to false`() {
        // Given: Fresh preferences (no value set)
        // When: Checking charging requirement
        val result = BackupPreferences.requiresCharging(context)
        
        // Then: Defaults to false (no charging required by default)
        assertThat(result).isFalse()
    }

    @Test
    fun `setRequiresCharging stores true correctly`() {
        // Given: Setting charging requirement to true
        BackupPreferences.setRequiresCharging(context, true)
        
        // When: Reading back
        val result = BackupPreferences.requiresCharging(context)
        
        // Then: Returns true
        assertThat(result).isTrue()
    }

    @Test
    fun `setRequiresCharging stores false correctly`() {
        // Given: Setting charging requirement to false
        BackupPreferences.setRequiresCharging(context, false)
        
        // When: Reading back
        val result = BackupPreferences.requiresCharging(context)
        
        // Then: Returns false
        assertThat(result).isFalse()
    }

    @Test
    fun `requiresCharging persists across reads`() {
        // Given: Setting charging requirement to true
        BackupPreferences.setRequiresCharging(context, true)
        
        // When: Reading multiple times
        val read1 = BackupPreferences.requiresCharging(context)
        val read2 = BackupPreferences.requiresCharging(context)
        val read3 = BackupPreferences.requiresCharging(context)
        
        // Then: All reads return the same value
        assertThat(read1).isTrue()
        assertThat(read2).isTrue()
        assertThat(read3).isTrue()
    }

    @Test
    fun `requiresCharging can toggle between true and false`() {
        // Given: Initial state is false
        assertThat(BackupPreferences.requiresCharging(context)).isFalse()
        
        // When: Setting to true
        BackupPreferences.setRequiresCharging(context, true)
        assertThat(BackupPreferences.requiresCharging(context)).isTrue()
        
        // And: Setting back to false
        BackupPreferences.setRequiresCharging(context, false)
        assertThat(BackupPreferences.requiresCharging(context)).isFalse()
        
        // And: Setting to true again
        BackupPreferences.setRequiresCharging(context, true)
        
        // Then: Final state is true
        assertThat(BackupPreferences.requiresCharging(context)).isTrue()
    }

    // ========== Independence Tests ==========

    @Test
    fun `cellular and charging preferences are independent`() {
        // Given: Setting cellular to true, charging to false
        BackupPreferences.setAllowsCellular(context, true)
        BackupPreferences.setRequiresCharging(context, false)
        
        // Then: Each returns its own value
        assertThat(BackupPreferences.allowsCellular(context)).isTrue()
        assertThat(BackupPreferences.requiresCharging(context)).isFalse()
        
        // When: Changing charging doesn't affect cellular
        BackupPreferences.setRequiresCharging(context, true)
        assertThat(BackupPreferences.allowsCellular(context)).isTrue()
        assertThat(BackupPreferences.requiresCharging(context)).isTrue()
        
        // When: Changing cellular doesn't affect charging
        BackupPreferences.setAllowsCellular(context, false)
        assertThat(BackupPreferences.allowsCellular(context)).isFalse()
        assertThat(BackupPreferences.requiresCharging(context)).isTrue()
    }

    @Test
    fun `preferences survive context recreation`() {
        // Given: Setting both preferences
        BackupPreferences.setAllowsCellular(context, true)
        BackupPreferences.setRequiresCharging(context, true)
        
        // When: Getting a new context (simulates app restart)
        val newContext = RuntimeEnvironment.getApplication()
        
        // Then: Preferences are still available
        assertThat(BackupPreferences.allowsCellular(newContext)).isTrue()
        assertThat(BackupPreferences.requiresCharging(newContext)).isTrue()
    }

    // ========== Security/Safety Tests ==========

    @Test
    fun `default values are conservative for user protection`() {
        // Given: Fresh preferences
        // Then: Defaults prevent unexpected behavior
        
        // WiFi only (prevents surprise data charges)
        assertThat(BackupPreferences.allowsCellular(context)).isFalse()
        
        // No charging requirement (allows backups anytime)
        assertThat(BackupPreferences.requiresCharging(context)).isFalse()
    }
}

