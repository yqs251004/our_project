package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateClubRelationAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: String,
    note: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val parsedRelation = ClubRelation(
        targetClubId = ClubId(targetClubId),
        relation = ClubRelationKind.valueOf(relation),
        updatedAt = Instant.now(),
        note = note
      )
      val actor = context.support.principal(PlayerId(operatorId))
      val occurredAt = Instant.now()

      module.transactionManager.inTransaction {
        module.clubRepository.findById(parsedClubId).map { club =>
          ensureClubActive(club)
          module.authorizationService.requirePermission(
            actor,
            Permission.SetClubTitle,
            clubId = Some(parsedClubId)
          )

          if parsedRelation.targetClubId == parsedClubId then
            throw IllegalArgumentException("A club cannot define a relation to itself")

          val targetClub = module.clubRepository
            .findById(parsedRelation.targetClubId)
            .map { club =>
              ensureClubActive(club)
              club
            }
            .getOrElse(
              throw NoSuchElementException(s"Club ${parsedRelation.targetClubId.value} was not found")
            )

          val updatedSourceClub =
            if parsedRelation.relation == ClubRelationKind.Neutral then
              module.clubRepository.save(club.removeRelation(parsedRelation.targetClubId))
            else module.clubRepository.save(club.upsertRelation(parsedRelation))

          if parsedRelation.relation == ClubRelationKind.Neutral then
            module.clubRepository.save(targetClub.removeRelation(parsedClubId))
          else
            module.clubRepository.save(
              targetClub.upsertRelation(
                parsedRelation.copy(targetClubId = parsedClubId)
              )
            )

          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "club",
              aggregateId = parsedClubId.value,
              eventType = "ClubRelationUpdated",
              occurredAt = occurredAt,
              actorId = actor.playerId,
              details = Map(
                "targetClubId" -> parsedRelation.targetClubId.value,
                "relation" -> parsedRelation.relation.toString
              ),
              note = parsedRelation.note
            )
          )
          updatedSourceClub
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
