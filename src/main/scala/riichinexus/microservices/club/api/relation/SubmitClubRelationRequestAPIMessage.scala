package riichinexus.microservices.club.api.relation
import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType, StructuredEventField}
import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.domain.relation.functions.ClubRelationAuthorizationFunctions
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.objects.relation.ClubRelationKind
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.microservices.player.api.`private`.ListAllPlayersPrivateAPIMessage
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.ClubJsonCodecs.given
/** 提交俱乐部关系申请。 */
final case class SubmitClubRelationRequestAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
) extends APIMessage[Vector[Notification]]:

  override def plan(context: ApiPlanContext): IO[Vector[Notification]] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      sourceClubKey = ClubId(clubId)
      targetClubKey = ClubId(targetClubId)
      operatorPlayerId = PlayerId(operatorId)
      normalizedNote = note.map(_.trim).filter(_.nonEmpty)
      sourceClub <- IO.blocking(resolveClub(context.connection, sourceClubKey))
      targetClub <- IO.blocking(resolveClub(context.connection, targetClubKey))
      players <- ListAllPlayersPrivateAPIMessage().plan(context)
      _ <- IO.delay(ensureRequestCanBeSubmitted(actor, sourceClub, targetClub))
      superAdmins = players.filter(player => player.active && player.roleGrants.exists(_.role == Role.SuperAdmin))
      _ <- IO.delay(ensureActiveSuperAdminAvailable(superAdmins.size))
      notificationRequests = relationNotificationRequests(sourceClub, targetClub, operatorPlayerId, relation, normalizedNote, superAdmins.map(_.id))
      notifications <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield notifications

  private def resolveClub(connection: Connection, id: ClubId): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable
      .findById(connection, id)
      .map { club =>
        ClubAuthorization.ensureClubActive(club)
        club
      }
      .getOrElse(throw NoSuchElementException(s"Club ${id.value} was not found"))

  private def ensureRequestCanBeSubmitted(
      actor: AccessPrincipalPrivateView,
      sourceClub: Club,
      targetClub: Club
  ): Unit =
    if sourceClub.id == targetClub.id then
      throw IllegalArgumentException("A club cannot request a relation to itself")

    ClubRelationAuthorizationFunctions.requireRelationRequestActor(actor, sourceClub)

  private def ensureActiveSuperAdminAvailable(superAdminCount: Int): Unit =
    if superAdminCount == 0 then
      throw IllegalStateException("No active super admin is available to review club relation requests")

  private def relationNotificationRequests(
      sourceClub: Club,
      targetClub: Club,
      operatorId: PlayerId,
      relation: ClubRelationKind,
      note: Option[String],
      superAdminIds: Vector[PlayerId]
  ): Vector[CreateNotificationRequest] =
    val relationText = ClubRelationKind.toString(relation)
    val body =
      s"${sourceClub.name} 申请将与 ${targetClub.name} 的公开关系调整为 $relationText。" +
        note.fold("")(value => s" 备注：$value")

    superAdminIds.map { superAdminId =>
      CreateNotificationRequest(
        recipientPlayerId = superAdminId.value,
        notificationType = NotificationType.ClubRelationChangeRequested,
        title = "俱乐部关系调整申请",
        body = body,
        severity = Some(NotificationSeverity.Info),
        sourceService = NotificationSourceService.Club,
        sourceType = NotificationSourceType.ClubRelationRequest,
        sourceId = s"${sourceClub.id.value}:${targetClub.id.value}:${ClubRelationKind.toString(relation)}",
        actionUrl = Some(s"/public/clubs/${sourceClub.id.value}"),
        objects = Map(
          StructuredEventField.toString(StructuredEventField.SourceClubId) -> sourceClub.id.value,
          StructuredEventField.toString(StructuredEventField.SourceClubName) -> sourceClub.name,
          StructuredEventField.toString(StructuredEventField.TargetClubId) -> targetClub.id.value,
          StructuredEventField.toString(StructuredEventField.TargetClubName) -> targetClub.name,
          StructuredEventField.toString(StructuredEventField.Relation) -> ClubRelationKind.toString(relation),
          StructuredEventField.toString(StructuredEventField.OperatorId) -> operatorId.value
        ) ++ note
          .map(value => Map(StructuredEventField.toString(StructuredEventField.Note) -> value))
          .getOrElse(Map.empty)
      )
    }
