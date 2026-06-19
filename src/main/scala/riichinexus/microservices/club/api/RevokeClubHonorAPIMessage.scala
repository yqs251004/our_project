package riichinexus.microservices.club.api
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import upickle.default.ReadWriter

/** 撤销俱乐部成员荣誉。 */
final case class RevokeClubHonorAPIMessage(
    clubId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      command = RevokeClubHonorCommand(
        clubId = ClubId(clubId),
        actor = actor,
        title = title,
        note = note,
        occurredAt = occurredAt
      )
      savedClub <- IO.blocking {
        {
          revokeHonor(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(revokeHonorAudit(command)).plan(context)
    yield ClubView.fromDomain(savedClub)

  private def revokeHonor(
      connection: java.sql.Connection,
      command: RevokeClubHonorCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      ensureHonorExists(club, command)
      commitHonorRevocation(connection, club, command)
    }

  private def ensureHonorExists(club: Club, command: RevokeClubHonorCommand): Unit =
    val normalizedTitle = command.title.trim.toLowerCase
    if !club.honors.exists(_.title.trim.toLowerCase == normalizedTitle) then
      throw NoSuchElementException(s"Club ${command.clubId.value} does not have honor '${command.title}'")

  private def commitHonorRevocation(
      connection: java.sql.Connection,
      club: Club,
      command: RevokeClubHonorCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.removeHonor(club, command.title))

  private def revokeHonorAudit(command: RevokeClubHonorCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = "ClubHonorRevoked",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map("title" -> command.title),
        note = command.note
      )
    )

  private final case class RevokeClubHonorCommand(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      title: String,
      note: Option[String],
      occurredAt: Instant
  )
