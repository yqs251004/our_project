package riichinexus.microservices.player.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.domain.model.Permission
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.player.domain.PlayerRankNormalizationService
import riichinexus.microservices.player.objects.{Player, PlayerStatus}
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.player.objects.apiTypes.PlayerLeaderboardEntry
import riichinexus.microservices.tournament.objects.apiTypes.RankSnapshotView
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class PublicPlayerLeaderboardAPIMessage(
    clubId: Option[String] = None,
    status: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerLeaderboardEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerLeaderboardEntry]] =
    for
      query <- IO.blocking(resolveQuery(context))
      players <- IO.blocking(publicPlayers(context))
      entries <- IO.blocking(publicPlayerLeaderboardEntries(players))
      filteredEntries <- IO.blocking(filterPublicPlayerLeaderboardEntries(entries, query))
    yield PagedResponse.fromItems(filteredEntries, limit, offset, query.appliedFilters)(identity)

  private def resolveQuery(context: ApiPlanContext): ResolvedPlayerLeaderboardQuery =
    context.support.authorizationService
      .requirePermission(AccessPrincipal.guest(), Permission.ViewPublicLeaderboard)
    ResolvedPlayerLeaderboardQuery(
      clubId = clubId.filter(_.nonEmpty).map(ClubId(_).value),
      status = status.filter(_.nonEmpty).map(
        context.support.parseEnum("status", _)(PlayerStatus.valueOf)
      ),
      appliedFilters = Vector(
        clubId.filter(_.nonEmpty).map("clubId" -> _),
        status.filter(_.nonEmpty).map("status" -> _)
      ).flatten.toMap
    )

  private def publicPlayers(context: ApiPlanContext): Vector[Player] =
    PlayerTable.findAll(context.connection)

  private def publicPlayerLeaderboardEntries(players: Vector[Player]): Vector[PlayerLeaderboardEntry] =
    players
      .map(player => player -> PlayerRankNormalizationService.normalize(player.currentRank))
      .sortBy { case (player, normalizedRank) =>
        val normalizedRankScore = normalizedRank.map(_.score).getOrElse(Int.MinValue)
        (-player.elo, -normalizedRankScore, player.nickname)
      }
      .map { case (player, normalizedRank) =>
        PlayerLeaderboardEntry(
          playerId = player.id,
          nickname = player.nickname,
          elo = player.elo,
          currentRank = RankSnapshotView.fromDomain(player.currentRank),
          normalizedRankScore = normalizedRank.map(_.score),
          clubIds = player.boundClubIds,
          status = player.status
        )
      }

  private def filterPublicPlayerLeaderboardEntries(
      entries: Vector[PlayerLeaderboardEntry],
      query: ResolvedPlayerLeaderboardQuery
  ): Vector[PlayerLeaderboardEntry] =
    entries
      .filter(entry => query.clubId.forall(entry.clubIds.contains))
      .filter(entry => query.status.forall(_.toString == entry.status))

  private final case class ResolvedPlayerLeaderboardQuery(
      clubId: Option[String],
      status: Option[PlayerStatus],
      appliedFilters: Map[String, String]
  )
