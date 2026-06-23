package riichinexus.microservices.club.api.membership

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType}
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.membership.model.ClubTitleAssignment
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.profile.ClubView
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
      requestedClubId = ClubId(clubId)
      requestedPlayerId = PlayerId(playerId)
      savedClub <- assignTitle(context, requestedClubId, requestedPlayerId, actor, title, note, assignedAt).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(assignTitleAudit(requestedClubId, requestedPlayerId, actor, title, note, assignedAt)).plan(context)
      _ <- RecordNotificationPrivateAPIMessage(assignTitleNotification(savedClub, requestedPlayerId, title)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def assignTitle(
      context: ApiPlanContext,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      title: String,
      note: Option[String],
      assignedAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId))
      player <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureTitleCanBeAssigned(club, player, playerId, actor)
          IO.blocking(Some(commitTitleAssignment(connection, club, playerId, title, note, assignedAt, assignedBy = actor.playerId.getOrElse(club.creator))))
    yield savedClub

  private def ensureTitleCanBeAssigned(
      club: Club,
      player: PlayerPrivateView,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"PlayerPrivateView ${playerId.value} cannot receive club title")
    ClubAuthorization.requireClubMember(club, playerId, "set internal title")
    ClubAuthorization.requireClubAdmin(actor = actor,
      club = club,
      permission = Permission.SetClubTitle
    )

  private def commitTitleAssignment(
      connection: java.sql.Connection,
      club: Club,
      playerId: PlayerId,
      title: String,
      note: Option[String],
      assignedAt: Instant,
      assignedBy: PlayerId
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(
      connection,
      ClubFunctions.setInternalTitle(club,
          ClubTitleAssignment(
            playerId = playerId,
            title = title,
            assignedBy = assignedBy,
            assignedAt = assignedAt,
            note = note
          )
        )
    )

  private def assignTitleAudit(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      title: String,
      note: Option[String],
      assignedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = clubId.value,
        eventType = AuditEventType.ClubTitleAssigned,
        occurredAt = assignedAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value,
          StructuredEventField.toString(StructuredEventField.Title) -> title
        ),
        note = note
      )
    )

  private def assignTitleNotification(
      updatedClub: Club,
      playerId: PlayerId,
      title: String
  ): CreateNotificationRequest =
    CreateNotificationRequest(
      recipientPlayerId = playerId.value,
      notificationType = NotificationType.ClubTitleAssigned,
      title = "获得俱乐部专属头衔",
      body = s"你在 ${updatedClub.name} 获得了专属头衔「${title}」。",
      severity = Some(NotificationSeverity.Success),
      sourceService = NotificationSourceService.Club,
      sourceType = NotificationSourceType.ClubTitle,
      sourceId = updatedClub.id.value,
      actionUrl = Some(s"/public/clubs/${updatedClub.id.value}"),
      objects = Map(
        StructuredEventField.toString(StructuredEventField.ClubId) -> updatedClub.id.value,
        StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value,
        StructuredEventField.toString(StructuredEventField.Title) -> title
      )
    )

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

