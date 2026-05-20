package riichinexus.microservices.tournament.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given
import riichinexus.system.objects.PagedResponse

object TournamentAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[TournamentListAPIMessage, PagedResponse[TournamentSummaryView]],
      RegisteredAPIMessage.api[TournamentGetAPIMessage, TournamentDetailView],
      RegisteredAPIMessage.api[TournamentStageDirectoryAPIMessage, Vector[TournamentStageDirectoryEntry]],
      RegisteredAPIMessage.api[TournamentWhitelistListAPIMessage, PagedResponse[TournamentWhitelistEntryView]],
      RegisteredAPIMessage.api[TournamentSettlementListAPIMessage, PagedResponse[TournamentSettlementView]],
      RegisteredAPIMessage.api[TournamentSettlementGetAPIMessage, TournamentSettlementView],
      RegisteredAPIMessage.created[TournamentCreateAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentPublishAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentStartAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentSettleAPIMessage, TournamentSettlementView],
      RegisteredAPIMessage.api[TournamentSettlementFinalizeAPIMessage, TournamentSettlementView],
      RegisteredAPIMessage.api[TournamentRegisterPlayerAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentRegisterClubAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentRemoveClubParticipationAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentWhitelistPlayerAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentWhitelistClubAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentAssignAdminAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentRevokeAdminAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentStageCreateAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentStageConfigureRulesAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentStageSubmitLineupAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentStageScheduleTablesAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentStageStandingsAPIMessage, StageRankingSnapshot],
      RegisteredAPIMessage.api[TournamentStageTablesAPIMessage, PagedResponse[TournamentTableView]],
      RegisteredAPIMessage.api[TournamentStageAdvancementPreviewAPIMessage, StageAdvancementSnapshot],
      RegisteredAPIMessage.api[TournamentStageKnockoutBracketAPIMessage, KnockoutBracketSnapshot],
      RegisteredAPIMessage.api[TournamentStageAdvanceAPIMessage, Vector[Table]],
      RegisteredAPIMessage.api[TournamentStageCompleteAPIMessage, StageAdvancementSnapshot],
      RegisteredAPIMessage.api[TournamentTableListAPIMessage, PagedResponse[TournamentTableView]],
      RegisteredAPIMessage.api[TournamentTableGetAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableUpdateSeatStateAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableUpdateOwnReadyAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableStartAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableUploadPaifuAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableResetAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentRecordListAPIMessage, PagedResponse[TournamentMatchRecordView]],
      RegisteredAPIMessage.api[TournamentRecordGetAPIMessage, TournamentMatchRecordView],
      RegisteredAPIMessage.api[TournamentRecordGetByTableAPIMessage, TournamentMatchRecordView],
      RegisteredAPIMessage.api[TournamentPaifuListAPIMessage, PagedResponse[TournamentPaifuSummaryView]],
      RegisteredAPIMessage.api[TournamentPaifuGetAPIMessage, TournamentPaifuSummaryView]
    )
