package com.laterbox.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ui.theme.InterFamily
import com.laterbox.app.ui.theme.JetBrainsMonoFamily
import com.laterbox.app.ui.theme.LaterboxColors
import com.laterbox.app.ui.theme.LaterboxTheme
import com.laterbox.app.ui.util.BrowserLauncher
import kotlinx.coroutines.delay
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.net.URI

private val MARKDOWN_EXTENSIONS = listOf(
    StrikethroughExtension.create(),
    TablesExtension.create(),
    AutolinkExtension.create()
)

private val COMMONMARK_PARSER: Parser = Parser.builder()
    .extensions(MARKDOWN_EXTENSIONS)
    .build()

/**
 * Parses [markdown] into a CommonMark AST safely, returning null if parsing fails on malformed
 * or extreme nesting inputs so callers can fall back cleanly to plain text.
 */
internal fun parseMarkdownDocumentSafely(markdown: String): Node? {
    if (markdown.isEmpty()) return null
    return runCatching { COMMONMARK_PARSER.parse(markdown) }.getOrNull()
}

/**
 * Validates that [rawUrl] uses an explicit `http` or `https` scheme with a non-blank host/authority.
 * Rejects `javascript:`, `file:`, `data:`, `intent:`, `content:`, etc.
 */
internal fun sanitizeMarkdownUrl(rawUrl: String?): String? {
    val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank() && uri.authority.isNullOrBlank()) return null
    return trimmed
}

@Immutable
private data class MarkdownPalette(
    val textPrimary: Color,
    val textSecondary: Color,
    val linkText: Color,
    val inlineCodeBackground: Color,
    val inlineCodeText: Color,
    val codeBlockBackground: Color,
    val codeBlockHeaderBackground: Color,
    val codeBlockBorder: Color,
    val codeBlockText: Color,
    val codeBlockMetaText: Color,
    val quoteBar: Color,
    val quoteBackground: Color,
    val divider: Color,
    val tableHeaderBackground: Color,
    val tableBorder: Color
)

@Composable
private fun rememberMarkdownPalette(
    colors: LaterboxColors,
    isUserMessage: Boolean
): MarkdownPalette {
    return remember(colors, isUserMessage) {
        if (isUserMessage) {
            MarkdownPalette(
                textPrimary = colors.onAccentPrimary,
                textSecondary = colors.onAccentPrimary.copy(alpha = 0.88f),
                linkText = colors.onAccentPrimary,
                inlineCodeBackground = Color.Black.copy(alpha = 0.20f),
                inlineCodeText = colors.onAccentPrimary,
                codeBlockBackground = Color.Black.copy(alpha = 0.24f),
                codeBlockHeaderBackground = Color.Black.copy(alpha = 0.14f),
                codeBlockBorder = colors.onAccentPrimary.copy(alpha = 0.24f),
                codeBlockText = colors.onAccentPrimary,
                codeBlockMetaText = colors.onAccentPrimary.copy(alpha = 0.82f),
                quoteBar = colors.onAccentPrimary.copy(alpha = 0.85f),
                quoteBackground = Color.Black.copy(alpha = 0.14f),
                divider = colors.onAccentPrimary.copy(alpha = 0.30f),
                tableHeaderBackground = Color.Black.copy(alpha = 0.16f),
                tableBorder = colors.onAccentPrimary.copy(alpha = 0.28f)
            )
        } else {
            MarkdownPalette(
                textPrimary = colors.textPrimary,
                textSecondary = colors.textSecondary,
                linkText = colors.accentPrimary,
                inlineCodeBackground = colors.surfaceSecondary,
                inlineCodeText = colors.textPrimary,
                codeBlockBackground = colors.surfaceSecondary,
                codeBlockHeaderBackground = if (colors.isDark) {
                    Color.White.copy(alpha = 0.04f)
                } else {
                    Color.Black.copy(alpha = 0.03f)
                },
                codeBlockBorder = colors.borderSubtle,
                codeBlockText = colors.textPrimary,
                codeBlockMetaText = colors.textSecondary,
                quoteBar = colors.accentPrimary.copy(alpha = 0.75f),
                quoteBackground = colors.surfaceSecondary.copy(alpha = 0.72f),
                divider = colors.borderSubtle,
                tableHeaderBackground = colors.surfaceSecondary,
                tableBorder = colors.borderSubtle
            )
        }
    }
}

