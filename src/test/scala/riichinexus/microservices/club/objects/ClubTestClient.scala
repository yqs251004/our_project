package riichinexus.microservices.club.objects

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

import riichinexus.bootstrap.ClubModuleContext

final class ClubTestClient(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dashboardRepository: DashboardRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val management = ClubTestManagement(
    clubRepository = clubRepository,
    playerRepository = playerRepository,
    globalDictionaryRepository = globalDictionaryRepository,
    dashboardRepository = dashboardRepository,
    auditEventRepository = auditEventRepository,
    transactionManager = transactionManager,
    authorizationService = authorizationService
  )

  private val applications = ClubTestMembershipApplications(
    clubRepository = clubRepository,
    playerRepository = playerRepository,
    globalDictionaryRepository = globalDictionaryRepository,
    dashboardRepository = dashboardRepository,
    transactionManager = transactionManager,
    authorizationService = authorizationService
  )

  def createClub(
      name: String,
      creatorId: PlayerId,
      createdAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.system
  ): Club =
    management.createClub(name, creatorId, createdAt, actor)

  def addMember(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Club] =
    management.addMember(clubId, playerId, actor)

  def removeMember(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Club] =
    management.removeMember(clubId, playerId, actor)

  def assignAdmin(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant = Instant.now()
  ): Option[Club] =
    management.assignAdmin(clubId, playerId, actor, grantedAt)

  def revokeAdmin(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Club] =
    management.revokeAdmin(clubId, playerId, actor)

  def setInternalTitle(
      clubId: ClubId,
      playerId: PlayerId,
      title: String,
      actor: AccessPrincipal,
      assignedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    management.setInternalTitle(clubId, playerId, title, actor, assignedAt, note)

  def clearInternalTitle(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      clearedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    management.clearInternalTitle(clubId, playerId, actor, clearedAt, note)

  def adjustTreasury(
      clubId: ClubId,
      delta: Long,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    management.adjustTreasury(clubId, delta, actor, occurredAt, note)

  def adjustPointPool(
      clubId: ClubId,
      delta: Int,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    management.adjustPointPool(clubId, delta, actor, occurredAt, note)

  def adjustMemberContribution(
      clubId: ClubId,
      playerId: PlayerId,
      delta: Int,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    management.adjustMemberContribution(clubId, playerId, delta, actor, occurredAt, note)

  def updateRankTree(
      clubId: ClubId,
      rankTree: Vector[ClubRankNode],
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    management.updateRankTree(clubId, rankTree, actor, occurredAt, note)

  def awardHonor(
      clubId: ClubId,
      honor: ClubHonor,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now()
  ): Option[Club] =
    management.awardHonor(clubId, honor, actor, occurredAt)

  def revokeHonor(
      clubId: ClubId,
      title: String,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    management.revokeHonor(clubId, title, actor, occurredAt, note)

  def updateRecruitmentPolicy(
      clubId: ClubId,
      policy: ClubRecruitmentPolicy,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    management.updateRecruitmentPolicy(clubId, policy, actor, occurredAt, note)

  def updateRelation(
      clubId: ClubId,
      relation: ClubRelation,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now()
  ): Option[Club] =
    management.updateRelation(clubId, relation, actor, occurredAt)

  def applyForMembership(
      clubId: ClubId,
      applicantUserId: Option[String],
      displayName: String,
      message: Option[String] = None,
      submittedAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.guest()
  ): Option[ClubMembershipApplication] =
    applications.applyForMembership(clubId, applicantUserId, displayName, message, submittedAt, actor)

  def withdrawMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      actor: AccessPrincipal,
      withdrawnAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[ClubMembershipApplication] =
    applications.withdrawMembershipApplication(clubId, applicationId, actor, withdrawnAt, note)

  def approveMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      approvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    applications.approveMembershipApplication(clubId, applicationId, playerId, actor, approvedAt, note)

  def rejectMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      actor: AccessPrincipal,
      rejectedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    applications.rejectMembershipApplication(clubId, applicationId, actor, rejectedAt, note)

object ClubTestClient:
  def from(module: ClubModuleContext): ClubTestClient =
    ClubTestClient(
      clubRepository = module.clubRepository,
      playerRepository = module.playerRepository,
      globalDictionaryRepository = module.globalDictionaryRepository,
      dashboardRepository = module.dashboardRepository,
      auditEventRepository = module.auditEventRepository,
      transactionManager = module.transactionManager,
      authorizationService = module.authorizationService
    )
