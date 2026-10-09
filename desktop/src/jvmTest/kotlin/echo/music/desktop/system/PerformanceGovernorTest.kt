package echo.music.desktop.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class PerformanceGovernorTest {

  @Test
  fun startsWithFullBudget() = runTest {
    val source = MutableWindowVisibilitySource()
    val governor = PerformanceGovernor(source, backgroundScope) {}
    assertEquals(RenderBudget.FULL, governor.budget.value)
  }

  @Test
  fun minimizedThrottlesAfterDebounce() = runTest {
    val source = MutableWindowVisibilitySource()
    val budgets = mutableListOf<RenderBudget>()
    val governor = PerformanceGovernor(source, backgroundScope) { budgets.add(it) }

    source.setMinimized(true)
    runCurrent()
    assertEquals(RenderBudget.FULL, governor.budget.value)

    advanceTimeBy(PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS * 2)
    assertEquals(RenderBudget.THROTTLED, governor.budget.value)
    assertEquals(listOf(RenderBudget.THROTTLED), budgets)
  }

  @Test
  fun hiddenThrottlesAfterDebounce() = runTest {
    val source = MutableWindowVisibilitySource()
    val budgets = mutableListOf<RenderBudget>()
    val governor = PerformanceGovernor(source, backgroundScope) { budgets.add(it) }

    source.setVisible(false)
    advanceTimeBy(PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS * 2)
    assertEquals(RenderBudget.THROTTLED, governor.budget.value)
    assertEquals(listOf(RenderBudget.THROTTLED), budgets)
  }

  @Test
  fun restoreAppliesFullBudgetImmediately() = runTest {
    val source = MutableWindowVisibilitySource()
    val budgets = mutableListOf<RenderBudget>()
    val governor = PerformanceGovernor(source, backgroundScope) { budgets.add(it) }

    source.setMinimized(true)
    advanceTimeBy(PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS * 2)
    assertEquals(RenderBudget.THROTTLED, governor.budget.value)

    source.setMinimized(false)
    runCurrent()
    assertEquals(RenderBudget.FULL, governor.budget.value)
    assertEquals(listOf(RenderBudget.THROTTLED, RenderBudget.FULL), budgets)
  }

  @Test
  fun visibleButNotMinimizedStaysFull() = runTest {
    val source = MutableWindowVisibilitySource(minimized = false, visible = true)
    val budgets = mutableListOf<RenderBudget>()
    val governor = PerformanceGovernor(source, backgroundScope) { budgets.add(it) }

    advanceTimeBy(PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS * 2)
    assertEquals(RenderBudget.FULL, governor.budget.value)
    assertEquals(emptyList(), budgets)
  }

  @Test
  fun quickMinimizeAndRestoreWithinDebounceStaysFull() = runTest {
    val source = MutableWindowVisibilitySource()
    val budgets = mutableListOf<RenderBudget>()
    val governor = PerformanceGovernor(source, backgroundScope) { budgets.add(it) }

    source.setMinimized(true)
    advanceTimeBy(PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS / 2)
    source.setMinimized(false)
    advanceTimeBy(PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS * 5)

    assertEquals(RenderBudget.FULL, governor.budget.value)
    assertEquals(emptyList(), budgets)
  }

  @Test
  fun repeatedFlappingEndsInCorrectState() = runTest {
    val source = MutableWindowVisibilitySource()
    val budgets = mutableListOf<RenderBudget>()
    val governor = PerformanceGovernor(source, backgroundScope) { budgets.add(it) }

    repeat(3) {
      source.setMinimized(true)
      advanceTimeBy(PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS * 2)
      source.setMinimized(false)
      runCurrent()
    }

    assertEquals(RenderBudget.FULL, governor.budget.value)
    assertEquals(
      listOf(
        RenderBudget.THROTTLED,
        RenderBudget.FULL,
        RenderBudget.THROTTLED,
        RenderBudget.FULL,
        RenderBudget.THROTTLED,
        RenderBudget.FULL,
      ),
      budgets,
    )
  }

  @Test
  fun stopStopsObserving() = runTest {
    val source = MutableWindowVisibilitySource()
    val budgets = mutableListOf<RenderBudget>()
    val governor = PerformanceGovernor(source, backgroundScope) { budgets.add(it) }

    governor.stop()
    source.setMinimized(true)
    advanceTimeBy(PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS * 2)
    assertEquals(RenderBudget.FULL, governor.budget.value)
    assertEquals(emptyList(), budgets)
  }
}
