package riichinexus.microservices.club.api.profile

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.profile.ClubView
/** 撤销俱乐部成员荣誉。 */
final case class RevokeClubHonorAPIMessage(
    clubId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      savedClub <- IO.blocking {
        revokeHonor(context.connection, requestedClubId, actor, title)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(revokeHonorAudit(requestedClubId, actor, title, note, occurredAt)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def revokeHonor(
      connection: java.sql.Connection,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      title: String
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(
        actor = actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      ensureHonorExists(club, clubId, title)
      commitHonorRevocation(connection, club, title)
    }

  private def ensureHonorExists(club: Club, clubId: ClubId, title: String): Unit =
    val normalizedTitle = title.trim.toLowerCase
    if !club.honors.exists(_.title.trim.toLowerCase == normalizedTitle) then
      throw NoSuchElementException(s"Club ${clubId.value} does not have honor '${title}'")

  private def commitHonorRevocation(
      connection: java.sql.Connection,
      club: Club,
      title: String
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.removeHonor(club, title))

  private def revokeHonorAudit(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      title: String,
      note: Option[String],
      occurredAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = clubId.value,
        eventType = AuditEventType.ClubHonorRevoked,
        occurredAt = occurredAt,
        actorId = actor.playerId,
        details = Map(StructuredEventField.toString(StructuredEventField.Title) -> title),
        note = note
      )
    )

