package riichinexus.microservices.platformadmin.api

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class SuperAdminService(
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def grantSuperAdmin(
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system,
      grantedAt: Instant = Instant.now()
  ): Option[Player] =
    transactionManager.inTransaction {
      if !actor.isSuperAdmin then
        throw AuthorizationFailure("Only an existing super admin can grant super admin access")

      playerRepository.findById(playerId).map { player =>
        val updatedPlayer =
          playerRepository.save(player.grantRole(RoleGrant.superAdmin(grantedAt, actor.playerId)))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "player",
            aggregateId = playerId.value,
            eventType = "SuperAdminGranted",
            occurredAt = grantedAt,
            actorId = actor.playerId,
            details = Map("playerId" -> playerId.value),
            note = Some(s"Granted super admin access to ${playerId.value}")
          )
        )
        updatedPlayer
      }
    }

  def banPlayer(
      playerId: PlayerId,
      reason: String,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Player] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.BanRegisteredPlayer)
      require(reason.trim.nonEmpty, "Ban reason cannot be empty")

      playerRepository.findById(playerId).map { player =>
        val banned = playerRepository.save(player.ban(reason))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "player",
            aggregateId = playerId.value,
            eventType = "PlayerBanned",
            occurredAt = at,
            actorId = actor.playerId,
            details = Map("reason" -> reason),
            note = Some(reason)
          )
        )
        eventBus.publish(PlayerBanned(playerId, reason, at))
        banned
      }
    }

  def dissolveClub(
      clubId: ClubId,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.DissolveClub)

      clubRepository.findById(clubId).map { club =>
        if club.dissolvedAt.nonEmpty then
          throw IllegalArgumentException(s"Club ${clubId.value} has already been dissolved")

        club.members.foreach { memberId =>
          playerRepository.findById(memberId).foreach { player =>
            playerRepository.save(
              player
                .leaveClub(clubId)
                .revokeClubAdmin(clubId)
            )
          }
        }

        clubRepository.findActive()
          .filterNot(_.id == clubId)
          .filter(_.relations.exists(_.targetClubId == clubId))
          .foreach { relatedClub =>
            clubRepository.save(relatedClub.removeRelation(clubId))
          }

        val dissolved = clubRepository.save(
          club.dissolve(actor.playerId.getOrElse(club.creator), at)
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubDissolved",
            occurredAt = at,
            actorId = actor.playerId,
            details = Map("memberCount" -> club.members.size.toString),
            note = Some(s"Club ${clubId.value} dissolved")
          )
        )
        eventBus.publish(ClubDissolved(clubId, at))
        dissolved
      }
    }
