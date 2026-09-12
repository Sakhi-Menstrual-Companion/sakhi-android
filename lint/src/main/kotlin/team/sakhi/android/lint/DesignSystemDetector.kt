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
 * Flags raw Material3 composables where `:core:ui` already has the Sakhi equivalent.
 *
 * The point is not that Material3 is bad, it is that Sakhi's versions carry decisions the raw
 * ones do not: phase-aware colour, the glass treatment, the shared corner radii, and the
 * loading/disabled behaviour the product expects. When a screen reaches past them, those
 * decisions silently stop applying on that screen only, which is how a UI drifts one
 * component at a time.
 *
 * DELIBERATELY NARROW. Only components with a genuine one-to-one replacement in `:core:ui`
 * are listed. `Text`, `Icon`, `Row`, `Column` and friends are NOT flagged: there is no Sakhi
 * wrapper for them, so flagging them would produce hundreds of warnings with nowhere to go,
 * and a rule people learn to ignore protects nothing.
 */
class DesignSystemDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = METHOD_NAMES.keys.toList()

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod,
    ) {
        val name = node.methodName ?: return
        val replacement = METHOD_NAMES[name] ?: return

        // Only complain when the call really is the Material one. Sakhi's own wrappers
        // legitimately call through to Material internally, and flagging :core:ui itself
        // would make the rule impossible to satisfy.
        val receiverPackage = method.containingClass?.qualifiedName.orEmpty()
        if (!receiverPackage.startsWith("androidx.compose.material")) return

        context.report(
            issue = ISSUE,
            location = context.getLocation(node),
            message = "Use `$replacement` from `:core:ui` instead of Material's `$name`",
            quickfixData = LintFix.create()
                .name("Replace with $replacement")
                .replace()
                .text(name)
                .with(replacement)
                .build(),
        )
    }

    companion object {
        /**
         * Only pairs where `:core:ui` genuinely has the equivalent. Verified against the
         * files in core/ui/src/main/kotlin/team/sakhi/android/ui/ rather than guessed.
         */
        val METHOD_NAMES = mapOf(
            "Button" to "PrimaryButton",
            "OutlinedButton" to "SecondaryButton",
            "TextButton" to "SecondaryButton",
            "Switch" to "SakhiSwitch",
            "TextField" to "SakhiTextField",
            "OutlinedTextField" to "SakhiTextField",
            "ModalBottomSheet" to "SakhiModalSheet",
            // Dividers are NOT here any more. They have their own rule, `SakhiDivider`
            // (DividerDetector), at ERROR rather than this rule's WARNING, because at
            // WARNING they drifted anyway. See that file.
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SakhiDesignSystem",
            briefDescription = "Raw Material component where a Sakhi one exists",
            explanation = """
                This call uses a Compose Material composable directly, bypassing the \
                equivalent in `:core:ui`. The Sakhi component carries the product's own \
                colour, shape and state behaviour; reaching past it means this one screen \
                quietly stops following the design system.

                If the Sakhi component genuinely cannot do what this screen needs, extend it \
                in `:core:ui` so every screen gets the improvement, rather than special-casing \
                here.
            """,
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                DesignSystemDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
