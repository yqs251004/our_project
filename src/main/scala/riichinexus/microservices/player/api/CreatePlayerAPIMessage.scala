package riichinexus.microservices.player.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.apiTypes.{CreatePlayerRequest, PlayerProfileView, PlayerResponse}
import riichinexus.microservices.player.objects.apiTypes.PlayerRequests.given
import upickle.default.*

final case class CreatePlayerAPIMessage(
    request: CreatePlayerRequest
) extends APIMessage[PlayerResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerResponse] =
    for
      player <- IO(
        context.support.playerModule.registration.registerPlayer(
          userId = request.userId,
          nickname = request.nickname,
          rank = request.toRankSnapshot,
          initialElo = request.initialElo
        )
      )
    yield PlayerProfileView.fromDomain(player)
