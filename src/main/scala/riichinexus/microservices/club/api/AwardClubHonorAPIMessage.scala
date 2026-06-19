package riichinexus.microservices.club.api
import riichinexus.microservices.club.domain.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
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
import riichinexus.microservices.club.domain.clubmanagement.model.ClubHonor
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
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
      command = AwardClubHonorCommand(
        clubId = ClubId(clubId),
        actor = actor,
        honor = ClubHonor(title = title, achievedAt = achievedAt.getOrElse(occurredAt), note = note),
        occurredAt = occurredAt
      )
      savedClub <- IO.blocking {
        {
          awardHonor(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(awardHonorAudit(command)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def awardHonor(
      connection: java.sql.Connection,
      command: AwardClubHonorCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      commitHonorAward(connection, club, command)
    }

  private def commitHonorAward(
      connection: java.sql.Connection,
      club: Club,
      command: AwardClubHonorCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.addHonor(club, command.honor))

  private def awardHonorAudit(command: AwardClubHonorCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = AuditEventType.ClubHonorAwarded,
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map("title" -> command.honor.title),
        note = command.honor.note
      )
    )

  private final case class AwardClubHonorCommand(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      honor: ClubHonor,
      occurredAt: Instant
  )
