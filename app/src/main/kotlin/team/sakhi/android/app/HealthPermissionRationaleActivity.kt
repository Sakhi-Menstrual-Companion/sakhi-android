package team.sakhi.android.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Health Connect's "why does Sakhi want this?" screen: it opens the privacy policy and closes. */
class HealthPermissionRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
        }
        finish()
    }

    private companion object {
        const val PRIVACY_URL = "https://sakhi.rachna.co/privacy"
    }
}
