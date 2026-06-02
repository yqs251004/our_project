package riichinexus.microservices.club.objects
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.*
import riichinexus.microservices.club.domain.Club

final class ClubTestOperations(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationPolicy = AuthorizationPolicy.permitAll
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
        ensureClubActive(club)
        requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubOperations,
          delegatedPrivileges = Set(ClubPrivilegeCode.ManageBank)
        )

        val updatedClub = clubRepository.save(ClubFunctions.adjustTreasury(club, delta))
        auditEventRepository.save(
          AuditEvent(
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
        ensureClubActive(club)
        requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubOperations,
          delegatedPrivileges = Set(ClubPrivilegeCode.ManageBank)
        )

        val updatedClub = clubRepository.save(ClubFunctions.adjustPointPool(club, delta))
        auditEventRepository.save(
          AuditEvent(
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
        ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedClub = clubRepository.save(ClubFunctions.updateRankTree(club, rankTree))
        auditEventRepository.save(
          AuditEvent(
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
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot receive club contribution updates")
        requireClubMember(club, playerId, "adjust contribution")
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedBy = actor.playerId.getOrElse(club.creator)
        val nextContribution = ClubFunctions.contributionOf(club, playerId) + delta
        require(nextContribution >= 0, s"Club member contribution for ${playerId.value} cannot be negative")

        val updatedClub = clubRepository.save(
          ClubFunctions.updateMemberContribution(club,
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
          AuditEvent(
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
              "rankCode" -> ClubFunctions.rankFor(updatedClub, playerId).map(_.code).getOrElse("unknown")
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
        ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedClub = clubRepository.save(ClubFunctions.addHonor(club, honor))
        auditEventRepository.save(
          AuditEvent(
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
        ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val normalizedTitle = title.trim.toLowerCase
        if !club.honors.exists(_.title.trim.toLowerCase == normalizedTitle) then
          throw NoSuchElementException(s"Club ${clubId.value} does not have honor '$title'")

        val updatedClub = clubRepository.save(ClubFunctions.removeHonor(club, title))
        auditEventRepository.save(
          AuditEvent(
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
        ensureClubActive(club)
        requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
        )

        val updatedClub = clubRepository.save(ClubFunctions.updateRecruitmentPolicy(club, policy))
        auditEventRepository.save(
          AuditEvent(
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
        ensureClubActive(club)
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
            ensureClubActive(club)
            club
          }
          .getOrElse(
            throw NoSuchElementException(s"Club ${relation.targetClubId.value} was not found")
          )

        val updatedSourceClub =
          if relation.relation == ClubRelationKind.Neutral then
            clubRepository.save(ClubFunctions.removeRelation(club, relation.targetClubId))
          else clubRepository.save(ClubFunctions.upsertRelation(club, relation))

        if relation.relation == ClubRelationKind.Neutral then
          clubRepository.save(ClubFunctions.removeRelation(targetClub, clubId))
        else
          clubRepository.save(
            ClubFunctions.upsertRelation(
              targetClub,
              relation.copy(targetClubId = clubId)
            )
          )

        auditEventRepository.save(
          AuditEvent(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubRelationUpdated",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "targetClubId" -> relation.targetClubId.value,
              "relation" -> ClubRelationKind.toString(relation.relation)
            ),
            note = relation.note
          )
        )
        updatedSourceClub
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )

  private def requireClubCapability(
      authorizationService: AuthorizationPolicy,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
      delegatedPrivileges.exists(privilege => ClubFunctions.hasPrivilege(club, playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )
