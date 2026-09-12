package team.sakhi.android.feature.auth

import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.ui.CloseButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.sakhiPageBackgroundBrush
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.validation.PhoneCountry

/**
 * iOS parity note: the real iOS implementation is a searchable bottom sheet.
 * Android keeps the same content, copy, and row treatment here, but presents it
 * as a full screen for now until the shared sheet lane lands.
 */
@Composable
fun CountryPicker(
    selectedCountry: PhoneCountry? = null,
    onCountrySelected: (PhoneCountry) -> Unit,
    onDismiss: () -> Unit = {},
    asSheet: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val filtered = remember(query) {
        if (query.isBlank()) {
            PhoneCountry.all
        } else {
            val normalizedQuery = query.trim().lowercase()
            PhoneCountry.all.filter {
                it.name.lowercase().contains(normalizedQuery) ||
                    it.dialCode.contains(normalizedQuery)
            }
        }
    }
    LaunchedEffect(asSheet) {
        if (!asSheet) return@LaunchedEffect
        // Match iOS CountryPickerSheet's focusOnAppear behavior once the sheet lands.
        delay(150)
        searchFieldFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (asSheet) {
                    Modifier.heightIn(min = SakhiSpacing.space12 * 8, max = SakhiSpacing.space12 * 14)
                } else {
                    Modifier
                }
            )
            // iOS `CountryPickerSheet` ends in `.profileStaticPageBackground()`: flat
            // `DS.Colors.background` in light, the follicular gradient in dark.
            .background(sakhiPageBackgroundBrush())
            .padding(horizontal = SakhiSpacing.space6),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (asSheet) SakhiSpacing.space3 else SakhiSpacing.space8,
                    bottom = SakhiSpacing.space5,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.auth_country_picker_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            CloseButton(
                onClick = onDismiss,
                contentDescription = stringResource(R.string.auth_country_picker_dismiss),
            )
        }

        val searchFieldLabel = stringResource(R.string.auth_country_picker_search_placeholder)
        SakhiTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = searchFieldLabel,
            textFieldModifier = Modifier.focusRequester(searchFieldFocusRequester),
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    // iOS `CountryPickerSheet`: `Image(systemName: "magnifyingglass")
                    // .foregroundColor(DS.Colors.tertiaryLabel)`.
                    tint = sakhiTertiaryLabel(),
                )
            },
            trailingContent = {
                if (query.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.auth_country_picker_clear_search),
                        // iOS: `Image(systemName: "xmark.circle.fill")
                        // .foregroundColor(DS.Colors.tertiaryLabel)`.
                        tint = sakhiTertiaryLabel(),
                        modifier = Modifier.clickable { query = "" },
                    )
                }
            },
            // Placeholder text alone disappears from the accessibility tree once the
            // field has real input, same class of gap fixed on PhoneScreen's field above --
            // this keeps "Search for a country" announced by TalkBack persistently.
            modifier = Modifier
                .padding(bottom = SakhiSpacing.space2)
                .semantics { contentDescription = searchFieldLabel },
        )

        if (filtered.isEmpty()) {
            EmptyCountrySearchState(query = query)
        } else {
            LazyColumn(
                flingBehavior = rememberSakhiFlingBehavior(),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = filtered,
                    key = { _, country -> country.code },
                ) { index, country ->
                    CountryRow(
                        country = country,
                        isSelected = selectedCountry?.code == country.code,
                        onClick = { onCountrySelected(country) },
                    )
                    if (index < filtered.lastIndex) {
                        SakhiListDivider(startInset = SakhiSpacing.space6 + SakhiSpacing.space12 - SakhiSpacing.space1)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCountrySearchState(query: String) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SakhiSpacing.space6),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space5),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(SakhiSpacing.space10)
                    .clip(RoundedCornerShape(SakhiRadius.md))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                Text(
                    text = stringResource(R.string.auth_country_picker_no_results, query),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                )
                Text(
                    text = stringResource(R.string.auth_country_picker_try_again),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                )
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: PhoneCountry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val rowColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    // iOS: dial code is `isSelected ? DS.Colors.pink : DS.Colors.secondaryLabel`.
    // `onSurfaceVariant` is a slot this theme never sets, so it was Material's faintly
    // purple #49454F rather than iOS's neutral ink.
    val dialColor = if (isSelected) MaterialTheme.colorScheme.primary else sakhiSecondaryLabel()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = SakhiSpacing.space3 + SakhiSpacing.space1 / 4),
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = SakhiSpacing.space4)
                    .clip(RoundedCornerShape(SakhiRadius.xl))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = SakhiSpacing.space6,
                    end = SakhiSpacing.space8 - SakhiSpacing.space1,
                ),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3 + SakhiSpacing.space1 / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(SakhiSpacing.space12 - SakhiSpacing.space1)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = country.flag,
                    fontSize = SakhiFontSize.xxl,
                )
            }
            Text(
                text = country.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
                color = rowColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = country.dialCode,
                style = MaterialTheme.typography.bodyMedium,
                color = dialColor,
            )
        }
    }
}
