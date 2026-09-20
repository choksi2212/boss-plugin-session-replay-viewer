package ai.rever.boss.plugin.dynamic.replay

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parsed view of an rparecorder session file.
 *
 * A session file is one of two shapes the recorder emits:
 * - the "raw" form: a JSON array of [RecordedStep] objects
 * - the "configuration" form: an object with `name`, `description`, `actions`,
 *   where each action is structurally the same action as a raw step
 *
 * Fields here default to empty / null so partial files still parse. The parser
 * normalizes both shapes into the same in-memory list.
 */
@Serializable
data class RecordedSession(
    val name: String = "",
    val description: String = "",
    val sourcePath: String = "",
    val actions: List<RecordedStep> = emptyList(),
)

/**
 * One step in a recorded session.
 *
 * Mirrors the rparecorder RecordedAction / RpaActionConfig shapes: `type` is the
 * action verb (click, input, navigate, wait, ...), `selector` describes how the
 * element was located, `value` is the typed or option text, `timestamp` is the
 * captured moment, `elementText` is the visible text of the targeted element,
 * `url` is the page at the moment of capture, and `elementType` is the DOM tag.
 */
@Serializable
data class RecordedStep(
    @SerialName("type")
    val type: String = "",
    @SerialName("selector")
    val selector: RecordedSelector = RecordedSelector(),
    @SerialName("value")
    val value: String? = null,
    @SerialName("timestamp")
    val timestamp: Long = 0L,
    @SerialName("elementText")
    val elementText: String? = null,
    @SerialName("url")
    val url: String? = null,
    @SerialName("elementType")
    val elementType: String? = null,
    @SerialName("name")
    val name: String? = null,
)

/**
 * How a step located its target on the page.
 *
 * `kind` is one of `css`, `xpath`, `text`, `id`, `none`. `value` is the actual
 * selector expression (or visible text for the `text` kind). `isUnique` records
 * whether the recorder was sure the selector matched exactly one element.
 */
@Serializable
data class RecordedSelector(
    @SerialName("type")
    val kind: String = "none",
    @SerialName("value")
    val value: String? = null,
    @SerialName("isUnique")
    val isUnique: Boolean? = null,
)

/**
 * Convenience accessors used by the narrative and the timeline.
 */
val RecordedStep.label: String
    get() = when (type.lowercase()) {
        "click" -> "Click"
        "input", "type" -> "Type"
        "select" -> "Select"
        "navigate", "navigation" -> "Navigate"
        "wait" -> "Wait"
        "scroll" -> "Scroll"
        "screenshot" -> "Screenshot"
        "assert", "assertion" -> "Assert"
        else -> type.replaceFirstChar { it.uppercase() }.ifEmpty { "Step" }
    }

fun RecordedSelector.display(): String = when (kind.lowercase()) {
    "css" -> "CSS"
    "xpath" -> "XPath"
    "id" -> "ID"
    "text" -> "Text"
    "none" -> ""
    else -> kind.uppercase()
}

/**
 * Pull the field "value" out of a JsonObject, treating both string and numeric
 * primitives as text. Returns null for missing / null entries.
 */
internal fun JsonObject.optString(field: String): String? {
    val v = get(field) ?: return null
    if (v !is JsonPrimitive) return null
    if (v.isString.not() && v.contentOrNull.isNullOrEmpty()) return null
    return v.contentOrNull
}

/**
 * Extract a long from a JsonObject field, accepting numeric and string forms.
 */
internal fun JsonObject.optLong(field: String): Long {
    val v = get(field) ?: return 0L
    if (v is JsonPrimitive) {
        v.contentOrNull?.toLongOrNull()?.let { return it }
    }
    return 0L
}

/**
 * Wraps `jsonPrimitive` access for null-safety in selector description rendering.
 */
internal fun JsonPrimitive.textOrNull(): String? =
    if (isString) contentOrNull else contentOrNull
