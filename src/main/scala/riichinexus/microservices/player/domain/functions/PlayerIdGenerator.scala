package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.player.domain.model.PlayerIdPrefix
import riichinexus.microservices.player.objects.PlayerId

import java.util.UUID

/** PlayerIdGenerator 负责生成玩家标识符生成器 相关的领域标识符。 */

private[player] object PlayerIdGenerator:
  private def nextId(prefix: PlayerIdPrefix): String =
    s"${PlayerIdPrefix.toString(prefix)}-${UUID.randomUUID().toString.take(8)}"

  def playerId(): PlayerId = PlayerId(nextId(PlayerIdPrefix.Player))
