package com.laterbox.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.data.local.entity.ChatConversationEntity
import com.laterbox.app.ui.navigation.LaterboxDestination
import com.laterbox.app.ui.theme.LaterboxTheme

enum class DrawerPrimarySection {
    INBOX,
    COLLECTIONS,
    DOWNLOADS,
    ARCHIVE,
    NOTIFICATIONS,
    SETTINGS,
    CHAT
}

@Composable
fun LaterboxModalDrawerSheet(
    currentSection: DrawerPrimarySection,
    activeConversationId: String?,
    recentConversations: List<ChatConversationEntity>,
    unreadNotificationCount: Int,
    userName: String,
    onNavigateInbox: () -> Unit,
    onNavigateCollections: () -> Unit,
    onNavigateDownloads: () -> Unit,
    onNavigateArchive: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNewChatClick: () -> Unit,
    onConversationClick: (ChatConversationEntity) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    ModalDrawerSheet(
        modifier = modifier.width(304.dp),
        drawerContainerColor = colors.backgroundPrimary,
        drawerContentColor = colors.textPrimary
    ) {
        LaterboxDrawerContent(
            currentSection = currentSection,
            activeConversationId = activeConversationId,
            recentConversations = recentConversations,
            unreadNotificationCount = unreadNotificationCount,
            userName = userName,
            onNavigateInbox = onNavigateInbox,
            onNavigateCollections = onNavigateCollections,
            onNavigateDownloads = onNavigateDownloads,
            onNavigateArchive = onNavigateArchive,
            onNavigateNotifications = onNavigateNotifications,
            onNavigateSettings = onNavigateSettings,
            onNewChatClick = onNewChatClick,
            onConversationClick = onConversationClick,
            onDeleteConversation = onDeleteConversation
        )
    }
}

@Composable
fun LaterboxPersistentSidePanel(
    currentSection: DrawerPrimarySection,
    activeConversationId: String?,
    recentConversations: List<ChatConversationEntity>,
    unreadNotificationCount: Int,
    userName: String,
    onNavigateInbox: () -> Unit,
    onNavigateCollections: () -> Unit,
    onNavigateDownloads: () -> Unit,
    onNavigateArchive: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNewChatClick: () -> Unit,
    onConversationClick: (ChatConversationEntity) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing
    Surface(
        modifier = modifier
            .width(276.dp)
            .fillMaxHeight(),
        color = colors.backgroundPrimary
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            LaterboxDrawerContent(
                currentSection = currentSection,
                activeConversationId = activeConversationId,
                recentConversations = recentConversations,
                unreadNotificationCount = unreadNotificationCount,
                userName = userName,
                onNavigateInbox = onNavigateInbox,
                onNavigateCollections = onNavigateCollections,
                onNavigateDownloads = onNavigateDownloads,
                onNavigateArchive = onNavigateArchive,
                onNavigateNotifications = onNavigateNotifications,
                onNavigateSettings = onNavigateSettings,
                onNewChatClick = onNewChatClick,
                onConversationClick = onConversationClick,
                onDeleteConversation = onDeleteConversation,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(spacing.hairline)
                    .fillMaxHeight()
                    .background(colors.borderSubtle)
            )
        }
    }
}

