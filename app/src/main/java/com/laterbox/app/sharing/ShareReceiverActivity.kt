package com.laterbox.app.sharing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.domain.util.SharedTextParser
import com.laterbox.app.domain.util.UrlNormalizer
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val parsed = parseIncomingIntent(intent)
        if (parsed == null) {
            finish()
            return
        }

        val app = application as LaterboxApplication
        val linkRepo = app.container.linkRepository
        val collectionRepo = app.container.collectionRepository

        setContent {
            val scope = rememberCoroutineScope()
            var title by remember { mutableStateOf(parsed.title ?: UrlNormalizer.extractDomain(parsed.url)) }
            var note by remember { mutableStateOf(parsed.note ?: "") }
            var duplicateLink by remember { mutableStateOf<SavedLink?>(null) }
            var isCheckingDuplicate by remember { mutableStateOf(true) }
            var isSaving by remember { mutableStateOf(false) }

            val domain = remember(parsed.url) { UrlNormalizer.extractDomain(parsed.url) }

            LaunchedEffect(parsed.url) {
                isCheckingDuplicate = true
                duplicateLink = linkRepo.findDuplicate(parsed.url)
                if (duplicateLink != null) {
                    title = duplicateLink!!.title
                    if (!duplicateLink!!.personalNote.isNullOrEmpty()) {
                        note = duplicateLink!!.personalNote!!
                    }
                }
                isCheckingDuplicate = false
            }

            ShareSheetOverlay(
                url = parsed.url,
                domain = domain,
                title = title,
                onTitleChange = { title = it },
                note = note,
                onNoteChange = { note = it },
                duplicateLink = duplicateLink,
                isCheckingDuplicate = isCheckingDuplicate,
                isSaving = isSaving,
                onDismiss = { finish() },
                onSave = {
                    if (!isSaving) {
                        isSaving = true
                        scope.launch {
                            val existing = duplicateLink
                            if (existing != null) {
                                // Update existing link
                                val updated = existing.copy(
                                    title = title,
                                    personalNote = note.ifBlank { null },
                                    isArchived = false // Restore to inbox if it was archived
                                )
                                linkRepo.updateLink(updated)
                                if (existing.metadataStatus != com.laterbox.app.domain.model.MetadataStatus.SUCCESS) {
                                    com.laterbox.app.data.metadata.MetadataSyncWorker.enqueue(applicationContext)
                                }
                            } else {
                                // Save new link (LinkRepository automatically enqueues MetadataSyncWorker and immediate fetch)
                                linkRepo.saveLink(
                                    originalUrl = parsed.url,
                                    title = title,
                                    personalNote = note.ifBlank { null }
                                )
                                com.laterbox.app.data.metadata.MetadataSyncWorker.enqueue(applicationContext)
                            }
                            finish()
                        }
                    }
                }
            )
        }
    }

    private fun parseIncomingIntent(intent: Intent?): ParsedContent? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null

        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        val parsed = SharedTextParser.parse(extraText, extraSubject) ?: return null
        return ParsedContent(parsed.url, parsed.title, parsed.note)
    }

    private data class ParsedContent(val url: String, val title: String?, val note: String?)
}

@Composable
private fun ShareSheetOverlay(
    url: String,
    domain: String,
    title: String,
    onTitleChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    duplicateLink: SavedLink?,
    isCheckingDuplicate: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    // Backdrop Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x77000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Sheet Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent dismiss */ }
                ),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color(0xFFFDFCF9),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // Header & Domain indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Save to Laterbox",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF191919)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = domain,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF66645E)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Duplicate notice if item already exists
                if (duplicateLink != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9EBE5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (duplicateLink.isArchived) {
                                "Already in your library (Archived). Saving will restore to Inbox."
                            } else {
                                "Already saved in your library."
                            },
                            fontSize = 13.sp,
                            color = Color(0xFFC85A32),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // URL preview
                Text(
                    text = url,
                    fontSize = 12.sp,
                    color = Color(0xFF94918A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Title input field
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF191919),
                        unfocusedBorderColor = Color(0xFFE6E4DD)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Personal Note input field
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Personal note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF191919),
                        unfocusedBorderColor = Color(0xFFE6E4DD)
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving
                    ) {
                        Text("Cancel", color = Color(0xFF66645E))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1.5f),
                        enabled = !isSaving && !isCheckingDuplicate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF191919),
                            contentColor = Color(0xFFFDFCF9)
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (duplicateLink != null) "Update Existing" else "Save to Inbox",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