/**
 * Renders CommonMark + GFM Markdown natively into Jetpack Compose UI inside Laterbox chat bubbles,
 * respecting user vs. assistant bubble styling, light/dark themes, streaming partial input,
 * safe HTTP/HTTPS-only link launching, and passive HTML suppression.
 */
@Composable
fun MarkdownText(
    markdown: String,
    isUserMessage: Boolean,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val context = LocalContext.current
    val palette = rememberMarkdownPalette(colors = colors, isUserMessage = isUserMessage)
    val baseBodyStyle = remember(typography, palette.textPrimary) {
        typography.bodyPrimary.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = palette.textPrimary
        )
    }

    val openSafeUrl: (String) -> Unit = remember(context, colors, onLinkClick) {
        { url ->
            val safe = sanitizeMarkdownUrl(url)
            if (safe != null) {
                if (onLinkClick != null) {
                    onLinkClick(safe)
                } else {
                    BrowserLauncher.openUrl(context, safe, colors)
                }
            }
        }
    }

    val document = remember(markdown) { parseMarkdownDocumentSafely(markdown) }
    if (document == null) {
        Text(
            text = markdown,
            style = baseBodyStyle,
            modifier = modifier
        )
        return
    }

    val topLevelNodes = remember(document) { document.childrenList() }
    if (topLevelNodes.isEmpty()) {
        Text(
            text = markdown,
            style = baseBodyStyle,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RenderBlockNodes(
            nodes = topLevelNodes,
            baseBodyStyle = baseBodyStyle,
            palette = palette,
            onOpenUrl = openSafeUrl,
            listDepth = 0
        )
    }
}

