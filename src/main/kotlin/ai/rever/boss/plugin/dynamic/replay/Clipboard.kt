package ai.rever.boss.plugin.dynamic.replay

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * JVM-side clipboard helper.
 *
 * Plugins cannot access `java.awt.Toolkit` directly under the host's classloader
 * isolation: AWT looks up classes on the system classloader at first use, which
 * either throws or silently uses the wrong owner. The right path is to ask the
 * host's [ClipboardProvider]; we fall back to AWT only when the provider is
 * null and we have logged that we are doing so.
 */
class ClipboardHelper(
    private val clipboardProvider: ClipboardProvider?,
) {
    private val logger = BossLogger.forComponent("ReplayClipboard")

    /**
     * Place [text] on the system clipboard. Returns true on success.
     */
    fun copy(text: String): Boolean {
        if (text.isEmpty()) return false
        val provider = clipboardProvider
        if (provider != null) {
            return try {
                provider.setText(text)
            } catch (e: Exception) {
                logger.warn(
                    LogCategory.UI,
                    "ClipboardProvider.setText failed, falling back to AWT",
                    mapOf("error" to (e.message ?: e::class.simpleName.orEmpty())),
                )
                fallbackCopy(text)
            }
        }
        logger.warn(
            LogCategory.UI,
            "No ClipboardProvider on this host - falling back to AWT (may fail under classloader isolation)",
        )
        return fallbackCopy(text)
    }

    /**
     * Read text from the clipboard, or null if it is empty / not text.
     */
    fun read(): String? {
        val provider = clipboardProvider
        if (provider != null) {
            return try {
                provider.readText()
            } catch (e: Exception) {
                logger.warn(
                    LogCategory.UI,
                    "ClipboardProvider.readText failed, falling back to AWT",
                    mapOf("error" to (e.message ?: e::class.simpleName.orEmpty())),
                )
                fallbackRead()
            }
        }
        return fallbackRead()
    }

    private fun fallbackCopy(text: String): Boolean = try {
        val sel = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
        true
    } catch (e: Exception) {
        logger.warn(
            LogCategory.UI,
            "AWT clipboard fallback failed",
            mapOf("error" to (e.message ?: e::class.simpleName.orEmpty())),
        )
        false
    }

    private fun fallbackRead(): String? = try {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            cb.getData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    } catch (e: Exception) {
        logger.warn(
            LogCategory.UI,
            "AWT clipboard read failed",
            mapOf("error" to (e.message ?: e::class.simpleName.orEmpty())),
        )
        null
    }
}
