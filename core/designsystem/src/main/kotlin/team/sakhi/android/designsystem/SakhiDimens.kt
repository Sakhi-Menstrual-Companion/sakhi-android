package team.sakhi.android.designsystem

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.design.DesignTokens

/** Compose `Dp`/`TextUnit` wrappers over KMM `DesignTokens` — the 4dp spacing grid. */
object SakhiSpacing {
    val space1 = DesignTokens.SPACE_1.dp
    val space2 = DesignTokens.SPACE_2.dp
    val space3 = DesignTokens.SPACE_3.dp
    val space4 = DesignTokens.SPACE_4.dp
    val space5 = DesignTokens.SPACE_5.dp
    val space6 = DesignTokens.SPACE_6.dp
    val space8 = DesignTokens.SPACE_8.dp
    val space10 = DesignTokens.SPACE_10.dp
    val space12 = DesignTokens.SPACE_12.dp
    val space16 = DesignTokens.SPACE_16.dp
}

object SakhiRadius {
    val sm = DesignTokens.RADIUS_SM.dp
    val md = DesignTokens.RADIUS_MD.dp
    val lg = DesignTokens.RADIUS_LG.dp
    val xl = DesignTokens.RADIUS_XL.dp
    val xxl = DesignTokens.RADIUS_2XL.dp
    val full = DesignTokens.RADIUS_FULL.dp
    val bottomSheet = DesignTokens.RADIUS_BOTTOM_SHEET.dp
}

object SakhiFontSize {
    val xs = DesignTokens.FONT_XS.sp
    val sm = DesignTokens.FONT_SM.sp
    val base = DesignTokens.FONT_BASE.sp
    val lg = DesignTokens.FONT_LG.sp
    val xl = DesignTokens.FONT_XL.sp
    val xxl = DesignTokens.FONT_2XL.sp
    val xxxl = DesignTokens.FONT_3XL.sp
    val xxxxl = DesignTokens.FONT_4XL.sp
    val xxxxxl = DesignTokens.FONT_5XL.sp
}
