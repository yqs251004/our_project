package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import java.util.UUID

/** PlayerIdGenerator 负责生成玩家标识符生成器 相关的领域标识符。 */

private[player] object PlayerIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def playerId(): PlayerId = PlayerId(nextId("player"))
