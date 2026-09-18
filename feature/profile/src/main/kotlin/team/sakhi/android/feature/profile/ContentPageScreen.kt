package team.sakhi.android.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.repositories.SanityFaq
import team.sakhi.repositories.SanityLegalPage
import team.sakhi.repositories.SanityPortableTextBlock
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel

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
        title = page.headingResId?.let { stringResource(it) } ?: page.heading,
        subtitle = page.subtitleResId?.let { stringResource(it) } ?: page.subtitle,
        headerIcon = pageId.heroIcon(),
        onBack = onBack,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(SakhiSpacing.space5),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            when {
                pageId == ContentPageId.FAQ && uiState.faqs.isNotEmpty() -> {
                    LiveFaqContent(faqs = uiState.faqs, languageCode = languageCode)
                }
                livePage != null && !livePage.body?.blocks(languageCode).isNullOrEmpty() -> {
                    LiveSanityPageContent(page = livePage, languageCode = languageCode)
                }
                else -> {
                    StaticContentPageBody(pageId = pageId, page = page)
                }
            }
        }
    }
}

private fun ContentPageId.heroIcon(): ImageVector = when (this) {
    ContentPageId.PRIVACY_POLICY -> Icons.Filled.Lock
    ContentPageId.TERMS_OF_SERVICE -> Icons.Filled.Description
    ContentPageId.CODE_OF_CONDUCT -> Icons.Filled.PanTool
    ContentPageId.COOKIE_POLICY -> Icons.Filled.Public
    ContentPageId.DATA_USAGE -> Icons.Filled.BarChart
    ContentPageId.GDPR_RIGHTS -> Icons.Filled.Gavel
    ContentPageId.SAFETY_GUIDELINES -> Icons.Filled.VerifiedUser
    ContentPageId.FAQ -> Icons.AutoMirrored.Filled.Help
    ContentPageId.ABOUT_US -> Icons.Filled.Favorite
    ContentPageId.CREDITS -> Icons.Filled.Groups
    ContentPageId.OPEN_SOURCE_LICENSES -> Icons.Filled.Code
}

@Composable
private fun StaticContentPageBody(
    pageId: ContentPageId,
    page: ContentPage,
) {
    var cardSectionIndex = 0
    page.sections.forEach { section ->
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
            ContentSectionHeader(
                title = section.titleResId?.let { stringResource(it) } ?: section.title,
            )
            if (section.style == ContentSectionStyle.CARDS) {
                val sectionIcons = pageId.cardSectionSfIcons.getOrNull(cardSectionIndex).orEmpty()
                val badgeStyle = pageId.cardBadgeStyle()
                cardSectionIndex += 1
                section.items.forEachIndexed { index, item ->
                    ContentCard(
                        item = item,
                        iconSfSymbol = sectionIcons.getOrNull(index),
                        badgeStyle = badgeStyle,
                    )
                }
            } else {
                section.items.forEach { item ->
                    TextContentCard(item)
                }
            }
        }
    }
}

private data class ContentCardBadgeStyle(
    val containerSize: Int,
    val iconSize: Int,
    val cornerRadius: Int,
    val topPadding: Int,
    val titleSize: Int,
    val bodySize: Int,
)

private fun ContentPageId.cardBadgeStyle(): ContentCardBadgeStyle = when (this) {
    ContentPageId.OPEN_SOURCE_LICENSES -> ContentCardBadgeStyle(
        containerSize = 42,
        iconSize = 16,
        cornerRadius = 12,
        topPadding = 1,
        titleSize = 15,
        bodySize = 13,
    )
    else -> ContentCardBadgeStyle(
        containerSize = 36,
        iconSize = 14,
        cornerRadius = 8,
        topPadding = 2,
        titleSize = 14,
        bodySize = 13,
    )
}

