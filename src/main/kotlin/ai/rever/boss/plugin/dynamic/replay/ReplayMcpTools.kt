package ai.rever.boss.plugin.dynamic.replay

import ai.rever.boss.plugin.api.FileSystemDataProvider
import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult

/**
 * MCP tools contributed by the Session Replay Viewer plugin.
 *
 * Three tools, all read-only:
 * - `replay_session_summary(path)` - action count, duration, per-action breakdown.
 * - `replay_step_narrative(path, stepIndex)` - the narrative paragraph for one step.
 * - `replay_open_session(path)` - parsed session summary as a text block.
 *
 * They share one [SessionParser] for the read path. The parser bounds file size
 * at [SessionParser.MAX_SESSION_BYTES] so an agent asking for a giant file gets
 * a clear refusal rather than a host freeze.
 */
internal class ReplayMcpToolProvider(
    override val providerId: String,
    private val fileSystemDataProvider: FileSystemDataProvider?,
) : McpToolProvider {

    private val parser = SessionParser(fileSystemDataProvider)

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "replay_session_summary",
            description = "Summarize an rparecorder session file: action count, total duration " +
                "(ms), first/last action timestamps, and an action-type breakdown.",
            inputSchema = PATH_SCHEMA,
            handler = McpToolHandler { args -> pathOp(args) { session -> summary(session) } },
        ),
        McpToolDefinition(
            name = "replay_step_narrative",
            description = "Return the one-paragraph narrative for one step in a recorded session. " +
                "Use 'stepIndex' (zero-based) to choose the step.",
            inputSchema = PATH_AND_INDEX_SCHEMA,
            handler = McpToolHandler { args -> pathOp(args) { session -> stepNarrative(args, session) } },
        ),
        McpToolDefinition(
            name = "replay_open_session",
            description = "Parse an rparecorder session file and return a short textual overview " +
                "(name, description, action count, first/last timestamps, action-type breakdown).",
            inputSchema = PATH_SCHEMA,
            handler = McpToolHandler { args -> pathOp(args) { session -> overview(session) } },
        ),
    )

    private suspend fun pathOp(args: McpToolArgs, body: (RecordedSession) -> McpToolResult): McpToolResult {
        val path = args.string("path")
            ?: return McpToolResult("Missing required argument: path", isError = true)
        return try {
            val res = parser.parse(path)
            res.fold(
                onSuccess = body,
                onFailure = { e ->
                    McpToolResult(
                        "Failed to parse session at '$path': ${e.message ?: e::class.simpleName.orEmpty()}",
                        isError = true,
                    )
                },
            )
        } catch (e: Exception) {
            McpToolResult("Unexpected error: ${e.message ?: e::class.simpleName.orEmpty()}", isError = true)
        }
    }

    private fun summary(session: RecordedSession): McpToolResult {
        val breakdown = Narrative.breakdownFor(session.actions)
        val first = session.actions.firstOrNull()?.timestamp ?: 0L
        val last = session.actions.lastOrNull()?.timestamp ?: first
        val durationMs = (last - first).coerceAtLeast(0L)
        val body = buildString {
            append("Action count: ${session.actions.size}\n")
            append("Duration: ${durationMs} ms\n")
            append("First timestamp: $first\n")
            append("Last timestamp: $last\n")
            if (breakdown.isNotEmpty()) {
                append("Breakdown: ")
                append(breakdown.entries.joinToString { (k, v) -> "$k=$v" })
                append('\n')
            }
        }
        return McpToolResult(body)
    }

    private fun overview(session: RecordedSession): McpToolResult {
        val first = session.actions.firstOrNull()?.timestamp ?: 0L
        val last = session.actions.lastOrNull()?.timestamp ?: first
        val durationMs = (last - first).coerceAtLeast(0L)
        val breakdown = Narrative.breakdownFor(session.actions)
        val body = buildString {
            if (session.name.isNotBlank()) {
                append("Name: ${session.name}\n")
            }
            if (session.description.isNotBlank()) {
                append("Description: ${session.description}\n")
            }
            append("Source: ${session.sourcePath}\n")
            append("Action count: ${session.actions.size}\n")
            append("Duration: ${durationMs} ms\n")
            if (first > 0) append("First timestamp: $first\n")
            if (last > 0) append("Last timestamp: $last\n")
            if (breakdown.isNotEmpty()) {
                append("Breakdown: ")
                append(breakdown.entries.joinToString { (k, v) -> "$k=$v" })
                append('\n')
            }
        }
        return McpToolResult(body.trimEnd())
    }

    private fun stepNarrative(args: McpToolArgs, session: RecordedSession): McpToolResult {
        val idx = args.int("stepIndex") ?: 0
        if (session.actions.isEmpty()) {
            return McpToolResult("Session has no actions.", isError = true)
        }
        if (idx < 0 || idx >= session.actions.size) {
            return McpToolResult(
                "stepIndex out of range (0..${session.actions.size - 1}); got $idx",
                isError = true,
            )
        }
        val step = session.actions[idx]
        val previous = session.actions.getOrNull(idx - 1)
        val first = session.actions.first().timestamp
        val relStart = if (first == 0L) 0L else step.timestamp - first
        val prevRelStart = if (idx == 0 || first == 0L) 0L else (previous?.timestamp ?: first) - first
        val paragraph = Narrative.forStep(step, previous, relStart, prevRelStart)
        return McpToolResult("Step ${idx + 1} of ${session.actions.size}:\n$paragraph")
    }

    private companion object {
        const val PATH_SCHEMA =
            """{"type":"object","properties":{"path":{"type":"string","description":"Absolute path to an rparecorder session JSON file."}},"required":["path"]}"""
        const val PATH_AND_INDEX_SCHEMA =
            """{"type":"object","properties":{"path":{"type":"string","description":"Absolute path to an rparecorder session JSON file."},"stepIndex":{"type":"integer","description":"Zero-based index of the step to narrate."}},"required":["path","stepIndex"]}"""
    }
}
