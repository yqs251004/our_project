package riichinexus.microservices.club.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.*
import riichinexus.microservices.club.objects.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.player.objects.PlayerProfileView
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given

object ClubAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[ClubPrivilegeDefinitionsAPIMessage, Vector[ClubPrivilegeDefinition]],
      RegisteredAPIMessage.api[ListClubsAPIMessage, PagedResponse[ClubView]],
      RegisteredAPIMessage.api[GetClubAPIMessage, ClubView],
      RegisteredAPIMessage.created[CreateClubAPIMessage, ClubView],
      RegisteredAPIMessage.api[ListClubTournamentsAPIMessage, PagedResponse[ClubTournamentParticipationView]],
      RegisteredAPIMessage.api[ListClubMembersAPIMessage, PagedResponse[PlayerProfileView]],
      RegisteredAPIMessage.api[ListClubMemberPrivilegesAPIMessage, PagedResponse[ClubMemberPrivilegeSnapshotView]],
      RegisteredAPIMessage.api[GetClubMemberPrivilegeAPIMessage, ClubMemberPrivilegeSnapshotView],
      RegisteredAPIMessage.api[ListClubContributionAuditsAPIMessage, PagedResponse[ClubContributionAuditEntry]],
      RegisteredAPIMessage.api[AddClubMemberAPIMessage, ClubView],
      RegisteredAPIMessage.api[RemoveClubMemberAPIMessage, ClubView],
      RegisteredAPIMessage.api[AssignClubAdminAPIMessage, ClubView],
      RegisteredAPIMessage.api[RevokeClubAdminAPIMessage, ClubView],
      RegisteredAPIMessage.api[AssignClubTitleAPIMessage, ClubView],
      RegisteredAPIMessage.api[ClearClubTitleAPIMessage, ClubView],
      RegisteredAPIMessage.api[AdjustClubTreasuryAPIMessage, ClubView],
      RegisteredAPIMessage.api[AdjustClubPointPoolAPIMessage, ClubView],
      RegisteredAPIMessage.api[AdjustClubMemberContributionAPIMessage, ClubView],
      RegisteredAPIMessage.api[UpdateClubRankTreeAPIMessage, ClubView],
      RegisteredAPIMessage.api[AwardClubHonorAPIMessage, ClubView],
      RegisteredAPIMessage.api[RevokeClubHonorAPIMessage, ClubView],
      RegisteredAPIMessage.api[UpdateClubRecruitmentPolicyAPIMessage, ClubView],
      RegisteredAPIMessage.api[UpdateClubRelationAPIMessage, ClubView],
      RegisteredAPIMessage.api[ListClubApplicationsAPIMessage, PagedResponse[ClubMembershipApplicationView]],
      RegisteredAPIMessage.api[GetCurrentClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[GetClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[SubmitClubApplicationAPIMessage, ClubMembershipApplicationResponse],
      RegisteredAPIMessage.api[WithdrawClubApplicationAPIMessage, ClubMembershipApplicationResponse],
      RegisteredAPIMessage.api[ReviewClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[ApproveClubApplicationAPIMessage, ClubView],
      RegisteredAPIMessage.api[RejectClubApplicationAPIMessage, ClubView],
      RegisteredAPIMessage.api[AcceptClubTournamentAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[DeclineClubTournamentAPIMessage, TournamentMutationView]
    )
