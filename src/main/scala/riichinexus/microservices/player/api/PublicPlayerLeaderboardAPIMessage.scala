package riichinexus.microservices.player.api

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.domain.model.Permission
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.player.objects.apiTypes.PlayerLeaderboardEntry
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
    AuthorizationPolicyFunctions.requirePermission(
      context.support.authorizationService,
      AccessPrincipalFunctions.guest(),
      Permission.ViewPublicLeaderboard
    )
    ResolvedPlayerLeaderboardQuery(
      clubId = clubId.filter(_.nonEmpty).map(ClubId(_).value),
      status = status.filter(_.nonEmpty).map(
        riichinexus.system.functions.EnumParsingFunctions.parse("status", _)(PlayerStatus.valueOf)
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

  private final case class ResolvedPlayerLeaderboardQuery(
      clubId: Option[String],
      status: Option[PlayerStatus],
      appliedFilters: Map[String, String]
  )
