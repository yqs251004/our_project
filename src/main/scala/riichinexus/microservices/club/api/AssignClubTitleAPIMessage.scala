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
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubTitleAssignment
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.notification.api.`private`.RecordNotificationPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
/** 为俱乐部成员授予头衔。 */
final case class AssignClubTitleAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      assignedAt <- IO.realTimeInstant
      command = AssignClubTitleCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        title = title,
        note = note,
        assignedAt = assignedAt
      )
      savedClub <- assignTitle(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(assignTitleAudit(command)).plan(context)
      _ <- RecordNotificationPrivateAPIMessage(assignTitleNotification(savedClub, command)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def assignTitle(
      context: ApiPlanContext,
      command: AssignClubTitleCommand
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId))
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureTitleCanBeAssigned(club, player, command)
          IO.blocking(Some(commitTitleAssignment(connection, club, command, assignedBy = command.actor.playerId.getOrElse(club.creator))))
    yield savedClub

  private def ensureTitleCanBeAssigned(
      club: Club,
      player: PlayerPrivateView,
      command: AssignClubTitleCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"PlayerPrivateView ${command.playerId.value} cannot receive club title")
    ClubAuthorization.requireClubMember(club, command.playerId, "set internal title")
    ClubAuthorization.requireClubAdmin(actor = command.actor,
      club = club,
      permission = Permission.SetClubTitle
    )

  private def commitTitleAssignment(
      connection: java.sql.Connection,
      club: Club,
      command: AssignClubTitleCommand,
      assignedBy: PlayerId
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(
      connection,
      ClubFunctions.setInternalTitle(club,
          ClubTitleAssignment(
            playerId = command.playerId,
            title = command.title,
            assignedBy = assignedBy,
            assignedAt = command.assignedAt,
            note = command.note
          )
        )
    )

  private def assignTitleAudit(command: AssignClubTitleCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = AuditEventType.ClubTitleAssigned,
        occurredAt = command.assignedAt,
        actorId = command.actor.playerId,
        details = Map(
          "playerId" -> command.playerId.value,
          "title" -> command.title
        ),
        note = command.note
      )
    )

  private def assignTitleNotification(
      updatedClub: Club,
      command: AssignClubTitleCommand
  ): CreateNotificationRequest =
    CreateNotificationRequest(
      recipientPlayerId = command.playerId.value,
      notificationType = NotificationType.ClubTitleAssigned,
      title = "获得俱乐部专属头衔",
      body = s"你在 ${updatedClub.name} 获得了专属头衔「${command.title}」。",
      severity = Some("success"),
      sourceService = "club",
      sourceType = "club-title",
      sourceId = updatedClub.id.value,
      actionUrl = Some(s"/public/clubs/${updatedClub.id.value}"),
      objects = Map(
        "clubId" -> updatedClub.id.value,
        "playerId" -> command.playerId.value,
        "title" -> command.title
      )
    )

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class AssignClubTitleCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      title: String,
      note: Option[String],
      assignedAt: Instant
  )
