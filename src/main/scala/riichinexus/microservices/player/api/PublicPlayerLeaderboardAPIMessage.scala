package riichinexus.microservices.player.api

import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.player.objects.PlayerLeaderboardEntry
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
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
      requestedClubId <- IO.blocking(clubId.filter(_.nonEmpty).map(ClubId(_).value))
      statusFilter <- IO.blocking(
        status.filter(_.nonEmpty).map(
          riichinexus.system.EnumParsing.parse(QueryFilterField.toString(QueryFilterField.Status), _)(PlayerStatus.valueOf)
        )
      )
      appliedFilters = leaderboardFilters
      players <- loadPublicPlayers(context)
      entries = publicPlayerLeaderboardEntries(players)
      filteredEntries = filterPublicPlayerLeaderboardEntries(entries, requestedClubId, statusFilter)
    yield PagedResponse.fromItems(filteredEntries, limit, offset, appliedFilters)(identity)

  private def requirePublicLeaderboardPermission(context: ApiPlanContext): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      permission = Permission.ViewPublicLeaderboard
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure("guest is not allowed to view public leaderboard"))
    }

  private def leaderboardFilters: Map[String, String] =
    Vector(
      clubId.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.ClubId) -> _),
      status.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.Status) -> _)
    ).flatten.toMap

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
      clubId: Option[String],
      status: Option[PlayerStatus]
  ): Vector[PlayerLeaderboardEntry] =
    entries
      .filter(entry => clubId.forall(entry.clubIds.contains))
      .filter(entry => status.forall(_.toString == entry.status))
