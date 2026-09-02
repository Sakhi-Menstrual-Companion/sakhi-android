package team.sakhi.android.feature.profile

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import team.sakhi.android.testing.MainDispatcherRule
import org.junit.Test
import team.sakhi.repositories.SanityFaq
import team.sakhi.repositories.SanityLegalPage
import team.sakhi.repositories.SanityLocalizedString
import team.sakhi.repositories.SanityLocalizedText
import team.sakhi.repositories.SanityRepository
import team.sakhi.repositories.SanitySiteSettings

/**
 * State-machine test for `SanityContentViewModel`. `SanityRepository` is a
 * concrete, non-open KMM class (same situation as this session's other ViewModel
 * tests), mocked at that one boundary. Uses the real, internal
 * `liveSanitySlugs()` (defined in `ContentPageScreen.kt`, same module) for the
 * real slug list rather than a hardcoded guess, so this test tracks the real
 * `ContentPageId` set instead of drifting from it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SanityContentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val testDispatcher get() = mainDispatcherRule.testDispatcher



    private fun legalPage(title: String) = SanityLegalPage(title = SanityLocalizedString(en = title))

    private fun faq(question: String) = SanityFaq(
        question = SanityLocalizedString(en = question),
        answer = SanityLocalizedText(en = "answer"),
    )

    @Test
    fun `a fully successful refresh populates real legal pages, faqs, and site settings`() = runTest {
        val slugs = liveSanitySlugs()
        val repository = mockk<SanityRepository> {
            coEvery { legalPage(any()) } answers { Result.success(legalPage(title = firstArg())) }
            coEvery { faqs() } returns Result.success(listOf(faq("Q1"), faq("Q2")))
            coEvery { siteSettings() } returns Result.success(SanitySiteSettings(appName = "Sakhi"))
        }
        val viewModel = SanityContentViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(slugs.size, state.legalPages.size)
        slugs.forEach { slug -> assertEquals(slug, state.legalPages.getValue(slug).title.en) }
        assertEquals(2, state.faqs.size)
        assertEquals("Sakhi", state.siteSettings?.appName)
        coVerify(exactly = 1) { repository.faqs() }
        coVerify(exactly = 1) { repository.siteSettings() }
        slugs.forEach { slug -> coVerify(exactly = 1) { repository.legalPage(slug) } }
    }

    @Test
    fun `one failing legal page slug is excluded while the rest still populate`() = runTest {
        val slugs = liveSanitySlugs()
        require(slugs.size >= 2) { "Test needs at least 2 real slugs to prove partial failure isolation" }
        val failingSlug = slugs.first()
        val repository = mockk<SanityRepository> {
            coEvery { legalPage(failingSlug) } returns Result.failure(RuntimeException("cms down"))
            coEvery { legalPage(match { it != failingSlug }) } answers {
                Result.success(legalPage(title = firstArg()))
            }
            coEvery { faqs() } returns Result.success(emptyList())
            coEvery { siteSettings() } returns Result.success(null)
        }
        val viewModel = SanityContentViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(failingSlug !in state.legalPages)
        assertEquals(slugs.size - 1, state.legalPages.size)
        (slugs - failingSlug).forEach { slug -> assertTrue(slug in state.legalPages) }
    }

    @Test
    fun `every legal page failing still leaves faqs and site settings intact, no crash`() = runTest {
        val slugs = liveSanitySlugs()
        val repository = mockk<SanityRepository> {
            coEvery { legalPage(any()) } returns Result.failure(RuntimeException("cms down"))
            coEvery { faqs() } returns Result.success(listOf(faq("Q1")))
            coEvery { siteSettings() } returns Result.success(SanitySiteSettings(appName = "Sakhi"))
        }
        val viewModel = SanityContentViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.legalPages.isEmpty())
        assertEquals(1, state.faqs.size)
        assertEquals("Sakhi", state.siteSettings?.appName)
        slugs.forEach { slug -> coVerify(exactly = 1) { repository.legalPage(slug) } }
    }

    @Test
    fun `a failing faqs fetch resolves to an empty list instead of crashing`() = runTest {
        val repository = mockk<SanityRepository> {
            coEvery { legalPage(any()) } returns Result.success(null)
            coEvery { faqs() } returns Result.failure(RuntimeException("cms down"))
            coEvery { siteSettings() } returns Result.success(null)
        }
        val viewModel = SanityContentViewModel(repository)

        advanceUntilIdle()

        assertEquals(emptyList<SanityFaq>(), viewModel.uiState.value.faqs)
    }

    @Test
    fun `a failing site settings fetch leaves the safe default null instead of crashing`() = runTest {
        val repository = mockk<SanityRepository> {
            coEvery { legalPage(any()) } returns Result.success(null)
            coEvery { faqs() } returns Result.success(emptyList())
            coEvery { siteSettings() } returns Result.failure(RuntimeException("cms down"))
        }
        val viewModel = SanityContentViewModel(repository)

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.siteSettings)
    }

    @Test
    fun `legal pages, faqs, and site settings are fetched concurrently, not sequentially`() = runTest {
        // Each call blocks on the same never-yet-completed gate. If the ViewModel
        // awaited legalPage/faqs/siteSettings one at a time instead of launching
        // them all via async first, faqs()/siteSettings() would never even be
        // *called* until every legal-page slug finished -- so verifying all three
        // were already invoked while still blocked on the gate proves real
        // concurrent dispatch, not just an eventual correct result.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = mockk<SanityRepository> {
            coEvery { legalPage(any()) } coAnswers {
                gate.await()
                Result.success(null)
            }
            coEvery { faqs() } coAnswers {
                gate.await()
                Result.success(emptyList())
            }
            coEvery { siteSettings() } coAnswers {
                gate.await()
                Result.success(null)
            }
        }
        SanityContentViewModel(repository)
        advanceUntilIdle()

        // All three were already called and are simultaneously suspended on the
        // gate -- proof of concurrent, not sequential, dispatch.
        coVerify(exactly = 1) { repository.faqs() }
        coVerify(exactly = 1) { repository.siteSettings() }
        liveSanitySlugs().forEach { slug -> coVerify(exactly = 1) { repository.legalPage(slug) } }

        gate.complete(Unit)
        advanceUntilIdle()
    }
}
