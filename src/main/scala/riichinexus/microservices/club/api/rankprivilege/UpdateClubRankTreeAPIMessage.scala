package riichinexus.microservices.club.api.rankprivilege

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
import riichinexus.microservices.club.objects.rankprivilege.ClubRankNode
import riichinexus.microservices.club.objects.profile.ClubView
import riichinexus.microservices.club.objects.rankprivilege.apiTypes.ClubRankNodeRequest
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
      requestedClubId = ClubId(clubId)
      rankTree = ranks.map(rankNode)
      savedClub <- IO.blocking {
        updateRankTree(context.connection, requestedClubId, actor, rankTree)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(updateRankTreeAudit(savedClub, actor, note, occurredAt)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def updateRankTree(
      connection: java.sql.Connection,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      ranks: Vector[ClubRankNode]
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(
        actor = actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      commitRankTreeUpdate(connection, club, ranks)
    }

  private def commitRankTreeUpdate(
      connection: java.sql.Connection,
      club: Club,
      ranks: Vector[ClubRankNode]
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.updateRankTree(club, ranks))

  private def updateRankTreeAudit(
      updatedClub: Club,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      occurredAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = updatedClub.id.value,
        eventType = AuditEventType.ClubRankTreeUpdated,
        occurredAt = occurredAt,
        actorId = actor.playerId,
        details = Map(StructuredEventField.toString(StructuredEventField.RankCount) -> updatedClub.rankTree.size.toString),
        note = note
      )
    )

  private def rankNode(request: ClubRankNodeRequest): ClubRankNode =
    ClubRankNode(
      code = request.code,
      label = request.label,
      minimumContribution = request.minimumContribution,
      privileges = request.privileges
    )

