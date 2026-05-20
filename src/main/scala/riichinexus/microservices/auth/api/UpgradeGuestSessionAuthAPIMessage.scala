package riichinexus.microservices.auth.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.ports.ClubRepository
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import upickle.default.*

final case class UpgradeGuestSessionAuthAPIMessage(
    sessionId: String,
    playerId: String
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestAccessSession] =
    IO {
      val module = context.support.authModule
      val guestSessionId = GuestSessionId(sessionId)
      val targetPlayerId = PlayerId(playerId)
      val upgradedAt = Instant.now()

      module.transactionManager.inTransaction {
        module.guestSessionRepository.findById(guestSessionId).map { session =>
          val player = module.playerRepository
            .findById(targetPlayerId)
            .getOrElse(throw NoSuchElementException(s"Player ${targetPlayerId.value} was not found"))
          require(
            player.status == PlayerStatus.Active,
            s"Player ${targetPlayerId.value} must be active before linking a guest session"
          )

          val updated = module.guestSessionRepository.save(session.upgrade(targetPlayerId, upgradedAt))
          reconcileGuestApplications(module.clubRepository, guestSessionId, player)
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "guest-session",
              aggregateId = guestSessionId.value,
              eventType = "GuestSessionUpgraded",
              occurredAt = upgradedAt,
              actorId = Some(targetPlayerId),
              details = Map("playerId" -> targetPlayerId.value)
            )
          )
          updated
        }.getOrElse(throw NoSuchElementException(s"Guest session $sessionId was not found"))
      }
    }

  private def reconcileGuestApplications(
      clubRepository: ClubRepository,
      sessionId: GuestSessionId,
      player: Player
  ): Unit =
    val guestApplicantId = s"guest:${sessionId.value}"
    clubRepository.findAll().foreach { club =>
      val updatedApplications = club.membershipApplications.map { application =>
        if application.isPending && application.applicantUserId.contains(guestApplicantId) then
          application.bindRegisteredApplicant(player.userId, player.nickname)
        else application
      }

      if updatedApplications != club.membershipApplications then
        clubRepository.save(club.copy(membershipApplications = updatedApplications))
    }
