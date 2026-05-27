package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.Permission
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.publicquery.objects.apiTypes.ClubLeaderboardEntry
import riichinexus.microservices.publicquery.domain.PublicDirectoryQueries
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class PublicClubLeaderboardAPIMessage(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubLeaderboardEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubLeaderboardEntry]] =
    for
      query <- IO(resolveQuery(context))
      leaderboard <- IO(listLeaderboard(context, query))
    yield PagedResponse.fromItems(leaderboard, limit, offset, query.appliedFilters)(identity)

  private def resolveQuery(context: ApiPlanContext): ResolvedClubLeaderboardQuery =
    context.support.authorizationService
      .requirePermission(AccessPrincipal.guest(), Permission.ViewPublicLeaderboard)
    ResolvedClubLeaderboardQuery(
      name = name.filter(_.nonEmpty),
      appliedFilters = Vector(name.filter(_.nonEmpty).map("name" -> _)).flatten.toMap
    )

  private def listLeaderboard(
      context: ApiPlanContext,
      query: ResolvedClubLeaderboardQuery
  ): Vector[ClubLeaderboardEntry] =
    PublicDirectoryQueries.publicClubLeaderboard(context.connection, Int.MaxValue)
      .filter(entry => query.name.forall(context.support.containsIgnoreCase(entry.name, _)))

  private final case class ResolvedClubLeaderboardQuery(
      name: Option[String],
      appliedFilters: Map[String, String]
  )
