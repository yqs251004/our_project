package riichinexus.microservices.club.api

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class ClubManagementService(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dashboardRepository: DashboardRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val roster = ClubRosterService(
    clubRepository = clubRepository,
    playerRepository = playerRepository,
    globalDictionaryRepository = globalDictionaryRepository,
    dashboardRepository = dashboardRepository,
    auditEventRepository = auditEventRepository,
    transactionManager = transactionManager,
    authorizationService = authorizationService
  )

  private val operations = ClubOperationsService(
    clubRepository = clubRepository,
    playerRepository = playerRepository,
    auditEventRepository = auditEventRepository,
    transactionManager = transactionManager,
    authorizationService = authorizationService
  )

  def createClub(
      name: String,
      creatorId: PlayerId,
      createdAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.system
  ): Club =
    roster.createClub(name, creatorId, createdAt, actor)

  def addMember(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Club] =
    roster.addMember(clubId, playerId, actor)

  def removeMember(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Club] =
    roster.removeMember(clubId, playerId, actor)

  def assignAdmin(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant = Instant.now()
  ): Option[Club] =
    roster.assignAdmin(clubId, playerId, actor, grantedAt)

  def revokeAdmin(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Club] =
    roster.revokeAdmin(clubId, playerId, actor)

  def setInternalTitle(
      clubId: ClubId,
      playerId: PlayerId,
      title: String,
      actor: AccessPrincipal,
      assignedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    roster.setInternalTitle(clubId, playerId, title, actor, assignedAt, note)

  def clearInternalTitle(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      clearedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    roster.clearInternalTitle(clubId, playerId, actor, clearedAt, note)

  def adjustTreasury(
      clubId: ClubId,
      delta: Long,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    operations.adjustTreasury(clubId, delta, actor, occurredAt, note)

  def adjustPointPool(
      clubId: ClubId,
      delta: Int,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    operations.adjustPointPool(clubId, delta, actor, occurredAt, note)

  def updateRankTree(
      clubId: ClubId,
      rankTree: Vector[ClubRankNode],
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    operations.updateRankTree(clubId, rankTree, actor, occurredAt, note)

  def adjustMemberContribution(
      clubId: ClubId,
      playerId: PlayerId,
      delta: Int,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    operations.adjustMemberContribution(clubId, playerId, delta, actor, occurredAt, note)

  def awardHonor(
      clubId: ClubId,
      honor: ClubHonor,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now()
  ): Option[Club] =
    operations.awardHonor(clubId, honor, actor, occurredAt)

  def revokeHonor(
      clubId: ClubId,
      title: String,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    operations.revokeHonor(clubId, title, actor, occurredAt, note)

  def updateRecruitmentPolicy(
      clubId: ClubId,
      policy: ClubRecruitmentPolicy,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    operations.updateRecruitmentPolicy(clubId, policy, actor, occurredAt, note)

  def updateRelation(
      clubId: ClubId,
      relation: ClubRelation,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now()
  ): Option[Club] =
    operations.updateRelation(clubId, relation, actor, occurredAt)
