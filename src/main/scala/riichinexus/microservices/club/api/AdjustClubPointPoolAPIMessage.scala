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
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.club.objects.clubmanagement.ClubView
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
      command = AdjustClubPointPoolCommand(
        clubId = ClubId(clubId),
        actor = actor,
        delta = delta,
        note = note,
        occurredAt = occurredAt
      )
      savedClub <- IO.blocking {
        {
          adjustPointPool(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(adjustPointPoolAudit(savedClub, command)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def adjustPointPool(
      connection: java.sql.Connection,
      command: AdjustClubPointPoolCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubCapability(        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations,
        delegatedPrivileges = Set(ClubPrivilegeCode.ManageBank)
      )
      commitPointPoolAdjustment(connection, club, command)
    }

  private def commitPointPoolAdjustment(
      connection: java.sql.Connection,
      club: Club,
      command: AdjustClubPointPoolCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.adjustPointPool(club, command.delta))

  private def adjustPointPoolAudit(
      updatedClub: Club,
      command: AdjustClubPointPoolCommand
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = updatedClub.id.value,
        eventType = AuditEventType.ClubPointPoolAdjusted,
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map(
          "delta" -> command.delta.toString,
          "pointPool" -> updatedClub.pointPool.toString
        ),
        note = command.note
      )
    )

  /** 调整俱乐部点数池时使用的已授权内部命令。 */
  private final case class AdjustClubPointPoolCommand(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  )
