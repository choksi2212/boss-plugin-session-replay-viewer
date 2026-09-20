package ai.rever.boss.plugin.dynamic.replay

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.FileSystemDataProvider
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory

/**
 * Session Replay Viewer dynamic plugin - loaded from an external JAR.
 *
 * Adds a side panel that walks through an rparecorder session one step at a
 * time, with a timeline and a per-step narrative, and contributes three MCP
 * tools that do the same parsing headlessly (for in-terminal agents).
 */
class ReplayDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.replay"
    override val displayName: String = "Session Replay Viewer"
    override val version: String = "0.1.0"
    override val description: String =
        "Visual, step-by-step playback of rparecorder session files - timeline + per-step narrative."
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/choksi2212/boss-plugin-session-replay-viewer"

    private val logger = BossLogger.forComponent("ReplayDynamicPlugin")

    private var fileSystemDataProvider: FileSystemDataProvider? = null
    private var clipboardProvider: ClipboardProvider? = null

    override fun register(context: PluginContext) {
        fileSystemDataProvider = context.fileSystemDataProvider
        clipboardProvider = context.clipboardProvider

        if (fileSystemDataProvider == null) {
            logger.warn(
                LogCategory.SYSTEM,
                "FileSystemDataProvider is null on this host - session parsing falls back to direct File I/O",
            )
        }
        if (clipboardProvider == null) {
            logger.warn(
                LogCategory.UI,
                "ClipboardProvider is null on this host - copy actions fall back to AWT (may fail under classloader isolation)",
            )
        }

        context.panelRegistry.registerPanel(ReplayInfo) { ctx, panelInfo ->
            ReplayComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                fileSystemDataProvider = fileSystemDataProvider,
                clipboardProvider = clipboardProvider,
            )
        }

        // Contribute replay_* MCP tools. The provider parses on demand, so it does
        // not hold session state and stays cheap to register once at startup.
        context.registerMcpToolProvider(
            ReplayMcpToolProvider(
                providerId = pluginId,
                fileSystemDataProvider = fileSystemDataProvider,
            )
        )
    }

    override fun dispose() {
        fileSystemDataProvider = null
        clipboardProvider = null
    }
}