@Composable
private fun RenderBlockNodes(
    nodes: List<Node>,
    baseBodyStyle: TextStyle,
    palette: MarkdownPalette,
    onOpenUrl: (String) -> Unit,
    listDepth: Int
) {
    nodes.forEach { node ->
        when (node) {
            is Paragraph -> {
                val annotated = remember(node, palette, onOpenUrl) {
                    buildMarkdownAnnotatedString(node, palette, onOpenUrl)
                }
                if (annotated.text.isNotEmpty()) {
                    Text(
                        text = annotated,
                        style = baseBodyStyle
                    )
                }
            }

            is Heading -> {
                val headingStyle = remember(node.level, baseBodyStyle, palette) {
                    headingStyleForLevel(node.level, baseBodyStyle, palette)
                }
                val annotated = remember(node, palette, onOpenUrl) {
                    buildMarkdownAnnotatedString(node, palette, onOpenUrl)
                }
                if (annotated.text.isNotEmpty()) {
                    Text(
                        text = annotated,
                        style = headingStyle,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            is BulletList -> {
                RenderListBlock(
                    listBlock = node,
                    isOrdered = false,
                    startNumber = 1,
                    baseBodyStyle = baseBodyStyle,
                    palette = palette,
                    onOpenUrl = onOpenUrl,
                    listDepth = listDepth
                )
            }

            is OrderedList -> {
                val start = node.markerStartNumber ?: 1
                RenderListBlock(
                    listBlock = node,
                    isOrdered = true,
                    startNumber = start,
                    baseBodyStyle = baseBodyStyle,
                    palette = palette,
                    onOpenUrl = onOpenUrl,
                    listDepth = listDepth
                )
            }

            is FencedCodeBlock -> {
                MarkdownCodeBlock(
                    code = node.literal.removeSuffix("\n"),
                    language = node.info?.trim()?.takeIf { it.isNotBlank() },
                    palette = palette
                )
            }

            is IndentedCodeBlock -> {
                MarkdownCodeBlock(
                    code = node.literal.removeSuffix("\n"),
                    language = null,
                    palette = palette
                )
            }

            is BlockQuote -> {
                val quoteChildren = remember(node) { node.childrenList() }
                if (quoteChildren.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.quoteBackground)
                            .testTag("markdown_blockquote")
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(palette.quoteBar)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RenderBlockNodes(
                                nodes = quoteChildren,
                                baseBodyStyle = baseBodyStyle.copy(color = palette.textSecondary),
                                palette = palette,
                                onOpenUrl = onOpenUrl,
                                listDepth = listDepth
                            )
                        }
                    }
                }
            }

            is ThematicBreak -> {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("markdown_thematic_break"),
                    thickness = 1.dp,
                    color = palette.divider
                )
            }

            is TableBlock -> {
                MarkdownTable(
                    tableBlock = node,
                    baseBodyStyle = baseBodyStyle,
                    palette = palette,
                    onOpenUrl = onOpenUrl
                )
            }

            is HtmlBlock -> {
                // Raw HTML blocks are safely suppressed in chat bubbles.
            }

            else -> {
                val fallbackChildren = remember(node) { node.childrenList() }
                if (fallbackChildren.isNotEmpty()) {
                    RenderBlockNodes(
                        nodes = fallbackChildren,
                        baseBodyStyle = baseBodyStyle,
                        palette = palette,
                        onOpenUrl = onOpenUrl,
                        listDepth = listDepth
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderListBlock(
    listBlock: ListBlock,
    isOrdered: Boolean,
    startNumber: Int,
    baseBodyStyle: TextStyle,
    palette: MarkdownPalette,
    onOpenUrl: (String) -> Unit,
    listDepth: Int
) {
    val items = remember(listBlock) {
        listBlock.childrenList().filterIsInstance<ListItem>()
    }
    val itemSpacing = if (listBlock.isTight) 3.dp else 6.dp
    val bulletChar = when (listDepth % 3) {
        0 -> "•"
        1 -> "◦"
        else -> "▪"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        items.forEachIndexed { index, item ->
            val marker = if (isOrdered) "${startNumber + index}." else bulletChar
            val itemChildren = remember(item) { item.childrenList() }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = marker,
                    style = baseBodyStyle.copy(
                        fontWeight = if (isOrdered) FontWeight.Medium else FontWeight.Bold,
                        color = palette.textSecondary
                    ),
                    modifier = Modifier
                        .widthIn(min = if (isOrdered) 24.dp else 16.dp)
                        .padding(end = 6.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderBlockNodes(
                        nodes = itemChildren,
                        baseBodyStyle = baseBodyStyle,
                        palette = palette,
                        onOpenUrl = onOpenUrl,
                        listDepth = listDepth + 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(
    code: String,
    language: String?,
    palette: MarkdownPalette
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("markdown_code_block"),
        shape = RoundedCornerShape(10.dp),
        color = palette.codeBlockBackground,
        border = BorderStroke(1.dp, palette.codeBlockBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.codeBlockHeaderBackground)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language ?: "code",
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = palette.codeBlockMetaText
                    )
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClickLabel = "Copy code") {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("markdown_copy_code_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (copied) LaterboxIcons.Check else LaterboxIcons.Copy
                        ),
                        contentDescription = if (copied) "Copied" else "Copy code",
                        modifier = Modifier.size(12.dp),
                        tint = palette.codeBlockMetaText
                    )
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        style = TextStyle(
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = palette.codeBlockMetaText
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = code,
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = palette.codeBlockText
                    ),
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    tableBlock: TableBlock,
    baseBodyStyle: TextStyle,
    palette: MarkdownPalette,
    onOpenUrl: (String) -> Unit
) {
    val rows = remember(tableBlock) {
        buildList {
            tableBlock.childrenList().forEach { section ->
                when (section) {
                    is TableHead -> {
                        section.childrenList().filterIsInstance<TableRow>().forEach { row ->
                            add(TableRenderRow(isHeader = true, cells = row.childrenList().filterIsInstance<TableCell>()))
                        }
                    }
                    is TableBody -> {
                        section.childrenList().filterIsInstance<TableRow>().forEach { row ->
                            add(TableRenderRow(isHeader = false, cells = row.childrenList().filterIsInstance<TableCell>()))
                        }
                    }
                }
            }
        }
    }
    if (rows.isEmpty()) return

    val columnCount = rows.maxOfOrNull { it.cells.size } ?: 0
    if (columnCount == 0) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, palette.tableBorder, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .testTag("markdown_table")
    ) {
        Column {
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .background(if (row.isHeader) palette.tableHeaderBackground else Color.Transparent)
                ) {
                    for (colIdx in 0 until columnCount) {
                        val cell = row.cells.getOrNull(colIdx)
                        val cellAnnotated = remember(cell, palette) {
                            if (cell != null) buildMarkdownAnnotatedString(cell, palette, onOpenUrl)
                            else AnnotatedString("")
                        }
                        Text(
                            text = cellAnnotated,
                            style = baseBodyStyle.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (row.isHeader) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            modifier = Modifier
                                .widthIn(min = 72.dp, max = 180.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = palette.tableBorder)
                }
            }
        }
    }
}

private data class TableRenderRow(
    val isHeader: Boolean,
    val cells: List<TableCell>
)

private fun headingStyleForLevel(
    level: Int,
    baseBodyStyle: TextStyle,
    palette: MarkdownPalette
): TextStyle {
    return when (level) {
        1 -> baseBodyStyle.copy(
            fontSize = 19.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        )
        2 -> baseBodyStyle.copy(
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.15).sp
        )
        3 -> baseBodyStyle.copy(
            fontSize = 15.5.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold
        )
        4 -> baseBodyStyle.copy(
            fontSize = 14.5.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        5 -> baseBodyStyle.copy(
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold
        )
        else -> baseBodyStyle.copy(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary
        )
    }
}

private fun buildMarkdownAnnotatedString(
    parent: Node,
    palette: MarkdownPalette,
    onOpenUrl: (String) -> Unit
): AnnotatedString = buildAnnotatedString {
    appendInlineChildren(parent, palette, onOpenUrl)
}

private fun AnnotatedString.Builder.appendInlineChildren(
    parent: Node,
    palette: MarkdownPalette,
    onOpenUrl: (String) -> Unit
) {
    var child = parent.firstChild
    while (child != null) {
        when (child) {
            is Text -> append(child.literal)

            is StrongEmphasis -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineChildren(child, palette, onOpenUrl)
                pop()
            }

            is Emphasis -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlineChildren(child, palette, onOpenUrl)
                pop()
            }

            is Strikethrough -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                appendInlineChildren(child, palette, onOpenUrl)
                pop()
            }

            is Code -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.5.sp,
                        background = palette.inlineCodeBackground,
                        color = palette.inlineCodeText
                    )
                )
                append(child.literal)
                pop()
            }

            is Link -> {
                val safeUrl = sanitizeMarkdownUrl(child.destination)
                if (safeUrl != null) {
                    val linkStyle = SpanStyle(
                        color = palette.linkText,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline
                    )
                    pushLink(
                        LinkAnnotation.Url(
                            url = safeUrl,
                            styles = TextLinkStyles(style = linkStyle),
                            linkInteractionListener = { onOpenUrl(safeUrl) }
                        )
                    )
                    appendInlineChildren(child, palette, onOpenUrl)
                    pop()
                } else {
                    // Unsafe schemes (javascript:, file:, etc.) are rendered passively as plain text.
                    appendInlineChildren(child, palette, onOpenUrl)
                }
            }

            is Image -> {
                // Render image alt text passively rather than fetching untrusted external images.
                val beforeLen = length
                appendInlineChildren(child, palette, onOpenUrl)
                if (length == beforeLen && !child.title.isNullOrBlank()) {
                    append(child.title)
                }
            }

            is SoftLineBreak -> append('\n')

            is HardLineBreak -> append('\n')

            is HtmlInline -> {
                val raw = child.literal?.trim().orEmpty()
                if (raw.equals("<br>", ignoreCase = true) ||
                    raw.equals("<br/>", ignoreCase = true) ||
                    raw.equals("<br />", ignoreCase = true)
                ) {
                    append('\n')
                }
                // Other inline HTML tags are suppressed cleanly.
            }

            else -> appendInlineChildren(child, palette, onOpenUrl)
        }
        child = child.next
    }
}

private fun Node.childrenList(): List<Node> {
    val result = ArrayList<Node>()
    var current = firstChild
    while (current != null) {
        result.add(current)
        current = current.next
    }
    return result
}
