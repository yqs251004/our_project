package riichinexus.microservices.club.api

import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.club.domain.{Club, ClubAuthorization}
import riichinexus.microservices.club.domain.relationmanagement.functions.ClubRelationAuthorizationFunctions
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.microservices.player.api.`private`.ListAllPlayersPrivateAPIMessage
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import upickle.default.ReadWriter

/** 提交俱乐部关系申请。 */
final case class SubmitClubRelationRequestAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
) extends APIMessage[Vector[Notification]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Notification]] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      command = SubmitClubRelationRequestCommand(
        sourceClubId = ClubId(clubId),
        targetClubId = ClubId(targetClubId),
        actor = actor,
        operatorId = PlayerId(operatorId),
        relation = relation,
        note = note.map(_.trim).filter(_.nonEmpty)
      )
      sourceClub <- IO.blocking(resolveClub(context.connection, command.sourceClubId))
      targetClub <- IO.blocking(resolveClub(context.connection, command.targetClubId))
      players <- ListAllPlayersPrivateAPIMessage().plan(context)
      _ <- IO.delay(ensureRequestCanBeSubmitted(command.actor, sourceClub, targetClub))
      superAdmins = players.filter(player => player.active && player.roleGrants.exists(_.role == Role.SuperAdmin))
      _ <- IO.delay(ensureActiveSuperAdminAvailable(superAdmins.size))
      notificationRequests = relationNotificationRequests(command, sourceClub, targetClub, superAdmins.map(_.id))
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
      command: SubmitClubRelationRequestCommand,
      sourceClub: Club,
      targetClub: Club,
      superAdminIds: Vector[PlayerId]
  ): Vector[CreateNotificationRequest] =
    val relationText = relationLabel(command.relation)
    val body =
      s"${sourceClub.name} 申请将与 ${targetClub.name} 的公开关系调整为 $relationText。" +
        command.note.fold("")(value => s" 备注：$value")

    superAdminIds.map { superAdminId =>
      CreateNotificationRequest(
        recipientPlayerId = superAdminId.value,
        notificationType = "ClubRelationChangeRequested",
        title = "俱乐部关系调整申请",
        body = body,
        severity = Some("info"),
        sourceService = "club",
        sourceType = "club-relation-request",
        sourceId = s"${sourceClub.id.value}:${targetClub.id.value}:${ClubRelationKind.toString(command.relation)}",
        actionUrl = Some(s"/public/clubs/${sourceClub.id.value}"),
        objects = Map(
          "sourceClubId" -> sourceClub.id.value,
          "sourceClubName" -> sourceClub.name,
          "targetClubId" -> targetClub.id.value,
          "targetClubName" -> targetClub.name,
          "relation" -> ClubRelationKind.toString(command.relation),
          "operatorId" -> command.operatorId.value
        ) ++ command.note.map(value => Map("note" -> value)).getOrElse(Map.empty)
      )
    }

  private def relationLabel(kind: ClubRelationKind): String =
    kind match
      case ClubRelationKind.Alliance => "联盟"
      case ClubRelationKind.Rivalry  => "对抗"
      case ClubRelationKind.Neutral  => "中立"

  private final case class SubmitClubRelationRequestCommand(
      sourceClubId: ClubId,
      targetClubId: ClubId,
      actor: AccessPrincipalPrivateView,
      operatorId: PlayerId,
      relation: ClubRelationKind,
      note: Option[String]
  )
