package riichinexus.microservices.club.api

import riichinexus.domain.model.*
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.shared.api.requests.OperatorRequest

object ClubManagementApi:

  def createClub(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      request: CreateClubRequest
  ): Club =
    service.createClub(
      name = request.name,
      creatorId = request.creator,
      actor = principalOf(request.creator)
    )

  def addMember(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: AddClubMemberRequest
  ): Option[Club] =
    service.addMember(
      clubId,
      request.player,
      request.operator.map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def removeMember(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      playerId: PlayerId,
      request: OperatorRequest
  ): Option[Club] =
    service.removeMember(
      clubId,
      playerId,
      request.operator.map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def assignAdmin(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: AssignClubAdminRequest
  ): Option[Club] =
    service.assignAdmin(
      clubId = clubId,
      playerId = request.player,
      actor = principalOf(request.operator)
    )

  def revokeAdmin(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      playerId: PlayerId,
      request: OperatorRequest
  ): Option[Club] =
    service.revokeAdmin(
      clubId = clubId,
      playerId = playerId,
      actor = request.operator.map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def setInternalTitle(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: AssignClubTitleRequest
  ): Option[Club] =
    service.setInternalTitle(
      clubId = clubId,
      playerId = request.player,
      title = request.title,
      actor = principalOf(request.operator),
      note = request.note
    )

  def clearInternalTitle(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      playerId: PlayerId,
      request: ClearClubTitleRequest
  ): Option[Club] =
    service.clearInternalTitle(
      clubId = clubId,
      playerId = playerId,
      actor = principalOf(request.operator),
      note = request.note
    )

  def adjustTreasury(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: AdjustClubTreasuryRequest
  ): Option[Club] =
    service.adjustTreasury(
      clubId = clubId,
      delta = request.delta,
      actor = principalOf(request.operator),
      note = request.note
    )

  def adjustPointPool(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: AdjustClubPointPoolRequest
  ): Option[Club] =
    service.adjustPointPool(
      clubId = clubId,
      delta = request.delta,
      actor = principalOf(request.operator),
      note = request.note
    )

  def adjustMemberContribution(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: AdjustClubMemberContributionRequest
  ): Option[Club] =
    service.adjustMemberContribution(
      clubId = clubId,
      playerId = request.player,
      delta = request.delta,
      actor = principalOf(request.operator),
      note = request.note
    )

  def updateRankTree(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: UpdateClubRankTreeRequest
  ): Option[Club] =
    service.updateRankTree(
      clubId = clubId,
      rankTree = request.nodes,
      actor = principalOf(request.operator),
      note = request.note
    )

  def awardHonor(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: AwardClubHonorRequest
  ): Option[Club] =
    service.awardHonor(
      clubId = clubId,
      honor = request.honor,
      actor = principalOf(request.operator)
    )

  def revokeHonor(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: RevokeClubHonorRequest
  ): Option[Club] =
    service.revokeHonor(
      clubId = clubId,
      title = request.title,
      actor = principalOf(request.operator),
      note = request.note
    )

  def updateRecruitmentPolicy(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: UpdateClubRecruitmentPolicyRequest
  ): Option[Club] =
    service.updateRecruitmentPolicy(
      clubId = clubId,
      policy = request.policy,
      actor = principalOf(request.operator),
      note = request.note
    )

  def updateRelation(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      request: UpdateClubRelationRequest
  ): Option[Club] =
    service.updateRelation(
      clubId = clubId,
      relation = request.toRelation(),
      actor = principalOf(request.operator)
    )
