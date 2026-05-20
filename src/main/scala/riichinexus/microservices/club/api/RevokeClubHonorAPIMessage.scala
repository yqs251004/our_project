package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class RevokeClubHonorAPIMessage(
    clubId: String,
    operatorId: String,
    title: String,
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

          val normalizedTitle = title.trim.toLowerCase
          if !club.honors.exists(_.title.trim.toLowerCase == normalizedTitle) then
            throw NoSuchElementException(s"Club ${parsedClubId.value} does not have honor '$title'")

          val updatedClub = module.clubRepository.save(club.removeHonor(title))
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "club",
              aggregateId = parsedClubId.value,
              eventType = "ClubHonorRevoked",
              occurredAt = occurredAt,
              actorId = actor.playerId,
              details = Map("title" -> title),
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
