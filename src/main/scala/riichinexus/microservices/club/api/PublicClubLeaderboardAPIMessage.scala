package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.Permission
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.club.domain.model.Club
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.club.objects.apiTypes.ClubLeaderboardEntry
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class PublicClubLeaderboardAPIMessage(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubLeaderboardEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubLeaderboardEntry]] =
    for
      query <- IO.blocking(resolveQuery(context))
      clubs <- IO.blocking(publicClubs(context))
      entries <- IO.blocking(publicClubLeaderboardEntries(clubs))
      filteredEntries <- IO.blocking(filterPublicClubLeaderboardEntries(context, entries, query))
    yield PagedResponse.fromItems(filteredEntries, limit, offset, query.appliedFilters)(identity)

  private def resolveQuery(context: ApiPlanContext): ResolvedClubLeaderboardQuery =
    context.support.authorizationService
      .requirePermission(AccessPrincipal.guest(), Permission.ViewPublicLeaderboard)
    ResolvedClubLeaderboardQuery(
      name = name.filter(_.nonEmpty),
      appliedFilters = Vector(name.filter(_.nonEmpty).map("name" -> _)).flatten.toMap
    )

  private def publicClubs(context: ApiPlanContext): Vector[Club] =
    ClubTable
      .findActive(context.connection)
      .sortBy(club => (-club.powerRating, -club.totalPoints, club.name))

  private def publicClubLeaderboardEntries(clubs: Vector[Club]): Vector[ClubLeaderboardEntry] =
    clubs.map { club =>
      ClubLeaderboardEntry(
        clubId = club.id,
        name = club.name,
        powerRating = round2(club.powerRating),
        totalPoints = club.totalPoints,
        memberCount = club.members.size
      )
    }

  private def filterPublicClubLeaderboardEntries(
      context: ApiPlanContext,
      entries: Vector[ClubLeaderboardEntry],
      query: ResolvedClubLeaderboardQuery
  ): Vector[ClubLeaderboardEntry] =
    entries
      .filter(entry => query.name.forall(context.support.containsIgnoreCase(entry.name, _)))

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  private final case class ResolvedClubLeaderboardQuery(
      name: Option[String],
      appliedFilters: Map[String, String]
  )
