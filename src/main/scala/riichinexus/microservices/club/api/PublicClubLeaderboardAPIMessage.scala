package riichinexus.microservices.club.api

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{ClubId, Permission}
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.ClubLeaderboardEntry
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
    AuthorizationPolicyFunctions.requirePermission(
      context.support.authorizationService,
      AccessPrincipalFunctions.guest(),
      Permission.ViewPublicLeaderboard
    )
    ResolvedClubLeaderboardQuery(
      name = name.filter(_.nonEmpty),
      appliedFilters = Vector(name.filter(_.nonEmpty).map("name" -> _)).flatten.toMap
    )

  private def publicClubs(context: ApiPlanContext): Vector[Club] =
    ClubTable
      .findFiltered(context.connection, activeOnly = true)
      .sortBy(club => (-club.powerRating, -club.totalPoints, club.name))

  private def publicClubLeaderboardEntries(clubs: Vector[Club]): Vector[ClubLeaderboardEntry] =
    clubs.map { club =>
      clubLeaderboardEntry(
        clubId = club.id,
        name = club.name,
        powerRating = round2(club.powerRating),
        totalPoints = club.totalPoints,
        memberCount = club.members.size
      )
    }

  private def clubLeaderboardEntry(
      clubId: ClubId,
      name: String,
      powerRating: Double,
      totalPoints: Int,
      memberCount: Int
  ): ClubLeaderboardEntry =
    ClubLeaderboardEntry(
      clubId = clubId.value,
      name = name,
      powerRating = powerRating,
      totalPoints = totalPoints,
      memberCount = memberCount
    )

  private def filterPublicClubLeaderboardEntries(
      context: ApiPlanContext,
      entries: Vector[ClubLeaderboardEntry],
      query: ResolvedClubLeaderboardQuery
  ): Vector[ClubLeaderboardEntry] =
    entries
      .filter(entry => query.name.forall(riichinexus.system.functions.TextSearchFunctions.containsIgnoreCase(entry.name, _)))

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  private final case class ResolvedClubLeaderboardQuery(
      name: Option[String],
      appliedFilters: Map[String, String]
  )
