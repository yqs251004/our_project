package riichinexus.microservices.club.api

import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.utils.ResolveAccessPrincipal
import riichinexus.microservices.club.domain.{Club, ClubAuthorization}
import riichinexus.microservices.club.domain.relationmanagement.functions.ClubRelationAuthorizationFunctions
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.notification.api.`private`.CreateBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.microservices.player.api.`private`.*
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import upickle.default.*

final case class SubmitClubRelationRequestAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
) extends APIMessage[Vector[Notification]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Notification]] =
    for
      actor <- ResolveAccessPrincipal(PlayerId(operatorId)).plan(context)
      notificationRequests <- buildNotificationRequests(context, actor)
      notifications <- CreateBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield notifications

  private def buildNotificationRequests(
      context: ApiPlanContext,
      actor: AccessPrincipal
  ): IO[Vector[CreateNotificationRequest]] =
    val connection = context.connection
    for
      sourceClub <- IO.blocking(resolveClub(connection, ClubId(clubId)))
      targetClub <- IO.blocking(resolveClub(connection, ClubId(targetClubId)))
      players <- ListAllPlayersPrivateAPIMessage().plan(context)
      requests <- IO.blocking {
        ensureRequestCanBeSubmitted(actor, sourceClub, targetClub)

        val superAdmins = players
          .filter(player => player.status == PlayerStatus.Active && player.roleGrants.exists(_.role == Role.SuperAdmin))

        if superAdmins.isEmpty then
          throw IllegalStateException("No active super admin is available to review club relation requests")

        val trimmedNote = note.map(_.trim).filter(_.nonEmpty)
        val relationText = relationLabel(relation)
        val body =
          s"${sourceClub.name} 申请将与 ${targetClub.name} 的公开关系调整为 $relationText。" +
            trimmedNote.fold("")(value => s" 备注：$value")

        superAdmins.map { superAdmin =>
          CreateNotificationRequest(
            recipientPlayerId = superAdmin.id.value,
            notificationType = "ClubRelationChangeRequested",
            title = "俱乐部关系调整申请",
            body = body,
            severity = Some("info"),
            sourceService = "club",
            sourceType = "club-relation-request",
            sourceId = s"${sourceClub.id.value}:${targetClub.id.value}:${ClubRelationKind.toString(relation)}",
            actionUrl = Some(s"/public/clubs/${sourceClub.id.value}"),
            objects = Map(
              "sourceClubId" -> sourceClub.id.value,
              "sourceClubName" -> sourceClub.name,
              "targetClubId" -> targetClub.id.value,
              "targetClubName" -> targetClub.name,
              "relation" -> ClubRelationKind.toString(relation),
              "operatorId" -> operatorId
            ) ++ trimmedNote.map(value => Map("note" -> value)).getOrElse(Map.empty)
          )
        }
      }
    yield requests

  private def resolveClub(connection: Connection, id: ClubId): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable
      .findById(connection, id)
      .map { club =>
        ClubAuthorization.ensureClubActive(club)
        club
      }
      .getOrElse(throw NoSuchElementException(s"Club ${id.value} was not found"))

  private def ensureRequestCanBeSubmitted(
      actor: AccessPrincipal,
      sourceClub: Club,
      targetClub: Club
  ): Unit =
    if sourceClub.id == targetClub.id then
      throw IllegalArgumentException("A club cannot request a relation to itself")

    ClubRelationAuthorizationFunctions.requireRelationRequestActor(actor, sourceClub)

  private def relationLabel(kind: ClubRelationKind): String =
    kind match
      case ClubRelationKind.Alliance => "联盟"
      case ClubRelationKind.Rivalry  => "对抗"
      case ClubRelationKind.Neutral  => "中立"
