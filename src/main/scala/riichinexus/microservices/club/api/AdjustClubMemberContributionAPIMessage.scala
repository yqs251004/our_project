package riichinexus.microservices.club.api
import riichinexus.microservices.club.domain.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMemberContribution
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.notification.api.`private`.RecordNotificationPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
/** 调整俱乐部成员贡献值。 */
final case class AdjustClubMemberContributionAPIMessage(
    clubId: String,
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
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
      _ <- RecordNotificationPrivateAPIMessage(adjustMemberContributionNotification(savedClub, command)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def adjustMemberContribution(
      context: ApiPlanContext,
      command: AdjustClubMemberContributionCommand
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId))
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.playerId.value} was not found")))
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
      player: PlayerPrivateView,
      command: AdjustClubMemberContributionCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"PlayerPrivateView ${command.playerId.value} cannot receive club contribution updates")
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
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = updatedClub.id.value,
        eventType = AuditEventType.ClubMemberContributionAdjusted,
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
      notificationType = NotificationType.ClubMemberContributionAdjusted,
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

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  /** 调整成员贡献值时使用的已授权内部命令。 */
  private final case class AdjustClubMemberContributionCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  )
