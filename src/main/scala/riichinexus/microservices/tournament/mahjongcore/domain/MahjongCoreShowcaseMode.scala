package riichinexus.microservices.tournament.mahjongcore.domain

import java.util.concurrent.atomic.AtomicBoolean

object MahjongCoreShowcaseMode:
  private val enabledRef = AtomicBoolean(false)

  def enabled: Boolean =
    enabledRef.get()

  def setEnabled(enabled: Boolean): Boolean =
    enabledRef.set(enabled)
    enabled
