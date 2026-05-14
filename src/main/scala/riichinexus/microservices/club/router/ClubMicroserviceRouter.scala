package riichinexus.microservices.club.router

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.{
  ClubApplicationApi,
  ClubApplicationService,
  ClubManagementApi,
  ClubQueryApi,
  ClubTournamentApi,
  ClubViewAssembler
}
import riichinexus.microservices.club.api.responses.ClubTournamentResponses.given
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.club.objects.*
import riichinexus.microservices.club.tables.ClubTables
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.given
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.shared.api.requests.OperatorRequest.given
import riichinexus.microservices.tournament.api.{TournamentApplicationService, TournamentViewAssembler}
import riichinexus.microservices.tournament.api.responses.TournamentOperationResponses.given
import riichinexus.api.http.RouteSupport

object ClubMicroserviceRouter:
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

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case GET -> Root / "club-privileges" =>
      support.handled(support.jsonResponse(Status.Ok, ClubQueryApi.privilegeDefinitions))

    case req @ GET -> Root / "clubs" =>
      support.handled {
        val query = ClubListQuery(
          activeOnly = support.queryBooleanParam(req, "activeOnly").contains(true),
          joinableOnly = support.queryBooleanParam(req, "joinableOnly").contains(true),
          memberId = support.queryParam(req, "memberId").filter(_.nonEmpty).map(PlayerId(_)),
          adminId = support.queryParam(req, "adminId").filter(_.nonEmpty).map(PlayerId(_)),
          name = support.queryParam(req, "name").filter(_.nonEmpty)
        )
        val clubs = ClubQueryApi.listClubs(deps.tables, query)
        support.pagedJsonResponse(req, clubs, support.activeFilters(req, "activeOnly", "joinableOnly", "memberId", "adminId", "name"))
      }

    case GET -> Root / "clubs" / clubId =>
      support.handled(support.optionJsonResponse(ClubQueryApi.findClub(deps.tables, ClubId(clubId))))

    case req @ GET -> Root / "clubs" / clubId / "tournaments" =>
      support.handled {
        val query = ClubTournamentParticipationQuery(
          scope = support.queryParam(req, "scope").filter(_.nonEmpty).getOrElse("recent"),
          viewer = support.queryParam(req, "viewer").filter(_.nonEmpty).map(PlayerId(_))
        )
        val items = ClubQueryApi.clubTournamentParticipations(
          deps.tables,
          deps.views,
          support.principal,
          ClubId(clubId),
          query
        )
        support.pagedJsonResponse(req, items, support.activeFilters(req, "scope", "viewer"))
      }

    case req @ GET -> Root / "clubs" / clubId / "members" =>
      support.handled {
        val query = ClubMemberQuery(
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(PlayerStatus.valueOf)
          ),
          nickname = support.queryParam(req, "nickname").filter(_.nonEmpty)
        )
        val members = ClubQueryApi.listMembers(deps.tables, support.containsIgnoreCase, ClubId(clubId), query)
        support.pagedJsonResponse(req, members, support.activeFilters(req, "status", "nickname"))
      }

    case req @ GET -> Root / "clubs" / clubId / "member-privileges" =>
      support.handled {
        val query = ClubPrivilegeSnapshotQuery(
          playerId = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_)),
          privilege = support.queryParam(req, "privilege").filter(_.nonEmpty).map(ClubPrivilegeRegistry.requireSupported),
          rankCode = support.queryParam(req, "rankCode").filter(_.nonEmpty).map(_.trim.toLowerCase)
        )
        val snapshots = ClubQueryApi.listPrivilegeSnapshots(deps.tables, ClubId(clubId), query)
        support.pagedJsonResponse(req, snapshots, support.activeFilters(req, "playerId", "privilege", "rankCode"))
      }

    case GET -> Root / "clubs" / clubId / "member-privileges" / playerId =>
      support.handled(
        support.optionJsonResponse(ClubQueryApi.privilegeSnapshot(deps.tables, ClubId(clubId), PlayerId(playerId)))
      )

    case req @ GET -> Root / "clubs" / clubId / "applications" =>
      support.handled {
        val query = ClubApplicationQuery(
          operatorId = support.queryParam(req, "operatorId").filter(_.nonEmpty).map(PlayerId(_)),
          guestSessionId = support.queryParam(req, "guestSessionId").filter(_.nonEmpty),
          status = support.queryParam(req, "status")
            .filter(_.nonEmpty)
            .map(support.parseEnum("status", _)(ClubMembershipApplicationStatus.valueOf)),
          applicantUserId = support.queryParam(req, "applicantUserId").filter(_.nonEmpty),
          displayName = support.queryParam(req, "displayName").filter(_.nonEmpty)
        )
        val applications = ClubApplicationApi.listApplications(
          deps.tables,
          deps.views,
          support.requestActor,
          support.containsIgnoreCase,
          ClubId(clubId),
          query
        )
        support.pagedJsonResponse(
          req,
          applications,
          support.activeFilters(req, "operatorId", "guestSessionId", "status", "applicantUserId", "displayName")
        )
      }

    case req @ GET -> Root / "clubs" / clubId / "applications" / "current" =>
      support.handled {
        support.optionJsonResponse(
          ClubApplicationApi.currentApplication(
            deps.tables,
            deps.views,
            support.requestActor,
            ClubId(clubId),
            support.queryParam(req, "guestSessionId").filter(_.nonEmpty).map(GuestSessionId(_)),
            support.queryParam(req, "operatorId").filter(_.nonEmpty).map(PlayerId(_))
          )
        )
      }

    case req @ GET -> Root / "clubs" / clubId / "applications" / applicationId =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          ClubApplicationApi.applicationDetail(
            deps.tables,
            deps.views,
            support.requestActor,
            ClubId(clubId),
            MembershipApplicationId(applicationId),
            support.queryParam(req, "guestSessionId").filter(_.nonEmpty).map(GuestSessionId(_)),
            support.queryParam(req, "operatorId").filter(_.nonEmpty).map(PlayerId(_))
          )
        )
      }

    case req @ POST -> Root / "clubs" =>
      support.handled {
        support.readJsonBody[CreateClubRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, ClubManagementApi.createClub(deps.service, support.principal, request))
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "members" =>
      support.handled {
        support.readJsonBody[AddClubMemberRequest](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.addMember(deps.service, support.principal, ClubId(clubId), request))
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "members" / playerId / "remove" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.removeMember(deps.service, support.principal, ClubId(clubId), PlayerId(playerId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" =>
      support.handled {
        support.readJsonBody[ClubMembershipApplicationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubApplicationApi.applyForMembership(
              deps.tables,
              deps.service,
              support.requestActor,
              ClubId(clubId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" / applicationId / "withdraw" =>
      support.handled {
        support.readJsonBody[WithdrawClubApplicationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubApplicationApi.withdrawMembershipApplication(
              deps.service,
              support.requestActor,
              ClubId(clubId),
              MembershipApplicationId(applicationId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" / applicationId / "review" =>
      support.handled {
        support.readJsonBody[ReviewClubApplicationRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            ClubApplicationApi.reviewMembershipApplication(
              deps.tables,
              deps.service,
              deps.views,
              support.principal,
              ClubId(clubId),
              MembershipApplicationId(applicationId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" / applicationId / "approve" =>
      support.handled {
        support.readJsonBody[ApproveClubApplicationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubApplicationApi.approveMembershipApplication(
              deps.service,
              support.principal,
              ClubId(clubId),
              MembershipApplicationId(applicationId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" / applicationId / "reject" =>
      support.handled {
        support.readJsonBody[RejectClubApplicationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubApplicationApi.rejectMembershipApplication(
              deps.service,
              support.principal,
              ClubId(clubId),
              MembershipApplicationId(applicationId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "tournaments" / tournamentId / "accept" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubTournamentApi.acceptParticipation(
              deps.tournamentService,
              deps.tournamentViews,
              support.principal,
              ClubId(clubId),
              TournamentId(tournamentId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "tournaments" / tournamentId / "decline" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubTournamentApi.declineParticipation(
              deps.tournamentService,
              deps.tournamentViews,
              support.principal,
              ClubId(clubId),
              TournamentId(tournamentId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "admins" =>
      support.handled {
        support.readJsonBody[AssignClubAdminRequest](req).flatMap { request =>
          support.optionJsonResponse(ClubManagementApi.assignAdmin(deps.service, support.principal, ClubId(clubId), request))
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "admins" / playerId / "revoke" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.revokeAdmin(deps.service, support.principal, ClubId(clubId), PlayerId(playerId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "titles" =>
      support.handled {
        support.readJsonBody[AssignClubTitleRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.setInternalTitle(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "titles" / playerId / "clear" =>
      support.handled {
        support.readJsonBody[ClearClubTitleRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.clearInternalTitle(
              deps.service,
              support.principal,
              ClubId(clubId),
              PlayerId(playerId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "treasury" =>
      support.handled {
        support.readJsonBody[AdjustClubTreasuryRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.adjustTreasury(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "point-pool" =>
      support.handled {
        support.readJsonBody[AdjustClubPointPoolRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.adjustPointPool(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "member-contributions" =>
      support.handled {
        support.readJsonBody[AdjustClubMemberContributionRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.adjustMemberContribution(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "rank-tree" =>
      support.handled {
        support.readJsonBody[UpdateClubRankTreeRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.updateRankTree(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "honors" =>
      support.handled {
        support.readJsonBody[AwardClubHonorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.awardHonor(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "honors" / "revoke" =>
      support.handled {
        support.readJsonBody[RevokeClubHonorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.revokeHonor(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "recruitment-policy" =>
      support.handled {
        support.readJsonBody[UpdateClubRecruitmentPolicyRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.updateRecruitmentPolicy(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "relations" =>
      support.handled {
        support.readJsonBody[UpdateClubRelationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubManagementApi.updateRelation(deps.service, support.principal, ClubId(clubId), request)
          )
        }
      }
  }


