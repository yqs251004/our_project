package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import java.util.UUID

object PlayerIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def playerId(): PlayerId = PlayerId(nextId("player"))
