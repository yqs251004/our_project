package riichinexus.microservices.club.router
import riichinexus.system.api.RegisteredAPIMessage


import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.api.participation.{AcceptClubTournamentAPIMessage, DeclineClubTournamentAPIMessage, ListClubTournamentsAPIMessage}
import riichinexus.microservices.club.api.membership.{AddClubMemberAPIMessage, AdjustClubMemberContributionAPIMessage, AssignClubAdminAPIMessage, AssignClubTitleAPIMessage, ClearClubTitleAPIMessage, GetClubApplicationAPIMessage, GetCurrentClubApplicationAPIMessage, ListClubApplicationsAPIMessage, ListClubMembersAPIMessage, RemoveClubMemberAPIMessage, ReviewClubApplicationAPIMessage, RevokeClubAdminAPIMessage, SubmitClubApplicationAPIMessage, WithdrawClubApplicationAPIMessage}
import riichinexus.microservices.club.api.profile.{AdjustClubPointPoolAPIMessage, AdjustClubTreasuryAPIMessage, AwardClubHonorAPIMessage, CreateClubAPIMessage, GetClubAPIMessage, GetPublicClubAPIMessage, ListClubsAPIMessage, ListPublicClubsAPIMessage, PublicClubLeaderboardAPIMessage, RevokeClubHonorAPIMessage, UpdateClubRecruitmentPolicyAPIMessage}
import riichinexus.microservices.club.api.rankprivilege.{ClubPrivilegeDefinitionsAPIMessage, GetClubMemberPrivilegeAPIMessage, ListClubMemberPrivilegesAPIMessage, UpdateClubRankTreeAPIMessage}
import riichinexus.microservices.club.api.audit.{ListClubContributionAuditsAPIMessage}
import riichinexus.microservices.club.api.relation.{SubmitClubRelationRequestAPIMessage, UpdateClubRelationAPIMessage}
import riichinexus.microservices.club.objects.profile.ClubView
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeDefinition
import riichinexus.microservices.club.objects.profile.{ClubLeaderboardEntry, PublicClubDetailView, PublicClubDirectoryEntry}
import riichinexus.microservices.club.objects.membership.apiTypes.{ClubMembershipApplicationResponse}
import riichinexus.microservices.club.objects.membership.{ClubMembershipApplicationView}
import riichinexus.microservices.club.objects.rankprivilege.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.objects.participation.ClubTournamentParticipationView
import riichinexus.microservices.club.objects.audit.ClubContributionAuditEntry
import riichinexus.microservices.player.objects.PlayerProfileView
import riichinexus.microservices.notification.objects.Notification
import riichinexus.system.objects.PagedResponse
import riichinexus.microservices.tournament.objects.competition.TournamentMutationView

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
