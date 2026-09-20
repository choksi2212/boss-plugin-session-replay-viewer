package ai.rever.boss.plugin.dynamic.replay

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.FileSystemDataProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

/**
 * Session Replay Viewer panel component (Dynamic Plugin).
 *
 * Hosts the file-picker, session list, timeline, step view and transport. The
 * actual UI is `ReplayContent`; this class only owns wiring and delegates
 * Compose rendering.
 */
class ReplayComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val fileSystemDataProvider: FileSystemDataProvider?,
    private val clipboardProvider: ClipboardProvider?,
) : PanelComponentWithUI, ComponentContext by ctx {

    private val parser = SessionParser(fileSystemDataProvider)
    private val clipboard = ClipboardHelper(clipboardProvider)
    private val viewModel = ReplayViewModel(parser, clipboard)

    @Composable
    override fun Content() {
        ReplayContent(viewModel = viewModel)
    }
}
