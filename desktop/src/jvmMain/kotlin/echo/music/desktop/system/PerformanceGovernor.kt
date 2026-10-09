package echo.music.desktop.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class RenderBudget {
  FULL,
  THROTTLED,
}

interface WindowVisibilitySource {
  val isMinimized: StateFlow<Boolean>
  val isVisible: StateFlow<Boolean>
}

class MutableWindowVisibilitySource(
  minimized: Boolean = false,
  visible: Boolean = true,
) : WindowVisibilitySource {

  private val _isMinimized = MutableStateFlow(minimized)
  override val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

  private val _isVisible = MutableStateFlow(visible)
  override val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

  fun setMinimized(value: Boolean) {
    _isMinimized.value = value
  }

  fun setVisible(value: Boolean) {
    _isVisible.value = value
  }
}

fun interface FrameClockController {
  fun setBudget(budget: RenderBudget)
}

class PerformanceGovernor(
  private val source: WindowVisibilitySource,
  private val scope: CoroutineScope,
  private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
  private val onBudgetChanged: (RenderBudget) -> Unit,
) {

  private val _budget = MutableStateFlow(RenderBudget.FULL)
  val budget: StateFlow<RenderBudget> = _budget.asStateFlow()

  private val observeJob: Job

  init {
    observeJob = scope.launch {
      combine(source.isMinimized, source.isVisible) { minimized, visible ->
          !minimized && visible
        }
        .distinctUntilChanged()
        .collectLatest { active ->
          if (active) {
            applyBudget(RenderBudget.FULL)
          } else {
            delay(debounceMillis)
            applyBudget(RenderBudget.THROTTLED)
          }
        }
    }
  }

  fun stop() {
    observeJob.cancel()
  }

  private fun applyBudget(budget: RenderBudget) {
    if (_budget.value == budget) return
    _budget.value = budget
    onBudgetChanged(budget)
  }

  companion object {
    const val DEFAULT_DEBOUNCE_MILLIS = 150L
  }
}
