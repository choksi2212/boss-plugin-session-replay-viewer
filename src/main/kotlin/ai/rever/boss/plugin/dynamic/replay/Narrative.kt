package ai.rever.boss.plugin.dynamic.replay

/**
 * Generates a one-paragraph narrative for a recorded step.
 *
 * The narrative is plain English so a non-technical reader can follow what a
 * recorded session did without opening the file. It carries three pieces of
 * context that the JSON view hides:
 *  - what kind of action it was (click / type / navigate / wait / ...)
 *  - what was targeted, by selector and (when present) by visible text
 *  - how long after the previous step it fired, and whether the URL changed
 */
object Narrative {

    /**
     * Build the narrative for one step.
     *
     * `previous` may be null when the step is the very first in the session.
     * The `relativeToStart` field is a monotonic "ms since session began" that
     * the caller computes against the session's first step.
     */
    fun forStep(
        step: RecordedStep,
        previous: RecordedStep?,
        relativeToStart: Long,
        previousRelativeToStart: Long,
    ): String {
        val delta = relativeToStart - previousRelativeToStart
        val delayPhrase = when {
            previous == null -> "At the start of the recording"
            delta < 0 -> "without waiting"
            delta == 0L -> "immediately"
            delta < 1_000L -> "${delta}ms later"
            delta < 60_000L -> "${"%.1f".format(delta / 1_000.0)}s later"
            else -> "${delta / 60_000L}m ${(delta % 60_000L) / 1_000L}s later"
        }
        val verbPhrase = verb(step)
        val targetPhrase = target(step)
        val valuePhrase = value(step)
        val urlPhrase = urlChange(step, previous)
        val atPhrase = "at ${formatRelative(relativeToStart)}"

        val middle = buildString {
            append(verbPhrase)
            if (targetPhrase.isNotEmpty()) {
                append(' ')
                append(targetPhrase)
            }
            if (valuePhrase.isNotEmpty()) {
                append(' ')
                append(valuePhrase)
            }
        }.trim()

        return buildString {
            append(delayPhrase)
            append(", ")
            append(middle)
            if (urlPhrase.isNotEmpty()) {
                append(". ")
                append(urlPhrase)
            }
            append(' ')
            append('(')
            append(atPhrase)
            append(')')
            append('.')
        }
    }

    /**
     * The Markdown export of a session narrative, one paragraph per step.
     * Used by the "Copy narrative" button and the `replay_step_narrative` tool.
     */
    fun markdownFor(
        session: RecordedSession,
        actions: List<RecordedStep> = session.actions,
    ): String {
        if (actions.isEmpty()) {
            return buildString {
                appendLine("# ${session.name.ifBlank { "Session Replay" }}")
                if (session.description.isNotBlank()) {
                    appendLine()
                    appendLine(session.description)
                }
                appendLine()
                appendLine("No actions recorded.")
            }
        }
        val first = actions.first().timestamp
        val sb = StringBuilder()
        sb.append("# ${session.name.ifBlank { "Session Replay" }}")
        if (session.description.isNotBlank()) {
            sb.append("\n\n").append(session.description)
        }
        sb.append("\n\n")
        sb.append("- **Actions:** ${actions.size}\n")
        sb.append("- **First timestamp:** ${first}\n")
        val last = actions.last().timestamp
        if (last > first) {
            sb.append("- **Duration:** ${formatDuration(last - first)}\n")
        }
        val breakdown = breakdownFor(actions)
        if (breakdown.isNotEmpty()) {
            sb.append("- **Breakdown:** ")
            sb.append(breakdown.entries.joinToString { (k, v) -> "$k=$v" })
            sb.append('\n')
        }
        sb.append('\n')
        actions.forEachIndexed { idx, step ->
            val prev = if (idx == 0) null else actions[idx - 1]
            val relStart = if (first == 0L) 0L else step.timestamp - first
            val prevRelStart = if (idx == 0 || first == 0L) 0L else prev!!.timestamp - first
            sb.append("**Step ${idx + 1}.** ")
            sb.append(forStep(step, prev, relStart, prevRelStart))
            sb.append("\n\n")
        }
        return sb.toString().trimEnd()
    }

    /**
     * Action-type breakdown for `replay_session_summary`.
     */
    fun breakdownFor(steps: List<RecordedStep>): Map<String, Int> {
        if (steps.isEmpty()) return emptyMap()
        val counts = linkedMapOf<String, Int>()
        steps.forEach { s ->
            val key = s.label.lowercase()
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts
    }

    private fun verb(step: RecordedStep): String = when (step.type.lowercase()) {
        "click" -> "clicked"
        "input", "type" -> "typed into"
        "select" -> "selected an option in"
        "navigate", "navigation" -> "navigated to"
        "wait" -> "waited"
        "scroll" -> "scrolled"
        "screenshot" -> "took a screenshot of"
        "assert", "assertion" -> "asserted on"
        "" -> "performed an action on"
        else -> "performed '${step.type}' on"
    }

    private fun target(step: RecordedStep): String {
        val sel = step.selector
        val kind = sel.display()
        val value = sel.value?.takeIf { it.isNotBlank() }
        val text = step.elementText?.takeIf { it.isNotBlank() }?.let { "'$it'" }
        return when {
            value != null && text != null && kind.isNotEmpty() -> "the $text element by $kind `$value`"
            value != null && kind.isNotEmpty() -> "the $kind `$value` element"
            text != null -> "the element labelled $text"
            step.elementType != null -> "the <${step.elementType}> element"
            else -> "an element"
        }
    }

    private fun value(step: RecordedStep): String {
        val v = step.value?.takeIf { it.isNotBlank() } ?: return ""
        return when (step.type.lowercase()) {
            "input", "type" -> "with value \"$v\""
            "select" -> "with value \"$v\""
            "navigate", "navigation" -> "to \"$v\""
            "wait" -> "for $v"
            "scroll" -> "to $v"
            else -> "($v)"
        }
    }

    private fun urlChange(step: RecordedStep, previous: RecordedStep?): String {
        val cur = step.url?.takeIf { it.isNotBlank() } ?: return ""
        val prev = previous?.url?.takeIf { it.isNotBlank() }
        return when {
            prev == null -> "URL was $cur"
            prev == cur -> ""
            else -> "URL changed from $prev to $cur"
        }
    }

    private fun formatRelative(ms: Long): String {
        if (ms <= 0) return "t=0"
        val totalSec = ms / 1_000.0
        return when {
            totalSec < 60 -> "${"%.2f".format(totalSec)}s"
            totalSec < 3_600 -> "${totalSec / 60.0}m"
            else -> "${totalSec / 3_600.0}h"
        }
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0s"
        val totalSec = ms / 1_000
        val h = totalSec / 3_600
        val m = (totalSec % 3_600) / 60
        val s = totalSec % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0) append("${m}m ")
            append("${s}s")
        }.trim()
    }
}
