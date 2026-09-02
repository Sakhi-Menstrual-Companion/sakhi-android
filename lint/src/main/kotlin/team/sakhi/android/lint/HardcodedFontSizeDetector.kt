package team.sakhi.android.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.ULiteralExpression

/**
 * Flags a literal point size written straight into the UI, as in `fontSize = 15.sp`.
 *
 * This is not a style preference. Transcribing sizes off a mockup is what made the Emergency
 * screen render visibly oversized: the numbers on the mockup were for a different density and
 * a different base scale than the one the app actually renders at, so each screen that copied
 * them drifted independently and nothing tied them back together.
 *
 * `SakhiFontSize` (core/designsystem/SakhiDimens.kt) wraps the shared KMM `DesignTokens`
 * scale, which is the same scale iOS reads. A size taken from there stays in step with iOS
 * and moves everywhere at once when the scale changes. A literal does neither.
 *
 * Reported at INFORMATIONAL by default because there are 48 existing occurrences: the rule is
 * meant to stop NEW ones while the backlog is worked down, not to fail the build today. Raise
 * it to WARNING or ERROR in lint.xml once the count reaches zero.
 */
class HardcodedFontSizeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
            // Matches the `<number>.sp` shape: a numeric literal receiver with an `sp`
            // selector. Deliberately does not match `SakhiFontSize.base`, whose receiver is
            // an identifier rather than a literal, which is exactly the distinction the rule
            // is about.
            if (node.selector.asRenderString() != "sp") return

            val receiver = node.receiver
            if (receiver !is ULiteralExpression || receiver.value !is Number) return

            context.report(
                issue = ISSUE,
                location = context.getLocation(node as UExpression),
                message = "Hardcoded `${receiver.value}.sp`. Use a `SakhiFontSize` token so " +
                    "this stays in step with the shared scale (and with iOS)",
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SakhiHardcodedFontSize",
            briefDescription = "Hardcoded sp value instead of a design token",
            explanation = """
                A literal `sp` size was written into the UI instead of a token from \
                `SakhiFontSize`.

                Sizes copied off a mockup drift per screen and cannot be corrected in one \
                place. That is what made the Emergency screen render oversized. `SakhiFontSize` \
                wraps the shared KMM `DesignTokens` scale, so a size taken from it matches iOS \
                and moves everywhere at once.

                If no existing token fits, add one to the shared scale rather than writing the \
                number here.
            """,
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 6,
            // INFORMATIONAL while the existing 48 are worked down; see the class note.
            severity = Severity.INFORMATIONAL,
            implementation = Implementation(
                HardcodedFontSizeDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
