package riichinexus.system.app

import java.util.concurrent.atomic.AtomicBoolean

/** Process-wide global switch for demo/showcase behavior; this is runtime app state, not mahjong domain data. */
object MahjongCoreShowcaseModeState:
  private val enabledRef = AtomicBoolean(false)

  def enabled: Boolean =
    enabledRef.get()

  def setEnabled(enabled: Boolean): Boolean =
    enabledRef.set(enabled)
    enabled
