package ai.rever.boss.plugin.dynamic.replay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the Session Replay Viewer panel.
 *
 * Holds the loaded session, the playback position, transport state, and the
 * auto-advance timer. The MCP tools share the same parser, but they read
 * sessions on demand and never mutate this state.
 */
class ReplayViewModel(
    private val parser: SessionParser,
    private val clipboard: ClipboardHelper,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _session = MutableStateFlow<RecordedSession?>(null)
    val session: StateFlow<RecordedSession?> = _session.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _speed = MutableStateFlow(1.0)
    val speed: StateFlow<Double> = _speed.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availableSessions = MutableStateFlow<List<String>>(emptyList())
    val availableSessions: StateFlow<List<String>> = _availableSessions.asStateFlow()

    private var autoAdvanceJob: Job? = null

    /** Total duration of the loaded session in ms, derived from the first/last action timestamps. */
    val totalDurationMs: Long?
        get() {
            val s = _session.value ?: return null
            val first = s.actions.firstOrNull()?.timestamp ?: 0L
            val last = s.actions.lastOrNull()?.timestamp ?: first
            return (last - first).takeIf { it > 0 }
        }

    /** Timestamp of the step at [currentIndex], or 0 if no session. */
    fun currentTimestamp(): Long =
        _session.value?.actions?.getOrNull(_currentIndex.value)?.timestamp ?: 0L

    /** Timestamp of the first step in the session, or 0 if no session. */
    fun sessionStartTimestamp(): Long =
        _session.value?.actions?.firstOrNull()?.timestamp ?: 0L

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    /**
     * Load a session from [path]. Cancels playback if running.
     */
    fun loadSession(path: String) {
        if (path.isBlank()) {
            _errorMessage.value = "Pick a session file first."
            return
        }
        scope.launch {
            _isLoading.value = true
            try {
                val res = parser.parse(path)
                res.fold(
                    onSuccess = { s ->
                        _session.value = s
                        _currentIndex.value = 0
                        _playing.value = false
                        cancelAutoAdvance()
                        _statusMessage.value =
                            "Loaded ${s.actions.size} actions from ${path.substringAfterLast('/')}"
                        _errorMessage.value = null
                    },
                    onFailure = { e ->
                        _errorMessage.value =
                            "Failed to parse session: ${e.message ?: e::class.simpleName.orEmpty()}"
                    },
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * List the `.json` files in [directory] and store them as session candidates.
     */
    fun listSessions(directory: String) {
        if (directory.isBlank()) {
            _errorMessage.value = "Pick a directory first."
            return
        }
        scope.launch {
            val files = parser.listSessionsInDirectory(directory)
            _availableSessions.value = files
            if (files.isEmpty()) {
                _statusMessage.value = "No .json files in $directory"
            } else {
                _statusMessage.value = "Found ${files.size} candidate session file(s)"
            }
        }
    }

    fun play() {
        val s = _session.value ?: return
        if (s.actions.isEmpty()) return
        if (_currentIndex.value >= s.actions.lastIndex) {
            // Restart at the beginning if we hit the end.
            _currentIndex.value = 0
        }
        _playing.value = true
        startAutoAdvance()
    }

    fun pause() {
        _playing.value = false
        cancelAutoAdvance()
    }

    fun togglePlay() {
        if (_playing.value) pause() else play()
    }

    fun stepForward() {
        val s = _session.value ?: return
        if (_currentIndex.value < s.actions.lastIndex) {
            _currentIndex.value = _currentIndex.value + 1
        } else if (_playing.value) {
            pause()
        }
    }

    fun stepBack() {
        if (_currentIndex.value > 0) {
            _currentIndex.value = _currentIndex.value - 1
        }
    }

    fun jumpToStart() {
        _currentIndex.value = 0
    }

    fun jumpToEnd() {
        val s = _session.value ?: return
        if (s.actions.isNotEmpty()) {
            _currentIndex.value = s.actions.lastIndex
        }
    }

    fun jumpTo(index: Int) {
        val s = _session.value ?: return
        if (index in s.actions.indices) {
            _currentIndex.value = index
        }
    }

    fun setSpeed(speed: Double) {
        if (speed <= 0.0) return
        _speed.value = speed
        if (_playing.value) startAutoAdvance()
    }

    /**
     * Copy the current session's narrative as Markdown to the clipboard.
     * Returns true on success.
     */
    fun copyNarrativeToClipboard(): Boolean {
        val s = _session.value ?: run {
            _errorMessage.value = "No session loaded."
            return false
        }
        val md = Narrative.markdownFor(s)
        val ok = clipboard.copy(md)
        if (ok) {
            _statusMessage.value = "Narrative copied to clipboard"
        } else {
            _errorMessage.value = "Could not copy narrative - clipboard unavailable."
        }
        return ok
    }

    /**
     * Copy only the current step's narrative paragraph to the clipboard.
     */
    fun copyStepNarrativeToClipboard(): Boolean {
        val s = _session.value ?: run {
            _errorMessage.value = "No session loaded."
            return false
        }
        val idx = _currentIndex.value
        val step = s.actions.getOrNull(idx) ?: return false
        val first = s.actions.first().timestamp
        val previous = s.actions.getOrNull(idx - 1)
        val prevFirst = previous?.timestamp ?: first
        val relStart = if (first == 0L) 0L else step.timestamp - first
        val prevRelStart = if (first == 0L) 0L else prevFirst - first
        val paragraph = Narrative.forStep(step, previous, relStart, prevRelStart)
        val ok = clipboard.copy(paragraph)
        if (ok) {
            _statusMessage.value = "Step ${idx + 1} narrative copied"
        } else {
            _errorMessage.value = "Could not copy - clipboard unavailable."
        }
        return ok
    }

    private fun startAutoAdvance() {
        cancelAutoAdvance()
        autoAdvanceJob = scope.launch {
            val baseDelayMs = 1_000L // 1s between steps at 1x
            while (isActive && _playing.value) {
                val s = _session.value
                if (s == null || s.actions.isEmpty()) {
                    _playing.value = false
                    return@launch
                }
                if (_currentIndex.value >= s.actions.lastIndex) {
                    _playing.value = false
                    return@launch
                }
                delay((baseDelayMs / _speed.value).toLong().coerceAtLeast(50L))
                if (isActive && _playing.value) {
                    _currentIndex.value = (_currentIndex.value + 1).coerceAtMost(s.actions.lastIndex)
                    if (_currentIndex.value >= s.actions.lastIndex) {
                        _playing.value = false
                        return@launch
                    }
                }
            }
        }
    }

    private fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    fun shutdown() {
        cancelAutoAdvance()
    }
}
