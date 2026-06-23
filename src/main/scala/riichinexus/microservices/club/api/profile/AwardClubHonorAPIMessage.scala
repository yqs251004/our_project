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
import riichinexus.microservices.club.domain.profile.model.ClubHonor
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.profile.ClubView
/** 为俱乐部成员授予荣誉。 */
final case class AwardClubHonorAPIMessage(
    clubId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None,
    achievedAt: Option[Instant] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      honor = ClubHonor(title = title, achievedAt = achievedAt.getOrElse(occurredAt), note = note)
      savedClub <- IO.blocking {
        awardHonor(context.connection, requestedClubId, actor, honor)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(awardHonorAudit(requestedClubId, actor, honor, occurredAt)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def awardHonor(
      connection: java.sql.Connection,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      honor: ClubHonor
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(
        actor = actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      commitHonorAward(connection, club, honor)
    }

  private def commitHonorAward(
      connection: java.sql.Connection,
      club: Club,
      honor: ClubHonor
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.addHonor(club, honor))

  private def awardHonorAudit(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      honor: ClubHonor,
      occurredAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = clubId.value,
        eventType = AuditEventType.ClubHonorAwarded,
        occurredAt = occurredAt,
        actorId = actor.playerId,
        details = Map(StructuredEventField.toString(StructuredEventField.Title) -> honor.title),
        note = honor.note
      )
    )

