package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.api.ApiPlanContext

private[player] object PlayerPrivateMutationSupport:
  def updatePlayer(
      context: ApiPlanContext,
      playerId: PlayerId
  )(update: Player => Player): IO[Option[Player]] =
    for
      player <- IO.blocking(PlayerTable.findById(context.connection, playerId))
      saved <- player match
        case Some(existing) =>
          IO.blocking(PlayerTable.save(context.connection, update(existing))).map(Some(_))
        case None =>
          IO.pure(None)
    yield saved
