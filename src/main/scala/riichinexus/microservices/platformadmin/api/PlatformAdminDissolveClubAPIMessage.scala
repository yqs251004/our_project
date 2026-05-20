package riichinexus.microservices.platformadmin.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.event.ClubDissolved
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminResponses.given
import upickle.default.*

final case class PlatformAdminDissolveClubAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminClubResponse] =
    IO {
      val module = context.support.platformAdminModule
      val request = DissolveClubRequest(operatorId = operatorId)
      val actor = context.support.principal(request.operatorId)
      val dissolvedAt = Instant.now()

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.DissolveClub)

        module.tables.findClub(clubId).map { club =>
          if club.dissolvedAt.nonEmpty then
            throw IllegalArgumentException(s"Club ${clubId.value} has already been dissolved")

          club.members.foreach { memberId =>
            module.playerRepository.findById(memberId).foreach { player =>
              module.playerRepository.save(
                player
                  .leaveClub(clubId)
                  .revokeClubAdmin(clubId)
              )
            }
          }

          module.clubRepository.findActive()
            .filterNot(_.id == clubId)
            .filter(_.relations.exists(_.targetClubId == clubId))
            .foreach { relatedClub =>
              module.clubRepository.save(relatedClub.removeRelation(clubId))
            }

          val dissolved = module.clubRepository.save(
            club.dissolve(actor.playerId.getOrElse(club.creator), dissolvedAt)
          )
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "club",
              aggregateId = clubId.value,
              eventType = "ClubDissolved",
              occurredAt = dissolvedAt,
              actorId = actor.playerId,
              details = Map("memberCount" -> club.members.size.toString),
              note = Some(s"Club ${clubId.value} dissolved")
            )
          )
          module.eventBus.publish(ClubDissolved(clubId, dissolvedAt))
          dissolved
        }
      }
        .map(PlatformAdminClubView.fromDomain)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    }
