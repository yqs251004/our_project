package riichinexus.microservices.club.router
import riichinexus.system.api.RegisteredAPIMessage


import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.api.{AcceptClubTournamentAPIMessage, AddClubMemberAPIMessage, AdjustClubMemberContributionAPIMessage, AdjustClubPointPoolAPIMessage, AdjustClubTreasuryAPIMessage, AssignClubAdminAPIMessage, AssignClubTitleAPIMessage, AwardClubHonorAPIMessage, ClearClubTitleAPIMessage, ClubPrivilegeDefinitionsAPIMessage, CreateClubAPIMessage, DeclineClubTournamentAPIMessage, GetClubAPIMessage, GetClubApplicationAPIMessage, GetClubMemberPrivilegeAPIMessage, GetCurrentClubApplicationAPIMessage, GetPublicClubAPIMessage, ListClubApplicationsAPIMessage, ListClubContributionAuditsAPIMessage, ListClubMemberPrivilegesAPIMessage, ListClubMembersAPIMessage, ListClubTournamentsAPIMessage, ListClubsAPIMessage, ListPublicClubsAPIMessage, PublicClubLeaderboardAPIMessage, RemoveClubMemberAPIMessage, ReviewClubApplicationAPIMessage, RevokeClubAdminAPIMessage, RevokeClubHonorAPIMessage, SubmitClubApplicationAPIMessage, SubmitClubRelationRequestAPIMessage, UpdateClubRankTreeAPIMessage, UpdateClubRecruitmentPolicyAPIMessage, UpdateClubRelationAPIMessage, WithdrawClubApplicationAPIMessage}
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeDefinition
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.{ClubLeaderboardEntry, PublicClubDetailView, PublicClubDirectoryEntry}
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubMembershipApplicationResponse, ClubMembershipApplicationView}
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.ClubTournamentParticipationView
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.ClubContributionAuditEntry
import riichinexus.microservices.player.objects.apiTypes.PlayerProfileView
import riichinexus.microservices.notification.objects.Notification
import riichinexus.system.objects.PagedResponse
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentMutationView

object ClubAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[ClubPrivilegeDefinitionsAPIMessage, Vector[ClubPrivilegeDefinition]],
      RegisteredAPIMessage.api[ListClubsAPIMessage, PagedResponse[ClubView]],
      RegisteredAPIMessage.api[GetClubAPIMessage, ClubView],
      RegisteredAPIMessage.api[ListPublicClubsAPIMessage, PagedResponse[PublicClubDirectoryEntry]],
      RegisteredAPIMessage.api[GetPublicClubAPIMessage, PublicClubDetailView],
      RegisteredAPIMessage.api[PublicClubLeaderboardAPIMessage, PagedResponse[ClubLeaderboardEntry]],
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
      RegisteredAPIMessage.accepted[SubmitClubRelationRequestAPIMessage, Vector[Notification]],
      RegisteredAPIMessage.api[ListClubApplicationsAPIMessage, PagedResponse[ClubMembershipApplicationView]],
      RegisteredAPIMessage.api[GetCurrentClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[GetClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[SubmitClubApplicationAPIMessage, ClubMembershipApplicationResponse],
      RegisteredAPIMessage.api[WithdrawClubApplicationAPIMessage, ClubMembershipApplicationResponse],
      RegisteredAPIMessage.api[ReviewClubApplicationAPIMessage, ClubMembershipApplicationView],
      RegisteredAPIMessage.api[AcceptClubTournamentAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[DeclineClubTournamentAPIMessage, TournamentMutationView]
    )
