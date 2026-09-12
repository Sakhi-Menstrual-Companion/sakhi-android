package team.sakhi.android.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

/**
 * Every divider in the app is `SakhiListDivider`. This makes that a build rule instead of a
 * habit.
 *
 * WHY AN ERROR, when the rest of the design-system checks are warnings: dividers were already
 * covered by `SakhiDesignSystem` at WARNING, and in practice that stopped nothing. By
 * 2026-09-12 the app had eighteen dividers drawn some other way (Material's default 1dp, a
 * 1dp `Box`, several 0.5dp lines) and five private wrappers (`RowDivider` twice,
 * `EmergencyRowDivider`, `IndentedDivider`, `LearningDivider`, `QuickLogMenuDivider`) each
 * re-wrapping the same line. Karan asked for one divider, used everywhere, and for "iss tarah
 * more components kabhi nhi banenge", i.e. for new ones to be impossible rather than merely
 * discouraged. A warning cannot do that; an error can.
 *
 * WHAT IT CATCHES: Material's `HorizontalDivider`, `VerticalDivider` and legacy `Divider`,
 * anywhere except `SakhiListDivider.kt`, which is the one place allowed to draw Material's line
 * because it IS the component.
 *
 * WHAT IT CANNOT CATCH: a hand-drawn line, e.g. `Box(Modifier.height(1.dp).background(...))`.
 * There is no reliable way to tell that apart from any other thin box. `SakhiListDivider`'s
 * own documentation says so, and code review has to cover it.
 *
 * If a screen genuinely needs something the component cannot do (a new colour, an inset on
 * both sides), extend `SakhiListDivider` in `:core:ui` so every screen gets it, rather than
 * suppressing this.
 */
class DividerDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = MATERIAL_DIVIDERS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod,
    ) {
        // Only Material's. A same-named composable of our own is not what this is about.
        val receiverPackage = method.containingClass?.qualifiedName.orEmpty()
        if (!receiverPackage.startsWith("androidx.compose.material")) return

        // The component itself has to call through to Material to draw the line.
        if (context.file.name == COMPONENT_FILE) return

        context.report(
            issue = ISSUE,
            location = context.getLocation(node),
            message = "Use `SakhiListDivider` from `:core:ui`. Every divider in Sakhi is that " +
                "one component; pass `startInset` or `color` instead of drawing a new line.",
            quickfixData = LintFix.create()
                .name("Replace with SakhiListDivider")
                .replace()
                .text(node.methodName ?: return)
                .with("SakhiListDivider")
                .build(),
        )
    }

    companion object {
        private val MATERIAL_DIVIDERS = listOf("HorizontalDivider", "VerticalDivider", "Divider")
        private const val COMPONENT_FILE = "SakhiListDivider.kt"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SakhiDivider",
            briefDescription = "Divider drawn without SakhiListDivider",
            explanation = """
                Every divider in Sakhi is `SakhiListDivider` from `:core:ui`: one physical \
                pixel (`Dp.Hairline`) in the iOS separator colour. Material's divider is 1dp, \
                which is about three pixels on a typical phone, so each one drawn directly \
                reads visibly heavier than the rest of the app.

                Pass `startInset` to indent it, or `color` for a tinted surface. If the \
                component cannot do what you need, extend it rather than drawing your own.
            """,
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                DividerDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
