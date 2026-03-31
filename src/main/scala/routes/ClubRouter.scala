package routes

import java.util.NoSuchElementException

import api.contracts.ApiContracts.*
import api.contracts.JsonSupport.given
import cats.effect.IO
import model.DomainModels.*
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*

object ClubRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "club-privileges" =>
      support.handled(support.jsonResponse(Status.Ok, ClubPrivilegeRegistry.definitions))

    case req @ GET -> Root / "clubs" =>
      support.handled {
        val activeOnly = support.queryBooleanParam(req, "activeOnly")
        val joinableOnly = support.queryBooleanParam(req, "joinableOnly")
        val memberIdFilter = support.queryParam(req, "memberId").filter(_.nonEmpty).map(PlayerId(_))
        val adminIdFilter = support.queryParam(req, "adminId").filter(_.nonEmpty).map(PlayerId(_))
        val nameFilter = support.queryParam(req, "name").filter(_.nonEmpty)
        val clubs = support.app.clubRepository.findAll()
          .filter(club => activeOnly.forall(flag => !flag || club.dissolvedAt.isEmpty))
          .filter(club => joinableOnly.forall(flag => !flag || support.clubApplicationsOpen(club)))
          .filter(club => memberIdFilter.forall(club.members.contains))
          .filter(club => adminIdFilter.forall(club.admins.contains))
          .filter(club => nameFilter.forall(support.containsIgnoreCase(club.name, _)))
          .sortBy(club => (club.dissolvedAt.nonEmpty, club.name, club.id.value))
        support.pagedJsonResponse(req, clubs, support.activeFilters(req, "activeOnly", "joinableOnly", "memberId", "adminId", "name"))
      }

    case GET -> Root / "clubs" / clubId =>
      support.handled(support.optionJsonResponse(support.app.clubRepository.findById(ClubId(clubId))))

    case req @ GET -> Root / "clubs" / clubId / "members" =>
      support.handled {
        val statusFilter = support.queryParam(req, "status").filter(_.nonEmpty).map(
          support.parseEnum("status", _)(PlayerStatus.valueOf)
        )
        val nicknameFilter = support.queryParam(req, "nickname").filter(_.nonEmpty)
        val members = support.app.playerRepository.findByClub(ClubId(clubId))
          .filter(player => statusFilter.forall(_ == player.status))
          .filter(player => nicknameFilter.forall(support.containsIgnoreCase(player.nickname, _)))
          .sortBy(player => (player.nickname, player.id.value))
        support.pagedJsonResponse(req, members, support.activeFilters(req, "status", "nickname"))
      }

    case req @ GET -> Root / "clubs" / clubId / "member-privileges" =>
      support.handled {
        val playerIdFilter = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val privilegeFilter = support.queryParam(req, "privilege").filter(_.nonEmpty).map(ClubPrivilegeRegistry.requireSupported)
        val rankCodeFilter = support.queryParam(req, "rankCode").filter(_.nonEmpty).map(_.trim.toLowerCase)
        val snapshots = support.app.clubService.listMemberPrivilegeSnapshots(ClubId(clubId))
          .filter(snapshot => playerIdFilter.forall(_ == snapshot.playerId))
          .filter(snapshot => privilegeFilter.forall(snapshot.privileges.contains))
          .filter(snapshot => rankCodeFilter.forall(_ == snapshot.rankCode.trim.toLowerCase))
        support.pagedJsonResponse(req, snapshots, support.activeFilters(req, "playerId", "privilege", "rankCode"))
      }

    case GET -> Root / "clubs" / clubId / "member-privileges" / playerId =>
      support.handled(support.optionJsonResponse(support.app.clubService.memberPrivilegeSnapshot(ClubId(clubId), PlayerId(playerId))))

    case req @ GET -> Root / "clubs" / clubId / "applications" =>
      support.handled {
        val club = support.app.clubRepository
          .findById(ClubId(clubId))
          .getOrElse(throw NoSuchElementException(s"Club $clubId was not found"))
        val actor = support.requestActor(
          support.queryParam(req, "guestSessionId").filter(_.nonEmpty).map(GuestSessionId(_)),
          support.queryParam(req, "operatorId").filter(_.nonEmpty).map(PlayerId(_))
        )
        support.requireClubApplicationManager(actor, club)
        val applications = club.membershipApplications
          .filter(application =>
            support.queryParam(req, "status")
              .filter(_.nonEmpty)
              .map(support.parseEnum("status", _)(ClubMembershipApplicationStatus.valueOf))
              .forall(_ == application.status)
          )
          .filter(application =>
            support.queryParam(req, "applicantUserId")
              .filter(_.nonEmpty)
              .forall(value => application.applicantUserId.contains(value))
          )
          .filter(application =>
            support.queryParam(req, "displayName")
              .filter(_.nonEmpty)
              .forall(support.containsIgnoreCase(application.displayName, _))
          )
          .sortBy(_.submittedAt)
          .map(application => support.buildClubMembershipApplicationView(club, application, actor))
        support.pagedJsonResponse(
          req,
          applications,
          support.activeFilters(req, "operatorId", "guestSessionId", "status", "applicantUserId", "displayName")
        )
      }

    case req @ GET -> Root / "clubs" / clubId / "applications" / applicationId =>
      support.handled {
        val club = support.app.clubRepository
          .findById(ClubId(clubId))
          .getOrElse(throw NoSuchElementException(s"Club $clubId was not found"))
        val actor = support.requestActor(
          support.queryParam(req, "guestSessionId").filter(_.nonEmpty).map(GuestSessionId(_)),
          support.queryParam(req, "operatorId").filter(_.nonEmpty).map(PlayerId(_))
        )
        val application = club.findApplication(MembershipApplicationId(applicationId)).getOrElse(
          throw NoSuchElementException(s"Membership application $applicationId was not found in club $clubId")
        )
        support.requireClubApplicationViewer(actor, club, application)
        support.jsonResponse(Status.Ok, support.buildClubMembershipApplicationView(club, application, actor))
      }

    case req @ POST -> Root / "clubs" =>
      support.handled {
        support.readJsonBody[CreateClubRequest](req).flatMap { request =>
          val club = support.app.clubService.createClub(
            name = request.name,
            creatorId = request.creator,
            actor = support.principal(request.creator)
          )
          support.jsonResponse(Status.Created, club)
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "members" =>
      support.handled {
        support.readJsonBody[AddClubMemberRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.addMember(
              ClubId(clubId),
              request.player,
              request.operator.map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "members" / playerId / "remove" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.removeMember(
              ClubId(clubId),
              PlayerId(playerId),
              request.operator.map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" =>
      support.handled {
        support.readJsonBody[ClubMembershipApplicationRequest](req).flatMap { request =>
          val actor = support.requestActor(request.session, request.operator)
          val applicantUserId = request.applicantUserId
            .orElse(request.session.map(session => s"guest:${session.value}"))
            .orElse(request.operator.flatMap(playerId => support.app.playerRepository.findById(playerId).map(_.userId)))
          val displayName = request.session.map(_ => actor.displayName)
            .orElse(request.operator.flatMap(playerId => support.app.playerRepository.findById(playerId).map(_.nickname)))
            .getOrElse(request.displayName)
          support.optionJsonResponse(
            support.app.clubService.applyForMembership(
              clubId = ClubId(clubId),
              applicantUserId = applicantUserId,
              displayName = displayName,
              message = request.message,
              actor = actor
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" / applicationId / "withdraw" =>
      support.handled {
        support.readJsonBody[WithdrawClubApplicationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.withdrawMembershipApplication(
              clubId = ClubId(clubId),
              applicationId = MembershipApplicationId(applicationId),
              actor = support.requestActor(request.session, request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" / applicationId / "review" =>
      support.handled {
        support.readJsonBody[ReviewClubApplicationRequest](req).flatMap { request =>
          val normalizedDecision = request.decision.trim.toLowerCase
          val clubKey = ClubId(clubId)
          val membershipKey = MembershipApplicationId(applicationId)
          val updatedClub =
            normalizedDecision match
              case "approve" | "approved" =>
                val club = support.app.clubRepository
                  .findById(clubKey)
                  .getOrElse(throw NoSuchElementException(s"Club $clubId was not found"))
                val application = club.findApplication(membershipKey).getOrElse(
                  throw NoSuchElementException(s"Membership application $applicationId was not found in club $clubId")
                )
                val player = request.player
                  .flatMap(playerId => support.app.playerRepository.findById(playerId))
                  .orElse(
                    application.applicantUserId
                      .filterNot(_.startsWith("guest:"))
                      .flatMap(support.app.playerRepository.findByUserId)
                  )
                  .getOrElse(
                    throw IllegalArgumentException(
                      s"Membership application $applicationId requires playerId when approving a guest-origin application"
                    )
                  )
                support.app.clubService.approveMembershipApplication(
                  clubId = clubKey,
                  applicationId = membershipKey,
                  playerId = player.id,
                  actor = support.principal(request.operator),
                  note = request.note
                )
              case "reject" | "rejected" =>
                support.app.clubService.rejectMembershipApplication(
                  clubId = clubKey,
                  applicationId = membershipKey,
                  actor = support.principal(request.operator),
                  note = request.note
                )
              case other =>
                throw IllegalArgumentException(
                  s"Unsupported review decision '$other'. Supported decisions: approve, reject"
                )

          val reviewedClub = updatedClub.getOrElse(throw NoSuchElementException(s"Club $clubId was not found"))
          val reviewedApplication = reviewedClub.findApplication(membershipKey).getOrElse(
            throw NoSuchElementException(s"Membership application $applicationId was not found in club $clubId")
          )
          support.jsonResponse(
            Status.Ok,
            support.buildClubMembershipApplicationView(reviewedClub, reviewedApplication, support.principal(request.operator))
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" / applicationId / "approve" =>
      support.handled {
        support.readJsonBody[ApproveClubApplicationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.approveMembershipApplication(
              clubId = ClubId(clubId),
              applicationId = MembershipApplicationId(applicationId),
              playerId = request.player,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "applications" / applicationId / "reject" =>
      support.handled {
        support.readJsonBody[RejectClubApplicationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.rejectMembershipApplication(
              clubId = ClubId(clubId),
              applicationId = MembershipApplicationId(applicationId),
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "admins" =>
      support.handled {
        support.readJsonBody[AssignClubAdminRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.assignAdmin(
              clubId = ClubId(clubId),
              playerId = request.player,
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "admins" / playerId / "revoke" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.revokeAdmin(
              clubId = ClubId(clubId),
              playerId = PlayerId(playerId),
              actor = request.operator.map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "titles" =>
      support.handled {
        support.readJsonBody[AssignClubTitleRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.setInternalTitle(
              clubId = ClubId(clubId),
              playerId = request.player,
              title = request.title,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "titles" / playerId / "clear" =>
      support.handled {
        support.readJsonBody[ClearClubTitleRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.clearInternalTitle(
              clubId = ClubId(clubId),
              playerId = PlayerId(playerId),
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "treasury" =>
      support.handled {
        support.readJsonBody[AdjustClubTreasuryRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.adjustTreasury(
              clubId = ClubId(clubId),
              delta = request.delta,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "point-pool" =>
      support.handled {
        support.readJsonBody[AdjustClubPointPoolRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.adjustPointPool(
              clubId = ClubId(clubId),
              delta = request.delta,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "member-contributions" =>
      support.handled {
        support.readJsonBody[AdjustClubMemberContributionRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.adjustMemberContribution(
              clubId = ClubId(clubId),
              playerId = request.player,
              delta = request.delta,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "rank-tree" =>
      support.handled {
        support.readJsonBody[UpdateClubRankTreeRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.updateRankTree(
              clubId = ClubId(clubId),
              rankTree = request.nodes,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "honors" =>
      support.handled {
        support.readJsonBody[AwardClubHonorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.awardHonor(
              clubId = ClubId(clubId),
              honor = request.honor,
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "honors" / "revoke" =>
      support.handled {
        support.readJsonBody[RevokeClubHonorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.revokeHonor(
              clubId = ClubId(clubId),
              title = request.title,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "recruitment-policy" =>
      support.handled {
        support.readJsonBody[UpdateClubRecruitmentPolicyRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.updateRecruitmentPolicy(
              clubId = ClubId(clubId),
              policy = request.policy,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "clubs" / clubId / "relations" =>
      support.handled {
        support.readJsonBody[UpdateClubRelationRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.clubService.updateRelation(
              clubId = ClubId(clubId),
              relation = request.toRelation(),
              actor = support.principal(request.operator)
            )
          )
        }
      }
  }
