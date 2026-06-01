package riichinexus.microservices.club.router
import riichinexus.api.functions.RegisteredAPIMessageFunctions

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.*
import riichinexus.microservices.club.objects.clubmanagement.*
import riichinexus.microservices.club.objects.membershipmanagement.*
import riichinexus.microservices.club.objects.rankprivilegemanagement.*
import riichinexus.microservices.club.objects.relationmanagement.*
import riichinexus.microservices.club.objects.tournamentparticipation.*
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.*
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.*
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.*
import riichinexus.microservices.club.objects.relationmanagement.apiTypes.*
import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.*
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*

object ClubAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessageFunctions.api[ClubPrivilegeDefinitionsAPIMessage, Vector[ClubPrivilegeDefinition]],
      RegisteredAPIMessageFunctions.api[ListClubsAPIMessage, PagedResponse[ClubView]],
      RegisteredAPIMessageFunctions.api[GetClubAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[ListPublicClubsAPIMessage, PagedResponse[PublicClubDirectoryEntry]],
      RegisteredAPIMessageFunctions.api[GetPublicClubAPIMessage, PublicClubDetailView],
      RegisteredAPIMessageFunctions.api[PublicClubLeaderboardAPIMessage, PagedResponse[ClubLeaderboardEntry]],
      RegisteredAPIMessageFunctions.created[CreateClubAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[ListClubTournamentsAPIMessage, PagedResponse[ClubTournamentParticipationView]],
      RegisteredAPIMessageFunctions.api[ListClubMembersAPIMessage, PagedResponse[PlayerProfileView]],
      RegisteredAPIMessageFunctions.api[ListClubMemberPrivilegesAPIMessage, PagedResponse[ClubMemberPrivilegeSnapshotView]],
      RegisteredAPIMessageFunctions.api[GetClubMemberPrivilegeAPIMessage, ClubMemberPrivilegeSnapshotView],
      RegisteredAPIMessageFunctions.api[ListClubContributionAuditsAPIMessage, PagedResponse[ClubContributionAuditEntry]],
      RegisteredAPIMessageFunctions.api[AddClubMemberAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[RemoveClubMemberAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[AssignClubAdminAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[RevokeClubAdminAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[AssignClubTitleAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[ClearClubTitleAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[AdjustClubTreasuryAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[AdjustClubPointPoolAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[AdjustClubMemberContributionAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[UpdateClubRankTreeAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[AwardClubHonorAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[RevokeClubHonorAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[UpdateClubRecruitmentPolicyAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[UpdateClubRelationAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[ListClubApplicationsAPIMessage, PagedResponse[ClubMembershipApplicationView]],
      RegisteredAPIMessageFunctions.api[GetCurrentClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessageFunctions.api[GetClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessageFunctions.api[SubmitClubApplicationAPIMessage, ClubMembershipApplicationResponse],
      RegisteredAPIMessageFunctions.api[WithdrawClubApplicationAPIMessage, ClubMembershipApplicationResponse],
      RegisteredAPIMessageFunctions.api[ReviewClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessageFunctions.api[ApproveClubApplicationAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[RejectClubApplicationAPIMessage, ClubView],
      RegisteredAPIMessageFunctions.api[AcceptClubTournamentAPIMessage, TournamentMutationView],
      RegisteredAPIMessageFunctions.api[DeclineClubTournamentAPIMessage, TournamentMutationView]
    )
