package riichinexus.microservices.club.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

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
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.auth.domain.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubRankNode
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.ClubRankNodeRequest
import upickle.default.*

final case class UpdateClubRankTreeAPIMessage(
    clubId: String,
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipal(PlayerId(operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      command = UpdateClubRankTreeCommand(
        clubId = ClubId(clubId),
        actor = actor,
        ranks = ranks.map(rankNode),
        note = note,
        occurredAt = occurredAt
      )
      savedClub <- IO.blocking {
        {
          updateRankTree(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(updateRankTreeAudit(savedClub, command)).plan(context)
    yield ClubView.fromDomain(savedClub)

  private def updateRankTree(
      connection: java.sql.Connection,
      command: UpdateClubRankTreeCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      commitRankTreeUpdate(connection, club, command)
    }

  private def commitRankTreeUpdate(
      connection: java.sql.Connection,
      club: Club,
      command: UpdateClubRankTreeCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.updateRankTree(club, command.ranks))

  private def updateRankTreeAudit(
      updatedClub: Club,
      command: UpdateClubRankTreeCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "club",
        aggregateId = updatedClub.id.value,
        eventType = "ClubRankTreeUpdated",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map("rankCount" -> updatedClub.rankTree.size.toString),
        note = command.note
      )
    )

  private final case class UpdateClubRankTreeCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      ranks: Vector[ClubRankNode],
      note: Option[String],
      occurredAt: Instant
  )

  private def rankNode(request: ClubRankNodeRequest): ClubRankNode =
    ClubRankNode(
      code = request.code,
      label = request.label,
      minimumContribution = request.minimumContribution,
      privileges = request.privileges
    )