private val ContentPageId.cardSectionSfIcons: List<List<String>>
    get() = when (this) {
        ContentPageId.PRIVACY_POLICY -> listOf(
            listOf("externaldrive.connected.to.line.below", "lock.fill", "key.fill", "checkmark.shield"),
            listOf("heart.text.square", "gear", "location.circle", "location.slash"),
            listOf("clock.arrow.circlepath", "trash.circle", "document.badge.clock", "envelope.fill"),
            listOf("globe.europe.africa", "globe.americas", "globe.asia.australia", "person.crop.circle.badge.minus"),
            listOf("eye.fill", "brain.head.profile", "shield.fill", "heart.circle.fill"),
        )
        ContentPageId.TERMS_OF_SERVICE -> listOf(
            listOf("person.crop.circle.badge.checkmark", "key.fill", "person.fill", "checkmark.seal.fill"),
            listOf("xmark.circle.fill", "cart.badge.minus", "exclamationmark.triangle.fill", "hand.raised.slash.fill"),
            listOf("person.crop.circle.fill", "lock.shield.fill", "arrow.triangle.2.circlepath", "trash.circle.fill"),
            listOf("cross.circle.fill", "waveform.path.ecg", "person.wave.2.fill", "phone.fill.arrow.up.right"),
            listOf("person.crop.circle.badge.xmark", "arrow.right.square.fill", "doc.badge.clock.fill", "envelope.fill"),
        )
        ContentPageId.CODE_OF_CONDUCT -> listOf(
            listOf("bubble.heart.fill", "ear.fill", "globe", "shield.lefthalf.filled"),
            listOf("checkmark.seal.fill", "cross.circle", "info.circle.fill", "doc.text.magnifyingglass"),
        )
        ContentPageId.COOKIE_POLICY -> listOf(
            listOf("gear.badge.checkmark", "chart.line.uptrend.xyaxis", "slider.horizontal.3", "lock.shield"),
            listOf("switch.2", "iphone", "hand.raised.fill", "envelope.fill"),
        )
        ContentPageId.DATA_USAGE -> listOf(
            listOf("heart.text.square.fill", "calendar.badge.checkmark", "shield.fill", "sparkles"),
            listOf("cross.case.fill", "shield.lefthalf.filled", "magnifyingglass.circle.fill", "checkmark.seal.fill"),
        )
        ContentPageId.GDPR_RIGHTS -> listOf(
            listOf("doc.text.magnifyingglass", "list.bullet.rectangle.fill", "arrow.triangle.branch", "envelope.fill"),
            listOf("pencil.circle.fill", "plus.circle.fill", "bolt.circle.fill", "heart.text.square.fill"),
            listOf("hand.raised.fill", "megaphone.fill", "chart.bar.xaxis", "envelope.circle.fill"),
            listOf("envelope.fill", "gearshape.fill", "clock.fill", "building.columns.fill"),
        )
        ContentPageId.SAFETY_GUIDELINES -> listOf(
            listOf("star.circle.fill", "location.fill", "mappin.and.ellipse", "brain.head.profile"),
            listOf("exclamationmark.triangle.fill", "clock.badge.exclamationmark.fill", "indianrupeesign.circle.fill", "questionmark.circle.fill"),
            listOf("flag.fill", "person.crop.circle.badge.xmark", "phone.fill.arrow.up.right", "hand.raised.circle.fill"),
            listOf("heart.circle.fill", "hand.raised.fill", "iphone.radiowaves.left.and.right", "person.3.sequence.fill"),
        )
        ContentPageId.FAQ -> listOf(
            listOf("calendar.badge.checkmark", "lock.shield.fill", "calendar.circle.fill"),
            listOf("star.circle.fill", "shield.fill", "flag.fill"),
            listOf("heart.circle.fill", "trash.circle.fill"),
        )
        ContentPageId.ABOUT_US -> listOf(
            listOf("lightbulb.fill", "person.3.sequence.fill", "megaphone.fill", "heart.text.square.fill"),
            listOf("link.circle.fill", "star.circle.fill", "lock.shield.fill", "sparkles"),
            listOf("heart.circle.fill", "arrow.up.right.circle.fill", "person.crop.circle.badge.plus", "globe.asia.australia.fill"),
        )
        ContentPageId.CREDITS -> listOf(
            listOf("person.fill", "person.fill"),
            listOf("graduationcap.circle.fill", "stethoscope", "person.crop.circle.badge.checkmark", "chevron.left.forwardslash.chevron.right"),
        )
        ContentPageId.OPEN_SOURCE_LICENSES -> listOf(
            listOf("icloud.fill", "server.rack", "shippingbox.fill"),
        )
    }

