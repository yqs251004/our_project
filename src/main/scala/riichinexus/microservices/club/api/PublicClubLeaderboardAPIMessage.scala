package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission

import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.ClubLeaderboardEntry
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
/** 获取前端公开俱乐部排行榜。 */
final case class PublicClubLeaderboardAPIMessage(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubLeaderboardEntry]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubLeaderboardEntry]] =
    for
      _ <- requirePublicLeaderboardPermission(context)
      query = resolveQuery
      clubs <- IO.blocking(publicClubs(context))
      entries = publicClubLeaderboardEntries(clubs)
      filteredEntries = filterPublicClubLeaderboardEntries(entries, query)
    yield PagedResponse.fromItems(filteredEntries, limit, offset, query.appliedFilters)(identity)

  private def requirePublicLeaderboardPermission(context: ApiPlanContext): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      permission = Permission.ViewPublicLeaderboard
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure("guest is not allowed to view public leaderboard"))
    }

  private def resolveQuery: ResolvedClubLeaderboardQuery =
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
      entries: Vector[ClubLeaderboardEntry],
      query: ResolvedClubLeaderboardQuery
  ): Vector[ClubLeaderboardEntry] =
    entries
      .filter(entry => query.name.forall(riichinexus.system.TextSearch.containsIgnoreCase(entry.name, _)))

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  /** 公开俱乐部排行榜接口解析后的名称过滤条件。 */
  private final case class ResolvedClubLeaderboardQuery(
      name: Option[String],
      appliedFilters: Map[String, String]
  )
