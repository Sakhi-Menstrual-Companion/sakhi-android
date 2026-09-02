package team.sakhi.android.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher for the duration of a test.
 *
 * Every ViewModel here launches on `viewModelScope`, which is `Dispatchers.Main`. Without
 * this, that dispatcher has no thread in a local JUnit run and the test fails on the first
 * `launch`.
 *
 * This existed as an identical `@Before` / `@After` pair in FOURTEEN test files:
 *
 *     @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
 *     @After fun tearDown() { Dispatchers.resetMain() }
 *
 * A rule is better than a copy of that pair for one concrete reason: `resetMain()` in an
 * `@After` is skipped if the test fails early in a way that throws out of `@Before`, which
 * leaks the dispatcher into whatever test runs next and produces a failure in an unrelated
 * file. `TestWatcher` always runs its `finished` hook.
 *
 * Defaults to [StandardTestDispatcher], which is what all fourteen migrated tests used. That
 * matters: Standard QUEUES coroutines until the test advances the scheduler, while
 * `UnconfinedTestDispatcher` runs them EAGERLY at the launch point. Swapping one for the other
 * changes when work happens relative to assertions.
 *
 * This default was originally written as Unconfined, on the assumption the tests used it. They
 * did not, and the result was `ChatViewModelTest` failing intermittently — work that used to
 * wait for `advanceUntilIdle()` began running early. Do not change this default to "fix" a
 * single test: pass the dispatcher that test wants as the constructor argument instead.
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
