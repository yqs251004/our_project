package riichinexus.microservices.club.api.messages

import org.http4s.Status
import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, EmptyApiMessageInput, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.{ClubApplicationApi, ClubApplicationService, ClubManagementApi, ClubQueryApi, ClubTournamentApi, ClubViewAssembler}
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.club.api.responses.ClubTournamentResponses.given
import riichinexus.microservices.player.api.responses.*
import riichinexus.microservices.player.api.responses.PlayerResponses.given
import riichinexus.microservices.club.objects.*
import riichinexus.microservices.club.tables.ClubTables
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.shared.api.requests.OperatorRequest.given
import riichinexus.microservices.shared.api.responses.PagedResponse
import riichinexus.microservices.tournament.api.{TournamentApplicationService, TournamentViewAssembler}
import riichinexus.microservices.tournament.api.responses.TournamentOperationResponses.given
import upickle.default.*

object ClubApiMessages:
  final case class ClubListClubsApiMessageInput(
      activeOnly: Option[Boolean] = None,
      joinableOnly: Option[Boolean] = None,
      memberId: Option[String] = None,
      adminId: Option[String] = None,
      name: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object ClubListClubsApiMessageInput:
    given ReadWriter[ClubListClubsApiMessageInput] = macroRW

  final case class ClubGetClubApiMessageInput(clubId: String) derives CanEqual

  object ClubGetClubApiMessageInput:
    given ReadWriter[ClubGetClubApiMessageInput] = macroRW

  final case class ClubListTournamentsApiMessageInput(
      clubId: String,
      scope: Option[String] = None,
      viewer: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object ClubListTournamentsApiMessageInput:
    given ReadWriter[ClubListTournamentsApiMessageInput] = macroRW

  final case class ClubListMembersApiMessageInput(
      clubId: String,
      status: Option[String] = None,
      nickname: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object ClubListMembersApiMessageInput:
    given ReadWriter[ClubListMembersApiMessageInput] = macroRW

  final case class ClubRemoveMemberApiMessageInput(
      clubId: String,
      playerId: String,
      operatorId: Option[String] = None
  ) derives CanEqual

  object ClubRemoveMemberApiMessageInput:
    given ReadWriter[ClubRemoveMemberApiMessageInput] = macroRW

  final case class ClubAssignAdminApiMessageInput(
      clubId: String,
      playerId: String,
      operatorId: String
  ) derives CanEqual

  object ClubAssignAdminApiMessageInput:
    given ReadWriter[ClubAssignAdminApiMessageInput] = macroRW

  final case class ClubListApplicationsApiMessageInput(
      clubId: String,
      operatorId: Option[String] = None,
      guestSessionId: Option[String] = None,
      status: Option[String] = None,
      applicantUserId: Option[String] = None,
      displayName: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object ClubListApplicationsApiMessageInput:
    given ReadWriter[ClubListApplicationsApiMessageInput] = macroRW

  final case class ClubGetCurrentApplicationApiMessageInput(
      clubId: String,
      operatorId: Option[String] = None,
      guestSessionId: Option[String] = None
  ) derives CanEqual

  object ClubGetCurrentApplicationApiMessageInput:
    given ReadWriter[ClubGetCurrentApplicationApiMessageInput] = macroRW

  final case class ClubGetApplicationApiMessageInput(
      clubId: String,
      membershipId: String,
      operatorId: Option[String] = None,
      guestSessionId: Option[String] = None
  ) derives CanEqual

  object ClubGetApplicationApiMessageInput:
    given ReadWriter[ClubGetApplicationApiMessageInput] = macroRW

  final case class ClubSubmitApplicationApiMessageInput(
      clubId: String,
      applicantUserId: Option[String],
      displayName: String,
      message: Option[String] = None,
      guestSessionId: Option[String] = None,
      operatorId: Option[String] = None
  ) derives CanEqual

  object ClubSubmitApplicationApiMessageInput:
    given ReadWriter[ClubSubmitApplicationApiMessageInput] = macroRW

  final case class ClubWithdrawApplicationApiMessageInput(
      clubId: String,
      membershipId: String,
      guestSessionId: Option[String] = None,
      operatorId: Option[String] = None,
      note: Option[String] = None
  ) derives CanEqual

  object ClubWithdrawApplicationApiMessageInput:
    given ReadWriter[ClubWithdrawApplicationApiMessageInput] = macroRW

  final case class ClubReviewApplicationApiMessageInput(
      clubId: String,
      membershipId: String,
      operatorId: String,
      decision: String,
      playerId: Option[String] = None,
      note: Option[String] = None
  ) derives CanEqual

  object ClubReviewApplicationApiMessageInput:
    given ReadWriter[ClubReviewApplicationApiMessageInput] = macroRW


  final case class ClubListMemberPrivilegesApiMessageInput(
      clubId: String,
      playerId: Option[String] = None,
      privilege: Option[String] = None,
      rankCode: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object ClubListMemberPrivilegesApiMessageInput:
    given ReadWriter[ClubListMemberPrivilegesApiMessageInput] = macroRW

  final case class ClubGetMemberPrivilegeApiMessageInput(clubId: String, playerId: String) derives CanEqual
  object ClubGetMemberPrivilegeApiMessageInput:
    given ReadWriter[ClubGetMemberPrivilegeApiMessageInput] = macroRW

  final case class ClubAddMemberApiMessageInput(clubId: String, request: AddClubMemberRequest) derives CanEqual
  object ClubAddMemberApiMessageInput:
    given ReadWriter[ClubAddMemberApiMessageInput] = macroRW

  final case class ClubApproveApplicationApiMessageInput(clubId: String, membershipId: String, request: ApproveClubApplicationRequest) derives CanEqual
  object ClubApproveApplicationApiMessageInput:
    given ReadWriter[ClubApproveApplicationApiMessageInput] = macroRW

  final case class ClubRejectApplicationApiMessageInput(clubId: String, membershipId: String, request: RejectClubApplicationRequest) derives CanEqual
  object ClubRejectApplicationApiMessageInput:
    given ReadWriter[ClubRejectApplicationApiMessageInput] = macroRW

  final case class ClubTournamentParticipationApiMessageInput(clubId: String, tournamentId: String, operatorId: Option[String] = None) derives CanEqual
  object ClubTournamentParticipationApiMessageInput:
    given ReadWriter[ClubTournamentParticipationApiMessageInput] = macroRW

  final case class ClubRevokeAdminApiMessageInput(clubId: String, playerId: String, operatorId: Option[String] = None) derives CanEqual
  object ClubRevokeAdminApiMessageInput:
    given ReadWriter[ClubRevokeAdminApiMessageInput] = macroRW

  final case class ClubAssignTitleApiMessageInput(clubId: String, request: AssignClubTitleRequest) derives CanEqual
  object ClubAssignTitleApiMessageInput:
    given ReadWriter[ClubAssignTitleApiMessageInput] = macroRW

  final case class ClubClearTitleApiMessageInput(clubId: String, playerId: String, request: ClearClubTitleRequest) derives CanEqual
  object ClubClearTitleApiMessageInput:
    given ReadWriter[ClubClearTitleApiMessageInput] = macroRW

  final case class ClubAdjustTreasuryApiMessageInput(clubId: String, request: AdjustClubTreasuryRequest) derives CanEqual
  object ClubAdjustTreasuryApiMessageInput:
    given ReadWriter[ClubAdjustTreasuryApiMessageInput] = macroRW

  final case class ClubAdjustPointPoolApiMessageInput(clubId: String, request: AdjustClubPointPoolRequest) derives CanEqual
  object ClubAdjustPointPoolApiMessageInput:
    given ReadWriter[ClubAdjustPointPoolApiMessageInput] = macroRW

  final case class ClubAdjustMemberContributionApiMessageInput(clubId: String, request: AdjustClubMemberContributionRequest) derives CanEqual
  object ClubAdjustMemberContributionApiMessageInput:
    given ReadWriter[ClubAdjustMemberContributionApiMessageInput] = macroRW

  final case class ClubUpdateRankTreeApiMessageInput(clubId: String, request: UpdateClubRankTreeRequest) derives CanEqual
  object ClubUpdateRankTreeApiMessageInput:
    given ReadWriter[ClubUpdateRankTreeApiMessageInput] = macroRW

  final case class ClubAwardHonorApiMessageInput(clubId: String, request: AwardClubHonorRequest) derives CanEqual
  object ClubAwardHonorApiMessageInput:
    given ReadWriter[ClubAwardHonorApiMessageInput] = macroRW

  final case class ClubRevokeHonorApiMessageInput(clubId: String, request: RevokeClubHonorRequest) derives CanEqual
  object ClubRevokeHonorApiMessageInput:
    given ReadWriter[ClubRevokeHonorApiMessageInput] = macroRW

  final case class ClubUpdateRecruitmentPolicyApiMessageInput(clubId: String, request: UpdateClubRecruitmentPolicyRequest) derives CanEqual
  object ClubUpdateRecruitmentPolicyApiMessageInput:
    given ReadWriter[ClubUpdateRecruitmentPolicyApiMessageInput] = macroRW

  final case class ClubUpdateRelationApiMessageInput(clubId: String, request: UpdateClubRelationRequest) derives CanEqual
  object ClubUpdateRelationApiMessageInput:
    given ReadWriter[ClubUpdateRelationApiMessageInput] = macroRW

  private final case class Dependencies(
      tables: ClubTables,
      service: ClubApplicationService,
      views: ClubViewAssembler,
      tournamentService: TournamentApplicationService,
      tournamentViews: TournamentViewAssembler
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.clubModule
    Dependencies(
      tables = module.tables,
      service = module.service,
      views = module.views,
      tournamentService = module.tournamentService,
      tournamentViews = module.tournamentViews
    )

  private def pagedJsonResponse[T: Writer](
      support: RouteSupport,
      items: Vector[T],
      limit: Option[Int],
      offset: Option[Int],
      appliedFilters: Map[String, String]
  ) =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    support.jsonResponse(
      Status.Ok,
      PagedResponse(
        items = page,
        total = items.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < items.size,
        appliedFilters = appliedFilters
      )
    )

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  val clubListClubsApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubListClubsApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubListClubsApiMessageInput](req).flatMap { request =>
          val query = ClubListQuery(
            activeOnly = request.activeOnly.contains(true),
            joinableOnly = request.joinableOnly.contains(true),
            memberId = request.memberId.filter(_.nonEmpty).map(PlayerId(_)),
            adminId = request.adminId.filter(_.nonEmpty).map(PlayerId(_)),
            name = request.name.filter(_.nonEmpty)
          )
          val clubs = ClubQueryApi.listClubs(deps.tables, query)
          pagedJsonResponse(
            support,
            clubs,
            request.limit,
            request.offset,
            filters(
              request.activeOnly.map(value => "activeOnly" -> value.toString),
              request.joinableOnly.map(value => "joinableOnly" -> value.toString),
              request.memberId.filter(_.nonEmpty).map("memberId" -> _),
              request.adminId.filter(_.nonEmpty).map("adminId" -> _),
              request.name.filter(_.nonEmpty).map("name" -> _)
            )
          )
        }
    )

  val clubGetClubApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubGetClubApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubGetClubApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubQueryApi.findClub(deps.tables, ClubId(request.clubId)))
        }
    )

  val clubCreateClubApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubCreateClubApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[CreateClubRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, ClubManagementApi.createClub(deps.service, support.principal, request))
        }
    )

  val clubListTournamentsApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubListTournamentsApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubListTournamentsApiMessageInput](req).flatMap { request =>
          val query = ClubTournamentParticipationQuery(
            scope = request.scope.filter(_.nonEmpty).getOrElse("recent"),
            viewer = request.viewer.filter(_.nonEmpty).map(PlayerId(_))
          )
          val items = ClubQueryApi.clubTournamentParticipations(
            deps.tables,
            deps.views,
            support.principal,
            ClubId(request.clubId),
            query
          )
          pagedJsonResponse(
            support,
            items,
            request.limit,
            request.offset,
            filters(request.scope.filter(_.nonEmpty).map("scope" -> _), request.viewer.filter(_.nonEmpty).map("viewer" -> _))
          )
        }
    )

  val clubListMembersApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubListMembersApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubListMembersApiMessageInput](req).flatMap { request =>
          val query = ClubMemberQuery(
            status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(PlayerStatus.valueOf)),
            nickname = request.nickname.filter(_.nonEmpty)
          )
          val members = ClubQueryApi.listMembers(deps.tables, support.containsIgnoreCase, ClubId(request.clubId), query).map(PlayerProfileView.fromDomain)
          pagedJsonResponse(
            support,
            members,
            request.limit,
            request.offset,
            filters(request.status.filter(_.nonEmpty).map("status" -> _), request.nickname.filter(_.nonEmpty).map("nickname" -> _))
          )
        }
    )

  val clubRemoveMemberApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubRemoveMemberApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubRemoveMemberApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.removeMember(
              deps.service,
              support.principal,
              ClubId(request.clubId),
              PlayerId(request.playerId),
              OperatorRequest(request.operatorId)
            )
          )
        }
    )

  val clubAssignAdminApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubAssignAdminApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubAssignAdminApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.assignAdmin(
              deps.service,
              support.principal,
              ClubId(request.clubId),
              AssignClubAdminRequest(playerId = request.playerId, operatorId = request.operatorId)
            )
          )
        }
    )

  val clubListApplicationsApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubListApplicationsApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubListApplicationsApiMessageInput](req).flatMap { request =>
          val query = ClubApplicationQuery(
            operatorId = request.operatorId.filter(_.nonEmpty).map(PlayerId(_)),
            guestSessionId = request.guestSessionId.filter(_.nonEmpty),
            status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(ClubMembershipApplicationStatus.valueOf)),
            applicantUserId = request.applicantUserId.filter(_.nonEmpty),
            displayName = request.displayName.filter(_.nonEmpty)
          )
          val applications = ClubApplicationApi.listApplications(
            deps.tables,
            deps.views,
            support.requestActor,
            support.containsIgnoreCase,
            ClubId(request.clubId),
            query
          )
          pagedJsonResponse(
            support,
            applications,
            request.limit,
            request.offset,
            filters(
              request.operatorId.filter(_.nonEmpty).map("operatorId" -> _),
              request.guestSessionId.filter(_.nonEmpty).map("guestSessionId" -> _),
              request.status.filter(_.nonEmpty).map("status" -> _),
              request.applicantUserId.filter(_.nonEmpty).map("applicantUserId" -> _),
              request.displayName.filter(_.nonEmpty).map("displayName" -> _)
            )
          )
        }
    )

  val clubGetCurrentApplicationApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubGetCurrentApplicationApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubGetCurrentApplicationApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            ClubApplicationApi.currentApplication(
              deps.tables,
              deps.views,
              support.requestActor,
              ClubId(request.clubId),
              request.guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)),
              request.operatorId.filter(_.nonEmpty).map(PlayerId(_))
            )
          )
        }
    )

  val clubGetApplicationApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubGetApplicationApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubGetApplicationApiMessageInput](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            ClubApplicationApi.applicationDetail(
              deps.tables,
              deps.views,
              support.requestActor,
              ClubId(request.clubId),
              MembershipApplicationId(request.membershipId),
              request.guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)),
              request.operatorId.filter(_.nonEmpty).map(PlayerId(_))
            )
          )
        }
    )

  val clubSubmitApplicationApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubSubmitApplicationApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubSubmitApplicationApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            ClubApplicationApi.applyForMembership(
              deps.tables,
              deps.service,
              support.requestActor,
              ClubId(request.clubId),
              ClubMembershipApplicationRequest(
                applicantUserId = request.applicantUserId,
                displayName = request.displayName,
                message = request.message,
                guestSessionId = request.guestSessionId,
                operatorId = request.operatorId
              )
            )
          )
        }
    )

  val clubWithdrawApplicationApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubWithdrawApplicationApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubWithdrawApplicationApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            ClubApplicationApi.withdrawMembershipApplication(
              deps.service,
              support.requestActor,
              ClubId(request.clubId),
              MembershipApplicationId(request.membershipId),
              WithdrawClubApplicationRequest(
                guestSessionId = request.guestSessionId,
                operatorId = request.operatorId,
                note = request.note
              )
            )
          )
        }
    )

  val clubReviewApplicationApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubReviewApplicationApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubReviewApplicationApiMessageInput](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            ClubApplicationApi.reviewMembershipApplication(
              deps.tables,
              deps.service,
              deps.views,
              support.principal,
              ClubId(request.clubId),
              MembershipApplicationId(request.membershipId),
              ReviewClubApplicationRequest(
                operatorId = request.operatorId,
                decision = request.decision,
                playerId = request.playerId,
                note = request.note
              )
            )
          )
        }
    )


  val clubPrivilegeDefinitionsApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubPrivilegeDefinitionsApiMessage",
      handle = (support, req) =>
        support.readJsonBody[EmptyApiMessageInput](req).flatMap { _ =>
          support.jsonResponse(Status.Ok, ClubQueryApi.privilegeDefinitions)
        }
    )

  val clubListMemberPrivilegesApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubListMemberPrivilegesApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubListMemberPrivilegesApiMessageInput](req).flatMap { request =>
          val query = ClubPrivilegeSnapshotQuery(
            playerId = request.playerId.filter(_.nonEmpty).map(PlayerId(_)),
            privilege = request.privilege.filter(_.nonEmpty).map(ClubPrivilegeRegistry.requireSupported),
            rankCode = request.rankCode.filter(_.nonEmpty).map(_.trim.toLowerCase)
          )
          val snapshots = ClubQueryApi.listPrivilegeSnapshots(deps.tables, ClubId(request.clubId), query)
          pagedJsonResponse(
            support,
            snapshots,
            request.limit,
            request.offset,
            filters(request.playerId.filter(_.nonEmpty).map("playerId" -> _), request.privilege.filter(_.nonEmpty).map("privilege" -> _), request.rankCode.filter(_.nonEmpty).map("rankCode" -> _))
          )
        }
    )

  val clubGetMemberPrivilegeApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubGetMemberPrivilegeApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubGetMemberPrivilegeApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubQueryApi.privilegeSnapshot(deps.tables, ClubId(request.clubId), PlayerId(request.playerId)))
        }
    )

  val clubAddMemberApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubAddMemberApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubAddMemberApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.addMember(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubApproveApplicationApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubApproveApplicationApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubApproveApplicationApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubApplicationApi.approveMembershipApplication(deps.service, support.principal, ClubId(request.clubId), MembershipApplicationId(request.membershipId), request.request))
        }
    )

  val clubRejectApplicationApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubRejectApplicationApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubRejectApplicationApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubApplicationApi.rejectMembershipApplication(deps.service, support.principal, ClubId(request.clubId), MembershipApplicationId(request.membershipId), request.request))
        }
    )

  val clubAcceptTournamentApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubAcceptTournamentApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubTournamentParticipationApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubTournamentApi.acceptParticipation(deps.tournamentService, deps.tournamentViews, support.principal, ClubId(request.clubId), TournamentId(request.tournamentId), OperatorRequest(request.operatorId)))
        }
    )

  val clubDeclineTournamentApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubDeclineTournamentApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubTournamentParticipationApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubTournamentApi.declineParticipation(deps.tournamentService, deps.tournamentViews, support.principal, ClubId(request.clubId), TournamentId(request.tournamentId), OperatorRequest(request.operatorId)))
        }
    )

  val clubRevokeAdminApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubRevokeAdminApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubRevokeAdminApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.revokeAdmin(deps.service, support.principal, ClubId(request.clubId), PlayerId(request.playerId), OperatorRequest(request.operatorId)))
        }
    )

  val clubAssignTitleApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubAssignTitleApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubAssignTitleApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.setInternalTitle(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubClearTitleApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubClearTitleApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubClearTitleApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.clearInternalTitle(deps.service, support.principal, ClubId(request.clubId), PlayerId(request.playerId), request.request))
        }
    )

  val clubAdjustTreasuryApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubAdjustTreasuryApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubAdjustTreasuryApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.adjustTreasury(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubAdjustPointPoolApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubAdjustPointPoolApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubAdjustPointPoolApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.adjustPointPool(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubAdjustMemberContributionApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubAdjustMemberContributionApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubAdjustMemberContributionApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.adjustMemberContribution(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubUpdateRankTreeApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubUpdateRankTreeApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubUpdateRankTreeApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.updateRankTree(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubAwardHonorApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubAwardHonorApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubAwardHonorApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.awardHonor(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubRevokeHonorApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubRevokeHonorApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubRevokeHonorApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.revokeHonor(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubUpdateRecruitmentPolicyApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubUpdateRecruitmentPolicyApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubUpdateRecruitmentPolicyApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.updateRecruitmentPolicy(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val clubUpdateRelationApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "clubUpdateRelationApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ClubUpdateRelationApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.updateRelation(deps.service, support.principal, ClubId(request.clubId), request.request))
        }
    )

  val handlers: Vector[ApiMessageHandler] =
    Vector(
      clubPrivilegeDefinitionsApiMessage,
      clubListClubsApiMessage,
      clubGetClubApiMessage,
      clubCreateClubApiMessage,
      clubListTournamentsApiMessage,
      clubListMembersApiMessage,
      clubListMemberPrivilegesApiMessage,
      clubGetMemberPrivilegeApiMessage,
      clubAddMemberApiMessage,
      clubRemoveMemberApiMessage,
      clubAssignAdminApiMessage,
      clubRevokeAdminApiMessage,
      clubAssignTitleApiMessage,
      clubClearTitleApiMessage,
      clubAdjustTreasuryApiMessage,
      clubAdjustPointPoolApiMessage,
      clubAdjustMemberContributionApiMessage,
      clubUpdateRankTreeApiMessage,
      clubAwardHonorApiMessage,
      clubRevokeHonorApiMessage,
      clubUpdateRecruitmentPolicyApiMessage,
      clubUpdateRelationApiMessage,
      clubListApplicationsApiMessage,
      clubGetCurrentApplicationApiMessage,
      clubGetApplicationApiMessage,
      clubSubmitApplicationApiMessage,
      clubWithdrawApplicationApiMessage,
      clubReviewApplicationApiMessage,
      clubApproveApplicationApiMessage,
      clubRejectApplicationApiMessage,
      clubAcceptTournamentApiMessage,
      clubDeclineTournamentApiMessage
    )

  val contracts: Vector[ApiMessageContract] =
    Vector(
      ApiMessageContract("clubPrivilegeDefinitionsApiMessage", "EmptyApiMessageInput", "Vector[ClubPrivilegeDefinition]", "club", "GET /club-privileges", "done"),
      ApiMessageContract("clubListClubsApiMessage", "ClubListClubsApiMessageInput", "PagedResponse[Club]", "club", "GET /clubs", "done"),
      ApiMessageContract("clubGetClubApiMessage", "ClubGetClubApiMessageInput", "Club", "club", "GET /clubs/{clubId}", "done"),
      ApiMessageContract("clubCreateClubApiMessage", "CreateClubRequest", "Club", "club", "POST /clubs", "done"),
      ApiMessageContract("clubListTournamentsApiMessage", "ClubListTournamentsApiMessageInput", "PagedResponse[ClubTournamentParticipationResponse]", "club", "GET /clubs/{clubId}/tournaments", "done"),
      ApiMessageContract("clubListMembersApiMessage", "ClubListMembersApiMessageInput", "PagedResponse[PlayerResponse]", "club", "GET /clubs/{clubId}/members", "done"),
      ApiMessageContract("clubListMemberPrivilegesApiMessage", "ClubListMemberPrivilegesApiMessageInput", "PagedResponse[ClubMemberPrivilegeSnapshot]", "club", "GET /clubs/{clubId}/member-privileges", "done"),
      ApiMessageContract("clubGetMemberPrivilegeApiMessage", "ClubGetMemberPrivilegeApiMessageInput", "ClubMemberPrivilegeSnapshot", "club", "GET /clubs/{clubId}/member-privileges/{playerId}", "done"),
      ApiMessageContract("clubAddMemberApiMessage", "ClubAddMemberApiMessageInput", "Club", "club", "POST /clubs/{clubId}/members", "done"),
      ApiMessageContract("clubRemoveMemberApiMessage", "ClubRemoveMemberApiMessageInput", "Club", "club", "POST /clubs/{clubId}/members/{playerId}/remove", "done"),
      ApiMessageContract("clubAssignAdminApiMessage", "ClubAssignAdminApiMessageInput", "Club", "club", "POST /clubs/{clubId}/admins", "done"),
      ApiMessageContract("clubRevokeAdminApiMessage", "ClubRevokeAdminApiMessageInput", "Club", "club", "POST /clubs/{clubId}/admins/{playerId}/revoke", "done"),
      ApiMessageContract("clubAssignTitleApiMessage", "ClubAssignTitleApiMessageInput", "Club", "club", "POST /clubs/{clubId}/titles", "done"),
      ApiMessageContract("clubClearTitleApiMessage", "ClubClearTitleApiMessageInput", "Club", "club", "POST /clubs/{clubId}/titles/{playerId}/clear", "done"),
      ApiMessageContract("clubAdjustTreasuryApiMessage", "ClubAdjustTreasuryApiMessageInput", "Club", "club", "POST /clubs/{clubId}/treasury", "done"),
      ApiMessageContract("clubAdjustPointPoolApiMessage", "ClubAdjustPointPoolApiMessageInput", "Club", "club", "POST /clubs/{clubId}/point-pool", "done"),
      ApiMessageContract("clubAdjustMemberContributionApiMessage", "ClubAdjustMemberContributionApiMessageInput", "Club", "club", "POST /clubs/{clubId}/member-contributions", "done"),
      ApiMessageContract("clubUpdateRankTreeApiMessage", "ClubUpdateRankTreeApiMessageInput", "Club", "club", "POST /clubs/{clubId}/rank-tree", "done"),
      ApiMessageContract("clubAwardHonorApiMessage", "ClubAwardHonorApiMessageInput", "Club", "club", "POST /clubs/{clubId}/honors", "done"),
      ApiMessageContract("clubRevokeHonorApiMessage", "ClubRevokeHonorApiMessageInput", "Club", "club", "POST /clubs/{clubId}/honors/revoke", "done"),
      ApiMessageContract("clubUpdateRecruitmentPolicyApiMessage", "ClubUpdateRecruitmentPolicyApiMessageInput", "Club", "club", "POST /clubs/{clubId}/recruitment-policy", "done"),
      ApiMessageContract("clubUpdateRelationApiMessage", "ClubUpdateRelationApiMessageInput", "Club", "club", "POST /clubs/{clubId}/relations", "done"),
      ApiMessageContract("clubListApplicationsApiMessage", "ClubListApplicationsApiMessageInput", "PagedResponse[ClubMembershipApplicationResponse]", "club", "GET /clubs/{clubId}/applications", "done"),
      ApiMessageContract("clubGetCurrentApplicationApiMessage", "ClubGetCurrentApplicationApiMessageInput", "ClubMembershipApplicationResponse", "club", "GET /clubs/{clubId}/applications/current", "done"),
      ApiMessageContract("clubGetApplicationApiMessage", "ClubGetApplicationApiMessageInput", "ClubMembershipApplicationResponse", "club", "GET /clubs/{clubId}/applications/{membershipId}", "done"),
      ApiMessageContract("clubSubmitApplicationApiMessage", "ClubSubmitApplicationApiMessageInput", "ClubMembershipApplication", "club", "POST /clubs/{clubId}/applications", "done"),
      ApiMessageContract("clubWithdrawApplicationApiMessage", "ClubWithdrawApplicationApiMessageInput", "ClubMembershipApplication", "club", "POST /clubs/{clubId}/applications/{membershipId}/withdraw", "done"),
      ApiMessageContract("clubReviewApplicationApiMessage", "ClubReviewApplicationApiMessageInput", "ClubMembershipApplicationResponse", "club", "POST /clubs/{clubId}/applications/{membershipId}/review", "done"),
      ApiMessageContract("clubApproveApplicationApiMessage", "ClubApproveApplicationApiMessageInput", "Club", "club", "POST /clubs/{clubId}/applications/{membershipId}/approve", "done"),
      ApiMessageContract("clubRejectApplicationApiMessage", "ClubRejectApplicationApiMessageInput", "Club", "club", "POST /clubs/{clubId}/applications/{membershipId}/reject", "done"),
      ApiMessageContract("clubAcceptTournamentApiMessage", "ClubTournamentParticipationApiMessageInput", "TournamentMutationResponse", "club", "POST /clubs/{clubId}/tournaments/{tournamentId}/accept", "done"),
      ApiMessageContract("clubDeclineTournamentApiMessage", "ClubTournamentParticipationApiMessageInput", "TournamentMutationResponse", "club", "POST /clubs/{clubId}/tournaments/{tournamentId}/decline", "done")
    )
