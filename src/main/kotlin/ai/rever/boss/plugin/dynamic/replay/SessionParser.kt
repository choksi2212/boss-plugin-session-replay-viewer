package ai.rever.boss.plugin.dynamic.replay

import ai.rever.boss.plugin.api.FileSystemDataProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Reads an rparecorder session file from disk and turns it into a [RecordedSession].
 *
 * The recorder writes two shapes:
 *   1. A bare JSON array of actions.
 *   2. An object `{ name, description, actions: [...] }` (the "configuration" form).
 *
 * The parser accepts both. It also bounds the size: a single file over
 * [MAX_SESSION_BYTES] is refused without reading further, since a runaway
 * recording can grow to tens of MiB and parsing it on the UI thread is a
 * freeze that no plugin should be allowed to cause.
 */
class SessionParser(
    private val fileSystemDataProvider: FileSystemDataProvider? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * Read `path` and parse it. Returns failure if the file is missing, oversized,
     * unreadable, or unparseable.
     */
    suspend fun parse(path: String): Result<RecordedSession> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("Path is blank"))
        }
        val size = fileSizeOrNull(path)
        if (size != null && size > MAX_SESSION_BYTES) {
            return Result.failure(
                IllegalStateException(
                    "Session file is too large ($size bytes > ${MAX_SESSION_BYTES} byte cap). " +
                        "Refusing to parse to protect the host."
                )
            )
        }
        val raw = readText(path) ?: return Result.failure(
            IllegalStateException("Could not read session file at $path")
        )
        if (raw.isBlank()) {
            return Result.failure(IllegalStateException("Session file is empty"))
        }
        return try {
            val element = json.parseToJsonElement(raw)
            val (name, description, rawActions) = when {
                element is JsonArray -> Triple("", "", element)
                element is JsonObject -> {
                    val name = element.optString("name").orEmpty()
                    val description = element.optString("description").orEmpty()
                    val actions = (element["actions"] as? JsonArray) ?: JsonArray(emptyList())
                    Triple(name, description, actions)
                }
                else -> return Result.failure(
                    SerializationException("Unexpected JSON root, expected array or object")
                )
            }
            val steps = rawActions.mapNotNull { item ->
                if (item !is JsonObject) return@mapNotNull null
                runCatching {
                    // Rebuild as a plain object so the @Serializable default values kick in
                    // for missing fields, and coerce null primitives where possible.
                    val obj = buildJsonObject {
                        item.forEach { (k, v) -> put(k, v) }
                    }
                    json.decodeFromJsonElement(RecordedStep.serializer(), obj)
                }.getOrNull()
            }
            Result.success(
                RecordedSession(
                    name = name,
                    description = description,
                    sourcePath = path,
                    actions = steps,
                )
            )
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readText(path: String): String? {
        val provider = fileSystemDataProvider ?: return fallbackRead(path)
        return provider.readFile(path).getOrNull() ?: fallbackRead(path)
    }

    /**
     * Direct read fallback. The FileSystemDataProvider should always succeed for
     * a path the user picked through a plugin dialog, but in older hosts the
     * provider may be null. We then read through java.io.File - which works for
     * plugin classloaders that share the host's filesystem view, and fails with
     * the same message otherwise.
     */
    private fun fallbackRead(path: String): String? = try {
        java.io.File(path).takeIf { it.exists() && it.isFile }?.readText()
    } catch (_: SecurityException) {
        null
    } catch (_: java.io.IOException) {
        null
    }

    private fun fileSizeOrNull(path: String): Long? = try {
        val f = java.io.File(path)
        if (f.exists() && f.isFile) f.length() else null
    } catch (_: SecurityException) {
        null
    } catch (_: java.io.IOException) {
        null
    }

    /**
     * Find all `.json` files in a directory that look like a recorded session.
     *
     * Matches by extension only; the parser itself decides whether the file is
     * actually a session (an arbitrary `.json` may be something else entirely).
     */
    suspend fun listSessionsInDirectory(directory: String): List<String> {
        if (directory.isBlank()) return emptyList()
        val provider = fileSystemDataProvider
        val nodes = provider?.scanDirectory(directory, showHidden = false)
            ?: fallbackListFiles(directory)
        return nodes
            ?.children
            ?.filter { !it.isDirectory }
            ?.map { it.path }
            ?.filter { it.endsWith(".json", ignoreCase = true) && it != "$directory/configurations" && !it.endsWith("settings.json") }
            ?: emptyList()
    }

    private fun fallbackListFiles(directory: String): ai.rever.boss.plugin.api.FileNodeData? {
        val dir = java.io.File(directory)
        if (!dir.exists() || !dir.isDirectory) return null
        val children = dir.listFiles()?.map { f ->
            ai.rever.boss.plugin.api.FileNodeData(
                name = f.name,
                path = f.absolutePath,
                isDirectory = f.isDirectory,
                children = emptyList(),
                hasChildren = if (f.isDirectory) (f.list()?.isNotEmpty() == true) else false,
            )
        } ?: emptyList()
        return ai.rever.boss.plugin.api.FileNodeData(
            name = dir.name,
            path = dir.absolutePath,
            isDirectory = true,
            children = children,
            hasChildren = children.isNotEmpty(),
        )
    }

    companion object {
        /** Refuse anything larger than this; the host UI cannot meaningfully render it. */
        const val MAX_SESSION_BYTES: Long = 32L * 1024L * 1024L
    }
}
