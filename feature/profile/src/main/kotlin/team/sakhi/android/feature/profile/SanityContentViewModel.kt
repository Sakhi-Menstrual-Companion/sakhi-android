package team.sakhi.android.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import team.sakhi.repositories.SanityFaq
import team.sakhi.repositories.SanityLegalPage
import team.sakhi.repositories.SanityRepository
import team.sakhi.repositories.SanitySiteSettings

data class SanityContentUiState(
    val legalPages: Map<String, SanityLegalPage> = emptyMap(),
    val faqs: List<SanityFaq> = emptyList(),
    val siteSettings: SanitySiteSettings? = null,
)

class SanityContentViewModel(
    private val sanityRepository: SanityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SanityContentUiState())
    val uiState: StateFlow<SanityContentUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            supervisorScope {
                val legalPagesDeferred = liveSanitySlugs().map { slug ->
                    async { slug to sanityRepository.legalPage(slug).getOrNull() }
                }
                val faqsDeferred = async { sanityRepository.faqs().getOrNull().orEmpty() }
                val siteSettingsDeferred = async { sanityRepository.siteSettings().getOrNull() }

                val legalPages = legalPagesDeferred.awaitAll()
                    .mapNotNull { (slug, page) -> page?.let { slug to it } }
                    .toMap()

                _uiState.update {
                    it.copy(
                        legalPages = it.legalPages + legalPages,
                        faqs = faqsDeferred.await(),
                        siteSettings = siteSettingsDeferred.await() ?: it.siteSettings,
                    )
                }
            }
        }
    }
}
