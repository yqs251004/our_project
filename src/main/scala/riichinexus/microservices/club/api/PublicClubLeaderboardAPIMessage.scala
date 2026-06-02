package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission

import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.ClubLeaderboardEntry
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class PublicClubLeaderboardAPIMessage(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubLeaderboardEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubLeaderboardEntry]] =
    for
      _ <- requirePublicLeaderboardPermission(context)
      query <- IO.blocking(resolveQuery(context))
      clubs <- IO.blocking(publicClubs(context))
      entries <- IO.blocking(publicClubLeaderboardEntries(clubs))
      filteredEntries <- IO.blocking(filterPublicClubLeaderboardEntries(context, entries, query))
    yield PagedResponse.fromItems(filteredEntries, limit, offset, query.appliedFilters)(identity)

  private def requirePublicLeaderboardPermission(context: ApiPlanContext): IO[Unit] =
    val guest = AccessPrincipalFunctions.guest()
    AuthCheckPermissionAPIMessage(
      principal = Some(guest),
      permission = Permission.ViewPublicLeaderboard
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${guest.displayName} is not allowed to view public leaderboard"))
    }

  private def resolveQuery(context: ApiPlanContext): ResolvedClubLeaderboardQuery =
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
      .filter(entry => query.name.forall(riichinexus.system.TextSearch.containsIgnoreCase(entry.name, _)))

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  private final case class ResolvedClubLeaderboardQuery(
      name: Option[String],
      appliedFilters: Map[String, String]
  )
