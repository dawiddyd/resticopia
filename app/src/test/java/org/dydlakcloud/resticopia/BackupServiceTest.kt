package org.dydlakcloud.resticopia

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowJobScheduler

/**
 * Unit tests for BackupService job scheduling and preferences.
 * 
 * These tests verify:
 * - Job scheduling configuration (network constraints, charging, periodic)
 * - BackupPreferences storage and retrieval
 * - Job rescheduling and updates
 * - No duplicate job creation
 * 
 * Note: For network-specific behavior (WiFi lock acquisition, network detection),
 * see BackupServiceNetworkTest which uses mocked system services.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU], manifest = Config.NONE)
class BackupServiceTest {

    private lateinit var context: Context
    private lateinit var jobScheduler: JobScheduler
    private lateinit var shadowJobScheduler: ShadowJobScheduler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        shadowJobScheduler = shadowOf(jobScheduler)
        
        // Clean SharedPreferences before each test for proper isolation
        context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        
        // Cancel all pending jobs for clean state
        jobScheduler.cancelAll()
    }
    
    @After
    fun tearDown() {
        // Clean up after each test
        jobScheduler.cancelAll()
        context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
    
    private fun getPendingJobs(): List<JobInfo> {
        // Use the JobScheduler's getAllPendingJobs() method which works with Robolectric
        return jobScheduler.allPendingJobs
    }

    @Test
    fun `schedule creates job with correct network constraint when cellular is allowed`() {
        // Given: Cellular network is allowed in preferences
        BackupPreferences.setAllowsCellular(context, true)
        
        // When: Scheduling backup job
        BackupService.reschedule(context)
        
        // Then: Job is scheduled with NETWORK_TYPE_ANY
        val pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        
        val job = pendingJobs[0]
        assertThat(job.networkType).isEqualTo(JobInfo.NETWORK_TYPE_ANY)
    }

    @Test
    fun `schedule creates job with WiFi only constraint when cellular is not allowed`() {
        // Given: Cellular network is NOT allowed (WiFi only)
        BackupPreferences.setAllowsCellular(context, false)
        
        // When: Scheduling backup job
        BackupService.reschedule(context)
        
        // Then: Job is scheduled with NETWORK_TYPE_UNMETERED (WiFi only)
        val pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        
        val job = pendingJobs[0]
        assertThat(job.networkType).isEqualTo(JobInfo.NETWORK_TYPE_UNMETERED)
    }

    @Test
    fun `reschedule cancels old job and creates new one`() {
        // Given: A job is already scheduled
        BackupPreferences.setAllowsCellular(context, false)
        BackupService.schedule(context)
        assertThat(getPendingJobs()).hasSize(1)
        
        // When: Rescheduling with different settings
        BackupPreferences.setAllowsCellular(context, true)
        BackupService.reschedule(context)
        
        // Then: Still only one job exists, but with new settings
        val pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        assertThat(pendingJobs[0].networkType).isEqualTo(JobInfo.NETWORK_TYPE_ANY)
    }

    @Test
    fun `schedule does not create duplicate jobs`() {
        // Given: Initial job schedule
        BackupService.schedule(context)
        assertThat(getPendingJobs()).hasSize(1)
        
        // When: Scheduling again
        BackupService.schedule(context)
        
        // Then: Still only one job exists (no duplicate)
        assertThat(getPendingJobs()).hasSize(1)
    }

    @Test
    fun `job is created as periodic with correct interval`() {
        // When: Scheduling backup job
        BackupService.schedule(context)
        
        // Then: Job is periodic with 1 hour interval
        val pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        
        val job = pendingJobs[0]
        assertThat(job.isPeriodic).isTrue()
        assertThat(job.intervalMillis).isEqualTo(60 * 60 * 1000L) // 1 hour
    }

    @Test
    fun `job is created with persist flag`() {
        // When: Scheduling backup job
        BackupService.schedule(context)
        
        // Then: Job is set to persist across device reboots
        val pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        
        val job = pendingJobs[0]
        assertThat(job.isPersisted).isTrue()
    }

    @Test
    fun `job applies charging constraint when required`() {
        // Given: Charging is required for backups
        BackupPreferences.setRequiresCharging(context, true)
        
        // When: Scheduling backup job
        BackupService.reschedule(context)
        
        // Then: Job requires charging
        val pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        
        val job = pendingJobs[0]
        assertThat(job.isRequireCharging).isTrue()
    }

    @Test
    fun `job does not require charging when not configured`() {
        // Given: Charging is NOT required
        BackupPreferences.setRequiresCharging(context, false)
        
        // When: Scheduling backup job
        BackupService.reschedule(context)
        
        // Then: Job does not require charging
        val pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        
        val job = pendingJobs[0]
        assertThat(job.isRequireCharging).isFalse()
    }

    @Test
    fun `job uses correct service component`() {
        // When: Scheduling backup job
        BackupService.schedule(context)
        
        // Then: Job is configured for BackupService
        val pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        
        val job = pendingJobs[0]
        val expectedComponent = ComponentName(context, BackupService::class.java)
        assertThat(job.service).isEqualTo(expectedComponent)
    }

    @Test
    fun `changing cellular preference updates job constraints`() {
        // Given: Initial setup with WiFi only
        BackupPreferences.setAllowsCellular(context, false)
        BackupService.schedule(context)
        var pendingJobs = getPendingJobs()
        assertThat(pendingJobs[0].networkType).isEqualTo(JobInfo.NETWORK_TYPE_UNMETERED)
        
        // When: User enables cellular backups
        BackupPreferences.setAllowsCellular(context, true)
        BackupService.reschedule(context)
        
        // Then: Job is rescheduled with cellular allowed
        pendingJobs = getPendingJobs()
        assertThat(pendingJobs).hasSize(1)
        assertThat(pendingJobs[0].networkType).isEqualTo(JobInfo.NETWORK_TYPE_ANY)
    }

    @Test
    fun `BackupPreferences correctly stores and retrieves cellular setting`() {
        // Given: Fresh context
        // When: Setting cellular to true
        BackupPreferences.setAllowsCellular(context, true)
        
        // Then: Retrieves true
        assertThat(BackupPreferences.allowsCellular(context)).isTrue()
        
        // When: Setting cellular to false
        BackupPreferences.setAllowsCellular(context, false)
        
        // Then: Retrieves false
        assertThat(BackupPreferences.allowsCellular(context)).isFalse()
    }

    @Test
    fun `BackupPreferences correctly stores and retrieves charging setting`() {
        // Given: Fresh context
        // When: Setting requiring charging to true
        BackupPreferences.setRequiresCharging(context, true)
        
        // Then: Retrieves true
        assertThat(BackupPreferences.requiresCharging(context)).isTrue()
        
        // When: Setting requiring charging to false
        BackupPreferences.setRequiresCharging(context, false)
        
        // Then: Retrieves false
        assertThat(BackupPreferences.requiresCharging(context)).isFalse()
    }

    @Test
    fun `BackupPreferences defaults to false for cellular`() {
        // Given: Clean SharedPreferences (setUp already cleared it)
        // When: Checking default value
        val allowsCellular = BackupPreferences.allowsCellular(context)
        
        // Then: Defaults to false (WiFi only - secure default)
        assertThat(allowsCellular).isFalse()
    }

    @Test
    fun `BackupPreferences defaults to false for charging`() {
        // Given: Clean SharedPreferences (setUp already cleared it)
        // When: Checking default value
        val requiresCharging = BackupPreferences.requiresCharging(context)
        
        // Then: Defaults to false (no charging required by default)
        assertThat(requiresCharging).isFalse()
    }
    
    @Test
    fun `job configuration is preserved across reschedule`() {
        // Given: Initial job with specific settings
        BackupPreferences.setAllowsCellular(context, true)
        BackupPreferences.setRequiresCharging(context, true)
        BackupService.schedule(context)
        
        val initialJobs = getPendingJobs()
        assertThat(initialJobs).hasSize(1)
        val initialJob = initialJobs[0]
        
        // When: Rescheduling without changing preferences
        BackupService.reschedule(context)
        
        // Then: Job maintains the same configuration
        val rescheduledJobs = getPendingJobs()
        assertThat(rescheduledJobs).hasSize(1)
        val rescheduledJob = rescheduledJobs[0]
        
        assertThat(rescheduledJob.networkType).isEqualTo(initialJob.networkType)
        assertThat(rescheduledJob.isRequireCharging).isEqualTo(initialJob.isRequireCharging)
        assertThat(rescheduledJob.isPeriodic).isEqualTo(initialJob.isPeriodic)
        assertThat(rescheduledJob.isPersisted).isEqualTo(initialJob.isPersisted)
    }
}
