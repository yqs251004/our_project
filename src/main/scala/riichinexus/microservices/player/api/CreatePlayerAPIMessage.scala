package riichinexus.microservices.player.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.PlayerRegistration
import riichinexus.microservices.player.objects.apiTypes.PlayerProfileView
import riichinexus.microservices.player.objects.apiTypes.CreatePlayerRequest
import upickle.default.*

final case class CreatePlayerAPIMessage(
    request: CreatePlayerRequest
) extends APIMessage[PlayerProfileView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerProfileView] =
    for
      registeredAt <- IO.realTimeInstant
      player <- IO.blocking {
        PlayerRegistration.register(
          connection = context.connection,
          userId = request.userId,
          nickname = request.nickname,
          rank = request.toRankSnapshot,
          registeredAt = registeredAt,
          initialElo = request.initialElo
        )
      }
    yield PlayerProfileView.fromDomain(player)
