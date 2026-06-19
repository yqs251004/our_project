package riichinexus.microservices.club.api
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
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
import riichinexus.microservices.club.domain.relationmanagement.model.ClubRelation
import riichinexus.microservices.club.domain.relationmanagement.functions.ClubRelationAuthorizationFunctions
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.ReadWriter

/** 更新俱乐部关系状态。 */
final case class UpdateClubRelationAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      command <- buildCommand(context)
      sourceClub <- loadActiveClub(context, command.clubId)
      targetClub <- loadActiveClub(context, command.relation.targetClubId)
      _ <- IO.blocking(ensureRelationCanBeUpdated(sourceClub, command))
      savedClub <- saveRelationUpdate(context, sourceClub, targetClub, command)
      _ <- RecordAuditEventsPrivateAPIMessage(updateRelationAudit(command)).plan(context)
    yield ClubView.fromDomain(savedClub)

  private def buildCommand(context: ApiPlanContext): IO[UpdateClubRelationCommand] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      relationUpdatedAt <- IO.realTimeInstant
      occurredAt <- IO.realTimeInstant
    yield UpdateClubRelationCommand(
      clubId = ClubId(clubId),
      actor = actor,
      relation = ClubRelation(
        targetClubId = ClubId(targetClubId),
        relation = relation,
        updatedAt = relationUpdatedAt,
        note = note
      ),
      occurredAt = occurredAt
    )

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
      command: UpdateClubRelationCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ClubRelationAuthorizationFunctions.requireDirectRelationUpdate(command.actor)
    if command.relation.targetClubId == command.clubId then
      throw IllegalArgumentException("A club cannot define a relation to itself")

  private def saveRelationUpdate(
      context: ApiPlanContext,
      club: Club,
      targetClub: Club,
      command: UpdateClubRelationCommand
  ): IO[Club] =
    IO.blocking {
      val sourceClub =
        if command.relation.relation == ClubRelationKind.Neutral then
          ClubFunctions.removeRelation(club, command.relation.targetClubId)
        else ClubFunctions.upsertRelation(club, command.relation)

      val savedSource = ClubTable.save(context.connection, sourceClub)
      if command.relation.relation == ClubRelationKind.Neutral then
        ClubTable.save(context.connection, ClubFunctions.removeRelation(targetClub, command.clubId))
      else
        ClubTable.save(
          context.connection,
          ClubFunctions.upsertRelation(
            targetClub,
            command.relation.copy(targetClubId = command.clubId)
          )
        )
      savedSource
    }

  private def updateRelationAudit(command: UpdateClubRelationCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = "ClubRelationUpdated",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map(
          "targetClubId" -> command.relation.targetClubId.value,
          "relation" -> ClubRelationKind.toString(command.relation.relation)
        ),
        note = command.relation.note
      )
    )

  private final case class UpdateClubRelationCommand(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      relation: ClubRelation,
      occurredAt: Instant
  )
