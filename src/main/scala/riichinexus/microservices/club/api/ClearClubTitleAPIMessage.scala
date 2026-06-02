package riichinexus.microservices.club.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

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
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class ClearClubTitleAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(operatorId)).resolve(context.connection))
      clearedAt <- IO.realTimeInstant
      command = ClearClubTitleCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        note = note,
        clearedAt = clearedAt
      )
      cleared <- IO.blocking {
        {
          clearTitle(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(clearTitleAudit(command, cleared.existingAssignment)).plan(context)
    yield ClubView.fromDomain(cleared.club)

  private def clearTitle(
      connection: java.sql.Connection,
      command: ClearClubTitleCommand
  ): Option[ClearedClubTitle] =
    for
      club <- riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId)
      player <- PlayerPersistenceFunctions.findPlayer(connection, command.playerId)
    yield
      ensureTitleCanBeCleared(club, player, command)
      val existingAssignment = resolveExistingAssignment(club, command)
      ClearedClubTitle(
        club = commitTitleClear(connection, club, command),
        existingAssignment = existingAssignment
      )

  private def ensureTitleCanBeCleared(
      club: Club,
      player: Player,
      command: ClearClubTitleCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot clear club title")
    ClubAuthorization.requireClubMember(club, command.playerId, "clear internal title")
    ClubAuthorization.requireClubAdmin(actor = command.actor,
      club = club,
      permission = Permission.SetClubTitle
    )

  private def resolveExistingAssignment(
      club: Club,
      command: ClearClubTitleCommand
  ): ClubTitleAssignment =
    club.titleAssignments.find(_.playerId == command.playerId)
      .getOrElse(
        throw NoSuchElementException(
          s"Player ${command.playerId.value} does not hold a title in club ${command.clubId.value}"
        )
      )

  private def commitTitleClear(
      connection: java.sql.Connection,
      club: Club,
      command: ClearClubTitleCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.clearInternalTitle(club, command.playerId))

  private def clearTitleAudit(
      command: ClearClubTitleCommand,
      existingAssignment: ClubTitleAssignment
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = "ClubTitleCleared",
        occurredAt = command.clearedAt,
        actorId = command.actor.playerId,
        details = Map(
          "playerId" -> command.playerId.value,
          "title" -> existingAssignment.title
        ),
        note = command.note
      )
    )

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class ClearClubTitleCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String],
      clearedAt: Instant
  )

  private final case class ClearedClubTitle(
      club: Club,
      existingAssignment: ClubTitleAssignment
  )
