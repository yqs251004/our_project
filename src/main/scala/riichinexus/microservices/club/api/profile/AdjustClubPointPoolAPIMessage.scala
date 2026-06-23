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
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.microservices.club.objects.profile.ClubView
/** 调整俱乐部积分池。 */
final case class AdjustClubPointPoolAPIMessage(
    clubId: String,
    operatorId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      savedClub <- IO.blocking {
        adjustPointPool(context.connection, requestedClubId, actor, delta)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(adjustPointPoolAudit(savedClub, actor, delta, note, occurredAt)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def adjustPointPool(
      connection: java.sql.Connection,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      delta: Int
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubCapability(
        actor = actor,
        club = club,
        permission = Permission.ManageClubOperations,
        delegatedPrivileges = Set(ClubPrivilegeCode.ManageBank)
      )
      commitPointPoolAdjustment(connection, club, delta)
    }

  private def commitPointPoolAdjustment(
      connection: java.sql.Connection,
      club: Club,
      delta: Int
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.adjustPointPool(club, delta))

  private def adjustPointPoolAudit(
      updatedClub: Club,
      actor: AccessPrincipalPrivateView,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = updatedClub.id.value,
        eventType = AuditEventType.ClubPointPoolAdjusted,
        occurredAt = occurredAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.Delta) -> delta.toString,
          StructuredEventField.toString(StructuredEventField.PointPool) -> updatedClub.pointPool.toString
        ),
        note = note
      )
    )

