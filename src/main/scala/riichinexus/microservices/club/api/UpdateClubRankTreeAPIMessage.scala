package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.ClubRankNodeRequest
import upickle.default.*

final case class UpdateClubRankTreeAPIMessage(
    clubId: String,
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val actor = context.support.principal(PlayerId(operatorId))
      val occurredAt = java.time.Instant.now()

      module.transactionManager.inTransaction {
        module.clubRepository.findById(parsedClubId).map { club =>
          ensureClubActive(club)
          module.authorizationService.requirePermission(
            actor,
            Permission.ManageClubOperations,
            clubId = Some(parsedClubId)
          )

          val updatedClub = module.clubRepository.save(club.updateRankTree(ranks.map(_.toNode)))
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "club",
              aggregateId = parsedClubId.value,
              eventType = "ClubRankTreeUpdated",
              occurredAt = occurredAt,
              actorId = actor.playerId,
              details = Map("rankCount" -> updatedClub.rankTree.size.toString),
              note = note
            )
          )
          updatedClub
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
