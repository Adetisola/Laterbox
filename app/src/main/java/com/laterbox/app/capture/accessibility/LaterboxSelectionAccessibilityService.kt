package com.laterbox.app.capture.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.capture.CaptureSource
import com.laterbox.app.capture.UrlCaptureExtractor
import com.laterbox.app.capture.UrlExtractionResult
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.quicksave.QuickSaveActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Optional accessibility service that detects text-selection events containing web links
 * and presents an unobtrusive floating shortcut pill to save the link directly into Laterbox.
 */
class LaterboxSelectionAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlayController: FloatingCaptureOverlayController
    private val deduplicator = SelectionPromptDeduplicator()

    private var linkRepository: LinkRepository? = null
    private var userPreferencesRepository: UserPreferencesRepository? = null

    private var isFeatureEnabled = false
    private var excludedPackages = emptySet<String>()
    private var overlayXRatio = 0.5f
    private var overlayYRatio = 0.82f
    private var debounceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayController = FloatingCaptureOverlayController(this, windowManager)

        try {
            val app = application as LaterboxApplication
            linkRepository = app.container.linkRepository
            userPreferencesRepository = app.container.userPreferencesRepository

            serviceScope.launch {
                userPreferencesRepository?.userPreferences?.collectLatest { prefs ->
                    isFeatureEnabled = prefs.accessibilityCaptureEnabled
                    excludedPackages = prefs.excludedCapturePackages
                    overlayXRatio = prefs.overlayXRatio
                    overlayYRatio = prefs.overlayYRatio

                    if (!isFeatureEnabled && overlayController.isShowing) {
                        overlayController.dismissImmediately()
                        deduplicator.clear()
                    }
                }
            }
        } catch (_: Exception) {
            // Safeguard against missing container in test environments
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isFeatureEnabled || event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val newPackage = event.packageName?.toString()
                if (deduplicator.shouldDismissOnWindowStateChange(newPackage, packageName)) {
                    overlayController.dismissImmediately()
                    deduplicator.clear()
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val outcome = SelectionTextExtractor.inspectEvent(
                    event = event,
                    ownPackageName = packageName,
                    excludedPackages = excludedPackages
                )

                when (outcome) {
                    is SelectionEventOutcome.SelectionCleared -> {
                        // Cancel pending candidate debounce, but do NOT prematurely dismiss
                        // an already visible capture prompt (e.g. when native action mode or focus shifts)
                        debounceJob?.cancel()
                    }

                    is SelectionEventOutcome.CandidateText -> {
                        debounceJob?.cancel()
                        debounceJob = serviceScope.launch {
                            delay(250) // Debounce rapid cursor movements
                            handleCandidateText(outcome)
                        }
                    }

                    is SelectionEventOutcome.Ignored -> {
                        // Do nothing
                    }
                }
            }
        }
    }

    private suspend fun handleCandidateText(candidate: SelectionEventOutcome.CandidateText) {
        val extraction = UrlCaptureExtractor.extract(candidate.text)
        if (extraction !is UrlExtractionResult.SingleUrl) return

        if (!deduplicator.shouldShowPrompt(extraction.normalizedUrl, candidate.packageName)) {
            return
        }

        // Avoid prompting for links that are already saved
        val existing = linkRepository?.findDuplicate(extraction.normalizedUrl)
        if (existing != null) return

        overlayController.showPrompt(
            domain = extraction.domain,
            url = extraction.normalizedUrl,
            boundsInScreen = candidate.boundsInScreen,
            initialXRatio = overlayXRatio,
            initialYRatio = overlayYRatio,
            onPositionChanged = { newX, newY ->
                overlayXRatio = newX
                overlayYRatio = newY
                serviceScope.launch {
                    userPreferencesRepository?.setOverlayPosition(newX, newY)
                }
            },
            onSave = {
                deduplicator.markPromptSaved(extraction.normalizedUrl)
                val intent = Intent(this@LaterboxSelectionAccessibilityService, QuickSaveActivity::class.java).apply {
                    putExtra(QuickSaveActivity.EXTRA_PREFILL_URL, extraction.normalizedUrl)
                    putExtra(QuickSaveActivity.EXTRA_CAPTURE_SOURCE, CaptureSource.ACCESSIBILITY_SELECTION.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            },
            onDismiss = {
                deduplicator.markPromptDismissed(extraction.normalizedUrl)
            }
        )

        deduplicator.markPromptShown(extraction.normalizedUrl, candidate.packageName)
    }

    override fun onInterrupt() {
        debounceJob?.cancel()
        overlayController.dismissImmediately()
        deduplicator.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        debounceJob?.cancel()
        serviceScope.cancel()
        overlayController.dismissImmediately()
        deduplicator.clear()
    }
}