@Composable
fun LaterboxDrawerContent(
    currentSection: DrawerPrimarySection,
    activeConversationId: String?,
    recentConversations: List<ChatConversationEntity>,
    unreadNotificationCount: Int,
    userName: String,
    onNavigateInbox: () -> Unit,
    onNavigateCollections: () -> Unit,
    onNavigateDownloads: () -> Unit,
    onNavigateArchive: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNewChatClick: () -> Unit,
    onConversationClick: (ChatConversationEntity) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    var conversationPendingDelete by remember { mutableStateOf<ChatConversationEntity?>(null) }

    conversationPendingDelete?.let { conv ->
        LaterboxConfirmDialog(
            title = "Delete conversation?",
            message = "\"${conv.title}\" will be removed from this device.",
            confirmText = "Delete",
            onConfirm = {
                conversationPendingDelete = null
                onDeleteConversation(conv.id)
            },
            onDismiss = { conversationPendingDelete = null }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .testTag("laterbox_drawer_list"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "drawer_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.sm)
            ) {
                Text(
                    text = "Laterbox",
                    style = typography.titleLarge,
                    color = colors.textPrimary
                )
                Text(
                    text = if (userName.isNotBlank()) "Welcome back, $userName" else "Your calm reading library",
                    style = typography.metaText,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(spacing.xs))
        }

        item(key = "drawer_new_chat") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LaterboxShapeTokens.Button)
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Button)
                    .clickable(role = Role.Button, onClick = onNewChatClick)
                    .padding(horizontal = spacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Chat),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.accentPrimary
                )
                Text(
                    text = "New chat",
                    style = typography.uiMedium.copy(fontSize = 14.sp),
                    color = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(spacing.md))
        }

        item(key = "drawer_nav_items") {
            Column {
                DrawerNavItem(
                    label = "Inbox",
                    iconRes = LaterboxIcons.Inbox,
                    selected = currentSection == DrawerPrimarySection.INBOX,
                    onClick = onNavigateInbox
                )
                DrawerNavItem(
                    label = "Collections",
                    iconRes = LaterboxIcons.Folder,
                    selected = currentSection == DrawerPrimarySection.COLLECTIONS,
                    onClick = onNavigateCollections
                )
                DrawerNavItem(
                    label = "Downloads",
                    iconRes = LaterboxIcons.Download,
                    selected = currentSection == DrawerPrimarySection.DOWNLOADS,
                    onClick = onNavigateDownloads
                )
                DrawerNavItem(
                    label = "Archive",
                    iconRes = LaterboxIcons.Archive,
                    selected = currentSection == DrawerPrimarySection.ARCHIVE,
                    onClick = onNavigateArchive
                )
                DrawerNavItem(
                    label = "Notifications",
                    iconRes = LaterboxIcons.Bell,
                    selected = currentSection == DrawerPrimarySection.NOTIFICATIONS,
                    badgeCount = unreadNotificationCount,
                    onClick = onNavigateNotifications
                )
                DrawerNavItem(
                    label = "Settings",
                    iconRes = LaterboxIcons.Settings,
                    selected = currentSection == DrawerPrimarySection.SETTINGS,
                    onClick = onNavigateSettings
                )
            }
        }

        item(key = "drawer_conversations_header") {
            Spacer(modifier = Modifier.height(spacing.md))
            HorizontalDivider(thickness = spacing.hairline, color = colors.borderSubtle)
            Spacer(modifier = Modifier.height(spacing.md))
            Text(
                text = "Recent conversations",
                style = typography.metaText.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.sm)
            )
            Spacer(modifier = Modifier.height(spacing.xs))
        }

        if (recentConversations.isEmpty()) {
            item(key = "drawer_conversations_empty") {
                Text(
                    text = "No conversations yet. Message the assistant from Inbox or tap New chat.",
                    style = typography.bodySecondary.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs)
                )
            }
        } else {
            items(recentConversations, key = { it.id }) { conversation ->
                val isSelected = currentSection == DrawerPrimarySection.CHAT && conversation.id == activeConversationId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.accentContainer else colors.backgroundPrimary)
                        .clickable(role = Role.Button) {
                            onConversationClick(conversation)
                        }
                        .padding(start = spacing.sm, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.title,
                        style = typography.bodySecondary.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isSelected) colors.accentPrimary else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Delete conversation ${conversation.title}",
                                onClick = { conversationPendingDelete = conversation }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = LaterboxIcons.Delete),
                            contentDescription = "Delete conversation",
                            modifier = Modifier.size(14.dp),
                            tint = colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerNavItem(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentContainer else colors.backgroundPrimary)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = if (selected) colors.accentPrimary else colors.textSecondary
        )
        Spacer(modifier = Modifier.width(spacing.md))
        Text(
            text = label,
            style = typography.uiMedium.copy(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) colors.accentPrimary else colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accentPrimary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    style = typography.metaText.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.onAccentPrimary
                )
            }
        }
    }
}

fun sectionForRoute(route: String?): DrawerPrimarySection {
    return when {
        route == LaterboxDestination.Collections.route || route?.startsWith("collection/") == true ->
            DrawerPrimarySection.COLLECTIONS
        route == LaterboxDestination.Downloads.route -> DrawerPrimarySection.DOWNLOADS
        route == LaterboxDestination.Archive.route -> DrawerPrimarySection.ARCHIVE
        route == LaterboxDestination.NotificationHistory.route -> DrawerPrimarySection.NOTIFICATIONS
        route?.startsWith("settings") == true -> DrawerPrimarySection.SETTINGS
        route?.startsWith("chat") == true -> DrawerPrimarySection.CHAT
        else -> DrawerPrimarySection.INBOX
    }
}
