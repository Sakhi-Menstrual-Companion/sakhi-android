package team.sakhi.android.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Registers Sakhi's custom lint checks. Discovered by lint through the
 * `Lint-Registry-v2` manifest attribute set in this module's jar task.
 */
class SakhiIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(
        DesignSystemDetector.ISSUE,
        HardcodedFontSizeDetector.ISSUE,
    )

    override val api: Int = CURRENT_API

    override val minApi: Int = 12

    override val vendor: Vendor = Vendor(
        vendorName = "Team Sakhi",
        feedbackUrl = "https://github.com/karankumar/sakhi",
    )
}
