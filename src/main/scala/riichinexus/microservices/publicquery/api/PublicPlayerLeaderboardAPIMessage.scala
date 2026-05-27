package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.domain.model.Permission
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.publicquery.objects.apiTypes.PlayerLeaderboardEntry
import riichinexus.microservices.publicquery.domain.PublicDirectoryQueries
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
      query <- IO(resolveQuery(context))
      leaderboard <- IO(listLeaderboard(context, query))
    yield PagedResponse.fromItems(leaderboard, limit, offset, query.appliedFilters)(identity)

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

  private def listLeaderboard(
      context: ApiPlanContext,
      query: ResolvedPlayerLeaderboardQuery
  ): Vector[PlayerLeaderboardEntry] =
    PublicDirectoryQueries.publicPlayerLeaderboard(context.connection, Int.MaxValue)
      .filter(entry => query.clubId.forall(entry.clubIds.contains))
      .filter(entry => query.status.forall(_.toString == entry.status))

  private final case class ResolvedPlayerLeaderboardQuery(
      clubId: Option[String],
      status: Option[PlayerStatus],
      appliedFilters: Map[String, String]
  )
