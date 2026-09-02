package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * iOS `PartnerAvatarCloud`: two overlapping circles, her on the left and the person she
 * invited on the right, with a small heart badge below.
 *
 * Lived privately inside the Care screen. The onboarding invite flow needed the same
 * thing and drew a share glyph in a circle instead, which is why that screen did not
 * look like the iOS one. One definition, both callers.
 */
@Composable
fun PartnerAvatarCloud(partnerName: String) {
    val partnerInitial = partnerName.take(1).uppercase()

    Box(
        modifier = Modifier.size(width = 196.dp, height = 144.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(116.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(116.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 13.dp, top = 13.dp)
                .size(90.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 13.dp, top = 13.dp)
                .size(90.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = partnerInitial,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-10).dp)
                .size(30.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

