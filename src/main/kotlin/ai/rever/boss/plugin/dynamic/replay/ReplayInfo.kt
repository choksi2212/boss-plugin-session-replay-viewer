package ai.rever.boss.plugin.dynamic.replay

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleFilled

/**
 * Session Replay Viewer panel info.
 *
 * Lives in the left sidebar's bottom slot, ordered at priority 66 so it sits
 * below the long-running panels but above the diagnostic ones.
 */
object ReplayInfo : PanelInfo {
    override val id = PanelId("session_replay_viewer", 66)
    override val displayName = "Session Replay"
    override val icon = Icons.Default.PlayCircleFilled
    override val defaultSlotPosition = left.bottom
}