@Composable
private fun ContentSectionHeader(title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(SakhiRadius.full),
            modifier = Modifier
                .padding(top = 1.dp)
                .size(width = 3.dp, height = 14.dp),
        ) {}
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LiveSanityPageContent(page: SanityLegalPage, languageCode: String) {
    page.lastUpdated.formattedCmsDate()?.let { formattedDate ->
        Text(
            text = stringResource(R.string.profile_content_last_updated, formattedDate),
            style = MaterialTheme.typography.bodySmall,
            color = sakhiSecondaryLabel(),
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
            modifier = Modifier.padding(top = SakhiSpacing.space1),
        )
        "h3" -> Text(
            text = block.plainText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        "blockquote" -> {
            Row(
                modifier = Modifier
                    .padding(start = SakhiSpacing.space1)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(SakhiRadius.full),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .fillMaxHeight()
                        .width(3.dp),
                ) {}
                Text(
                    text = block.plainText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                )
            }
        }
        else -> Text(
            text = block.asAnnotatedString(),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )
    }
}

@Composable
private fun BulletList(blocks: List<SanityPortableTextBlock>) {
    Column(
        modifier = Modifier.padding(start = SakhiSpacing.space1),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
    ) {
        blocks.forEach { block ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(SakhiRadius.full),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(5.dp),
                ) {}
                Text(
                    text = block.plainText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
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
                color = sakhiSystemBackground(),
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
                                sakhiSecondaryLabel()
                            },
                            modifier = Modifier.padding(start = SakhiSpacing.space3),
                        )
                    }
                    if (expandedId == faqId) {
                        SakhiListDivider(modifier = Modifier.padding(horizontal = SakhiSpacing.space4))
                        Text(
                            text = faq.answer.localized(languageCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sakhiSecondaryLabel(),
                            modifier = Modifier.padding(SakhiSpacing.space4),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentCard(
    item: ContentItem,
    iconSfSymbol: String?,
    badgeStyle: ContentCardBadgeStyle,
) {
    Surface(
        color = sakhiSystemBackground(),
        shape = RoundedCornerShape(SakhiRadius.lg),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            val itemTitle = item.titleResId?.let { stringResource(it) } ?: item.title
            val itemBody = SakhiContact.resolve(item.bodyResId?.let { stringResource(it) } ?: item.body)
            iconSfSymbol?.let { symbol ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(badgeStyle.cornerRadius.dp),
                    modifier = Modifier
                        .padding(top = badgeStyle.topPadding.dp)
                        .size(badgeStyle.containerSize.dp),
                ) {
                    Icon(
                        imageVector = sfSymbolToMaterialIcon(symbol),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding((badgeStyle.containerSize - badgeStyle.iconSize).dp / 2)
                            .size(badgeStyle.iconSize.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                Text(
                    text = itemTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = badgeStyle.titleSize.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = itemBody,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = badgeStyle.bodySize.sp),
                    color = sakhiSecondaryLabel(),
                )
            }
        }
    }
}

private fun sfSymbolToMaterialIcon(symbol: String): ImageVector = when {
    symbol.contains("person.crop.circle.badge.plus") -> Icons.Filled.PersonAdd
    symbol.contains("person.crop.circle.badge.minus") || symbol.contains("person.crop.circle.badge.xmark") ->
        Icons.Filled.PersonRemove
    symbol.contains("person.crop.circle.badge.checkmark") -> Icons.Filled.HowToReg
    symbol.contains("checkmark.shield") -> Icons.Filled.VerifiedUser
    symbol.contains("lock.shield") -> Icons.Filled.Shield
    symbol.contains("lock") -> Icons.Filled.Lock
    symbol.contains("shield") -> Icons.Filled.Shield
    symbol.startsWith("heart.text.square") -> Icons.Filled.MonitorHeart
    symbol.contains("heart.circle") || symbol.contains("bubble.heart") -> Icons.Filled.Favorite
    symbol.contains("calendar") -> Icons.Filled.CalendarMonth
    symbol.contains("clock") || symbol.contains("document.badge.clock") || symbol.contains("doc.badge.clock") -> Icons.Filled.Schedule
    symbol.contains("envelope") -> Icons.Filled.Email
    symbol.contains("phone") -> Icons.Filled.Phone
    symbol.contains("globe") || symbol == "network" -> Icons.Filled.Public
    symbol.contains("person.3") -> Icons.Filled.Groups
    symbol.contains("person") -> Icons.Filled.Person
    symbol.contains("location") || symbol.contains("mappin") -> Icons.Filled.LocationOn
    symbol.contains("chart") -> Icons.Filled.BarChart
    symbol.contains("gear") || symbol.contains("switch") || symbol.contains("slider") || symbol == "iphone" -> Icons.Filled.Settings
    symbol.contains("sparkles") -> Icons.Filled.AutoAwesome
    symbol.contains("graduationcap") -> Icons.Filled.School
    symbol.contains("stethoscope") -> Icons.Filled.MedicalServices
    symbol.contains("cross.case") || symbol.contains("cross.circle") || symbol.contains("waveform") -> Icons.Filled.MonitorHeart
    symbol.contains("trash") -> Icons.Filled.Delete
    symbol.contains("pencil") -> Icons.Filled.Edit
    symbol.contains("plus") -> Icons.Filled.AddCircle
    symbol.contains("questionmark") -> Icons.AutoMirrored.Filled.Help
    symbol.contains("flag") -> Icons.Filled.Flag
    symbol.contains("megaphone") -> Icons.Filled.Campaign
    symbol.contains("link") -> Icons.Filled.Link
    symbol.contains("server") || symbol.contains("externaldrive") || symbol.contains("icloud") || symbol.contains("shippingbox") -> Icons.Filled.Storage
    symbol.startsWith("doc") || symbol.startsWith("document") || symbol.contains("list.bullet") -> Icons.Filled.Description
    symbol.contains("building") -> Icons.Filled.AccountBalance
    symbol.contains("indianrupeesign") -> Icons.Filled.CurrencyRupee
    symbol.contains("eye") -> Icons.Filled.Visibility
    symbol.contains("key") -> Icons.Filled.VpnKey
    symbol.contains("star") -> Icons.Filled.Star
    symbol.contains("bolt") -> Icons.Filled.Bolt
    symbol.contains("lightbulb") || symbol.contains("brain") -> Icons.Filled.Lightbulb
    symbol.contains("ear") || symbol.contains("info") -> Icons.Filled.Info
    symbol.contains("xmark") || symbol.contains("exclamationmark.triangle") -> Icons.Filled.Warning
    symbol.contains("arrow.up.right") || symbol.contains("arrow.right.square") -> Icons.AutoMirrored.Filled.ArrowForward
    symbol.contains("arrow.triangle") -> Icons.Filled.Sync
    symbol.contains("cart") -> Icons.Filled.ShoppingCart
    symbol.contains("checkmark") -> Icons.Filled.CheckCircle
    symbol.contains("hand.raised") -> Icons.Filled.PanTool
    symbol.contains("magnifyingglass") -> Icons.Filled.Search
    symbol.contains("forwardslash") -> Icons.Filled.Code
    else -> Icons.Filled.Star
}

@Composable
private fun TextContentCard(item: ContentItem) {
    Surface(
        color = sakhiSystemBackground(),
        shape = RoundedCornerShape(SakhiRadius.lg),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SakhiSpacing.space4)
                .padding(vertical = SakhiSpacing.space1),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            val itemTitle = item.titleResId?.let { stringResource(it) } ?: item.title
            val itemBody = SakhiContact.resolve(item.bodyResId?.let { stringResource(it) } ?: item.body)
            if (item.titleResId != null || itemTitle.isNotBlank()) {
                Text(
                    text = itemTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (item.bodyResId != null || itemBody.isNotBlank()) {
                Text(
                    text = itemBody,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = sakhiSecondaryLabel(),
                )
            }
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
