package com.laterbox.app.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.laterbox.app.ui.util.SaveFeedbackHelper
import com.laterbox.app.ui.components.LaterboxShapeTokens
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.laterbox.app.ai.ChatAction
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.media.ui.downloads.DownloadsTab
import com.laterbox.app.ui.AppViewModelProvider
import com.laterbox.app.ui.archive.ArchiveScreen
import com.laterbox.app.ui.archive.ArchiveViewModel
import com.laterbox.app.ui.chat.ChatScreen
import com.laterbox.app.ui.chat.ChatViewModel
import com.laterbox.app.ui.collections.CollectionDetailScreen
import com.laterbox.app.ui.collections.CollectionDetailViewModel
import com.laterbox.app.ui.collections.CollectionsScreen
import com.laterbox.app.ui.collections.CollectionsViewModel
import com.laterbox.app.ai.speech.SpeechStatus
import com.laterbox.app.ui.components.ComposerInsetHost
import com.laterbox.app.ui.components.FloatingChatComposer
import com.laterbox.app.ui.components.appendDictatedTranscript
import com.laterbox.app.ui.components.LaterboxModalDrawerSheet
import com.laterbox.app.ui.components.LaterboxPersistentSidePanel
import com.laterbox.app.ui.components.sectionForRoute
import com.laterbox.app.ui.inbox.InboxScreen
import com.laterbox.app.ui.inbox.InboxViewModel
import com.laterbox.app.ui.settings.SettingsScreen
import com.laterbox.app.ui.settings.SettingsViewModel
import com.laterbox.app.ui.sheets.SaveLinkBottomSheet
import com.laterbox.app.ui.sheets.SaveLinkViewModel
import com.laterbox.app.ui.sheets.SavedItemDetailSheet
import com.laterbox.app.ui.sheets.SavedItemDetailViewModel
import com.laterbox.app.ui.theme.LaterboxMotion
import com.laterbox.app.ui.theme.LaterboxTheme
import com.laterbox.app.ui.theme.rememberReducedMotion
import com.laterbox.app.ui.util.BrowserLauncher
import com.laterbox.app.ui.util.rememberLaterboxHaptics
import com.laterbox.app.LaterboxApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaterboxApp(
    modifier: Modifier = Modifier,
    initialOpenLinkId: Long? = null,
    onClearOpenLinkId: () -> Unit = {},
    initialOpenDestination: Pair<String, String?>? = null,
    onClearOpenDestination: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = LaterboxTheme.colors
    val haptics = rememberLaterboxHaptics()
    val reducedMotion = rememberReducedMotion()

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
    val chatViewModel: ChatViewModel = viewModel(factory = AppViewModelProvider.Factory)

    // Current route & shared state observation
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val preferences by settingsViewModel.preferences.collectAsState()
    val inboxUiState by inboxViewModel.uiState.collectAsState()
    val chatUiState by chatViewModel.uiState.collectAsState()
    val recentConversations by chatViewModel.recentConversations.collectAsState()
    val speechState by chatViewModel.speechState.collectAsState()

    // Hoisted Inbox list state & composer state so scroll & draft survive navigation
    val inboxListState = rememberLazyListState()
    var inboxComposerText by rememberSaveable { mutableStateOf("") }
    var inboxComposerHeightPx by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAudioConsentTarget by remember { mutableStateOf<String?>(null) }
    var pendingPermissionTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentRoute) {
        pendingAudioConsentTarget = null
    }

    val startTargetDictation: (String) -> Unit = { target ->
        if (target == "INBOX") {
            chatViewModel.startDictation { transcript ->
                inboxComposerText = appendDictatedTranscript(inboxComposerText, transcript)
            }
        } else {
            chatViewModel.startDictationIntoChatDraft()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingPermissionTarget
        pendingPermissionTarget = null
        if (granted && target != null) {
            startTargetDictation(target)
        } else if (!granted) {
            val activity = context as? Activity
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            coroutineScope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = "Microphone permission is needed for voice input.",
                    actionLabel = if (permanentlyDenied) "Settings" else null,
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }

    val beginDictationFor: (String) -> Unit = { target ->
        haptics.tick()
        if (!preferences.audioTranscriptionConsentAccepted) {
            pendingAudioConsentTarget = target
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startTargetDictation(target)
        } else {
            pendingPermissionTarget = target
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val acceptAudioConsentAndStart: () -> Unit = {
        val target = pendingAudioConsentTarget
        pendingAudioConsentTarget = null
        chatViewModel.acceptAudioConsent()
        if (target != null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startTargetDictation(target)
            } else {
                pendingPermissionTarget = target
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Cancel active microphone capture ONLY if the host pauses/disposes while recording/listening
    // (must NOT key on speechState.status, otherwise stopping recording triggers onPauseOrDispose!).
    val latestSpeechStatus by rememberUpdatedState(speechState.status)
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            if (latestSpeechStatus == SpeechStatus.RECORDING ||
                latestSpeechStatus == SpeechStatus.TRANSCRIBING_FALLBACK
            ) {
                chatViewModel.cancelDictation()
            }
        }
    }

    // Modal sheet states
    var showSaveSheet by remember { mutableStateOf(false) }
    var selectedLinkForDetails by remember { mutableStateOf<SavedLink?>(null) }
    var activeViewerAsset by remember { mutableStateOf<com.laterbox.app.media.domain.model.MediaAsset?>(null) }
    val saveSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    LaunchedEffect(initialOpenDestination) {
        val (dest, prompt) = initialOpenDestination ?: return@LaunchedEffect
        when {
            dest == "downloads" || dest == LaterboxDestination.Downloads.route -> {
                downloadsViewModel.onTabSelected(DownloadsTab.QUEUE)
                navController.navigate(LaterboxDestination.Downloads.route) {
                    popUpTo(LaterboxDestination.Inbox.route)
                    launchSingleTop = true
                }
            }
            dest == "chat" || dest == LaterboxDestination.Chat.BASE_ROUTE -> {
                navController.navigate(LaterboxDestination.Chat.createRoute(initialPrompt = prompt))
            }
            dest.startsWith("settings") -> {
                navController.navigate(dest) { launchSingleTop = true }
            }
        }
        onClearOpenDestination()
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
        haptics.tick()
        BrowserLauncher.openUrl(context, link.originalUrl, colors)
        inboxViewModel.onLinkOpened(link.id)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideTablet = maxWidth >= 840.dp
        val currentSection = sectionForRoute(currentRoute)

        val closeDrawerIfNarrow: () -> Unit = {
            if (!isWideTablet) {
                coroutineScope.launch { drawerState.close() }
            }
        }

        val navigateTopLevel: (String) -> Unit = { route ->
            haptics.tick()
            closeDrawerIfNarrow()
            if (route == LaterboxDestination.Inbox.route) {
                navController.navigate(route) {
                    popUpTo(LaterboxDestination.Inbox.route) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(route) {
                    popUpTo(LaterboxDestination.Inbox.route)
                    launchSingleTop = true
                }
            }
        }

        val handleNewChat: () -> Unit = {
            haptics.tick()
            closeDrawerIfNarrow()
            chatViewModel.startNewChat()
            navController.navigate(LaterboxDestination.Chat.createRoute()) {
                popUpTo(LaterboxDestination.Inbox.route)
                launchSingleTop = true
            }
        }

        val handleOpenConversation: (com.laterbox.app.data.local.entity.ChatConversationEntity) -> Unit = { conv ->
            haptics.tick()
            closeDrawerIfNarrow()
            chatViewModel.selectConversation(conv.id)
            navController.navigate(LaterboxDestination.Chat.createRoute(conversationId = conv.id)) {
                popUpTo(LaterboxDestination.Inbox.route)
                launchSingleTop = true
            }
        }

        val openMenuHandler: (() -> Unit)? = if (isWideTablet) {
            null
        } else {
            {
                haptics.tick()
                coroutineScope.launch { drawerState.open() }
            }
        }

        val mainContent: @Composable () -> Unit = {
            val density = LocalDensity.current
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = colors.backgroundPrimary,
                contentWindowInsets = WindowInsets.statusBars,
                snackbarHost = {
                    val composerDp = with(density) { inboxComposerHeightPx.toDp() }
                    val snackbarBottomPad = if (
                        currentRoute == LaterboxDestination.Inbox.route &&
                        preferences.assistantEnabled &&
                        !inboxUiState.isSelectionMode &&
                        inboxComposerHeightPx > 0
                    ) composerDp + 12.dp else 16.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
                            )
                            .padding(bottom = snackbarBottomPad)
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
                    val topLevelRoutes = remember {
                        setOf(
                            LaterboxDestination.Inbox.route,
                            LaterboxDestination.Collections.route,
                            LaterboxDestination.Downloads.route,
                            LaterboxDestination.Archive.route,
                            LaterboxDestination.NotificationHistory.route,
                            LaterboxDestination.Chat.route
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = LaterboxDestination.Inbox.route,
                        enterTransition = {
                            if (reducedMotion) {
                                fadeIn(animationSpec = LaterboxMotion.tweenSpec(0, reducedMotion = true))
                            } else if (initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes) {
                                fadeIn(animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium, LaterboxMotion.decelerateEasing))
                            } else {
                                slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium, LaterboxMotion.emphasizedDecelerate),
                                    initialOffset = { fullWidth -> fullWidth / 6 }
                                ) + fadeIn(animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium, LaterboxMotion.decelerateEasing))
                            }
                        },
                        exitTransition = {
                            if (reducedMotion) {
                                fadeOut(animationSpec = LaterboxMotion.tweenSpec(0, reducedMotion = true))
                            } else if (initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes) {
                                fadeOut(animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationShort, LaterboxMotion.standardEasing))
                            } else {
                                slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium, LaterboxMotion.emphasizedAccelerate),
                                    targetOffset = { fullWidth -> -fullWidth / 6 }
                                ) + fadeOut(animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationShort, LaterboxMotion.standardEasing))
                            }
                        },
                        popEnterTransition = {
                            if (reducedMotion) {
                                fadeIn(animationSpec = LaterboxMotion.tweenSpec(0, reducedMotion = true))
                            } else if (initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes) {
                                fadeIn(animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium, LaterboxMotion.decelerateEasing))
                            } else {
                                slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium, LaterboxMotion.emphasizedDecelerate),
                                    initialOffset = { fullWidth -> -fullWidth / 6 }
                                ) + fadeIn(animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium, LaterboxMotion.decelerateEasing))
                            }
                        },
                        popExitTransition = {
                            if (reducedMotion) {
                                fadeOut(animationSpec = LaterboxMotion.tweenSpec(0, reducedMotion = true))
                            } else if (initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes) {
                                fadeOut(animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationShort, LaterboxMotion.standardEasing))
                            } else {
                                slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium, LaterboxMotion.emphasizedAccelerate),
                                    targetOffset = { fullWidth -> fullWidth / 6 }
                                ) + fadeOut(animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationShort, LaterboxMotion.standardEasing))
                            }
                        }
                    ) {
                        // Inbox Screen
                        composable(LaterboxDestination.Inbox.route) {
                            InboxScreen(
                                uiState = inboxUiState,
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
                                onToggleSelectLink = inboxViewModel::onToggleSelectLink,
                                onSelectAll = { inboxViewModel.onSelectAll(inboxUiState.links) },
                                onClearSelection = inboxViewModel::onClearSelection,
                                onArchiveSelected = {
                                    inboxViewModel.onArchiveSelected()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Selected links archived",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                onToggleReadSelected = { inboxViewModel.onToggleReadSelected(inboxUiState.links) },
                                onDeleteSelected = {
                                    inboxViewModel.onDeleteSelected()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Selected links deleted",
                                            duration = SnackbarDuration.Short
                                        )
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
                                onDismissClipboardSuggestion = inboxViewModel::dismissClipboardSuggestion,
                                onMenuClick = openMenuHandler,
                                listState = inboxListState,
                                bottomComposerHeightPx = if (
                                    preferences.assistantEnabled &&
                                    !inboxUiState.isSelectionMode &&
                                    inboxUiState.hasCompletedOnboarding
                                ) inboxComposerHeightPx else 0
                            )
                        }

                        // Chat Assistant Screen
                        composable(
                            route = LaterboxDestination.Chat.route,
                            arguments = listOf(
                                navArgument("conversationId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                    nullable = true
                                },
                                navArgument("initialPrompt") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                    nullable = true
                                }
                            )
                        ) { backStackEntry ->
                            val rawConvId = backStackEntry.arguments?.getString("conversationId").orEmpty()
                            val rawPrompt = backStackEntry.arguments?.getString("initialPrompt").orEmpty()
                            val decodedConvId = java.net.URLDecoder.decode(rawConvId, "UTF-8").takeIf { it.isNotBlank() }
                            val decodedPrompt = java.net.URLDecoder.decode(rawPrompt, "UTF-8").takeIf { it.isNotBlank() }

                            LaunchedEffect(backStackEntry.id) {
                                chatViewModel.openChat(
                                    conversationId = decodedConvId,
                                    initialPrompt = decodedPrompt,
                                    invocationKey = backStackEntry.id
                                )
                            }

                            ChatScreen(
                                uiState = chatUiState,
                                onMenuClick = openMenuHandler,
                                onNewChatClick = { chatViewModel.startNewChat() },
                                onRunHealthCheck = { chatViewModel.runAppHealthCheck() },
                                onAcceptAiDisclosure = { chatViewModel.acceptAiDisclosure() },
                                onOpenAiSettings = { navController.navigate(LaterboxDestination.SettingsRemindersAi.route) },
                                onComposerTextChange = chatViewModel::onComposerTextChange,
                                onSendMessage = { chatViewModel.sendCurrentMessage() },
                                onStopStreaming = { chatViewModel.cancelStreaming() },
                                onRetryMessage = { msgId -> chatViewModel.retryMessage(msgId) },
                                onQuickPromptClick = { prompt -> chatViewModel.sendMessage(prompt) },
                                onActionClick = { action ->
                                    when (action) {
                                        is ChatAction.SearchLinks -> {
                                            inboxViewModel.onSearchQueryChange(action.query)
                                            navigateTopLevel(LaterboxDestination.Inbox.route)
                                        }
                                        is ChatAction.OpenLinkDetail -> {
                                            coroutineScope.launch {
                                                val app = context.applicationContext as? LaterboxApplication
                                                val link = try {
                                                    app?.container?.linkRepository?.getLinkById(action.linkId)?.first()
                                                } catch (_: Exception) { null }
                                                if (link != null) {
                                                    savedItemDetailViewModel.setLink(link)
                                                    selectedLinkForDetails = link
                                                } else {
                                                    snackbarHostState.showSnackbar("Saved link is no longer in your library.")
                                                }
                                            }
                                        }
                                        is ChatAction.OpenDownloads -> {
                                            downloadsViewModel.onTabSelected(
                                                if (action.initialTab == 1) DownloadsTab.QUEUE else DownloadsTab.LIBRARY
                                            )
                                            navigateTopLevel(LaterboxDestination.Downloads.route)
                                        }
                                        is ChatAction.OpenCollections -> {
                                            navigateTopLevel(LaterboxDestination.Collections.route)
                                        }
                                        is ChatAction.OpenSettings -> {
                                            navController.navigate(action.route)
                                        }
                                        is ChatAction.RunHealthCheck -> {
                                            chatViewModel.runAppHealthCheck()
                                        }
                                        is ChatAction.RetryInspection,
                                        is ChatAction.RetryDownload,
                                        is ChatAction.RefreshMetadata -> {
                                            chatViewModel.requestMutatingActionConfirmation(action)
                                        }
                                    }
                                },
                                onConfirmMutatingAction = { chatViewModel.confirmMutatingAction() },
                                onDismissMutatingAction = { chatViewModel.dismissMutatingAction() },
                                onAlwaysAllowMutatingAction = { chatViewModel.alwaysAllowMutatingActionInConversation() },
                                onDismissNotice = { chatViewModel.clearStatusNotice() },
                                onAddLinkClick = {
                                    saveLinkViewModel.reset()
                                    showSaveSheet = true
                                },
                                onMicClick = { beginDictationFor("CHAT") },
                                speechState = speechState,
                                onStopDictation = { chatViewModel.stopDictationIntoChatDraft() },
                                onCancelDictation = { chatViewModel.cancelDictation() },
                                showAudioConsent = pendingAudioConsentTarget == "CHAT",
                                onAcceptAudioConsent = acceptAudioConsentAndStart,
                                onDismissAudioConsent = { pendingAudioConsentTarget = null }
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
                                onDeleteCollection = collectionsViewModel::onDeleteCollection,
                                onSettingsClick = {
                                    navController.navigate(LaterboxDestination.Settings.route)
                                },
                                onMenuClick = openMenuHandler
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
                                onPermanentDelete = archiveViewModel::onPermanentDelete,
                                onSettingsClick = {
                                    navController.navigate(LaterboxDestination.Settings.route)
                                },
                                onMenuClick = openMenuHandler
                            )
                        }

                        // Downloads Screen
                        composable(LaterboxDestination.Downloads.route) {
                            com.laterbox.app.media.ui.downloads.DownloadsScreen(
                                viewModel = downloadsViewModel,
                                onSettingsClick = {
                                    navController.navigate(LaterboxDestination.Settings.route)
                                },
                                onOpenLinkDetail = { targetId ->
                                    coroutineScope.launch {
                                        val app = context.applicationContext as? LaterboxApplication
                                        try {
                                            val link = app?.container?.linkRepository?.getLinkById(targetId)?.first()
                                            if (link != null) {
                                                savedItemDetailViewModel.setLink(link)
                                                selectedLinkForDetails = link
                                            }
                                        } catch (_: Exception) {}
                                    }
                                },
                                onMenuClick = openMenuHandler
                            )
                        }

                        // Settings Screen (Landing Hub)
                        composable(LaterboxDestination.Settings.route) {
                            SettingsScreen(
                                preferences = preferences,
                                onThemeModeChange = settingsViewModel::setThemeMode,
                                onNavigateToAppearanceProfile = { navController.navigate(LaterboxDestination.SettingsAppearanceProfile.route) },
                                onNavigateToRemindersAi = { navController.navigate(LaterboxDestination.SettingsRemindersAi.route) },
                                onNavigateToCaptureShortcuts = { navController.navigate(LaterboxDestination.SettingsCaptureShortcuts.route) },
                                onNavigateToReadingBehavior = { navController.navigate(LaterboxDestination.SettingsReadingBehavior.route) },
                                onNavigateToMediaDownloads = { navController.navigate(LaterboxDestination.SettingsMediaDownloads.route) },
                                onNavigateToDataStorage = { navController.navigate(LaterboxDestination.SettingsDataStorage.route) },
                                onNavigateToAboutPrivacy = { navController.navigate(LaterboxDestination.SettingsAboutPrivacy.route) },
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Settings: Appearance & Profile
                        composable(LaterboxDestination.SettingsAppearanceProfile.route) {
                            com.laterbox.app.ui.settings.AppearanceProfileSettingsScreen(
                                preferences = preferences,
                                onThemeModeChange = settingsViewModel::setThemeMode,
                                onFontSizeScaleChange = settingsViewModel::setFontSizeScale,
                                onUserNameChange = settingsViewModel::setUserName,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Settings: Reminders & AI
                        composable(LaterboxDestination.SettingsRemindersAi.route) {
                            val aiConnectionStatus by settingsViewModel.aiConnectionStatus.collectAsState()
                            com.laterbox.app.ui.settings.RemindersAiSettingsScreen(
                                preferences = preferences,
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
                                onBackClick = { navController.popBackStack() },
                                onAssistantEnabledChange = settingsViewModel::setAssistantEnabled,
                                onResetAiDisclosureConsent = { settingsViewModel.setAssistantAiDisclosureAccepted(false) },
                                onSmartNotificationsExtractionFailureChange = settingsViewModel::setSmartNotificationsExtractionFailure,
                                onSmartNotificationsDownloadFailureChange = settingsViewModel::setSmartNotificationsDownloadFailure,
                                onSmartNotificationsAppHealthChange = settingsViewModel::setSmartNotificationsAppHealth,
                                onSmartNotificationsSummariesChange = settingsViewModel::setSmartNotificationsSummaries,
                                onSmartNotificationsTipsChange = settingsViewModel::setSmartNotificationsTips,
                                onQuietHoursEnabledChange = settingsViewModel::setQuietHoursEnabled,
                                onQuietHoursWindowChange = settingsViewModel::setQuietHoursWindow
                            )
                        }

                        // Settings: Capture & Shortcuts
                        composable(LaterboxDestination.SettingsCaptureShortcuts.route) {
                            com.laterbox.app.ui.settings.CaptureShortcutsSettingsScreen(
                                preferences = preferences,
                                onAccessibilityCaptureEnabledChange = settingsViewModel::setAccessibilityCaptureEnabled,
                                onClipboardSuggestionEnabledChange = settingsViewModel::setClipboardSuggestionEnabled,
                                onToggleExcludedPackage = settingsViewModel::toggleExcludedCapturePackage,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Settings: Reading & Behaviour
                        composable(LaterboxDestination.SettingsReadingBehavior.route) {
                            com.laterbox.app.ui.settings.ReadingBehaviorSettingsScreen(
                                preferences = preferences,
                                onAutoMarkAsReadChange = settingsViewModel::setAutoMarkAsRead,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Settings: Media & Downloads
                        composable(LaterboxDestination.SettingsMediaDownloads.route) {
                            val isUpdatingYtDlp by settingsViewModel.isUpdatingYtDlp.collectAsState()
                            val ytDlpState by settingsViewModel.ytDlpState.collectAsState()
                            val ffmpegState by settingsViewModel.ffmpegState.collectAsState()

                            com.laterbox.app.ui.settings.MediaDownloadsSettingsScreen(
                                preferences = preferences,
                                onMediaDownloadNetworkPolicyChange = settingsViewModel::setMediaDownloadNetworkPolicy,
                                onAutoInspectMediaOnSaveChange = settingsViewModel::setAutoInspectMediaOnSave,
                                onFastEngineEnabledChange = settingsViewModel::setFastEngineEnabled,
                                isUpdatingYtDlp = isUpdatingYtDlp,
                                onUpdateYtDlpClick = {
                                    settingsViewModel.updateYtDlp { msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                ytDlpState = ytDlpState,
                                ffmpegState = ffmpegState,
                                onGetDiagnosticReport = settingsViewModel::getDiagnosticReport,
                                onGetDiagnosticReportText = settingsViewModel::getDiagnosticsReportText,
                                onRetryInitializeEngine = {
                                    settingsViewModel.retryInitializeEngine { msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSetCustomExportDirectory = settingsViewModel::setCustomExportDirectory,
                                onClearCustomExportDirectory = settingsViewModel::clearCustomExportDirectory,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Settings: Data & Storage
                        composable(LaterboxDestination.SettingsDataStorage.route) {
                            com.laterbox.app.ui.settings.DataStorageSettingsScreen(
                                preferences = preferences,
                                onAllowSystemBackupChange = settingsViewModel::setAllowSystemBackup,
                                onExportBackup = settingsViewModel::exportBackup,
                                onImportBackup = settingsViewModel::importBackup,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Settings: About & Privacy
                        composable(LaterboxDestination.SettingsAboutPrivacy.route) {
                            val isCheckingUpdate by settingsViewModel.isCheckingUpdate.collectAsState()
                            val availableUpdateInfo by settingsViewModel.updateCheckResult.collectAsState()

                            com.laterbox.app.ui.settings.AboutPrivacySettingsScreen(
                                isCheckingUpdate = isCheckingUpdate,
                                availableUpdateInfo = availableUpdateInfo,
                                onCheckForUpdates = {
                                    settingsViewModel.checkForUpdates { msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDismissUpdateDialog = settingsViewModel::dismissUpdateDialog,
                                onPostponeUpdate = settingsViewModel::postponeUpdate,
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
                                },
                                onMenuClick = openMenuHandler
                            )
                        }
                    }

                    // Bottom Floating Composer pill on Inbox ONLY (uses same ComposerInsetHost as Chat)
                    if (currentRoute == LaterboxDestination.Inbox.route &&
                        preferences.assistantEnabled &&
                        !inboxUiState.isSelectionMode &&
                        inboxUiState.hasCompletedOnboarding
                    ) {
                        ComposerInsetHost(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            horizontalPadding = 16.dp,
                            onMeasuredHeightPx = { inboxComposerHeightPx = it }
                        ) {
                            FloatingChatComposer(
                                text = inboxComposerText,
                                onTextChange = { inboxComposerText = it },
                                onSend = {
                                    val prompt = inboxComposerText.trim()
                                    if (prompt.isNotBlank()) {
                                        haptics.tick()
                                        inboxComposerText = ""
                                        navController.navigate(
                                            LaterboxDestination.Chat.createRoute(initialPrompt = prompt)
                                        )
                                    }
                                },
                                onAddLinkClick = {
                                    saveLinkViewModel.reset()
                                    showSaveSheet = true
                                },
                                onMicClick = { beginDictationFor("INBOX") },
                                speechState = speechState,
                                onStopDictation = {
                                    chatViewModel.stopDictation { transcript ->
                                        inboxComposerText = appendDictatedTranscript(inboxComposerText, transcript)
                                    }
                                },
                                onCancelDictation = { chatViewModel.cancelDictation() },
                                showAudioConsent = pendingAudioConsentTarget == "INBOX",
                                onAcceptAudioConsent = acceptAudioConsentAndStart,
                                onDismissAudioConsent = { pendingAudioConsentTarget = null }
                            )
                        }
                    }
                }
            }
        }

        if (isWideTablet) {
            Row(modifier = Modifier.fillMaxSize()) {
                LaterboxPersistentSidePanel(
                    currentSection = currentSection,
                    activeConversationId = chatUiState.activeConversationId,
                    recentConversations = recentConversations,
                    unreadNotificationCount = inboxUiState.unreadNotificationCount,
                    userName = preferences.userName,
                    onNavigateInbox = { navigateTopLevel(LaterboxDestination.Inbox.route) },
                    onNavigateCollections = { navigateTopLevel(LaterboxDestination.Collections.route) },
                    onNavigateDownloads = { navigateTopLevel(LaterboxDestination.Downloads.route) },
                    onNavigateArchive = { navigateTopLevel(LaterboxDestination.Archive.route) },
                    onNavigateNotifications = { navigateTopLevel(LaterboxDestination.NotificationHistory.route) },
                    onNavigateSettings = { navigateTopLevel(LaterboxDestination.Settings.route) },
                    onNewChatClick = handleNewChat,
                    onConversationClick = handleOpenConversation,
                    onDeleteConversation = { convId -> chatViewModel.deleteConversation(convId) }
                )
                Box(modifier = Modifier.weight(1f)) {
                    mainContent()
                }
            }
        } else {
            val gesturesEnabled = currentRoute?.startsWith("settings") != true &&
                currentRoute?.startsWith("collection/") != true
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = gesturesEnabled,
                drawerContent = {
                    LaterboxModalDrawerSheet(
                        currentSection = currentSection,
                        activeConversationId = chatUiState.activeConversationId,
                        recentConversations = recentConversations,
                        unreadNotificationCount = inboxUiState.unreadNotificationCount,
                        userName = preferences.userName,
                        onNavigateInbox = { navigateTopLevel(LaterboxDestination.Inbox.route) },
                        onNavigateCollections = { navigateTopLevel(LaterboxDestination.Collections.route) },
                        onNavigateDownloads = { navigateTopLevel(LaterboxDestination.Downloads.route) },
                        onNavigateArchive = { navigateTopLevel(LaterboxDestination.Archive.route) },
                        onNavigateNotifications = { navigateTopLevel(LaterboxDestination.NotificationHistory.route) },
                        onNavigateSettings = { navigateTopLevel(LaterboxDestination.Settings.route) },
                        onNewChatClick = handleNewChat,
                        onConversationClick = handleOpenConversation,
                        onDeleteConversation = { convId -> chatViewModel.deleteConversation(convId) }
                    )
                }
            ) {
                mainContent()
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
                haptics.confirm()
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
                haptics.confirm()
                savedItemDetailViewModel.onSaveEdits {
                    SaveFeedbackHelper.showSaveSuccess(context, isUpdate = true)
                    coroutineScope.launch {
                        detailSheetState.hide()
                        selectedLinkForDetails = null
                    }
                }
            },
            onToggleRead = {
                haptics.tick()
                savedItemDetailViewModel.onToggleRead()
            },
            onToggleArchive = {
                haptics.tick()
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
                haptics.warning()
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
                haptics.warning()
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
            onCancelInspection = savedItemDetailViewModel::cancelInspection,
            onDownloadCandidate = { candidate ->
                haptics.confirm()
                savedItemDetailViewModel.downloadCandidate(candidate)
            },
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
