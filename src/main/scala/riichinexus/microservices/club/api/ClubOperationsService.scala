package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class ClubOperationsService(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def adjustTreasury(
      clubId: ClubId,
      delta: Long,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubOperations,
          delegatedPrivileges = Set(ClubPrivilege.ManageBank)
        )

        val updatedClub = clubRepository.save(club.adjustTreasury(delta))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubTreasuryAdjusted",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "delta" -> delta.toString,
              "treasuryBalance" -> updatedClub.treasuryBalance.toString
            ),
            note = note
          )
        )
        updatedClub
      }
    }

  def adjustPointPool(
      clubId: ClubId,
      delta: Int,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubOperations,
          delegatedPrivileges = Set(ClubPrivilege.ManageBank)
        )

        val updatedClub = clubRepository.save(club.adjustPointPool(delta))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubPointPoolAdjusted",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "delta" -> delta.toString,
              "pointPool" -> updatedClub.pointPool.toString
            ),
            note = note
          )
        )
        updatedClub
      }
    }

  def updateRankTree(
      clubId: ClubId,
      rankTree: Vector[ClubRankNode],
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedClub = clubRepository.save(club.updateRankTree(rankTree))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubRankTreeUpdated",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map("rankCount" -> updatedClub.rankTree.size.toString),
            note = note
          )
        )
        updatedClub
      }
    }

  def adjustMemberContribution(
      clubId: ClubId,
      playerId: PlayerId,
      delta: Int,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireActivePlayer(player, s"Player ${playerId.value} cannot receive club contribution updates")
        ClubPolicySupport.requireClubMember(club, playerId, "adjust contribution")
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedBy = actor.playerId.getOrElse(club.creator)
        val nextContribution = club.contributionOf(playerId) + delta
        require(nextContribution >= 0, s"Club member contribution for ${playerId.value} cannot be negative")

        val updatedClub = clubRepository.save(
          club.updateMemberContribution(
            ClubMemberContribution(
              playerId = playerId,
              amount = nextContribution,
              updatedAt = occurredAt,
              updatedBy = updatedBy,
              note = note
            )
          )
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubMemberContributionAdjusted",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "playerId" -> playerId.value,
              "delta" -> delta.toString,
              "contribution" -> nextContribution.toString,
              "rankCode" -> updatedClub.rankFor(playerId).map(_.code).getOrElse("unknown")
            ),
            note = note
          )
        )
        updatedClub
    }

  def awardHonor(
      clubId: ClubId,
      honor: ClubHonor,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedClub = clubRepository.save(club.addHonor(honor))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubHonorAwarded",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map("title" -> honor.title),
            note = honor.note
          )
        )
        updatedClub
      }
    }

  def revokeHonor(
      clubId: ClubId,
      title: String,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val normalizedTitle = title.trim.toLowerCase
        if !club.honors.exists(_.title.trim.toLowerCase == normalizedTitle) then
          throw NoSuchElementException(s"Club ${clubId.value} does not have honor '$title'")

        val updatedClub = clubRepository.save(club.removeHonor(title))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubHonorRevoked",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map("title" -> title),
            note = note
          )
        )
        updatedClub
      }
    }

  def updateRecruitmentPolicy(
      clubId: ClubId,
      policy: ClubRecruitmentPolicy,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val updatedClub = clubRepository.save(club.updateRecruitmentPolicy(policy))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubRecruitmentPolicyUpdated",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "applicationsOpen" -> policy.applicationsOpen.toString,
              "requirementsText" -> policy.requirementsText.getOrElse("none"),
              "expectedReviewSlaHours" -> policy.expectedReviewSlaHours.map(_.toString).getOrElse("none")
            ),
            note = note
          )
        )
        updatedClub
      }
    }

  def updateRelation(
      clubId: ClubId,
      relation: ClubRelation,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.SetClubTitle,
          clubId = Some(clubId)
        )

        if relation.targetClubId == clubId then
          throw IllegalArgumentException("A club cannot define a relation to itself")

        val targetClub = clubRepository
          .findById(relation.targetClubId)
          .map { club =>
            ClubPolicySupport.ensureClubActive(club)
            club
          }
          .getOrElse(
            throw NoSuchElementException(s"Club ${relation.targetClubId.value} was not found")
          )

        val updatedSourceClub =
          if relation.relation == ClubRelationKind.Neutral then
            clubRepository.save(club.removeRelation(relation.targetClubId))
          else clubRepository.save(club.upsertRelation(relation))

        if relation.relation == ClubRelationKind.Neutral then
          clubRepository.save(targetClub.removeRelation(clubId))
        else
          clubRepository.save(
            targetClub.upsertRelation(
              relation.copy(targetClubId = clubId)
            )
          )

        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubRelationUpdated",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "targetClubId" -> relation.targetClubId.value,
              "relation" -> relation.relation.toString
            ),
            note = relation.note
          )
        )
        updatedSourceClub
      }
    }
