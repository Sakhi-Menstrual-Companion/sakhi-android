package team.sakhi.android.feature.profile

/**
 * Verbatim port of iOS `SakhiContentLibrary.swift`'s static fallback content.
 * This is still the authoritative fallback for Legal/About/Help pages even
 * after Android's Sanity CMS wiring landed: `ContentPageScreen` now tries the
 * shared `SanityRepository` first for legal pages / FAQ / team / about-us, then
 * falls back instantly to this same static body when CMS data is absent, empty,
 * or not configured.
 */
enum class ContentPageId {
    PRIVACY_POLICY, TERMS_OF_SERVICE, CODE_OF_CONDUCT,
    COOKIE_POLICY, DATA_USAGE, GDPR_RIGHTS,
    SAFETY_GUIDELINES, FAQ,
    ABOUT_US, CREDITS, OPEN_SOURCE_LICENSES,
}

enum class ContentSectionStyle { CARDS, TEXT }

data class ContentItem(
    val title: String,
    val body: String,
)

data class ContentSection(
    val title: String,
    val style: ContentSectionStyle = ContentSectionStyle.TEXT,
    val items: List<ContentItem>,
)

data class ContentPage(
    val heading: String,
    val subtitle: String,
    val sections: List<ContentSection>,
)

object ContentLibrary {

    fun page(for_: ContentPageId): ContentPage = when (for_) {
        ContentPageId.PRIVACY_POLICY -> privacyPolicy
        ContentPageId.TERMS_OF_SERVICE -> termsOfService
        ContentPageId.CODE_OF_CONDUCT -> codeOfConduct
        ContentPageId.COOKIE_POLICY -> cookiePolicy
        ContentPageId.DATA_USAGE -> dataUsage
        ContentPageId.GDPR_RIGHTS -> gdprRights
        ContentPageId.SAFETY_GUIDELINES -> safetyGuidelines
        ContentPageId.FAQ -> faq
        ContentPageId.ABOUT_US -> aboutUs
        ContentPageId.CREDITS -> credits
        ContentPageId.OPEN_SOURCE_LICENSES -> openSourceLicenses
    }

    private fun section(title: String, style: ContentSectionStyle = ContentSectionStyle.TEXT, items: List<ContentItem>) =
        ContentSection(title, style, items)

    private fun textItem(title: String, body: String) = ContentItem(title, body)
    private fun cardItem(title: String, body: String) = ContentItem(title, body)

