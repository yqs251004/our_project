package riichinexus.microservices.club.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
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
import riichinexus.microservices.club.domain.relationmanagement.functions.ClubRelationAuthorizationFunctions
import riichinexus.microservices.auth.domain.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import upickle.default.*

final case class UpdateClubRelationAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(operatorId)).resolve(context.connection))
      relationUpdatedAt <- IO.realTimeInstant
      occurredAt <- IO.realTimeInstant
      command = UpdateClubRelationCommand(
        clubId = ClubId(clubId),
        actor = actor,
        relation = ClubRelation(
          targetClubId = ClubId(targetClubId),
          relation = relation,
          updatedAt = relationUpdatedAt,
          note = note
        ),
        occurredAt = occurredAt
      )
      savedClub <- IO.blocking {
        {
          updateRelation(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(updateRelationAudit(command)).plan(context)
    yield ClubView.fromDomain(savedClub)

  private def updateRelation(
      connection: java.sql.Connection,
      command: UpdateClubRelationCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ensureRelationCanBeUpdated(club, command)
      val targetClub = resolveTargetClub(connection, command)
      commitRelationUpdate(connection, club, targetClub, command)
    }

  private def ensureRelationCanBeUpdated(
      club: Club,
      command: UpdateClubRelationCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ClubRelationAuthorizationFunctions.requireDirectRelationUpdate(command.actor)
    if command.relation.targetClubId == command.clubId then
      throw IllegalArgumentException("A club cannot define a relation to itself")

  private def resolveTargetClub(
      connection: java.sql.Connection,
      command: UpdateClubRelationCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable
      .findById(connection, command.relation.targetClubId)
      .map { club =>
        ClubAuthorization.ensureClubActive(club)
        club
      }
      .getOrElse(
        throw NoSuchElementException(s"Club ${command.relation.targetClubId.value} was not found")
      )

  private def commitRelationUpdate(
      connection: java.sql.Connection,
      club: Club,
      targetClub: Club,
      command: UpdateClubRelationCommand
  ): Club =
    val sourceClub =
      if command.relation.relation == ClubRelationKind.Neutral then
        ClubFunctions.removeRelation(club, command.relation.targetClubId)
      else ClubFunctions.upsertRelation(club, command.relation)

    val savedSource = riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, sourceClub)
    if command.relation.relation == ClubRelationKind.Neutral then
      riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.removeRelation(targetClub, command.clubId))
    else
      riichinexus.microservices.club.tables.clubs.ClubTable.save(
        connection,
        ClubFunctions.upsertRelation(
          targetClub,
          command.relation.copy(targetClubId = command.clubId)
        )
      )
    savedSource

  private def updateRelationAudit(command: UpdateClubRelationCommand): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = "ClubRelationUpdated",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map(
          "targetClubId" -> command.relation.targetClubId.value,
          "relation" -> ClubRelationKind.toString(command.relation.relation)
        ),
        note = command.relation.note
      )
    )

  private final case class UpdateClubRelationCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      relation: ClubRelation,
      occurredAt: Instant
  )
