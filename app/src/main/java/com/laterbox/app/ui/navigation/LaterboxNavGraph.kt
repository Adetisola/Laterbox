package com.laterbox.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaterboxApp(modifier: Modifier = Modifier) {
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

    // Current route observation
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentBottomDestination = when {
        currentRoute == LaterboxDestination.Collections.route || currentRoute?.startsWith("collection/") == true -> BottomBarDestination.COLLECTIONS
        currentRoute == LaterboxDestination.Archive.route -> BottomBarDestination.ARCHIVE
        else -> BottomBarDestination.INBOX
    }

    // Modal sheet states
    var showSaveSheet by remember { mutableStateOf(false) }
    var selectedLinkForDetails by remember { mutableStateOf<SavedLink?>(null) }
    val saveSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }

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
                    .padding(bottom = if (currentRoute != LaterboxDestination.Settings.route) 84.dp else 16.dp)
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
                        }
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

                // Settings Screen
                composable(LaterboxDestination.Settings.route) {
                    val preferences by settingsViewModel.preferences.collectAsState()
                    SettingsScreen(
                        preferences = preferences,
                        onThemeModeChange = settingsViewModel::setThemeMode,
                        onUserNameChange = settingsViewModel::setUserName,
                        onAutoMarkAsReadChange = settingsViewModel::setAutoMarkAsRead,
                        onAllowSystemBackupChange = settingsViewModel::setAllowSystemBackup,
                        onFontSizeScaleChange = settingsViewModel::setFontSizeScale,
                        onSmartRemindersEnabledChange = settingsViewModel::setSmartRemindersEnabled,
                        onAiEngineChange = settingsViewModel::setAiEngine,
                        onSendTestSmartDigest = settingsViewModel::sendTestSmartDigest,
                        onExportBackup = settingsViewModel::exportBackup,
                        onImportBackup = settingsViewModel::importBackup,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            if (currentRoute != LaterboxDestination.Settings.route) {
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
                saveLinkViewModel.onSave {
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
                savedItemDetailViewModel.deleteWithUndo { deletedLink ->
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
}
