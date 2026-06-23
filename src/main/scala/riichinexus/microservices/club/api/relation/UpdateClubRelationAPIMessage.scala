package riichinexus.microservices.club.api.relation

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
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
import riichinexus.microservices.club.domain.relation.model.ClubRelation
import riichinexus.microservices.club.domain.relation.functions.ClubRelationAuthorizationFunctions
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.relation.ClubRelationKind
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.profile.ClubView
import riichinexus.microservices.club.tables.clubs.ClubTable
/** 更新俱乐部关系状态。 */
final case class UpdateClubRelationAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      relationUpdatedAt <- IO.realTimeInstant
      occurredAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      relationDraft = ClubRelation(
        targetClubId = ClubId(targetClubId),
        relation = relation,
        updatedAt = relationUpdatedAt,
        note = note
      )
      sourceClub <- loadActiveClub(context, requestedClubId)
      targetClub <- loadActiveClub(context, relationDraft.targetClubId)
      _ <- IO.blocking(ensureRelationCanBeUpdated(sourceClub, requestedClubId, actor, relationDraft))
      savedClub <- saveRelationUpdate(context, sourceClub, targetClub, requestedClubId, relationDraft)
      _ <- RecordAuditEventsPrivateAPIMessage(updateRelationAudit(requestedClubId, actor, relationDraft, occurredAt)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def loadActiveClub(context: ApiPlanContext, clubId: ClubId): IO[Club] =
    IO.blocking {
      ClubTable
        .findById(context.connection, clubId)
        .map { club =>
          ClubAuthorization.ensureClubActive(club)
          club
        }
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    }

  private def ensureRelationCanBeUpdated(
      club: Club,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      relation: ClubRelation
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ClubRelationAuthorizationFunctions.requireDirectRelationUpdate(actor)
    if relation.targetClubId == clubId then
      throw IllegalArgumentException("A club cannot define a relation to itself")

  private def saveRelationUpdate(
      context: ApiPlanContext,
      club: Club,
      targetClub: Club,
      clubId: ClubId,
      relation: ClubRelation
  ): IO[Club] =
    IO.blocking {
      val sourceClub =
        if relation.relation == ClubRelationKind.Neutral then
          ClubFunctions.removeRelation(club, relation.targetClubId)
        else ClubFunctions.upsertRelation(club, relation)

      val savedSource = ClubTable.save(context.connection, sourceClub)
      if relation.relation == ClubRelationKind.Neutral then
        ClubTable.save(context.connection, ClubFunctions.removeRelation(targetClub, clubId))
      else
        ClubTable.save(
          context.connection,
          ClubFunctions.upsertRelation(
            targetClub,
            relation.copy(targetClubId = clubId)
          )
        )
      savedSource
    }

  private def updateRelationAudit(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      relation: ClubRelation,
      occurredAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = clubId.value,
        eventType = AuditEventType.ClubRelationUpdated,
        occurredAt = occurredAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.TargetClubId) -> relation.targetClubId.value,
          StructuredEventField.toString(StructuredEventField.Relation) -> ClubRelationKind.toString(relation.relation)
        ),
        note = relation.note
      )
    )
