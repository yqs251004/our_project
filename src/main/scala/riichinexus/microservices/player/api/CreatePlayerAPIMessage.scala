package riichinexus.microservices.player.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.apiTypes.{PlayerProfileView, PlayerResponse}
import upickle.default.*

final case class CreatePlayerAPIMessage(
    userId: String,
    nickname: String,
    rankPlatform: String,
    tier: String,
    stars: Option[Int] = None,
    initialElo: Int = 1500
) extends APIMessage[PlayerResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerResponse] =
    IO {
      PlayerProfileView.fromDomain(
        context.support.playerModule.registration.registerPlayer(
          userId = userId,
          nickname = nickname,
          rank = RankSnapshot(RankPlatform.valueOf(rankPlatform), tier, stars),
          initialElo = initialElo
        )
      )
    }
