package team.sakhi.android.feature.profile

import androidx.annotation.StringRes

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
    val title: String = "",
    @StringRes val titleResId: Int? = null,
    val body: String = "",
    @StringRes val bodyResId: Int? = null,
)

data class ContentSection(
    val title: String = "",
    @StringRes val titleResId: Int? = null,
    val style: ContentSectionStyle = ContentSectionStyle.TEXT,
    val items: List<ContentItem>,
)

data class ContentPage(
    val heading: String = "",
    @StringRes val headingResId: Int? = null,
    val subtitle: String = "",
    @StringRes val subtitleResId: Int? = null,
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

    private fun section(
        title: String = "",
        @StringRes titleResId: Int? = null,
        style: ContentSectionStyle = ContentSectionStyle.TEXT,
        items: List<ContentItem>,
    ) = ContentSection(title = title, titleResId = titleResId, style = style, items = items)

    private fun textItem(
        title: String = "",
        @StringRes titleResId: Int? = null,
        body: String = "",
        @StringRes bodyResId: Int? = null,
    ) = ContentItem(title = title, titleResId = titleResId, body = body, bodyResId = bodyResId)

    private fun cardItem(
        title: String = "",
        @StringRes titleResId: Int? = null,
        body: String = "",
        @StringRes bodyResId: Int? = null,
    ) = ContentItem(title = title, titleResId = titleResId, body = body, bodyResId = bodyResId)

    private val privacyPolicy = ContentPage(
        headingResId = R.string.profile_content_heading_privacy_policy,
        subtitleResId = R.string.profile_content_subtitle_privacy_policy,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_privacy_policy_our_privacy_promise, items = listOf(
                textItem(titleResId = R.string.profile_content_item_privacy_policy_your_data_is_sacred_to_us, bodyResId = R.string.profile_content_item_body_privacy_policy_your_data_is_sacred_to_us),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_introduction_to_our_privacy_policy, items = listOf(
                textItem(titleResId = R.string.profile_content_item_privacy_policy_welcome_to_sakhi_privacy_protection, bodyResId = R.string.profile_content_item_body_privacy_policy_welcome_to_sakhi_privacy_protection),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_how_we_secure_your_data, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_firebase_database_protection, bodyResId = R.string.profile_content_item_body_privacy_policy_firebase_database_protection),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_data_encryption, bodyResId = R.string.profile_content_item_body_privacy_policy_data_encryption),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_authentication_controls, bodyResId = R.string.profile_content_item_body_privacy_policy_authentication_controls),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_access_rules_and_monitoring, bodyResId = R.string.profile_content_item_body_privacy_policy_access_rules_and_monitoring),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_what_personal_information_we_collect, items = listOf(
                textItem(titleResId = R.string.profile_content_item_privacy_policy_account_and_profile_information, bodyResId = R.string.profile_content_item_body_privacy_policy_account_and_profile_information),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_health_data_collection, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_menstrual_health_data, bodyResId = R.string.profile_content_item_body_privacy_policy_menstrual_health_data),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_technical_information, bodyResId = R.string.profile_content_item_body_privacy_policy_technical_information),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_location_data_safety, bodyResId = R.string.profile_content_item_body_privacy_policy_location_data_safety),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_no_continuous_tracking, bodyResId = R.string.profile_content_item_body_privacy_policy_no_continuous_tracking),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_how_we_use_your_data, items = listOf(
                textItem(titleResId = R.string.profile_content_item_privacy_policy_core_app_features_and_analysis, bodyResId = R.string.profile_content_item_body_privacy_policy_core_app_features_and_analysis),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_how_we_share_your_information, items = listOf(
                textItem(titleResId = R.string.profile_content_item_privacy_policy_strict_limits_on_data_sharing, bodyResId = R.string.profile_content_item_body_privacy_policy_strict_limits_on_data_sharing),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_data_storage_and_deletion, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_how_long_we_keep_your_data, bodyResId = R.string.profile_content_item_body_privacy_policy_how_long_we_keep_your_data),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_your_deletion_rights, bodyResId = R.string.profile_content_item_body_privacy_policy_your_deletion_rights),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_complete_data_removal, bodyResId = R.string.profile_content_item_body_privacy_policy_complete_data_removal),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_contact_for_rights, bodyResId = R.string.profile_content_item_body_privacy_policy_contact_for_rights),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_your_privacy_rights_and_controls, items = listOf(
                textItem(titleResId = R.string.profile_content_item_privacy_policy_accessing_and_controlling_your_data, bodyResId = R.string.profile_content_item_body_privacy_policy_accessing_and_controlling_your_data),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_regional_privacy_rights, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_european_users, bodyResId = R.string.profile_content_item_body_privacy_policy_european_users),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_california_residents, bodyResId = R.string.profile_content_item_body_privacy_policy_california_residents),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_indian_users, bodyResId = R.string.profile_content_item_body_privacy_policy_indian_users),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_childrens_privacy, bodyResId = R.string.profile_content_item_body_privacy_policy_childrens_privacy),
            )),
            section(titleResId = R.string.profile_content_section_privacy_policy_our_promise_to_you, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_transparency_in_everything, bodyResId = R.string.profile_content_item_body_privacy_policy_transparency_in_everything),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_your_right_to_understand, bodyResId = R.string.profile_content_item_body_privacy_policy_your_right_to_understand),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_continuous_protection, bodyResId = R.string.profile_content_item_body_privacy_policy_continuous_protection),
                cardItem(titleResId = R.string.profile_content_item_privacy_policy_building_trust_together, bodyResId = R.string.profile_content_item_body_privacy_policy_building_trust_together),
            )),
        ),
    )

    private val termsOfService = ContentPage(
        headingResId = R.string.profile_content_heading_terms_of_service,
        subtitleResId = R.string.profile_content_subtitle_terms_of_service,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_terms_of_service_agreement_to_our_terms, items = listOf(
                textItem(titleResId = R.string.profile_content_item_terms_of_service_welcome_to_sakhi, bodyResId = R.string.profile_content_item_body_terms_of_service_welcome_to_sakhi),
            )),
            section(titleResId = R.string.profile_content_section_terms_of_service_who_can_use_sakhi, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_age_requirements, bodyResId = R.string.profile_content_item_body_terms_of_service_age_requirements),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_account_responsibility, bodyResId = R.string.profile_content_item_body_terms_of_service_account_responsibility),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_one_account_per_person, bodyResId = R.string.profile_content_item_body_terms_of_service_one_account_per_person),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_honest_information, bodyResId = R.string.profile_content_item_body_terms_of_service_honest_information),
            )),
            section(titleResId = R.string.profile_content_section_terms_of_service_how_to_use_sakhi_properly, items = listOf(
                textItem(titleResId = R.string.profile_content_item_terms_of_service_using_sakhi_responsibly, bodyResId = R.string.profile_content_item_body_terms_of_service_using_sakhi_responsibly),
            )),
            section(titleResId = R.string.profile_content_section_terms_of_service_what_you_cannot_do_on_sakhi, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_prohibited_activities, bodyResId = R.string.profile_content_item_body_terms_of_service_prohibited_activities),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_no_commercial_use, bodyResId = R.string.profile_content_item_body_terms_of_service_no_commercial_use),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_false_information, bodyResId = R.string.profile_content_item_body_terms_of_service_false_information),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_illegal_activities, bodyResId = R.string.profile_content_item_body_terms_of_service_illegal_activities),
            )),
            section(titleResId = R.string.profile_content_section_terms_of_service_your_privacy_and_data, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_data_ownership, bodyResId = R.string.profile_content_item_body_terms_of_service_data_ownership),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_privacy_policy_agreement, bodyResId = R.string.profile_content_item_body_terms_of_service_privacy_policy_agreement),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_data_sharing, bodyResId = R.string.profile_content_item_body_terms_of_service_data_sharing),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_right_to_delete, bodyResId = R.string.profile_content_item_body_terms_of_service_right_to_delete),
            )),
            section(titleResId = R.string.profile_content_section_terms_of_service_sakhis_services_and_limitations, items = listOf(
                textItem(titleResId = R.string.profile_content_item_terms_of_service_what_sakhi_provides, bodyResId = R.string.profile_content_item_body_terms_of_service_what_sakhi_provides),
            )),
            section(titleResId = R.string.profile_content_section_terms_of_service_important_disclaimers, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_not_medical_advice, bodyResId = R.string.profile_content_item_body_terms_of_service_not_medical_advice),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_prediction_accuracy, bodyResId = R.string.profile_content_item_body_terms_of_service_prediction_accuracy),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_community_assistance_limitations, bodyResId = R.string.profile_content_item_body_terms_of_service_community_assistance_limitations),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_emergency_services, bodyResId = R.string.profile_content_item_body_terms_of_service_emergency_services),
            )),
            section(titleResId = R.string.profile_content_section_terms_of_service_account_suspension_and_termination, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_when_we_may_suspend, bodyResId = R.string.profile_content_item_body_terms_of_service_when_we_may_suspend),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_your_right_to_terminate, bodyResId = R.string.profile_content_item_body_terms_of_service_your_right_to_terminate),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_effect_of_termination, bodyResId = R.string.profile_content_item_body_terms_of_service_effect_of_termination),
                cardItem(titleResId = R.string.profile_content_item_terms_of_service_appeal_process, bodyResId = R.string.profile_content_item_body_terms_of_service_appeal_process),
            )),
            section(titleResId = R.string.profile_content_section_terms_of_service_changes_and_contact, items = listOf(
                textItem(titleResId = R.string.profile_content_item_terms_of_service_staying_updated, bodyResId = R.string.profile_content_item_body_terms_of_service_staying_updated),
            )),
        ),
    )

    private val codeOfConduct = ContentPage(
        headingResId = R.string.profile_content_heading_code_of_conduct,
        subtitleResId = R.string.profile_content_subtitle_code_of_conduct,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_code_of_conduct_our_community_values, items = listOf(
                textItem(titleResId = R.string.profile_content_item_code_of_conduct_the_foundation_of_sakhi, bodyResId = R.string.profile_content_item_body_code_of_conduct_the_foundation_of_sakhi),
            )),
            section(titleResId = R.string.profile_content_section_code_of_conduct_respectful_communication_standards, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_code_of_conduct_speak_with_kindness, bodyResId = R.string.profile_content_item_body_code_of_conduct_speak_with_kindness),
                cardItem(titleResId = R.string.profile_content_item_code_of_conduct_listen_and_understand, bodyResId = R.string.profile_content_item_body_code_of_conduct_listen_and_understand),
                cardItem(titleResId = R.string.profile_content_item_code_of_conduct_cultural_sensitivity, bodyResId = R.string.profile_content_item_body_code_of_conduct_cultural_sensitivity),
                cardItem(titleResId = R.string.profile_content_item_code_of_conduct_professional_boundaries, bodyResId = R.string.profile_content_item_body_code_of_conduct_professional_boundaries),
            )),
            section(titleResId = R.string.profile_content_section_code_of_conduct_prohibited_behavior, items = listOf(
                textItem(titleResId = R.string.profile_content_item_code_of_conduct_zero_tolerance_for_harmful_conduct, bodyResId = R.string.profile_content_item_body_code_of_conduct_zero_tolerance_for_harmful_conduct),
            )),
            section(titleResId = R.string.profile_content_section_code_of_conduct_privacy_and_confidentiality, items = listOf(
                textItem(titleResId = R.string.profile_content_item_code_of_conduct_privacy_protection_standards, bodyResId = R.string.profile_content_item_body_code_of_conduct_privacy_protection_standards),
            )),
            section(titleResId = R.string.profile_content_section_code_of_conduct_content_and_sharing_guidelines, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_code_of_conduct_appropriate_content_only, bodyResId = R.string.profile_content_item_body_code_of_conduct_appropriate_content_only),
                cardItem(titleResId = R.string.profile_content_item_code_of_conduct_no_medical_advice, bodyResId = R.string.profile_content_item_body_code_of_conduct_no_medical_advice),
                cardItem(titleResId = R.string.profile_content_item_code_of_conduct_factual_information, bodyResId = R.string.profile_content_item_body_code_of_conduct_factual_information),
                cardItem(titleResId = R.string.profile_content_item_code_of_conduct_intellectual_property_respect, bodyResId = R.string.profile_content_item_body_code_of_conduct_intellectual_property_respect),
            )),
            section(titleResId = R.string.profile_content_section_code_of_conduct_enforcement_and_consequences, items = listOf(
                textItem(titleResId = R.string.profile_content_item_code_of_conduct_code_of_conduct_enforcement, bodyResId = R.string.profile_content_item_body_code_of_conduct_code_of_conduct_enforcement),
            )),
            section(titleResId = R.string.profile_content_section_code_of_conduct_our_commitment_to_you, items = listOf(
                textItem(titleResId = R.string.profile_content_item_code_of_conduct_a_community_built_on_trust, bodyResId = R.string.profile_content_item_body_code_of_conduct_a_community_built_on_trust),
            )),
        ),
    )

    private val cookiePolicy = ContentPage(
        headingResId = R.string.profile_content_heading_cookie_policy,
        subtitleResId = R.string.profile_content_subtitle_cookie_policy,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_cookie_policy_what_are_cookies, items = listOf(
                textItem(titleResId = R.string.profile_content_item_cookie_policy_understanding_cookies, bodyResId = R.string.profile_content_item_body_cookie_policy_understanding_cookies),
            )),
            section(titleResId = R.string.profile_content_section_cookie_policy_types_of_cookies_we_use, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_cookie_policy_essential_cookies, bodyResId = R.string.profile_content_item_body_cookie_policy_essential_cookies),
                cardItem(titleResId = R.string.profile_content_item_cookie_policy_performance_cookies, bodyResId = R.string.profile_content_item_body_cookie_policy_performance_cookies),
                cardItem(titleResId = R.string.profile_content_item_cookie_policy_functional_cookies, bodyResId = R.string.profile_content_item_body_cookie_policy_functional_cookies),
                cardItem(titleResId = R.string.profile_content_item_cookie_policy_security_cookies, bodyResId = R.string.profile_content_item_body_cookie_policy_security_cookies),
            )),
            section(titleResId = R.string.profile_content_section_cookie_policy_how_we_use_cookies, items = listOf(
                textItem(titleResId = R.string.profile_content_item_cookie_policy_cookie_usage_for_core_services, bodyResId = R.string.profile_content_item_body_cookie_policy_cookie_usage_for_core_services),
            )),
            section(titleResId = R.string.profile_content_section_cookie_policy_cookie_retention_periods, items = listOf(
                textItem(titleResId = R.string.profile_content_item_cookie_policy_how_long_we_keep_cookies, bodyResId = R.string.profile_content_item_body_cookie_policy_how_long_we_keep_cookies),
            )),
            section(titleResId = R.string.profile_content_section_cookie_policy_managing_your_cookie_preferences, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_cookie_policy_in_app_cookie_controls, bodyResId = R.string.profile_content_item_body_cookie_policy_in_app_cookie_controls),
                cardItem(titleResId = R.string.profile_content_item_cookie_policy_device_level_controls, bodyResId = R.string.profile_content_item_body_cookie_policy_device_level_controls),
                cardItem(titleResId = R.string.profile_content_item_cookie_policy_opt_out_options, bodyResId = R.string.profile_content_item_body_cookie_policy_opt_out_options),
                cardItem(titleResId = R.string.profile_content_item_cookie_policy_contact_for_questions, bodyResId = R.string.profile_content_item_body_cookie_policy_contact_for_questions),
            )),
            section(titleResId = R.string.profile_content_section_cookie_policy_international_cookie_compliance, items = listOf(
                textItem(titleResId = R.string.profile_content_item_cookie_policy_cookie_compliance_by_region, bodyResId = R.string.profile_content_item_body_cookie_policy_cookie_compliance_by_region),
            )),
        ),
    )

    private val dataUsage = ContentPage(
        headingResId = R.string.profile_content_heading_data_usage,
        subtitleResId = R.string.profile_content_subtitle_data_usage,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_data_usage_our_data_usage_philosophy, items = listOf(
                textItem(titleResId = R.string.profile_content_item_data_usage_responsible_data_stewardship, bodyResId = R.string.profile_content_item_body_data_usage_responsible_data_stewardship),
            )),
            section(titleResId = R.string.profile_content_section_data_usage_core_service_data_usage, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_data_usage_personalized_health_insights, bodyResId = R.string.profile_content_item_body_data_usage_personalized_health_insights),
                cardItem(titleResId = R.string.profile_content_item_data_usage_cycle_prediction_accuracy, bodyResId = R.string.profile_content_item_body_data_usage_cycle_prediction_accuracy),
                cardItem(titleResId = R.string.profile_content_item_data_usage_safety_and_security, bodyResId = R.string.profile_content_item_body_data_usage_safety_and_security),
                cardItem(titleResId = R.string.profile_content_item_data_usage_product_improvement, bodyResId = R.string.profile_content_item_body_data_usage_product_improvement),
            )),
            section(titleResId = R.string.profile_content_section_data_usage_product_development_and_improvement, items = listOf(
                textItem(titleResId = R.string.profile_content_item_data_usage_how_your_data_drives_innovation, bodyResId = R.string.profile_content_item_body_data_usage_how_your_data_drives_innovation),
            )),
            section(titleResId = R.string.profile_content_section_data_usage_data_anonymization_and_protection, items = listOf(
                textItem(titleResId = R.string.profile_content_item_data_usage_privacy_first_data_processing, bodyResId = R.string.profile_content_item_body_data_usage_privacy_first_data_processing),
            )),
            section(titleResId = R.string.profile_content_section_data_usage_legal_and_regulatory_compliance, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_data_usage_healthcare_regulations, bodyResId = R.string.profile_content_item_body_data_usage_healthcare_regulations),
                cardItem(titleResId = R.string.profile_content_item_data_usage_data_protection_laws, bodyResId = R.string.profile_content_item_body_data_usage_data_protection_laws),
                cardItem(titleResId = R.string.profile_content_item_data_usage_research_ethics, bodyResId = R.string.profile_content_item_body_data_usage_research_ethics),
                cardItem(titleResId = R.string.profile_content_item_data_usage_audit_and_compliance, bodyResId = R.string.profile_content_item_body_data_usage_audit_and_compliance),
            )),
            section(titleResId = R.string.profile_content_section_data_usage_your_control_over_data_usage, items = listOf(
                textItem(titleResId = R.string.profile_content_item_data_usage_your_data_usage_choices, bodyResId = R.string.profile_content_item_body_data_usage_your_data_usage_choices),
            )),
        ),
    )

    private val gdprRights = ContentPage(
        headingResId = R.string.profile_content_heading_gdpr_rights,
        subtitleResId = R.string.profile_content_subtitle_gdpr_rights,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_gdpr_rights_understanding_gdpr_and_your_rights, items = listOf(
                textItem(titleResId = R.string.profile_content_item_gdpr_rights_what_is_gdpr, bodyResId = R.string.profile_content_item_body_gdpr_rights_what_is_gdpr),
            )),
            section(titleResId = R.string.profile_content_section_gdpr_rights_right_to_information, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_transparent_data_processing, bodyResId = R.string.profile_content_item_body_gdpr_rights_transparent_data_processing),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_processing_purposes, bodyResId = R.string.profile_content_item_body_gdpr_rights_processing_purposes),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_data_recipients, bodyResId = R.string.profile_content_item_body_gdpr_rights_data_recipients),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_contact_information, bodyResId = R.string.profile_content_item_body_gdpr_rights_contact_information),
            )),
            section(titleResId = R.string.profile_content_section_gdpr_rights_right_of_access, items = listOf(
                textItem(titleResId = R.string.profile_content_item_gdpr_rights_accessing_your_personal_data, bodyResId = R.string.profile_content_item_body_gdpr_rights_accessing_your_personal_data),
            )),
            section(titleResId = R.string.profile_content_section_gdpr_rights_right_to_rectification, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_correcting_inaccurate_data, bodyResId = R.string.profile_content_item_body_gdpr_rights_correcting_inaccurate_data),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_completing_incomplete_data, bodyResId = R.string.profile_content_item_body_gdpr_rights_completing_incomplete_data),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_immediate_processing, bodyResId = R.string.profile_content_item_body_gdpr_rights_immediate_processing),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_health_data_accuracy, bodyResId = R.string.profile_content_item_body_gdpr_rights_health_data_accuracy),
            )),
            section(titleResId = R.string.profile_content_section_gdpr_rights_right_to_erasure_right_to_be_forgotten, items = listOf(
                textItem(titleResId = R.string.profile_content_item_gdpr_rights_when_you_can_request_erasure, bodyResId = R.string.profile_content_item_body_gdpr_rights_when_you_can_request_erasure),
            )),
            section(titleResId = R.string.profile_content_section_gdpr_rights_right_to_data_portability, items = listOf(
                textItem(titleResId = R.string.profile_content_item_gdpr_rights_taking_your_data_with_you, bodyResId = R.string.profile_content_item_body_gdpr_rights_taking_your_data_with_you),
            )),
            section(titleResId = R.string.profile_content_section_gdpr_rights_right_to_object, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_objecting_to_legitimate_interest_processing, bodyResId = R.string.profile_content_item_body_gdpr_rights_objecting_to_legitimate_interest_processing),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_direct_marketing_objection, bodyResId = R.string.profile_content_item_body_gdpr_rights_direct_marketing_objection),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_research_objection, bodyResId = R.string.profile_content_item_body_gdpr_rights_research_objection),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_objection_process, bodyResId = R.string.profile_content_item_body_gdpr_rights_objection_process),
            )),
            section(titleResId = R.string.profile_content_section_gdpr_rights_right_to_withdraw_consent, items = listOf(
                textItem(titleResId = R.string.profile_content_item_gdpr_rights_withdrawing_your_consent, bodyResId = R.string.profile_content_item_body_gdpr_rights_withdrawing_your_consent),
            )),
            section(titleResId = R.string.profile_content_section_gdpr_rights_how_to_exercise_your_gdpr_rights, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_contact_us_directly, bodyResId = R.string.profile_content_item_body_gdpr_rights_contact_us_directly),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_in_app_settings, bodyResId = R.string.profile_content_item_body_gdpr_rights_in_app_settings),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_response_timeline, bodyResId = R.string.profile_content_item_body_gdpr_rights_response_timeline),
                cardItem(titleResId = R.string.profile_content_item_gdpr_rights_supervisory_authority, bodyResId = R.string.profile_content_item_body_gdpr_rights_supervisory_authority),
            )),
        ),
    )

    private val safetyGuidelines = ContentPage(
        headingResId = R.string.profile_content_heading_safety_guidelines,
        subtitleResId = R.string.profile_content_subtitle_safety_guidelines,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_safety_guidelines_before_meeting_your_helper, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_check_their_trust_level, bodyResId = R.string.profile_content_item_body_safety_guidelines_check_their_trust_level),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_share_your_location_with_trusted_contact, bodyResId = R.string.profile_content_item_body_safety_guidelines_share_your_location_with_trusted_contact),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_choose_public_meeting_spots, bodyResId = R.string.profile_content_item_body_safety_guidelines_choose_public_meeting_spots),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_trust_your_instincts, bodyResId = R.string.profile_content_item_body_safety_guidelines_trust_your_instincts),
            )),
            section(titleResId = R.string.profile_content_section_safety_guidelines_during_the_help_interaction, items = listOf(
                textItem(titleResId = R.string.profile_content_item_safety_guidelines_staying_safe_while_getting_help, bodyResId = R.string.profile_content_item_body_safety_guidelines_staying_safe_while_getting_help),
            )),
            section(titleResId = R.string.profile_content_section_safety_guidelines_red_flags_to_watch_for, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_inappropriate_requests, bodyResId = R.string.profile_content_item_body_safety_guidelines_inappropriate_requests),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_pressure_or_urgency, bodyResId = R.string.profile_content_item_body_safety_guidelines_pressure_or_urgency),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_asking_for_payment, bodyResId = R.string.profile_content_item_body_safety_guidelines_asking_for_payment),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_inconsistent_behavior, bodyResId = R.string.profile_content_item_body_safety_guidelines_inconsistent_behavior),
            )),
            section(titleResId = R.string.profile_content_section_safety_guidelines_protecting_your_personal_information, items = listOf(
                textItem(titleResId = R.string.profile_content_item_safety_guidelines_what_to_share_and_what_to_keep_private, bodyResId = R.string.profile_content_item_body_safety_guidelines_what_to_share_and_what_to_keep_private),
            )),
            section(titleResId = R.string.profile_content_section_safety_guidelines_reporting_and_blocking, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_when_to_report_someone, bodyResId = R.string.profile_content_item_body_safety_guidelines_when_to_report_someone),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_how_to_block_users, bodyResId = R.string.profile_content_item_body_safety_guidelines_how_to_block_users),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_contact_authorities_for_serious_situations, bodyResId = R.string.profile_content_item_body_safety_guidelines_contact_authorities_for_serious_situations),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_community_reporting, bodyResId = R.string.profile_content_item_body_safety_guidelines_community_reporting),
            )),
            section(titleResId = R.string.profile_content_section_safety_guidelines_emergency_situations, items = listOf(
                textItem(titleResId = R.string.profile_content_item_safety_guidelines_when_to_contact_authorities, bodyResId = R.string.profile_content_item_body_safety_guidelines_when_to_contact_authorities),
            )),
            section(titleResId = R.string.profile_content_section_safety_guidelines_remember_your_safety_comes_first, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_trust_your_instincts_always, bodyResId = R.string.profile_content_item_body_safety_guidelines_trust_your_instincts_always),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_its_okay_to_say_no, bodyResId = R.string.profile_content_item_body_safety_guidelines_its_okay_to_say_no),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_stay_connected, bodyResId = R.string.profile_content_item_body_safety_guidelines_stay_connected),
                cardItem(titleResId = R.string.profile_content_item_safety_guidelines_help_build_a_safer_community, bodyResId = R.string.profile_content_item_body_safety_guidelines_help_build_a_safer_community),
            )),
        ),
    )

    private val faq = ContentPage(
        headingResId = R.string.profile_content_heading_faq,
        subtitleResId = R.string.profile_content_subtitle_faq,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_faq_getting_started_with_sakhi, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_faq_how_accurate_are_sakhis_period_predictions, bodyResId = R.string.profile_content_item_body_faq_how_accurate_are_sakhis_period_predictions),
                cardItem(titleResId = R.string.profile_content_item_faq_is_my_health_data_really_safe_and_private, bodyResId = R.string.profile_content_item_body_faq_is_my_health_data_really_safe_and_private),
                cardItem(titleResId = R.string.profile_content_item_faq_do_i_need_to_track_every_day, bodyResId = R.string.profile_content_item_body_faq_do_i_need_to_track_every_day),
            )),
            section(titleResId = R.string.profile_content_section_faq_using_core_features, items = listOf(
                textItem(titleResId = R.string.profile_content_item_faq_common_feature_questions, bodyResId = R.string.profile_content_item_body_faq_common_feature_questions),
            )),
            section(titleResId = R.string.profile_content_section_faq_trust_and_safety, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_faq_what_is_the_trust_level_system, bodyResId = R.string.profile_content_item_body_faq_what_is_the_trust_level_system),
                cardItem(titleResId = R.string.profile_content_item_faq_is_it_safe_to_meet_strangers_through_sakhi, bodyResId = R.string.profile_content_item_body_faq_is_it_safe_to_meet_strangers_through_sakhi),
                cardItem(titleResId = R.string.profile_content_item_faq_what_if_someone_makes_me_uncomfortable, bodyResId = R.string.profile_content_item_body_faq_what_if_someone_makes_me_uncomfortable),
            )),
            section(titleResId = R.string.profile_content_section_faq_account_and_privacy, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_faq_is_sakhi_completely_free_to_use, bodyResId = R.string.profile_content_item_body_faq_is_sakhi_completely_free_to_use),
                cardItem(titleResId = R.string.profile_content_item_faq_how_do_i_delete_my_account, bodyResId = R.string.profile_content_item_body_faq_how_do_i_delete_my_account),
            )),
        ),
    )

    private val aboutUs = ContentPage(
        headingResId = R.string.profile_content_heading_about_us,
        subtitleResId = R.string.profile_content_subtitle_about_us,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_about_us_where_it_all_started, items = listOf(
                textItem(titleResId = R.string.profile_content_item_about_us_the_beginning_of_sakhi, bodyResId = R.string.profile_content_item_body_about_us_the_beginning_of_sakhi),
            )),
            section(titleResId = R.string.profile_content_section_about_us_the_inspiration_behind_our_mission, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_about_us_understanding_the_real_problem, bodyResId = R.string.profile_content_item_body_about_us_understanding_the_real_problem),
                cardItem(titleResId = R.string.profile_content_item_about_us_the_power_of_community, bodyResId = R.string.profile_content_item_body_about_us_the_power_of_community),
                cardItem(titleResId = R.string.profile_content_item_about_us_breaking_the_silence, bodyResId = R.string.profile_content_item_body_about_us_breaking_the_silence),
                cardItem(titleResId = R.string.profile_content_item_about_us_technology_for_good, bodyResId = R.string.profile_content_item_body_about_us_technology_for_good),
            )),
            section(titleResId = R.string.profile_content_section_about_us_building_sakhi_the_early_days, items = listOf(
                textItem(titleResId = R.string.profile_content_item_about_us_the_development_journey, bodyResId = R.string.profile_content_item_body_about_us_the_development_journey),
            )),
            section(titleResId = R.string.profile_content_section_about_us_the_vision_takes_shape, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_about_us_more_than_just_tracking, bodyResId = R.string.profile_content_item_body_about_us_more_than_just_tracking),
                cardItem(titleResId = R.string.profile_content_item_about_us_trust_based_community, bodyResId = R.string.profile_content_item_body_about_us_trust_based_community),
                cardItem(titleResId = R.string.profile_content_item_about_us_privacy_as_a_foundation, bodyResId = R.string.profile_content_item_body_about_us_privacy_as_a_foundation),
                cardItem(titleResId = R.string.profile_content_item_about_us_technology_with_purpose, bodyResId = R.string.profile_content_item_body_about_us_technology_with_purpose),
            )),
            section(titleResId = R.string.profile_content_section_about_us_the_sakhi_philosophy, items = listOf(
                textItem(titleResId = R.string.profile_content_item_about_us_the_heart_of_sakhi, bodyResId = R.string.profile_content_item_body_about_us_the_heart_of_sakhi),
            )),
            section(titleResId = R.string.profile_content_section_about_us_real_impact, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_about_us_changing_lives_daily, bodyResId = R.string.profile_content_item_body_about_us_changing_lives_daily),
                cardItem(titleResId = R.string.profile_content_item_about_us_breaking_stigma, bodyResId = R.string.profile_content_item_body_about_us_breaking_stigma),
                cardItem(titleResId = R.string.profile_content_item_about_us_community_growth, bodyResId = R.string.profile_content_item_body_about_us_community_growth),
                cardItem(titleResId = R.string.profile_content_item_about_us_looking_forward, bodyResId = R.string.profile_content_item_body_about_us_looking_forward),
            )),
            section(titleResId = R.string.profile_content_section_about_us_join_our_mission, items = listOf(
                textItem(titleResId = R.string.profile_content_item_about_us_be_part_of_the_story, bodyResId = R.string.profile_content_item_body_about_us_be_part_of_the_story),
            )),
        ),
    )

    private val credits = ContentPage(
        headingResId = R.string.profile_content_heading_credits,
        subtitleResId = R.string.profile_content_subtitle_credits,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_credits_core_development_team, items = listOf(
                textItem(titleResId = R.string.profile_content_item_credits_karan_kumar_lead_developer_and_designer, bodyResId = R.string.profile_content_item_body_credits_karan_kumar_lead_developer_and_designer),
            )),
            section(titleResId = R.string.profile_content_section_credits_research_and_strategy, items = listOf(
                textItem(titleResId = R.string.profile_content_item_credits_arpita_gupta_lead_researcher_and_business_strategist, bodyResId = R.string.profile_content_item_body_credits_arpita_gupta_lead_researcher_and_business_strategist),
            )),
            section(titleResId = R.string.profile_content_section_credits_founding_contributors, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_credits_aman_prakash, bodyResId = R.string.profile_content_item_body_credits_aman_prakash),
                cardItem(titleResId = R.string.profile_content_item_credits_shweta_kumari, bodyResId = R.string.profile_content_item_body_credits_shweta_kumari),
            )),
            section(titleResId = R.string.profile_content_section_credits_mentorship_and_guidance, items = listOf(
                textItem(titleResId = R.string.profile_content_item_credits_dr_shruti_sachdeva_project_mentor_and_guiding_force, bodyResId = R.string.profile_content_item_body_credits_dr_shruti_sachdeva_project_mentor_and_guiding_force),
            )),
            section(titleResId = R.string.profile_content_section_credits_special_acknowledgments, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_credits_academic_support, bodyResId = R.string.profile_content_item_body_credits_academic_support),
                cardItem(titleResId = R.string.profile_content_item_credits_healthcare_professional_advisory, bodyResId = R.string.profile_content_item_body_credits_healthcare_professional_advisory),
                cardItem(titleResId = R.string.profile_content_item_credits_beta_testing_community, bodyResId = R.string.profile_content_item_body_credits_beta_testing_community),
                cardItem(titleResId = R.string.profile_content_item_credits_open_source_community, bodyResId = R.string.profile_content_item_body_credits_open_source_community),
            )),
            section(titleResId = R.string.profile_content_section_credits_technology_stack, items = listOf(
                textItem(titleResId = R.string.profile_content_item_credits_sakhis_technological_foundation, bodyResId = R.string.profile_content_item_body_credits_sakhis_technological_foundation),
            )),
        ),
    )

    private val openSourceLicenses = ContentPage(
        headingResId = R.string.profile_content_heading_open_source_licenses,
        subtitleResId = R.string.profile_content_subtitle_open_source_licenses,
        sections = listOf(
            section(titleResId = R.string.profile_content_section_open_source_licenses_our_commitment_to_open_source, items = listOf(
                textItem(titleResId = R.string.profile_content_item_open_source_licenses_standing_on_the_shoulders_of_giants, bodyResId = R.string.profile_content_item_body_open_source_licenses_standing_on_the_shoulders_of_giants),
            )),
            section(titleResId = R.string.profile_content_section_open_source_licenses_core_libraries, style = ContentSectionStyle.CARDS, items = listOf(
                cardItem(titleResId = R.string.profile_content_item_open_source_licenses_jetpack_compose, bodyResId = R.string.profile_content_item_body_open_source_licenses_jetpack_compose),
                cardItem(titleResId = R.string.profile_content_item_open_source_licenses_kotlin_multiplatform, bodyResId = R.string.profile_content_item_body_open_source_licenses_kotlin_multiplatform),
                cardItem(titleResId = R.string.profile_content_item_open_source_licenses_koin, bodyResId = R.string.profile_content_item_body_open_source_licenses_koin),
                cardItem(titleResId = R.string.profile_content_item_open_source_licenses_supabase_kt, bodyResId = R.string.profile_content_item_body_open_source_licenses_supabase_kt),
            )),
            section(titleResId = R.string.profile_content_section_open_source_licenses_ui_and_experience_libraries, items = listOf(
                textItem(titleResId = R.string.profile_content_item_open_source_licenses_user_interface_libraries_used_in_sakhi, bodyResId = R.string.profile_content_item_body_open_source_licenses_user_interface_libraries_used_in_sakhi),
            )),
            section(titleResId = R.string.profile_content_section_open_source_licenses_development_and_testing_tools, items = listOf(
                textItem(titleResId = R.string.profile_content_item_open_source_licenses_development_tools, bodyResId = R.string.profile_content_item_body_open_source_licenses_development_tools),
            )),
            section(titleResId = R.string.profile_content_section_open_source_licenses_license_acknowledgment, items = listOf(
                textItem(titleResId = R.string.profile_content_item_open_source_licenses_gratitude_and_compliance, bodyResId = R.string.profile_content_item_body_open_source_licenses_gratitude_and_compliance),
            )),
        ),
    )
}
