package team.sakhi.android.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.repositories.SanityFaq
import team.sakhi.repositories.SanityLegalPage
import team.sakhi.repositories.SanityPortableTextBlock

/**
 * Generic renderer for any [ContentPageId]. Legal/About/Help sub-pages first try
 * the real shared Sanity-backed content and fall back instantly to the static
 * `ContentLibrary` port, matching iOS's `SanityLegalPageView`/`SanityFAQView`
 * behavior. There is intentionally no loading state here.
 */
@Composable
fun ContentPageScreen(
    pageId: ContentPageId,
    onBack: () -> Unit,
    contentViewModel: SanityContentViewModel = koinViewModel(),
) {
    val page = ContentLibrary.page(for_ = pageId)
    val uiState by contentViewModel.uiState.collectAsState()
    val languageCode = Locale.getDefault().language.lowercase(Locale.ROOT)
    val livePage = pageId.sanitySlug?.let(uiState.legalPages::get)

    DetailSheetScaffold(
        title = null,
        onBack = onBack,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(SakhiSpacing.space5),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            Text(
                text = page.heading,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = page.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                pageId == ContentPageId.FAQ && uiState.faqs.isNotEmpty() -> {
                    LiveFaqContent(faqs = uiState.faqs, languageCode = languageCode)
                }
                livePage != null && !livePage.body?.blocks(languageCode).isNullOrEmpty() -> {
                    LiveSanityPageContent(page = livePage, languageCode = languageCode)
                }
                else -> {
                    StaticContentPageBody(page = page)
                }
            }
        }
    }
}

@Composable
private fun StaticContentPageBody(page: ContentPage) {
    page.sections.forEach { section ->
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
            Text(
                text = section.title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (section.style == ContentSectionStyle.CARDS) {
                section.items.forEach { item ->
                    ContentCard(item)
                }
            } else {
                section.items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1)) {
                        if (item.title.isNotBlank()) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        Text(
                            text = item.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSanityPageContent(page: SanityLegalPage, languageCode: String) {
    page.lastUpdated.formattedCmsDate()?.let { formattedDate ->
        Text(
            text = "Last updated $formattedDate",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    PortableTextContent(blocks = page.body?.blocks(languageCode).orEmpty())
}

@Composable
private fun PortableTextContent(blocks: List<SanityPortableTextBlock>) {
    var currentList = mutableListOf<SanityPortableTextBlock>()
    val groupedBlocks = buildList {
        blocks.forEach { block ->
            if (block.listItem != null) {
                currentList.add(block)
            } else {
                if (currentList.isNotEmpty()) {
                    add(currentList.toList())
                    currentList = mutableListOf()
                }
                add(listOf(block))
            }
        }
        if (currentList.isNotEmpty()) add(currentList.toList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4)) {
        groupedBlocks.forEach { group ->
            if (group.firstOrNull()?.listItem != null) {
                BulletList(group)
            } else {
                group.firstOrNull()?.let { block ->
                    PortableTextBlockView(block = block)
                }
            }
        }
    }
}

@Composable
private fun PortableTextBlockView(block: SanityPortableTextBlock) {
    when (block.style) {
        "h2" -> Text(
            text = block.plainText,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        "h3" -> Text(
            text = block.plainText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        "blockquote" -> {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(SakhiRadius.full),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth(0.01f),
                ) {}
                Text(
                    text = block.plainText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> Text(
            text = block.asAnnotatedString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BulletList(blocks: List<SanityPortableTextBlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        blocks.forEach { block ->
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = block.plainText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveFaqContent(faqs: List<SanityFaq>, languageCode: String) {
    var expandedId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        faqs.forEach { faq ->
            val faqId = faq.stableId.ifBlank { faq.question.localized(languageCode) }
            Surface(
                shape = RoundedCornerShape(SakhiRadius.lg),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedId = if (expandedId == faqId) null else faqId }
                            .padding(SakhiSpacing.space4),
                    ) {
                        Text(
                            text = faq.question.localized(languageCode),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (expandedId == faqId) FontWeight.Bold else FontWeight.Normal,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (expandedId == faqId) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = if (expandedId == faqId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(start = SakhiSpacing.space3),
                        )
                    }
                    if (expandedId == faqId) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = SakhiSpacing.space4))
                        Text(
                            text = faq.answer.localized(languageCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(SakhiSpacing.space4),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentCard(item: ContentItem) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ContentPageId.sanitySlug: String?
    get() = when (this) {
        ContentPageId.PRIVACY_POLICY -> "privacy-policy"
        ContentPageId.TERMS_OF_SERVICE -> "terms-of-service"
        ContentPageId.CODE_OF_CONDUCT -> "code-of-conduct"
        ContentPageId.COOKIE_POLICY -> "cookie-policy"
        ContentPageId.DATA_USAGE -> "data-usage-policy"
        ContentPageId.GDPR_RIGHTS -> "gdpr-rights"
        ContentPageId.SAFETY_GUIDELINES -> "safety-guidelines"
        ContentPageId.ABOUT_US -> "about-us"
        ContentPageId.CREDITS -> "team"
        else -> null
    }

internal fun liveSanitySlugs(): List<String> =
    ContentPageId.entries.mapNotNull { it.sanitySlug }.distinct()

private fun String?.formattedCmsDate(): String? {
    if (this.isNullOrBlank()) return null
    return runCatching {
        LocalDate.parse(this).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }.getOrElse { this }
}

private fun SanityPortableTextBlock.asAnnotatedString() = buildAnnotatedString {
    children.forEach { span ->
        val styles = buildList {
            if ("strong" in span.marks) add(SpanStyle(fontWeight = FontWeight.Bold))
            if ("underline" in span.marks) add(SpanStyle(textDecoration = TextDecoration.Underline))
        }
        val text = span.text.orEmpty()
        if (styles.isEmpty()) {
            append(text)
        } else {
            val start = length
            append(text)
            styles.forEach { addStyle(it, start, length) }
        }
    }
}
