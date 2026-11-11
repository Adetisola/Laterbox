package com.laterbox.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.laterbox.app.ui.util.SaveFeedbackHelper
import com.laterbox.app.ui.components.LaterboxShapeTokens
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laterbox.app.update.AppUpdateDialog
import com.laterbox.app.update.AppUpdateInfo
import com.laterbox.app.update.GitHubUpdateChecker
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.AppViewModelProvider
import com.laterbox.app.ui.archive.ArchiveScreen
import com.laterbox.app.ui.archive.ArchiveViewModel
import com.laterbox.app.ui.collections.CollectionDetailScreen
import com.laterbox.app.ui.collections.CollectionDetailViewModel
import com.laterbox.app.ui.collections.CollectionsScreen
import com.laterbox.app.ui.collections.CollectionsViewModel
import com.laterbox.app.ui.components.BottomBarDestination
import com.laterbox.app.ui.components.LaterboxBottomBar
import com.laterbox.app.ui.inbox.InboxScreen
import com.laterbox.app.ui.inbox.InboxViewModel
import com.laterbox.app.ui.settings.SettingsScreen
import com.laterbox.app.ui.settings.SettingsViewModel
import com.laterbox.app.ui.sheets.SaveLinkBottomSheet
import com.laterbox.app.ui.sheets.SaveLinkViewModel
import com.laterbox.app.ui.sheets.SavedItemDetailSheet
import com.laterbox.app.ui.sheets.SavedItemDetailViewModel
import com.laterbox.app.ui.theme.LaterboxTheme
import com.laterbox.app.ui.util.BrowserLauncher
import com.laterbox.app.LaterboxApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaterboxApp(
    modifier: Modifier = Modifier,
    initialOpenLinkId: Long? = null,
    onClearOpenLinkId: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = LaterboxTheme.colors

    // ViewModels
    val inboxViewModel: InboxViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val collectionsViewModel: CollectionsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val collectionDetailViewModel: CollectionDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val archiveViewModel: ArchiveViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val saveLinkViewModel: SaveLinkViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val savedItemDetailViewModel: SavedItemDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val notificationHistoryViewModel: com.laterbox.app.ui.notifications.NotificationHistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val downloadsViewModel: com.laterbox.app.media.ui.downloads.DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory)

    // Current route observation
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentBottomDestination = when {
        currentRoute == LaterboxDestination.Collections.route || currentRoute?.startsWith("collection/") == true -> BottomBarDestination.COLLECTIONS
        currentRoute == LaterboxDestination.Downloads.route -> BottomBarDestination.DOWNLOADS
        currentRoute == LaterboxDestination.Archive.route -> BottomBarDestination.ARCHIVE
        else -> BottomBarDestination.INBOX
    }

    // Modal sheet states
    var showSaveSheet by remember { mutableStateOf(false) }
    var selectedLinkForDetails by remember { mutableStateOf<SavedLink?>(null) }
    var activeViewerAsset by remember { mutableStateOf<com.laterbox.app.media.domain.model.MediaAsset?>(null) }
    val saveSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialOpenLinkId) {
        val targetId = initialOpenLinkId ?: return@LaunchedEffect
        val app = context.applicationContext as? LaterboxApplication
        try {
            val link = app?.container?.linkRepository?.getLinkById(targetId)?.first()
            if (link != null) {
                savedItemDetailViewModel.setLink(link)
                selectedLinkForDetails = link
            }
        } catch (_: Exception) {}
        onClearOpenLinkId()
    }

    var appUpdateInfoToPrompt by remember { mutableStateOf<AppUpdateInfo?>(null) }

    LifecycleResumeEffect(Unit) {
        val checkJob = coroutineScope.launch {
            val sixHoursMs = 6L * 60 * 60 * 1000L
            val oneDayMs = 24L * 60 * 60 * 1000L
            val now = System.currentTimeMillis()
            val app = context.applicationContext as? LaterboxApplication
            val prefsRepo = app?.container?.userPreferencesRepository
            val prefs = prefsRepo?.userPreferencesFlow?.first() ?: return@launch
            if (now - prefs.lastUpdateCheckTime >= sixHoursMs) {
                prefsRepo.recordUpdateCheck(now)
                val result = GitHubUpdateChecker.checkForUpdate(com.laterbox.app.BuildConfig.VERSION_NAME)
                if (result is com.laterbox.app.update.UpdateCheckResult.UpdateAvailable) {
                    val info = result.updateInfo
                    val isDismissedRecently = prefs.dismissedUpdateVersion == info.versionName &&
                        (now - prefs.dismissedUpdateTimestamp < oneDayMs)
                    if (!isDismissedRecently) {
                        appUpdateInfoToPrompt = info
                    }
                }
            }
        }
        onPauseOrDispose {
            checkJob.cancel()
        }
    }

    val handleOpenLink: (SavedLink) -> Unit = { link ->
        BrowserLauncher.openUrl(context, link.originalUrl, colors)
        inboxViewModel.onLinkOpened(link.id)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundPrimary,
        snackbarHost = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = if (currentRoute != LaterboxDestination.Settings.route && currentRoute != LaterboxDestination.NotificationHistory.route) 84.dp else 16.dp)
            ) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = colors.surfaceElevated,
                            contentColor = colors.textPrimary,
                            actionColor = colors.accentPrimary,
                            shape = LaterboxShapeTokens.Card
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = LaterboxDestination.Inbox.route
            ) {
                // Inbox Screen
                composable(LaterboxDestination.Inbox.route) {
                    val uiState by inboxViewModel.uiState.collectAsState()
                    InboxScreen(
                        uiState = uiState,
                        onSearchQueryChange = inboxViewModel::onSearchQueryChange,
                        onClearSearch = inboxViewModel::onClearSearch,
                        onFilterContentType = inboxViewModel::onFilterContentType,
                        onFilterDateRange = inboxViewModel::onFilterDateRange,
                        onLinkClick = { link ->
                            savedItemDetailViewModel.setLink(link)
                            selectedLinkForDetails = link
                        },
                        onQuickOpenClick = handleOpenLink,
                        onAddLinkClick = {
                            saveLinkViewModel.reset()
                            showSaveSheet = true
                        },
                        onSettingsClick = {
                            navController.navigate(LaterboxDestination.Settings.route)
                        },
                        onNotificationHistoryClick = {
                            navController.navigate(LaterboxDestination.NotificationHistory.route)
                        },
                        onToggleViewMode = inboxViewModel::onToggleViewMode,
                        onRefresh = inboxViewModel::refresh,
                        onCompleteOnboarding = inboxViewModel::onCompleteOnboarding,
                        onToggleRead = { link ->
                            inboxViewModel.onToggleRead(link.id, link.isRead)
                        },
                        onArchive = { link ->
                            inboxViewModel.onArchive(link.id)
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Link archived",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    inboxViewModel.unarchive(link.id)
                                }
                            }
                        },
                        onDelete = { link ->
                            inboxViewModel.onDelete(link.id)
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Link deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    inboxViewModel.onRestore(link)
                                }
                            }
                        },
                        onCheckClipboard = inboxViewModel::checkClipboard,
                        onSaveClipboardSuggestion = { suggestion ->
                            inboxViewModel.saveClipboardSuggestion(
                                suggestion = suggestion,
                                onSuccess = {
                                    SaveFeedbackHelper.showSaveSuccess(context, isUpdate = false)
                                }
                            )
                        },
                        onDismissClipboardSuggestion = inboxViewModel::dismissClipboardSuggestion
                    )
                }

                // Collections Screen
                composable(LaterboxDestination.Collections.route) {
                    val uiState by collectionsViewModel.uiState.collectAsState()
                    CollectionsScreen(
                        uiState = uiState,
                        onCollectionClick = { collection ->
                            navController.navigate(
                                LaterboxDestination.CollectionDetail.createRoute(
                                    collection.id,
                                    collection.name
                                )
                            )
                        },
                        onOpenCreateDialog = collectionsViewModel::onOpenCreateDialog,
                        onCloseCreateDialog = collectionsViewModel::onCloseCreateDialog,
                        onCreateCollection = collectionsViewModel::onCreateCollection,
                        onOpenRenameDialog = collectionsViewModel::onOpenRenameDialog,
                        onCloseRenameDialog = collectionsViewModel::onCloseRenameDialog,
                        onRenameCollection = collectionsViewModel::onRenameCollection,
                        onOpenDeleteDialog = collectionsViewModel::onOpenDeleteDialog,
                        onCloseDeleteDialog = collectionsViewModel::onCloseDeleteDialog,
                        onDeleteCollection = collectionsViewModel::onDeleteCollection
                    )
                }

                // Collection Detail Screen
                composable(
                    route = LaterboxDestination.CollectionDetail.route,
                    arguments = listOf(
                        navArgument("collectionId") { type = NavType.LongType },
                        navArgument("collectionName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val collectionId = backStackEntry.arguments?.getLong("collectionId") ?: 0L
                    val rawName = backStackEntry.arguments?.getString("collectionName") ?: ""
                    val collectionName = java.net.URLDecoder.decode(rawName, "UTF-8")

                    LaunchedEffect(collectionId, collectionName) {
                        collectionDetailViewModel.setCollection(collectionId, collectionName)
                    }

                    val uiState by collectionDetailViewModel.uiState.collectAsState()
                    CollectionDetailScreen(
                        uiState = uiState,
                        onBackClick = { navController.popBackStack() },
                        onLinkClick = { link ->
                            savedItemDetailViewModel.setLink(link)
                            selectedLinkForDetails = link
                        },
                        onQuickOpenClick = handleOpenLink
                    )
                }

                // Archive Screen
                composable(LaterboxDestination.Archive.route) {
                    val uiState by archiveViewModel.uiState.collectAsState()
                    ArchiveScreen(
                        uiState = uiState,
                        onSearchQueryChange = archiveViewModel::onSearchQueryChange,
                        onClearSearch = archiveViewModel::onClearSearch,
                        onLinkClick = { link ->
                            savedItemDetailViewModel.setLink(link)
                            selectedLinkForDetails = link
                        },
                        onQuickOpenClick = handleOpenLink,
                        onRestoreLink = archiveViewModel::onRestoreLink,
                        onOpenDeleteDialog = archiveViewModel::onOpenDeleteDialog,
                        onCloseDeleteDialog = archiveViewModel::onCloseDeleteDialog,
                        onPermanentDelete = archiveViewModel::onPermanentDelete
                    )
                }

                // Downloads Screen
                composable(LaterboxDestination.Downloads.route) {
                    com.laterbox.app.media.ui.downloads.DownloadsScreen(
                        viewModel = downloadsViewModel,
                        onSettingsClick = {
                            navController.navigate(LaterboxDestination.Settings.route)
                        }
                    )
                }

                // Settings Screen
                composable(LaterboxDestination.Settings.route) {
                    val preferences by settingsViewModel.preferences.collectAsState()
                    val aiConnectionStatus by settingsViewModel.aiConnectionStatus.collectAsState()
                    val isCheckingUpdate by settingsViewModel.isCheckingUpdate.collectAsState()
                    val availableUpdateInfo by settingsViewModel.updateCheckResult.collectAsState()
                    val isUpdatingYtDlp by settingsViewModel.isUpdatingYtDlp.collectAsState()
                    val ytDlpState by settingsViewModel.ytDlpState.collectAsState()
                    val ffmpegState by settingsViewModel.ffmpegState.collectAsState()

                    SettingsScreen(
                        preferences = preferences,
                        isCheckingUpdate = isCheckingUpdate,
                        availableUpdateInfo = availableUpdateInfo,
                        isUpdatingYtDlp = isUpdatingYtDlp,
                        ytDlpState = ytDlpState,
                        ffmpegState = ffmpegState,
                        onGetDiagnosticReport = settingsViewModel::getDiagnosticReport,
                        onRetryInitializeEngine = {
                            settingsViewModel.retryInitializeEngine { msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUpdateYtDlpClick = {
                            settingsViewModel.updateYtDlp { msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCheckForUpdates = {
                            settingsViewModel.checkForUpdates { msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismissUpdateDialog = settingsViewModel::dismissUpdateDialog,
                        onPostponeUpdate = settingsViewModel::postponeUpdate,
                        onThemeModeChange = settingsViewModel::setThemeMode,
                        onUserNameChange = settingsViewModel::setUserName,
                        onAutoMarkAsReadChange = settingsViewModel::setAutoMarkAsRead,
                        onAllowSystemBackupChange = settingsViewModel::setAllowSystemBackup,
                        onFontSizeScaleChange = settingsViewModel::setFontSizeScale,
                        onSmartRemindersEnabledChange = settingsViewModel::setSmartRemindersEnabled,
                        onMorningEveningEnabledChange = settingsViewModel::setMorningEveningEnabled,
                        onMorningTimeChange = settingsViewModel::setMorningTime,
                        onEveningTimeChange = settingsViewModel::setEveningTime,
                        onWeekendRoundupEnabledChange = settingsViewModel::setWeekendRoundupEnabled,
                        onWeekendRoundupTimeChange = settingsViewModel::setWeekendRoundupTime,
                        onDailyDigestEnabledChange = settingsViewModel::setDailyDigestEnabled,
                        onDailyDigestTimeChange = settingsViewModel::setDailyDigestTime,
                        onDailyDigestWeekdaysOnlyChange = settingsViewModel::setDailyDigestWeekdaysOnly,
                        onCustomScheduleEnabledChange = settingsViewModel::setCustomScheduleEnabled,
                        onCustomScheduleDaysChange = settingsViewModel::setCustomScheduleDays,
                        onCustomScheduleTimeChange = settingsViewModel::setCustomScheduleTime,
                        onSmartReminderPersistenceChange = settingsViewModel::setSmartReminderPersistence,
                        aiConnectionStatus = aiConnectionStatus,
                        onGeminiApiKeyChange = settingsViewModel::setGeminiApiKey,
                        onTestAiConnection = settingsViewModel::testAiConnection,
                        onSendTestSmartDigest = settingsViewModel::sendTestSmartDigest,
                        onExportBackup = settingsViewModel::exportBackup,
                        onImportBackup = settingsViewModel::importBackup,
                        onAccessibilityCaptureEnabledChange = settingsViewModel::setAccessibilityCaptureEnabled,
                        onClipboardSuggestionEnabledChange = settingsViewModel::setClipboardSuggestionEnabled,
                        onToggleExcludedPackage = settingsViewModel::toggleExcludedCapturePackage,
                        onMediaDownloadNetworkPolicyChange = settingsViewModel::setMediaDownloadNetworkPolicy,
                        onAutoInspectMediaOnSaveChange = settingsViewModel::setAutoInspectMediaOnSave,
                        onSetCustomExportDirectory = settingsViewModel::setCustomExportDirectory,
                        onClearCustomExportDirectory = settingsViewModel::clearCustomExportDirectory,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Notification History Screen
                composable(LaterboxDestination.NotificationHistory.route) {
                    com.laterbox.app.ui.notifications.NotificationHistoryScreen(
                        viewModel = notificationHistoryViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenUrl = { url, linkId ->
                            BrowserLauncher.openUrl(context, url, colors)
                            if (linkId != null) {
                                inboxViewModel.onLinkOpened(linkId)
                            }
                        }
                    )
                }
            }

            if (currentRoute != LaterboxDestination.Settings.route && currentRoute != LaterboxDestination.NotificationHistory.route) {
                LaterboxBottomBar(
                    currentDestination = currentBottomDestination,
                    onDestinationSelected = { destination ->
                        when (destination) {
                            BottomBarDestination.INBOX -> {
                                navController.navigate(LaterboxDestination.Inbox.route) {
                                    popUpTo(LaterboxDestination.Inbox.route) { inclusive = true }
                                }
                            }
                            BottomBarDestination.COLLECTIONS -> {
                                navController.navigate(LaterboxDestination.Collections.route) {
                                    popUpTo(LaterboxDestination.Inbox.route)
                                }
                            }
                            BottomBarDestination.DOWNLOADS -> {
                                navController.navigate(LaterboxDestination.Downloads.route) {
                                    popUpTo(LaterboxDestination.Inbox.route)
                                }
                            }
                            BottomBarDestination.ARCHIVE -> {
                                navController.navigate(LaterboxDestination.Archive.route) {
                                    popUpTo(LaterboxDestination.Inbox.route)
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // Save Link Bottom Sheet
    if (showSaveSheet) {
        val saveUiState by saveLinkViewModel.uiState.collectAsState()
        SaveLinkBottomSheet(
            uiState = saveUiState,
            sheetState = saveSheetState,
            onUrlChange = saveLinkViewModel::onUrlChange,
            onTitleChange = saveLinkViewModel::onTitleChange,
            onPersonalNoteChange = saveLinkViewModel::onPersonalNoteChange,
            onToggleCollection = saveLinkViewModel::onToggleCollection,
            onCreateCollection = saveLinkViewModel::onCreateCollection,
            onReminderAtChange = saveLinkViewModel::onReminderAtChange,
            onSaveClick = {
                saveLinkViewModel.onSave { isUpdate ->
                    SaveFeedbackHelper.showSaveSuccess(context, isUpdate)
                    coroutineScope.launch {
                        saveSheetState.hide()
                        showSaveSheet = false
                    }
                }
            },
            onDismissRequest = {
                coroutineScope.launch {
                    saveSheetState.hide()
                    showSaveSheet = false
                }
            }
        )
    }

    // Saved Item Detail Sheet
    if (selectedLinkForDetails != null) {
        val detailUiState by savedItemDetailViewModel.uiState.collectAsState()
        SavedItemDetailSheet(
            uiState = detailUiState,
            sheetState = detailSheetState,
            onTitleChange = savedItemDetailViewModel::onTitleChange,
            onNoteChange = savedItemDetailViewModel::onNoteChange,
            onToggleCollection = savedItemDetailViewModel::onToggleCollection,
            onCreateCollection = savedItemDetailViewModel::onCreateAndAssignCollection,
            onSetReminder = savedItemDetailViewModel::onSetReminder,
            onSaveEdits = {
                savedItemDetailViewModel.onSaveEdits {
                    SaveFeedbackHelper.showSaveSuccess(context, isUpdate = true)
                    coroutineScope.launch {
                        detailSheetState.hide()
                        selectedLinkForDetails = null
                    }
                }
            },
            onToggleRead = savedItemDetailViewModel::onToggleRead,
            onToggleArchive = {
                val item = selectedLinkForDetails
                val wasArchived = item?.isArchived == true
                val linkId = item?.id
                savedItemDetailViewModel.onToggleArchive {
                    coroutineScope.launch {
                        detailSheetState.hide()
                        selectedLinkForDetails = null
                        if (!wasArchived && linkId != null) {
                            val result = snackbarHostState.showSnackbar(
                                message = "Archived",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                inboxViewModel.unarchive(linkId)
                            }
                        }
                    }
                }
            },
            onDelete = {
                savedItemDetailViewModel.deleteWithUndo(deleteFiles = false) { deletedLink ->
                    coroutineScope.launch {
                        detailSheetState.hide()
                        selectedLinkForDetails = null
                        val result = snackbarHostState.showSnackbar(
                            message = "Deleted link",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            inboxViewModel.restoreLink(deletedLink)
                        }
                    }
                }
            },
            onDeleteWithFiles = { deleteFiles ->
                savedItemDetailViewModel.deleteWithUndo(deleteFiles = deleteFiles) { deletedLink ->
                    coroutineScope.launch {
                        detailSheetState.hide()
                        selectedLinkForDetails = null
                        val result = snackbarHostState.showSnackbar(
                            message = if (deleteFiles) "Deleted link & files" else "Deleted link",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            inboxViewModel.restoreLink(deletedLink)
                        }
                    }
                }
            },
            onInspectMedia = { savedItemDetailViewModel.inspectLink(forceRefresh = false) },
            onRetryInspection = { savedItemDetailViewModel.retryInspection() },
            onDownloadCandidate = savedItemDetailViewModel::downloadCandidate,
            onOpenMediaAsset = { asset ->
                when (asset.mediaType) {
                    com.laterbox.app.media.domain.model.MediaType.IMAGE -> activeViewerAsset = asset
                    com.laterbox.app.media.domain.model.MediaType.VIDEO, com.laterbox.app.media.domain.model.MediaType.AUDIO -> activeViewerAsset = asset
                    com.laterbox.app.media.domain.model.MediaType.DOCUMENT, com.laterbox.app.media.domain.model.MediaType.OTHER -> com.laterbox.app.media.ui.viewer.DocumentOpener.openDocument(context, asset)
                }
            },
            onExportMediaAsset = { asset ->
                savedItemDetailViewModel.exportAsset(asset.id) { success ->
                    android.widget.Toast.makeText(
                        context,
                        if (success) "Exported successfully" else "Failed to export",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDeleteMediaAsset = { asset -> savedItemDetailViewModel.deleteAsset(asset.id) },
            onOverrideCellular = savedItemDetailViewModel::overrideCellular,
            onCancelTask = savedItemDetailViewModel::cancelTask,
            onDismissTask = savedItemDetailViewModel::dismissTask,
            onQuickOpen = { url ->
                BrowserLauncher.openUrl(context, url, colors)
                selectedLinkForDetails?.let { inboxViewModel.onLinkOpened(it.id) }
            },
            onRetryMetadata = savedItemDetailViewModel::onRetryMetadata,
            onDismissRequest = {
                coroutineScope.launch {
                    detailSheetState.hide()
                    selectedLinkForDetails = null
                }
            }
        )
    }

    // Active Media Viewer Overlay
    activeViewerAsset?.let { asset ->
        when (asset.mediaType) {
            com.laterbox.app.media.domain.model.MediaType.IMAGE -> {
                com.laterbox.app.media.ui.viewer.ImageViewer(
                    asset = asset,
                    onDismiss = { activeViewerAsset = null },
                    onExport = {
                        savedItemDetailViewModel.exportAsset(asset.id) { success ->
                            android.widget.Toast.makeText(
                                context,
                                if (success) "Exported to gallery" else "Failed to export",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
            com.laterbox.app.media.domain.model.MediaType.VIDEO, com.laterbox.app.media.domain.model.MediaType.AUDIO -> {
                com.laterbox.app.media.ui.viewer.MediaPlayerScreen(
                    asset = asset,
                    onDismiss = { activeViewerAsset = null },
                    onExport = {
                        savedItemDetailViewModel.exportAsset(asset.id) { success ->
                            android.widget.Toast.makeText(
                                context,
                                if (success) "Exported to storage" else "Failed to export",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
            else -> {
                activeViewerAsset = null
            }
        }
    }

    if (appUpdateInfoToPrompt != null) {
        AppUpdateDialog(
            updateInfo = appUpdateInfoToPrompt!!,
            onDismiss = { appUpdateInfoToPrompt = null },
            onPostpone = { version ->
                appUpdateInfoToPrompt = null
                coroutineScope.launch {
                    val app = context.applicationContext as? LaterboxApplication
                    app?.container?.userPreferencesRepository?.dismissUpdate(version)
                }
            }
        )
    }
}
