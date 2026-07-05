package team.sakhi.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
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
) {
    var query by remember { mutableStateOf("") }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = SakhiSpacing.space6),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space8, bottom = SakhiSpacing.space5),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Select your country",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SakhiTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search for a country...",
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                if (query.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { query = "" },
                    )
                }
            },
            modifier = Modifier.padding(bottom = SakhiSpacing.space2),
        )

        if (filtered.isEmpty()) {
            EmptyCountrySearchState(query = query)
        } else {
            LazyColumn(
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
                        HorizontalDivider(
                            modifier = Modifier.padding(start = SakhiSpacing.space6 + SakhiSpacing.space12 - SakhiSpacing.space1),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        )
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
                    text = "No results for \"$query\"",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                )
                Text(
                    text = "Try a different spelling or a dial code like +91",
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
    val dialColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

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
