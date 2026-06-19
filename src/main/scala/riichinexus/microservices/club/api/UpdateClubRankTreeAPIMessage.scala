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
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubRankNode
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.ClubRankNodeRequest
/** 更新俱乐部段位树。 */
final case class UpdateClubRankTreeAPIMessage(
    clubId: String,
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      command = UpdateClubRankTreeCommand(
        clubId = ClubId(clubId),
        actor = actor,
        ranks = ranks.map(rankNode),
        note = note,
        occurredAt = occurredAt
      )
      savedClub <- IO.blocking {
        {
          updateRankTree(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(updateRankTreeAudit(savedClub, command)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def updateRankTree(
      connection: java.sql.Connection,
      command: UpdateClubRankTreeCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      commitRankTreeUpdate(connection, club, command)
    }

  private def commitRankTreeUpdate(
      connection: java.sql.Connection,
      club: Club,
      command: UpdateClubRankTreeCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.updateRankTree(club, command.ranks))

  private def updateRankTreeAudit(
      updatedClub: Club,
      command: UpdateClubRankTreeCommand
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = updatedClub.id.value,
        eventType = AuditEventType.ClubRankTreeUpdated,
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map("rankCount" -> updatedClub.rankTree.size.toString),
        note = command.note
      )
    )

  private final case class UpdateClubRankTreeCommand(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      ranks: Vector[ClubRankNode],
      note: Option[String],
      occurredAt: Instant
  )

  private def rankNode(request: ClubRankNodeRequest): ClubRankNode =
    ClubRankNode(
      code = request.code,
      label = request.label,
      minimumContribution = request.minimumContribution,
      privileges = request.privileges
    )
