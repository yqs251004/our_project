package riichinexus.microservices.club.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

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
import riichinexus.microservices.notification.api.`private`.CreateNotificationPrivateAPIMessage
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class AdjustClubMemberContributionAPIMessage(
    clubId: String,
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipal(PlayerId(operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      command = AdjustClubMemberContributionCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        delta = delta,
        note = note,
        occurredAt = occurredAt
      )
      savedClub <- adjustMemberContribution(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(adjustMemberContributionAudit(savedClub, command)).plan(context)
      _ <- CreateNotificationPrivateAPIMessage(adjustMemberContributionNotification(savedClub, command)).plan(context)
    yield ClubView.fromDomain(savedClub)

  private def adjustMemberContribution(
      context: ApiPlanContext,
      command: AdjustClubMemberContributionCommand
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId))
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureContributionCanBeAdjusted(club, player, command)
          val nextContribution = resolveNextContribution(club, command)
          val updatedBy = command.actor.playerId.getOrElse(club.creator)
          IO.blocking(Some(commitContributionAdjustment(connection, club, command, nextContribution, updatedBy)))
    yield savedClub

  private def ensureContributionCanBeAdjusted(
      club: Club,
      player: Player,
      command: AdjustClubMemberContributionCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot receive club contribution updates")
    ClubAuthorization.requireClubMember(club, command.playerId, "adjust contribution")
    ClubAuthorization.requireClubAdmin(actor = command.actor,
      club = club,
      permission = Permission.ManageClubOperations
    )

  private def resolveNextContribution(
      club: Club,
      command: AdjustClubMemberContributionCommand
  ): Int =
    val nextContribution = ClubFunctions.contributionOf(club, command.playerId) + command.delta
    require(nextContribution >= 0, s"Club member contribution for ${command.playerId.value} cannot be negative")
    nextContribution

  private def commitContributionAdjustment(
      connection: java.sql.Connection,
      club: Club,
      command: AdjustClubMemberContributionCommand,
      nextContribution: Int,
      updatedBy: PlayerId
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(
      connection,
      ClubFunctions.updateMemberContribution(club,
          ClubMemberContribution(
            playerId = command.playerId,
            amount = nextContribution,
            updatedAt = command.occurredAt,
            updatedBy = updatedBy,
            note = command.note
          )
        )
    )

  private def adjustMemberContributionAudit(
      updatedClub: Club,
      command: AdjustClubMemberContributionCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "club",
        aggregateId = updatedClub.id.value,
        eventType = "ClubMemberContributionAdjusted",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map(
          "playerId" -> command.playerId.value,
          "delta" -> command.delta.toString,
          "contribution" -> ClubFunctions.contributionOf(updatedClub, command.playerId).toString,
          "rankCode" -> ClubFunctions.rankFor(updatedClub, command.playerId).map(_.code).getOrElse("unknown")
        ),
        note = command.note
      )
    )

  private def adjustMemberContributionNotification(
      updatedClub: Club,
      command: AdjustClubMemberContributionCommand
  ): CreateNotificationRequest =
    val nextContribution = ClubFunctions.contributionOf(updatedClub, command.playerId)
    val deltaText =
      if command.delta > 0 then s"+${command.delta}"
      else command.delta.toString
    CreateNotificationRequest(
      recipientPlayerId = command.playerId.value,
      notificationType = "ClubMemberContributionAdjusted",
      title = "俱乐部贡献值已调整",
      body = s"你在 ${updatedClub.name} 的贡献值调整了 $deltaText，当前贡献值为 $nextContribution。",
      severity = Some("info"),
      sourceService = "club",
      sourceType = "club-contribution",
      sourceId = updatedClub.id.value,
      actionUrl = Some(s"/public/clubs/${updatedClub.id.value}"),
      objects = Map(
        "clubId" -> updatedClub.id.value,
        "playerId" -> command.playerId.value,
        "delta" -> command.delta.toString,
        "contribution" -> nextContribution.toString
      )
    )

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class AdjustClubMemberContributionCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  )
