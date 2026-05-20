package riichinexus.microservices.club.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.PlayerResponses.given
import riichinexus.system.objects.PagedResponse
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given

object ClubAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[ClubPrivilegeDefinitionsAPIMessage, Vector[ClubPrivilegeDefinition]],
      RegisteredAPIMessage.api[ListClubsAPIMessage, PagedResponse[Club]],
      RegisteredAPIMessage.api[GetClubAPIMessage, Club],
      RegisteredAPIMessage.created[CreateClubAPIMessage, Club],
      RegisteredAPIMessage.api[ListClubTournamentsAPIMessage, PagedResponse[ClubTournamentParticipationView]],
      RegisteredAPIMessage.api[ListClubMembersAPIMessage, PagedResponse[PlayerProfileView]],
      RegisteredAPIMessage.api[ListClubMemberPrivilegesAPIMessage, PagedResponse[ClubMemberPrivilegeSnapshot]],
      RegisteredAPIMessage.api[GetClubMemberPrivilegeAPIMessage, ClubMemberPrivilegeSnapshot],
      RegisteredAPIMessage.api[AddClubMemberAPIMessage, Club],
      RegisteredAPIMessage.api[RemoveClubMemberAPIMessage, Club],
      RegisteredAPIMessage.api[AssignClubAdminAPIMessage, Club],
      RegisteredAPIMessage.api[RevokeClubAdminAPIMessage, Club],
      RegisteredAPIMessage.api[AssignClubTitleAPIMessage, Club],
      RegisteredAPIMessage.api[ClearClubTitleAPIMessage, Club],
      RegisteredAPIMessage.api[AdjustClubTreasuryAPIMessage, Club],
      RegisteredAPIMessage.api[AdjustClubPointPoolAPIMessage, Club],
      RegisteredAPIMessage.api[AdjustClubMemberContributionAPIMessage, Club],
      RegisteredAPIMessage.api[UpdateClubRankTreeAPIMessage, Club],
      RegisteredAPIMessage.api[AwardClubHonorAPIMessage, Club],
      RegisteredAPIMessage.api[RevokeClubHonorAPIMessage, Club],
      RegisteredAPIMessage.api[UpdateClubRecruitmentPolicyAPIMessage, Club],
      RegisteredAPIMessage.api[UpdateClubRelationAPIMessage, Club],
      RegisteredAPIMessage.api[ListClubApplicationsAPIMessage, PagedResponse[ClubMembershipApplicationView]],
      RegisteredAPIMessage.api[GetCurrentClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[GetClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[SubmitClubApplicationAPIMessage, ClubMembershipApplication],
      RegisteredAPIMessage.api[WithdrawClubApplicationAPIMessage, ClubMembershipApplication],
      RegisteredAPIMessage.api[ReviewClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[ApproveClubApplicationAPIMessage, Club],
      RegisteredAPIMessage.api[RejectClubApplicationAPIMessage, Club],
      RegisteredAPIMessage.api[AcceptClubTournamentAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[DeclineClubTournamentAPIMessage, TournamentMutationView]
    )
