package com.rlg.inappupdates

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en", sdk = [Build.VERSION_CODES.P])
class InAppUpdateRepoImplTest {

    private lateinit var context: Context
    private lateinit var fakeAppUpdateManager: FakeAppUpdateManager
    private lateinit var repository: InAppUpdateRepoImpl
    private lateinit var mockPromptTimestampDataStore: PromptTimestampDataStore
    private lateinit var testDispatcher: CoroutineDispatcher

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        fakeAppUpdateManager = FakeAppUpdateManager(context)
        mockPromptTimestampDataStore = mockk()
        testDispatcher = StandardTestDispatcher()
        repository =
            InAppUpdateRepoImpl(
                appUpdateManager = fakeAppUpdateManager,
                promptDataStore = mockPromptTimestampDataStore,
                testDispatcher,
            )
    }

    @Test
    fun `isFlexibleUpdatePromptCooldownExpired returns true when cooldown has expired`() =
        runBlocking {
            // Arrange
            val expiredTimestamp =
                System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000) // 2 days ago
            coEvery { mockPromptTimestampDataStore.getLastPromptTimestamp() } returns
                flowOf(expiredTimestamp)

            // Act
            val isExpired = repository.isFlexibleUpdatePromptCooldownExpired()

            // Assert
            assertTrue(isExpired)
        }

    @Test
    fun `isFlexibleUpdatePromptCooldownExpired returns false when cooldown has not expired`() =
        runTest {
            // Arrange
            val recentTimestamp = System.currentTimeMillis() - (12 * 60 * 60 * 1000) // 12 hours ago
            coEvery { mockPromptTimestampDataStore.getLastPromptTimestamp() } returns
                flowOf(recentTimestamp)

            // Act
            val isExpired = repository.isFlexibleUpdatePromptCooldownExpired()

            // Assert
            assertFalse(isExpired)
        }

    @Test
    fun `registerUpdateListener triggers installPromptFlow when download completes`() = runTest {
        // Arrange
        fakeAppUpdateManager.setUpdateAvailable(2)
        val mockLauncher = mockk<ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
        coEvery { mockPromptTimestampDataStore.setLastPromptTimestamp(any()) } returns Unit
        repository.triggerFlexibleUpdate(mockLauncher)

        // Act
        fakeAppUpdateManager.userAcceptsUpdate()
        fakeAppUpdateManager.downloadStarts()
        fakeAppUpdateManager.downloadCompletes()

        // Allow state propagation
        delay(1000)

        // Assert
        assertEquals(InAppInstallState.DOWNLOADED, repository.installStateFlow.value)
    }

    @Test
    fun `triggerImmediateUpdate starts immediate update flow`() = runTest {
        // Arrange
        fakeAppUpdateManager.setUpdateAvailable(2)
        val mockLauncher = mockk<ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
        assertFalse(fakeAppUpdateManager.isImmediateFlowVisible())
        // Act
        repository.triggerImmediateUpdate(mockLauncher)

        // Assert
        assertTrue(fakeAppUpdateManager.isImmediateFlowVisible())
    }
}
