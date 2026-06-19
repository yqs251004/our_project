package riichinexus.microservices.player.api.`private`

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.auth.domain.authorization.RoleGrantFunctions
import riichinexus.microservices.opsanalytics.api.`private`.EnsurePlayerDashboardAPIMessage
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.RankSnapshot
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CreatePlayerPrivateAPIMessage(
    userId: String,
    nickname: String,
    rank: RankSnapshot,
    registeredAt: Instant,
    initialElo: Int
) extends APIMessage[Player] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Player] =
    for
      player <- IO.blocking {
        PlayerTable.findByUserId(context.connection, userId) match
          case Some(existing) =>
            existing.copy(
              nickname = nickname,
              currentRank = rank
            )
          case None =>
            Player(
              id = PlayerIdGenerator.playerId(),
              userId = userId,
              nickname = nickname,
              registeredAt = registeredAt,
              currentRank = rank,
              elo = initialElo,
              roleGrants = Vector(RoleGrantFunctions.registered(registeredAt))
            )
      }
      savedPlayer <- IO.blocking(PlayerTable.save(context.connection, player))
      _ <- EnsurePlayerDashboardAPIMessage(savedPlayer.id, registeredAt).plan(context)
    yield savedPlayer
