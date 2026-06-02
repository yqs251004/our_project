package riichinexus.microservices.club.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.api.`private`.ListAuditEventsPrivateAPIMessage
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
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.{
  ClubContributionAuditEntry,
  ClubContributionAuditQuery
}
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListClubContributionAuditsAPIMessage(
    clubId: String,
    query: ClubContributionAuditQuery
) extends APIMessage[PagedResponse[ClubContributionAuditEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubContributionAuditEntry]] =
    for
      operator <- IO.blocking(ResolveAccessPrincipal(query.operatorId).resolve(context.connection))
      _ <- requireContributionAuditPermission(context, operator)
      parsedClubId = ClubId(clubId)
      resolved = resolveQuery(parsedClubId, query)
      audits <- listContributionAudits(context, resolved)
    yield PagedResponse.fromItems(
      audits,
      query.limit,
      query.offset,
      resolved.appliedFilters
    )(clubContributionAuditEntry(parsedClubId, _))

  private def requireContributionAuditPermission(context: ApiPlanContext, operator: AccessPrincipal): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      principal = Some(operator),
      permission = Permission.ViewAuditTrail
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to view audit trail"))
    }

  private def resolveQuery(
      clubId: ClubId,
      query: ClubContributionAuditQuery
  ): ResolvedContributionAuditQuery =
    ResolvedContributionAuditQuery(
      clubId = clubId,
      appliedFilters = Map(
        "clubId" -> clubId.value,
        "operatorId" -> query.operatorId.value
      )
    )

  private def listContributionAudits(
      context: ApiPlanContext,
      query: ResolvedContributionAuditQuery
  ): IO[Vector[AuditEvent]] =
    ListAuditEventsPrivateAPIMessage(
      aggregateType = Some("club"),
      aggregateId = Some(query.clubId.value),
      eventType = Some("ClubMemberContributionAdjusted"),
      oldestFirst = true
    ).plan(context)

  private def clubContributionAuditEntry(clubId: ClubId, entry: AuditEvent): ClubContributionAuditEntry =
    ClubContributionAuditEntry(
      id = entry.id.value,
      clubId = clubId.value,
      playerId = entry.details.get("playerId"),
      delta = entry.details.get("delta"),
      contribution = entry.details.get("contribution"),
      occurredAt = entry.occurredAt.toString,
      actorId = entry.actorId.map(_.value),
      note = entry.note
    )

  private final case class ResolvedContributionAuditQuery(
      clubId: ClubId,
      appliedFilters: Map[String, String]
  )