    private val privacyPolicy = ContentPage(
        heading = "Your Privacy Matters",
        subtitle = "How we protect and handle your personal and health data at Sakhi",
        sections = listOf(
            section("Our Privacy Promise", items = listOf(
                textItem("Your Data is Sacred to Us", "At Sakhi, your privacy is our top priority. Your data is securely stored and remains entirely under your control. We are committed to maintaining the confidentiality and security of your personal information.\n\nYour personal data is never shared, sold, or distributed to third parties. We do not track your activities, and all data processing is designed for strict confidentiality. Only you can access your information. You have full control over your data and may request its deletion anytime."),
            )),
            section("Introduction to Our Privacy Policy", items = listOf(
                textItem("Welcome to Sakhi Privacy Protection", "We understand that your menstrual health data and personal information are deeply private. This Privacy Policy details how we collect, use, store, and protect your information when you use the Sakhi mobile application.\n\nSakhi is designed to provide menstrual cycle tracking, symptom logging, and care features. We take the security and privacy of your data seriously and have designed our systems with privacy as a priority.\n\nBy using Sakhi, you agree to the terms outlined in this Privacy Policy."),
            )),
            section("How We Secure Your Data", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Firebase Database Protection", "Your data in Firebase Realtime Database is protected with multiple layers of security as prescribed in applicable laws."),
                cardItem("Data Encryption", "All data is encrypted during transmission using HTTPS. Data at rest is encrypted on Google's servers. Database access requires secure authentication."),
                cardItem("Authentication Controls", "Access to your data requires valid authentication. Password policies enforce strong credential requirements. Device-based session management keeps you secure."),
                cardItem("Access Rules and Monitoring", "Strict Firebase security rules limit data access. Users can only access their own data. All database operations are logged and monitored."),
            )),
            section("What Personal Information We Collect", items = listOf(
                textItem("Account and Profile Information", "Personal Information We Collect:\n\n• Name: To personalize your experience\n• Email address: For account access and communications\n• Phone number: Required for app features\n• Date of birth: Helps with cycle tracking and predictions\n• Occupation: Optional for profile customization\n• Profile photo: Optional personal choice\n• Device information: To optimize app performance\n\nWe only collect what's necessary to provide you with the best Sakhi experience."),
            )),
            section("Health Data Collection", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Menstrual Health Data", "We collect menstrual cycle information including start and end dates, flow intensity levels, cycle length patterns, and symptoms like moods and cramps that you choose to log."),
                cardItem("Technical Information", "We gather device information, usage data on how you interact with features, and connection status."),
                cardItem("Location Data Safety", "Location data is only tracked when the emergency feature is actively being used. Your location is not continuously monitored."),
                cardItem("No Continuous Tracking", "We only track location when you actively use emergency features. We do not perform aggregate analysis across users."),
            )),
            section("How We Use Your Data", items = listOf(
                textItem("Core App Features and Analysis", "Using Your Data for App Features:\n\n• Period Tracking: Record cycle dates and flow levels\n• Cycle Predictions: Calculate future period dates based on your history\n• Symptom Logging: Track mood, cramps, and other symptoms\n• Cycle History: View and analyze your past cycles\n• Health Insights: Provide personalized cycle phase information\n\nAll predictions are calculated using standard mathematical formulas based on your historical data. All analysis is performed directly on your device."),
            )),
            section("How We Share Your Information", items = listOf(
                textItem("Strict Limits on Data Sharing", "We Strictly Limit Information Sharing:\n\n• Emergency Assistance: During emergencies, we share live location only with your designated trusted contacts when you actively trigger a request\n• Service Providers: We partner with Google Firebase for secure data storage and Firebase Cloud Messaging for notifications\n• Legal Requirements: We may share information only in response to valid legal requests\n\nWe never sell your data or share it for commercial purposes."),
            )),
            section("Data Storage and Deletion", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("How Long We Keep Your Data", "Account information is maintained as long as your account is active. Menstrual cycle history is stored to provide historical insights to you."),
                cardItem("Your Deletion Rights", "You can delete specific logs, cycle entries, or your entire account at any time."),
                cardItem("Complete Data Removal", "When you delete your account, all your personal data is permanently removed from our database within 30 days."),
                cardItem("Contact for Rights", "To exercise any privacy rights, please contact us at hello@getswipe.in."),
            )),
            section("Your Privacy Rights and Controls", items = listOf(
                textItem("Accessing and Controlling Your Data", "Your Complete Data Control Rights:\n\n• Accessing Your Data: View profile data in app settings or contact us for a comprehensive data report\n• Correcting Your Information: Edit profile information, modify or delete cycle entries and symptoms\n• Controlling Your Data: Adjust privacy settings, control notification settings, manage account controls\n\nYou have complete control over your personal information at all times."),
            )),
            section("Regional Privacy Rights", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("European Users (GDPR)", "You have the right to access, correct, and delete your data. You can restrict processing, object to certain uses, request data portability, and withdraw consent."),
                cardItem("California Residents (CCPA)", "You have the right to know what personal information is collected, delete personal information, and opt-out of data sale."),
                cardItem("Indian Users (DPDPA)", "You have rights to correction, erasure of personal data, and obtaining summary of processed data."),
                cardItem("Children's Privacy", "Our services are not intended for users under 13. If we discover data from a child under 13 was collected, we delete it immediately."),
            )),
            section("Our Promise to You", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Transparency in Everything", "This Privacy Policy is designed to be transparent about our data practices while ensuring the protection of your personal information."),
                cardItem("Your Right to Understand", "We believe in your right to understand how your data is handled and to maintain control over your personal information."),
                cardItem("Continuous Protection", "We are continuously committed to protecting your privacy and will always prioritize your data security."),
                cardItem("Building Trust Together", "Your trust in us to handle your most personal health data is something we never take for granted."),
            )),
        ),
    )

    private val termsOfService = ContentPage(
        heading = "Terms of Service",
        subtitle = "Understanding your rights and responsibilities when using Sakhi",
        sections = listOf(
            section("Agreement to Our Terms", items = listOf(
                textItem("Welcome to Sakhi", "Welcome to Sakhi! These Terms of Service govern your use of the Sakhi mobile application. By using Sakhi, you agree to be bound by these terms.\n\nSakhi is designed to help women track their menstrual cycles, log symptoms, and access health insights. We provide these services to support women's health and create a caring community.\n\nIf you do not agree with any part of these terms, please do not use our services. Your continued use of Sakhi means you accept these terms and any updates we make to them."),
            )),
            section("Who Can Use Sakhi", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Age Requirements", "You must be at least 13 years old to use Sakhi. Users under 18 should have parental consent. We do not knowingly collect data from children under 13."),
                cardItem("Account Responsibility", "You are responsible for maintaining the security of your account credentials. Do not share your login information with others. You are responsible for all activity under your account."),
                cardItem("One Account Per Person", "Each user may only maintain one account. Creating multiple accounts is prohibited and may result in suspension of all accounts."),
                cardItem("Honest Information", "You agree to provide accurate, current, and complete information when creating your account and using Sakhi's features."),
            )),
            section("How to Use Sakhi Properly", items = listOf(
                textItem("Using Sakhi Responsibly", "Using Sakhi Responsibly:\n\n• Use the app for its intended purposes: menstrual tracking and health monitoring\n• Provide help to community members genuinely and safely\n• Respect other users and treat everyone with kindness\n• Report inappropriate behavior or misuse of the platform\n• Keep your personal information and login details secure\n• Be honest when providing feedback"),
            )),
            section("What You Cannot Do on Sakhi", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Prohibited Activities", "Do not harass, bully, threaten, or intimidate other users. Abusive language, hate speech, and discriminatory conduct are strictly prohibited."),
                cardItem("No Commercial Use", "Sakhi is for personal, non-commercial use only. Advertising, solicitation, or commercial promotion of any kind is not permitted."),
                cardItem("False Information", "Do not post false, misleading, or inaccurate health information. Spreading misinformation that could harm other users is a serious violation."),
                cardItem("Illegal Activities", "You may not use Sakhi for any unlawful purpose, or to solicit others to perform or participate in any unlawful acts."),
            )),
            section("Your Privacy and Data", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Data Ownership", "You own your personal health data. Sakhi does not claim ownership of the data you enter into the app."),
                cardItem("Privacy Policy Agreement", "By using Sakhi, you agree to our Privacy Policy, which is incorporated into these Terms of Service by reference."),
                cardItem("Data Sharing", "We do not sell your data. We share information only as described in our Privacy Policy, for essential services and when legally required."),
                cardItem("Right to Delete", "You can delete your data and account at any time. Upon deletion, your data will be permanently removed within 30 days."),
            )),
            section("Sakhi's Services and Limitations", items = listOf(
                textItem("What Sakhi Provides", "Sakhi provides the following services:\n\n• Menstrual Cycle Tracking: Log and monitor your period dates and flow\n• Cycle Predictions: Data-driven estimates of future period dates\n• Community Assistance: Connect with other women for mutual support\n• Health Insights: Personalized information based on your cycle data\n• Secure Data Storage: Safe storage of your personal health information\n\nSakhi is continually evolving, and we may add, modify, or remove features at any time. We will notify you of significant changes through the app."),
            )),
            section("Important Disclaimers", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Not Medical Advice", "Sakhi is not a medical service. The information provided is for general wellness purposes only and does not constitute medical advice, diagnosis, or treatment."),
                cardItem("Prediction Accuracy", "Cycle predictions are estimates based on your logged data. They are not guaranteed to be accurate, especially for irregular cycles or those with health conditions."),
                cardItem("Community Assistance Limitations", "Community helpers are fellow Sakhi users, not trained medical professionals. Assistance provided through Sakhi should not replace professional medical care."),
                cardItem("Emergency Services", "Sakhi is not a substitute for emergency services. In life-threatening situations, always contact official emergency services immediately."),
            )),
            section("Account Suspension and Termination", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("When We May Suspend", "We may suspend or terminate accounts that violate these Terms, engage in harmful behavior, or are used for fraudulent or illegal purposes."),
                cardItem("Your Right to Terminate", "You may delete your account at any time through the app settings. Account termination is effective immediately."),
                cardItem("Effect of Termination", "Upon termination, your right to use Sakhi ends immediately. Your data will be deleted within 30 days as described in our Privacy Policy."),
                cardItem("Appeal Process", "If you believe your account was suspended in error, contact us at hello@getswipe.in to appeal the decision."),
            )),
            section("Changes and Contact", items = listOf(
                textItem("Staying Updated", "We may update these Terms of Service periodically. We will notify you of significant changes through the app. Continued use of Sakhi after changes means you accept the updated terms.\n\nFor questions about these Terms, contact us at hello@getswipe.in."),
            )),
        ),
    )

    private val codeOfConduct = ContentPage(
        heading = "Code of Conduct",
        subtitle = "Building a safe, respectful, and supportive environment for all Sakhi users",
        sections = listOf(
            section("Our Community Values", items = listOf(
                textItem("The Foundation of Sakhi", "Sakhi is built on the foundation of mutual respect, empathy, and support among women. Our community exists to help each other during menstrual health challenges and create a safe environment where every woman feels valued and protected.\n\nWe believe in treating every member with dignity, regardless of age, background, culture, or personal circumstances.\n\nBy participating in our community, you agree to uphold these values and contribute to an environment where women can seek and provide help without fear of judgment, harassment, or exploitation."),
            )),
            section("Respectful Communication Standards", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Speak with Kindness", "Use respectful, compassionate language in all interactions. Choose words that uplift and support rather than diminish or hurt."),
                cardItem("Listen and Understand", "Approach every interaction with empathy and a genuine desire to understand. Acknowledge different experiences and perspectives without judgment."),
                cardItem("Cultural Sensitivity", "Respect cultural differences in how women experience and discuss menstrual health. Avoid making assumptions based on cultural background."),
                cardItem("Professional Boundaries", "Maintain appropriate boundaries in all interactions. Keep conversations focused on providing helpful, relevant support."),
            )),
            section("Prohibited Behavior", items = listOf(
                textItem("Zero Tolerance for Harmful Conduct", "Zero Tolerance for Harmful Conduct:\n\n• Harassment, bullying, intimidation, or threats of any kind\n• Discrimination based on age, race, religion, sexual orientation, or disability\n• Sexual harassment, inappropriate advances, or sexually explicit content\n• Stalking or persistent unwanted contact\n• Sharing personal information of other users without consent\n• Impersonation or creating fake profiles\n• Spam, commercial solicitation, or promotional content\n• Encouraging or glorifying self-harm\n\nViolation will result in immediate account suspension and may be reported to authorities."),
            )),
            section("Privacy and Confidentiality", items = listOf(
                textItem("Privacy Protection Standards", "Privacy Protection Standards:\n\n• Respect the privacy of all community members and keep personal information confidential\n• Do not share, screenshot, or distribute private conversations\n• Only share information necessary for providing assistance\n• Use Sakhi's communication features rather than exchanging personal contact information\n• Delete any saved personal information after assistance is completed"),
            )),
            section("Content and Sharing Guidelines", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Appropriate Content Only", "Share only content that is relevant, helpful, and appropriate for a women's health community. Avoid content that is offensive, graphic, or unrelated to menstrual health support."),
                cardItem("No Medical Advice", "Do not present yourself as a medical professional or provide medical diagnoses. Share personal experiences, not medical prescriptions."),
                cardItem("Factual Information", "Share accurate information about menstrual health. Do not spread misinformation or unverified health claims that could harm other members."),
                cardItem("Intellectual Property Respect", "Only share content you have the right to share. Respect copyrights and do not distribute copyrighted material without permission."),
            )),
            section("Enforcement and Consequences", items = listOf(
                textItem("Code of Conduct Enforcement", "Code of Conduct Enforcement:\n\n• First violation: Warning and education about community standards\n• Minor repeated violations: Temporary restriction of account features\n• Serious violations: Temporary account suspension (7–30 days)\n• Severe violations: Immediate permanent account termination\n• Illegal activities: Report to appropriate law enforcement\n\nEnforcement aims to educate and correct behavior while being fair and proportionate."),
            )),
            section("Our Commitment to You", items = listOf(
                textItem("A Community Built on Trust", "This Code of Conduct exists to protect and empower every woman in our community. We are committed to enforcing these standards fairly and consistently while fostering an environment of mutual support and respect.\n\nWe believe that when women support each other with dignity and care, we create something powerful and transformative.\n\nThank you for being part of this important mission and for helping us create positive change in women's lives."),
            )),
        ),
    )

    private val cookiePolicy = ContentPage(
        heading = "Cookie Usage Policy",
        subtitle = "How Sakhi uses cookies and similar technologies to improve your experience",
        sections = listOf(
            section("What Are Cookies", items = listOf(
                textItem("Understanding Cookies", "Cookies are small text files stored on your device when you use mobile applications. They help us remember your preferences, keep you logged in, and improve your experience with Sakhi.\n\nSimilar technologies include local storage, session storage, and mobile app identifiers. We use these technologies responsibly and only collect information necessary to provide and improve our services."),
            )),
            section("Types of Cookies We Use", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Essential Cookies", "Required for the app to function properly. These include authentication tokens, session management cookies, and security identifiers. You cannot opt out of essential cookies."),
                cardItem("Performance Cookies", "Help us understand how you use the app by collecting anonymized usage statistics, crash reports, and performance metrics to improve reliability."),
                cardItem("Functional Cookies", "Remember your preferences and settings such as notification preferences, theme selection, and app customizations to personalize your experience."),
                cardItem("Security Cookies", "Protect your account from unauthorized access, detect fraudulent activity, and ensure secure communication between the app and our servers."),
            )),
            section("How We Use Cookies", items = listOf(
                textItem("Cookie Usage for Core Services", "Cookie Usage for Core Services:\n\n• Authentication: Keep you securely logged in across sessions\n• Security: Protect against fraud and unauthorized access\n• Preferences: Remember notification settings, theme, and app preferences\n• Data Sync: Ensure your health data is properly synchronized\n• Error Prevention: Help prevent data loss during app usage\n• Legal Compliance: Meet regulatory requirements for data protection"),
            )),
            section("Cookie Retention Periods", items = listOf(
                textItem("How Long We Keep Cookies", "How Long We Keep Cookies:\n\n• Session Cookies: Deleted when you close the app\n• Authentication Cookies: Retained until you log out (typically 30–90 days)\n• Preference Cookies: Stored until you change your settings or clear app data\n• Analytics Cookies: Retained for up to 2 years\n• Security Cookies: Kept for 6–12 months\n• Performance Cookies: Stored for up to 1 year"),
            )),
            section("Managing Your Cookie Preferences", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("In-App Cookie Controls", "Manage your cookie preferences through the app's Privacy Settings. You can enable or disable non-essential cookies while keeping essential functionality intact."),
                cardItem("Device-Level Controls", "You can clear all app data through your device's Settings app, which will remove all stored cookies and require you to log in again."),
                cardItem("Opt-Out Options", "You can opt out of performance and analytics cookies while continuing to use Sakhi's core features. Essential cookies cannot be disabled as the app requires them to function."),
                cardItem("Contact for Questions", "For questions about our cookie practices or to exercise your cookie-related rights, contact us at hello@getswipe.in."),
            )),
            section("International Cookie Compliance", items = listOf(
                textItem("Cookie Compliance by Region", "Cookie Compliance by Region:\n\n• European Union (GDPR): We obtain explicit consent for non-essential cookies\n• California (CCPA): We provide detailed information and allow opt-out\n• India (DPDPA): We comply with Indian data protection laws\n\nRegardless of your location, we are committed to transparent and responsible cookie usage."),
            )),
        ),
    )

    private val dataUsage = ContentPage(
        heading = "How We Use Your Data",
        subtitle = "Understanding how your information helps improve Sakhi and support women's health",
        sections = listOf(
            section("Our Data Usage Philosophy", items = listOf(
                textItem("Responsible Data Stewardship", "At Sakhi, we believe your health data can make a meaningful difference not just for you, but for women everywhere. We use your information responsibly to improve our services, advance women's health understanding, and create better experiences for our entire community.\n\nEvery piece of data you share helps us understand patterns, improve predictions, and develop features that truly serve women's needs. We approach this responsibility with the utmost care, ensuring your privacy is protected."),
            )),
            section("Core Service Data Usage", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Personalized Health Insights", "Your cycle data powers personalized insights about your health patterns, helping you understand your body better and plan around your cycle with greater confidence."),
                cardItem("Cycle Prediction Accuracy", "Historical cycle data is used to refine prediction algorithms, making forecasts increasingly accurate and personalized to your unique biological patterns over time."),
                cardItem("Safety and Security", "Usage patterns help us detect unusual activity, protect your account from unauthorized access, and maintain the integrity of your personal health data."),
                cardItem("Product Improvement", "Aggregated, anonymized usage data helps our team identify areas for improvement, prioritize new features, and eliminate pain points in the user experience."),
            )),
            section("Product Development and Improvement", items = listOf(
                textItem("How Your Data Drives Innovation", "How Your Data Drives Innovation:\n\n• Feature Usage Analysis: Understanding which features are most helpful to prioritize improvements\n• User Experience Optimization: Analyzing navigation patterns to make Sakhi more intuitive\n• Performance Improvement: Using technical data to optimize app speed and reliability\n• Bug Detection and Resolution: Identifying issues quickly for smoother experiences\n• Accessibility Enhancement: Understanding diverse user needs\n\nAll analysis is performed on aggregated, anonymized data to protect individual privacy."),
            )),
            section("Data Anonymization and Protection", items = listOf(
                textItem("Privacy-First Data Processing", "Privacy-First Data Processing:\n\n• Complete Anonymization: Personal identifiers are removed before any analysis, making individual identification impossible\n• Data Aggregation: Individual data points are combined into statistical groups\n• Secure Processing: All data analysis occurs in secure, controlled environments with strict access controls\n• Regular Data Purging: Outdated or unnecessary data is regularly deleted\n• Third-Party Restrictions: External researchers receive only anonymized, aggregated data\n\nWe employ multiple layers of protection to ensure your personal information remains private."),
            )),
            section("Legal and Regulatory Compliance", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Healthcare Regulations", "We comply with applicable healthcare data regulations in all regions where Sakhi operates, ensuring your health data is handled according to the highest legal standards."),
                cardItem("Data Protection Laws", "Our data usage practices comply with GDPR, CCPA, DPDPA, and other applicable data protection laws, giving you meaningful rights over your personal information."),
                cardItem("Research Ethics", "Any research involving user data follows strict ethical guidelines, with full anonymization and aggregate-only analysis to protect individual privacy."),
                cardItem("Audit and Compliance", "We conduct regular internal audits of our data usage practices and work with external reviewers to ensure ongoing compliance with all applicable regulations."),
            )),
            section("Your Control Over Data Usage", items = listOf(
                textItem("Your Data Usage Choices", "Your Data Usage Choices:\n\n• Essential Services: Basic app functionality requires minimal data usage\n• Analytics Opt-Out: You can limit data usage for product improvement while still using core features\n• Withdrawal Rights: You can withdraw consent for specific data usages at any time\n• Data Transparency: Clear explanations of how your data will be used before any new usage\n\nContact us at hello@getswipe.in to exercise your data control rights."),
            )),
        ),
    )

    private val gdprRights = ContentPage(
        heading = "Your GDPR Rights",
        subtitle = "Understanding your rights under the General Data Protection Regulation",
        sections = listOf(
            section("Understanding GDPR and Your Rights", items = listOf(
                textItem("What is GDPR", "The General Data Protection Regulation (GDPR) is a comprehensive data protection law that applies to all individuals within the European Union and European Economic Area. It also applies to organizations like Sakhi that process personal data of EU residents, regardless of where the organization is located.\n\nGDPR gives you fundamental rights over your personal data, including how it's collected, processed, stored, and shared. These rights are designed to give you control and transparency over your personal information, especially sensitive health data like what you share with Sakhi."),
            )),
            section("Right to Information (Articles 13-14)", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Transparent Data Processing", "You have the right to know when and how we collect your personal data, presented in a clear, plain language format that is easy to understand."),
                cardItem("Processing Purposes", "We must inform you of the specific purposes for which we process your data, the legal basis for processing, and how long we retain your information."),
                cardItem("Data Recipients", "You have the right to know which third parties may receive your personal data, including service providers and any international data transfers."),
                cardItem("Contact Information", "We provide clear contact information for privacy-related inquiries, including how to reach our data protection contact at hello@getswipe.in."),
            )),
            section("Right of Access (Article 15)", items = listOf(
                textItem("Accessing Your Personal Data", "Your Right to Access Includes:\n\n• Confirmation of whether we process your personal data\n• Access to your personal data and a copy of the data undergoing processing\n• Information about processing purposes, categories of data, and recipients\n• Retention periods or criteria used to determine retention periods\n• Information about your other GDPR rights\n\nWe provide this information free of charge, typically within one month of your request."),
            )),
            section("Right to Rectification (Article 16)", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Correcting Inaccurate Data", "You have the right to have inaccurate personal data corrected without undue delay. This includes updating incorrect profile information, contact details, or health data entries."),
                cardItem("Completing Incomplete Data", "If your personal data is incomplete, you have the right to have it completed, including by providing a supplementary statement where necessary."),
                cardItem("Immediate Processing", "We process rectification requests promptly, typically within a few business days for straightforward corrections made through app settings."),
                cardItem("Health Data Accuracy", "Given the sensitivity of health data, we prioritize accuracy. You can correct cycle dates, symptoms, and health entries directly through the app at any time."),
            )),
            section("Right to Erasure - 'Right to be Forgotten' (Article 17)", items = listOf(
                textItem("When You Can Request Erasure", "Grounds for Erasure Under GDPR:\n\n• The personal data is no longer necessary for the original purpose it was collected for\n• You withdraw consent and there's no other legal ground for processing\n• You object to processing and there are no overriding legitimate grounds\n• Your personal data has been unlawfully processed\n\nWhen we erase your data, we also inform third parties who received your data about the erasure request where technically feasible."),
            )),
            section("Right to Data Portability (Article 20)", items = listOf(
                textItem("Taking Your Data With You", "Data Portability Rights Include:\n\n• Receiving your personal data in a structured, commonly used, machine-readable format\n• Transmitting your data to another controller without hindrance from Sakhi\n• The right covers data you provided to us that we process based on consent or contract\n• We provide data in common formats like JSON or CSV to ensure compatibility\n\nThis right empowers you to switch between services while giving you control over your personal data."),
            )),
            section("Right to Object (Article 21)", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Objecting to Legitimate Interest Processing", "You can object to processing based on legitimate interests. We must stop processing unless we can demonstrate compelling legitimate grounds that override your interests."),
                cardItem("Direct Marketing Objection", "You have an absolute right to object to your data being used for direct marketing purposes. We will stop immediately upon receiving your objection, with no exceptions."),
                cardItem("Research Objection", "You can object to processing for research or statistical purposes, unless the processing is necessary for tasks carried out in the public interest."),
                cardItem("Objection Process", "To exercise your right to object, contact us at hello@getswipe.in or use the in-app privacy settings. We will respond within one month."),
            )),
            section("Right to Withdraw Consent (Article 7)", items = listOf(
                textItem("Withdrawing Your Consent", "Where processing is based on consent, you can withdraw that consent at any time. Withdrawal doesn't affect the lawfulness of processing before withdrawal.\n\nWithdrawing consent is as easy as giving it. You can do so through your app settings or by contacting us at hello@getswipe.in.\n\nAfter withdrawal, we will stop processing for the consented purpose, though we may continue processing on other legal grounds where applicable."),
            )),
            section("How to Exercise Your GDPR Rights", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Contact Us Directly", "Email hello@getswipe.in with your rights request. Include your account details and specify which right you wish to exercise."),
                cardItem("In-App Settings", "Many rights like data access, correction, and deletion can be exercised directly through your app settings without contacting us."),
                cardItem("Response Timeline", "We respond to all rights requests within one month. Complex requests may take up to three months, with notification to you."),
                cardItem("Supervisory Authority", "If you believe we've violated your GDPR rights, you have the right to lodge a complaint with your local supervisory authority."),
            )),
        ),
    )

    private val safetyGuidelines = ContentPage(
        heading = "Stay Safe with Sakhi",
        subtitle = "Essential safety tips for using Sakhi and protecting yourself",
        sections = listOf(
            section("Before Meeting Your Helper", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Check Their Trust Level", "Always review a helper's trust level and community standing before agreeing to meet. Higher trust levels indicate a stronger track record of safe, helpful interactions."),
                cardItem("Share Your Location with Trusted Contact", "Before meeting anyone, let a trusted friend or family member know where you are going, who you are meeting, and when you expect to return."),
                cardItem("Choose Public Meeting Spots", "Always meet in busy, public places like pharmacies, shopping centers, or well-lit public areas. Avoid isolated locations, private homes, or unfamiliar places."),
                cardItem("Trust Your Instincts", "If anything about the interaction feels wrong, trust your gut feeling. It is always okay to cancel or leave. Your instincts are your most powerful safety tool."),
            )),
            section("During the Help Interaction", items = listOf(
                textItem("Staying Safe While Getting Help", "Staying Safe While Getting Help:\n\n• Meet in busy, public areas like shops, cafes, or main streets\n• Keep your phone charged and accessible at all times\n• Don't share personal information like your home address\n• Accept help graciously but maintain appropriate boundaries\n• If you feel uncomfortable, politely end the interaction\n• Never get into someone's vehicle unless absolutely necessary\n\nGenuine helpers will respect your boundaries and prioritize your comfort and safety."),
            )),
            section("Red Flags to Watch For", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Inappropriate Requests", "Be alert if someone asks for personal information beyond what's needed, requests payment, or tries to move the conversation off the Sakhi platform."),
                cardItem("Pressure or Urgency", "Genuine helpers never pressure you. If someone creates artificial urgency or makes you feel rushed into decisions, this is a serious warning sign."),
                cardItem("Asking for Payment", "Sakhi help is always free. Anyone asking for money, gifts, or favors in exchange for assistance is violating community standards and should be reported immediately."),
                cardItem("Inconsistent Behavior", "If someone's story doesn't add up, their behavior changes suddenly, or they seem overly interested in personal details, proceed with extreme caution."),
            )),
            section("Protecting Your Personal Information", items = listOf(
                textItem("What to Share and What to Keep Private", "What to Share and What to Keep Private:\n\n• Share only what's necessary for the help you need\n• Don't share your home address unless absolutely required\n• Avoid sharing social media profiles or personal details\n• Don't share financial information or banking details\n• Use first names only when introducing yourself\n• Be cautious about sharing your daily routine or schedule\n\nLegitimate helpers only need basic information to assist you."),
            )),
            section("Reporting and Blocking", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("When to Report Someone", "Report any user who makes you feel unsafe, requests inappropriate information, asks for payment, behaves inconsistently, or violates any community guidelines."),
                cardItem("How to Block Users", "You can block any user from their profile page. Blocked users cannot contact you or see your profile. Blocking is immediate and confidential."),
                cardItem("Contact Authorities for Serious Situations", "For serious threats, stalking, or any situation involving your physical safety, contact local police immediately. Do not rely on the app for emergency safety situations."),
                cardItem("Community Reporting", "Reporting unsafe behavior protects the entire Sakhi community. All reports are reviewed confidentially, and repeat offenders are permanently removed from the platform."),
            )),
            section("Emergency Situations", items = listOf(
                textItem("When to Contact Authorities", "When to Contact Authorities:\n\n• Life-threatening situations: Call emergency services immediately (100, 108, or 112 in India)\n• Feeling threatened or unsafe: Contact police right away\n• Medical emergencies: Use official medical emergency numbers\n• Physical assault or threats: Prioritize your safety, call for help\n\nSakhi is designed to help with period-related support, but serious safety concerns should always be handled by professional emergency services. Your safety is the top priority."),
            )),
            section("Remember: Your Safety Comes First", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Trust Your Instincts Always", "Your feelings and instincts are valid. If something feels wrong, it probably is. Always prioritize your safety and comfort over social obligation."),
                cardItem("It's Okay to Say No", "You are never obligated to meet anyone or accept help you feel uncomfortable with. Declining gracefully is always the right choice when you have doubts."),
                cardItem("Stay Connected", "Always keep your phone charged and let someone you trust know your plans. Staying connected is one of the most effective safety measures you can take."),
                cardItem("Help Build a Safer Community", "By reporting suspicious behavior and following safety guidelines, you contribute to making Sakhi safer for every woman in our community."),
            )),
        ),
    )

    private val faq = ContentPage(
        heading = "Common Questions",
        subtitle = "Quick answers to frequently asked questions about using Sakhi",
        sections = listOf(
            section("Getting Started with Sakhi", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("How accurate are Sakhi's period predictions?", "Sakhi's predictions become more accurate as you track more cycles. Initially, we use average cycle data, but after 3–6 cycles, our predictions are personalized to your unique patterns and can be 85–95% accurate for regular cycles."),
                cardItem("Is my health data really safe and private?", "Absolutely. Your data is encrypted, stored securely, and never sold or shared without your consent. Only you can access your personal information, and we follow strict privacy laws including GDPR."),
                cardItem("Do I need to track every day?", "No, you don't need daily tracking. The most important data is your period start and end dates. Logging symptoms and mood is helpful but optional."),
            )),
            section("Using Core Features", items = listOf(
                textItem("Common Feature Questions", "Common Feature Questions:\n\n• How do I log my period? Tap the calendar on the Home screen and select your start date. You can add flow intensity, symptoms, and mood from there.\n\n• Can I use Sakhi if I have irregular cycles? Yes! Sakhi adapts to irregular patterns and helps you understand your unique cycle, even if it's unpredictable.\n\n• Can I export my data? Yes, you can export your cycle data anytime through Profile → Export My Data for your records or to share with healthcare providers.\n\n• Does Sakhi work offline? Basic tracking works offline, but data sync requires internet connection."),
            )),
            section("Trust and Safety", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("What is the Trust Level system?", "Trust Levels reflect a helper's history of safe, reliable interactions in our community. Higher levels indicate more verified positive experiences. Always consider trust levels when connecting with helpers."),
                cardItem("Is it safe to meet strangers through Sakhi?", "Sakhi has safety measures in place, but always follow our safety guidelines. Meet in public places, tell someone where you're going, and trust your instincts. Your safety is always the top priority."),
                cardItem("What if someone makes me uncomfortable?", "Block and report the user immediately through their profile. You can also contact our support team at hello@getswipe.in. Your report is kept confidential and helps keep the community safe."),
            )),
            section("Account and Privacy", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Is Sakhi completely free to use?", "Yes, Sakhi is completely free. All features including cycle tracking and health insights are available at no cost. We believe women's health support should be accessible to everyone."),
                cardItem("How do I delete my account?", "Go to Profile → Manage Account → Delete Account. Your data will be permanently removed within 30 days. You can also contact hello@getswipe.in for assistance."),
            )),
        ),
    )

    private val aboutUs = ContentPage(
        heading = "Our Journey",
        subtitle = "How a college project became a mission to ensure no woman ever feels alone",
        sections = listOf(
            section("Where It All Started", items = listOf(
                textItem("The Beginning of Sakhi", "It all began with Karan Kumar, who came up with an innovative idea to address a real problem that women face every day. Initially, Karan collaborated with Aman Prakash to shape and refine this concept, working together to understand the technical possibilities and user needs.\n\nRecognizing the potential impact of their idea, they approached Dr. Shruti Sachdeva, who immediately saw the value in their vision. With her guidance and mentorship, they officially started the project as part of the ISDP (iOS Student Developer Program) at our university.\n\nAs the project gained momentum, Arpita Gupta and Shweta joined the team, bringing their unique skills and perspectives. Together, this passionate group of students worked tirelessly to transform a simple idea into something that could genuinely impact lives."),
            )),
            section("The Inspiration Behind Our Mission", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Understanding the Real Problem", "We saw firsthand how women struggle to find support during their periods, especially in emergencies. The lack of accessible, judgment-free help inspired us to create a better solution."),
                cardItem("The Power of Community", "We believe in the incredible strength of women supporting women. Sakhi was built on the conviction that community bonds can solve problems that institutions have long overlooked."),
                cardItem("Breaking the Silence", "Menstrual health is still surrounded by stigma and silence in many communities. Sakhi aims to break those barriers by creating a space where women can speak openly and seek help freely."),
                cardItem("Technology for Good", "We saw technology as a powerful equalizer, a way to give every woman access to community support and health insights regardless of her location or resources."),
            )),
            section("Building Sakhi, The Early Days", items = listOf(
                textItem("The Development Journey", "The Development Journey:\n\nWith Dr. Shruti Sachdeva's mentorship and the ISDP framework, what started as Karan's innovative idea began taking shape into a real solution. The early collaboration between Karan and Aman Prakash laid the foundation for the technical approach and user experience.\n\nAs Arpita Gupta and Shweta joined the team, each member brought their unique strengths to the project. Karan continued to lead the technical development and overall vision, while Aman contributed to the initial design concepts.\n\nLate nights in computer labs turned into brainstorming sessions about how to make women feel supported and empowered. The name 'Sakhi' came naturally. In Sanskrit, it means 'female friend', exactly what we wanted our platform to be for every woman who used it."),
            )),
            section("The Vision Takes Shape", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("More Than Just Tracking", "We quickly realized Sakhi needed to be more than a period tracker. By connecting women who need help with those who can provide it, we created something far more meaningful."),
                cardItem("Trust-Based Community", "Our trust level system emerged from our commitment to safety. We wanted women to feel confident in every interaction on the platform, knowing help was coming from verified, caring community members."),
                cardItem("Privacy as a Foundation", "From day one, privacy was not an afterthought but a foundation. We built Sakhi's architecture with the understanding that health data is deeply personal and deserves the highest protection."),
                cardItem("Technology with Purpose", "Every technical decision was guided by one question: does this make women's lives better? This purposeful approach to development shaped every feature we built."),
            )),
            section("The Sakhi Philosophy", items = listOf(
                textItem("The Heart of Sakhi", "The Heart of Sakhi:\n\nOur fundamental belief is simple yet powerful: no woman should ever feel alone during her menstrual health journey. This philosophy drives every decision we make, every feature we build, and every interaction we facilitate.\n\nSakhi isn't just about predicting periods or tracking symptoms. It's about creating a mindset, a way of thinking about women's health that celebrates community support and mutual care.\n\nEvery time a woman uses Sakhi, she's not just managing her health, she's joining a community of women who believe in supporting each other through every challenge and triumph."),
            )),
            section("Real Impact", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Changing Lives Daily", "Every day, women across our community use Sakhi to get support when they need it most. Each successful interaction represents our mission becoming reality."),
                cardItem("Breaking Stigma", "By creating a judgment-free space for menstrual health conversations, Sakhi is actively working to dismantle the stigma that has silenced women for generations."),
                cardItem("Community Growth", "Our community grows every day as more women discover the power of having a dedicated support network for their menstrual health journey."),
                cardItem("Looking Forward", "We envision a future where every woman has access to the support she needs, regardless of where she lives. Sakhi is our contribution to that future."),
            )),
            section("Join Our Mission", items = listOf(
                textItem("Be Part of the Story", "Sakhi's story is still being written, and every woman who joins our community becomes part of this ongoing narrative of change and empowerment.\n\nWhen you use Sakhi, you're not just using an app, you're joining a movement that believes women deserve better support, more understanding, and stronger communities.\n\nWelcome to Sakhi. Welcome to a community where you'll always have a friend."),
            )),
        ),
    )

    private val credits = ContentPage(
        heading = "Meet the Team",
        subtitle = "The passionate minds behind Sakhi who made this vision a reality",
        sections = listOf(
            section("Core Development Team", items = listOf(
                textItem("Karan Kumar, Lead Developer & Designer", "Visionary Behind Sakhi's Technical Excellence:\n\nKaran Kumar serves as the principal architect and creative force behind Sakhi's technical infrastructure and user experience design. As the lead developer, he has been instrumental in crafting every aspect of the application, from the elegant user interface to the robust backend systems that power the platform.\n\nHis expertise spans the complete development lifecycle, conceptualizing user flows, designing intuitive interfaces, implementing complex algorithms for cycle prediction, and ensuring seamless performance across all devices.\n\nKaran's unique ability to combine technical precision with design aesthetics has resulted in an application that is not only functionally superior but also emotionally resonant with users."),
            )),
            section("Research and Strategy", items = listOf(
                textItem("Arpita Gupta, Lead Researcher & Business Strategist", "The Strategic Mind Behind Sakhi's Impact:\n\nArpita Gupta brings exceptional research acumen and business insight to Sakhi, serving as the driving force behind the platform's evidence-based approach and strategic direction. Her comprehensive understanding of women's health challenges, combined with sharp business instincts, has been crucial in shaping Sakhi's mission.\n\nAs Lead Researcher, Arpita has conducted extensive studies on menstrual health patterns, user behavior analysis, and community dynamics that inform every feature development decision.\n\nArpita's ability to bridge the gap between academic research and practical business application has been invaluable in ensuring that Sakhi remains both scientifically sound and commercially viable."),
            )),
            section("Founding Contributors", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Aman Prakash", "Aman played a pivotal role in shaping Sakhi's early design philosophy and user interface concepts during the foundational development phase, contributing valuable insights that continue to influence the platform today."),
                cardItem("Shweta Kumari", "Shweta was instrumental in conceptualizing and developing the community-centric features that make Sakhi unique, particularly the emergency assistance system and trust-building mechanisms."),
            )),
            section("Mentorship and Guidance", items = listOf(
                textItem("Dr. Shruti Sachdeva, Project Mentor & Guiding Force", "The Backbone of Sakhi:\n\nDr. Shruti Sachdeva has been the guiding light and backbone of the Sakhi project from its very inception. As our project mentor, she provided not just academic supervision but visionary leadership that shaped Sakhi's direction.\n\nShe went far beyond traditional academic guidance. She helped us understand the broader implications of our work, encouraging us to think beyond technical solutions to create something that could genuinely transform women's lives.\n\nWe are deeply grateful for her mentorship, guidance, and belief in our mission to ensure no woman ever feels alone."),
            )),
            section("Special Acknowledgments", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Academic Support", "We are grateful to our university's academic department for providing the ISDP framework and institutional support that enabled Sakhi to grow from idea to reality."),
                cardItem("Healthcare Professional Advisory", "Healthcare professionals who provided insights into women's health needs and helped us ensure our features are medically responsible and genuinely supportive."),
                cardItem("Beta Testing Community", "The brave early users who tested Sakhi, provided candid feedback, and helped shape the app into what it is today. Their honesty and patience were invaluable."),
                cardItem("Open Source Community", "The global open source developer community whose libraries and frameworks form the technical foundation of Sakhi. Their generosity enables innovation worldwide."),
            )),
            section("Technology Stack", items = listOf(
                textItem("Sakhi's Technological Foundation", "Core Technologies:\n\n• Cross-Platform: Kotlin Multiplatform (SakhiCore) shares business logic between iOS and Android\n• iOS: Native Swift/SwiftUI\n• Android: Native Kotlin/Jetpack Compose\n• Backend Infrastructure: Supabase for real-time data synchronization and authentication\n• AI Integration: Anthropic Claude for conversational health insights\n\nEvery technology choice reflects our commitment to user privacy, data security, and exceptional user experience."),
            )),
        ),
    )

    private val openSourceLicenses = ContentPage(
        heading = "Third Party Libraries",
        subtitle = "Acknowledging the open source community and libraries that power Sakhi",
        sections = listOf(
            section("Our Commitment to Open Source", items = listOf(
                textItem("Standing on the Shoulders of Giants", "Sakhi is built using various open source libraries and frameworks created by the global developer community. We are deeply grateful to the countless developers who have contributed their time, expertise, and creativity to make these tools available for everyone.\n\nOpen source software enables innovation, collaboration, and the democratization of technology. By using these libraries, we can focus on creating meaningful features for women's health while standing on the proven foundation built by expert developers worldwide.\n\nWe respect all open source licenses and maintain compliance with their terms."),
            )),
            section("Core Libraries", style = ContentSectionStyle.CARDS, items = listOf(
                cardItem("Jetpack Compose", "Google's modern declarative UI toolkit for building native Android interfaces. Licensed under Apache License 2.0."),
                cardItem("Kotlin Multiplatform", "JetBrains' technology for sharing code between iOS and Android. Licensed under Apache License 2.0."),
                cardItem("Koin", "Pragmatic dependency injection framework for Kotlin. Licensed under Apache License 2.0."),
                cardItem("Supabase Kt", "Kotlin client for Supabase, providing authentication, database, and realtime functionality. Licensed under MIT License."),
            )),
            section("UI and Experience Libraries", items = listOf(
                textItem("User Interface Libraries Used in Sakhi", "User Interface Libraries Used in Sakhi:\n\n• Jetpack Navigation Compose: Type-safe in-app navigation\n• Material3: Google's design system components\n• Coil: Image loading for Compose\n• Ktor Client: Networking layer shared across platforms via KMM"),
            )),
            section("Development and Testing Tools", items = listOf(
                textItem("Development Tools", "Development Tools:\n\n• JUnit / Kotlin Test: Testing frameworks ensuring Sakhi's reliability and quality\n• Gradle: Build automation for the whole multi-module project\n\nThese tools help us maintain high code quality, catch issues early, and deliver a reliable experience to all Sakhi users."),
            )),
            section("License Acknowledgment", items = listOf(
                textItem("Gratitude and Compliance", "We acknowledge and thank all open source contributors. The full text of all applicable licenses is available upon request.\n\nIf you believe we have inadvertently missed attribution for any library or if you have any licensing questions, please contact us at hello@getswipe.in.\n\nThe open source community makes software development better for everyone, and we are proud to be part of this ecosystem."),
            )),
        ),
    )
}
