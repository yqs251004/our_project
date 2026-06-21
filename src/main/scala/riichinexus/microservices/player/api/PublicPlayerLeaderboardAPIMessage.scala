package riichinexus.microservices.player.api

import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.Permission
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.player.objects.apiTypes.PlayerLeaderboardEntry
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
/** 获取前端公开玩家排行榜。 */
final case class PublicPlayerLeaderboardAPIMessage(
    clubId: Option[String] = None,
    status: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerLeaderboardEntry]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerLeaderboardEntry]] =
    for
      _ <- requirePublicLeaderboardPermission(context)
      query <- IO.blocking(resolveQuery(context))
      players <- loadPublicPlayers(context)
      entries = publicPlayerLeaderboardEntries(players)
      filteredEntries = filterPublicPlayerLeaderboardEntries(entries, query)
    yield PagedResponse.fromItems(filteredEntries, limit, offset, query.appliedFilters)(identity)

  private def requirePublicLeaderboardPermission(context: ApiPlanContext): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      permission = Permission.ViewPublicLeaderboard
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure("guest is not allowed to view public leaderboard"))
    }

  private def resolveQuery(context: ApiPlanContext): ResolvedPlayerLeaderboardQuery =
    ResolvedPlayerLeaderboardQuery(
      clubId = clubId.filter(_.nonEmpty).map(ClubId(_).value),
      status = status.filter(_.nonEmpty).map(
        riichinexus.system.EnumParsing.parse("status", _)(PlayerStatus.valueOf)
      ),
      appliedFilters = Vector(
        clubId.filter(_.nonEmpty).map("clubId" -> _),
        status.filter(_.nonEmpty).map("status" -> _)
      ).flatten.toMap
    )

  private def loadPublicPlayers(context: ApiPlanContext): IO[Vector[Player]] =
    IO.blocking(PlayerTable.findAll(context.connection))

  private def publicPlayerLeaderboardEntries(players: Vector[Player]): Vector[PlayerLeaderboardEntry] =
    players
      .sortBy(player => (-player.elo, player.nickname))
      .map { player =>
        PlayerLeaderboardEntry(
          playerId = player.id.value,
          nickname = player.nickname,
          elo = player.elo,
          currentRank = player.currentRank,
          normalizedRankScore = None,
          clubIds = PlayerClubBindingFunctions.boundClubIds(player).map(_.value),
          status = player.status.toString
        )
      }

  private def filterPublicPlayerLeaderboardEntries(
      entries: Vector[PlayerLeaderboardEntry],
      query: ResolvedPlayerLeaderboardQuery
  ): Vector[PlayerLeaderboardEntry] =
    entries
      .filter(entry => query.clubId.forall(entry.clubIds.contains))
      .filter(entry => query.status.forall(_.toString == entry.status))

  /** 公开玩家排行榜接口解析后的过滤条件。 */
  private final case class ResolvedPlayerLeaderboardQuery(
      clubId: Option[String],
      status: Option[PlayerStatus],
      appliedFilters: Map[String, String]
  )
