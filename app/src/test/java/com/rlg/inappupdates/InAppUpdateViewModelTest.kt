package com.rlg.inappupdates

import UpdateUIState
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import de.palm.composestateevents.StateEvent
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en", sdk = [Build.VERSION_CODES.P])
class InAppUpdateViewModelTest {

    private lateinit var fakeAppUpdateManager: FakeAppUpdateManager
    private lateinit var promptTimestampDataStore: PromptTimestampDataStore
    private lateinit var clockUtil: ClockUtil
    private lateinit var viewModel: InAppUpdateViewModel
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        val mockApplicationContext: Context = mockk(relaxed = true) // Mock application context
        every { mockApplicationContext.packageName } returns "com.example.test"
        every { mockApplicationContext.applicationContext } returns mockApplicationContext

        fakeAppUpdateManager = FakeAppUpdateManager(mockApplicationContext)
        promptTimestampDataStore = mockk(relaxed = true)
        clockUtil = mockk { every { getCurrentTimeMillis() } returns 1725337204000 }
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)

        val inAppUpdateRepo =
            InAppUpdateRepoImpl(fakeAppUpdateManager, promptTimestampDataStore, testDispatcher, clockUtil)
        viewModel = InAppUpdateViewModel(inAppUpdateRepo, clockUtil, testDispatcher)
    }

    @Test
    fun `flexible update lifecycle simulation`() =
        testScope.runTest {
            fakeAppUpdateManager.setUpdateAvailable(2, AppUpdateType.FLEXIBLE)

            val emittedStates = mutableListOf<UpdateUIState>()
            val collectJob = launch(testDispatcher) { viewModel.uiState.toList(emittedStates) }

            // Check for updates
            viewModel.checkForUpdates()
            advanceUntilIdle()

            assertEquals(UpdateStatus.FLEXIBLE_REQUIRED, emittedStates.last().updateStatus)

            // Trigger flexible update
            val mockLauncher = mockk<ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
            viewModel.triggerFlexibleUpdate(mockLauncher)
            advanceUntilIdle()
            assertTrue(fakeAppUpdateManager.isConfirmationDialogVisible)
            fakeAppUpdateManager.userAcceptsUpdate()
            assertFalse(fakeAppUpdateManager.isConfirmationDialogVisible)
            fakeAppUpdateManager.downloadStarts()
            fakeAppUpdateManager.downloadCompletes()
            advanceUntilIdle()

            assertEquals(StateEvent.Triggered, emittedStates.last().showInstallPrompt)
            // Simulate consuming install prompt and completing update
            viewModel.onShowInstallPromptConsumed()
            advanceUntilIdle()
            assertEquals(StateEvent.Consumed, emittedStates.last().showInstallPrompt)

            viewModel.installDownloadedUpdate()
            fakeAppUpdateManager.installCompletes()

            collectJob.cancel()
        }

    @Test
    fun `flexible update lifecycle where user resumes from background after download`() =
        testScope.runTest {
            // Arrange: Set up a flexible update
            fakeAppUpdateManager.setUpdateAvailable(2, AppUpdateType.FLEXIBLE)

            val emittedStates = mutableListOf<UpdateUIState>()
            val collectJob = launch(testDispatcher) { viewModel.uiState.toList(emittedStates) }

            // Act 1: Start flexible update
            viewModel.checkForUpdates()
            advanceUntilIdle()
            assertEquals(UpdateStatus.FLEXIBLE_REQUIRED, emittedStates.last().updateStatus)

            val mockLauncher = mockk<ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
            viewModel.triggerFlexibleUpdate(mockLauncher)
            advanceUntilIdle()
            assertTrue(fakeAppUpdateManager.isConfirmationDialogVisible)

            fakeAppUpdateManager.userAcceptsUpdate()
            assertFalse(fakeAppUpdateManager.isConfirmationDialogVisible)
            fakeAppUpdateManager.downloadStarts()

            // Simulate app restart or system recheck
            fakeAppUpdateManager
                .downloadCompletes() // Simulate download while user is in background state
            viewModel.checkForUpdates() // User resumes app
            advanceUntilIdle()

            // Assert: Verify the system acknowledges the partial update.
            // Note: although this is what the unit test gives, and I expect, in reality on physical
            // device, I saw a different result
            assertEquals(
                UpdateStatus.DOWNLOAD_TRIGGERED_INCOMPLETE(
                    isDownloaded = true,
                    isActivelyDownloading = false,
                ),
                emittedStates.last().updateStatus,
            )

            // Act 2: Resume and complete the download
            advanceUntilIdle()
            assertEquals(StateEvent.Triggered, emittedStates.last().showInstallPrompt)

            viewModel.onShowInstallPromptConsumed()
            advanceUntilIdle()
            assertEquals(StateEvent.Consumed, emittedStates.last().showInstallPrompt)

            // Complete the installation
            viewModel.installDownloadedUpdate()
            fakeAppUpdateManager.installCompletes()

            // Assert: Verify final state after installation
            assertFalse(fakeAppUpdateManager.isInstallSplashScreenVisible)

            collectJob.cancel()
        }

    @Test
    fun `flexible update lifecycle with download failure`() =
        testScope.runTest {
            // Arrange: Set up a flexible update
            fakeAppUpdateManager.setUpdateAvailable(2, AppUpdateType.FLEXIBLE)

            val emittedStates = mutableListOf<UpdateUIState>()
            val collectJob = launch(testDispatcher) { viewModel.uiState.toList(emittedStates) }

            // Act 1: Start flexible update
            viewModel.checkForUpdates()
            advanceUntilIdle()
            assertEquals(UpdateStatus.FLEXIBLE_REQUIRED, emittedStates.last().updateStatus)

            val mockLauncher = mockk<ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
            viewModel.triggerFlexibleUpdate(mockLauncher)
            advanceUntilIdle()
            assertTrue(fakeAppUpdateManager.isConfirmationDialogVisible)

            fakeAppUpdateManager.userAcceptsUpdate()
            assertFalse(fakeAppUpdateManager.isConfirmationDialogVisible)
            fakeAppUpdateManager.downloadStarts()

            // Simulate download failure
            fakeAppUpdateManager.downloadFails()
            advanceUntilIdle()

            // Assert: Verify the ViewModel updates its state to indicate failure
            assertEquals(UpdateStatus.FAILED, emittedStates.last().updateStatus)

            // Act 2: Reattempt update
            fakeAppUpdateManager.setUpdateAvailable(2, AppUpdateType.FLEXIBLE)
            viewModel.checkForUpdates()
            advanceUntilIdle()
            assertEquals(UpdateStatus.FLEXIBLE_REQUIRED, emittedStates.last().updateStatus)

            collectJob.cancel()
        }

    @Test
    fun `immediate update lifecycle simulation`() =
        testScope.runTest {
            // Arrange: Set up update with sufficient priority and staleness to trigger immediate
            // update
            fakeAppUpdateManager.setUpdateAvailable(2, AppUpdateType.IMMEDIATE)
            fakeAppUpdateManager.setUpdatePriority(5) // Matches PRIORITY_THRESHOLD
            fakeAppUpdateManager.setClientVersionStalenessDays(
                10
            ) // Exceeds STALENESS_THRESHOLD_DAYS

            val emittedStates = mutableListOf<UpdateUIState>()
            val collectJob = launch(testDispatcher) { viewModel.uiState.toList(emittedStates) }

            // Act: Check for updates
            viewModel.checkForUpdates()
            advanceUntilIdle()

            // Assert: Validate immediate update is required
            assertEquals(UpdateStatus.IMMEDIATE_REQUIRED, emittedStates.last().updateStatus)

            // Trigger immediate update
            val mockLauncher = mockk<ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
            assertFalse(fakeAppUpdateManager.isImmediateFlowVisible)
            viewModel.triggerImmediateUpdate(mockLauncher)
            advanceUntilIdle()
            assertTrue(fakeAppUpdateManager.isImmediateFlowVisible)

            fakeAppUpdateManager.userAcceptsUpdate()
            fakeAppUpdateManager.downloadStarts()
            fakeAppUpdateManager.downloadCompletes()
            fakeAppUpdateManager.installCompletes()
            assertFalse(fakeAppUpdateManager.isImmediateFlowVisible)

            collectJob.cancel()
        }
}
