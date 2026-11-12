package com.laterbox.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.theme.LaterboxTheme

/**
 * Unified "More (⋮)" actions menu for saved links, accessible via the ⋮ button
 * and triggered by long-pressing any link card in the inbox and archive.
 */
@Composable
fun LinkQuickActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    link: SavedLink,
    onToggleRead: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        // Mark as read / unread
        DropdownMenuItem(
            text = {
                Text(
                    text = if (link.isRead) "Mark as unread" else "Mark as read",
                    style = LaterboxTheme.typography.bodyPrimary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(
                        id = if (link.isRead) LaterboxIcons.Refresh else LaterboxIcons.Check
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LaterboxTheme.colors.textSecondary
                )
            },
            onClick = {
                onDismissRequest()
                onToggleRead()
            }
        )

        // Copy link
        DropdownMenuItem(
            text = {
                Text(
                    text = "Copy link",
                    style = LaterboxTheme.typography.bodyPrimary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LaterboxTheme.colors.textSecondary
                )
            },
            onClick = {
                onDismissRequest()
                onCopyLink()
            }
        )

        // Share
        DropdownMenuItem(
            text = {
                Text(
                    text = "Share",
                    style = LaterboxTheme.typography.bodyPrimary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Share),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LaterboxTheme.colors.textSecondary
                )
            },
            onClick = {
                onDismissRequest()
                onShareLink()
            }
        )

        // Archive / Unarchive
        DropdownMenuItem(
            text = {
                Text(
                    text = if (link.isArchived) "Unarchive" else "Archive",
                    style = LaterboxTheme.typography.bodyPrimary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Archive),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LaterboxTheme.colors.textSecondary
                )
            },
            onClick = {
                onDismissRequest()
                onArchive()
            }
        )

        // Delete (Destructive)
        DropdownMenuItem(
            text = {
                Text(
                    text = "Delete",
                    style = LaterboxTheme.typography.bodyPrimary,
                    fontSize = 14.sp,
                    color = LaterboxTheme.colors.statusDanger
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Delete),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LaterboxTheme.colors.statusDanger
                )
            },
            onClick = {
                onDismissRequest()
                onDelete()
            }
        )
    }
}
